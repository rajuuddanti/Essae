-- MahaMart Multi-Device Store Registration / Admin Push Sync
-- Apply AFTER:
--   1. MahaMart_FRESH_SUPABASE_SCHEMA.sql
--   2. MahaMart_SUPABASE_ADMIN_PUSH_MIGRATION.sql
--
-- Purpose:
--   A store can have multiple Android devices.
--   STORE CODE is the permanent store identity.
--   DEVICE ID identifies one physical Android device.
--   IP is network/audit information only.
--
-- This migration is additive. It does not delete or alter the existing
-- store_ip_map table, so the current stable app remains compatible while
-- the Android client is moved to the multi-device model.

create table if not exists public.store_devices (
    id uuid primary key default gen_random_uuid(),
    store_id uuid not null references public.stores(id) on delete cascade,
    device_id text not null,
    device_name text not null default '',
    device_ip text not null default '',
    active boolean not null default true,
    registered_at timestamptz not null default now(),
    last_seen_at timestamptz,
    updated_at timestamptz not null default now(),
    constraint store_devices_device_id_unique unique (device_id)
);

create index if not exists idx_store_devices_store
    on public.store_devices(store_id);

create index if not exists idx_store_devices_active_store
    on public.store_devices(store_id, active);

create table if not exists public.store_device_registration_codes (
    id uuid primary key default gen_random_uuid(),
    store_id uuid not null references public.stores(id) on delete cascade,
    registration_code text not null unique,
    created_at timestamptz not null default now(),
    expires_at timestamptz not null,
    used_at timestamptz,
    used_device_id text
);

create index if not exists idx_store_device_registration_codes_store
    on public.store_device_registration_codes(store_id);

-- One Admin Push may be synced by several devices belonging to the same store.
-- This table records each device independently.
create table if not exists public.admin_price_update_device_state (
    id uuid primary key default gen_random_uuid(),
    update_id uuid not null references public.admin_price_updates(id) on delete cascade,
    store_id uuid not null references public.stores(id) on delete cascade,
    device_id text not null,
    status text not null default 'SYNCED'
        check (status in ('SYNCED', 'UPLOADED')),
    synced_at timestamptz not null default now(),
    uploaded_at timestamptz,
    updated_at timestamptz not null default now(),
    constraint admin_price_update_device_state_unique
        unique (update_id, device_id)
);

create index if not exists idx_admin_price_update_device_state_device
    on public.admin_price_update_device_state(device_id, status);

create index if not exists idx_admin_price_update_device_state_update_store
    on public.admin_price_update_device_state(update_id, store_id);

alter table public.store_devices enable row level security;
alter table public.store_device_registration_codes enable row level security;
alter table public.admin_price_update_device_state enable row level security;

-- These tables are accessed through security-definer RPCs only.
revoke all on table public.store_devices from anon, authenticated;
revoke all on table public.store_device_registration_codes from anon, authenticated;
revoke all on table public.admin_price_update_device_state from anon, authenticated;

-- ============================================================
-- ADMIN: GENERATE ONE-TIME REGISTRATION CODE
-- ============================================================

create or replace function public.admin_generate_store_device_code(
    p_store_code text,
    p_expires_minutes integer default 60
)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
    v_store_id uuid;
    v_code text;
begin
    if not public.is_admin() then
        raise exception 'ADMIN access required';
    end if;

    if coalesce(trim(p_store_code), '') = '' then
        raise exception 'Store code is required';
    end if;

    if coalesce(p_expires_minutes, 0) < 1
       or p_expires_minutes > 1440 then
        raise exception 'Expiry must be between 1 and 1440 minutes';
    end if;

    select id
    into v_store_id
    from public.stores
    where store_code = trim(p_store_code)
      and active = true
    limit 1;

    if v_store_id is null then
        raise exception 'Active store not found';
    end if;

    -- Do not invalidate other active codes for this store.
    -- Each physical device gets its own one-time code.
    v_code := replace(gen_random_uuid()::text, '-', '');

    insert into public.store_device_registration_codes (
        store_id,
        registration_code,
        expires_at
    )
    values (
        v_store_id,
        v_code,
        now() + make_interval(mins => p_expires_minutes)
    );

    return v_code;
end;
$$;

-- ============================================================
-- STORE: REGISTER ONE PHYSICAL DEVICE
-- ============================================================
-- A registration code is single-use.
-- Multiple devices at the same store therefore use multiple codes.
-- The same physical device_id can only belong to one store at a time.

create or replace function public.store_register_device(
    p_registration_code text,
    p_device_id text,
    p_device_name text default '',
    p_device_ip text default ''
)
returns table (
    store_code text,
    store_name text,
    device_id text,
    device_name text,
    device_ip text,
    registered_at timestamptz
)
language plpgsql
security definer
set search_path = public
as $$
declare
    v_code_id uuid;
    v_store_id uuid;
    v_store_code text;
    v_store_name text;
    v_device_row public.store_devices%rowtype;
begin
    if coalesce(trim(p_registration_code), '') = '' then
        raise exception 'Registration code is required';
    end if;

    if coalesce(trim(p_device_id), '') = '' then
        raise exception 'Device ID is required';
    end if;

    select
        c.id,
        c.store_id,
        s.store_code,
        s.store_name
    into
        v_code_id,
        v_store_id,
        v_store_code,
        v_store_name
    from public.store_device_registration_codes c
    join public.stores s
      on s.id = c.store_id
    where c.registration_code = trim(p_registration_code)
      and c.used_at is null
      and c.expires_at > now()
      and s.active = true
    for update of c;

    if v_code_id is null then
        raise exception 'Registration code is invalid, expired, or already used';
    end if;

    insert into public.store_devices (
        store_id,
        device_id,
        device_name,
        device_ip,
        active,
        registered_at,
        last_seen_at,
        updated_at
    )
    values (
        v_store_id,
        trim(p_device_id),
        coalesce(trim(p_device_name), ''),
        coalesce(trim(p_device_ip), ''),
        true,
        now(),
        now(),
        now()
    )
    on conflict on constraint store_devices_device_id_unique
    do update set
        store_id = excluded.store_id,
        device_name = excluded.device_name,
        device_ip = excluded.device_ip,
        active = true,
        last_seen_at = now(),
        updated_at = now()
    returning * into v_device_row;

    update public.store_device_registration_codes
    set used_at = now(),
        used_device_id = trim(p_device_id)
    where id = v_code_id;

    return query
    select
        v_store_code,
        v_store_name,
        v_device_row.device_id,
        v_device_row.device_name,
        v_device_row.device_ip,
        v_device_row.registered_at;
end;
$$;

-- ============================================================
-- STORE: UPDATE DEVICE PRESENCE
-- ============================================================

create or replace function public.store_touch_device(
    p_device_id text,
    p_device_ip text default '',
    p_device_name text default ''
)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
begin
    update public.store_devices
    set device_ip = coalesce(trim(p_device_ip), device_ip),
        device_name = case
            when coalesce(trim(p_device_name), '') = '' then device_name
            else trim(p_device_name)
        end,
        last_seen_at = now(),
        updated_at = now()
    where device_id = trim(p_device_id)
      and active = true;

    return found;
end;
$$;

-- ============================================================
-- ADMIN: DEVICE LIST
-- ============================================================

create or replace view public.admin_store_devices as
select
    d.id,
    s.store_code,
    s.store_name,
    d.device_id,
    d.device_name,
    d.device_ip,
    d.active,
    d.registered_at,
    d.last_seen_at,
    d.updated_at
from public.store_devices d
join public.stores s
  on s.id = d.store_id;

revoke all on public.admin_store_devices from anon, authenticated;
grant select on public.admin_store_devices to authenticated;

-- ============================================================
-- MULTI-DEVICE ADMIN PUSH: GET PENDING PUSHES FOR THIS DEVICE
-- ============================================================
-- IMPORTANT:
-- Do NOT use the old store-level SYNCED flag as the device acknowledgement.
-- Every physical device gets its own row in admin_price_update_device_state.

create or replace function public.store_get_pending_admin_price_updates_v2(
    p_device_id text,
    p_device_ip text default ''
)
returns table (
    update_id uuid,
    target_store_id uuid,
    created_at timestamptz,
    note text,
    item_count integer,
    items jsonb
)
language plpgsql
security definer
set search_path = public
as $$
declare
    v_store_id uuid;
begin
    select store_id
    into v_store_id
    from public.store_devices
    where device_id = trim(p_device_id)
      and active = true
    limit 1;

    if v_store_id is null then
        raise exception 'Device is not registered to an active store';
    end if;

    update public.store_devices
    set device_ip = coalesce(trim(p_device_ip), device_ip),
        last_seen_at = now(),
        updated_at = now()
    where device_id = trim(p_device_id)
      and active = true;

    return query
    select
        u.id,
        t.store_id,
        u.created_at,
        u.note,
        u.item_count,
        u.items
    from public.admin_price_updates u
    join public.admin_price_update_targets t
      on t.update_id = u.id
    where t.store_id = v_store_id
      and t.status <> 'UPLOADED'
      and not exists (
          select 1
          from public.admin_price_update_device_state ds
          where ds.update_id = u.id
            and ds.device_id = trim(p_device_id)
      )
      -- A newer local MANUAL/CSV change must not be overwritten by an
      -- older Admin Push that was still waiting in the cloud.
      --
      -- Example:
      --   Admin Push -> 90
      --   Store changes -> 69.90
      --   old 90 must NOT come back down on the next 10-second poll.
      and u.created_at > coalesce(
          (
              select max(l.changed_at)
              from public.store_price_change_log l
              where l.device_id = trim(p_device_id)
                and l.source in ('MANUAL', 'CSV')
                and exists (
                    select 1
                    from jsonb_array_elements(
                        case
                            when jsonb_typeof(u.items) = 'array'
                            then u.items
                            else '[]'::jsonb
                        end
                    ) item
                    where item ? 'plu_no'
                      and (item->>'plu_no')::integer = l.plu_no
                )
                and l.changed_at is not null
          ),
          '-infinity'::timestamptz
      )
    order by u.created_at asc;
end;
$$;

-- ============================================================
-- MULTI-DEVICE ADMIN PUSH: MARK SYNCED FOR THIS DEVICE
-- ============================================================

create or replace function public.store_mark_admin_price_updates_synced_v2(
    p_device_id text,
    p_update_ids uuid[]
)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
    v_store_id uuid;
    affected integer := 0;
begin
    select store_id
    into v_store_id
    from public.store_devices
    where device_id = trim(p_device_id)
      and active = true
    limit 1;

    if v_store_id is null then
        raise exception 'Device is not registered to an active store';
    end if;

    insert into public.admin_price_update_device_state (
        update_id,
        store_id,
        device_id,
        status,
        synced_at,
        updated_at
    )
    select
        t.update_id,
        t.store_id,
        trim(p_device_id),
        'SYNCED',
        now(),
        now()
    from public.admin_price_update_targets t
    where t.store_id = v_store_id
      and t.update_id = any(coalesce(p_update_ids, '{}'::uuid[]))
      and t.status <> 'UPLOADED'
    on conflict (update_id, device_id)
    do nothing;

    get diagnostics affected = row_count;

    -- Keep the legacy target status useful for existing reports.
    update public.admin_price_update_targets t
    set status = case
            when t.status = 'PENDING' then 'SYNCED'
            else t.status
        end,
        synced_at = coalesce(t.synced_at, now())
    where t.store_id = v_store_id
      and t.update_id = any(coalesce(p_update_ids, '{}'::uuid[]))
      and t.status = 'PENDING';

    return affected;
end;
$$;

-- ============================================================
-- MULTI-DEVICE: MARK THE CURRENT DEVICE'S PUSH AS UPLOADED
-- ============================================================
-- Called only after the physical Essae upload succeeds.
-- The store target is marked UPLOADED only when every device that was
-- registered before the Admin Push has uploaded it.
--
-- This prevents Device A uploading from falsely telling the Admin that
-- Device B's scale is also physically updated.

create or replace function public.store_mark_admin_price_updates_uploaded_v2(
    p_device_id text,
    p_update_ids uuid[]
)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
    v_store_id uuid;
    affected integer := 0;
begin
    select store_id
    into v_store_id
    from public.store_devices
    where device_id = trim(p_device_id)
      and active = true
    limit 1;

    if v_store_id is null then
        raise exception 'Device is not registered to an active store';
    end if;

    update public.admin_price_update_device_state ds
    set status = 'UPLOADED',
        uploaded_at = coalesce(ds.uploaded_at, now()),
        updated_at = now()
    where ds.store_id = v_store_id
      and ds.device_id = trim(p_device_id)
      and ds.update_id = any(coalesce(p_update_ids, '{}'::uuid[]))
      and ds.status = 'SYNCED';

    get diagnostics affected = row_count;

    -- A target is fully uploaded only when every device that existed
    -- before the push has uploaded it.
    update public.admin_price_update_targets t
    set status = 'UPLOADED',
        uploaded_at = coalesce(t.uploaded_at, now())
    from public.admin_price_updates u
    where t.update_id = u.id
      and t.store_id = v_store_id
      and t.update_id = any(coalesce(p_update_ids, '{}'::uuid[]))
      and t.status <> 'UPLOADED'
      and not exists (
          select 1
          from public.store_devices d
          where d.store_id = t.store_id
            and d.active = true
            and d.registered_at <= u.created_at
            and not exists (
                select 1
                from public.admin_price_update_device_state ds2
                where ds2.update_id = u.id
                  and ds2.device_id = d.device_id
                  and ds2.status = 'UPLOADED'
            )
      );

    return affected;
end;
$$;

-- ============================================================
-- ADMIN DEVICE STATUS REPORT
-- ============================================================

create or replace view public.admin_store_device_status as
select
    s.store_code,
    s.store_name,
    d.device_id,
    d.device_name,
    d.device_ip,
    d.active,
    d.registered_at,
    d.last_seen_at,
    d.updated_at
from public.store_devices d
join public.stores s
  on s.id = d.store_id;

revoke all on public.admin_store_device_status from anon, authenticated;
grant select on public.admin_store_device_status to authenticated;

-- ============================================================
-- FUNCTION GRANTS
-- ============================================================

revoke all on function public.admin_generate_store_device_code(text, integer) from public;
grant execute on function public.admin_generate_store_device_code(text, integer) to authenticated;

revoke all on function public.store_register_device(text, text, text, text) from public;
grant execute on function public.store_register_device(text, text, text, text) to anon, authenticated;

revoke all on function public.store_touch_device(text, text, text) from public;
grant execute on function public.store_touch_device(text, text, text) to anon, authenticated;

revoke all on function public.store_get_pending_admin_price_updates_v2(text, text) from public;
grant execute on function public.store_get_pending_admin_price_updates_v2(text, text) to anon, authenticated;

revoke all on function public.store_mark_admin_price_updates_synced_v2(text, uuid[]) from public;
grant execute on function public.store_mark_admin_price_updates_synced_v2(text, uuid[]) to anon, authenticated;

revoke all on function public.store_mark_admin_price_updates_uploaded_v2(text, uuid[]) from public;
grant execute on function public.store_mark_admin_price_updates_uploaded_v2(text, uuid[]) to anon, authenticated;

-- Existing admin mapping remains available for legacy devices.
-- New registrations should use store_devices instead.

-- END MULTI-DEVICE MIGRATION

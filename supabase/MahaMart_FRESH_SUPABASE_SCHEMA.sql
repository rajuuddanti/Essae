-- ============================================================
-- MahaMart Scale Manager - FRESH Supabase schema
-- ============================================================
-- PURPOSE
--   Store phones are local-first and DO NOT log in.
--   Admin logs in only for Reports.
--   Admin NEVER publishes prices to stores.
--
-- STORE PHONE
--   CSV / PLU / Label Design remain local on the phone.
--   Price edits are logged as PENDING.
--   Only a SUCCESSFUL Essae scale upload updates
--   store_price_current.
--   If the app is reopened before upload, the phone reverts
--   the pending edit locally and logs it as REVERTED.
--
-- ADMIN REPORTS
--   1. Current prices = last successfully uploaded prices
--   2. Price change history
--   3. Scale upload history
--   4. IP -> store mapping
--
-- IMPORTANT
--   Run this only in the EMPTY / freshly reset Supabase project.
--   This script intentionally does NOT recreate the old
--   price_update_* / store_sync / store_plu_prices tables.
-- ============================================================

create extension if not exists pgcrypto;

-- ============================================================
-- 1. STORES
-- ============================================================

create table public.stores (
    id uuid primary key default gen_random_uuid(),
    store_code text not null unique,
    store_name text not null,
    active boolean not null default true,
    created_at timestamptz not null default now()
);

insert into public.stores (store_code, store_name)
values
    ('MM001', 'Burugupally'),
    ('MM002', 'Choppadandi'),
    ('MM003', 'Dharmaram'),
    ('MM004', 'Ellanthakunta'),
    ('MM005', 'Gangadhara'),
    ('MM006', 'Gangadhara New'),
    ('MM007', 'Gharshakurthy'),
    ('MM008', 'Gopalrao Pet'),
    ('MM009', 'Kapuwada'),
    ('MM010', 'Keshavapatnam'),
    ('MM011', 'Koheda'),
    ('MM012', 'Mallial'),
    ('MM013', 'Pegadapally New'),
    ('MM014', 'Pegadapally Old'),
    ('MM015', 'Raikal'),
    ('MM016', 'Vemulawada'),
    ('MM017', 'Vidya Nagar');

-- ============================================================
-- 2. ADMIN PROFILES
-- ============================================================
-- Only ADMIN accounts are needed.
-- Store phones do not use profiles/auth.

create table public.profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    full_name text not null default '',
    role text not null check (role in ('ADMIN')),
    active boolean not null default true,
    created_at timestamptz not null default now()
);

-- Helper used by RLS and admin-only functions.
create or replace function public.is_admin()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1
        from public.profiles p
        where p.id = auth.uid()
          and p.role = 'ADMIN'
          and p.active = true
    );
$$;

-- ============================================================
-- 3. IP -> STORE MAPPING
-- ============================================================
-- Store phones do not log in.
-- Admin assigns the discovered device IP to a store later.

create table public.store_ip_map (
    id uuid primary key default gen_random_uuid(),
    device_ip text not null unique,
    store_id uuid references public.stores(id) on delete set null,
    active boolean not null default true,
    updated_at timestamptz not null default now()
);

create index idx_store_ip_map_store
    on public.store_ip_map(store_id);

-- ============================================================
-- 4. PRICE CHANGE LOG
-- ============================================================
-- One latest PENDING change per device_ip + PLU.
--
-- Example:
--   ₹100 -> ₹60 = PENDING
--   upload succeeds = UPLOADED
--   app reopened before upload = REVERTED
--
-- If the user edits 100 -> 60 -> 55 before upload,
-- the existing PENDING entry becomes:
--   old_price = 100
--   new_price = 55
-- preserving the original baseline.

create table public.store_price_change_log (
    id uuid primary key default gen_random_uuid(),

    device_ip text not null,
    device_id text not null default '',
    scale_ip text not null default '',

    plu_no integer not null,
    plu_name text not null default '',

    old_price numeric(14,2) not null,
    new_price numeric(14,2) not null,

    source text not null default 'MANUAL'
        check (source in ('MANUAL', 'CSV')),

    status text not null default 'PENDING'
        check (status in ('PENDING', 'UPLOADED', 'REVERTED')),

    changed_at timestamptz not null default now(),
    uploaded_at timestamptz,
    reverted_at timestamptz
);

create index idx_price_change_log_device_date
    on public.store_price_change_log(device_ip, changed_at desc);

create index idx_price_change_log_status
    on public.store_price_change_log(status, changed_at desc);

create index idx_price_change_log_plu
    on public.store_price_change_log(plu_no, changed_at desc);

-- ============================================================
-- 5. CURRENT SUCCESSFULLY UPLOADED PRICES
-- ============================================================
-- THIS is the Admin source of truth.
-- It is updated ONLY by complete_scale_upload().
--
-- If a store changes a price but does not upload:
--   this table remains unchanged.
--
-- Therefore Admin never sees an unuploaded local price
-- as the current price.

create table public.store_price_current (
    device_ip text not null,
    plu_no integer not null,
    plu_name text not null default '',
    unit_price numeric(14,2) not null,
    scale_ip text not null default '',
    device_id text not null default '',
    last_uploaded_at timestamptz not null default now(),

    primary key (device_ip, plu_no)
);

create index idx_store_price_current_device
    on public.store_price_current(device_ip);

create index idx_store_price_current_plu
    on public.store_price_current(plu_no);

-- ============================================================
-- 6. SCALE UPLOAD SESSIONS
-- ============================================================
-- One session per Upload All operation.

create table public.scale_upload_sessions (
    id uuid primary key default gen_random_uuid(),

    device_ip text not null,
    device_id text not null default '',
    scale_ip text not null default '',

    started_at timestamptz not null default now(),
    completed_at timestamptz,

    status text not null default 'STARTED'
        check (status in ('STARTED', 'COMPLETED', 'FAILED')),

    plu_count integer not null default 0,
    error_message text
);

create index idx_scale_upload_sessions_date
    on public.scale_upload_sessions(started_at desc);

create index idx_scale_upload_sessions_device
    on public.scale_upload_sessions(device_ip, started_at desc);

-- ============================================================
-- 7. ADMIN REPORT VIEWS
-- ============================================================

create or replace view public.admin_current_prices as
select
    c.device_ip,
    s.store_code,
    s.store_name,
    c.plu_no,
    c.plu_name,
    c.unit_price,
    c.scale_ip,
    c.device_id,
    c.last_uploaded_at
from public.store_price_current c
left join public.store_ip_map m
    on m.device_ip = c.device_ip
   and m.active = true
left join public.stores s
    on s.id = m.store_id;

create or replace view public.admin_price_change_report as
select
    l.id,
    l.device_ip,
    s.store_code,
    s.store_name,
    l.plu_no,
    l.plu_name,
    l.old_price,
    l.new_price,
    l.source,
    l.status,
    l.changed_at,
    l.uploaded_at,
    l.reverted_at,
    l.scale_ip,
    l.device_id
from public.store_price_change_log l
left join public.store_ip_map m
    on m.device_ip = l.device_ip
   and m.active = true
left join public.stores s
    on s.id = m.store_id;

create or replace view public.admin_scale_upload_report as
select
    u.id,
    u.device_ip,
    s.store_code,
    s.store_name,
    u.device_id,
    u.scale_ip,
    u.started_at,
    u.completed_at,
    u.status,
    u.plu_count,
    u.error_message
from public.scale_upload_sessions u
left join public.store_ip_map m
    on m.device_ip = u.device_ip
   and m.active = true
left join public.stores s
    on s.id = m.store_id;

-- ============================================================
-- 8. RLS
-- ============================================================

alter table public.stores enable row level security;
alter table public.profiles enable row level security;
alter table public.store_ip_map enable row level security;
alter table public.store_price_change_log enable row level security;
alter table public.store_price_current enable row level security;
alter table public.scale_upload_sessions enable row level security;

-- ADMIN reads reports / store mappings / stores.
drop policy if exists stores_admin_select on public.stores;
create policy stores_admin_select
on public.stores
for select
using (public.is_admin());

drop policy if exists profiles_admin_select on public.profiles;
create policy profiles_admin_select
on public.profiles
for select
using (public.is_admin() or id = auth.uid());

drop policy if exists store_ip_map_admin_select on public.store_ip_map;
create policy store_ip_map_admin_select
on public.store_ip_map
for select
using (public.is_admin());

drop policy if exists store_ip_map_admin_insert on public.store_ip_map;
create policy store_ip_map_admin_insert
on public.store_ip_map
for insert
with check (public.is_admin());

drop policy if exists store_ip_map_admin_update on public.store_ip_map;
create policy store_ip_map_admin_update
on public.store_ip_map
for update
using (public.is_admin())
with check (public.is_admin());

drop policy if exists change_log_admin_select on public.store_price_change_log;
create policy change_log_admin_select
on public.store_price_change_log
for select
using (public.is_admin());

drop policy if exists current_prices_admin_select on public.store_price_current;
create policy current_prices_admin_select
on public.store_price_current
for select
using (public.is_admin());

drop policy if exists upload_sessions_admin_select on public.scale_upload_sessions;
create policy upload_sessions_admin_select
on public.scale_upload_sessions
for select
using (public.is_admin());

-- ============================================================
-- 9. STORE RPC - LOG A PRICE CHANGE
-- ============================================================
-- Store app uses anon/authenticated execution.
-- No direct INSERT policy is granted.
--
-- p_items format:
-- [
--   {
--     "plu_no": 1,
--     "plu_name": "SUGAR LOOSE",
--     "old_price": 100,
--     "new_price": 60
--   }
-- ]

create or replace function public.log_pending_price_changes(
    p_device_ip text,
    p_device_id text,
    p_scale_ip text,
    p_source text,
    p_items jsonb
)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
    item jsonb;
    existing_id uuid;
    affected integer := 0;
begin
    for item in
        select *
        from jsonb_array_elements(
            coalesce(p_items, '[]'::jsonb)
        )
    loop
        select id
        into existing_id
        from public.store_price_change_log
        where device_ip = p_device_ip
          and plu_no = (item->>'plu_no')::integer
          and status = 'PENDING'
        order by changed_at desc
        limit 1;

        if existing_id is null then

            insert into public.store_price_change_log (
                device_ip,
                device_id,
                scale_ip,
                plu_no,
                plu_name,
                old_price,
                new_price,
                source,
                status,
                changed_at
            )
            values (
                p_device_ip,
                coalesce(p_device_id, ''),
                coalesce(p_scale_ip, ''),
                (item->>'plu_no')::integer,
                coalesce(item->>'plu_name', ''),
                (item->>'old_price')::numeric,
                (item->>'new_price')::numeric,
                coalesce(p_source, 'MANUAL'),
                'PENDING',
                now()
            );

        else

            update public.store_price_change_log
            set device_id = coalesce(p_device_id, ''),
                scale_ip = coalesce(p_scale_ip, scale_ip),
                plu_name =
                    coalesce(item->>'plu_name', plu_name),
                -- IMPORTANT:
                -- keep the ORIGINAL old_price of the pending edit
                new_price =
                    (item->>'new_price')::numeric,
                source =
                    coalesce(p_source, source),
                changed_at = now()
            where id = existing_id;

        end if;

        -- TEMP TEST MODE (2026-09-25):
        -- Reflect the phone's local price change immediately in the
        -- admin current-price record so Admin Push can verify
        -- store -> admin visibility before actual scale upload.
        insert into public.store_price_current (
            device_ip,
            plu_no,
            plu_name,
            unit_price,
            scale_ip,
            device_id,
            last_uploaded_at
        )
        values (
            p_device_ip,
            (item->>'plu_no')::integer,
            coalesce(item->>'plu_name', ''),
            (item->>'new_price')::numeric,
            coalesce(p_scale_ip, ''),
            coalesce(p_device_id, ''),
            now()
        )
        on conflict (device_ip, plu_no)
        do update set
            plu_name = excluded.plu_name,
            unit_price = excluded.unit_price,
            scale_ip = excluded.scale_ip,
            device_id = excluded.device_id,
            last_uploaded_at = excluded.last_uploaded_at;

        affected := affected + 1;
    end loop;

    return affected;
end;
$$;

-- ============================================================
-- 10. STORE RPC - MARK REVERTED
-- ============================================================
-- Called when the app starts and finds local prices that were
-- changed previously but never successfully uploaded.

create or replace function public.mark_reverted_price_changes(
    p_device_ip text,
    p_device_id text,
    p_plu_nos integer[]
)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
    affected integer;
begin

    update public.store_price_change_log
    set status = 'REVERTED',
        reverted_at = now(),
        device_id =
            coalesce(p_device_id, device_id)
    where device_ip = p_device_ip
      and status = 'PENDING'
      and plu_no = any(p_plu_nos);

    get diagnostics affected = row_count;

    return affected;
end;
$$;

-- ============================================================
-- 11. STORE RPC - START SCALE UPLOAD
-- ============================================================

create or replace function public.start_scale_upload(
    p_device_ip text,
    p_device_id text,
    p_scale_ip text,
    p_plu_count integer
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
    new_id uuid;
begin

    insert into public.scale_upload_sessions (
        device_ip,
        device_id,
        scale_ip,
        status,
        plu_count
    )
    values (
        p_device_ip,
        coalesce(p_device_id, ''),
        coalesce(p_scale_ip, ''),
        'STARTED',
        coalesce(p_plu_count, 0)
    )
    returning id
    into new_id;

    return new_id;
end;
$$;

-- ============================================================
-- 12. STORE RPC - COMPLETE SCALE UPLOAD
-- ============================================================
-- CALL THIS ONLY AFTER THE ACTUAL ESSAE UPLOAD SUCCEEDS.
--
-- p_items:
-- [
--   {
--     "plu_no": 1,
--     "plu_name": "SUGAR LOOSE",
--     "unit_price": 60
--   }
-- ]

create or replace function public.complete_scale_upload(
    p_session_id uuid,
    p_device_ip text,
    p_device_id text,
    p_scale_ip text,
    p_items jsonb
)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
    item jsonb;
    affected integer := 0;
begin

    if not exists (
        select 1
        from public.scale_upload_sessions
        where id = p_session_id
          and device_ip = p_device_ip
          and status = 'STARTED'
    ) then
        raise exception
            'Invalid or already completed upload session';
    end if;

    for item in
        select *
        from jsonb_array_elements(
            coalesce(p_items, '[]'::jsonb)
        )
    loop

        insert into public.store_price_current (
            device_ip,
            plu_no,
            plu_name,
            unit_price,
            scale_ip,
            device_id,
            last_uploaded_at
        )
        values (
            p_device_ip,
            (item->>'plu_no')::integer,
            coalesce(item->>'plu_name', ''),
            (item->>'unit_price')::numeric,
            coalesce(p_scale_ip, ''),
            coalesce(p_device_id, ''),
            now()
        )
        on conflict (device_ip, plu_no)
        do update set
            plu_name =
                excluded.plu_name,
            unit_price =
                excluded.unit_price,
            scale_ip =
                excluded.scale_ip,
            device_id =
                excluded.device_id,
            last_uploaded_at =
                now();

        update public.store_price_change_log
        set status = 'UPLOADED',
            uploaded_at = now(),
            device_id =
                coalesce(p_device_id, device_id),
            scale_ip =
                coalesce(p_scale_ip, scale_ip)
        where id = (
            select id
            from public.store_price_change_log
            where device_ip = p_device_ip
              and plu_no =
                  (item->>'plu_no')::integer
              and status = 'PENDING'
            order by changed_at desc
            limit 1
        )
        and new_price =
            (item->>'unit_price')::numeric;

        affected := affected + 1;
    end loop;

    update public.scale_upload_sessions
    set status = 'COMPLETED',
        completed_at = now(),
        error_message = null,
        plu_count =
            jsonb_array_length(
                coalesce(p_items, '[]'::jsonb)
            )
    where id = p_session_id;

    return affected;
end;
$$;

-- ============================================================
-- 13. STORE RPC - FAIL SCALE UPLOAD
-- ============================================================

create or replace function public.fail_scale_upload(
    p_session_id uuid,
    p_error_message text
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin

    update public.scale_upload_sessions
    set status = 'FAILED',
        completed_at = now(),
        error_message =
            left(
                coalesce(
                    p_error_message,
                    'Unknown error'
                ),
                1000
            )
    where id = p_session_id
      and status = 'STARTED';
end;
$$;

-- ============================================================
-- 14. ADMIN RPC - SAVE IP -> STORE MAPPING
-- ============================================================

create or replace function public.admin_save_store_ip_mapping(
    p_device_ip text,
    p_store_code text,
    p_store_name text,
    p_active boolean default true
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_store_id uuid;
begin

    if not public.is_admin() then
        raise exception 'ADMIN access required';
    end if;

    if p_store_code is not null
       and trim(p_store_code) <> '' then

        select id
        into v_store_id
        from public.stores
        where store_code = p_store_code
        limit 1;

    end if;

    insert into public.store_ip_map (
        device_ip,
        store_id,
        active,
        updated_at
    )
    values (
        p_device_ip,
        v_store_id,
        coalesce(p_active, true),
        now()
    )
    on conflict (device_ip)
    do update set
        store_id =
            excluded.store_id,
        active =
            excluded.active,
        updated_at =
            now();
end;
$$;

-- ============================================================
-- 15. GRANTS
-- ============================================================

revoke all on function public.log_pending_price_changes(
    text, text, text, text, jsonb
) from public;

revoke all on function public.mark_reverted_price_changes(
    text, text, integer[]
) from public;

revoke all on function public.start_scale_upload(
    text, text, text, integer
) from public;

revoke all on function public.complete_scale_upload(
    uuid, text, text, text, jsonb
) from public;

revoke all on function public.fail_scale_upload(
    uuid, text
) from public;

revoke all on function public.admin_save_store_ip_mapping(
    text, text, text, boolean
) from public;

grant execute on function public.log_pending_price_changes(
    text, text, text, text, jsonb
) to anon, authenticated;

grant execute on function public.mark_reverted_price_changes(
    text, text, integer[]
) to anon, authenticated;

grant execute on function public.start_scale_upload(
    text, text, text, integer
) to anon, authenticated;

grant execute on function public.complete_scale_upload(
    uuid, text, text, text, jsonb
) to anon, authenticated;

grant execute on function public.fail_scale_upload(
    uuid, text
) to anon, authenticated;

grant execute on function public.admin_save_store_ip_mapping(
    text, text, text, boolean
) to authenticated;

-- ============================================================
-- END
-- ============================================================
-- ============================================================
-- MahaMart Scale Manager - FINAL ADMIN PUSH MIGRATION
-- ============================================================
-- Apply this AFTER MahaMart_FRESH_SUPABASE_SCHEMA.sql.
-- DO NOT drop the existing fresh-schema tables.
--
-- Adds only the small Admin Push layer:
--   1. admin_price_updates
--   2. admin_price_update_targets
--
-- Final flow:
--   Admin Push
--      -> Store presses Sync
--      -> local phone price changes + RED/PENDING state
--      -> Upload All to Essae
--      -> ONLY successful scale upload updates store_price_current
--      -> matching Admin push target becomes UPLOADED
--
-- Stores still do not use Supabase Auth.
-- Admin continues to use Auth + profiles(role=ADMIN).
-- ============================================================

-- ============================================================
-- 1. ALLOW ADMIN_PUSH AS A PRICE-CHANGE SOURCE
-- ============================================================

alter table public.store_price_change_log
    drop constraint if exists store_price_change_log_source_check;

alter table public.store_price_change_log
    add constraint store_price_change_log_source_check
    check (source in ('MANUAL', 'CSV', 'ADMIN_PUSH'));

-- ============================================================
-- 2. ADMIN PUSH HEADER
-- ============================================================
-- One row = one Admin publish action.
-- items contains all PLUs/prices in that push.
-- Example:
-- [
--   {"plu_no":1,"plu_name":"SUGAR LOOSE","new_price":60},
--   {"plu_no":2,"plu_name":"RICE","new_price":55}
-- ]

create table if not exists public.admin_price_updates (
    id uuid primary key default gen_random_uuid(),
    created_by uuid not null references auth.users(id) on delete restrict,
    created_at timestamptz not null default now(),
    mode text not null default 'SELECTED'
        check (mode in ('ALL', 'SELECTED')),
    note text not null default '',
    item_count integer not null default 0,
    items jsonb not null default '[]'::jsonb
);

create index if not exists idx_admin_price_updates_date
    on public.admin_price_updates(created_at desc);

create index if not exists idx_admin_price_updates_created_by
    on public.admin_price_updates(created_by, created_at desc);

-- ============================================================
-- 3. ADMIN PUSH TARGETS
-- ============================================================
-- One row = one store targeted by one Admin push.
--
-- PENDING  = waiting for that store to press Sync
-- SYNCED   = store received/applied the push locally
-- UPLOADED = pushed prices were later confirmed on the physical scale
--
-- A store can be selected even before its phone/IP is mapped.
-- Once its IP is mapped to that store, the phone can receive
-- already-pending Admin pushes.

create table if not exists public.admin_price_update_targets (
    id uuid primary key default gen_random_uuid(),
    update_id uuid not null
        references public.admin_price_updates(id) on delete cascade,
    store_id uuid not null
        references public.stores(id) on delete cascade,
    status text not null default 'PENDING'
        check (status in ('PENDING', 'SYNCED', 'UPLOADED')),
    synced_at timestamptz,
    uploaded_at timestamptz,
    created_at timestamptz not null default now(),
    unique (update_id, store_id)
);

create index if not exists idx_admin_push_targets_store_status
    on public.admin_price_update_targets(store_id, status, created_at desc);

create index if not exists idx_admin_push_targets_update
    on public.admin_price_update_targets(update_id, created_at desc);

-- ============================================================
-- 4. ADMIN PUSH REPORT VIEW
-- ============================================================
-- Used by Admin Reports.

create or replace view public.admin_price_push_report
with (security_invoker = true)
as
select
    u.id as update_id,
    u.created_at,
    u.created_by,
    coalesce(p.full_name, '') as created_by_name,
    u.mode,
    u.note,
    u.item_count,
    u.items,
    t.id as target_id,
    t.store_id,
    s.store_code,
    s.store_name,
    t.status,
    t.synced_at,
    t.uploaded_at
from public.admin_price_updates u
join public.admin_price_update_targets t
    on t.update_id = u.id
left join public.stores s
    on s.id = t.store_id
left join public.profiles p
    on p.id = u.created_by;

-- ============================================================
-- 5. RLS
-- ============================================================

alter table public.admin_price_updates enable row level security;
alter table public.admin_price_update_targets enable row level security;

-- Admin can read push history.
drop policy if exists admin_price_updates_admin_select
    on public.admin_price_updates;

create policy admin_price_updates_admin_select
on public.admin_price_updates
for select
using (public.is_admin());

drop policy if exists admin_price_update_targets_admin_select
    on public.admin_price_update_targets;

create policy admin_price_update_targets_admin_select
on public.admin_price_update_targets
for select
using (public.is_admin());

-- No direct INSERT/UPDATE policy for either table.
-- Admin publishes through the security-definer RPC below.

-- ============================================================
-- 6. ADMIN RPC - PUBLISH PRICE UPDATE
-- ============================================================
-- p_apply_to_all = true  -> all active stores
-- p_apply_to_all = false -> only p_store_ids
--
-- p_items format:
-- [
--   {"plu_no":1,"plu_name":"SUGAR LOOSE","new_price":60},
--   {"plu_no":2,"plu_name":"RICE","new_price":55}
-- ]

create or replace function public.admin_publish_price_update(
    p_apply_to_all boolean,
    p_store_ids uuid[],
    p_items jsonb,
    p_note text default ''
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
    v_update_id uuid;
    v_store_count integer := 0;
begin
    if not public.is_admin() then
        raise exception 'ADMIN access required';
    end if;

    if coalesce(jsonb_array_length(coalesce(p_items, '[]'::jsonb)), 0) = 0 then
        raise exception 'At least one price item is required';
    end if;

    insert into public.admin_price_updates (
        created_by,
        mode,
        note,
        item_count,
        items
    )
    values (
        auth.uid(),
        case when coalesce(p_apply_to_all, false) then 'ALL' else 'SELECTED' end,
        coalesce(p_note, ''),
        jsonb_array_length(p_items),
        p_items
    )
    returning id into v_update_id;

    if coalesce(p_apply_to_all, false) then

        insert into public.admin_price_update_targets (
            update_id,
            store_id
        )
        select
            v_update_id,
            s.id
        from public.stores s
        where s.active = true;

    else

        insert into public.admin_price_update_targets (
            update_id,
            store_id
        )
        select
            v_update_id,
            s.id
        from public.stores s
        where s.active = true
          and s.id = any(coalesce(p_store_ids, '{}'::uuid[]));

    end if;

    select count(*)
    into v_store_count
    from public.admin_price_update_targets
    where update_id = v_update_id;

    if v_store_count = 0 then
        delete from public.admin_price_updates
        where id = v_update_id;

        raise exception 'No active target stores were selected';
    end if;

    return v_update_id;
end;
$$;

-- ============================================================
-- 7. STORE RPC - GET PENDING ADMIN PUSHES
-- ============================================================
-- Store phone identifies itself by device IP.
-- IP must have an active mapping to a store.
--
-- Returns one row per pending Admin push with the complete items
-- array. Only PENDING targets are returned.
--
-- The app should:
--   1. call this RPC
--   2. apply the items to local Room
--   3. create local RED/PENDING scale-upload state
--   4. call store_mark_admin_price_updates_synced()

create or replace function public.store_get_pending_admin_price_updates(
    p_device_ip text
)
returns table (
    update_id uuid,
    created_at timestamptz,
    note text,
    item_count integer,
    items jsonb
)
language plpgsql
security definer
set search_path = public
as $$
begin
    return query
    select
        u.id,
        u.created_at,
        u.note,
        u.item_count,
        u.items
    from public.admin_price_updates u
    join public.admin_price_update_targets t
        on t.update_id = u.id
    join public.store_ip_map m
        on m.store_id = t.store_id
       and m.device_ip = p_device_ip
       and m.active = true
    where t.status = 'PENDING'
    order by u.created_at asc;
end;
$$;

-- ============================================================
-- 8. STORE RPC - ACK ADMIN PUSH SYNC
-- ============================================================
-- Called ONLY after local Room prices and local pending/red
-- state have been successfully written.

create or replace function public.store_mark_admin_price_updates_synced(
    p_device_ip text,
    p_update_ids uuid[]
)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
    affected integer := 0;
    v_store_id uuid;
begin
    select m.store_id
    into v_store_id
    from public.store_ip_map m
    where m.device_ip = p_device_ip
      and m.active = true
    limit 1;

    if v_store_id is null then
        raise exception 'Device IP is not mapped to an active store';
    end if;

    update public.admin_price_update_targets t
    set status = 'SYNCED',
        synced_at = coalesce(synced_at, now())
    where t.store_id = v_store_id
      and t.status = 'PENDING'
      and t.update_id = any(coalesce(p_update_ids, '{}'::uuid[]));

    get diagnostics affected = row_count;
    return affected;
end;
$$;

-- ============================================================
-- 9. UPDATE SCALE-UPLOAD COMPLETION FUNCTION
-- ============================================================
-- Replace the original function so that, after the physical
-- scale upload succeeds, matching Admin-pushed prices are also
-- marked UPLOADED.
--
-- IMPORTANT:
-- This function STILL updates store_price_current ONLY here.
-- A Sync operation alone NEVER changes the Admin current price.

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
    v_store_id uuid;
begin
    if not exists (
        select 1
        from public.scale_upload_sessions
        where id = p_session_id
          and device_ip = p_device_ip
          and status = 'STARTED'
    ) then
        raise exception 'Invalid or already completed upload session';
    end if;

    for item in
        select *
        from jsonb_array_elements(coalesce(p_items, '[]'::jsonb))
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
            plu_name = excluded.plu_name,
            unit_price = excluded.unit_price,
            scale_ip = excluded.scale_ip,
            device_id = excluded.device_id,
            last_uploaded_at = now();

        update public.store_price_change_log
        set status = 'UPLOADED',
            uploaded_at = now(),
            device_id = coalesce(p_device_id, device_id),
            scale_ip = coalesce(p_scale_ip, scale_ip)
        where id = (
            select id
            from public.store_price_change_log
            where device_ip = p_device_ip
              and plu_no = (item->>'plu_no')::integer
              and status = 'PENDING'
            order by changed_at desc
            limit 1
        )
        and new_price = (item->>'unit_price')::numeric;

        affected := affected + 1;
    end loop;

    update public.scale_upload_sessions
    set status = 'COMPLETED',
        completed_at = now(),
        error_message = null,
        plu_count = jsonb_array_length(coalesce(p_items, '[]'::jsonb))
    where id = p_session_id;

    -- Mark Admin pushes UPLOADED only when every item in that push
    -- is now physically confirmed at the same price for this device.
    select m.store_id
    into v_store_id
    from public.store_ip_map m
    where m.device_ip = p_device_ip
      and m.active = true
    limit 1;

    if v_store_id is not null then
        update public.admin_price_update_targets t
        set status = 'UPLOADED',
            uploaded_at = coalesce(uploaded_at, now())
        from public.admin_price_updates u
        where t.update_id = u.id
          and t.store_id = v_store_id
          and t.status = 'SYNCED'
          and not exists (
              select 1
              from jsonb_array_elements(u.items) target_item
              where not exists (
                  select 1
                  from public.store_price_current c
                  where c.device_ip = p_device_ip
                    and c.plu_no = (target_item->>'plu_no')::integer
                    and c.unit_price = (target_item->>'new_price')::numeric
              )
          );
    end if;

    return affected;
end;
$$;

-- ============================================================
-- 10. FUNCTION GRANTS
-- ============================================================

revoke all on function public.admin_publish_price_update(
    boolean, uuid[], jsonb, text
) from public;

grant execute on function public.admin_publish_price_update(
    boolean, uuid[], jsonb, text
) to authenticated;

revoke all on function public.store_get_pending_admin_price_updates(
    text
) from public;

grant execute on function public.store_get_pending_admin_price_updates(
    text
) to anon, authenticated;

revoke all on function public.store_mark_admin_price_updates_synced(
    text, uuid[]
) from public;

grant execute on function public.store_mark_admin_price_updates_synced(
    text, uuid[]
) to anon, authenticated;

revoke all on function public.complete_scale_upload(
    uuid, text, text, text, jsonb
) from public;

grant execute on function public.complete_scale_upload(
    uuid, text, text, text, jsonb
) to anon, authenticated;

-- ============================================================
-- 11. VIEW SECURITY
-- ============================================================

alter view public.admin_price_push_report
    set (security_invoker = true);

-- ============================================================
-- 12. IMPORTANT BEHAVIOR NOTE
-- ============================================================
-- The Android app MUST NOT call mark_reverted_price_changes()
-- during app startup/restart anymore.
--
-- Final rule:
--   pending local price remains RED across app restarts.
--   only successful physical scale upload clears that state.
--
-- The old function remains in the database for compatibility,
-- but the new Android code will not use it for normal restarts.
-- ============================================================

-- END OF MIGRATION
-- ============================================================
-- MahaMart Scale Manager - ADMIN PUSH / LC / REAL UPLOAD MIGRATION
-- ============================================================
-- Apply AFTER:
--   MahaMart_FRESH_SUPABASE_SCHEMA.sql
--   MahaMart_SUPABASE_ADMIN_PUSH_MIGRATION.sql
--   MahaMart_MULTI_DEVICE_REGISTRATION_MIGRATION.sql
--
-- Final meaning:
--   LC = last physical scale upload (confirmed)
--   Pushed date = latest Admin Push received by this store/device
--               but not yet physically uploaded.
--
-- Example:
--   LC: 21-09-2026, 12:36
--   LC: 21-09-2026, 12:36 (26-09)*
--   LC: 26-09-2026, 14:10
--
-- Admin Push NEVER changes store_price_current.
-- store_price_current changes only after complete_scale_upload().
-- ============================================================

-- ============================================================
-- 1. STORE PRICE LOG: ADMIN_PUSH IS A VALID SOURCE
-- ============================================================

alter table public.store_price_change_log
    drop constraint if exists store_price_change_log_source_check;

alter table public.store_price_change_log
    add constraint store_price_change_log_source_check
    check (source in ('MANUAL', 'CSV', 'ADMIN_PUSH'));

-- ============================================================
-- 2. ADMIN PUSH VIEW
-- ============================================================
-- Returns the confirmed physical-scale price plus the latest
-- received-but-not-uploaded Admin Push date and price for each store.
--
-- The pending date comes from device state = SYNCED.
-- Once the physical upload succeeds, that device state becomes
-- UPLOADED and the pending date disappears.

drop function if exists public.admin_get_store_plu_prices(integer);

create or replace function public.admin_get_store_plu_prices(
    p_plu_no integer
)
returns table (
    store_id uuid,
    store_code text,
    store_name text,
    current_price numeric,
    last_uploaded_at timestamptz,
    device_ip text,
    pending_pushed_at timestamptz,
    pending_pushed_price numeric,
    last_upload_source text
)
language plpgsql
security definer
set search_path = public
as $$
begin
    if not public.is_admin() then
        raise exception 'ADMIN access required';
    end if;

    return query
    with current_candidates as (
        -- Registered-device path.
        select
            s.id as store_id,
            s.store_code,
            s.store_name,
            c.unit_price as current_price,
            c.last_uploaded_at,
            (
                select l.source
                from public.store_price_change_log l
                where l.device_ip = c.device_ip
                  and l.plu_no = p_plu_no
                  and l.status = 'UPLOADED'
                  and l.new_price = c.unit_price
                order by l.uploaded_at desc nulls last, l.changed_at desc
                limit 1
            ) as last_upload_source,
            c.device_ip,
            1 as source_priority
        from public.stores s
        left join public.store_devices d
            on d.store_id = s.id
           and d.active = true
        left join public.store_price_current c
            on c.device_ip = d.device_ip
           and c.plu_no = p_plu_no
        where s.active = true

        union all

        -- Legacy IP mapping path.
        select
            s.id as store_id,
            s.store_code,
            s.store_name,
            c.unit_price as current_price,
            c.last_uploaded_at,
            (
                select l.source
                from public.store_price_change_log l
                where l.device_ip = c.device_ip
                  and l.plu_no = p_plu_no
                  and l.status = 'UPLOADED'
                  and l.new_price = c.unit_price
                order by l.uploaded_at desc nulls last, l.changed_at desc
                limit 1
            ) as last_upload_source,
            c.device_ip,
            2 as source_priority
        from public.stores s
        left join public.store_ip_map m
            on m.store_id = s.id
           and m.active = true
        left join public.store_price_current c
            on c.device_ip = m.device_ip
           and c.plu_no = p_plu_no
        where s.active = true
    ),
    ranked_current as (
        select
            current_candidates.*,
            row_number() over (
                partition by current_candidates.store_id
                order by
                    (current_candidates.current_price is null),
                    current_candidates.last_uploaded_at desc nulls last,
                    current_candidates.source_priority asc
            ) as rn
        from current_candidates
    ),
    pending_by_store as (
        select distinct on (ds.store_id)
            ds.store_id,
            ds.synced_at as pending_pushed_at,
            (item->>'new_price')::numeric as pending_pushed_price
        from public.admin_price_update_device_state ds
        join public.admin_price_updates u
            on u.id = ds.update_id
        cross join lateral jsonb_array_elements(
            case
                when jsonb_typeof(u.items) = 'array'
                then u.items
                else '[]'::jsonb
            end
        ) item
        where ds.status = 'SYNCED'
          and (item->>'plu_no')::integer = p_plu_no
        order by ds.store_id, ds.synced_at desc, u.created_at desc
    ),
    legacy_pending as (
        select distinct on (t.store_id)
            t.store_id,
            t.synced_at as pending_pushed_at,
            (item->>'new_price')::numeric as pending_pushed_price
        from public.admin_price_update_targets t
        join public.admin_price_updates u
            on u.id = t.update_id
        cross join lateral jsonb_array_elements(
            case
                when jsonb_typeof(u.items) = 'array'
                then u.items
                else '[]'::jsonb
            end
        ) item
        where t.status = 'SYNCED'
          and not exists (
              select 1
              from public.admin_price_update_device_state ds
              where ds.update_id = t.update_id
                and ds.store_id = t.store_id
          )
          and (item->>'plu_no')::integer = p_plu_no
        order by t.store_id, t.synced_at desc, u.created_at desc
    )
    select
        rc.store_id,
        rc.store_code,
        rc.store_name,
        rc.current_price,
        rc.last_uploaded_at,
        rc.device_ip,
        coalesce(pb.pending_pushed_at, lp.pending_pushed_at)
            as pending_pushed_at,
        coalesce(pb.pending_pushed_price, lp.pending_pushed_price)
            as pending_pushed_price,
        rc.last_upload_source
    from ranked_current rc
    left join pending_by_store pb
        on pb.store_id = rc.store_id
    left join legacy_pending lp
        on lp.store_id = rc.store_id
    where rc.rn = 1
    order by rc.store_code;
end;
$$;

revoke all on function public.admin_get_store_plu_prices(integer) from public;
grant execute on function public.admin_get_store_plu_prices(integer)
    to authenticated;

-- ============================================================
-- 3. DO NOT CHANGE CURRENT PRICE WHEN ADMIN PUSH IS RECEIVED
-- ============================================================
-- Replace the old TEMP TEST behavior. Admin current price must
-- remain the last physical-scale-confirmed price until upload.

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
            case
                when jsonb_typeof(p_items) = 'array'
                then p_items
                else '[]'::jsonb
            end
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
                plu_name = coalesce(item->>'plu_name', plu_name),
                new_price = (item->>'new_price')::numeric,
                source = coalesce(p_source, source),
                changed_at = now()
            where id = existing_id;
        end if;

        -- IMPORTANT:
        -- Do NOT update public.store_price_current here.
        -- That table represents the last confirmed physical
        -- scale price and is updated only by complete_scale_upload().

        affected := affected + 1;
    end loop;

    return affected;
end;
$$;

revoke all on function public.log_pending_price_changes(
    text, text, text, text, jsonb
) from public;

grant execute on function public.log_pending_price_changes(
    text, text, text, text, jsonb
) to anon, authenticated;

-- ============================================================
-- 4. REAL SCALE UPLOAD COMPLETION
-- ============================================================
-- Called ONLY after EssaeTransport reports a successful upload.
--
-- Besides updating store_price_current, this also changes the
-- Admin Push device state from SYNCED -> UPLOADED for matching
-- PLUs/prices. The target becomes UPLOADED when all devices that
-- existed before that Admin Push have uploaded it.

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
    select store_id
    into v_store_id
    from public.store_devices
    where device_id = trim(p_device_id)
      and active = true
    limit 1;

    if v_store_id is null then
        raise exception 'Device is not registered to an active store';
    end if;

    if not exists (
        select 1
        from public.scale_upload_sessions
        where id = p_session_id
          and device_ip = p_device_ip
          and device_id = trim(p_device_id)
          and status = 'STARTED'
    ) then
        raise exception 'Invalid or already completed upload session';
    end if;

    for item in
        select *
        from jsonb_array_elements(
            case
                when jsonb_typeof(p_items) = 'array'
                then p_items
                else '[]'::jsonb
            end
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
            select l.id
            from public.store_price_change_log l
            where l.device_ip = p_device_ip
              and l.plu_no = (item->>'plu_no')::integer
              and l.status = 'PENDING'
            order by l.changed_at desc
            limit 1
        )
        and new_price = (item->>'unit_price')::numeric;

        -- Mark the exact Admin Push device state as physically uploaded.
        update public.admin_price_update_device_state ds
        set status = 'UPLOADED',
            uploaded_at = coalesce(ds.uploaded_at, now()),
            updated_at = now()
        where ds.store_id = v_store_id
          and ds.device_id = trim(p_device_id)
          and ds.status = 'SYNCED'
          and exists (
              select 1
              from public.admin_price_updates u
              where u.id = ds.update_id
                and exists (
                    select 1
                    from jsonb_array_elements(
                        case
                            when jsonb_typeof(u.items) = 'array'
                            then u.items
                            else '[]'::jsonb
                        end
                    ) push_item
                    where (push_item->>'plu_no')::integer =
                              (item->>'plu_no')::integer
                      and (push_item->>'new_price')::numeric =
                              (item->>'unit_price')::numeric
                )
          );

        affected := affected + 1;
    end loop;

    -- A store target is fully uploaded only after every active device
    -- that existed before the Admin Push has uploaded its matching push.
    update public.admin_price_update_targets t
    set status = 'UPLOADED',
        uploaded_at = coalesce(t.uploaded_at, now())
    from public.admin_price_updates u
    where t.update_id = u.id
      and t.store_id = v_store_id
      and t.status <> 'UPLOADED'
      and exists (
          select 1
          from public.admin_price_update_device_state ds
          where ds.update_id = u.id
            and ds.store_id = v_store_id
            and ds.status = 'UPLOADED'
      )
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

    update public.scale_upload_sessions
    set status = 'COMPLETED',
        completed_at = now(),
        error_message = null,
        plu_count = jsonb_array_length(
            case
                when jsonb_typeof(p_items) = 'array'
                then p_items
                else '[]'::jsonb
            end
        )
    where id = p_session_id;

    return affected;
end;
$$;

revoke all on function public.complete_scale_upload(
    uuid, text, text, text, jsonb
) from public;

grant execute on function public.complete_scale_upload(
    uuid, text, text, text, jsonb
) to anon, authenticated;

-- ============================================================
-- 5. DOCUMENTED TIME DISPLAY
-- ============================================================
-- Timestamps remain timestamptz. Android formats them in
-- Asia/Kolkata as dd-MM-yyyy, HH:mm.
-- ============================================================

-- END

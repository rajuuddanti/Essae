-- ============================================================
-- MahaMart Scale Manager - ADMIN PUSH STORE PRICE VIEW
-- ============================================================
-- current_price / last_uploaded_at = last confirmed physical scale upload
-- pending_pushed_at = latest Admin Push received by a store/device
-- but not yet physically uploaded.
--
-- Android displays:
--   LC: 21-09-2026, 12:36
--   LC: 21-09-2026, 12:36 (26-09)*
--   LC: 26-09-2026, 14:10
-- ============================================================

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
    pending_pushed_at timestamptz
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
        select
            ds.store_id,
            max(ds.synced_at) as pending_pushed_at
        from public.admin_price_update_device_state ds
        join public.admin_price_updates u
            on u.id = ds.update_id
        where ds.status = 'SYNCED'
          and exists (
              select 1
              from jsonb_array_elements(
                  case
                      when jsonb_typeof(u.items) = 'array'
                      then u.items
                      else '[]'::jsonb
                  end
              ) item
              where (item->>'plu_no')::integer = p_plu_no
          )
        group by ds.store_id
    ),
    legacy_pending as (
        select
            t.store_id,
            max(t.synced_at) as pending_pushed_at
        from public.admin_price_update_targets t
        join public.admin_price_updates u
            on u.id = t.update_id
        where t.status = 'SYNCED'
          and not exists (
              select 1
              from public.admin_price_update_device_state ds
              where ds.update_id = t.update_id
                and ds.store_id = t.store_id
          )
          and exists (
              select 1
              from jsonb_array_elements(
                  case
                      when jsonb_typeof(u.items) = 'array'
                      then u.items
                      else '[]'::jsonb
                  end
              ) item
              where (item->>'plu_no')::integer = p_plu_no
          )
        group by t.store_id
    )
    select
        rc.store_id,
        rc.store_code,
        rc.store_name,
        rc.current_price,
        rc.last_uploaded_at,
        rc.device_ip,
        coalesce(pb.pending_pushed_at, lp.pending_pushed_at)
            as pending_pushed_at
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

-- END OF MIGRATION

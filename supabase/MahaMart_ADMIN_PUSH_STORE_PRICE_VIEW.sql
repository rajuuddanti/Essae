-- ============================================================
-- MahaMart Scale Manager - ADMIN PUSH STORE PRICE VIEW
-- ============================================================
-- Shows the latest known confirmed scale price for one PLU
-- in every active store.
--
-- Source priority:
--   1. Active registered store devices
--   2. Legacy store_ip_map
--
-- This is READ ONLY for Admin Push. It does not change prices.
-- The returned current_price is the last uploaded/confirmed
-- physical-scale price available for that store.
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
    device_ip text
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
    with candidates as (
        -- Current multi-device registration path.
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

        -- Legacy store/IP mapping compatibility.
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
    ranked as (
        select
            candidates.*,
            row_number() over (
                partition by candidates.store_id
                order by
                    (candidates.current_price is null),
                    candidates.last_uploaded_at desc nulls last,
                    candidates.source_priority asc
            ) as rn
        from candidates
    )
    select
        ranked.store_id,
        ranked.store_code,
        ranked.store_name,
        ranked.current_price,
        ranked.last_uploaded_at,
        ranked.device_ip
    from ranked
    where ranked.rn = 1
    order by ranked.store_code;
end;
$$;

revoke all on function public.admin_get_store_plu_prices(integer) from public;

grant execute on function public.admin_get_store_plu_prices(integer)
    to authenticated;

-- END OF MIGRATION

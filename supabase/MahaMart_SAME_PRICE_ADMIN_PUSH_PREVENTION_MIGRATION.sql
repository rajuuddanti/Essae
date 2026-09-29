-- Prevent SAME-price Admin Push rows from entering RED/PENDING state.
-- Comparison is per store + PLU against Store Master Price.
-- History remains in admin_price_updates; only actionable rows reach the phone.

alter table public.store_price_change_log
    drop constraint if exists store_price_change_log_status_check;

alter table public.store_price_change_log
    add constraint store_price_change_log_status_check
    check (status in ('PENDING', 'MASTER', 'UPLOADED', 'REVERTED', 'SUPERSEDED', 'NO_CHANGE'));

create or replace function public.store_get_pending_admin_price_updates_v2(
    p_device_id text,
    p_device_token text,
    p_device_ip text default ''
)
returns table(
    update_id uuid,
    target_store_id uuid,
    created_at timestamptz,
    note text,
    item_count integer,
    items jsonb
)
language plpgsql
security definer
set search_path = 'public'
as $function$
declare
    v_store_id uuid;
begin
    v_store_id := public.verify_store_device_token(
        trim(p_device_id),
        trim(p_device_token)
    );

    update public.store_devices
    set device_ip = case
            when coalesce(trim(p_device_ip), '') = ''
                then device_ip
            else trim(p_device_ip)
        end,
        last_seen_at = now(),
        updated_at = now()
    where device_id = trim(p_device_id)
      and store_id = v_store_id
      and active = true;

    return query
    with candidate_updates as (
        select
            u.id,
            t.store_id,
            u.created_at,
            u.note,
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
    )
    select
        cu.id,
        cu.store_id,
        cu.created_at,
        cu.note,
        coalesce(actionable.item_count, 0)::integer,
        coalesce(actionable.items, '[]'::jsonb)
    from candidate_updates cu
    left join lateral (
        select
            count(*)::integer as item_count,
            jsonb_agg(item order by (item->>'plu_no')::integer) as items
        from jsonb_array_elements(
            case
                when jsonb_typeof(cu.items) = 'array'
                then cu.items
                else '[]'::jsonb
            end
        ) item
        left join public.store_price_master m
          on m.store_id = cu.store_id
         and m.plu_no = (item->>'plu_no')::integer
        where item ? 'plu_no'
          and item ? 'new_price'
          and m.master_price is distinct from
              (item->>'new_price')::numeric
    ) actionable on true
    order by cu.created_at asc;
end;
$function$;

revoke all on function public.store_get_pending_admin_price_updates_v2(
    text, text, text
) from public;

grant execute on function public.store_get_pending_admin_price_updates_v2(
    text, text, text
) to anon, authenticated;


-- Defense-in-depth: even if an older/newer client sends the full Admin
-- Push item list, SAME-price rows are never written as PENDING.
create or replace function public.log_pending_price_changes(
    p_device_ip text,
    p_device_id text,
    p_device_token text,
    p_scale_ip text,
    p_source text,
    p_items jsonb
)
returns integer
language plpgsql
security definer
set search_path = 'public'
as $function$
declare
    item jsonb;
    existing_id uuid;
    affected integer := 0;
    v_store_id uuid;
    v_device_ip text;
    v_new_price numeric;
    v_plu_no integer;
    v_master_price numeric;
begin
    v_store_id := public.verify_store_device_token(
        p_device_id,
        p_device_token
    );

    select d.device_ip
      into v_device_ip
    from public.store_devices d
    where d.device_id = trim(p_device_id)
      and d.store_id = v_store_id
      and d.active = true
    limit 1;

    v_device_ip := coalesce(
        nullif(trim(p_device_ip), ''),
        v_device_ip,
        ''
    );

    update public.store_devices
    set device_ip = v_device_ip,
        last_seen_at = now(),
        updated_at = now()
    where device_id = trim(p_device_id)
      and store_id = v_store_id
      and active = true;

    for item in
        select value
        from jsonb_array_elements(
            case
                when jsonb_typeof(p_items) = 'array'
                then p_items
                else '[]'::jsonb
            end
        )
    loop
        v_plu_no := (item->>'plu_no')::integer;
        v_new_price := (item->>'new_price')::numeric;

        if v_plu_no is null or v_new_price is null then
            continue;
        end if;

        select m.master_price
          into v_master_price
        from public.store_price_master m
        where m.store_id = v_store_id
          and m.plu_no = v_plu_no;

        -- SAME price is a valid Admin Push result, but it requires no
        -- scale action. Keep an explicit audit row so the manager can see
        -- that this SKU was reviewed and required NO CHANGE.
        if v_master_price is not distinct from v_new_price then
            update public.store_price_change_log
            set status = 'SUPERSEDED',
                reverted_at = coalesce(reverted_at, now())
            where device_id = trim(p_device_id)
              and plu_no = v_plu_no
              and status = 'PENDING';

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
                v_device_ip,
                trim(p_device_id),
                coalesce(p_scale_ip, ''),
                v_plu_no,
                coalesce(item->>'plu_name', ''),
                v_master_price,
                v_new_price,
                coalesce(p_source, 'ADMIN_PUSH'),
                'NO_CHANGE',
                now()
            );

            continue;
        end if;

        select id
          into existing_id
        from public.store_price_change_log
        where device_id = trim(p_device_id)
          and plu_no = v_plu_no
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
                v_device_ip,
                trim(p_device_id),
                coalesce(p_scale_ip, ''),
                v_plu_no,
                coalesce(item->>'plu_name', ''),
                coalesce((item->>'old_price')::numeric, v_master_price, 0),
                v_new_price,
                coalesce(p_source, 'MANUAL'),
                'PENDING',
                now()
            );
        else
            update public.store_price_change_log
            set device_ip = v_device_ip,
                device_id = trim(p_device_id),
                scale_ip = coalesce(p_scale_ip, scale_ip),
                plu_name = coalesce(item->>'plu_name', plu_name),
                new_price = v_new_price,
                source = coalesce(p_source, source),
                changed_at = now()
            where id = existing_id;
        end if;

        affected := affected + 1;
    end loop;

    return affected;
end;
$function$;

revoke all on function public.log_pending_price_changes(
    text, text, text, text, text, jsonb
) from public;

grant execute on function public.log_pending_price_changes(
    text, text, text, text, text, jsonb
) to anon, authenticated;

-- END

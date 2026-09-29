-- Fix post-scale-upload master synchronization and duplicate Admin Push completion.
-- Applies to all stores; the Vidya Nagar 133-SKU case is repaired by the
-- backfill at the end of this migration.

create or replace function public.complete_scale_upload(
    p_session_id uuid,
    p_device_ip text,
    p_device_id text,
    p_device_token text,
    p_scale_ip text,
    p_items jsonb
)
returns integer
language plpgsql
security definer
set search_path = 'public'
as $function$
declare
    item jsonb;
    affected integer := 0;
    v_store_id uuid;
    v_plu_no integer;
    v_price numeric;
    v_existing_master_source text;
    v_pending_source text;
begin
    v_store_id := public.verify_store_device_token(
        trim(p_device_id),
        trim(p_device_token)
    );

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
        select value
        from jsonb_array_elements(
            case
                when jsonb_typeof(p_items) = 'array' then p_items
                else '[]'::jsonb
            end
        )
    loop
        v_plu_no := (item->>'plu_no')::integer;
        v_price := (item->>'unit_price')::numeric;

        if v_plu_no is null or v_price is null then
            continue;
        end if;

        select m.source
          into v_existing_master_source
        from public.store_price_master m
        where m.store_id = v_store_id
          and m.plu_no = v_plu_no;

        select l.source
          into v_pending_source
        from public.store_price_change_log l
        where l.device_id = trim(p_device_id)
          and l.plu_no = v_plu_no
          and l.status = 'PENDING'
          and l.new_price = v_price
        order by l.changed_at desc
        limit 1;

        insert into public.store_price_current (
            device_ip, plu_no, plu_name, unit_price,
            scale_ip, device_id, last_uploaded_at
        )
        values (
            p_device_ip,
            v_plu_no,
            coalesce(item->>'plu_name', ''),
            v_price,
            coalesce(p_scale_ip, ''),
            trim(p_device_id),
            now()
        )
        on conflict (device_ip, plu_no)
        do update set
            plu_name = excluded.plu_name,
            unit_price = excluded.unit_price,
            scale_ip = excluded.scale_ip,
            device_id = excluded.device_id,
            last_uploaded_at = now();

        -- A successful physical upload is the confirmed Store Master Price.
        -- Preserve an existing CSV/MANUAL/ADMIN_PUSH source where available.
        insert into public.store_price_master (
            store_id, plu_no, plu_name, master_price,
            updated_at, source, device_ip, device_id
        )
        values (
            v_store_id,
            v_plu_no,
            coalesce(item->>'plu_name', ''),
            v_price,
            now(),
            coalesce(v_pending_source, v_existing_master_source, 'ADMIN_PUSH'),
            p_device_ip,
            trim(p_device_id)
        )
        on conflict (store_id, plu_no)
        do update set
            plu_name = excluded.plu_name,
            master_price = excluded.master_price,
            updated_at = now(),
            source = excluded.source,
            device_ip = excluded.device_ip,
            device_id = excluded.device_id;

        update public.store_price_change_log
        set status = 'UPLOADED',
            uploaded_at = now(),
            device_id = trim(p_device_id),
            scale_ip = coalesce(p_scale_ip, scale_ip)
        where id = (
            select l.id
            from public.store_price_change_log l
            where l.device_id = trim(p_device_id)
              and l.plu_no = v_plu_no
              and l.status = 'PENDING'
            order by l.changed_at desc
            limit 1
        )
        and new_price = v_price;

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
                            when jsonb_typeof(u.items) = 'array' then u.items
                            else '[]'::jsonb
                        end
                    ) push_item
                    where (push_item->>'plu_no')::integer = v_plu_no
                      and (push_item->>'new_price')::numeric = v_price
                )
          );

        affected := affected + 1;
    end loop;

    -- A successful physical upload resolves the store-level Admin Push.
    -- An unrelated active device must not keep the store push RED forever.
    update public.admin_price_update_targets t
    set status = 'UPLOADED',
        uploaded_at = coalesce(t.uploaded_at, now())
    from public.admin_price_updates u
    where t.update_id = u.id
      and t.store_id = v_store_id
      and t.status <> 'UPLOADED'
      and (
          exists (
              select 1
              from public.admin_price_update_device_state ds
              where ds.update_id = u.id
                and ds.store_id = v_store_id
                and ds.status = 'UPLOADED'
          )
          or not exists (
              select 1
              from jsonb_array_elements(
                  case
                      when jsonb_typeof(u.items) = 'array' then u.items
                      else '[]'::jsonb
                  end
              ) push_item
              left join public.store_price_master m
                on m.store_id = v_store_id
               and m.plu_no = (push_item->>'plu_no')::integer
              where push_item ? 'plu_no'
                and push_item ? 'new_price'
                and m.master_price is distinct from
                    (push_item->>'new_price')::numeric
          )
      );

    update public.scale_upload_sessions
    set status = 'COMPLETED',
        completed_at = now(),
        error_message = null,
        plu_count = jsonb_array_length(
            case
                when jsonb_typeof(p_items) = 'array' then p_items
                else '[]'::jsonb
            end
        )
    where id = p_session_id
      and device_id = trim(p_device_id);

    return affected;
end;
$function$;

revoke all on function public.complete_scale_upload(
    uuid, text, text, text, text, jsonb
) from public;

grant execute on function public.complete_scale_upload(
    uuid, text, text, text, text, jsonb
) to anon, authenticated;


-- Backfill Store Master from the last known physical scale price for rows
-- that were already successfully uploaded before this fix.
insert into public.store_price_master (
    store_id, plu_no, plu_name, master_price,
    updated_at, source, device_ip, device_id
)
select
    d.store_id,
    c.plu_no,
    c.plu_name,
    c.unit_price,
    coalesce(c.last_uploaded_at, now()),
    coalesce(
        (
            select l.source
            from public.store_price_change_log l
            where l.device_id = c.device_id
              and l.plu_no = c.plu_no
              and l.status = 'UPLOADED'
              and l.new_price = c.unit_price
            order by l.uploaded_at desc nulls last, l.changed_at desc
            limit 1
        ),
        coalesce(m.source, 'ADMIN_PUSH')
    ),
    c.device_ip,
    c.device_id
from public.store_price_current c
join public.store_devices d
  on d.device_id = c.device_id
left join public.store_price_master m
  on m.store_id = d.store_id
 and m.plu_no = c.plu_no
where c.unit_price is not null
  and (
      m.plu_no is null
      or m.master_price is distinct from c.unit_price
  )
on conflict (store_id, plu_no)
do update set
    plu_name = excluded.plu_name,
    master_price = excluded.master_price,
    updated_at = excluded.updated_at,
    source = excluded.source,
    device_ip = excluded.device_ip,
    device_id = excluded.device_id;


-- Any historical Admin Push whose complete item set now matches the
-- confirmed Store Master is no longer actionable.
update public.admin_price_update_targets t
set status = 'UPLOADED',
    uploaded_at = coalesce(
        t.uploaded_at,
        (
            select max(c.last_uploaded_at)
            from public.store_price_current c
            join public.store_devices d on d.device_id = c.device_id
            where d.store_id = t.store_id
        ),
        now()
    )
from public.admin_price_updates u
where t.update_id = u.id
  and t.status <> 'UPLOADED'
  and not exists (
      select 1
      from jsonb_array_elements(
          case
              when jsonb_typeof(u.items) = 'array' then u.items
              else '[]'::jsonb
          end
      ) push_item
      left join public.store_price_master m
        on m.store_id = t.store_id
       and m.plu_no = (push_item->>'plu_no')::integer
      where push_item ? 'plu_no'
        and push_item ? 'new_price'
        and m.master_price is distinct from
            (push_item->>'new_price')::numeric
  );

-- Existing duplicate RED rows are converted to NO_CHANGE when the current
-- Store Master already equals their requested price.
insert into public.store_price_change_log (
    device_ip, device_id, scale_ip, plu_no, plu_name,
    old_price, new_price, source, status, changed_at
)
select
    l.device_ip,
    l.device_id,
    l.scale_ip,
    l.plu_no,
    l.plu_name,
    m.master_price,
    l.new_price,
    l.source,
    'NO_CHANGE',
    now()
from public.store_price_change_log l
join public.store_devices d on d.device_id = l.device_id
join public.store_price_master m
  on m.store_id = d.store_id
 and m.plu_no = l.plu_no
 and m.master_price = l.new_price
where l.status = 'PENDING'
  and not exists (
      select 1
      from public.store_price_change_log n
      where n.device_id = l.device_id
        and n.plu_no = l.plu_no
        and n.new_price = l.new_price
        and n.status = 'NO_CHANGE'
        and n.changed_at >= l.changed_at
  );

update public.store_price_change_log l
set status = 'SUPERSEDED',
    reverted_at = coalesce(reverted_at, now())
where l.status = 'PENDING'
  and exists (
      select 1
      from public.store_devices d
      join public.store_price_master m
        on m.store_id = d.store_id
       and m.plu_no = l.plu_no
       and m.master_price = l.new_price
      where d.device_id = l.device_id
  );

-- END

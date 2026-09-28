-- ============================================================
-- MahaMart Scale Manager - STORE MASTER PRICE / CSV BASELINE
-- ============================================================
-- Store Master Price is the authoritative store price immediately
-- after an authorized CSV import.
--
-- Physical scale price remains in store_price_current.
-- Therefore Admin can show:
--   MASTER PRICE  = latest authorized store price
--   SCALE PRICE   = last confirmed physical scale price
--
-- CSV import:
--   device + token authorized -> master prices updated immediately
--   pending local/admin-push state for affected PLUs is superseded
--
-- Admin Push:
--   does NOT change master price when received
--   successful physical upload changes master price + scale price
-- ============================================================

create table if not exists public.store_price_master (
    store_id uuid not null references public.stores(id) on delete cascade,
    plu_no integer not null,
    plu_name text not null default '',
    master_price numeric(14,2) not null,
    updated_at timestamptz not null default now(),
    source text not null default 'CSV'
        check (source in ('CSV', 'ADMIN_PUSH', 'MANUAL')),
    device_ip text not null default '',
    device_id text not null default '',
    primary key (store_id, plu_no)
);

create index if not exists idx_store_price_master_store
    on public.store_price_master(store_id, updated_at desc);

create index if not exists idx_store_price_master_plu
    on public.store_price_master(plu_no);

alter table public.store_price_master enable row level security;

drop policy if exists store_price_master_admin_select
    on public.store_price_master;

create policy store_price_master_admin_select
on public.store_price_master
for select
using (public.is_admin());

-- Existing price log now records CSV baseline updates too.
alter table public.store_price_change_log
    drop constraint if exists store_price_change_log_status_check;

alter table public.store_price_change_log
    add constraint store_price_change_log_status_check
    check (status in ('PENDING', 'MASTER', 'UPLOADED', 'REVERTED', 'SUPERSEDED'));

-- Pending Admin Push records superseded by an authorized CSV baseline
-- must stop appearing as pending, while history remains available.
alter table public.admin_price_update_targets
    drop constraint if exists admin_price_update_targets_status_check;

alter table public.admin_price_update_targets
    add constraint admin_price_update_targets_status_check
    check (status in ('PENDING', 'SYNCED', 'UPLOADED', 'SUPERSEDED'));

alter table public.admin_price_update_device_state
    drop constraint if exists admin_price_update_device_state_status_check;

alter table public.admin_price_update_device_state
    add constraint admin_price_update_device_state_status_check
    check (status in ('SYNCED', 'UPLOADED', 'SUPERSEDED'));

create or replace function public.store_apply_csv_master_prices(
    p_device_id text,
    p_device_token text,
    p_device_ip text,
    p_items jsonb
)
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
    item jsonb;
    v_store_id uuid;
    v_old_price numeric(14,2);
    v_new_price numeric(14,2);
    v_plu_no integer;
    affected integer := 0;
begin
    v_store_id := public.verify_store_device_token(
        trim(p_device_id),
        trim(p_device_token)
    );

    if v_store_id is null then
        raise exception 'Device is not registered to an active store';
    end if;

    if jsonb_typeof(coalesce(p_items, '[]'::jsonb)) <> 'array' then
        raise exception 'CSV price items must be a JSON array';
    end if;

    for item in
        select value
        from jsonb_array_elements(p_items)
    loop
        v_plu_no := (item->>'plu_no')::integer;
        v_new_price := (item->>'unit_price')::numeric;

        if v_plu_no is null or v_new_price is null or v_new_price < 0 then
            continue;
        end if;

        select m.master_price
        into v_old_price
        from public.store_price_master m
        where m.store_id = v_store_id
          and m.plu_no = v_plu_no;

        insert into public.store_price_master (
            store_id,
            plu_no,
            plu_name,
            master_price,
            updated_at,
            source,
            device_ip,
            device_id
        )
        values (
            v_store_id,
            v_plu_no,
            coalesce(item->>'plu_name', ''),
            v_new_price,
            now(),
            'CSV',
            coalesce(p_device_ip, ''),
            trim(p_device_id)
        )
        on conflict (store_id, plu_no)
        do update set
            plu_name = excluded.plu_name,
            master_price = excluded.master_price,
            updated_at = now(),
            source = 'CSV',
            device_ip = excluded.device_ip,
            device_id = excluded.device_id;

        if v_old_price is distinct from v_new_price then
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
                coalesce(p_device_ip, ''),
                trim(p_device_id),
                '',
                v_plu_no,
                coalesce(item->>'plu_name', ''),
                coalesce(v_old_price, 0),
                v_new_price,
                'CSV',
                'MASTER',
                now()
            );
        end if;

        -- A new authorized CSV baseline supersedes any older
        -- local/cloud pending change for this PLU. History remains.
        update public.store_price_change_log l
        set status = 'SUPERSEDED',
            reverted_at = coalesce(l.reverted_at, now())
        where l.device_ip = coalesce(p_device_ip, '')
          and l.plu_no = v_plu_no
          and l.status = 'PENDING';

        update public.admin_price_update_device_state ds
        set status = 'SUPERSEDED',
            updated_at = now()
        where ds.store_id = v_store_id
          and ds.status = 'SYNCED'
          and exists (
              select 1
              from public.admin_price_updates u
              cross join lateral jsonb_array_elements(
                  case
                      when jsonb_typeof(u.items) = 'array'
                      then u.items
                      else '[]'::jsonb
                  end
              ) push_item
              where u.id = ds.update_id
                and (push_item->>'plu_no')::integer = v_plu_no
          );

        update public.admin_price_update_targets t
        set status = 'SUPERSEDED',
            uploaded_at = null
        where t.store_id = v_store_id
          and t.status in ('PENDING', 'SYNCED')
          and exists (
              select 1
              from public.admin_price_updates u
              cross join lateral jsonb_array_elements(
                  case
                      when jsonb_typeof(u.items) = 'array'
                      then u.items
                      else '[]'::jsonb
                  end
              ) push_item
              where u.id = t.update_id
                and (push_item->>'plu_no')::integer = v_plu_no
          );

        affected := affected + 1;
    end loop;

    return affected;
end;
$$;

revoke all on function public.store_apply_csv_master_prices(
    text, text, text, jsonb
) from public;

grant execute on function public.store_apply_csv_master_prices(
    text, text, text, jsonb
) to anon, authenticated;

-- Successful physical upload now also becomes the Store Master Price.
-- CSV may already have set it; upload simply confirms the physical side.
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
set search_path = ''
as $$
declare
    item jsonb;
    affected integer := 0;
    v_store_id uuid;
begin
    select d.store_id
    into v_store_id
    from public.store_devices d
    where d.device_id = trim(p_device_id)
      and d.active = true
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
        select value
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

        insert into public.store_price_master (
            store_id,
            plu_no,
            plu_name,
            master_price,
            updated_at,
            source,
            device_ip,
            device_id
        )
        values (
            v_store_id,
            (item->>'plu_no')::integer,
            coalesce(item->>'plu_name', ''),
            (item->>'unit_price')::numeric,
            now(),
            coalesce(
                (
                    select l.source
                    from public.store_price_change_log l
                    where l.device_ip = p_device_ip
                      and l.plu_no = (item->>'plu_no')::integer
                      and l.status = 'PENDING'
                      and l.new_price = (item->>'unit_price')::numeric
                    order by l.changed_at desc
                    limit 1
                ),
                (
                    select m.source
                    from public.store_price_master m
                    where m.store_id = v_store_id
                      and m.plu_no = (item->>'plu_no')::integer
                      and m.master_price = (item->>'unit_price')::numeric
                ),
                'ADMIN_PUSH'
            ),
            p_device_ip,
            trim(p_device_id)
        )
        on conflict (store_id, plu_no)
        do update set
            plu_name = excluded.plu_name,
            master_price = excluded.master_price,
            updated_at = now(),
            source = 'ADMIN_PUSH',
            device_ip = excluded.device_ip,
            device_id = excluded.device_id;

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

    update public.admin_price_update_targets t
    set status = 'UPLOADED',
        uploaded_at = coalesce(t.uploaded_at, now())
    from public.admin_price_updates u
    where t.update_id = u.id
      and t.store_id = v_store_id
      and t.status = 'SYNCED'
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

-- Admin store price view: current_price is now MASTER PRICE.
-- Physical scale price is exposed separately.
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
    last_upload_source text,
    scale_price numeric,
    scale_last_uploaded_at timestamptz
)
language plpgsql
security definer
set search_path = ''
as $$
begin
    if not public.is_admin() then
        raise exception 'ADMIN access required';
    end if;

    return query
    with scale_candidates as (
        select
            s.id as store_id,
            s.store_code,
            s.store_name,
            c.unit_price as scale_price,
            c.last_uploaded_at as scale_last_uploaded_at,
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

        select
            s.id as store_id,
            s.store_code,
            s.store_name,
            c.unit_price as scale_price,
            c.last_uploaded_at as scale_last_uploaded_at,
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
    ranked_scale as (
        select
            sc.*,
            row_number() over (
                partition by sc.store_id
                order by
                    (sc.scale_price is null),
                    sc.scale_last_uploaded_at desc nulls last,
                    sc.source_priority asc
            ) as rn
        from scale_candidates sc
    ),
    master as (
        select
            m.store_id,
            m.master_price,
            m.updated_at,
            m.source
        from public.store_price_master m
        where m.plu_no = p_plu_no
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
        s.store_id,
        s.store_code,
        s.store_name,
        coalesce(m.master_price, s.scale_price) as current_price,
        s.scale_last_uploaded_at as last_uploaded_at,
        s.device_ip,
        coalesce(pb.pending_pushed_at, lp.pending_pushed_at),
        coalesce(pb.pending_pushed_price, lp.pending_pushed_price),
        (select l.source
         from public.store_price_change_log l
         where l.device_ip = s.device_ip
           and l.plu_no = p_plu_no
           and l.status = 'UPLOADED'
           and l.new_price = s.scale_price
         order by l.uploaded_at desc nulls last, l.changed_at desc
         limit 1),
        s.scale_price,
        s.scale_last_uploaded_at
    from ranked_scale s
    left join master m
        on m.store_id = s.store_id
    left join pending_by_store pb
        on pb.store_id = s.store_id
    left join legacy_pending lp
        on lp.store_id = s.store_id
    where s.rn = 1
    order by s.store_code;
end;
$$;

revoke all on function public.admin_get_store_plu_prices(integer) from public;
grant execute on function public.admin_get_store_plu_prices(integer) to authenticated;

-- Admin current-price report now reads Store Master Price.
drop view if exists public.admin_current_prices;

create view public.admin_current_prices
with (security_invoker = true)
as
select
    m.store_id,
    m.plu_no,
    m.plu_name,
    m.master_price as unit_price,
    m.updated_at as last_updated_at,
    m.source,
    s.store_code,
    s.store_name,
    m.device_ip,
    m.device_id,
    c.unit_price as scale_price,
    c.last_uploaded_at as scale_last_uploaded_at
from public.store_price_master m
left join public.stores s
    on s.id = m.store_id
left join lateral (
    select c1.unit_price, c1.last_uploaded_at
    from public.store_price_current c1
    left join public.store_devices d1
      on d1.device_ip = c1.device_ip
     and d1.active = true
    where d1.store_id = m.store_id
      and c1.plu_no = m.plu_no
    order by c1.last_uploaded_at desc
    limit 1
) c on true;

-- Backfill Master Price for stores that already have confirmed scale prices.
insert into public.store_price_master (
    store_id,
    plu_no,
    plu_name,
    master_price,
    updated_at,
    source,
    device_ip,
    device_id
)
select
    d.store_id,
    c.plu_no,
    c.plu_name,
    c.unit_price,
    c.last_uploaded_at,
    'ADMIN_PUSH',
    c.device_ip,
    c.device_id
from public.store_price_current c
join public.store_devices d
  on d.device_ip = c.device_ip
 and d.active = true
on conflict (store_id, plu_no) do nothing;

-- END

-- DRAFT ONLY — Fix 2: make Admin Push audit logging idempotent.
-- Review and test in a non-production Supabase project before applying.
-- Does not modify historical rows; their admin_update_id remains NULL.
-- Android supplies the stable Admin Push update UUID in each p_items object.

begin;

alter table public.store_price_change_log
    add column if not exists admin_update_id uuid;

create unique index if not exists uq_store_price_change_log_admin_update
    on public.store_price_change_log(device_id, admin_update_id, plu_no)
    where admin_update_id is not null;

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
set search_path = public
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
    v_update_id uuid;
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
        v_update_id := nullif(item->>'admin_update_id', '')::uuid;

        if v_plu_no is null or v_new_price is null then
            continue;
        end if;

        -- Serialize retries for this device/update/SKU. The unique index
        -- is the final guard; the lock prevents concurrent duplicate paths.
        if v_update_id is not null then
            perform pg_advisory_xact_lock(
                hashtextextended(
                    trim(p_device_id) || ':' || v_update_id::text || ':' || v_plu_no::text,
                    0
                )
            );

            if exists (
                select 1
                from public.store_price_change_log l
                where l.device_id = trim(p_device_id)
                  and l.admin_update_id = v_update_id
                  and l.plu_no = v_plu_no
            ) then
                continue;
            end if;
        end if;

        select m.master_price
          into v_master_price
        from public.store_price_master m
        where m.store_id = v_store_id
          and m.plu_no = v_plu_no;

        -- SAME price is a valid Admin Push result, but it requires no
        -- scale action. Keep an explicit NO_CHANGE audit.
        if v_master_price is not distinct from v_new_price then
            update public.store_price_change_log
            set status = 'SUPERSEDED',
                reverted_at = coalesce(reverted_at, now())
            where device_id = trim(p_device_id)
              and plu_no = v_plu_no
              and status = 'PENDING';

            insert into public.store_price_change_log (
                device_ip, device_id, scale_ip, plu_no, plu_name,
                old_price, new_price, source, status, changed_at,
                admin_update_id
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
                now(),
                v_update_id
            );

            affected := affected + 1;
            continue;
        end if;

        if v_update_id is not null then
            -- Keep one immutable audit identity per Admin Push/SKU.
            -- Supersede an older pending value instead of overwriting its key.
            update public.store_price_change_log
            set status = 'SUPERSEDED',
                reverted_at = coalesce(reverted_at, now())
            where device_id = trim(p_device_id)
              and plu_no = v_plu_no
              and status = 'PENDING';
            existing_id := null;
        else
            select id
              into existing_id
            from public.store_price_change_log
            where device_id = trim(p_device_id)
              and plu_no = v_plu_no
              and status = 'PENDING'
            order by changed_at desc
            limit 1;
        end if;

        if existing_id is null then
            insert into public.store_price_change_log (
                device_ip, device_id, scale_ip, plu_no, plu_name,
                old_price, new_price, source, status, changed_at,
                admin_update_id
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
                now(),
                v_update_id
            );
        else
            update public.store_price_change_log
            set device_ip = v_device_ip,
                device_id = trim(p_device_id),
                scale_ip = coalesce(p_scale_ip, scale_ip),
                plu_name = coalesce(item->>'plu_name', plu_name),
                new_price = v_new_price,
                source = coalesce(p_source, source),
                changed_at = now(),
                admin_update_id = coalesce(v_update_id, admin_update_id)
            where id = existing_id;
        end if;

        affected := affected + 1;
    end loop;

    return affected;
end;
$function$;

-- Preserve the current function ACL. No grants/revokes are made here.
commit;

-- Rollback (run separately only if approved):
-- DROP INDEX IF EXISTS public.uq_store_price_change_log_admin_update;
-- ALTER TABLE public.store_price_change_log DROP COLUMN IF EXISTS admin_update_id;
-- Restore the prior function definition from the recorded production snapshot.

-- ============================================================
-- MahaMart DEVICE TOKEN SECURITY MIGRATION
-- 2026-09-27
--
-- Purpose:
--   Prevent store RPC impersonation by requiring a per-device
--   secret token in addition to the Android device ID.
--
-- Multi-device remains supported:
--   one store -> many devices -> one unique token per device.
--
-- IMPORTANT:
--   Existing registered devices do not have a token.
--   After this migration, each existing phone must be
--   re-registered with a fresh one-time Admin registration code.
-- ============================================================

create extension if not exists pgcrypto;

alter table public.store_devices
    add column if not exists device_token_hash text;

create index if not exists idx_store_devices_token_hash
    on public.store_devices(device_token_hash);

-- ------------------------------------------------------------
-- Internal device authentication helper
-- ------------------------------------------------------------
create or replace function public.verify_store_device_token(
    p_device_id text,
    p_device_token text
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
    v_store_id uuid;
begin
    if coalesce(trim(p_device_id), '') = ''
       or coalesce(trim(p_device_token), '') = '' then
        raise exception 'Device authentication required';
    end if;

    select d.store_id
      into v_store_id
    from public.store_devices d
    where d.device_id = trim(p_device_id)
      and d.active = true
      and d.device_token_hash =
          encode(digest(trim(p_device_token), 'sha256'), 'hex')
    limit 1;

    if v_store_id is null then
        raise exception 'Invalid device authentication';
    end if;

    return v_store_id;
end;
$$;

revoke all on function public.verify_store_device_token(text, text) from public;

-- ------------------------------------------------------------
-- DEVICE REGISTRATION
-- A successful one-time registration creates a fresh token.
-- The raw token is returned exactly once to the phone.
-- ------------------------------------------------------------

drop function if exists public.store_register_device(
    text, text, text, text
);

create function public.store_register_device(
    p_registration_code text,
    p_device_id text,
    p_device_name text,
    p_device_ip text default ''
)
returns table(
    store_code text,
    store_name text,
    device_id text,
    device_name text,
    device_ip text,
    registered_at timestamptz,
    device_token text
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
    v_device_token text;
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

    v_device_token := encode(gen_random_bytes(32), 'hex');

    insert into public.store_devices (
        store_id,
        device_id,
        device_name,
        device_ip,
        device_token_hash,
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
        encode(digest(v_device_token, 'sha256'), 'hex'),
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
        device_token_hash = excluded.device_token_hash,
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
        v_device_row.registered_at,
        v_device_token;
end;
$$;

revoke all on function public.store_register_device(
    text, text, text, text
) from public;

grant execute on function public.store_register_device(
    text, text, text, text
) to anon, authenticated;

-- ------------------------------------------------------------
-- DEVICE HEARTBEAT
-- ------------------------------------------------------------

drop function if exists public.store_touch_device(
    text, text, text
);

create function public.store_touch_device(
    p_device_id text,
    p_device_token text,
    p_device_ip text default '',
    p_device_name text default ''
)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
    v_store_id uuid;
begin
    v_store_id :=
        public.verify_store_device_token(
            p_device_id,
            p_device_token
        );

    update public.store_devices
    set device_ip = case
            when coalesce(trim(p_device_ip), '') = ''
                then device_ip
            else trim(p_device_ip)
        end,
        device_name = case
            when coalesce(trim(p_device_name), '') = ''
                then device_name
            else trim(p_device_name)
        end,
        last_seen_at = now(),
        updated_at = now()
    where device_id = trim(p_device_id)
      and store_id = v_store_id
      and active = true;

    return found;
end;
$$;

revoke all on function public.store_touch_device(
    text, text, text, text
) from public;

revoke all on function public.store_touch_device(
    text, text, text, text
) from public;

grant execute on function public.store_touch_device(
    text, text, text, text
) to anon, authenticated;

-- ------------------------------------------------------------
-- ADMIN PUSH: GET
-- ------------------------------------------------------------

drop function if exists public.store_get_pending_admin_price_updates_v2(
    text, text
);

create function public.store_get_pending_admin_price_updates_v2(
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
set search_path = public
as $$
declare
    v_store_id uuid;
begin
    v_store_id :=
        public.verify_store_device_token(
            p_device_id,
            p_device_token
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

revoke all on function public.store_get_pending_admin_price_updates_v2(
    text, text, text
) from public;

revoke all on function public.store_get_pending_admin_price_updates_v2(
    text, text, text
) from public;

grant execute on function public.store_get_pending_admin_price_updates_v2(
    text, text, text
) to anon, authenticated;

-- ------------------------------------------------------------
-- ADMIN PUSH: SYNC ACK
-- ------------------------------------------------------------

drop function if exists public.store_mark_admin_price_updates_synced_v2(
    text, uuid[]
);

create function public.store_mark_admin_price_updates_synced_v2(
    p_device_id text,
    p_device_token text,
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
    v_store_id :=
        public.verify_store_device_token(
            p_device_id,
            p_device_token
        );

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

revoke all on function public.store_mark_admin_price_updates_synced_v2(
    text, text, uuid[]
) from public;

revoke all on function public.store_mark_admin_price_updates_synced_v2(
    text, text, uuid[]
) from public;

grant execute on function public.store_mark_admin_price_updates_synced_v2(
    text, text, uuid[]
) to anon, authenticated;

-- ------------------------------------------------------------
-- ADMIN PUSH: PHYSICAL UPLOAD ACK
-- ------------------------------------------------------------

drop function if exists public.store_mark_admin_price_updates_uploaded_v2(
    text, uuid[]
);

create function public.store_mark_admin_price_updates_uploaded_v2(
    p_device_id text,
    p_device_token text,
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
    v_store_id :=
        public.verify_store_device_token(
            p_device_id,
            p_device_token
        );

    update public.admin_price_update_device_state ds
    set status = 'UPLOADED',
        uploaded_at = coalesce(ds.uploaded_at, now()),
        updated_at = now()
    where ds.store_id = v_store_id
      and ds.device_id = trim(p_device_id)
      and ds.update_id = any(coalesce(p_update_ids, '{}'::uuid[]))
      and ds.status = 'SYNCED';

    get diagnostics affected = row_count;

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

revoke all on function public.store_mark_admin_price_updates_uploaded_v2(
    text, text, uuid[]
) from public;

revoke all on function public.store_mark_admin_price_updates_uploaded_v2(
    text, text, uuid[]
) from public;

grant execute on function public.store_mark_admin_price_updates_uploaded_v2(
    text, text, uuid[]
) to anon, authenticated;

-- ------------------------------------------------------------
-- PENDING PRICE AUDIT LOG
-- ------------------------------------------------------------

drop function if exists public.log_pending_price_changes(
    text, text, text, text, jsonb
);

create function public.log_pending_price_changes(
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
as $$
declare
    item jsonb;
    existing_id uuid;
    affected integer := 0;
    v_store_id uuid;
    v_device_ip text;
begin
    v_store_id :=
        public.verify_store_device_token(
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

    v_device_ip :=
        coalesce(
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
        where device_id = trim(p_device_id)
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
                v_device_ip,
                trim(p_device_id),
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
            set device_ip = v_device_ip,
                device_id = trim(p_device_id),
                scale_ip = coalesce(p_scale_ip, scale_ip),
                plu_name = coalesce(item->>'plu_name', plu_name),
                new_price = (item->>'new_price')::numeric,
                source = coalesce(p_source, source),
                changed_at = now()
            where id = existing_id;
        end if;

        affected := affected + 1;
    end loop;

    return affected;
end;
$$;

revoke all on function public.log_pending_price_changes(
    text, text, text, text, text, jsonb
) from public;

revoke all on function public.log_pending_price_changes(
    text, text, text, text, text, jsonb
) from public;

grant execute on function public.log_pending_price_changes(
    text, text, text, text, text, jsonb
) to anon, authenticated;

-- ------------------------------------------------------------
-- REVERTED PRICE AUDIT
-- ------------------------------------------------------------

drop function if exists public.mark_reverted_price_changes(
    text, text, integer[]
);

create function public.mark_reverted_price_changes(
    p_device_id text,
    p_device_token text,
    p_plu_nos integer[]
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
    v_store_id :=
        public.verify_store_device_token(
            p_device_id,
            p_device_token
        );

    update public.store_price_change_log l
    set status = 'REVERTED',
        reverted_at = now(),
        device_id = trim(p_device_id)
    where l.device_id = trim(p_device_id)
      and l.status = 'PENDING'
      and l.plu_no = any(coalesce(p_plu_nos, '{}'::integer[]));

    get diagnostics affected = row_count;
    return affected;
end;
$$;

revoke all on function public.mark_reverted_price_changes(
    text, text, integer[]
) from public;

grant execute on function public.mark_reverted_price_changes(
    text, text, integer[]
) to anon, authenticated;

-- ------------------------------------------------------------
-- START SCALE UPLOAD
-- ------------------------------------------------------------

drop function if exists public.start_scale_upload(
    text, text, text, integer
);

create function public.start_scale_upload(
    p_device_ip text,
    p_device_id text,
    p_device_token text,
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
    v_store_id uuid;
    v_device_ip text;
begin
    v_store_id :=
        public.verify_store_device_token(
            p_device_id,
            p_device_token
        );

    select device_ip
      into v_device_ip
    from public.store_devices
    where device_id = trim(p_device_id)
      and store_id = v_store_id
      and active = true
    limit 1;

    v_device_ip :=
        coalesce(
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

    insert into public.scale_upload_sessions (
        device_ip,
        device_id,
        scale_ip,
        status,
        plu_count
    )
    values (
        v_device_ip,
        trim(p_device_id),
        coalesce(trim(p_scale_ip), ''),
        'STARTED',
        coalesce(p_plu_count, 0)
    )
    returning id into new_id;

    return new_id;
end;
$$;

revoke all on function public.start_scale_upload(
    text, text, text, text, integer
) from public;

grant execute on function public.start_scale_upload(
    text, text, text, text, integer
) to anon, authenticated;

-- ------------------------------------------------------------
-- COMPLETE SCALE UPLOAD
-- ------------------------------------------------------------

drop function if exists public.complete_scale_upload(
    uuid, text, text, text, jsonb
);

create function public.complete_scale_upload(
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
set search_path = public
as $$
declare
    item jsonb;
    affected integer := 0;
    v_store_id uuid;
begin
    v_store_id :=
        public.verify_store_device_token(
            p_device_id,
            p_device_token
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

        update public.store_price_change_log
        set status = 'UPLOADED',
            uploaded_at = now(),
            device_id = trim(p_device_id),
            scale_ip = coalesce(p_scale_ip, scale_ip)
        where id = (
            select l.id
            from public.store_price_change_log l
            where l.device_id = trim(p_device_id)
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
    where id = p_session_id
      and device_id = trim(p_device_id);

    return affected;
end;
$$;

revoke all on function public.complete_scale_upload(
    uuid, text, text, text, text, jsonb
) from public;

grant execute on function public.complete_scale_upload(
    uuid, text, text, text, text, jsonb
) to anon, authenticated;

-- ------------------------------------------------------------
-- FAIL SCALE UPLOAD
-- ------------------------------------------------------------

drop function if exists public.fail_scale_upload(
    uuid, text
);

create function public.fail_scale_upload(
    p_session_id uuid,
    p_device_id text,
    p_device_token text,
    p_error_message text
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_store_id uuid;
begin
    v_store_id :=
        public.verify_store_device_token(
            p_device_id,
            p_device_token
        );

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
      and device_id = trim(p_device_id)
      and status = 'STARTED';

    if not found then
        raise exception 'Invalid or already completed upload session';
    end if;
end;
$$;

revoke all on function public.fail_scale_upload(
    uuid, text, text, text
) from public;

grant execute on function public.fail_scale_upload(
    uuid, text, text, text
) to anon, authenticated;

-- ------------------------------------------------------------
-- Disable legacy store RPC entry points so they cannot bypass
-- the token-authenticated v2 path.
-- ------------------------------------------------------------

revoke all on function public.store_get_pending_admin_price_updates(
    text
) from public;

revoke all on function public.store_mark_admin_price_updates_synced(
    text, uuid[]
) from public;

-- ------------------------------------------------------------
-- END DEVICE TOKEN SECURITY MIGRATION
-- ============================================================
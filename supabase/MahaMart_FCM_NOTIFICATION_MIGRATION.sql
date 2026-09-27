-- MahaMart Scale Manager - FCM live notification migration
-- Apply after MahaMart_DEVICE_TOKEN_SECURITY_MIGRATION_2026-09-27.sql.

alter table public.store_devices
    add column if not exists fcm_token text;

create index if not exists idx_store_devices_fcm_token
    on public.store_devices(fcm_token)
    where fcm_token is not null;

create or replace function public.store_set_fcm_token(
    p_device_id text,
    p_device_token text,
    p_fcm_token text
)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
    v_store_id uuid;
begin
    if coalesce(trim(p_fcm_token), '') = '' then
        raise exception 'FCM token is required';
    end if;

    v_store_id := public.verify_store_device_token(
        p_device_id,
        p_device_token
    );

    update public.store_devices
    set fcm_token = trim(p_fcm_token),
        last_seen_at = now(),
        updated_at = now()
    where device_id = trim(p_device_id)
      and store_id = v_store_id
      and active = true;

    return found;
end;
$$;

revoke all on function public.store_set_fcm_token(text, text, text) from public;

grant execute on function public.store_set_fcm_token(text, text, text)
    to anon, authenticated;

-- ============================================================
-- MahaMart Scale Manager - RLS / SECURITY INVOKER HARDENING
-- 2026-09-27
-- ============================================================
-- Apply AFTER the current schema + Admin Push + multi-device +
-- LC/upload migrations.
--
-- Goal:
--   1. Keep store phones RPC-only.
--   2. Keep Admin data accessible only to ADMIN users.
--   3. Make Admin report views SECURITY INVOKER.
--   4. Put explicit RLS policies on tables used by Admin views.
--   5. Remove unnecessary PUBLIC function execution.
--
-- IMPORTANT:
--   This migration does NOT change the Admin Push / LC / physical
--   upload lifecycle.
-- ============================================================

-- ============================================================
-- 1. ENABLE RLS ON ALL DATA TABLES
-- ============================================================

alter table public.stores enable row level security;
alter table public.profiles enable row level security;
alter table public.store_ip_map enable row level security;
alter table public.store_price_change_log enable row level security;
alter table public.store_price_current enable row level security;
alter table public.scale_upload_sessions enable row level security;
alter table public.admin_price_updates enable row level security;
alter table public.admin_price_update_targets enable row level security;
alter table public.store_devices enable row level security;
alter table public.store_device_registration_codes enable row level security;
alter table public.admin_price_update_device_state enable row level security;

-- ============================================================
-- 2. REMOVE DIRECT CLIENT ACCESS TO STORE-ONLY TABLES
-- ============================================================
-- Store phones use SECURITY DEFINER RPCs. They should not query
-- these tables directly through PostgREST.

revoke all on table public.store_device_registration_codes
from anon, authenticated;

revoke all on table public.admin_price_update_device_state
from anon, authenticated;

-- store_devices is exposed to Admin through the report views only.
revoke all on table public.store_devices
from anon, authenticated;

-- ============================================================
-- 3. ADMIN READ POLICIES
-- ============================================================

drop policy if exists stores_admin_select on public.stores;
create policy stores_admin_select
on public.stores
for select
to authenticated
using (public.is_admin());

drop policy if exists profiles_admin_select on public.profiles;
create policy profiles_admin_select
on public.profiles
for select
to authenticated
using (public.is_admin() or id = auth.uid());

drop policy if exists store_ip_map_admin_select on public.store_ip_map;
create policy store_ip_map_admin_select
on public.store_ip_map
for select
to authenticated
using (public.is_admin());

drop policy if exists change_log_admin_select on public.store_price_change_log;
create policy change_log_admin_select
on public.store_price_change_log
for select
to authenticated
using (public.is_admin());

drop policy if exists current_prices_admin_select on public.store_price_current;
create policy current_prices_admin_select
on public.store_price_current
for select
to authenticated
using (public.is_admin());

drop policy if exists upload_sessions_admin_select on public.scale_upload_sessions;
create policy upload_sessions_admin_select
on public.scale_upload_sessions
for select
to authenticated
using (public.is_admin());

drop policy if exists admin_price_updates_admin_select
on public.admin_price_updates;
create policy admin_price_updates_admin_select
on public.admin_price_updates
for select
to authenticated
using (public.is_admin());

drop policy if exists admin_price_update_targets_admin_select
on public.admin_price_update_targets;
create policy admin_price_update_targets_admin_select
on public.admin_price_update_targets
for select
to authenticated
using (public.is_admin());

drop policy if exists store_devices_admin_select
on public.store_devices;
create policy store_devices_admin_select
on public.store_devices
for select
to authenticated
using (public.is_admin());

-- Registration-code and device-state tables remain RPC-only.
-- No SELECT/INSERT/UPDATE/DELETE policies are intentionally created
-- for client roles.

-- ============================================================
-- 4. EXPLICIT ADMIN WRITE POLICIES
-- ============================================================
-- IP mapping UI currently writes through admin_save_store_ip_mapping().
-- Keep direct table writes closed; the RPC is the write boundary.
--
-- Admin Push is written through admin_publish_price_update().
-- Therefore no direct INSERT/UPDATE/DELETE policies are needed.

revoke insert, update, delete on table public.store_ip_map
from anon, authenticated;

revoke insert, update, delete on table public.admin_price_updates
from anon, authenticated;

revoke insert, update, delete on table public.admin_price_update_targets
from anon, authenticated;

revoke insert, update, delete on table public.store_price_change_log
from anon, authenticated;

revoke insert, update, delete on table public.store_price_current
from anon, authenticated;

revoke insert, update, delete on table public.scale_upload_sessions
from anon, authenticated;

-- ============================================================
-- 5. CLIENT READ GRANTS FOR ADMIN VIEWS / RLS
-- ============================================================
-- SECURITY INVOKER views execute as the requesting user.
-- Their underlying tables therefore need SELECT privilege.
-- RLS still limits authenticated users to ADMIN rows.

grant select on table public.stores
to authenticated;

grant select on table public.profiles
to authenticated;

grant select on table public.store_ip_map
to authenticated;

grant select on table public.store_price_change_log
to authenticated;

grant select on table public.store_price_current
to authenticated;

grant select on table public.scale_upload_sessions
to authenticated;

grant select on table public.admin_price_updates
to authenticated;

grant select on table public.admin_price_update_targets
to authenticated;

grant select on table public.store_devices
to authenticated;

-- ============================================================
-- 6. SECURITY INVOKER ADMIN REPORT VIEWS
-- ============================================================
-- Do this for the Advisor findings visible on 2026-09-27.
--
-- SECURITY INVOKER means the caller's privileges/RLS are applied
-- to the underlying tables instead of silently using the view
-- owner's privileges.

alter view public.admin_current_prices
    set (security_invoker = true);

alter view public.admin_price_change_report
    set (security_invoker = true);

alter view public.admin_scale_upload_report
    set (security_invoker = true);

alter view public.admin_price_push_report
    set (security_invoker = true);

alter view public.admin_store_devices
    set (security_invoker = true);

alter view public.admin_store_device_status
    set (security_invoker = true);

-- ============================================================
-- 7. VIEW GRANTS
-- ============================================================

revoke all on public.admin_current_prices
from anon, authenticated;
grant select on public.admin_current_prices to authenticated;

revoke all on public.admin_price_change_report
from anon, authenticated;
grant select on public.admin_price_change_report to authenticated;

revoke all on public.admin_scale_upload_report
from anon, authenticated;
grant select on public.admin_scale_upload_report to authenticated;

revoke all on public.admin_price_push_report
from anon, authenticated;
grant select on public.admin_price_push_report to authenticated;

revoke all on public.admin_store_devices
from anon, authenticated;
grant select on public.admin_store_devices to authenticated;

revoke all on public.admin_store_device_status
from anon, authenticated;
grant select on public.admin_store_device_status to authenticated;

-- ============================================================
-- 8. FUNCTION EXECUTE GRANTS
-- ============================================================
-- PUBLIC execution is removed. Store functions remain available
-- to anon/authenticated because store phones have no login.
-- Admin functions are authenticated-only.
--
-- Function bodies remain responsible for device/admin authorization.

revoke all on function public.admin_generate_store_device_code(text, integer)
from public;
grant execute on function public.admin_generate_store_device_code(text, integer)
to authenticated;

revoke all on function public.admin_get_store_plu_prices(integer)
from public;
grant execute on function public.admin_get_store_plu_prices(integer)
to authenticated;

revoke all on function public.admin_publish_price_update(boolean, uuid[], jsonb, text)
from public;
grant execute on function public.admin_publish_price_update(boolean, uuid[], jsonb, text)
to authenticated;

revoke all on function public.admin_save_store_ip_mapping(text, text, text, boolean)
from public;
grant execute on function public.admin_save_store_ip_mapping(text, text, text, boolean)
to authenticated;

revoke all on function public.log_pending_price_changes(text, text, text, text, jsonb)
from public;
grant execute on function public.log_pending_price_changes(text, text, text, text, jsonb)
to anon, authenticated;

revoke all on function public.start_scale_upload(text, text, text, integer)
from public;
grant execute on function public.start_scale_upload(text, text, text, integer)
to anon, authenticated;

revoke all on function public.complete_scale_upload(uuid, text, text, text, jsonb)
from public;
grant execute on function public.complete_scale_upload(uuid, text, text, text, jsonb)
to anon, authenticated;

revoke all on function public.fail_scale_upload(uuid, text)
from public;
grant execute on function public.fail_scale_upload(uuid, text)
to anon, authenticated;

revoke all on function public.store_register_device(text, text, text, text)
from public;
grant execute on function public.store_register_device(text, text, text, text)
to anon, authenticated;

revoke all on function public.store_touch_device(text, text, text)
from public;
grant execute on function public.store_touch_device(text, text, text)
to anon, authenticated;

revoke all on function public.store_get_pending_admin_price_updates_v2(text, text)
from public;
grant execute on function public.store_get_pending_admin_price_updates_v2(text, text)
to anon, authenticated;

revoke all on function public.store_mark_admin_price_updates_synced_v2(text, uuid[])
from public;
grant execute on function public.store_mark_admin_price_updates_synced_v2(text, uuid[])
to anon, authenticated;

revoke all on function public.store_mark_admin_price_updates_uploaded_v2(text, uuid[])
from public;
grant execute on function public.store_mark_admin_price_updates_uploaded_v2(text, uuid[])
to anon, authenticated;

-- Legacy store functions, if still present.
revoke all on function public.store_get_pending_admin_price_updates(text)
from public;
grant execute on function public.store_get_pending_admin_price_updates(text)
to anon, authenticated;

revoke all on function public.store_mark_admin_price_updates_synced(text, uuid[])
from public;
grant execute on function public.store_mark_admin_price_updates_synced(text, uuid[])
to anon, authenticated;

-- ============================================================
-- 9. VERIFICATION QUERIES
-- ============================================================
-- Run these AFTER this migration. They are intentionally read-only.

-- RLS enabled:
-- select schemaname, tablename, rowsecurity
-- from pg_tables
-- where schemaname = 'public'
--   and tablename in (
--     'stores','profiles','store_ip_map',
--     'store_price_change_log','store_price_current',
--     'scale_upload_sessions','admin_price_updates',
--     'admin_price_update_targets','store_devices',
--     'store_device_registration_codes',
--     'admin_price_update_device_state'
--   )
-- order by tablename;

-- Policies:
-- select schemaname, tablename, policyname, roles, cmd
-- from pg_policies
-- where schemaname = 'public'
-- order by tablename, policyname;

-- View security mode:
-- select n.nspname as schema_name,
--        c.relname as view_name,
--        c.reloptions
-- from pg_class c
-- join pg_namespace n on n.oid = c.relnamespace
-- where n.nspname = 'public'
--   and c.relkind = 'v'
--   and c.relname in (
--      'admin_current_prices',
--      'admin_price_change_report',
--      'admin_scale_upload_report',
--      'admin_price_push_report',
--      'admin_store_devices',
--      'admin_store_device_status'
--   );

-- Function grants:
-- select routine_schema, routine_name, routine_type
-- from information_schema.routines
-- where routine_schema = 'public'
-- order by routine_name;

-- ============================================================
-- END
-- ============================================================

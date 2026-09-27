# Supabase Advisor Findings — 2026-09-27

## Source

Captured from the Supabase Dashboard Security Advisor for the MahaMart-Essae production project on 2026-09-27.

These are **Advisor findings to review**, not proof that every flagged object is incorrectly secured. Security-definer objects can be intentional, but their execution privileges, search path, RLS behavior, and exposed data should be verified before changing them.

## Findings visible in the Advisor

### Security Definer View — CRITICAL
The Advisor currently flags these public views:

- `public.admin_current_prices`
- `public.admin_price_change_report`
- `public.admin_scale_upload_report`
- `public.store_devices`
- `public.admin_store_device_status`

Required review:
- whether each view is intentionally SECURITY DEFINER;
- who can execute/select through it;
- whether it exposes data beyond the intended Admin/Store scope;
- whether the underlying tables have appropriate RLS;
- whether the view owner and grants are correct;
- whether a SECURITY INVOKER design is possible without breaking the application.

Do **not** blindly remove SECURITY DEFINER from these views. First inspect the current definitions, RLS policies, grants, and the Android/Admin call paths.

### Auth RLS Initialization Plan
The Advisor flags:

- `public.profiles`

This needs review of the table's RLS policies and intended access pattern. The existing project architecture requires Admin authorization through Supabase Auth and the ADMIN profile/role.

### Leaked Password Protection Disabled
The Advisor reports:

- Auth → Leaked Password Protection Disabled

This is an Auth configuration setting, not an Android code change. Review whether Supabase Auth's leaked-password protection should be enabled for the production project.

### Public Can Execute SECURITY DEFINER Function
The Advisor flags at least these functions as publicly executable SECURITY DEFINER functions:

- `public.admin_generate_store_device_code(...)`
- `public.admin_get_store_plu_prices(p_plu_no integer)`
- `public.admin_publish_price_update(p_apply_to_all boolean, p_store...)`
- `public.admin_save_store_ip_mapping(p_device_ip text, p_store_co...)`

The screenshot indicates additional Advisor entries may exist below the visible portion. The complete list must be retrieved from the live Advisor/database before changing grants.

Required review:
- current EXECUTE grants for PUBLIC/anon/authenticated;
- whether each function already performs internal admin authorization;
- whether public EXECUTE is actually required by the app;
- whether EXECUTE should be revoked from PUBLIC and granted only to the intended role(s).

## Important project-security rule

Do not put service-role keys, passwords, signing credentials, or other secrets into this project-brain file.

## Next security pass

Before production-final testing:

1. Capture the complete current Advisor list.
2. Inspect each flagged view/function definition.
3. Inspect RLS policies and grants.
4. Map each function to its Android/Admin call path.
5. Decide the minimum grant/RLS changes that preserve the current architecture.
6. Apply changes in a controlled SQL migration.
7. Re-run the Advisor and test Admin Push, store registration, reports, and physical upload lifecycle.

This security pass is separate from the current Admin Push → Store Sync → Physical Essae Upload lifecycle. Do not alter working price/upload semantics while addressing Advisor findings.

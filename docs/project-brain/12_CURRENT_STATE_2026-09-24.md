# Current State — 2026-09-24

## Android
The corrected Admin Auth build successfully reached the Admin Dashboard on the user's physical Android device.

Admin flow:
Scale Manager long press -> Admin Login -> Supabase Auth -> profiles role/active check -> Admin Dashboard.

The dashboard shell confirmed:
- MahaMart Admin
- ADMIN
- authentication connected successfully
- placeholders for Admin Push, Reports, and Store/IP Mapping

## Supabase
Database rebuild and Admin Push migration are in place.

Tables include:
admin_current_prices, admin_price_change_report, admin_price_push_report, admin_price_update_targets, admin_price_updates, admin_scale_upload_report, profiles, scale_upload_sessions, store_ip_map, store_price_change_log, store_price_current, stores.

Relevant RPCs/functions include:
admin_publish_price_update, admin_save_store_ip_mapping, complete_scale_upload, fail_scale_upload, is_admin, log_pending_price_changes, mark_reverted_price_changes, start_scale_upload, store_get_pending_admin_price_updates, store_mark_admin_price_updates_synced.

## Next implementation
Build the real Admin Dashboard modules, starting with Admin Push, while keeping the stable Essae upload path unchanged.

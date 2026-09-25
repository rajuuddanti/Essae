# Supabase --- Final Database Handoff

## Project

URL:

``` text
https://ztuzcrcvbbprytwotpwo.supabase.co
```

Client Android authentication must use the publishable/anon key only.

Never embed a service-role/secret key in Android.

## Scripts already run

1.  `MahaMart_FRESH_SUPABASE_SCHEMA.sql`
2.  `MahaMart_SUPABASE_ADMIN_PUSH_MIGRATION.sql`

Do not rerun the fresh schema against a populated production database.

## Core tables

### stores

17 active store master rows.

### profiles

Admin-only profile table.

``` text
id → auth.users.id
full_name
role = ADMIN
active
created_at
```

### store_ip_map

Maps a device IP to a store.

``` text
device_ip
store_id
active
updated_at
```

### store_price_change_log

Audit history.

Important fields:

``` text
device_ip
device_id
scale_ip
plu_no
plu_name
old_price
new_price
source
status
changed_at
uploaded_at
reverted_at
```

Final source values:

``` text
MANUAL
CSV
ADMIN_PUSH
```

### store_price_current

Authoritative Admin current price snapshot.

Primary key:

``` text
(device_ip, plu_no)
```

Fields:

``` text
device_ip
plu_no
plu_name
unit_price
scale_ip
device_id
last_uploaded_at
```

Only successful scale upload should update it.

### scale_upload_sessions

Tracks Upload All operations.

Fields:

``` text
id
device_ip
device_id
scale_ip
started_at
completed_at
status
plu_count
error_message
```

## Admin Push tables

### admin_price_updates

One row per Admin publish action.

Includes:

``` text
id
created_by
created_at
mode = ALL / SELECTED
note
item_count
items jsonb
```

### admin_price_update_targets

One row per target store.

Statuses:

``` text
PENDING
SYNCED
UPLOADED
```

## Views

Core:

``` text
admin_current_prices
admin_price_change_report
admin_scale_upload_report
```

Admin Push:

``` text
admin_price_push_report
```

## Core RPCs

Fresh schema:

``` text
is_admin()
log_pending_price_changes(device_ip, device_id, scale_ip, source, items)
mark_reverted_price_changes(device_ip, device_id, plu_nos)
start_scale_upload(device_ip, device_id, scale_ip, plu_count)
complete_scale_upload(session_id, device_ip, device_id, scale_ip, items)
fail_scale_upload(session_id, error_message)
admin_save_store_ip_mapping(device_ip, store_code, store_name, active)
```

Admin Push migration:

``` text
admin_publish_price_update(apply_to_all, store_ids, items, note)
store_get_pending_admin_price_updates(device_ip)
store_mark_admin_price_updates_synced(device_ip, update_ids)
```

The migration also replaces/updates `complete_scale_upload()` so Admin
Push targets are marked uploaded when physical scale upload succeeds.

## RLS/security model

-   Tables use RLS.
-   Admin reads report tables/views.
-   Store phones do not get direct table insert/update access for audit
    data.
-   Store-side operations go through security-definer RPCs.
-   Admin Push tables have no direct public insert/update policy; Admin
    publishes through the RPC.
-   Admin mapping is Admin-only.

## Important legacy RPC

`mark_reverted_price_changes()` exists in the fresh schema.

It must NOT be called on app startup in the final Android behavior.

Reason:

The final user requirement is persistent RED/PENDING across restart.

## Data retention

No automatic deletion policy.

Keep history indefinitely until a deliberate archive/retention feature
is introduced.

## Exact SQL source

The complete original fresh schema and Admin Push migration are included
as separate Markdown appendices in this handoff pack.
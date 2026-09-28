# Store Operations — Current Checkpoint (2026-09-28)

Branch: `ChatGPT-sugegstions`

## Current feature

Admin Dashboard now has **Store Operations** using existing device, price-change, Admin Push, and scale-upload records. No new database table was added.

## Store overview pending count

The Store Operations list counts **pending Admin Push updates**, not unique SKUs.

Example:

- 45 Admin Push update records are synced to MM017.
- Those 45 records affect only 5 unique SKUs.
- None have been physically uploaded to the scale.
- Store Operations therefore shows **45 PENDING**.

Repeated pushes for the same SKU are intentionally counted separately in this overview.

## Store detail pending prices

Inside a store, **Pending Prices** represents the unique SKUs that currently have an Admin Push price waiting for physical scale upload.

For the MM017 test case:

- Pending Admin Push update records: **45**
- Unique pending SKUs: **5**
- Uploaded: **0**

The five current pending SKUs are:

1. SUGAR LOOSE
2. PALLILU LOOSE
3. PESARA PAPPU NO.1 LOOSE
4. GODHUMA PINDI NO.1 LOOSE
5. ALLAM NEW LOOSE

Admin Push History continues to retain the individual update records, including repeated pushes for the same SKU.

## Status semantics

`SYNCED` means the Admin Push reached the store phone.

`uploaded_at = NULL` means the update has not yet been confirmed as physically uploaded to the scale.

After successful physical scale upload, the pending state must clear and the upload timestamp is recorded.

## Store detail history tabs

Pending Prices remains directly visible.

The following three history sections are now clickable tabs:

- **RECENT PRICE CHANGES** — existing recent price-change records.
- **UPLOAD HISTORY** — existing scale upload records.
- **ADMIN PUSH HISTORY** — existing Admin Push records.

Only the selected history dataset is displayed, reducing the long scrolling detail page.

## Important implementation notes

- No new Supabase table is required for Store Operations.
- Existing `admin_price_push_report`, `admin_price_change_report`, `admin_scale_upload_report`, and device reporting data are reused.
- Existing Essae transport/upload behavior is not rewritten for this feature.
- Database NULL `uploaded_at` must be treated as Kotlin null, not the literal string `"null"`.

## Current pause point

The Store Operations tab redesign is implemented but has not yet been user-approved. If the tab layout is not satisfactory, revert the tab-specific commit rather than changing the underlying pending-price logic.

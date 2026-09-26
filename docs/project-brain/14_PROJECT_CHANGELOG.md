# Project Changelog

## Stable Essae base
Established working local PLU handling, Room storage, direct scale communication, and Upload All.

## Cloud audit layer
Added Supabase-backed audit structures, store mapping, price-current state, upload sessions, and admin operations.

## Admin authentication
Added Supabase Auth plus profiles role checking. Only ADMIN + active users enter the Admin area.

## Admin entry
Admin entry is hidden behind a long press on Scale Manager.

## Multi-device registration
Added store-code + device-ID registration, one-time registration codes, device last-seen tracking, and Admin Store/Device Mapping.

## Registration bug fix
Fixed the registration RPC conflict ambiguity by using the explicit `store_devices_device_id_unique` constraint.

## Admin Push — per-store price view — 2026-09-25
Redesigned Admin Push to follow the main Scale Manager workflow:
- First screen is the PLU master list.
- Tap a SKU/PLU to open its Admin detail.
- Detail shows every active store with store code/name and the latest known confirmed running price.
- Store selection is on the left.
- Each selected store can have its own new price.
- Different new prices are grouped into separate Admin Push updates.
- The screen does not directly upload to physical Essae scales.

Added `admin_get_store_plu_prices(plu_no)` to read the latest confirmed price per store, using registered-device data first and legacy store/IP mapping as fallback.

Status: **code pushed; Supabase migration and Android build/device test still required**.

## Next milestones
1. Apply `MahaMart_ADMIN_PUSH_STORE_PRICE_VIEW.sql` in Supabase.
2. Build/device-test the redesigned Admin Push.
3. Verify current price values for all stores.
4. Verify selected-store and different-price pushes.
5. Device-level Store Sync.
6. Persistent RED/PENDING state.
7. Physical upload cloud lifecycle.
8. Reports/export.
9. Multi-device end-to-end testing.

## Guardrails
- Do not rewrite `EssaeTransport.kt`.
- Do not commit secrets.
- Do not merge experimental Admin Push work into `main` until end-to-end tests pass.
## 2026-09-25 — Bundled PLU Master + Two Label Designs

- Added the supplied 133-PLU CSV as the bundled fresh-install PLU master.
- Bundled PLU master intentionally contains zero prices; it seeds the local Room PLU list only.
- Fresh install seeds the PLU database once; later CSV imports continue using the existing import/update path.
- Added two persistent bundled label-design slots:
  - **Weight Only**
  - **Weight + ₹ Price**
- Label Design screen now has exactly two tabs with those names.
- Import / Replace Design replaces the currently selected slot locally.
- Upload Label Design sends the currently selected slot to the Essae scale.
- Existing Essae transport and price-edit/upload behavior were not changed.
- Supplied LFT contents are preserved; only their local slot names are normalized for the app.
## 2026-09-25 — Temporary Store → Admin Price Visibility Test

- Admin Push store rows simplified to show only store code/name and running price; removed the "No confirmed scale price" / last-upload detail text.
- For a controlled test only, `log_pending_price_changes` now also upserts `store_price_current` when a phone changes a local price.
- This temporary behavior intentionally makes Admin Push see the changed phone price before Essae Upload All, so store → cloud → admin visibility can be verified.
- This must be reverted after the test so `store_price_current` again represents the confirmed physical-scale price after successful Essae upload.
## 2026-09-25 — Admin Push Store Sync + Compact Store View

- Confirmed Store → Admin visibility works: a local store price change appears in Admin Push without Essae upload under the temporary test mode.
- Added store-side Admin Push polling for registered devices using the multi-device v2 RPCs:
  - `store_get_pending_admin_price_updates_v2`
  - `store_mark_admin_price_updates_synced_v2`
- Pending Admin Push prices are now applied to local Room when the store app opens/resumes; the physical scale is still not changed by Sync.
- Admin Push store rows were compacted:
  - store name only (store code removed)
  - running price
  - new-price field
  - `LC: dd-MM-yyyy, HH:mm` in Asia/Kolkata
  - removed long "No confirmed scale price" / ISO timestamp text.
- The existing temporary Store → Admin test behavior remains intentionally active until the reverse flow is verified, then it must be reverted.
## 2026-09-26 — TEMP TEST: Admin Push Current Price + Store Highlight

- Clarified the two-price test state: the store Room price can move to the Admin Push value while the cloud `store_price_current` may still contain the prior confirmed value.
- In temporary test mode, Admin Push sync now also writes the pushed price to `store_price_current` through `log_pending_price_changes`, so Admin immediately sees the same running price.
- Store Admin Push sync now returns the exact PLU numbers applied and marks those PLUs as changed in the main Scale Manager UI immediately.
- This is test-only behavior and must be reverted before production so `store_price_current` again represents the successfully uploaded physical-scale price.



## 2026-09-26 — stale Admin Push overwrite fix

- Root cause identified from end-to-end Admin Push/store behavior: an older pending Admin Push could remain eligible for device polling and overwrite a newer local MANUAL/CSV price change.
- Updated `supabase/MahaMart_MULTI_DEVICE_REGISTRATION_MIGRATION.sql` function `store_get_pending_admin_price_updates_v2` to ignore an Admin Push when a newer local MANUAL/CSV change exists for the same device and PLU.
- Example protected flow: Admin Push ₹90 → store changes to ₹69.90 → old ₹90 must not return on the next polling cycle.
- A genuinely newer Admin Push remains eligible because its `created_at` is newer than the local change.
- Commit: `3b2c3189b679a367736182d9f0afbf66ad85b794`.
- Live Supabase must have this function applied through SQL Editor before testing; changing the repository SQL alone does not alter the live database.


## 2026-09-26 — Final Admin Push → Store → Scale LC Lifecycle

- Admin Push master price is based only on the current/running price that is repeated across at least 2 stores.
- If no price repeats in at least 2 stores, Master Price displays —.
- The Admin PLU list no longer displays the local phone plu.unitPrice as if it were the Master Price.
- Admin Push detail continues to show every store's own current/confirmed selling price.
- Admin enters one NEW PRICE and selects one or more stores; the same price is published to all selected stores.
- Store receipt of an Admin Push:
  - local Room PLU price changes to the pushed price
  - SKU becomes RED/CHANGED
  - physical Essae scale is not changed by Sync
  - confirmed Admin LC remains the previous physical-scale upload
  - Admin detail additionally shows the received-but-not-uploaded push date as (dd-MM)*
- Physical Essae upload:
  - store_price_current is updated only by complete_scale_upload()
  - Admin LC moves to the physical upload timestamp
  - matching Admin Push device state changes from SYNCED to UPLOADED
  - pending (dd-MM)* disappears
  - local RED/CHANGED state is cleared after successful physical upload
- Added supabase/MahaMart_ADMIN_PUSH_LC_AND_UPLOAD_MIGRATION.sql.
- Added Android cloud upload-session calls around the existing Essae transport without modifying EssaeTransport.kt.
- Android now sends explicit JSON arrays for Admin Push price-change logging.
- Live SQL/application must be updated with the new migration before the final end-to-end test.

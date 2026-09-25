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


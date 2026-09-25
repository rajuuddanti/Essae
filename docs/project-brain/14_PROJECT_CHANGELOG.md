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

## Admin Push checkpoint — 2026-09-25
Added the first functional Admin Push module on `feature/admin-device-registration`:
- Dashboard entry.
- PLU/SKU/item search from local PLU data.
- New price entry.
- All-store or selected-store targeting.
- Confirmation dialog.
- `admin_publish_price_update` Supabase call.
- Returned update ID / error display.

Status: **implemented, not yet build/device-tested**.

## Next milestones
1. Build/device-test Admin Push.
2. Verify Supabase update header and targets.
3. Device-level Store Sync.
4. Persistent RED/PENDING state.
5. Physical upload cloud lifecycle.
6. Reports/export.
7. Multi-device end-to-end testing.

## Guardrails
- Do not rewrite `EssaeTransport.kt`.
- Do not commit secrets.
- Do not merge experimental Admin Push work into `main` until end-to-end tests pass.

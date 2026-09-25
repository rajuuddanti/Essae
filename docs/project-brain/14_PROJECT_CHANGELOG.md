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

## 2026-09-25 checkpoint
Registration and Store/Device Mapping are verified end-to-end. The existing Essae transport/upload path remains unchanged.

Next milestone:
1. Admin Push UI
2. Device-level Store Sync
3. Persistent RED/PENDING state
4. Physical upload cloud lifecycle
5. Reports/export
6. Multi-device end-to-end tests

## Guardrails
- Do not rewrite `EssaeTransport.kt`.
- Do not commit secrets.
- Do not merge experimental Admin Push work into `main` until end-to-end tests pass.

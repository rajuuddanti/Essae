# Current State — 2026-09-25

## Verified Android
The current feature branch has verified Admin authentication, Store Device Registration, Store/Device Mapping, device persistence, last-seen updates, and the existing Essae upload path.

## Verified device test
- Store: MM017 — Vidya Nagar
- Device: sdk_gphone16k_x86_64
- Device ID: 7bf621a9c15a33ea
- IP: 10.0.2.15

## Supabase
The multi-device registration migration is applied. Device registration and device-level state objects are present.

The registration RPC conflict bug was fixed by using the explicit unique constraint `store_devices_device_id_unique`.

## Timezone
Keep database timestamps as `timestamptz`; Admin/report presentation should use Asia/Kolkata (IST, UTC+05:30).

## Immediate next module
**Admin Push**

Admin Push must search/select a PLU, show its current Admin price, allow all/selected stores, accept a new price, confirm before publishing, call the Admin Push RPC, and record auditable results. It creates pending store/device work; it does not directly upload to Essae.

After Admin Push: device-level Store Sync, persistent RED/PENDING state, physical-upload lifecycle, then Reports/export and multi-device testing.

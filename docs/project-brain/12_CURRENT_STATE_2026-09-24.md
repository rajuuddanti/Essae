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

## Admin Push checkpoint
A first functional Admin Push screen is now implemented on the feature branch:
- Admin dashboard entry button.
- PLU/SKU/item search using the local PLU master.
- Single PLU selection.
- New price input.
- All active stores or selected stores.
- Confirmation dialog.
- Supabase `admin_publish_price_update` call.
- Success/failure message with returned update ID.

This is **implemented but not yet build/device-tested**. Do not merge to `main` yet.

The Admin Push screen only creates cloud pending work. It does not directly upload to Essae.

## Timezone
Keep database timestamps as absolute `timestamptz` values. Admin/report presentation should use Asia/Kolkata / IST.

## Immediate next module
1. Build/test Admin Push on the Android device.
2. Verify the Supabase push row and selected/all store targets.
3. Implement device-level Store Sync using the v2 RPCs.
4. Make RED/PENDING state persistent.
5. Connect physical Upload All success/failure to cloud lifecycle.
6. Reports/export and multi-device end-to-end testing.

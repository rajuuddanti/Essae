# Admin CSV Remote Price Push — 2026-09-29

## Purpose

Add a new **Admin-side CSV import and bulk price push** workflow.

This feature removes the need to physically visit a store just to initialize or update its price list.

### Important boundary

**DO NOT MODIFY the existing Store CSV Import workflow.**

The existing Store CSV Import remains unchanged and continues to work independently.

## Existing Store CSV Flow — FROZEN

Store device:
CSV Import -> Store Master Price

This existing implementation must not be changed, refactored, or replaced as part of the Admin CSV feature.

## New Admin CSV Flow

Admin:
CSV Import
-> validate/preview CSV
-> choose one, multiple, or all stores
-> create Admin Push
-> selected registered store devices receive the prices
-> local store PLU/price state becomes PENDING
-> manager performs physical Essae scale upload
-> successful physical upload becomes confirmed scale price

## Main use case

Initial store setup can be done remotely:

1. Install the app at the store.
2. Register the device to its store.
3. Admin imports the store's complete price CSV remotely.
4. Admin selects the target store.
5. Admin pushes the complete price list.
6. Store receives the prices when online.
7. Store can then perform physical scale upload.

No physical CSV visit is required.

## Store selection

Admin CSV push must support:

- One selected store
- Multiple selected stores
- All stores

## SKU volume

There is **no special 4-SKU or 133-SKU limit**.

The intended workflow should support:
- 1 SKU
- 4 SKUs
- 133 SKUs
- 500+ SKUs
- 1,000+ SKUs where practical

Large imports/pushes should use batching/chunking internally if necessary so the Android UI remains responsive and Supabase requests remain safe.

## Admin CSV is an Admin Push

The Admin CSV must NOT directly establish a confirmed physical scale price.

It represents an Admin instruction:

Admin CSV -> Admin Push -> Store -> Physical Scale Upload

Therefore:
- CSV-imported Admin prices are pending until physical scale upload succeeds.
- store_price_current remains the physical-scale-confirmed layer.
- Existing Admin Push audit/history must remain usable.
- Each selected store has independent delivery/sync/upload status.

## Audit requirements

The system must preserve:
- Admin who initiated the CSV push
- CSV push/update ID
- CSV SKU count
- Target stores
- Per-store status
- Per-device sync status
- Admin price
- Sync timestamp
- Physical upload timestamp
- Physical uploaded price
- Manager/device/IP/scale information where already supported
- Existing reconciliation and override reporting

Do not create a separate parallel pricing/audit architecture if the existing Admin Push infrastructure can be reused.

## New SKU behavior

A new PLU arriving through Admin Push may be added to the store's local PLU list by the existing Admin Push sync logic.

This is a secondary/edge case, not the primary purpose of this feature. Do not redesign the PLU architecture solely for this feature.

## Existing architecture to reuse

Current Admin Push path:
Admin Push -> Store sync -> Room/local price -> PENDING -> physical Essae Upload -> successful cloud confirmation.

Existing Supabase objects include:
- admin_price_updates
- admin_price_update_targets
- admin_price_update_device_state
- store_price_change_log
- store_price_current
- store_price_master
- existing Admin Push/reporting views and RPCs

Existing Store CSV implementation remains untouched.

## Proposed Admin UI

Admin-side flow should be:

1. Open Admin CSV Import.
2. Select CSV.
3. Parse and validate.
4. Show preview/count, e.g. "133 SKUs ready".
5. Select stores:
   - SELECT ALL
   - individual store selection
6. Show final summary:
   - SKU count
   - selected store count
   - total store/SKU targets
7. Confirm PUSH.
8. Create the normal Admin Push records.
9. Show push result/status.

## Important distinction

Store CSV:
- Store-originated master price update.
- Existing implementation.
- DO NOT TOUCH.

Admin CSV:
- Admin-originated price instruction.
- Bulk Admin Push to selected stores.
- New feature.

## Implementation safety rules

1. Do not modify EssaeTransport.kt for this feature.
2. Do not modify the existing Store CSV import flow.
3. Reuse the existing Admin Push backend/RPC architecture where possible.
4. Do not mark scale prices confirmed merely because an Admin CSV push was created or synced.
5. Preserve per-store/per-device audit state.
6. Do not impose a fixed 133-SKU limit.
7. Handle large CSVs without blocking the UI.
8. Test single-store, multi-store, and all-store pushes.
9. Test offline store behavior: Admin can create the push while the store is offline; the registered device should receive it when it next syncs.
10. Before implementation, inspect the current Admin Push UI/backend and existing Store CSV code to avoid touching the frozen Store CSV path.

## Next implementation target

Build **Admin -> CSV Import -> Select Store(s) -> Push All Prices** as a separate Admin-side feature, reusing the current Admin Push lifecycle.

This document is the implementation checkpoint to resume from in a new conversation.

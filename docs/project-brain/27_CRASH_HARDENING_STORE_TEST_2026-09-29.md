# Crash Hardening + Store Test Fixes — 2026-09-29

## Branch

`feature/crash-hardening`

## Findings from store test

1. Admin CSV Push did not carry `plucode` into Admin Push payloads.
   - Admin CSV parser now reads `PLU_CODE`, `PLUCODE`, `CODE`, or `BARCODE`.
   - Admin Push payload now carries `plu_code`.
   - Store-side Admin Push sync now applies the code to the local Room PLU record.
   - Admin CSV preview now displays the code when present.

2. Repeated identical Admin CSV Pushes could still produce another pending action while the earlier identical Admin Push was waiting for physical upload.
   - Live `store_get_pending_admin_price_updates_v2` now ignores an identical price for the same PLU when an earlier identical Admin Push is already SYNCED and still not physically uploaded.
   - Store Master Price comparison remains the first same-price guard.

3. Label store name could overflow the label width.
   - Runtime LFT generation now reduces the store-name text size from `2,2` to `1,1` when the configured label name exceeds 19 characters.
   - Short names retain the original `2,2` sizing.

4. FSSAI is now editable when an imported/bundled label design contains an FSSAI field.
   - Exactly 14 digits are required.
   - The value is applied to every runtime label design containing an FSSAI field.
   - Designs without an FSSAI field are not modified.

5. Admin Push notifications were inconsistent because the live Edge Function used a notification+data payload while the Android client also generates its own local notification.
   - Edge Function version 4 is deployed with data-only + HIGH priority.
   - Android `FirebaseMessagingService` remains the single local-notification path.
   - Android 13+ notification permission remains required.

6. MainVm crash hardening had missing coroutine imports and several coroutines were still using the unhandled `viewModelScope.launch`.
   - Imports fixed.
   - MainVm operations now use the crash-safe exception handler.
   - CI trigger added for this branch.

## Important label note

The supplied PLU CSV contains a valid `plucode` column. The original Admin CSV Push code discarded that field, which explains why Admin CSV-driven PLUs could reach the phone/scale without the code. The LFT barcode rendering itself is a separate label-template/protocol behavior and should be validated after the PLU-code fix before changing the barcode template blindly.

## Live backend

- Same-price duplicate pending protection deployed.
- `send-admin-push-notification` Edge Function deployed as version 4.

# Stable Checkpoint — 2026-09-26

## Purpose

This is the latest durable project-brain checkpoint for resuming the MahaMart Scale Manager project in a new conversation. If the current conversation reaches a limit, start here and then read the linked project-brain documents.

## Repository / branch

- Repository: `rajuuddanti/Essae`
- Active development branch: `feature/admin-device-registration`
- Stable baseline: MahaMart Scale Manager / Essae v4
- Package: `com.mahamart.essae`
- Never rewrite `EssaeTransport.kt` while adding cloud/admin features.

## Current lifecycle — authoritative

### Confirmed price

`store_price_current` means the **last successfully uploaded physical Essae scale price**.

Admin Push does NOT update this confirmed price.

### Admin Push

1. Admin selects a PLU.
2. Master Price is the price repeated in at least 2 stores. If no price repeats at least twice, display `—`.
3. Admin detail shows every active store's confirmed/running price.
4. Admin enters ONE NEW PRICE.
5. Admin selects one or more stores.
6. The same new price is pushed to all selected stores.
7. Admin Push creates cloud pending work; it does not physically upload to Essae.

### Store receipt

When the store receives an Admin Push:

- local Room PLU price becomes the pushed price;
- the PLU is RED/PENDING;
- physical scale is unchanged;
- confirmed Admin price remains the previous physical-scale price;
- pending push date and price are retained in cloud state.

Admin display:

```
₹89.00
LC: 26-09-2026, 17:22 (26-09)*(12)
```

Meaning:
- ₹89 = confirmed physical scale price;
- 26-09 = Admin Push received date;
- * = pending physical upload;
- (12) = pushed price waiting to be uploaded.

### Physical upload success

Store runs the existing Upload All → Essae flow.

Only after successful Essae upload:

- `complete_scale_upload()` updates `store_price_current`;
- matching Admin Push device state changes SYNCED → UPLOADED;
- pending date/price disappears;
- local RED/PENDING state clears.

Result:

```
₹12.00
LC: 26-09-2026, HH:MM
```

### Physical upload failure

- confirmed cloud price does not change;
- pending Admin Push remains;
- local RED/PENDING state remains.

## Manual manager price changes — NEW FINAL RULE

Manager manual price changes are tracked separately through the existing audit source:

`MANUAL`

When a manager changes a price locally:

- Room price changes;
- local `PriceChangeAudit` is created with source `MANUAL`;
- status is PENDING;
- cloud `log_pending_price_changes()` receives source MANUAL;
- physical upload has not yet been confirmed.

Only after successful physical scale upload should the Admin current price move.

When the confirmed uploaded price came from a manual manager change, Admin displays:

```
Store Name   ₹19.00
LC: 26-09-2026, 17:22 M
```

`M` means the last confirmed price was uploaded from a manager manual change.

If the confirmed price came from Admin Push and was uploaded successfully:

```
Store Name   ₹19.00
LC: 26-09-2026, 18:05
```

No `M`.

Important: `M` is only shown after the manual price is physically uploaded. Merely editing the phone price must not change Admin confirmed pricing.

## Source values

The existing price audit source values are:

- `MANUAL`
- `CSV`
- `ADMIN_PUSH`

The current Admin UI explicitly interprets `MANUAL` as `M` for a confirmed uploaded price. Admin Push has normal LC display.

## Current code changes pushed

Recent branch changes include:

- Admin Push pending price is returned by `admin_get_store_plu_prices()`.
- Android reads `pending_pushed_price`.
- Admin UI renders `(dd-MM)*(pendingPrice)`.
- Android reads `last_upload_source`.
- Admin UI renders `M` when the latest confirmed uploaded source is MANUAL.
- Supabase Admin store-price function derives `last_upload_source` from `store_price_change_log`.
- Existing Essae transport was not modified.

## Required live SQL action

The repository SQL is not the live Supabase database.

Before testing the latest UI/database behavior, apply:

`supabase/MahaMart_ADMIN_PUSH_LC_AND_UPLOAD_MIGRATION.sql`

in the live Supabase SQL Editor.

This migration currently:
- keeps Admin current price tied to physical upload;
- returns pending Admin Push date + price;
- returns `last_upload_source`;
- updates current price only in `complete_scale_upload()`;
- marks matching Admin Push device state uploaded after successful physical upload.

## Android build history — latest issue resolved

A local `StoreAdminPushSync.kt` copy had a malformed JSONTokener fallback and end-of-file quote/brace errors. The authoritative GitHub file was restored/fixed.

Correct JSONTokener fallback:

```kotlin
JSONTokener(response.ifBlank { "\"\"" }).nextValue()
```

The end of the file must be:

```kotlin
        return response
    }
}
```

A later Gradle failure was a Windows/KAPT build-directory file lock, not a Kotlin source problem.

## Testing order

Do not test all 17 stores first.

1. Build APK.
2. Apply live SQL migration.
3. Test one PLU + one store.
4. Admin Push price → store sync → verify RED.
5. Restart store app → RED must persist.
6. Admin must still show old confirmed price plus pending date/price.
7. Upload to Essae → verify Admin current price changes and pending marker disappears.
8. Manual manager price change → upload → verify `M`.
9. Admin Push price → upload → verify no `M`.
10. Test failed physical upload → confirmed price must not change and RED must remain.
11. Then test multiple stores/devices.

## Store/device architecture

Permanent identity is store code, not IP.

Current registration architecture:
- one-time registration code;
- store code + Android device ID binding;
- multiple devices per store;
- IP is audit/mapping information;
- Store app has no normal login;
- Admin has Supabase Auth.

Verified test device:
- MM017 Vidya Nagar
- sdk_gphone16k_x86_64
- device ID 7bf621a9c15a33ea
- IP 10.0.2.15

## Security

Never commit:
- service-role key;
- Supabase password;
- `local.properties`;
- signing credentials;
- other secrets.

Client Android uses publishable/anon key only.

## Resume instruction for a new chat

If a new conversation starts, say:

> Continue MahaMart Scale Manager from the 2026-09-26 stable checkpoint. Read `docs/project-brain/19_STABLE_CHECKPOINT_2026-09-26.md` first, then inspect the current branch before changing code.

Then inspect GitHub rather than reconstructing the project from memory.

## Do not change without explicit need

- `EssaeTransport.kt`
- stable TCP protocol
- store identity model
- confirmed-price semantics
- Admin Push pending semantics
- physical-upload confirmation rule

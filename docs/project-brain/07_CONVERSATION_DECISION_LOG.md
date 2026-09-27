# Conversation Decision Log

Important project decisions preserved from development:

- Started from a stable Essae Android v4 base.
- Supabase was added as an audit/admin layer rather than replacing local operation.
- An earlier oversized Supabase/manager-login architecture was abandoned.
- Store login was rejected; stores operate without login.
- Admin login was retained and later hidden behind a 10-second long press on Scale Manager.
- Admin Push was removed during iteration and later restored.
- Revert-on-restart behavior was rejected because pending price state must survive until the upload result is known.
- Version 4.1.0 was considered broken and is not the stable reference.
- Several Gradle/import issues were fixed without changing the core architecture.
- Correct Supabase project URL was restored after a wrong-host DNS error.

## 2026-09-26 — Final confirmed-price / pending-price model

- store_price_current is the confirmed physical-scale price.
- Admin Push does not change confirmed price.
- Store Sync changes local Room price and creates RED/PENDING state.
- Pending Admin Push is displayed as LC: confirmed (dd-MM)*(newPrice).
- Successful physical Essae upload changes the confirmed price and removes the pending marker.
- Failed physical upload leaves confirmed price and pending state unchanged.

## 2026-09-26 — Manual manager source marker

- Existing audit source MANUAL is now surfaced in Admin pricing.
- A confirmed price whose latest uploaded source is MANUAL is displayed as LC: date/time M.
- Admin Push confirmed uploads have normal LC and no M.
- The M marker appears only after successful physical scale upload, not merely after local editing.

## 2026-09-26 — Durable conversation checkpoint

- Added docs/project-brain/19_STABLE_CHECKPOINT_2026-09-26.md.
- This file is the first-read source when a future conversation needs to resume the project after a conversation limit.


## 2026-09-27 — Device-token security validated

- Existing test phone was re-registered successfully after the live pgcrypto search-path issue was corrected.
- Live pgcrypto is installed in the `extensions` schema; token registration functions use `search_path = public, extensions`.
- Corrected migration commit: `773e1f9bcc947c06625329d13fc5cfda2da8cbb2`.

## 2026-09-27 — Admin Push notification branch

- Created `feature/admin-push-notifications` from `feature/admin-device-registration`.
- Stable parent branch remains untouched.
- Desired notification: when a registered store device receives an Admin Push, show a notification telling the store that a new price update was received and must be uploaded to the scale.
- Notification is only an alert. Supabase remains the source of truth; missed notifications must never lose a price update.

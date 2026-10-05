# MahaMart Essae — CHAT HANDOFF / CONVERSATION BRAIN

This file is intentionally written as a practical memory for a new ChatGPT chat. It captures the important conversation history, decisions, debugging discoveries, and user expectations that matter for continuing the project.

## How the user works
- User prefers “bro” style and fast, direct answers.
- They often test live on Android devices and Supabase while chatting.
- They want exact actionable steps, not generic explanations.
- Never ask them to repeat information already in this handoff.
- Do not expose credentials/tokens.
- User expects the assistant to inspect the repo and Supabase before proposing changes.

## Project history / major feature work
- Started with stable Essae Android project.
- Added Supabase cloud logs without moving core PLU operation out of local Room.
- Added device registration and store mapping.
- Added Admin Push from admin to store device.
- Added Admin Push history and manager push history.
- Added upload confirmation tracking.
- Added reports/export requirements.
- Added background Admin Push polling.
- Added Firebase notification work.
- Added admin hidden access by 10-second hold.
- Added app version 1.1.0.
- Added device ID protection requirement with PIN 1413.
- Added multi-instance Room invalidation.
- Fixed several Gradle/build issues.
- Fixed Room migration issues including PriceAudit table/version.
- Tested app on multiple devices.

## Important past bugs
1. Room migration error because schema changed without version/migration update.
2. Gradle directory deletion/build problems.
3. Admin Push notifications only arriving while app open.
4. Admin Push pending count became stale.
5. Repeated pushes for same PLU caused older values to resurface.
6. PostgreSQL RPC failed with 400 due ambiguous `created_at`.
7. Manager Push report was blank.
8. Price=0 handling was missing and was added.
9. Device/store code/name mapping was initially null but registration/database mapping was corrected.
10. CSV import could leave unexpected update counts.

## Admin Push debugging story
The major conceptual mistake was treating every historical Admin Push as independently actionable.

Example:
- Push 100.
- Push 1.
- Upload 1.
- Old 100 must never come back.

The correct cloud algorithm is latest-per-PLU first, then compare that latest price with Store Master.

The Android side has a second problem: Room can retain an old price even when cloud returns no actionable Admin Push. Therefore the app needs reconciliation of confirmed Store Master state.

## Red-marker story
The red marker is not cloud truth by itself. It is local UI state derived from pending local audits.

Bad behavior:
- physical upload succeeds;
- app calls `markAllPendingUploaded()`;
- app sets `changedPluNumbers = emptySet()`;
- a newer/unrelated pending Admin Push can disappear from the UI.

Correct behavior:
- complete the exact uploaded PLU/price audit(s);
- reload remaining pending audits;
- rebuild red markers from those audits.

## Supabase observations
- Device auth is currently working.
- `last_seen_at` proves successful RPC authentication.
- Store Master for current test PLU 1 was verified at ₹1 after physical upload.
- `admin_price_update_targets` supports PENDING/SYNCED/UPLOADED/SUPERSEDED.

## Relevant code locations
- `app/src/main/java/com/mahamart/essae/cloud/StoreAdminPushSync.kt`
- MainActivity/ViewModel where Admin Push sync and scale upload are triggered.
- Room entities/DAOs for PLU and PriceChangeAudit.
- Supabase SQL/RPC functions listed in PROJECT_BRAIN.

## Git history/context
Important recent branches/commits mentioned during development:
- `feature/admin-device-registration`
- `feature/admin-push-notifications`
- `ChatGPT-sugegstions`
- `fix/admin-operations-stale-pending`
- `feature/qr-device-registration`
- `feature/crash-free-audit`
- `feature/background-admin-price-sync-2026-10-04`

A previous reset to commit `a86e79d` occurred during development. The current handoff branch later reached `7d3e2e97411996fe85ce744f741046eb4c430fae`.

## User's other retail/business context relevant to the project
- Mahalaxmi Mahamart is a retail supermarket chain.
- Multiple stores use the scale manager.
- Store-wise pricing is required.
- Store code mapping is important.
- Offline/local operation matters.
- CSV/PLU master files are used.
- User wants Excel reports and store-level reporting.

## Next-chat startup script
When a new chat begins, the user may simply say “continue Essae”. Do this:
1. Read these four docs.
2. Inspect current Git branch/SHA.
3. Inspect actual relevant Kotlin files.
4. Inspect live Supabase functions/tables before modifying anything.
5. State the exact current state in 3–5 bullets.
6. Continue from unresolved items; do not restart the project.

## Important caution about chat completeness
This document preserves the project-relevant brain available to the assistant at handoff. It is not a literal export of every historical ChatGPT message. The actual repo, SQL, commits, screenshots, and these handoff files are the authoritative continuation artifacts.

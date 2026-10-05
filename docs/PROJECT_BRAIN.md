# MahaMart Essae — PROJECT BRAIN

> Master handoff for continuing development in another ChatGPT conversation.
> Repo: https://github.com/rajuuddanti/Essae
> Active branch at last handoff: `feature/background-admin-price-sync-2026-10-04`
> Last known SHA: `7d3e2e97411996fe85ce744f741046eb4c430fae`

## 1. Product
- Android app: **MahaMart Scale Manager**
- Package: `com.mahamart.essae`
- Purpose: manage Essae weighing-scale PLU data, labels, store pricing, Admin Push, physical scale uploads, audit/reporting.
- Supabase is used for cloud state/logs/admin operations; PLU data is primarily local on the device.
- Stores do not require login. Admin requires login.
- Admin entry was moved off the main screen and is accessed by holding “Scale Manager” for 10 seconds.

## 2. Core architecture
- Android/Kotlin.
- Room local DB for PLU and audit state.
- Supabase REST/RPC for cloud operations.
- `EssaeTransport` handles scale communication.
- `SupabaseRest` handles REST/RPC.
- `StoreAdminPushSync` handles background Admin Push pull/apply/ACK.
- Main/ViewModel owns UI state including changed PLU markers.
- Room multi-instance invalidation was enabled.

## 3. Pricing model
There are multiple price layers and they must not be confused:
1. Store Master / cloud confirmed price.
2. Admin Push price waiting to be uploaded to physical scale.
3. Local Room PLU price displayed by the app.
4. Physical scale price.
5. Local/cloud audit history.

**Rule:** an Admin Push changes the device PLU locally first. Physical scale upload is the confirmation step. A confirmed physical upload updates cloud Store Master and marks the matching Admin operation uploaded.

## 4. Admin Push semantics
- Multiple Admin Pushes for the same PLU can happen rapidly.
- Only the **latest actionable Admin Push per store + PLU** should drive the device.
- Older pushes must not reappear after a newer push has been uploaded.
- Ordering must use `created_at DESC, id DESC` to break timestamp ties.
- When a newer price supersedes an older pending/synced price, older cloud target/audit records should be represented as `SUPERSEDED` where appropriate.
- Do not treat an old 100 price as current merely because it remains in a historical log.

## 5. Device registration
Current known test device:
- device ID: `f0d40d646691b3f6`
- IP: `192.168.31.185`
- model/user-agent: `24108PCE2I`
- store ID: `9295ec05-2091-4981-a51b-1d65b4b1a7d3`
- store code: `MM006`
- store: Gangadhara New

Registration SharedPreferences:
- file: `store_device_registration`
- key: `device_token`

Do not ask the user to re-register unless investigation proves registration is broken. Current auth succeeds.

## 6. Device authentication
Function: `verify_store_device_token(p_device_id, p_device_token)`.
- Requires non-empty device ID/token.
- Hashes supplied token with SHA-256 and compares against `store_devices.device_token_hash`.
- Returns store UUID.
- Current device authentication is known to work because RPC calls update `last_seen_at`.

## 7. Important database tables
- `stores`
- `store_devices`
- `admin_price_updates`
- `admin_price_update_targets`
- `admin_price_update_device_state`
- `store_price_master`
- `store_price_current`
- `store_price_change_log`
- `scale_upload_sessions`

## 8. Current pending-price RPC
Function: `store_get_pending_admin_price_updates_v2(p_device_id, p_device_token, p_device_ip)`.

Current intended algorithm:
1. Authenticate device.
2. Update device IP/last_seen.
3. Collect all non-UPLOADED target updates for the store.
4. Expand each JSON item.
5. For each store + PLU choose latest update using:
   `ORDER BY update_created_at DESC, update_id DESC`.
6. Only after selecting latest-per-PLU compare its new price against `store_price_master.master_price`.
7. Return only actionable latest rows.

This ordering is critical. The old implementation filtered by Store Master before selecting latest, which allowed an older price to resurface after a newer price had already become master.

A PostgreSQL error was also fixed by aliasing `u.created_at` to `update_created_at` because an unqualified `created_at` became ambiguous inside the CTE/RETURN QUERY.

## 9. Current physical upload function
Function: `complete_scale_upload(p_session_id,p_device_ip,p_device_id,p_device_token,p_scale_ip,p_items)`.

It:
- authenticates device;
- validates upload session;
- processes each uploaded PLU/price;
- upserts Store Price Current/Master;
- marks matching pending price-change log as UPLOADED;
- marks matching Admin Push device state UPLOADED;
- updates Admin Push targets where appropriate;
- closes the scale upload session.

Important: broad local cleanup in the Android layer currently calls `auditDao.markAllPendingUploaded()` and clears `changedPluNumbers`. This is too broad and is a known bug area. Cleanup must be tied to the actual uploaded PLU + uploaded price, then re-query remaining pending audits.

## 10. Local Admin Push sync
File: `app/src/main/java/com/mahamart/essae/cloud/StoreAdminPushSync.kt`.

Important behavior:
- Uses process-wide Mutex to prevent overlapping pulls.
- Reads registered device token from SharedPreferences.
- Calls `store_get_pending_admin_price_updates_v2`.
- Sorts returned updates by created_at/update_id.
- Applies each item to Room.
- Verifies saved price.
- Inserts local `PriceChangeAudit` if needed.
- ACKs cloud update IDs.
- MainActivity/ViewModel runs sync at startup, on resume, and roughly every 10 seconds.

## 11. Red marker
`MainVm.changedPluNumbers` is in-memory.
- Startup starts empty.
- After successful sync it is rebuilt from pending local price-change audits.
- Physical upload currently clears it blindly.

Correct behavior:
- Red marker means there is a locally pending Admin Push that has not yet been confirmed uploaded.
- After upload, only matching uploaded audit rows should be marked uploaded.
- Then reload pending PLU numbers from Room/cloud.
- Never use `markAllPendingUploaded()` for one physical upload if unrelated Admin Pushes remain.

## 12. Latest real test state
Stress sequence on PLU 1 / SUGAR LOOSE included rapid Admin Push values around:
- 878/879
- 52/789 depending test sequence
- 89
- 989
- 100
- 1

Cloud Store Master was confirmed:
- PLU 1 = **₹1.00**
- source = `ADMIN_PUSH`
- updated at `2026-10-05 07:34:46.771532+00`

At one point device Room still displayed ₹100 while cloud Master was ₹1. This established the need for local reconciliation when cloud says a newer confirmed state but the local Room has stale data.

A later `store_price_change_log` row showed a 1→100 ADMIN_PUSH PENDING record around 07:40 UTC, so before changing logic always query `admin_price_updates` itself to determine whether a genuinely newer Admin Push was created after the ₹1 upload. Do not assume every ₹100 log is stale.

## 13. Correct reconciliation design
When the device pulls Admin Push:
- If latest Admin Push differs from Store Master: apply it locally and show red pending.
- If latest Admin Push equals Store Master: it is already confirmed; do not recreate an old pending state.
- If local Room differs from confirmed Store Master and there is no newer actionable Admin Push: reconcile Room to confirmed cloud price without creating a pending audit.
- After physical upload: use the exact uploaded PLU/price list returned/confirmed by cloud to reconcile Room and audits.

Best implementation is explicit cloud reconciliation rather than guessing from old audits.

## 14. Critical SQL status values
Admin target status supports:
- PENDING
- SYNCED
- UPLOADED
- SUPERSEDED

Use SUPERSEDED for obsolete Admin Pushes rather than leaving them looking pending forever.

## 15. UI/report requirements
Admin should have:
- Admin Push tab/history.
- Pending prices.
- Manager Push history.
- Upload history.
- Reports tab.
- Excel export.
- Store/device mapping.
- IP-based store identification in logs.

Store app:
- no login;
- device registration;
- preloaded PLU list;
- two label designs by default: Weight Only and Weight + ₹ Price;
- scale connection error should match Test Connection error wording;
- app version target was changed to 1.1.0.

## 16. Data safety rules
- Do not alter stable functionality unless specifically requested.
- Do not reinstall/clear app data as a debugging shortcut.
- Changing device ID must require PIN 1413 and must NOT clear all device data.
- Offline operation is important.
- CSV import must establish master/store price correctly at import time.
- Physical scale upload is the authoritative confirmation event for a price pushed to the scale.

## 17. Known unfinished/review items
- Full crash-free audit of Android code + Supabase DB/functions/RLS/permissions.
- Re-audit all Admin Push status transitions.
- Fix exact pending/superseded semantics.
- Fix local Room reconciliation.
- Fix red marker cleanup so unrelated pending changes are never hidden.
- Finish/verify Admin web console.
- Verify Manager Push report is populated.
- Keep Firebase notifications reliable even when app is closed/backgrounded.

## 18. Development workflow
Before changing code:
1. Inspect current branch and latest commit.
2. Inspect both Android code and relevant Supabase functions/tables.
3. Reproduce with one PLU and one store.
4. Query cloud state before modifying data.
5. Make one logical change at a time.
6. Build APK.
7. Test install/startup.
8. Test rapid repeated Admin Pushes.
9. Test physical upload.
10. Test restart/background/resume.
11. Re-query Supabase after each transition.

## 19. Current known branch
`feature/background-admin-price-sync-2026-10-04`
Last known SHA: `7d3e2e97411996fe85ce744f741046eb4c430fae`

## 20. Handoff instruction for the next ChatGPT
Start by reading:
1. `docs/PROJECT_BRAIN.md`
2. `docs/DECISIONS.md`
3. `docs/CURRENT_STATE.md`
4. `docs/CHAT_HANDOFF.md`

Then inspect the actual code/DB. Do not assume the brain file is newer than the repo. The repo and live Supabase state are authoritative for implementation; this file preserves reasoning and decisions from the conversations.

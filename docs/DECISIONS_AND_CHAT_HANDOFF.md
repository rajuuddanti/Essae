# MAHAMART SCALE MANAGER — DECISIONS / CHAT HISTORY HANDOFF

This file is the compact decision log for continuing the project in a new ChatGPT conversation.

## NON-NEGOTIABLE PROJECT RULES
- Keep the last stable Essae Android v4 / v4.1.0 base.
- Do not change UI, architecture, names, or unrelated features unless explicitly requested.
- Preserve complete local PLU data.
- Admin/manager price changes are store-specific.
- Every SKU has a real price.
- Price 0 is not "no price"; it may only be used for label/display behavior.
- Admin Push changes only affected SKUs.
- Upload All sends the complete local dataset to the physical scale.
- Supabase logs/audits changes.
- Do not expose tokens or credentials.
- Targeted fixes only.

## MAJOR WORKSTREAMS COMPLETED / DISCUSSED

### Essae scale protocol
- ETLDRV protocol captured.
- 10 PLUs/frame.
- Incrementing sequence.
- Per-frame ACK.
- 4-byte IEEE-754 prices.
- Header/checksum behavior established.
- PCS/UOM disabled.
- Test 6 revealed ACK validation must not expect one fixed checksum.
- Test 7 corrected frame-dependent ACK handling.
- 133-PLU WEIGH dataset used/planned.
- Do not regress transport.

### Supabase/device registration
- Device registration implemented.
- store_devices links device to store.
- Device token stored in SharedPreferences.
- verify_store_device_token validates hash.
- Store/device identity is server-derived from authenticated token.
- Current MM006 test device is f0d40d646691b3f6.
- Current store is Gangadhara New / MM006.

### Admin Push
- Admin login and push UI exist.
- Admin Push history exists.
- Device-side background sync exists.
- RPC polling is roughly every 10 seconds and on resume/startup.
- Mutex prevents concurrent pull/apply.
- Local PriceChangeAudit tracks Admin Push state.
- Cloud audit/logging records changes.
- Physical upload confirms actual scale state.

### Reports
Tabs:
CURRENT / CHANGES / UPLOADS / PUSH

Admin Push history and Manager Push history were tested.
Manager push report previously had a blank issue and was targeted for fixing.
Excel export is part of the intended admin reporting workflow.

### Notifications
Firebase was configured for live notifications.
A previous test found notifications arriving only while app was open.
This is unresolved unless later verified.

### Version
User requested app version 1.1.0.

## GIT / BRANCH DECISIONS

Relevant branches:
- feature/admin-push-notifications
- ChatGPT-sugegstions
- fix/admin-operations-stale-pending
- feature/qr-device-registration
- feature/crash-free-audit
- feature/background-admin-price-sync-2026-10-04

Current work branch:
feature/background-admin-price-sync-2026-10-04

Known current HEAD before handoff docs:
7d3e2e97411996fe85ce744f741046eb4c430fae

Recent concepts/commits:
- Fix stale Admin Operations state
- Admin Push history
- Manager Push history
- missing price=0 logic
- Admin Push ordered loop syntax
- multi-instance Room invalidation

Do not reset or force-push.

## ROOM DECISIONS

Room schema changes require version increment and migration.
A previous crash occurred because schema changed without version increment and PriceAudit migration was incomplete.

Current Room has PLU and PriceAudit/local audit state.
Multi-instance invalidation was enabled.

When fixing Admin Push:
- never duplicate local audits
- never manufacture an audit merely to reconcile a stale Room value
- local pending marker must come from persistent audit state, not only an in-memory set

## ADMIN PUSH ORDERING DECISION

The most important business rule:

For each store + PLU, determine the newest Admin Push FIRST.

Then compare its price with Store Master.

Never:
1. filter old rows by current master
2. then choose latest

That caused old prices to resurrect.

Correct:
1. all candidate rows
2. latest per PLU
3. compare latest to master
4. return latest only

Tie-breaker:
created_at DESC, update UUID/id DESC.

## SUPERSEDE DECISION

For a same-PLU sequence:
879 -> 789 -> 100 -> 1

After 1 is physically confirmed:
- 1 = UPLOADED
- 100 = SUPERSEDED
- 789 = SUPERSEDED
- 879 = SUPERSEDED

No old row can become actionable again.

Use existing SUPERSEDED status rather than inventing another state.

## PHYSICAL UPLOAD DECISION

Physical upload is the source of truth for what reached the scale.

After successful upload:
- cloud current/master must match uploaded value
- matching Admin Push device state is uploaded
- local pending marker should clear only for the confirmed values
- unrelated pending Admin Pushes must remain pending

Do not use blanket:
auditDao.markAllPendingUploaded()

Instead use targeted reconciliation/reload.

## RED MARKER DECISION

The red PLU marker means there is a genuine local pending price change that still needs physical upload.

It must NOT mean:
- any historical Admin Push exists
- any cloud audit exists
- an older superseded push exists

It must survive app restart by being reconstructed from Room.

It must turn off when the matching price is physically confirmed.

## CURRENT FAILURE UNDER INVESTIGATION

Observed:
Admin says ₹1 -> ₹100 PENDING.
Device PLU says ₹100.
Device red marker is missing.
Physical scale was reportedly uploaded with ₹1.

Cloud master confirmed ₹1 at 07:34:46 UTC.

A later store_price_change_log entry showed ₹1 -> ₹100 PENDING at 07:40:46 UTC.

Therefore the first thing in the next chat is to determine whether there was a genuinely NEW admin_price_updates row for ₹100 after the ₹1 upload.

Do not assume.
Do not reinstall.
Do not re-register.
Do not change SQL blindly.

## POSSIBLE OUTCOMES

### Outcome A: newer ₹100 Admin Push exists
Then:
- Device ₹100 is correct.
- Admin pending ₹100 is correct.
- Only red marker is broken.
Fix marker reconstruction/clearing.

### Outcome B: no newer ₹100 Admin Push
Then:
- Device ₹100 is stale.
- Admin pending ₹100 is stale.
- Cloud ₹1 is authoritative.
Fix local reconciliation and supersede stale audit rows.

## CRASH-FREE TEST PLAN

After the current bug is fixed:
1. Fresh install.
2. Register device.
3. Pull base PLU data.
4. Admin push one SKU.
5. Verify Room.
6. Verify red marker.
7. Upload physical scale.
8. Verify cloud.
9. Verify red marker clears.
10. Restart app.
11. Verify Room/cloud consistency.
12. Push same SKU rapidly multiple times.
13. Upload newest only.
14. Verify old pushes are SUPERSEDED.
15. Background sync.
16. Offline then online.
17. Multiple devices.
18. Verify no old price resurrection.
19. Verify Admin pending count.
20. Verify no Room migration crash.

## DO NOT FORGET
- User wants targeted engineering, not redesign.
- User expects direct "bro" communication.
- User is continuing in another chat, so read docs/CHATGPT_BRAIN.md and docs/SUPABASE_DEBUG_STATE.md first.
- The docs are the project handoff; continue from them instead of asking the user to repeat history.

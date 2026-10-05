# MahaMart Essae — DECISIONS LOG

## Core decisions

### D001 — Stable base is sacred
Do not modify unrelated stable functionality. Changes must be narrow and directly tied to the requested feature/bug.

### D002 — Store devices do not log in
Only Admin requires login. Store devices authenticate using registered device ID + device token.

### D003 — Device registration token storage
SharedPreferences file: `store_device_registration`; key: `device_token`.

### D004 — Physical scale upload is confirmation
Admin Push is not considered physically completed merely because the phone Room changed. The physical Essae upload must succeed and cloud must record the upload.

### D005 — Latest Admin Push wins per PLU
For the same store + PLU, only the latest Admin Push should be actionable. Use `created_at DESC, id DESC`.

### D006 — Select latest before comparing to Store Master
Never filter old updates by Store Master and then choose latest. That caused old prices to resurface.

### D007 — Superseded status exists for obsolete target records
Use `SUPERSEDED` for Admin Pushes replaced by a newer operation when they should no longer appear pending.

### D008 — Local Room must not resurrect cloud history
Historical Admin Push records must never overwrite a newer confirmed local/cloud price.

### D009 — Red marker is derived state
The red PLU marker should represent actual pending local Admin Push state, not simply “an upload happened”.

### D010 — Never mark all audits uploaded blindly
The old `auditDao.markAllPendingUploaded()` after any successful physical upload is too broad. Only uploaded PLU/price records should be completed.

### D011 — Reconciliation must be explicit
If cloud Store Master says ₹1 but Room still says ₹100 and no newer actionable Admin Push exists, reconcile Room to ₹1 without creating a new pending audit.

### D012 — Do not re-register to fix data bugs
Authentication is currently working. Re-registration is not a solution for stale Room/cloud state.

### D013 — Device ID change protection
Changing device ID requires PIN `1413`; changing device ID must not clear all device data.

### D014 — Admin access UX
Admin is hidden from the main screen and accessed by holding Scale Manager for 10 seconds.

### D015 — Label defaults
Two default label designs: Weight Only and Weight + ₹ Price.

### D016 — Reports
Admin needs push history, manager history, upload history, reports and Excel export.

### D017 — App version
Target app version was changed to 1.1.0.

## Debugging decisions

### D018 — SQL ambiguity fix
In `store_get_pending_admin_price_updates_v2`, qualify/alias `u.created_at` as `update_created_at` to avoid PostgreSQL ambiguous `created_at` errors.

### D019 — Mutex for background sync
Admin Push pulls are protected by a process-wide Mutex to prevent overlapping pulls/apply cycles.

### D020 — Verify saved Room price
After applying an Admin Push item, read the saved PLU back and verify its price before treating the operation as successful.

### D021 — Do not guess from store_price_change_log
A change-log row may not be enough to determine whether a genuinely newer Admin Push exists. Query `admin_price_updates` + target/device state when sequence matters.

## Testing decisions

### D022 — Rapid repeated-price testing is mandatory
Test sequences such as 879 → 789 → 100 → 1 on the same PLU. Also test repeated identical values and pushes while the app is open/backgrounded.

### D023 — Test after physical upload
After upload verify all four independently:
1. physical scale,
2. cloud Store Master,
3. device Room PLU,
4. red/pending marker.

### D024 — One change at a time
When debugging crash/state problems, make one DB/code change, build, install/test, then continue.

## Current unresolved decision
The exact treatment of a later 1→100 Admin Push after a 1 upload must be based on its actual `admin_price_updates.created_at`. If it truly happened later, ₹100 on device is expected and only the red marker may be wrong. If no later Admin Push exists, ₹100 is stale Room state and reconciliation must force Room to ₹1.

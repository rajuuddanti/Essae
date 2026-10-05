# MahaMart Essae — CURRENT STATE

Date of handoff: 2026-10-05

## Repo
- Repository: `rajuuddanti/Essae`
- Branch: `feature/background-admin-price-sync-2026-10-04`
- Last known SHA: `7d3e2e97411996fe85ce744f741046eb4c430fae`

## Current device/store
- Device ID: `f0d40d646691b3f6`
- Device IP: `192.168.31.185`
- Device model: `24108PCE2I`
- Store: Gangadhara New
- Store code: MM006
- Store UUID: `9295ec05-2091-4981-a51b-1d65b4b1a7d3`

## Confirmed live cloud state
For PLU 1:
- `store_price_master.master_price = 1.00`
- source = `ADMIN_PUSH`
- updated_at = `2026-10-05 07:34:46.771532+00`

This was the price confirmed by physical upload.

## Important recent sequence
Rapid Admin Push tests produced several values. One known cloud sequence included approximately:
- 878 → 52 → 89 → 989 → 100 → 100 → 1
- latest known upload for PLU 1 was ₹1.

At one point:
- Admin showed a pending ₹1 → ₹100 Admin Push.
- Device PLU list showed ₹100.
- Red marker was absent.
- Cloud Store Master was ₹1.

There was also a `store_price_change_log` entry around 07:40 UTC for ₹1 → ₹100 ADMIN_PUSH PENDING. This MUST be checked against `admin_price_updates` before deciding whether it is a genuine later push or stale audit data.

## Current bugs under investigation
### Bug A — obsolete Admin Push resurfaces
Previously the pending RPC selected old updates after filtering against Store Master. Fixed conceptually by selecting latest-per-PLU first.

### Bug B — local Room can remain stale
When cloud says latest confirmed price is ₹1 and no actionable Admin Push is returned, Room may still contain ₹100. There is currently no robust cloud-to-Room reconciliation path for this case.

### Bug C — red marker can disappear incorrectly
After physical upload, Android currently does:
- `auditDao.markAllPendingUploaded()`
- `changedPluNumbers = emptySet()`

This can hide unrelated/new pending Admin Pushes.

### Bug D — pending cloud status can be misleading
Older Admin Push target/log records may remain PENDING unless explicitly superseded. Status transitions need an audit.

## SQL function status
### `store_get_pending_admin_price_updates_v2`
Current intended algorithm is latest-per-PLU then Store Master comparison. A previous PostgreSQL 42702 ambiguous `created_at` error was fixed by aliasing it.

### `complete_scale_upload`
Updates Store Master/current and matching upload state. It should be treated as authoritative physical upload confirmation.

### `verify_store_device_token`
Working. Dummy invalid token correctly raises Invalid device authentication. Current real device calls authenticate successfully.

## What NOT to do now
- Do not re-register device.
- Do not clear Room/app data.
- Do not reinstall as a “fix”.
- Do not manually edit Store Master just to make UI look correct.
- Do not mark all pending audits uploaded.
- Do not change stable code unrelated to this bug.

## Immediate next debugging query
Query Admin Pushes for PLU 1 after 2026-10-05 07:34 UTC, including:
- admin_price_updates.id
- created_at
- items/new_price
- admin_price_update_targets.status
- admin_price_update_device_state.status/synced_at/uploaded_at

Then compare with store_price_change_log timestamps.

## Success criteria
After a final test:
- Latest cloud confirmed price = physical scale price.
- Device Room price = latest cloud confirmed price unless a genuinely newer Admin Push is pending.
- Red marker exists exactly when a pending Admin Push exists locally.
- Admin pending list excludes superseded operations.
- Rapid pushes do not resurrect old values.
- App survives restart/background/resume.

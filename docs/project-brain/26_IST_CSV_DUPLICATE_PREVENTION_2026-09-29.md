# IST + CSV/Admin Push Same-Price Prevention — Checkpoint (2026-09-29)

Branch: `feature/ist-csv-duplicate-prevention`

## Purpose

This branch is a testing-only branch for the next project changes discussed after the Admin CSV Push work:

1. Prevent unchanged prices from becoming RED / PENDING.
2. Make manager-facing timestamps display in IST everywhere.

This branch is intentionally separate from:
- `ChatGPT-sugegstions`
- `feature/admin-web-console-testing`

No branch merge is planned.

## Same-price prevention requirement

The Admin will often push a complete CSV without manually removing SKUs whose prices are already correct at stores.

For every store and every SKU:

- If pushed Admin Push / CSV price equals the store's current **Store Master Price**:
  - do not create RED state;
  - do not create a PENDING scale-upload action;
  - no physical scale upload is required for that SKU.
- If pushed price differs from Store Master Price:
  - create the normal RED / PENDING workflow;
  - manager can upload the changed price to the physical scale.
- A SKU absent from the store's price master should be treated as a new/change case and remain actionable.

The comparison must be **per store + per PLU**.

## Why Store Master Price is the comparison layer

`store_price_master` is the Admin-visible current/master price.

`store_price_current` represents the last successfully uploaded physical scale price.

Therefore:

```
Admin pushed price
       ↓
compare with store_price_master.master_price
       ↓
SAME     → no RED / no pending action
DIFFERENT → RED / PENDING → physical scale upload
```

The physical scale may temporarily be behind the Store Master Price. That is a separate upload state and must not cause an unchanged Admin Push to become a new pending action.

## Preserve manager visibility

Same-price rows must not simply disappear from history.

The manager needs to know:
- what was pushed;
- what was already the same;
- what actually changed;
- what remains pending;
- what was physically uploaded.

The intended operational terminology is:

- **SAME / NO CHANGE** — pushed price already matched Store Master Price.
- **CHANGED / PENDING** — pushed price differed and needs physical scale upload.
- **UPLOADED** — physical Essae upload succeeded.
- Existing history must remain available for Admin Push/CSV operations.

The original Admin Push / CSV operation should remain auditable even when some rows require no action.

## Important backend behavior

Current implementation has a gap:

`StoreAdminPushSync.pullAndApply()` correctly avoids creating a local `PriceChangeAudit(PENDING)` when the local price already equals the incoming price, but it still sends the full item list to `log_pending_price_changes()`.

The live `log_pending_price_changes()` function currently creates/updates a PENDING cloud record for every supplied item without checking price equality.

Therefore same-price prevention needs to be enforced at the backend/device-sync boundary rather than relying only on the local Android comparison.

Preferred direction:

- filter actionable items using the store's `store_price_master`;
- only changed items enter the pending/upload workflow;
- unchanged items remain visible through operation/history/report data.

## Existing CSV semantics

Admin CSV Push is an update, not a full replacement.

A CSV containing only selected PLUs changes only those PLUs. Other store PLUs remain untouched.

A full CSV may therefore safely be pushed without manually removing rows whose prices are already correct.

## CSV duplicate validation

CSV parsing should continue to reject duplicate PLU rows rather than silently applying conflicting prices.

The distinction is:

- **duplicate PLU inside the CSV** → CSV validation problem;
- **same price already present at the store** → valid operation, but NO CHANGE;
- **different price at the store** → valid operation, becomes PENDING.

These are separate cases and should not be conflated.

## Repeated Admin Pushes

History must be retained, but current actionable state should represent the latest effective Admin instruction for a store + PLU.

Older Admin Push records remain historical records.

If a newer operation supersedes an older pending instruction, the older instruction should not create an additional current upload requirement.

## IST — project-wide display rule

All manager/admin-facing timestamps must display in:

**Asia/Kolkata (IST, UTC+05:30)**

This applies to:
- Admin Push history
- Admin CSV Push history
- Store Sync timestamps
- Scale Upload timestamps
- Last Seen
- Device registration
- Price history
- Store Operations
- Reports
- Reconciliation reports
- Notifications/history where timestamps are shown
- Label-related timestamps if introduced

PostgreSQL/Supabase should continue storing absolute timestamps as `timestamptz`.

The timezone conversion is for presentation/export, not for changing the stored instant.

## Android display rule

Use a central timestamp formatting utility rather than formatting timestamps independently throughout activities.

Preferred timezone:

```
ZoneId.of("Asia/Kolkata")
```

Avoid displaying raw UTC ISO strings directly to managers.

## Supabase/report display rule

For SQL/report outputs that are directly consumed by managers or exported for them, timestamps should be presented in IST where appropriate.

Do not change the underlying `timestamptz` storage model merely to change display timezone.

## Scope guardrails

- Do not rewrite `EssaeTransport.kt`.
- Do not change the physical Essae protocol.
- Do not merge this branch automatically.
- Do not alter the separate laptop web-console testing branch.
- Do not remove historical Admin Push/CSV records just because an item is SAME.
- Do not treat SAME price as a CSV error.
- Do not treat a full CSV as a full-store replacement.
- Keep database timestamps as absolute instants.

## Next implementation order

1. Inspect all Android timestamp formatting paths.
2. Add/use one central IST formatter.
3. Identify all manager-facing report timestamp outputs.
4. Change Admin Push/CSV same-price handling at the backend sync boundary.
5. Preserve SAME/NO CHANGE audit visibility.
6. Keep only changed prices in RED/PENDING workflow.
7. Validate duplicate PLU rows in Admin CSV.
8. Test across multiple stores and full CSV pushes.
9. Test restart, sync, pending, successful upload and failed upload behavior.
10. Run CI and keep this branch isolated for testing.


## Implementation checkpoint

Implemented on this branch:

- Added central Android `TimeFormat` utility using `Asia/Kolkata`.
- Admin Push and Store Operations screens now use the central IST formatter.
- Backend `store_get_pending_admin_price_updates_v2` now filters each SKU against `store_price_master.master_price`.
- SAME-price rows are returned as an empty actionable item list, allowing the device to ACK the Admin Push without creating RED/PENDING state.
- Android Store Sync now ACKs valid Admin Push updates even when the actionable item list is empty.
- Backend `log_pending_price_changes` has a second equality check so same-price rows cannot become PENDING even if an older client sends them.
- SAME-price outcomes are recorded as `NO_CHANGE` in `store_price_change_log` for manager visibility.
- The reconciliation report now exposes `NO CHANGE` when the corresponding no-action audit exists.
- CSV duplicate PLU validation already existed in Admin CSV Push and remains enforced.

Live Supabase migrations applied:
- `same_price_admin_push_prevention_2026_09_29`
- `same_price_admin_push_audit_2026_09_29`
- `admin_price_reconciliation_no_change_2026_09_29`

A current live-data comparison found 1 same-price Admin Push item and 472 changed-price items across existing Admin Push history. Existing historical same-price rows were not retroactively rewritten; NO_CHANGE auditing begins with the new behavior.

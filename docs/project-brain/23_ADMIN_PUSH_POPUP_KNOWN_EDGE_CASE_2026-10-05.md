# Admin Push Popup — Known Edge Case (2026-10-05)

## Reproduction
Store Master Price:
- PLU 1
- ₹59

Admin actions:
1. Push ₹591
2. Push ₹59 again

The second push is intentionally skipped because:
`Store Master Price = Pushed Price`.

## Actual result
The popup can still display:
`₹59 → ₹591`

## Why
The old ₹591 Admin Push remains as a local Room pending audit.
The later ₹59 push is skipped server-side, but the local stale audit is not necessarily reconciled/removed.
The popup therefore sees the older pending audit.

## Required fix
Do NOT change the working same-price validation.

Instead, reconcile the local pending Admin Push audits against the latest effective server/store state.

For each PLU:
- Determine the latest effective Admin Push price.
- Compare it with the current Store Master price.
- If equal, suppress/remove stale pending Admin Push popup state.
- If different, show only the latest effective change.
- Never resurrect an older Admin Push that has been superseded by a later same-price result.

Expected:
- 59 → 591 → 59 = NO POPUP
- 59 → 591 = POPUP: ₹59 → ₹591
- 59 → 591 → 600 = POPUP: ₹59 → ₹600

## Safety constraints
- No Room database version bump unless truly required.
- No data clearing.
- No Supabase schema change unless explicitly required.
- No Essae transport/protocol changes.
- No changes to working red-marker/upload behavior.
- No changes to Admin UI or reports.
- Keep the fix isolated to Admin Push popup reconciliation.

# 28_ADMIN_OPERATIONS_STALE_PENDING_INVESTIGATION_2026-09-29

## Current issue

MM017 / Vidya Nagar Admin Operations is showing old Admin Push entries as PENDING even though the current store device has no pending update / RED state.

Observed entries:
- PLU 1 SUGAR LOOSE — Admin Push ₹50 — pushed 29 Sep 2026 05:11 pm
- PLU 6 ALLAM NEW LOOSE — Admin Push ₹27 — pushed 28 Sep 2026 12:38 pm
- PLU 5 GODHUMA PINDI NO.1 LOOSE — Admin Push ₹7 — pushed 28 Sep 2026 12:24 pm
- PLU 2 PALLILU LOOSE — Admin Push ₹37 — pushed 28 Sep 2026 12:24 pm
- PLU 3 PESARA PAPPU NO.1 LOOSE — Admin Push ₹1 — pushed 27 Sep 2026 06:17 pm

The device itself currently has no corresponding pending/RED update.

## Important distinction

The phone's local pending state and the Admin Operations history are currently using different reconciliation signals.

The Android Admin Operations code currently treats an Admin Push as unresolved when the push report has status = SYNCED and uploaded_at is NULL. That is insufficient by itself. A historical push can remain in that state even after a later price became effective or after the device state was otherwise cleared/reconciled.

Therefore:
- Do not change the phone RED/pending mechanism to solve this issue.
- Do not blindly mark the five historical rows as uploaded.
- Reconcile each row against Store Master Price, physical scale-confirmed price, upload history, push/device state, and later pushes.

## Required reconciliation rule

An Admin Push should be shown as current PENDING only when it is still the active unresolved target for that store/SKU.

Older pushes for the same store/SKU should no longer appear as active pending when a newer effective price has superseded them.

Possible final states:
- PENDING — genuinely awaiting physical scale confirmation.
- SUPERSEDED — a newer effective price replaced this push.
- COMPLETED / uploaded — the corresponding physical upload is confirmed.
- NO_CHANGE — already matched the effective master/current price.

## Related LC issue

The Admin Push detail screen showed Vidya Nagar with LC 29-09-2026, 17:14 and PENDING ₹50 while the current confirmed price was ₹51 and the device was normal. This is the same reconciliation class: stale unresolved Admin Push history is being surfaced as active pending.

## Next action

1. Inspect the five MM017 records in the live database.
2. Compare each push with master/current scale price and upload history.
3. Reconcile only rows whose final state is provable.
4. Fix the Admin Operations query/backend rule so SYNCED + uploaded_at NULL alone does not mean active PENDING.
5. Retest MM017 without creating duplicate physical uploads.

Do not alter EssaeTransport.kt or the phone upload lifecycle for this issue.
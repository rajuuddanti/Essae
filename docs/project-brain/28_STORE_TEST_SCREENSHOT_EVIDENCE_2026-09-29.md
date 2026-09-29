# Store Test Screenshot Evidence — 2026-09-29

## Branch

`feature/crash-hardening`

## Evidence supplied by store test

Three screenshots were supplied from the current build on a store device.

### Screenshot 1 — Store Operations detail: MM017 Vidya Nagar

Visible state:
- Store: `MM017`
- Name: Vidya Nagar
- Pending Prices: `133`
- Visible rows are marked `PENDING`
- Visible examples:
  - SUGAR LOOSE — Admin Push ₹50.00
  - PALLILU LOOSE — Admin Push ₹160.00
  - PESARA PAPPU NO.1 LOOSE — Admin Push ₹125.00
  - Kandi pappu No.1 LOOSE — Admin Push ₹144.00
  - GODHUMA PINDI NO.1 LOOSE — Admin Push ₹44.00
  - ALLAM NEW LOOSE — Admin Push ₹100.00
- Visible status text: `Waiting for scale upload`
- Displayed push time: `29 Sep 2026, 04:11 pm`

### Screenshot 2 — Store Operations overview

Visible state:
- Stores: `17`
- Registered: `3`
- Pending: `3`
- MM015 Raikal — No device / No pending
- MM016 Vemulawada — 1 active device / `275 PENDING`
- MM017 Vidya Nagar — 2 active devices / `710 PENDING`
- MM017 last seen displayed as `29 Sep 2026, 04:28 pm`
- Last completed upload is shown as `—` for the visible stores.

### Screenshot 3 — Admin Push PLU screen

Visible state:
- PLU: `61 • 101311`
- Name: `KANDULU LOOSE`
- Master price: `—`
- New price field is empty
- Store list includes Keshavapatnam, Koheda, Mallial, Pegadapally New, Pegadapally Old, Raikal, Vemulawada, Vidya Nagar
- Vemulawada displays `₹0.00`
- Vidya Nagar displays `₹140.00`
- Vidya Nagar LC text is displayed as `29-09-2026, 16:10 (29-09)*(140)`
- Push button is disabled while no store is selected.

## Scope

This document records only what is visibly present in the supplied screenshots. It does not infer root causes or change the intended state machine.

## Next analysis targets

1. Explain why the pending counts differ between Store Operations overview and MM017 detail.
2. Check whether the visible `133` pending rows are expected current actionable SKUs or duplicated historical/pending records.
3. Check Admin Push PLU screen semantics for `Master price: —`, `₹0.00`, and LC values.
4. Reconcile these UI observations with the current backend and Android implementation.
5. Identify any additional issues visible in the screenshots before changing code.

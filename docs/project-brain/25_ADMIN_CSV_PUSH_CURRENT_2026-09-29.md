# Admin CSV Push — Current Checkpoint (2026-09-29)

Branch: `ChatGPT-sugegstions`

## Current feature

Admin Dashboard now includes **ADMIN CSV PUSH**.

The feature lets an authenticated Admin:
- select a price CSV;
- preview the imported SKUs and prices;
- select one, multiple, or all active stores;
- publish the CSV rows as an existing Admin Price Update;
- trigger the existing Admin Push notification flow.

No new Admin CSV database architecture was introduced. It reuses the existing `admin_publish_price_update` RPC and notification path.

## CSV behavior

Supported PLU headers include:
- `PLU_NO`
- `PLUNO`
- `PLU`
- `NUMBER`

Supported price headers include:
- `PRICE`
- `NEW_PRICE`
- `UNIT_PRICE`
- `UNITPRICE`

Product-name headers are optional.

The CSV must contain a valid price for every imported row. A blank price produces an error such as:
`Invalid price at CSV line 2.`

## Partial CSV push semantics

A CSV containing only a subset of PLUs updates only those PLUs.

Example:
- Store already has 117 PLUs.
- Admin CSV contains 4 PLUs.
- Pushing the CSV updates those 4 PLUs only.
- The other 113 existing PLUs are not deleted or replaced.

This is an **update**, not a full-store CSV replacement.

## Preview behavior

The CSV preview is a fixed-height scrollable list.

It now displays **all imported SKUs**, rather than showing only the first 20 followed by `...and N more SKUs`.

For example, a 133-SKU CSV displays all 133 rows inside the preview area and can be scrolled independently of the store-selection list.

## Admin Dashboard layout fix

The Admin Dashboard is vertically scrollable so lower controls such as STORE OPERATIONS, CLOSE, and LOG OUT are not clipped on shorter device screens.

The previous fixed `Spacer.weight(1f)` behavior was removed from the bottom section.

## Relevant commits

- `ad875783a848025caeb908e2d99cdf0e3a51be07` — Add Admin CSV price push screen
- `08dd6602ef767ad17443e3db4df7c7383c4b13bf` — Register Admin CSV Push activity
- `3579c4206be4a5114b2103eb7bc8da132420d49e` — Add Admin CSV Push entry
- `7d597ef31bae5082c5e5b04ab20cb48ad81ded03` — Make full Admin CSV preview scrollable
- `dd9f7c558484a5086cad6f0186c115fa56d9f033` — Make Admin Dashboard vertically scrollable

## Build status

Admin CSV Push commit `3579c420...` passed GitHub Actions run #56.

Dashboard scroll commit `dd9f7c558...` is the current branch head; its GitHub Actions run #57 was queued at the time this checkpoint was written.

## Guardrails

- Do not rewrite `EssaeTransport.kt`.
- Do not replace the existing Admin Push backend with a separate CSV backend unless a future requirement explicitly needs it.
- Do not treat partial CSV push as full-store replacement.
- Do not commit secrets.

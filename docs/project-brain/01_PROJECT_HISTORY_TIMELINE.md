# Project History and Decision Timeline

## Origin

The project started as an Android replacement for the existing
Windows/legacy workflow used to send price/PLU information to
Essae-Teraoka scales.

The core requirement was direct Android phone → Essae scale TCP/IP
communication.

The working base became `MahaMart Scale Manager v4`.

## Proven technical breakthrough

The team established the Essae communication behavior:

-   TCP port 4321.
-   143-byte PLU records.
-   Little-endian float price at offset +71.
-   10 records per frame.
-   Checksums.
-   Working Upload All.
-   Working direct price editing/upload.
-   Working generic LFT label upload.

This was the part the user repeatedly asked not to break.

## v4 stable baseline

Stable commit:

``` text
8bd68fc
icon added v4.0.0
```

Repo:

``` text
https://github.com/rajuuddanti/MahaMart-Essae-Final.git
```

## First cloud direction --- old architecture

The first Admin/Manager design used a larger set of tables:

-   stores
-   profiles
-   plu_master
-   price_update_batches
-   price_update_items
-   price_update_item_targets
-   store_sync
-   store_plu_prices
-   price_change_log
-   scale_upload_log

There were Admin and Manager accounts and manager-specific RPCs.

This caused complexity and "No profile found" issues.

The user then decided the manager-login architecture was unnecessary.

## Fresh Supabase reset

The user deleted the old tables and requested a clean database.

The new baseline became:

-   stores
-   profiles
-   store_ip_map
-   store_price_change_log
-   store_price_current
-   scale_upload_sessions

Views:

-   admin_current_prices
-   admin_price_change_report
-   admin_scale_upload_report

The fresh schema intentionally removed the old `price_update_*`,
`store_sync`, and `store_plu_prices` architecture.

## First fresh operating model

The first fresh model was:

-   store phone no login
-   local-first data
-   manual/local price edits logged
-   Admin Reports
-   current price only after successful scale upload
-   IP mapping

At that point Admin did NOT push prices to stores.

## Requirement reversal --- Admin Push returns

The user later explicitly restored Admin Push.

Final desired model:

``` text
Admin Push
↓
Store presses Sync
↓
local price changes
↓
RED/PENDING
↓
Upload All
↓
SUCCESS = confirmed
FAIL = remains RED
```

Instead of resurrecting the old huge manager system, a small Admin Push
layer was added:

-   admin_price_updates
-   admin_price_update_targets
-   admin_price_push_report

The migration also added `ADMIN_PUSH` as a price-change source.

## Restart behavior changed again

An earlier schema concept said:

-   pending local changes revert on app restart.

The user explicitly rejected that.

Final rule:

``` text
Restart before successful upload
→ RED/PENDING remains
```

Only physical scale upload success clears it.

The migration keeps the old revert RPC only for compatibility; Android
should not call it at startup.

## Manual audit requirement

The user asked:

> Can I know if managers manually changed prices without Admin Push?

Answer/decision:

Yes --- every price change must carry a source:

``` text
MANUAL
CSV
ADMIN_PUSH
```

The manual edit is persisted and audited.

## Reports UI decision

The first reports idea placed filters directly on the screen.

The user rejected the large filter area.

Final compact UI:

``` text
Today ▼                    FILTER
```

Filter popup:

-   Store
-   IP
-   SKU/PLU
-   Date
-   Status

Tabs:

-   Current
-   Changes
-   Uploads
-   Push

CSV export.

## Admin entry decision

Store phones should not show a login screen during normal use.

Admin entry is hidden behind a long press on the header.

The title was initially `MAHAMART`.

The user later specifically requested:

``` text
MAHAMART → Scale Manager
```

Same size/style as the previous title.

Long press on `Scale Manager` should still open Admin Login.

## Admin UI decisions

Admin dashboard requested:

-   Label Design icon
-   Reports icon
-   Sync/refresh
-   compact power/logout
-   Admin Push
-   Store/IP mapping

## Current failed implementation attempt

A 4.1.0 feature build was created around the stable v4 code.

It included:

-   manual audit
-   Supabase
-   Admin login
-   Sync
-   Admin Push
-   Reports
-   IP mapping
-   title change to Scale Manager

But it produced compile errors and later a runtime force close.

Therefore it is not the new stable baseline.

The stable v4 project must be used for the next implementation.

## Current working principle

The user strongly prefers:

-   one pasteable file
-   no unnecessary full-project ZIPs
-   no redesign of working features
-   exact code
-   minimal back-and-forth
-   test each layer before adding another

## Important phrase for resuming

"Last time bruh" means: avoid another architecture reset. Continue from
the decisions above.
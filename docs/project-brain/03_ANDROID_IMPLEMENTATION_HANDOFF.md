# Android Implementation Handoff

## Stable baseline

Use the user's last stable v4 project.

``` text
Repo:
https://github.com/rajuuddanti/MahaMart-Essae-Final.git

Commit:
8bd68fc
icon added v4.0.0
```

Do not use the broken 4.1.0 feature build as the baseline.

## Existing working functionality

-   direct Essae TCP connection
-   Upload All
-   price editing
-   CSV import
-   local Room PLU database
-   Label Design
-   generic LFT upload
-   existing UI/status behavior
-   clear local PLUs protected by PIN 3331 in the recent stable code

## Desired main store screen

Opening the app:

``` text
Scale Manager
↻   🏷
```

The title must not be enlarged just because the text changed from
MAHAMART.

The title is the same visual size/style as the old title.

Long-pressing the title opens:

``` text
Admin Login
```

Normal store use never shows a login.

## Sync button

Store must have a real manual Sync button.

It calls the Store-side Admin Push RPC.

Sync must:

1.  identify device IP/device ID
2.  find mapped store
3.  retrieve pending Admin Pushes
4.  apply prices to local Room
5.  create/maintain local RED/PENDING state
6.  acknowledge the Admin Push as SYNCED

Sync must NOT:

-   update `store_price_current`
-   count as physical scale upload
-   clear red state

## Manual price edit

When a store user edits a price:

1.  Validate numeric price.
2.  Update local Room.
3.  Persist a local audit row.
4.  Mark local price as RED/PENDING.
5.  Try cloud `log_pending_price_changes()`.
6.  If cloud fails, local audit remains and can retry later.

The user explicitly wants to know when managers manually change prices.

## Upload All

Before physical upload:

``` text
start_scale_upload()
```

Then run the existing proven Essae TCP upload.

Only after the TCP upload succeeds:

``` text
complete_scale_upload()
```

If it fails:

``` text
fail_scale_upload()
```

Do not clear local pending state on failure.

## Persistent pending state

Do NOT use only an in-memory `Set<Int>`.

The old implementation had:

``` text
changedPluNumbers
```

which was session-only.

Final implementation needs persistent local audit/pending data in Room.

The UI derives:

``` text
priceChanged = pending audit exists for PLU
```

## Admin login

Use Supabase Auth email/password.

Then verify:

``` text
profiles.role == ADMIN
profiles.active == true
```

Admin email previously established:

``` text
itteam@mahamartco.in
```

## Admin dashboard

Requested controls:

-   Label Design
-   Reports
-   Admin Push
-   Store/IP Mapping
-   Refresh/Sync
-   Logout

The store screen should not expose all of these.

## Admin Push

Admin enters:

``` text
PLU Number
PLU Name
New Price
Optional note
```

and chooses:

``` text
ALL STORES
```

or selected store codes.

The Admin Push RPC creates the cloud push header + target rows.

It does not immediately change Admin current price.

## Reports

Reports should be loaded only after Admin login.

Compact layout:

``` text
Today ▼                  FILTER
```

Tabs:

``` text
CURRENT | CHANGES | UPLOADS | PUSH
```

Filter popup:

``` text
Store
IP
SKU / PLU / name
From date
To date
Status
```

Export:

``` text
CSV
```

## Current Prices

Shows `admin_current_prices`.

Meaning:

**last successfully uploaded physical scale price.**

## Price Changes

Shows `admin_price_change_report`.

Important columns:

``` text
date/time
store
IP
PLU
name
old
new
source
status
scale IP
device ID
```

## Uploads

Shows `admin_scale_upload_report`.

Important:

``` text
started
completed
store
IP
PLU count
status
error
```

## Admin Push

Shows `admin_price_push_report`.

Important:

``` text
push time
admin
store
PLU/items
new price
target status
```

## Current known broken implementation

File:

``` text
MainActivity_4.1.0_TITLE_FIXED.kt
```

It is NOT the stable baseline.

The screenshot after attempting it showed:

``` text
Unresolved reference: pendingAudits
Unresolved reference: it
```

at the PLU list line.

The source contains:

``` kotlin
val pending = pendingAudits.any { it.pluNo == plu.number }
```

inside a `LazyColumn` item block, where the state reference was not
available in that scope in the compiled version.

There were also earlier ReportTab/type errors and a force-close after
installing a later attempt.

Do not continue patching blindly. Rebuild the feature layer cleanly from
the stable v4 baseline.

## Build.gradle

The feature attempt added:

``` kotlin
import java.util.Properties
```

This import belongs in:

``` text
app/build.gradle.kts
```

not the root `build.gradle.kts`.

Supabase values are loaded from:

``` text
local.properties
```

using:

``` text
SUPABASE_URL
SUPABASE_PUBLISHABLE_KEY
```

## Dependencies in the feature attempt

-   Compose BOM 2024.06.00
-   activity-compose 1.9.0
-   material3
-   lifecycle-viewmodel-compose 2.8.1
-   navigation-compose 2.7.7
-   coroutines-android 1.8.1
-   Room runtime/ktx/compiler 2.6.1
-   documentfile 1.0.1

Do not upgrade dependencies unless required.

## Development style requested by user

The user strongly prefers:

-   exact pasteable files
-   no entire project ZIP unless specifically requested
-   one change at a time
-   preserve stable code
-   no architecture resets
-   explain exactly where to paste a file
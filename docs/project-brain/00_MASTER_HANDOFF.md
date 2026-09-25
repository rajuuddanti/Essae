# MahaMart Scale Manager --- Project Handoff / Resume Pack

**Document date:** 2026-09-24\
**Purpose:** This is the single source-of-truth handoff for resuming the
MahaMart Scale Manager project in a new ChatGPT conversation without
re-explaining the history.

## 1. Project identity

-   Android app: **MahaMart Scale Manager**
-   Package: `com.mahamart.essae`
-   GitHub repo:
    `https://github.com/rajuuddanti/MahaMart-Essae-Final.git`
-   Original local project:
    `D:\Essae Android App\MahaMart_Scale_Manager_v4\finalprint`
-   Stable v4 Git commit: `8bd68fc` --- `icon added v4.0.0`
-   Working test scale:
    -   TCP port: `4321`
    -   Office/test scale IP used during development: `192.168.100.200`
    -   Do **not** assume store scale IPs.
-   Android baseline:
    -   compileSdk 34
    -   minSdk 26
    -   targetSdk 34
    -   Kotlin 1.9.22
    -   AGP 8.5.0
    -   Compose BOM 2024.06.00
    -   Room 2.6.1

## 2. What the app is

The app replaces the old Windows-based workflow for communicating
directly from an Android phone to an Essae-Teraoka weighing/label scale
over TCP/IP.

The proven working part is the Essae transport/protocol layer. The
cloud/reporting/admin features are an additional layer around the
existing local-first scale manager.

## 3. Golden rule for future development

**Do not replace or rewrite the working Essae protocol just to add
cloud/admin/reporting features.**

Use the last stable v4 project as the base and add the cloud/audit/admin
layer around it.

## 4. Final intended operating model

### Store phone

-   No store login.
-   App opens directly to Scale Manager.
-   Existing local CSV/PLU database remains on the phone.
-   Existing Label Design remains local.
-   Store can manually edit prices.
-   Store has a manual **Sync** button.
-   Sync pulls pending Admin Push changes from Supabase.
-   Sync is NOT a physical scale upload.
-   Synced/Admin-pushed prices become local and are marked **RED /
    PENDING SCALE UPLOAD**.
-   Manual edits are also audited as `MANUAL`.
-   Pending/red state must survive app close/reopen.
-   Upload failure leaves the red state.
-   Only successful physical Essae **Upload All** clears the red/pending
    state.
-   Successful physical upload updates the Admin current-price source of
    truth.

### Admin

-   Admin login required.
-   Store phones do not log in.
-   Hidden Admin entry: long-press the header title.
-   The title should be **Scale Manager**, not MAHAMART.
-   The title must keep the same size/style as the previous MAHAMART
    title.
-   Admin can:
    -   Push a price to all stores.
    -   Push a price to selected stores.
    -   View Reports.
    -   View/store IP mapping.
    -   Open Label Design.
    -   Refresh Admin data.
    -   Logout.

## 5. Critical price state machine

``` text
ADMIN PUSH
    ↓
STORE PRESSES SYNC
    ↓
LOCAL PRICE CHANGES
    ↓
RED / PENDING SCALE UPLOAD
    ↓
UPLOAD ALL TO PHYSICAL ESSAE SCALE
    ├── SUCCESS → NORMAL + Admin current price updated
    └── FAILED  → RED REMAINS
```

Manual change:

``` text
MANUAL EDIT
    ↓
LOCAL PRICE CHANGES
    ↓
RED / PENDING SCALE UPLOAD
    ↓
UPLOAD ALL
    ├── SUCCESS → NORMAL + current price updated
    └── FAILED  → RED REMAINS
```

If the user changes the same SKU more than once before upload:

``` text
₹100 → ₹60 → ₹55
```

The original confirmed baseline remains `₹100`; the pending record
should represent:

``` text
old_price = 100
new_price = 55
```

## 6. Authoritative price rule

`store_price_current` is the Admin source of truth.

It must represent the **last successfully uploaded physical scale
price**, not merely a synced or locally edited phone price.

Example:

``` text
Confirmed scale = ₹100
Admin pushes ₹60
Store presses Sync
Phone = ₹60 RED
Admin Current = ₹100

Upload fails
Phone = ₹60 RED
Admin Current = ₹100

Upload succeeds
Phone = ₹60 NORMAL
Admin Current = ₹60
```

## 7. Manual price audit requirement

The user explicitly asked whether managers can manually change prices
without Admin Push and whether Admin can know.

Final requirement:

Every price change must have a source:

-   `MANUAL`
-   `CSV`
-   `ADMIN_PUSH`

Manual changes must be auditable with:

-   PLU number
-   PLU name
-   old price
-   new price
-   source
-   status
-   timestamp
-   device IP
-   device ID
-   scale IP

A manual edit must not disappear just because the app closes.

## 8. Device identity

Use both:

-   `device_ip`
-   `device_id`

IP is useful for store identification/reporting but can change because
of DHCP. Device ID is intended as the more persistent phone identifier.

Admin can later map an IP to a store.

## 9. Reports UI --- locked decision

Do NOT put all filters permanently on the screen.

Main Reports header:

``` text
Reports

Today ▼                         FILTER
```

The Filter button opens a compact popup/bottom-sheet style filter UI.

Filters:

-   Store
-   IP address
-   SKU / PLU / name
-   Date range
-   Status

Date presets:

-   Today
-   Yesterday
-   Last 7 Days
-   Last 30 Days
-   This Month
-   Custom Range

Reports tabs:

-   CURRENT
-   CHANGES
-   UPLOADS
-   PUSH

Export:

-   CSV that Excel can open.
-   Later Excel/XLSX can be added if required.

## 10. Data retention

No automatic retention/deletion policy was requested or implemented.

Historical records should remain indefinitely unless an explicit
archive/retention feature is later added.

## 11. Current implementation status --- very important

### Stable

The stable base is **v4.0.0 / commit `8bd68fc`**.

Known stable pieces:

-   Essae TCP connection
-   PLU upload
-   price editing
-   Upload All
-   CSV import
-   local Room PLU storage
-   Label Design
-   generic `.LFT` upload
-   default label filename `LABEL01.LFT`
-   established Essae checksums/protocol behavior

### NOT stable

The later `4.1.0` feature attempts are NOT the stable baseline.

The latest attempted file:

`MainActivity_4.1.0_TITLE_FIXED.kt`

contains the requested feature direction but the user reported
runtime/compile problems.

The user's Android Studio screenshot showed:

``` text
Unresolved reference: pendingAudits
Unresolved reference: it
```

at the PLU list line using `pendingAudits.any { ... }`.

The earlier attempted file also produced many report-tab/type errors and
a runtime force-close.

**Therefore: start from the stable v4.0.0 project, not from the broken
4.1.0 file.**

## 12. Current Supabase status

The fresh schema was successfully run in Supabase.

The user also ran the Admin Push migration. Their Supabase Table Editor
showed:

-   `admin_price_updates`
-   `admin_price_update_targets`
-   `admin_price_push_report`
-   `admin_current_prices`
-   `admin_price_change_report`
-   `admin_scale_upload_report`
-   `profiles`
-   `scale_upload_sessions`
-   `store_ip_map`
-   `store_price_change_log`
-   `store_price_current`
-   `stores`

Do **not** drop the tables again unless intentionally rebuilding the
database.

## 13. Supabase project

Project URL:

`https://ztuzcrcvbbprytwotpwo.supabase.co`

Android must use the **publishable/anon client key**, never a
`service_role`/secret key.

The user supplied credentials during the conversation. Do not place
secret credentials in this handoff; use `local.properties` or another
local secret mechanism.

## 14. Admin identity

Previously established Admin Auth identity:

-   Email: `itteam@mahamartco.in`
-   UID: `1ccc9915-6016-4d70-a49f-c8a7615dea98`
-   Role: `ADMIN`

The profile insert/update was planned after the fresh schema reset.
Verify in Supabase before assuming the profile exists.

## 15. Store master

17 stores:

  Code    Store
  ------- -----------------
  MM001   Burugupally
  MM002   Choppadandi
  MM003   Dharmaram
  MM004   Ellanthakunta
  MM005   Gangadhara
  MM006   Gangadhara New
  MM007   Gharshakurthy
  MM008   Gopalrao Pet
  MM009   Kapuwada
  MM010   Keshavapatnam
  MM011   Koheda
  MM012   Mallial
  MM013   Pegadapally New
  MM014   Pegadapally Old
  MM015   Raikal
  MM016   Vemulawada
  MM017   Vidya Nagar

## 16. Important historical conflict to remember

The original fresh schema contains an old
`mark_reverted_price_changes()` RPC and comments describing automatic
revert on app restart.

That behavior was later explicitly **superseded by the user**.

Final requirement is:

> Do NOT revert a pending local price on restart. Keep it RED/PENDING
> until successful physical scale upload.

The Admin Push migration itself documents that the old revert function
remains only for compatibility and the new Android code must not call it
during startup.

## 17. Label Design

Label Design is separate from the cloud/admin layer.

Known working behavior:

-   Generic `.LFT` upload works.
-   Uploaded bytes remain unchanged.
-   Default filename: `LABEL01.LFT`.
-   Final post-upload handshake was established.
-   Do not disturb this implementation while changing
    reports/admin/cloud behavior.

## 18. CSV format

Store CSV format:

``` text
pluno,pluname,plucode,uom,unitprice
```

There were 133 PLUs in the development dataset at one point.

Local PLU/CSV remains store-side; Admin does not need to publish the
full PLU master.

## 19. Essae protocol facts already established

-   TCP port: `4321`
-   PLU record size: `143 bytes`
-   Price float is little-endian at offset `+71`
-   10 records per frame
-   Checksums established
-   Upload All works
-   Direct price upload works

These protocol facts are the interoperability baseline. Do not replace
them with assumptions from vendor software.

## 20. Security/legal notes

-   The Android app uses pure Kotlin/Android code.
-   Do not redistribute vendor proprietary executables, DLLs, manuals,
    or assets.
-   The app is not an official Essae application unless separately
    authorized.
-   Reverse engineering/interoperability legality depends on applicable
    licenses and law.

## 21. Resume instruction for a new ChatGPT conversation

Start the new conversation by attaching or pasting this handoff pack and
say:

> "Continue MahaMart Scale Manager from this handoff. Do not redesign
> the architecture. Use v4.0.0/commit 8bd68fc as the Android baseline.
> Supabase fresh schema + Admin Push migration are already applied.
> Implement the requested features one file at a time and preserve the
> working Essae protocol."

Then work in this order:

1.  Verify the stable v4 Android project.
2.  Verify Gradle/build.
3.  Add persistent local audit model safely.
4.  Add SupabaseRest methods matching the already-applied schema.
5.  Add manual audit.
6.  Add Admin login.
7.  Add Sync.
8.  Add Admin Push.
9.  Add persistent red/pending state.
10. Connect successful Upload All to cloud confirmation.
11. Add Reports.
12. Test restart/failure/success flows.
13. Only then polish UI.

**Do not start by replacing the whole project.**
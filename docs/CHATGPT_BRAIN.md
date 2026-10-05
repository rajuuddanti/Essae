# MAHAMART SCALE MANAGER — CHATGPT BRAIN / PROJECT HANDOFF

Last updated: 2026-10-05
Repository: https://github.com/rajuuddanti/Essae
Active branch: feature/background-admin-price-sync-2026-10-04
Current known HEAD before this handoff: 7d3e2e97411996fe85ce744f741046eb4c430fae

## 1. PROJECT IDENTITY

Android app: MahaMart Scale Manager
Package: com.mahamart.essae
Business: Mahalaxmi Mahamart
Purpose: manage PLU/price data locally, upload complete price dataset to Essae weighing scales, and receive Admin Push price changes through Supabase.

Hard constraint from user:
- Continue from the last stable Essae Android v4 / v4.1.0 project.
- Do NOT alter UI, architecture, names, existing behavior, or refactor unless explicitly requested.
- Store prices are independently editable by admin/manager.
- Every SKU has a real price. Price 0 is only a display/label convention when requested; it does NOT mean the SKU has no price.
- Admin/manager sync must change only affected SKUs and preserve all other local prices.
- Upload All sends the complete local dataset to the physical scale.

## 2. CURRENT ARCHITECTURE

Android:
- Kotlin
- Room local DB
- EssaeTransport for scale communication
- Supabase REST / RPC for cloud sync and audit/logging
- CSV import
- Admin login/dashboard/reports
- Device registration/authentication
- Admin Push background sync
- Manual price audit
- Physical scale upload sessions

Room stores PLU data and PriceAudit/local price-change state.

Supabase is primarily the cloud control/audit layer; PLU data is intentionally available locally on devices.

The app has:
- Scale Manager main screen
- PLU list
- Admin dashboard
- reports tabs: CURRENT / CHANGES / UPLOADS / PUSH
- CSV import
- device ID/IP
- admin push
- manual price changes
- physical Essae upload
- upload history
- admin push history

Admin login exists. User previously specified admin access should be hidden from the normal main screen and accessed by holding Scale Manager for 10 seconds. PINs used in earlier project work include 3331 for admin dashboard context; do not change unless explicitly requested.

## 3. DEVICE / STORE TEST CONTEXT

Current test device:
- Android ID: f0d40d646691b3f6
- Device IP: 192.168.31.185
- Device model/user-agent: 24108PCE2I
- Store ID: 9295ec05-2091-4981-a51b-1d65b4b1a7d3
- Store code: MM006
- Store name: Gangadhara New
- active store_devices registration
- scale IP in recent screenshot: 192.168.31.210
- scale port: 4321
- local PLUs shown: 133

Do not ask the user to re-register unless there is evidence registration is broken. Registration has already been tested multiple times.

## 4. DEVICE AUTH

Supabase store_devices columns:
id, store_id, device_id, device_name, device_ip, active, registered_at, last_seen_at, updated_at, device_token_hash, fcm_token

verify_store_device_token:
- requires non-empty device_id and device_token
- hashes token with SHA-256
- requires active store_devices row
- returns store_id
- dummy token correctly returns Invalid device authentication
- current real device calls update last_seen_at, proving auth works.

Android registration preferences:
SharedPreferences name = store_device_registration
key = device_token

Do not replace this auth mechanism casually.

## 5. ADMIN PUSH FLOW

Admin creates admin_price_updates.
Each update has targets in admin_price_update_targets.
Device state is tracked in admin_price_update_device_state.
Price audit/logging exists in store_price_change_log.
Store confirmed master price is in store_price_master.
Current price state also exists in store_price_current.

Desired semantics:
1. Admin pushes a new price.
2. Device receives latest relevant Admin Push.
3. Room PLU is updated locally.
4. Red pending marker is shown until physical scale upload confirms it.
5. Physical scale upload confirms the actual uploaded price.
6. Cloud master/current becomes the uploaded price.
7. Superseded older pushes must not remain actionable/pending.
8. Device should never resurrect an older Admin Push after a newer price has been confirmed.
9. Rapid pushes for the same PLU must resolve to the newest effective Admin Push.

## 6. IMPORTANT BUG HISTORY

### Bug A — older Admin Push resurfacing
Original RPC selected/filtering rows in the wrong order. It could filter out the latest push because it matched Store Master, then surface an older push.

Example:
879 -> 789 -> 100 -> 1
After 1 was uploaded, the old 100 could incorrectly return.

Fix applied to store_get_pending_admin_price_updates_v2:
- collect ALL non-UPLOADED target updates first
- partition by store + PLU
- select latest row per PLU using created_at DESC, id DESC
- ONLY THEN compare latest new_price against store_price_master
- return only the latest actionable price

This is the correct ordering.

### Bug B — SQL ambiguity
Postgres log showed:
column reference "created_at" is ambiguous
SQLSTATE 42702
inside store_get_pending_admin_price_updates_v2 RETURN QUERY.

Fixed by aliasing u.created_at as update_created_at and using that alias throughout the CTEs.

### Bug C — local Room can be stale
After cloud correctly says latest effective price is already confirmed, the RPC may return no actionable item. If Room still contains an old Admin Push price, the app has no reconciliation path to repair that stale local value.

This is a real app-side issue and must be solved without inventing a new Admin Push.

### Bug D — red marker is cleared too broadly
Current MainActivity physical upload success code includes:
auditDao.markAllPendingUploaded()
changedPluNumbers = emptySet()

This is too broad. A physical upload should only clear pending state for the actual PLU/price values that were successfully uploaded/confirmed. It must not blindly clear every local pending audit.

Also changedPluNumbers is in-memory and can be empty after restart. After successful Admin Push sync, it should be rebuilt from the actual pending local audits.

## 7. CURRENT RPC

Current store_get_pending_admin_price_updates_v2 logic:

- authenticate device
- update device IP / last_seen_at
- all_items: all target updates for store where target status != UPLOADED
- latest_per_plu:
  row_number partition by store_id + item.plu_no
  order by update_created_at DESC, update_id DESC
- actionable:
  latest row only
  and store_price_master.master_price IS DISTINCT FROM latest new_price
- group items and return in ascending created_at order

Important: this RPC is intentionally latest-per-PLU.

Current SQL shape:

CREATE OR REPLACE FUNCTION public.store_get_pending_admin_price_updates_v2(
 p_device_id text,
 p_device_token text,
 p_device_ip text default ''
)
RETURNS TABLE(update_id uuid,target_store_id uuid,created_at timestamptz,note text,item_count integer,items jsonb)
LANGUAGE plpgsql SECURITY DEFINER
SET search_path=public,extensions
AS $$
declare v_store_id uuid;
begin
 v_store_id:=public.verify_store_device_token(trim(p_device_id),trim(p_device_token));

 update public.store_devices
 set device_ip=case when coalesce(trim(p_device_ip),'')='' then device_ip else trim(p_device_ip) end,
     last_seen_at=now(),updated_at=now()
 where device_id=trim(p_device_id) and store_id=v_store_id and active=true;

 return query
 with all_items as (
   select u.id as update_id,
          u.created_at as update_created_at,
          u.note as update_note,
          t.store_id,
          jsonb_array_elements(
             case when jsonb_typeof(u.items)='array'
                  then u.items else '[]'::jsonb end
          ) as item
   from public.admin_price_updates u
   join public.admin_price_update_targets t on t.update_id=u.id
   where t.store_id=v_store_id and t.status<>'UPLOADED'
 ),
 latest_per_plu as (
   select a.*,
          row_number() over(
             partition by a.store_id,(a.item->>'plu_no')
             order by a.update_created_at desc,a.update_id desc
          ) as rn
   from all_items a
   where (a.item->>'plu_no') ~ '^[0-9]+$'
 ),
 actionable as (
   select l.*
   from latest_per_plu l
   left join public.store_price_master m
     on m.store_id=l.store_id
    and m.plu_no=(l.item->>'plu_no')::integer
   where l.rn=1
     and m.master_price is distinct from (l.item->>'new_price')::numeric
 ),
 grouped as (
   select a.update_id,a.store_id,a.update_created_at,a.update_note,
          jsonb_agg(a.item) as grouped_items
   from actionable a
   group by a.update_id,a.store_id,a.update_created_at,a.update_note
 )
 select g.update_id,g.store_id,g.update_created_at,g.update_note,
        jsonb_array_length(g.grouped_items),g.grouped_items
 from grouped g
 order by g.update_created_at asc;
end; $$;

Do not blindly rewrite this function again. First inspect current live DB state and app behavior.

## 8. CURRENT PRICE CONFIRMATION

For current test store MM006 / PLU 1:
store_price_master currently confirmed:
master_price = 1.00
source = ADMIN_PUSH
updated_at = 2026-10-05 07:34:46.771532+00

This is the physical/cloud-confirmed price from the ₹1 upload.

## 9. RAPID PUSH TEST

User stress-tested PLU 1 / SUGAR LOOSE with multiple rapid Admin Push prices.

Observed recent admin updates included approximately:
₹878
₹52
₹89
₹989
₹100
₹100
₹1

The important confirmed upload:
- ₹1 Admin Push was uploaded to the physical scale.
- device state for that ₹1 update became UPLOADED.
- store_price_master became ₹1 at 07:34:46.771532 UTC.

There was also a later store_price_change_log entry around 07:40:46:
₹1.00 -> ₹100.00 ADMIN_PUSH PENDING.
This timestamp must be checked against admin_price_updates before assuming it was a new Admin Push. Do NOT guess. The user's stated sequence was that ₹100 was pushed before ₹1, so verify the actual admin update timestamps/state.

Recent screenshot state reported by user:
- Admin: 1 SUGAR LOOSE, ₹1.00 -> ₹100.00, ADMIN_PUSH, PENDING
- Device PLU list: ₹100.00
- Device red pending marker: missing
- physical scale upload had been done with ₹1

This led to the conclusion that either:
A) a genuinely newer ₹100 Admin Push was created after the ₹1 upload, in which case device ₹100 is correct and only the red marker is broken; OR
B) there was no newer ₹100 Admin Push and Room is stale, in which case both Admin pending and local ₹100 are stale.

The next investigation must query admin_price_updates after 07:34 UTC for PLU 1 before making DB changes.

## 10. PHYSICAL UPLOAD

complete_scale_upload currently:
- verifies device token
- validates STARTED session
- loops uploaded items
- upserts store_price_current
- upserts store_price_master with uploaded price
- marks matching pending store_price_change_log for same device/plu/new_price as UPLOADED
- marks matching admin_price_update_device_state rows as UPLOADED
- updates admin_price_update_targets based on uploaded device state or no remaining master-price difference
- completes scale upload session

Important concern:
admin_price_update_targets can become UPLOADED for historical updates when store_price_master no longer differs, which is useful for resolution, but store_price_change_log rows for superseded prices may remain PENDING unless explicitly marked SUPERSEDED.

admin_price_update_targets supports:
PENDING, SYNCED, UPLOADED, SUPERSEDED.

## 11. REQUIRED SUPSERSESSION SEMANTICS

For a same-PLU chain:
879 -> 789 -> 100 -> 1
once 1 is confirmed uploaded:
- 879 should be SUPERSEDED
- 789 should be SUPERSEDED
- 100 should be SUPERSEDED
- 1 should be UPLOADED
- no old update should return from pending RPC
- device local price should be 1
- red marker should be off

Do not mark unrelated PLUs.

## 12. Store / company context

MahaMart operates supermarkets and uses retail software.
The Scale Manager is one internal tool.
Store master and device mapping are important.

Current test store:
MM006 — Gangadhara New

Other known stores include:
Dharmaram, Gharshakurthy, Choppadandi, Vidya Nagar, Mallial, Pegadapally (Old), Kapuwada, Pegadapally (New), Burugupally, Keshavapatnam, Gangadhara, Koheda, Ellanthakunta, Gangadhara New, Gopalrao Pet, Vemulawada.

## 13. APP UI / UX CONSTRAINTS

User explicitly does not want unrelated UI changes.
Do not:
- redesign screens
- rename existing tabs
- add unnecessary settings
- refactor architecture
- remove existing reports
- alter stable scale transport behavior

Only make targeted fixes requested.

Admin:
- admin login
- stores
- push
- mapping
- reports
- admin push history
- manager push history
- upload history
- current/changes/uploads/push tabs

## 14. DEVICE REGISTRATION / DEVICE ID

Earlier requested:
- device ID changing should require PIN 1413
- changing device ID must NOT clear all device data
This feature was discussed for the separate Audit app context; do not mix it into Scale Manager unless explicitly requested.

Scale Manager device registration uses store device token.

## 15. NOTIFICATIONS

Firebase was configured on a personal account during testing.
Issue observed: notifications only arriving while app open.
Live notifications were desired.
Branches included feature/admin-push-notifications.
Do not assume this is solved unless tested.

## 16. GIT / BRANCH HISTORY

Important branches:
- feature/admin-push-notifications
- ChatGPT-sugegstions
- fix/admin-operations-stale-pending
- feature/qr-device-registration
- feature/crash-free-audit
- feature/background-admin-price-sync-2026-10-04

Current active work:
feature/background-admin-price-sync-2026-10-04

Recent commits / concepts:
- reset HEAD to a86e79d during earlier work
- Fix stale pending Admin Operations state
- Admin Push history
- manager push history
- missing price=0 handling
- Admin Push ordered loop syntax
- current branch HEAD known: 7d3e2e97411996fe85ce744f741046eb4c430fae

Do not reset or force-push without explicit instruction.

## 17. TESTING HISTORY

The app was installed on two devices and latest build was running normally.
Tests confirmed:
- pushing 2 prices for same SKU, latest uploaded works as intended in one test
- manager/admin push history tested
- pending stale state was fixed once
- Admin push can overwrite local price; user wants this behavior visible so store user knows it was overwritten
- connection error should match the test connection error
- version was requested as 1.1.0

Full crash-free test and complete Supabase table/function/permission audit were not yet completed.

## 18. ROOM / MIGRATION HISTORY

A Room data integrity error occurred because schema changed without version increment.
PriceAudit table was not handled by migration.
App crashed on mobile while emulator worked.
This was fixed during prior work, but every future Room schema change must:
- increment version
- provide correct migration
- test fresh install
- test upgrade from existing DB

Multi-instance invalidation was added:
Room database .enableMultiInstanceInvalidation()

## 19. SCALE PROTOCOL HISTORY

Earlier protocol work:
- 349-PLU ETLDRV capture
- 10 PLUs/frame
- per-frame ACKs
- incrementing sequence
- IEEE-754 4-byte prices
- checksum/header rules
- PCS/UOM disabled
- 133-PLU WEIGH test planned

Test 6 stopped around 20 PLUs because ACK validation incorrectly expected 97.
Actual ACK checksum varies by frame:
- frame 2: 96
- frame 3: 95
Test 7 fixed ACK validation.

Do not regress stable EssaeTransport behavior while fixing Admin Push.

## 20. CSV / PLU DATA

CSV import behavior:
- imported CSV should set master/store price for all SKUs at that time.
- One observed issue: after clearing/importing CSV for store MM016, app still showed 2 updates available.
- Do not let Admin Push stale state survive a full CSV replacement unless explicitly desired.

Local device currently has 133 PLUs in recent screenshot.

## 21. REPORTING / AUDIT

Admin reports should appear in app and Excel export.
Admin Push history should show update time, store/device, prices, statuses.
Manager push report had once been blank and needed fixing.
Store-wise pricing is required.
Supabase logs who changed prices based on IP.
Later store-to-IP mapping will be maintained.

## 22. DEBUGGING METHOD THAT WORKED

For Supabase bugs:
1. inspect app code
2. inspect live function definition
3. inspect Postgres/edge logs
4. reproduce
5. query exact rows
6. make one targeted change
7. test again

Example:
Postgres log exposed ambiguous created_at in the RPC, which was much faster than guessing.

For this current issue:
DO NOT change more SQL until current admin_price_updates after 07:34 is queried.

## 23. CURRENT TODO / NEXT CHAT START HERE

Priority 1:
Query admin_price_updates for MM006 / PLU 1 after 2026-10-05 07:34:00 UTC, with target status and device state.

Priority 2:
If a new ₹100 Admin Push exists after ₹1 upload:
- device ₹100 is expected
- Admin pending ₹100 is expected
- red marker missing is the bug
- fix local pending-marker reconstruction and physical-upload clearing

Priority 3:
If NO newer ₹100 Admin Push exists:
- device Room ₹100 is stale
- Admin pending ₹100 is stale
- implement local reconciliation to confirmed store_price_master
- supersede old updates correctly
- ensure Room becomes ₹1 without creating a fake audit

Priority 4:
Fix upload clearing:
Replace broad auditDao.markAllPendingUploaded() with targeted confirmation/reload logic.

Priority 5:
After fix:
- rapid pushes same PLU
- upload newest
- restart app
- background sync
- offline -> online
- verify no older price resurfaces
- verify red marker exactly matches pending local audit
- verify Admin pending count
- verify store_price_master/current
- verify target/device states
- verify no crash

## 24. USER COMMUNICATION STYLE

User prefers concise, direct, informal "bro" style.
They want fast progress and concrete findings.
Avoid asking them to repeat information already known.
When changing code, explain exactly what file/function changes and why.
Never expose credentials, tokens, secrets, or private keys.

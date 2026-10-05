# MAHAMART SCALE MANAGER — SUPABASE + DEBUG STATE

Last updated: 2026-10-05

## Current test identity
- Store: MM006 / Gangadhara New
- Store ID: 9295ec05-2091-4981-a51b-1d65b4b1a7d3
- Device ID: f0d40d646691b3f6
- Device IP: 192.168.31.185
- Scale IP: 192.168.31.210
- Scale port: 4321
- Test PLU: 1 — SUGAR LOOSE

## Confirmed cloud price
store_price_master for MM006 + PLU 1:
- master_price = 1.00
- source = ADMIN_PUSH
- updated_at = 2026-10-05 07:34:46.771532+00

Therefore the last confirmed physical/cloud price is ₹1.

## Important DB objects

### Device auth
public.verify_store_device_token(p_device_id text, p_device_token text)
Authenticates against store_devices.device_token_hash using SHA-256 and returns store_id.

### Admin pending RPC
public.store_get_pending_admin_price_updates_v2(p_device_id text, p_device_token text, p_device_ip text default '')
Correct intended algorithm:
1. Authenticate.
2. Collect all non-UPLOADED target updates for this store.
3. Expand update items.
4. Partition by store + PLU.
5. Select newest update using created_at DESC, id DESC.
6. Compare ONLY newest price to store_price_master.
7. Return only newest actionable update.

Do not filter against master before selecting newest.

Previous SQL error:
column reference "created_at" is ambiguous / SQLSTATE 42702.
Current implementation aliases update timestamp as update_created_at.

### Physical upload
public.complete_scale_upload(...)
On successful physical upload it:
- verifies device
- validates upload session
- upserts store_price_current
- upserts store_price_master
- marks matching price-change log rows uploaded
- marks matching admin device state uploaded
- updates admin targets
- completes upload session

### Admin target status
admin_price_update_targets.status supports:
- PENDING
- SYNCED
- UPLOADED
- SUPERSEDED

Expected same-PLU resolution:
old pushes become SUPERSEDED when a newer price is confirmed; newest uploaded push becomes UPLOADED.

## Critical state to investigate
A store_price_change_log row was observed around:
2026-10-05 07:40:46.559901+00
It showed:
- PLU 1
- ₹1.00 -> ₹100.00
- ADMIN_PUSH
- PENDING

This is later than the confirmed ₹1 upload at 07:34:46.

Do NOT assume this means a new Admin Push occurred. Query admin_price_updates directly after 07:34:00 and compare:
- update id
- created_at
- items
- target status
- admin_price_update_device_state status
- synced_at
- uploaded_at

The user says ₹100 was pushed before ₹1, so this discrepancy must be resolved from DB evidence.

## User-reported screenshots
Admin:
- MM006 Gangadhara New
- PENDING PRICES = 1
- PLU 1 SUGAR LOOSE
- ₹1.00 -> ₹100.00
- ADMIN_PUSH
- PENDING
- changed around 05 Oct 2026 01:07 pm

Device:
- PLU 1 SUGAR LOOSE = ₹100.00
- 133 PLUs
- upload-complete banner
- no red pending marker

User says the physical scale was uploaded with ₹1.

## Code issue already identified
In MainActivity physical upload success:
auditDao.markAllPendingUploaded()
changedPluNumbers = emptySet()

This is too broad.

Correct behavior:
- only clear audits corresponding to prices actually confirmed by physical upload
- reload/recompute pending PLUs after upload
- never clear unrelated pending Admin Pushes
- never rely only on in-memory changedPluNumbers

## Room stale-state issue
If cloud master is ₹1 and RPC returns no actionable update, Room may still contain ₹100.

Need a reconciliation path that updates Room from confirmed cloud state without creating a fake Admin Push audit.

Preferred conceptual behavior:
- confirmed cloud/physical state is authoritative
- local Room follows it
- Admin Push audit only represents a genuine outstanding push
- a superseded update does not create a pending marker

## Testing checklist
### Same PLU rapid push
Test: ₹879 -> ₹789 -> ₹100 -> ₹1
Expected after ₹1 upload:
- Room ₹1
- scale ₹1
- store_price_master ₹1
- latest Admin Push UPLOADED
- previous updates SUPERSEDED
- pending count 0
- red marker off

### Newer push after upload
If ₹1 is uploaded, then ₹100 is pushed afterward:
- scale remains ₹1
- Room becomes ₹100
- Admin pending ₹100
- red marker ON
This is not a bug; it is a legitimate newer Admin Push.

### Restart
- Room remains correct
- red marker reconstructed from DB
- no old push resurfaces

### Offline/online
- no data loss
- sync latest only
- no duplicate audit
- no older price resurrection

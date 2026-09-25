# MahaMart Scale Manager — Project Handoff / Resume Pack

**Document date:** 2026-09-25  
**Purpose:** Single source of truth for resuming MahaMart Scale Manager in a new conversation without re-explaining the project.

## 1. Project identity
- Android app: **MahaMart Scale Manager**
- Package: `com.mahamart.essae`
- GitHub repo: `https://github.com/rajuuddanti/Essae`
- Stable Essae baseline: v4 / working direct-upload implementation.
- TCP port: `4321`
- Do not assume store scale IPs.

## 2. Golden rule
**Do not rewrite the working Essae protocol just to add cloud/admin/reporting features.**
The local-first Room/PLU and direct Essae upload path are the proven core.

## 3. Final operating model

### Store phone
- No store login.
- Opens directly to Scale Manager.
- Local PLU/CSV remains on phone.
- Manual price edits are allowed.
- Manual **Sync** pulls pending Admin Push changes from Supabase.
- Sync is NOT a physical scale upload.
- Synced/admin-pushed prices become local **RED / PENDING SCALE UPLOAD**.
- Pending/red state must survive restart.
- Upload failure leaves RED.
- Only successful physical Essae Upload All clears RED.
- Successful upload updates the confirmed/current price source of truth.

### Admin
- Admin login required.
- Hidden Admin entry: long-press Scale Manager.
- Admin modules: Admin Push, Reports, Store/Device Mapping, Store Device Registration, Label Design, Refresh, Logout.
- Admin Push creates pending cloud updates; it does not directly upload to Essae.

## 4. Price state machine
```text
ADMIN PUSH
  ↓
STORE SYNC
  ↓
LOCAL PRICE CHANGE
  ↓
RED / PENDING SCALE UPLOAD
  ↓
UPLOAD ALL → ESSAE
  ├─ SUCCESS → CONFIRMED / NORMAL
  └─ FAILED  → RED REMAINS
```

Manual edits follow the same local pending/upload lifecycle.

## 5. Confirmed-price rule
`store_price_current` represents the last successfully uploaded physical-scale price, not merely a phone-side synced value.

Example:
```text
Confirmed ₹100
Admin pushes ₹60
Store Sync → phone ₹60 RED; confirmed remains ₹100
Upload fails → phone ₹60 RED; confirmed remains ₹100
Upload succeeds → phone ₹60 NORMAL; confirmed becomes ₹60
```

## 6. Audit requirements
Every price change has a source:
- `MANUAL`
- `CSV`
- `ADMIN_PUSH`

Audit data includes PLU, item name, old/new price, source, status, timestamp, device IP, device ID and scale IP where available.

## 7. Device identity / registration
Permanent store identity is `store_code` (MM001–MM017), not IP.

Current registration architecture:
- physical Android device is registered to a store using a one-time registration code;
- registration binds `store_code + device_id`;
- registration code expires and is one-use;
- device IP is audit/mapping data, not the primary identity;
- multiple devices per store are supported;
- Store Device Mapping is an Admin-only view;
- Store-side registration requires no normal store login.

Supabase multi-device objects:
- `store_devices`
- `store_device_registration_codes`
- `admin_price_update_device_state`

Device-level RPCs:
- `store_register_device`
- `store_touch_device`
- `store_get_pending_admin_price_updates_v2`
- `store_mark_admin_price_updates_synced_v2`
- `store_mark_admin_price_updates_uploaded_v2`

## 8. Current verified registration test
A physical/emulator test device successfully registered:
- Store: `MM017 — Vidya Nagar`
- Device: `sdk_gphone16k_x86_64`
- Device ID: `7bf621a9c15a33ea`
- IP: `10.0.2.15`

Admin Store/Device Mapping successfully showed the registered device and updated last-seen timestamp.

## 9. Supabase
Correct live project URL:
`https://idkhzkehbdzsawpuevgd.supabase.co`

Use only the publishable/anon client key in Android. Never commit service-role keys, passwords, `local.properties`, or other secrets.

Core tables include:
`stores`, `profiles`, `store_ip_map`, `store_devices`, `store_device_registration_codes`, `store_price_current`, `store_price_change_log`, `scale_upload_sessions`, `admin_price_updates`, `admin_price_update_targets`, `admin_price_update_device_state`, `admin_price_push_report`, `admin_current_prices`, `admin_price_change_report`, `admin_scale_upload_report`.

Important RPCs include:
`is_admin`, `admin_publish_price_update`, `admin_generate_store_device_code`, `store_register_device`, `store_touch_device`, `complete_scale_upload`, `fail_scale_upload`, and the v2 device-level push/sync/upload RPCs.

### Registration bug fixed
The live `store_register_device` function had a PL/pgSQL ambiguity on `device_id`. It was fixed to use:
`ON CONFLICT ON CONSTRAINT store_devices_device_id_unique`
The direct registration test then succeeded.

## 10. Admin identity
Current known Admin Auth identity:
- UID: `600bcd92-f9f9-4fb4-a349-8bea165b778c`
- Email: `admin@mahamartco.ir`
- Role: `ADMIN`
- Profile name: `MahaMart Admin`

Do not place credentials in source/docs.

## 11. Store master
MM001 Burugupally  
MM002 Choppadandi  
MM003 Dharmaram  
MM004 Ellanthakunta  
MM005 Gangadhara  
MM006 Gangadhara New  
MM007 Gharshakurthy  
MM008 Gopalrao Pet  
MM009 Kapuwada  
MM010 Keshavapatnam  
MM011 Koheda  
MM012 Mallial  
MM013 Pegadapally New  
MM014 Pegadapally Old  
MM015 Raikal  
MM016 Vemulawada  
MM017 Vidya Nagar

## 12. Reports direction
Reports should use compact filters rather than permanently occupying the main screen:
- Store
- IP
- SKU / PLU / name
- Date range
- Status

Tabs:
- CURRENT
- CHANGES
- UPLOADS
- PUSH

Export: CSV first; XLSX can be added later.

## 13. Essae protocol — do not disturb
- TCP `4321`
- 143-byte PLU records
- 10 records/frame
- price IEEE float LE at record offset +71
- established checksums and ACK behavior
- working Upload All
- PCS unsupported
- `EssaeTransport.kt` is the stable protocol layer.

## 14. Timezone/display rule
Supabase/PostgreSQL `timestamptz` values represent absolute instants. The project should keep timestamps as `timestamptz` and format Admin/report UI timestamps in **Asia/Kolkata (IST, UTC+05:30)**. Do not alter stored instants merely to change display timezone.

## 15. Current Android implementation status
Verified:
- Admin login/auth
- Admin dashboard shell
- Store Device Registration
- Store/Device Mapping
- device last-seen update
- stable Essae upload path

Still to implement/connect:
1. Functional Admin Push UI.
2. Store Sync using v2 device-level RPCs.
3. Persistent RED/PENDING state.
4. Connect successful Upload All to cloud upload confirmation.
5. Reports UI and CSV export.
6. Full end-to-end multi-device testing.

Important existing limitations:
- current `MainActivity.kt` uses an in-memory `changedPluNumbers` set;
- current Upload All does not yet call cloud upload lifecycle RPCs;
- do not reintroduce automatic revert-on-restart behavior.

## 16. Git branches / checkpoint
- `main`: stable combined repository.
- `feature/admin-device-registration`: current feature branch containing registration, mapping and the registration-function fix.
- Registration-function fix commit: `08228045ff3069b39f70827744f15acdfcab7eb5`.

Do not merge the feature branch to main until Admin Push + device-level sync/upload are tested.

## 17. Build troubleshooting
If Gradle fails because `app/build` or KAPT files are locked:
```powershell
.\gradlew.bat --stop
Remove-Item -Recurse -Force .\app\build
.\gradlew.bat clean
```
Then build/run again from Android Studio or USB device.

## 18. Resume order
1. Verify current feature branch builds.
2. Commit/push brain checkpoint.
3. Implement Admin Push without touching EssaeTransport.
4. Implement device-level Store Sync.
5. Make RED/PENDING persistent.
6. Connect Upload All success/failure to cloud lifecycle.
7. Build Reports.
8. Test restart/failure/success and multiple devices.
9. Only then merge to main.

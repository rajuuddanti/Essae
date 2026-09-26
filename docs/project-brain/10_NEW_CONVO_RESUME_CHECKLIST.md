# New Conversation Resume Checklist

## First read
- 19_STABLE_CHECKPOINT_2026-09-26.md
- 00_MASTER_HANDOFF.md
- 02_FINAL_ARCHITECTURE_AND_DECISIONS.md
- 03_ANDROID_IMPLEMENTATION_HANDOFF.md
- 04_SUPABASE_HANDOFF.md
- 06_ESSAE_PROTOCOL.md
- 07_CONVERSATION_DECISION_LOG.md

## Current known state
1. Stable Essae transport must be preserved.
2. Supabase Admin Auth and device registration are working checkpoints.
3. Admin Push is implemented.
4. Admin current price means last confirmed physical-scale price.
5. Pending Admin Push is shown separately from confirmed price.
6. Manual confirmed uploads are marked with M; Admin Push confirmed uploads have normal LC.
7. The latest SQL migration must be applied in live Supabase before final end-to-end testing.
8. Do not change the database or Android architecture without evidence from a failing test.

## Before coding
- Inspect the current Android source on the active branch.
- Compare it with the stable baseline.
- Check the current Supabase SQL/RPC definitions.
- Identify the smallest required change.
- Build and test on the physical device.
- Never rewrite EssaeTransport.kt for cloud/admin work.

## Current test order
1. Build APK.
2. Apply MahaMart_ADMIN_PUSH_LC_AND_UPLOAD_MIGRATION.sql to live Supabase.
3. Test one PLU + one store.
4. Verify Admin Push → Store Sync → RED/PENDING.
5. Restart store app and verify RED persists.
6. Verify Admin remains on old confirmed price with pending (date)*(price).
7. Upload successfully to Essae and verify confirmed price/LC updates.
8. Manual manager change + successful upload → verify M.
9. Admin Push + successful upload → verify no M.
10. Failed upload → confirmed price unchanged and RED remains.
11. Then test multiple stores/devices.

## GitHub
Repository: rajuuddanti/Essae
Branch: feature/admin-device-registration
Keep project brain under docs/project-brain/ and SQL under supabase/.

## Security
Never commit local.properties, passwords, service-role keys, signing credentials, or other secrets.

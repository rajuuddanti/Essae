# Android + Supabase Audit Handoff — 2026-10-03

## Goal
Audit the current MahaMart Scale Manager Android application and its live Supabase project together before making any fixes. Work through confirmed issues one at a time, with the user approving each change.

## Repository / branch
- Repository: `rajuuddanti/Essae`
- Active work branch: `feature/qr-device-registration`
- Do not merge the testing branch into `main` unless the user explicitly asks.
- Existing project-brain documentation is under `docs/project-brain/`.

## Supabase project
- Project: Essae
- Project ref: `idkhzkehbdzsawpuevgd`
- Organization: Mahamart-Essae
- Region: Singapore (`ap-southeast-1`)
- Treat this as production. Audit read-only until a specific change is approved.

## Known architecture / invariants
- `store_price_master` is the Admin/CSV master layer; CSV import updates master values and does not prove a physical scale upload.
- `store_price_current` represents the physical scale-confirmed price layer.
- Flow: Admin Push → Store Sync → local Room target / pending state → physical Essae upload → reconcile only after confirmed success.
- Failed physical uploads must remain pending.
- Device ID identifies a device; store code identifies a store; device token is the device secret; IP is audit metadata, not the primary identity.
- Multiple devices per store are supported.
- Do not modify `EssaeTransport.kt` for cloud, admin, registration, or notification work unless specifically requested; preserve the physical upload lifecycle.

## Read-only database audit notes
The initial audit reported:
- No obvious structural failure in the inspected public schema; primary/foreign key and check constraints were present and validated.
- Supabase advisors raised security/performance findings that need review, including broadly executable `SECURITY DEFINER` RPCs and an unindexed foreign key.
- `store_ip_map` had no rows, while report resolution depended on IP mapping in at least one view. Prefer registered device ID → `store_devices` → `stores`, with IP fallback only where justified.
- Some active stores lacked active registered devices.
- A small number of master/current price mismatches and push targets without device-state rows were reported; inspect exact records and app semantics before changing status or prices.
- Leaked-password protection was reported disabled.
- These are audit leads, not authorization to alter production.

## Android audit still required
Inspect the exact current branch source and cross-check:
- Supabase RPC names, parameters, return schemas, auth/token handling and nullability against live SQL definitions.
- Admin Push target creation, local pending state, retry and reconciliation behavior.
- CSV import/master-price semantics and store/device targeting.
- Physical upload completion/error callbacks and UI status transitions.
- QR/device registration, token persistence and multi-device-per-store behavior.
- Room entities, DAO queries, database version, migrations and schema validation.
- JSON parsing, network failures, coroutine lifecycle, threading and empty/missing response handling.
- Build configuration and test coverage.

## Working method for the next conversation
1. Read this handoff and the existing project-brain index/master handoff.
2. Fetch the latest `feature/qr-device-registration` source from GitHub; do not assume local or prior-turn source is current.
3. Inspect the live Essae schema/functions read-only and compare with Kotlin RPC callers.
4. Produce a prioritized issue list with file/function or database object, evidence, impact and a minimal proposed fix.
5. Ask/confirm before each individual change. Apply one change at a time, build/test where possible, then move to the next.
6. Never claim crash-free status without build, migration and device-flow testing.

## Current state
- No production database changes were made as part of the initial read-only audit.
- No Android source changes should be considered verified by this audit note.
- Next step: perform the source-level Android ↔ Supabase contract comparison, then agree on issue #1.

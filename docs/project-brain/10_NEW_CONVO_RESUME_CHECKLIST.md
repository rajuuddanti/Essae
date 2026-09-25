# New Conversation Resume Checklist

## First read
- 00_PROJECT_BRAIN_INDEX.md
- 00_MASTER_HANDOFF.md
- 02_FINAL_ARCHITECTURE_AND_DECISIONS.md
- 12_CURRENT_STATE_2026-09-24.md

## Current known state
1. Stable Essae transport must be preserved.
2. Supabase database and Admin Auth are working.
3. Admin Login successfully reaches the Admin Dashboard.
4. The next implementation milestone is the real Admin Push screen and reports.
5. Do not change the database unless new evidence requires it.
6. Do not replace the working Essae transport while adding cloud/admin features.

## Before coding
- Inspect the current Android source.
- Compare it with the stable baseline.
- Check the existing Supabase schema/RPCs.
- Identify the smallest required change.
- Build and test on the physical device.

## GitHub
Repository: rajuuddanti/Essae
Default branch: main
Keep project brain under docs/project-brain/ and SQL under supabase/.

## Security
Never commit local.properties, passwords, service-role keys, or other secrets.

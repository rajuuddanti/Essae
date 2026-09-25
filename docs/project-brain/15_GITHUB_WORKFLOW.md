# GitHub Workflow

Repository:
rajuuddanti/Essae

Default branch:
main

Purpose:
Use GitHub as the durable source-control and project-memory location.

Recommended structure:
- Android Studio project at repository root
- docs/project-brain/ for durable project documentation
- supabase/ for database SQL and migrations

Never commit:
- local.properties
- service-role or secret Supabase keys
- passwords
- generated build directories
- unnecessary APK/output artifacts

When changing the Android app:
1. Preserve the stable baseline.
2. Make the smallest required change.
3. Build/test.
4. Commit with a descriptive message.
5. Push to main only after verification.

# Deployment and Build Log

## Stable baseline
The project was developed from the stable MahaMart Scale Manager / Essae v4 project.

## Admin auth checkpoint
Initial checkpoint had a Gradle Kotlin DSL error because java.util.Properties was not imported. This was fixed.

The next checkpoint had missing Compose imports:
- rememberSaveable
- detectTapGestures

These imports were corrected.

A later checkpoint used the wrong Supabase host. The correct project URL is the current Supabase project URL documented in the local configuration/source; credentials must not be committed.

After the URL correction and login-field cleanup, the app reached the Admin Dashboard successfully.

## Deployment rule
Every significant Android change should be tested on the physical device and should preserve the working Essae scale communication.

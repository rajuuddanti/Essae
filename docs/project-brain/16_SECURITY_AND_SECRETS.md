# Security and Secrets

## Rules
- Never commit Supabase service-role/secret keys to GitHub.
- Never commit passwords or authentication tokens.
- Keep local.properties and machine-specific configuration out of Git.
- Publishable/client-side Supabase configuration may be used by an Android client, but credentials should still be managed deliberately and not confused with service-role secrets.
- Audit records should capture actor/device/IP information where designed by the schema.

## Current admin model
Admin authentication is handled by Supabase Auth. Authorization is checked through the ADMIN role and active profile state.

The repository documentation describes configuration and architecture, not live passwords.

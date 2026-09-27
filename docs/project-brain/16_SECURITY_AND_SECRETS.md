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


## Supabase Security Advisor — 2026-09-27

A live Supabase Dashboard Security Advisor screenshot was captured on 2026-09-27. The detailed findings are recorded in `docs/project-brain/20_SUPABASE_ADVISOR_FINDINGS_2026-09-27.md`.

Visible findings include SECURITY DEFINER views, the `profiles` Auth RLS Initialization Plan finding, Leaked Password Protection Disabled, and PUBLIC execution of several SECURITY DEFINER admin functions. These are recorded as items for controlled review; they are not to be blindly "fixed" without checking definitions, RLS, grants, and application call paths.

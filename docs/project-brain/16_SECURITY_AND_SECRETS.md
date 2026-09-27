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


## 2026-09-27 — Device token security validated

- Store RPC authorization now uses a per-device secret token in addition to device ID.
- Supabase stores the SHA-256 hash; the raw token is returned only during registration.
- IP is never treated as authorization.
- Multiple devices per store remain supported.
- Live `pgcrypto` is installed in the `extensions` schema. Token registration functions therefore use `search_path = public, extensions`.
- The registration issue `function gen_random_bytes(integer) does not exist` was fixed in the live database and the repository migration was corrected.

## Notification branch security

The notification experiment is isolated on `feature/admin-push-notifications`. Notifications must not become a second authorization system. Supabase remains the source of truth for Admin Push targeting and device authorization.

Never commit Firebase service-account JSON, private keys, or other push credentials to GitHub.

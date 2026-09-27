# Admin Push Notifications — Experimental Branch

## Branch

`feature/admin-push-notifications`

This branch was created from `feature/admin-device-registration` on 2026-09-27.

## Purpose

Add a notification to a registered store device when the device receives an Admin Push price update.

Example:

```text
🔔 New Admin Price Update
A new price update was received. Please upload it to the scale.
```

## Non-negotiable architecture

The notification is an alert layer only.

```text
Admin Push
    ↓
Supabase — source of truth
    ↓
Store device receives/syncs update
    ├── Room price changes
    ├── RED/PENDING state
    └── notification
            ↓
       Store uploads to Essae
            ↓
       physical confirmation
            ↓
       confirmed cloud price / LC updates
```

Do not use the notification as proof that the scale was updated.
Do not replace the existing Admin Push polling/sync with notifications.
If a notification is missed, the pending Admin Push must still be received by normal Supabase sync.

## Device model

Current device security remains:

- STORE CODE = store identity.
- DEVICE ID = physical Android device identity.
- DEVICE TOKEN = per-device secret.
- IP = audit/network information only.

Multiple devices per store remain supported.

## Preferred implementation direction

Use Firebase Cloud Messaging (FCM) as the notification transport if the Android project is suitable for Firebase integration.

FCM should be mapped to the registered physical device, not used as the authority for store identity.

Do not commit Firebase service-account JSON, private keys, or other server credentials.

## First test

1. Build the notification branch APK.
2. Install it on the already registered test phone.
3. Allow notification permission when Android requests it.
4. From Admin, select one PLU such as SUGAR LOOSE.
5. Push a new price to the registered store.
6. Store device receives the Admin Push through the existing sync path.
7. Store device displays the notification.
8. Verify the existing RED/PENDING state still works.
9. Verify physical Essae upload is still required.
10. Verify Admin confirmed price changes only after successful physical upload.

## Stable branch protection

Do not modify or merge into `feature/admin-device-registration` until the notification feature has been built and physically tested.

Never rewrite `EssaeTransport.kt` for notification work.

## Current security checkpoint

The live `pgcrypto` extension is installed in the `extensions` schema. Device-token registration functions therefore use `search_path = public, extensions`.

The live registration test succeeded after applying:

```sql
alter function public.verify_store_device_token(text, text)
set search_path = public, extensions;

alter function public.store_register_device(text, text, text, text)
set search_path = public, extensions;
```

Corrected migration commit:
`773e1f9bcc947c06625329d13fc5cfda2da8cbb2`.

## Current status

- Device-token registration: working on test phone.
- Admin Push: existing working implementation preserved.
- Device → Admin manual price reflection: next functional test.
- Admin Push notification: new experimental branch, implementation not yet merged.

## Resume instruction

When continuing this branch, read this file first, then inspect the current Android and Supabase source on `feature/admin-push-notifications` before making changes.
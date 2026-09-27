# Admin Push Notifications — Experimental Branch

## Branch

`feature/admin-push-notifications`

This branch was created from `feature/admin-device-registration` on 2026-09-27.

## Purpose

Add a live notification to a registered store device when the device receives an Admin Push price update.

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
    └── FCM notification
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
- FCM TOKEN = notification endpoint for the registered Android device.
- IP = audit/network information only.

Multiple devices per store remain supported.

## Firebase / FCM implementation

The Android app uses Firebase Cloud Messaging.

- Firebase Android package: `com.mahamart.essae`.
- Firebase configuration is committed as `app/google-services.json`.
- Firebase Messaging dependency uses the Firebase Android BoM.
- Registered devices obtain an FCM token and register it through `store_set_fcm_token`.
- The server-side notification sender is the Supabase Edge Function:
  `send-admin-push-notification`.
- The Edge Function validates the logged-in Admin and the Admin Push update before sending to the target stores' registered FCM tokens.
- Firebase service-account credentials are stored in Supabase as the secret `FIREBASE_SERVICE_ACCOUNT_JSON`; the private service-account JSON must never be committed to Git.
- The Edge Function was successfully deployed to the live MahaMart Supabase project.

## Notification behavior

FCM live notification testing is working.

When the store app is backgrounded/closed normally, the phone receives the FCM notification.

A duplicate notification was identified when opening the app: the FCM notification was shown first, then the normal Admin Push sync path showed a second local notification for the same update.

The duplicate local notification from the normal sync path was removed. FCM remains the notification source.

Current notification commit:
`ded10f8cec646d9bcb5706ff3f82ebda81b7baf8`.

## Admin Push pending-audit fix

During testing, Admin Push sync failed because the server-side `store_price_change_log.old_price` column is NOT NULL, while the Admin Push pending-audit payload did not provide `old_price`.

The store sync code was updated to derive `old_price` from the existing local PLU price before sending the pending audit payload.

Current fix commit:
`cdd0444615ae12e7e3352c81d972f5064477b5fd`.

This preserves the intended audit lifecycle:

```text
existing local price = old_price
Admin Push price      = new_price
        ↓
pending audit
        ↓
physical Essae upload
        ↓
confirmed upload / LC update
```

## Main UPLOAD ALL connection preflight

During physical upload testing, Label Design's upload path correctly tests the scale connection and immediately reports an error when the scale is disconnected.

Main Activity's `UPLOAD ALL` path previously did not perform the same preflight check. It could enter the bulk-upload path and remain at a message such as:

```text
Uploading 133 PLUs...
```

while waiting for the scale/protocol connection to fail.

A small Main Activity-only preflight was added:

```text
UPLOAD ALL
    ↓
TESTING
    ↓
scale connection check
    ├── failure → CONNECTION ERROR and stop
    └── success → existing bulk upload continues
```

No Essae protocol implementation was rewritten.

No Label Design upload code was changed.

No Admin Push, FCM, or cloud-confirmation architecture was changed.

Current preflight commit:
`a86e79da3ca32ca5b639c03d41fa49b768070202`.

## Rollback checkpoint

Before adding the Main Activity upload preflight, a separate Git checkpoint branch was created:

`checkpoint-before-upload-preflight`

This points to the exact state immediately before the preflight change and can be used to recover the previous behavior if testing shows an unexpected regression.

The stable `main` branch and `feature/admin-device-registration` branch are not being changed by this experiment.

## First functional test sequence

1. Build the notification branch APK.
2. Install it on the already registered test phone.
3. Allow Android notification permission.
4. Put the store app in background/lock it.
5. From Admin, select one PLU such as SUGAR LOOSE.
6. Publish a new price.
7. Verify exactly one FCM notification is received.
8. Open the store app and verify the Admin Push price is applied locally.
9. Verify RED/PENDING state.
10. Verify pending audit has a valid old price.
11. With scale disconnected, tap Main → UPLOAD ALL and verify it quickly reports CONNECTION ERROR instead of hanging.
12. With scale connected, tap Main → UPLOAD ALL and verify the existing bulk upload proceeds.
13. Verify physical Essae upload is still required.
14. Verify confirmed cloud price / LC changes only after successful physical upload.

## Stable architecture rules

- Do not rewrite `EssaeTransport.kt` for cloud/admin/notification work.
- Do not use FCM delivery as proof of scale upload.
- Do not update confirmed cloud price merely because Admin Push reached the phone.
- Do not replace normal Admin Push polling/sync with FCM.
- Do not modify the stable branches until this branch is physically tested.
- Firebase service-account private credentials must remain only in Supabase secrets.

## Current status

- Device-token registration: working on test phone.
- Admin Push: working.
- FCM live notification: working.
- Duplicate notification on app open: fixed.
- Admin Push pending-audit `old_price` failure: fixed in app code.
- Main UPLOAD ALL disconnected-scale handling: preflight fix added; physical connected/disconnected test still pending.
- Label Design upload/test-connection behavior: unchanged and working.
- Supabase Edge Function `send-admin-push-notification`: deployed.
- Branch remains experimental and has not been merged into stable branches.

## Resume instruction

When continuing this branch, read this file first, then inspect the current Android and Supabase source on `feature/admin-push-notifications` before making changes.

The immediate next test is the Main Activity UPLOAD ALL connection preflight, followed by a connected 133-PLU upload test.

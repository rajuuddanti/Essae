# Admin Push Notifications Branch

## Branch
`feature/admin-push-notifications`

Based on:
`feature/admin-device-registration`

## Current implementation

This branch adds a **local Android system notification** when an Admin Push is actually received and applied by the registered store device.

Flow:

```
Admin Push
  -> Supabase
  -> existing StoreAdminPushSync polling
  -> Room price update
  -> pending audit
  -> notification
```

The notification is only posted when `pullAndApply()` reports one or more newly applied PLUs, so repeated polling does not repeatedly notify for the same already-ACKed update.

Notification example:

- Title: `New Admin Price Update`
- Body: `A new price update was received. Please upload it to the scale.`

For multiple prices the title becomes `N New Admin Price Updates`.

Tapping the notification opens the existing `MainActivity`.

## Android 13+

The branch declares `POST_NOTIFICATIONS` and requests notification permission for a device that is already registered with a valid device token. Android 13+ requires runtime notification permission for normal app notifications.

## Files changed

- `app/src/main/java/com/mahamart/essae/AdminPushNotification.kt`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/mahamart/essae/MainActivity.kt`

## Important limitation

This first implementation deliberately does **not** add Firebase/FCM, Supabase Edge Functions, or any new server-side push infrastructure.

Therefore this notification is generated when the existing Admin Push sync runs. The current app polls Admin Push every 10 seconds while the main app process is active.

A future FCM phase can provide true remote/background push delivery when the app is not running. It should be added without changing the existing Admin Push source-of-truth lifecycle.

## Stable branch safety

No changes were made to:

- `feature/admin-device-registration`
- Supabase schemas/functions
- device-token security
- Admin Push lifecycle
- Essae transport/upload protocol

This branch is intended for independent notification testing before any merge.

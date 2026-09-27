# Live FCM Admin Push Notifications

Branch: `feature/admin-push-notifications`

## What changed

- Android now uses Firebase Cloud Messaging (FCM).
- Every registered physical store device registers its current FCM token with Supabase.
- Admin Push still creates the same Supabase Admin Push record and does not change physical scale prices.
- After a successful Admin Push publish, the Admin app calls the protected Supabase Edge Function.
- The Edge Function finds only the targeted active store devices and sends an FCM notification.
- Existing store polling/sync remains the fallback/source-of-truth path.
- The FCM notification is an alert only; physical Essae upload is still required before confirmed store price changes.

## 1. Firebase Android setup

Create/select a Firebase project and add Android app package:

`com.mahamart.essae`

Download `google-services.json` and place it here:

`app/google-services.json`

The file is intentionally ignored by Git.

Firebase currently documents the Google Services Gradle plugin and Firebase BoM setup here:
https://firebase.google.com/docs/android/setup

## 2. Supabase SQL

Run:

`supabase/MahaMart_FCM_NOTIFICATION_MIGRATION.sql`

This adds:

- `store_devices.fcm_token`
- `store_set_fcm_token(device_id, device_token, fcm_token)`

The FCM token is never returned to the Admin UI.

## 3. Firebase server credential

In Firebase Console:

Project settings -> Service accounts -> Generate new private key.

Keep that JSON private. Do NOT commit it to GitHub and do NOT put it in the Android app.

In Supabase Edge Function secrets, create:

`FIREBASE_SERVICE_ACCOUNT_JSON`

Value: the complete service-account JSON.

Supabase already provides `SUPABASE_URL` and server-side secret-key access to Edge Functions. The function uses the service credential only to obtain an OAuth access token for FCM HTTP v1.

## 4. Deploy the Edge Function

Function:

`send-admin-push-notification`

Path:

`supabase/functions/send-admin-push-notification/index.ts`

Deploy with the Supabase CLI:

`supabase functions deploy send-admin-push-notification`

## 5. Test sequence

1. Add `google-services.json`.
2. Run the FCM SQL migration.
3. Set `FIREBASE_SERVICE_ACCOUNT_JSON` in Supabase Edge Function secrets.
4. Deploy `send-admin-push-notification`.
5. Build/install this branch.
6. Open the app once and allow Android notifications.
7. Register/re-register the test store phone if necessary.
8. Confirm the device receives an FCM token.
9. Put the app in background and lock the phone.
10. From Admin Push, change one PLU price for that store.
11. The phone should receive the notification without opening the app first.
12. Open the app and verify the normal Admin Push pending/RED state is still present.
13. Press Upload All and verify only successful Essae upload changes the confirmed cloud price.

## Important Android behavior

A normal background/locked app can receive FCM without the app UI being open. Firebase documents that notification messages received in the background are displayed by the system tray, while foreground messages reach `FirebaseMessagingService.onMessageReceived`.

If the user explicitly force-stops the app from Android Settings, Android can suppress delivery until the app is opened again. OEM battery restrictions can also affect background delivery.

## Security

Never put the Firebase service-account JSON, private key, Supabase service-role key, or other server credentials in the APK or Git repository.

Firebase FCM HTTP v1 requires authenticated server-side requests. Supabase Edge Function secrets are the intended place for the server credential.

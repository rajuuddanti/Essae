# Multi-Device Store Registration

## Decision

A store is identified by its permanent **store code** (MM001–MM017). A physical Android device is a separate identity.

Example:

```
MM001 Burugupally
├── Device A
├── Device B
└── Device C
```

## Identity rules

- STORE CODE = permanent business/store identity.
- DEVICE ID = physical Android device identity.
- IP = current network/audit information only.
- Multiple devices can belong to the same store.
- One physical device ID can belong to only one store at a time.

## Registration

Admin generates a one-time registration code for a store.

Each device uses its own code:

```
Admin
  ↓
Generate MM001 code
  ↓
Device A registers

Generate another MM001 code
  ↓
Device B registers
```

Registration creates/updates `store_devices`.

## Admin Push

Admin Push remains store-targeted.

Because multiple devices can exist at one store, a store-level `SYNCED` flag is not enough. The migration adds `admin_price_update_device_state` so each device has its own SYNCED/UPLOADED state.

This prevents:

- Device A syncing from preventing Device B from receiving the same push.
- Device A uploading from falsely marking Device B's scale as uploaded.

The store-level target is considered fully UPLOADED only after all active devices that existed before the push have uploaded it.

## Migration

Apply:

`supabase/MahaMart_MULTI_DEVICE_REGISTRATION_MIGRATION.sql`

after the existing fresh schema and Admin Push migration.

The existing `store_ip_map` and legacy RPCs are intentionally preserved for compatibility while the Android app moves to the new device registry.

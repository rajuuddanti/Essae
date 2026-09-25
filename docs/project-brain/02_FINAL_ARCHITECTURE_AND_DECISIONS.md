# Final Architecture and Decisions

## Architecture
- Android app is local-first.
- PLU/product data lives locally through Room.
- Essae scales are reached directly over TCP/IP.
- Supabase is the cloud audit/admin layer.
- Stores do not need store-user login.
- Admin authentication uses Supabase Auth and the profiles role.
- Admin-only screens expose reports, store/IP mapping, and price administration.

## Price state
A phone price is not automatically a confirmed scale price.

ADMIN PUSH -> STORE SYNC -> LOCAL PRICE CHANGE -> RED/PENDING -> UPLOAD ALL -> ESSAE -> SUCCESS = CONFIRMED; FAILED = RED/PENDING.

## Important decisions
- Do not replace the proven EssaeTransport implementation merely to add cloud features.
- Sync and Upload are different operations.
- Pending/red state is persistent and must not silently revert on app restart.
- Audit who changed a price, when, from which device/IP, and whether the scale upload succeeded.

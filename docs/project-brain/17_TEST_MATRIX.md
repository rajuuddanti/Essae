# Test Matrix

## Authentication
- Normal store launch -> store UI
- Long press Scale Manager for 10 seconds -> Admin Login
- Valid admin credentials -> Admin Dashboard
- Non-admin/inactive profile -> denied
- Wrong credentials -> denied

## Price state
- Admin publishes update -> target store receives pending update
- Store syncs -> local price reflects admin update
- Manual local price change -> RED/PENDING state
- Upload All succeeds -> confirmed state
- Upload All fails -> remains RED/PENDING
- App restart while pending -> pending state remains

## Essae
- PLU upload reaches scale
- Scale ACK is handled
- Price is written correctly
- Multi-record frame behavior remains intact
- Existing Upload All regression tests remain green

## Reports
- Admin can view reports
- Store names resolve correctly
- IP/device information is retained
- CSV/Excel export matches in-app report data

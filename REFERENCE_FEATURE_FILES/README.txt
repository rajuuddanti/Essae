LAST SEND-OFF REFERENCE FILES

The project outside this folder is the last stable 4.0.1 manual-audit + Supabase build.
The files in this folder are the feature/reference files discussed afterward (Admin login, Sync, Admin Push, Reports, pending-scale behavior, etc.).
Some experimental feature files were not compile-verified and are intentionally kept as reference rather than replacing the stable MainActivity.

Final agreed behavior:
- Store: no login, manual Sync, persistent red pending until physical Upload All succeeds.
- Admin: hidden login, Admin Push all/specific stores, Reports, mapping.
- Manual edits are audited separately from Admin Push.
- Reports: compact Today + Filter popup; Store/IP/SKU/date/status filters.

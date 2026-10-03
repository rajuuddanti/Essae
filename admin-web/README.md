# MahaMart Admin Web Console (Testing)

A dependency-free, static UI prototype for the MahaMart Scale Manager web console.

## Current scope
- Overview, store directory, device directory, Admin Push History (view-only), and Reports.
- Search/filter controls and CSV export of the currently visible **sample** report rows.
- Admin Push is visibly disabled; no price updates, CSV pushes, device registration, or other write actions exist.

## Safety status
- This is a UI prototype, not a connected or authenticated application.
- All displayed records and counts are illustrative sample data, not live store records.
- It does not connect to Supabase, does not change production data, and contains no keys or secrets.
- Do not publish as an operational admin portal. Before connecting live data, implement Supabase Auth admin/active-profile authorization and verify read access is enforced by RLS or narrowly scoped RPCs. Never place a service-role key in browser code.

## Run locally
Open `index.html` in a modern browser. No build step or package installation is required.

## Next development phase
After review of the UI, implement real admin sign-in and read-only data loading with the existing Supabase schema/RPCs. Keep all write operations disabled until separately approved and reviewed.

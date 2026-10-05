# MahaMart Scale Manager — Current State (2026-10-05)

## Project
- Repository: `rajuuddanti/Essae`
- Android package: `com.mahamart.essae`
- App: MahaMart Scale Manager / Essae SI-810PR
- Supabase project: `idkhzkehbdzsawpuevgd`
- Current feature branch for Admin Push popup work: `feature/admin-push-price-popup-2026-10-05`

## Stable principles
- Do not alter Essae scale transport/protocol unless explicitly requested.
- Do not clear Room/local app data as part of normal fixes.
- Stores do not require login; Admin requires login.
- Supabase is used for registration, cloud sync, Admin Push, notifications and reports.
- CSV/master-price and scale-upload behavior must remain separate from the physical scale transport.
- Existing working features must remain unchanged unless the requested fix directly requires them.

## Current working price-marker behavior
The PLU red changed marker is based on the latest audit for each PLU.
A latest pending audit remains red after Admin Push changes the Room price.
Physical scale upload marks the matching pending audit uploaded and the red marker disappears.

Important:
- Old pending historical audits must not resurrect the red marker.
- Latest audit wins for a PLU.
- No Supabase migration is required for this behavior.

## Admin Push popup
Purpose:
- When a store receives an Admin Push and opens/resumes the app, show the pending Admin price changes.
- Popup displays compact lines such as:
  `SUGAR LOOSE ₹98 → ₹102`
- No `.0` suffix for whole-number prices.
- Popup shows:
  `New price updates: N`
- The SKU list is scrollable.
- Popup repeats on app resume/open while the effective Admin Push remains pending.

Current implementation includes:
- Room DAO query for pending Admin Push changes.
- Latest uploaded-price baseline lookup so a sequence such as `₹98 → ₹100 → ₹102` is presented as `₹98 → ₹102`.
- Price formatting with trailing-zero stripping.
- Scrollable popup and update count.
- Sync is awaited before popup data is read to avoid a race.

## Known popup edge case — NOT FIXED YET
Sequence:
1. Store master = ₹59
2. Admin Push ₹591
3. Admin Push ₹59
4. The second push is correctly skipped by the server-side same-price validation.
5. A stale local pending audit for ₹591 can still cause the popup to show `₹59 → ₹591`.

Desired behavior:
- Latest effective Admin Push must be compared with the current Store Master.
- If latest pushed price equals Store Master, there should be NO popup.
- Older pending Admin Push audits must not resurrect a superseded price.
- Example:
  - `59 → 591 → 59` = no popup
  - `59 → 591` = popup `₹59 → ₹591`

Do not change the existing server-side same-price validation. It is working.

## CSV behavior
The CSV file tested in the current discussion contained 133 valid rows.
The intended server-side rule is:
- Store Master Price == pushed price -> skip.
- Different price -> actionable push.

The current observed 133 -> 105 result should be explained through the actual push/filter pipeline rather than assuming invalid CSV rows.

## Typography decision
The original offline v4.0.0 APK was inspected as a visual/source reference.
Conclusion:
- It does not appear to rely on a special bundled font.
- Its compact look comes mainly from Material/Compose typography hierarchy and explicit FontWeight choices.
- User decided NOT to change typography now.
- Keep the present build as-is and test current behavior in real time.
- Do not make a typography-only branch unless the user explicitly asks later.

## Testing status / recent behavior
- Admin Push latest-price behavior has been tested.
- Physical upload behavior is working in prior tests.
- Multiple Admin Pushes for the same SKU collapse correctly for the effective latest price in the intended flow.
- The remaining known issue is stale local popup audit state for a later same-price push.

## Next continuation rule
If continuing this project:
1. Read the project-brain docs first.
2. Preserve the current branch/state.
3. Do not restart architecture.
4. Fix only the explicitly requested issue.
5. Keep scale transport/protocol untouched.

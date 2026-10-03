# Initial Supabase Audit Findings — 2026-10-03

> Read-only first-pass observations for the Essae project. Findings must be revalidated against current schema/data and Android call sites before any corrective change.

## Reported findings to verify

| Priority for review | Finding | Verification / next action |
|---|---|---|
| High | Several `SECURITY DEFINER` functions were reported executable by `anon`, including legacy IP-based pending/sync RPCs. | Inventory exact signatures, function bodies, grants and Android callers. Determine whether anonymous execution is required; do not revoke blindly. |
| High | Store report resolution is incomplete: `store_ip_map` was reported empty and report logic uses IP mapping. | Inspect view/function SQL and validate resolution by registered `device_id` through `store_devices` to `stores`; preserve justified fallback for historical rows. |
| Medium | Some active stores were reported without active registered devices. | Compare current active-store/device inventory with rollout expectations; not automatically a defect. |
| Medium | A master-versus-physical scale price mismatch was reported. | Inspect exact store, PLU, values and timestamps; determine if pending sync or expected manager upload before reconciliation. |
| Medium | Some Admin Push targets were reported without matching device-state rows. | Inspect affected update/store pairs and all related history/status rows before deciding whether state repair is needed. |
| Medium | RLS-enabled tables without direct policies and a profile policy performance warning were reported. | Trace intended RPC access and role grants; test least-privilege access paths before changing policies. |
| Advisory | Leaked-password protection was reported disabled. | Review Auth configuration and enable according to operational requirements. |
| Advisory | An unindexed foreign key and unused indexes were reported by advisors. | Confirm query plans/workload before adding or removing indexes. |

## Scope limitations
- This is not a complete app crash audit.
- No exact exception traces, device test results, or complete Kotlin-to-RPC mapping are recorded here.
- Advisor warnings are not automatically exploitable vulnerabilities or runtime failures.
- A healthy schema does not guarantee a crash-free Android app.
- No schema, policy, function, price, registration, or status data was changed for this review.

## One-at-a-time remediation protocol
For each confirmed issue:
1. Record exact evidence and affected object/call site.
2. Describe the user-visible or operational impact.
3. Propose the smallest safe change and any migration/rollback plan.
4. Obtain user approval.
5. Change only that issue, verify build/SQL behavior and relevant flow, then document the result.

-- Admin Price Reconciliation Report
-- Source of truth remains the existing operational tables.
-- This view is for export/reporting only; it does not modify app behavior.

create or replace view public.admin_price_reconciliation_report
with (security_invoker = true)
as
with admin_push_items as (
  select
    u.id as update_id,
    u.created_at as admin_pushed_at,
    u.created_by,
    coalesce(p.full_name, '') as admin_name,
    u.mode,
    u.note,
    t.store_id,
    s.store_code,
    s.store_name,
    t.status as target_status,
    t.synced_at,
    t.uploaded_at as admin_target_uploaded_at,
    (item->>'plu_no')::integer as plu_no,
    coalesce(item->>'plu_name', '') as plu_name,
    (item->>'new_price')::numeric as admin_push_price
  from public.admin_price_updates u
  join public.admin_price_update_targets t on t.update_id = u.id
  join public.stores s on s.id = t.store_id
  left join public.profiles p on p.id = u.created_by
  cross join lateral jsonb_array_elements(u.items) item
),
latest_admin_push as (
  select distinct on (store_id, plu_no) *
  from admin_push_items
  where target_status <> 'SUPERSEDED'
  order by store_id, plu_no, admin_pushed_at desc, update_id desc
),
latest_upload_after_push as (
  select
    ap.store_id,
    ap.plu_no,
    l.id as upload_log_id,
    l.old_price as manager_old_price,
    l.new_price as manager_upload_price,
    l.source as upload_source,
    l.status as upload_status,
    l.changed_at as upload_changed_at,
    l.uploaded_at as manager_uploaded_at,
    l.device_ip as manager_device_ip,
    l.device_id as manager_device_id,
    l.scale_ip as manager_scale_ip
  from latest_admin_push ap
  left join lateral (
    select l.*
    from public.store_price_change_log l
    left join public.store_ip_map ipm
      on ipm.device_ip = l.device_ip and ipm.active = true
    where l.plu_no = ap.plu_no
      and ipm.store_id = ap.store_id
      and l.uploaded_at is not null
      and l.status = 'UPLOADED'
      and l.uploaded_at >= ap.admin_pushed_at
    order by l.uploaded_at asc, l.changed_at asc, l.id asc
    limit 1
  ) l on true
),
manager_latest_upload as (
  select distinct on (ipm.store_id, l.plu_no)
    ipm.store_id,
    l.plu_no,
    l.plu_name,
    l.old_price as manager_old_price,
    l.new_price as manager_upload_price,
    l.source as upload_source,
    l.status as upload_status,
    l.changed_at as upload_changed_at,
    l.uploaded_at as manager_uploaded_at,
    l.device_ip as manager_device_ip,
    l.device_id as manager_device_id,
    l.scale_ip as manager_scale_ip
  from public.store_price_change_log l
  join public.store_ip_map ipm
    on ipm.device_ip = l.device_ip and ipm.active = true
  where l.uploaded_at is not null and l.status = 'UPLOADED'
  order by ipm.store_id, l.plu_no, l.uploaded_at desc, l.changed_at desc, l.id desc
)
select
  ap.store_code,
  ap.store_name,
  ap.plu_no,
  ap.plu_name,
  ap.admin_push_price,
  ap.admin_pushed_at,
  ap.admin_name,
  ap.update_id as admin_update_id,
  ap.target_status as admin_push_status,
  ap.synced_at as admin_synced_at,
  ap.admin_target_uploaded_at,
  ua.manager_upload_price as latest_manager_or_current_price,
  ua.manager_upload_price,
  ua.manager_uploaded_at,
  ua.upload_source,
  ua.upload_status,
  ua.manager_old_price,
  ua.manager_device_id,
  ua.manager_device_ip,
  ua.manager_scale_ip,
  pm.master_price as current_admin_price,
  pm.source as current_admin_price_source,
  pm.updated_at as current_admin_price_updated_at,
  case
    when ua.manager_uploaded_at is null then 'PENDING'
    when ua.manager_upload_price = ap.admin_push_price then 'MATCH'
    else 'DIFFERENT'
  end as result
from latest_admin_push ap
left join latest_upload_after_push ua
  on ua.store_id = ap.store_id and ua.plu_no = ap.plu_no
left join public.store_price_master pm
  on pm.store_id = ap.store_id and pm.plu_no = ap.plu_no

union all

select
  s.store_code,
  s.store_name,
  mu.plu_no,
  mu.plu_name,
  null::numeric,
  null::timestamptz,
  null::text,
  null::uuid,
  null::text,
  null::timestamptz,
  null::timestamptz,
  mu.manager_upload_price,
  mu.manager_upload_price,
  mu.manager_uploaded_at,
  mu.upload_source,
  mu.upload_status,
  mu.manager_old_price,
  mu.manager_device_id,
  mu.manager_device_ip,
  mu.manager_scale_ip,
  pm.master_price,
  pm.source,
  pm.updated_at,
  'MANUAL UPLOAD'
from manager_latest_upload mu
join public.stores s on s.id = mu.store_id
left join latest_admin_push ap
  on ap.store_id = mu.store_id and ap.plu_no = mu.plu_no
left join public.store_price_master pm
  on pm.store_id = mu.store_id and pm.plu_no = mu.plu_no
where ap.store_id is null;

comment on view public.admin_price_reconciliation_report is
'Admin Push vs successful Manager/Scale Upload reconciliation. Latest active Admin Push is compared with the first successful upload after that push; manager-only successful uploads are also included. Current Admin price comes from store_price_master.';

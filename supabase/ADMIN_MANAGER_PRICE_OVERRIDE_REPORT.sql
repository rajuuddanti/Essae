-- Manager Price Override Report
-- Every successful physical scale upload is retained.
-- Flags uploads that override the latest active Admin Push.

create or replace view public.admin_manager_price_override_report
with (security_invoker = true)
as
with pushes as (
  select distinct on (t.store_id,(item->>'plu_no')::integer)
    t.store_id,s.store_code,s.store_name,u.id admin_update_id,
    u.created_at admin_pushed_at,coalesce(p.full_name,'') admin_name,
    (item->>'plu_no')::integer plu_no,coalesce(item->>'plu_name','') plu_name,
    (item->>'new_price')::numeric admin_push_price
  from public.admin_price_updates u
  join public.admin_price_update_targets t on t.update_id=u.id
  join public.stores s on s.id=t.store_id
  left join public.profiles p on p.id=u.created_by
  cross join lateral jsonb_array_elements(u.items) item
  where t.status <> 'SUPERSEDED'
  order by t.store_id,(item->>'plu_no')::integer,u.created_at desc,u.id desc
),
uploads as (
  select ipm.store_id,l.id upload_log_id,l.plu_no,l.plu_name,
    l.old_price manager_old_price,l.new_price manager_upload_price,
    l.source upload_source,l.uploaded_at manager_uploaded_at,
    l.changed_at upload_changed_at,l.device_id manager_device_id,
    l.device_ip manager_device_ip,l.scale_ip manager_scale_ip
  from public.store_price_change_log l
  join public.store_ip_map ipm
    on ipm.device_ip=l.device_ip and ipm.active=true
  where l.status='UPLOADED' and l.uploaded_at is not null
)
select
  s.store_code,s.store_name,u.plu_no,u.plu_name,
  u.upload_log_id,u.manager_old_price,u.manager_upload_price,u.upload_source,
  u.manager_uploaded_at,u.upload_changed_at,u.manager_device_id,
  u.manager_device_ip,u.manager_scale_ip,
  p.admin_update_id,p.admin_pushed_at,p.admin_name,p.admin_push_price,
  case
    when p.admin_update_id is not null
      and u.manager_upload_price <> p.admin_push_price
      and u.manager_uploaded_at >= p.admin_pushed_at
      then 'ADMIN OVERRIDDEN BY MANAGER'
    when p.admin_update_id is null then 'MANUAL PRICE CHANGE'
    else 'MANAGER UPLOAD MATCHED ADMIN'
  end as result
from uploads u
join public.stores s on s.id=u.store_id
left join pushes p on p.store_id=u.store_id and p.plu_no=u.plu_no;

comment on view public.admin_manager_price_override_report is
'Every successful physical scale upload. Flags manager uploads that override the latest active Admin Push, plus manual uploads without an Admin Push. Preserves upload/device/IP/time audit fields.';

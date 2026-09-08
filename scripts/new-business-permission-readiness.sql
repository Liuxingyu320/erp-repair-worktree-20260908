/*
 * Active-business permission and OE master-data readiness report.
 * MySQL 5.7 compatible and strictly read-only. Run with a SELECT-only account.
 * Every returned row needs remediation or an explicit reviewer decision before
 * enabling the remaining live feature flags.
 */

/* Result set 1: required permission objects missing or disabled. */
select 'PERMISSION_OBJECT_NOT_READY' as issue_code,
       required.permission_code,
       coalesce(m.menu_name, '') as menu_name,
       case
           when m.menu_id is null then 'MISSING'
           when m.status <> '0' then 'DISABLED'
           else 'HIDDEN_OR_INVALID'
       end as issue_detail
from (
    select 'inv:customerCard:list' permission_code
    union all select 'inv:customerCard:query'
    union all select 'inv:customerCard:add'
    union all select 'inv:customerCard:edit'
    union all select 'inv:customerCard:record:add'
    union all select 'inv:customerCard:archive'
    union all select 'inv:customerCard:audit'
    union all select 'hr:healthCertificate:remind'
    union all select 'inv:transfer:list'
    union all select 'inv:transfer:deliver'
    union all select 'inv:transfer:receive'
    union all select 'inv:transfer:discrepancy:handle'
) required
left join sys_menu m on m.perms = required.permission_code
where m.menu_id is null or m.status <> '0'
order by required.permission_code;

/* Result set 2: active store employees missing customer-service abilities. */
select 'STORE_EMPLOYEE_MISSING_PERMISSION' as issue_code,
       shop.dept_id as scope_dept_id,
       shop.dept_name as scope_name,
       u.user_id,
       u.user_name,
       u.nick_name,
       required.permission_code
from sys_user_shop membership
join sys_dept shop
  on shop.dept_id = membership.dept_id
 and upper(coalesce(shop.dept_type, '')) = 'STORE'
 and shop.status = '0' and shop.del_flag = '0'
join sys_user u on u.user_id = membership.user_id
left join sys_user_profile profile on profile.user_id = u.user_id
join (
    select 'inv:customerCard:list' permission_code
    union all select 'inv:customerCard:query'
    union all select 'inv:customerCard:add'
    union all select 'inv:customerCard:edit'
    union all select 'inv:customerCard:record:add'
) required
where u.user_id <> 1 and u.status = '0' and u.del_flag = '0'
  and (profile.employee_status is null or profile.employee_status <> '离职')
  and not exists (
      select 1
      from sys_user_role ur
      join sys_role r on r.role_id = ur.role_id
                     and r.status = '0' and r.del_flag = '0'
      join sys_role_menu rm on rm.role_id = r.role_id
      join sys_menu permission_menu
        on permission_menu.menu_id = rm.menu_id
       and permission_menu.status = '0'
      where ur.user_id = u.user_id
        and permission_menu.perms = required.permission_code
  )
order by shop.dept_id, u.user_id, required.permission_code;

/* Result set 3: store leaders missing health-reminder ability. */
select 'STORE_LEADER_MISSING_PERMISSION' as issue_code,
       store.dept_id as scope_dept_id,
       store.dept_name as scope_name,
       leader.user_id,
       leader.user_name,
       leader.nick_name,
       required.permission_code
from sys_dept store
join sys_user leader on leader.user_id = store.leader_user_id
join (
    select 'hr:healthCertificate:remind' permission_code
) required
where upper(coalesce(store.dept_type, '')) = 'STORE'
  and store.status = '0' and store.del_flag = '0'
  and leader.status = '0' and leader.del_flag = '0'
  and leader.user_id <> 1
  and not exists (
      select 1
      from sys_user_role ur
      join sys_role r on r.role_id = ur.role_id
                     and r.status = '0' and r.del_flag = '0'
      join sys_role_menu rm on rm.role_id = r.role_id
      join sys_menu permission_menu
        on permission_menu.menu_id = rm.menu_id
       and permission_menu.status = '0'
      where ur.user_id = leader.user_id
        and permission_menu.perms = required.permission_code
  )
order by store.dept_id, leader.user_id, required.permission_code;

/* Result set 4: active warehouse personnel missing transfer abilities. */
select 'WAREHOUSE_EMPLOYEE_MISSING_PERMISSION' as issue_code,
       warehouse.dept_id as scope_dept_id,
       warehouse.dept_name as scope_name,
       u.user_id,
       u.user_name,
       u.nick_name,
       required.permission_code
from sys_dept warehouse
join sys_dept employee_dept
  on employee_dept.dept_id = warehouse.dept_id
  or find_in_set(warehouse.dept_id, employee_dept.ancestors)
join sys_user u on u.dept_id = employee_dept.dept_id
left join sys_user_profile profile on profile.user_id = u.user_id
join (
    select 'inv:transfer:list' permission_code
    union all select 'inv:transfer:deliver'
    union all select 'inv:transfer:receive'
    union all select 'inv:transfer:discrepancy:handle'
) required
where upper(coalesce(warehouse.dept_type, '')) = 'WAREHOUSE'
  and warehouse.status = '0' and warehouse.del_flag = '0'
  and employee_dept.status = '0' and employee_dept.del_flag = '0'
  and u.status = '0' and u.del_flag = '0' and u.user_id <> 1
  and (profile.employee_status is null or profile.employee_status <> '离职')
  and not exists (
      select 1
      from sys_user_role ur
      join sys_role r on r.role_id = ur.role_id
                     and r.status = '0' and r.del_flag = '0'
      join sys_role_menu rm on rm.role_id = r.role_id
      join sys_menu permission_menu
        on permission_menu.menu_id = rm.menu_id
       and permission_menu.status = '0'
      where ur.user_id = u.user_id
        and permission_menu.perms = required.permission_code
  )
order by warehouse.dept_id, u.user_id, required.permission_code;

/* Result set 5: every non-admin high-risk role grant requires review. */
select 'HIGH_RISK_ROLE_GRANT_REVIEW' as issue_code,
       r.role_id,
       r.role_name,
       r.role_key,
       m.perms as permission_code,
       count(distinct ur.user_id) as assigned_user_count,
       case
           when m.perms = 'inv:customerCard:archive'
                and lower(r.role_key) in
                    ('dz','zdjl','store_manager','shop_manager')
               then 'EXPECTED_MANAGER_TEMPLATE'
           else 'MANUAL_REVIEW_REQUIRED'
       end as review_reason
from sys_role r
join sys_role_menu rm on rm.role_id = r.role_id
join sys_menu m on m.menu_id = rm.menu_id
left join sys_user_role ur on ur.role_id = r.role_id
where r.role_id <> 1 and lower(r.role_key) <> 'admin'
  and r.status = '0' and r.del_flag = '0'
  and m.status = '0'
  and m.perms = 'inv:customerCard:archive'
group by r.role_id, r.role_name, r.role_key, m.perms
order by m.perms, r.role_id;

/* Result set 6: enabled fixed-asset OE records that are not launch-ready. */
select 'OE_MASTER_DATA_NOT_READY' as issue_code,
       config.config_id,
       config.shop_dept_id,
       item.oe_item_id,
       item.oe_item_code,
       item.oe_item_name,
       case
           when item.oe_item_id is null then 'OE_ITEM_MISSING'
           when nullif(trim(item.oe_item_code), '') is null then 'CODE_MISSING'
           when nullif(trim(item.oe_item_name), '') is null then 'NAME_MISSING'
           when nullif(trim(item.image_url), '') is null then 'IMAGE_MISSING'
           when nullif(trim(item.item_description), '') is null then 'DESCRIPTION_MISSING'
           when nullif(trim(item.order_unit), '') is null then 'ORDER_UNIT_MISSING'
           when nullif(trim(item.purchase_reference_url), '') is null then 'PURCHASE_REFERENCE_MISSING'
           when lower(item.purchase_reference_url) not like 'https://%' then 'PURCHASE_REFERENCE_NOT_HTTPS'
           when nullif(trim(item.purchase_reference_note), '') is null then 'PURCHASE_NOTE_MISSING'
           else 'PURCHASE_REFERENCE_NOT_SPECIFIC'
       end as readiness_issue
from oa_fixed_asset_config config
left join inv_oe_item item
  on item.oe_item_id = config.oe_item_id
 and item.del_flag = '0' and item.status = '0'
where config.status = '0'
  and (
      item.oe_item_id is null
      or nullif(trim(item.oe_item_code), '') is null
      or nullif(trim(item.oe_item_name), '') is null
      or nullif(trim(item.image_url), '') is null
      or nullif(trim(item.item_description), '') is null
      or nullif(trim(item.order_unit), '') is null
      or nullif(trim(item.purchase_reference_url), '') is null
      or lower(item.purchase_reference_url) not like 'https://%'
      or substring(item.purchase_reference_url,
                   locate('://', item.purchase_reference_url) + 3)
         not like '%/%'
      or nullif(trim(item.purchase_reference_note), '') is null
  )
order by config.shop_dept_id, config.config_id;

/* Result set 7: unresolved stable leaders; names alone are not authorization. */
select 'LEADER_IDENTITY_NOT_READY' as issue_code,
       d.dept_id,
       d.dept_name,
       d.dept_type,
       d.leader as leader_name_snapshot,
       d.leader_user_id,
       case
           when d.leader_user_id is null then 'LEADER_USER_ID_MISSING'
           when u.user_id is null then 'LEADER_ACCOUNT_MISSING'
           when u.status <> '0' or u.del_flag <> '0' then 'LEADER_ACCOUNT_DISABLED'
           when nullif(trim(d.leader), '') is null then 'LEADER_NAME_SNAPSHOT_MISSING'
           when trim(d.leader) <> trim(u.nick_name) then 'LEADER_NAME_MISMATCH'
           else 'UNKNOWN'
       end as readiness_issue
from sys_dept d
left join sys_user u on u.user_id = d.leader_user_id
where upper(coalesce(d.dept_type, '')) in ('STORE','WAREHOUSE')
  and d.status = '0' and d.del_flag = '0'
  and (
      d.leader_user_id is null
      or u.user_id is null
      or u.status <> '0'
      or u.del_flag <> '0'
      or nullif(trim(d.leader), '') is null
      or trim(d.leader) <> trim(u.nick_name)
  )
order by d.dept_type, d.dept_id;

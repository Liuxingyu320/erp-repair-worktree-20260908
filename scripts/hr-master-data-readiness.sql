/*
 * HR master-data go-live readiness report (MySQL 5.7 compatible).
 * READ ONLY: this file contains SELECT statements only. Run it with a SELECT-only account.
 * The application API applies user data scope; this DBA report intentionally scans the full tenant database.
 */

/* Result set 1: feature mode. Enforcement must remain false during the observation period. */
select flags.config_key,
       coalesce(nullif(trim(sc.config_value), ''), flags.default_value) as effective_value,
       case
           when flags.config_key = 'hr.master-data.enforce-on-enable'
                and lower(coalesce(nullif(trim(sc.config_value), ''), flags.default_value)) in ('true','1','yes','on')
               then 'warning_enforcement_enabled'
           else 'ready'
       end as readiness_status
from (
    select 'hr.master-data.readiness.enabled' as config_key, 'true' as default_value
    union all select 'hr.master-data.enforce-on-enable', 'false'
    union all select 'hr.completeness.policy.v2.enabled', 'true'
) flags
left join sys_config sc on sc.config_key = flags.config_key
order by flags.config_key;

/* Result set 2: organization path, company, store and leader findings. */
select findings.issue_code,
       findings.severity,
       findings.resource_type,
       findings.resource_id,
       findings.resource_name,
       findings.affected_employee_count,
       findings.detail
from (
    select 'ORG_PATH_INVALID' as issue_code,
           'P0' as severity,
           'DEPARTMENT' as resource_type,
           cast(d.dept_id as char) as resource_id,
           d.dept_name as resource_name,
           (select count(*) from sys_user u
            left join sys_user_profile eup on eup.user_id = u.user_id
            where u.del_flag = '0' and u.user_id <> 1 and u.dept_id = d.dept_id
              and (eup.employee_status is null or eup.employee_status <> '离职')) as affected_employee_count,
           case
               when upper(coalesce(d.dept_type, '')) = 'GROUP' and d.parent_id <> 0
                   then 'GROUP node must be the root organization'
               when d.parent_id <> 0 and p.dept_id is null then 'parent department does not exist'
               when d.parent_id <> 0 and p.status <> '0' then 'parent department is disabled'
               when d.parent_id <> 0 and not find_in_set(d.parent_id, d.ancestors) then 'ancestors does not contain parent_id'
               else 'invalid organization path'
           end as detail
    from sys_dept d
    left join sys_dept p on p.dept_id = d.parent_id and p.del_flag = '0'
    where d.del_flag = '0'
      and d.status = '0'
      and d.parent_id <> 0
      and (
          upper(coalesce(d.dept_type, '')) = 'GROUP'
          or p.dept_id is null
          or p.status <> '0'
          or not find_in_set(d.parent_id, d.ancestors)
      )

    union all

    select 'COMPANY_NODE_MISSING',
           'P0',
           'DEPARTMENT',
           cast(d.dept_id as char),
           d.dept_name,
           (select count(*) from sys_user u
            left join sys_user_profile eup on eup.user_id = u.user_id
            where u.del_flag = '0' and u.user_id <> 1 and u.dept_id = d.dept_id
              and (eup.employee_status is null or eup.employee_status <> '离职')),
           'the node immediately after the nearest GROUP is not a valid company layer'
    from sys_dept d
    where d.del_flag = '0'
      and d.status = '0'
      and upper(coalesce(d.dept_type, '')) <> 'GROUP'
      and not exists (
          select 1
          from sys_dept company
          where company.del_flag = '0'
            and company.status = '0'
            and upper(coalesce(company.dept_type, '')) not in ('GROUP','STORE','WAREHOUSE')
            and (company.dept_id = d.dept_id or find_in_set(company.dept_id, d.ancestors))
            and company.parent_id = (
                select nearest_group.dept_id
                from sys_dept nearest_group
                where nearest_group.del_flag = '0'
                  and nearest_group.status = '0'
                  and upper(coalesce(nearest_group.dept_type, '')) = 'GROUP'
                  and (nearest_group.dept_id = d.dept_id
                       or find_in_set(nearest_group.dept_id, d.ancestors))
                order by
                    (length(coalesce(nearest_group.ancestors, ''))
                     - length(replace(coalesce(nearest_group.ancestors, ''), ',', ''))) desc,
                    nearest_group.dept_id desc
                limit 1
            )
      )

    union all

    select 'STORE_MAPPING_MISSING',
           'P1',
           'DEPARTMENT',
           cast(d.dept_id as char),
           d.dept_name,
           (select count(*) from sys_user u
            left join sys_user_profile eup on eup.user_id = u.user_id
            where u.del_flag = '0' and u.user_id <> 1 and u.dept_id = d.dept_id
              and (eup.employee_status is null or eup.employee_status <> '离职')),
           concat('department is mounted below store node: ', s.dept_name)
    from sys_dept d
    join sys_dept s
      on s.del_flag = '0'
     and s.status = '0'
     and upper(coalesce(s.dept_type, '')) = 'STORE'
     and find_in_set(s.dept_id, d.ancestors)
    where d.del_flag = '0'
      and d.status = '0'

    union all

    select case
               when owner.leader_user_id is null
                    and nullif(trim(owner.leader), '') is null
                   then 'DEPT_LEADER_MISSING'
               else 'DEPT_LEADER_UNRESOLVED'
           end,
           'P0',
           'DEPARTMENT',
           cast(owner.dept_id as char),
           owner.dept_name,
           responsibility.affected_employee_count,
           case
               when owner.leader_user_id is null
                    and nullif(trim(owner.leader), '') is null
                   then 'department leader identity and name are both blank'
               when owner.leader_user_id is null then 'legacy leader name has no stable employee identity'
               when lu.user_id is null or lu.user_id = 1 or lu.del_flag <> '0' then 'leader account does not exist or is not eligible'
               when lu.status <> '0' then 'leader account is disabled'
               when lp.employee_status = '离职' then 'leader employee has departed'
               when nullif(trim(lu.nick_name), '') is null then 'leader employee name is blank'
               when nullif(trim(owner.leader), '') is null then 'leader name snapshot is blank'
               else 'leader name snapshot differs from employee master data'
           end
    from (
        select mapping.owner_dept_id,
               count(distinct case
                   when employee.del_flag = '0'
                    and employee.user_id <> 1
                    and (employee_profile.employee_status is null
                         or employee_profile.employee_status <> '离职')
                       then employee.user_id
               end) as affected_employee_count
        from (
            select with_company.source_dept_id,
                   coalesce(
                       (
                           select layer.dept_id
                           from sys_dept layer
                           where layer.del_flag = '0'
                             and layer.status = '0'
                             and upper(coalesce(layer.dept_type, '')) not in ('GROUP','STORE','WAREHOUSE')
                             and (layer.dept_id = with_company.source_dept_id
                                  or find_in_set(layer.dept_id, with_company.source_ancestors))
                             and find_in_set(with_company.company_dept_id, layer.ancestors)
                             and (
                                 with_company.nearest_store_depth is null
                                 or (length(coalesce(layer.ancestors, ''))
                                     - length(replace(coalesce(layer.ancestors, ''), ',', '')))
                                    < with_company.nearest_store_depth
                             )
                           order by
                               (length(coalesce(layer.ancestors, ''))
                                - length(replace(coalesce(layer.ancestors, ''), ',', ''))) asc,
                               layer.dept_id
                           limit 1
                       ),
                       with_company.company_dept_id
                   ) as owner_dept_id
            from (
                select with_group.source_dept_id,
                       with_group.source_ancestors,
                       with_group.nearest_store_depth,
                       (
                           select company.dept_id
                           from sys_dept company
                           where company.del_flag = '0'
                             and company.status = '0'
                             and upper(coalesce(company.dept_type, '')) not in ('GROUP','STORE','WAREHOUSE')
                             and company.parent_id = with_group.nearest_group_id
                             and (company.dept_id = with_group.source_dept_id
                                  or find_in_set(company.dept_id, with_group.source_ancestors))
                           order by
                               (length(coalesce(company.ancestors, ''))
                                - length(replace(coalesce(company.ancestors, ''), ',', ''))) asc,
                               company.dept_id
                           limit 1
                       ) as company_dept_id
                from (
                    select source.dept_id as source_dept_id,
                           source.ancestors as source_ancestors,
                           (
                               select nearest_group.dept_id
                               from sys_dept nearest_group
                               where nearest_group.del_flag = '0'
                                 and nearest_group.status = '0'
                                 and upper(coalesce(nearest_group.dept_type, '')) = 'GROUP'
                                 and (nearest_group.dept_id = source.dept_id
                                      or find_in_set(nearest_group.dept_id, source.ancestors))
                               order by
                                   (length(coalesce(nearest_group.ancestors, ''))
                                    - length(replace(coalesce(nearest_group.ancestors, ''), ',', ''))) desc,
                                   nearest_group.dept_id desc
                               limit 1
                           ) as nearest_group_id,
                           (
                               select (length(coalesce(nearest_store.ancestors, ''))
                                       - length(replace(coalesce(nearest_store.ancestors, ''), ',', '')))
                               from sys_dept nearest_store
                               where nearest_store.del_flag = '0'
                                 and nearest_store.status = '0'
                                 and upper(coalesce(nearest_store.dept_type, '')) = 'STORE'
                                 and (nearest_store.dept_id = source.dept_id
                                      or find_in_set(nearest_store.dept_id, source.ancestors))
                               order by
                                   (length(coalesce(nearest_store.ancestors, ''))
                                    - length(replace(coalesce(nearest_store.ancestors, ''), ',', ''))) desc,
                                   nearest_store.dept_id desc
                               limit 1
                           ) as nearest_store_depth
                    from sys_dept source
                    where source.del_flag = '0'
                      and source.status = '0'
                ) with_group
            ) with_company
            where with_company.company_dept_id is not null
        ) mapping
        left join sys_user employee on employee.dept_id = mapping.source_dept_id
        left join sys_user_profile employee_profile on employee_profile.user_id = employee.user_id
        group by mapping.owner_dept_id
    ) responsibility
    join sys_dept owner on owner.dept_id = responsibility.owner_dept_id
    left join sys_user lu on lu.user_id = owner.leader_user_id
    left join sys_user_profile lp on lp.user_id = lu.user_id
    where owner.del_flag = '0'
      and owner.status = '0'
      and (
          owner.leader_user_id is null
          or (
              lu.user_id is null
              or lu.user_id = 1
              or lu.del_flag <> '0'
              or lu.status <> '0'
              or lp.employee_status = '离职'
              or nullif(trim(lu.nick_name), '') is null
              or nullif(trim(owner.leader), '') is null
              or trim(owner.leader) <> trim(lu.nick_name)
          )
      )
) findings
order by case findings.severity when 'P0' then 0 when 'P1' then 1 else 2 end,
         findings.affected_employee_count desc,
         findings.issue_code,
         findings.resource_id;

/* Result set 3: employee-post relationship findings. */
select findings.issue_code,
       findings.severity,
       findings.resource_type,
       findings.resource_id,
       findings.resource_name,
       findings.affected_employee_count,
       findings.detail
from (
    select 'EMPLOYEE_PROFILE_MISSING' as issue_code,
           'P0' as severity,
           'EMPLOYEE' as resource_type,
           cast(u.user_id as char) as resource_id,
           u.nick_name as resource_name,
           1 as affected_employee_count,
           'employee account has no sys_user_profile row' as detail
    from sys_user u
    left join sys_user_profile up on up.user_id = u.user_id
    where u.del_flag = '0'
      and u.user_id <> 1
      and up.profile_id is null

    union all

    select 'POST_MISSING_OR_DISABLED' as issue_code,
           'P0' as severity,
           'EMPLOYEE' as resource_type,
           cast(u.user_id as char) as resource_id,
           u.nick_name as resource_name,
           1 as affected_employee_count,
           'employee has no active post' as detail
    from sys_user u
    left join sys_user_profile up on up.user_id = u.user_id
    where u.del_flag = '0'
      and u.user_id <> 1
      and (up.employee_status is null or up.employee_status <> '离职')
      and not exists (
          select 1
          from sys_user_post ur
          join sys_post p on p.post_id = ur.post_id and p.status = '0'
          where ur.user_id = u.user_id
      )

    union all

    select 'POST_MISSING_OR_DISABLED',
           'P0',
           'POST',
           cast(ur.post_id as char),
           coalesce(p.post_name, concat('missing post ', ur.post_id)),
           count(distinct ur.user_id),
           case when p.post_id is null then 'post relation references a missing post' else 'post relation references a disabled post' end
    from sys_user_post ur
    join sys_user u on u.user_id = ur.user_id and u.del_flag = '0'
    left join sys_user_profile up on up.user_id = u.user_id
    left join sys_post p on p.post_id = ur.post_id
    where u.user_id <> 1
      and (up.employee_status is null or up.employee_status <> '离职')
      and (p.post_id is null or p.status <> '0')
    group by ur.post_id, p.post_id, p.post_name
) findings
order by findings.affected_employee_count desc, findings.resource_type, findings.resource_id;

/* Result set 4: dictionary routing readiness. */
select 'DICTIONARY_ROUTE_MISSING' as issue_code,
       'P1' as severity,
       'SYSTEM_CONFIG' as resource_type,
       routes.routing_key as resource_id,
       routes.field_key as resource_name,
       0 as affected_employee_count,
       case
           when sc.config_id is null or nullif(trim(sc.config_value), '') is null then 'routing config is missing'
           when dt.dict_id is null or dt.status <> '0' then 'dictionary type is missing or disabled'
           when coalesce(active_values.active_value_count, 0) = 0 then 'dictionary has no active values'
           else 'ready'
       end as detail,
       case
           when sc.config_id is null or nullif(trim(sc.config_value), '') is null then 'missing'
           when dt.dict_id is null or dt.status <> '0' then 'missing'
           when coalesce(active_values.active_value_count, 0) = 0 then 'missing'
           else 'ready'
       end as readiness_status
from (
    select 'employeeCategory' as field_key, 'hr.onboarding.dict_type.employeeCategory' as routing_key
    union all select 'sex', 'hr.onboarding.dict_type.sex'
    union all select 'idType', 'hr.onboarding.dict_type.idType'
    union all select 'maritalStatus', 'hr.onboarding.dict_type.maritalStatus'
    union all select 'ethnicity', 'hr.onboarding.dict_type.ethnicity'
    union all select 'workCityLevel', 'hr.onboarding.dict_type.workCityLevel'
    union all select 'contractType', 'hr.onboarding.dict_type.contractType'
    union all select 'socialType', 'hr.onboarding.dict_type.socialType'
    union all select 'probationPeriod', 'hr.onboarding.dict_type.probationPeriod'
) routes
left join sys_config sc on sc.config_key = routes.routing_key
left join sys_dict_type dt on dt.dict_type = nullif(trim(sc.config_value), '')
left join (
    select dict_type, count(*) as active_value_count
    from sys_dict_data
    where status = '0'
    group by dict_type
) active_values on active_values.dict_type = dt.dict_type
order by routes.field_key;

/* Result set 5: active post x active employee-category configuration readiness. */
select case
           when c.config_id is null or c.status <> '0' then 'POSITION_CONFIG_MISSING'
           when c.account_enabled = '1' and coalesce(default_roles.active_role_count, 0) = 0
               then 'POSITION_CONFIG_ROLE_MISSING'
           else 'READY'
       end as issue_code,
       case
           when c.config_id is null or c.status <> '0' then 'P1'
           when c.account_enabled = '1' and coalesce(default_roles.active_role_count, 0) = 0 then 'P1'
           else null
       end as severity,
       p.post_id,
       p.post_name,
       category.employee_category,
       c.config_id,
       coalesce(default_roles.active_role_count, 0) as active_default_role_count,
       coalesce(usage_count.affected_employee_count, 0) as affected_employee_count
from sys_post p
cross join (
    select dd.dict_value as employee_category
    from sys_config category_route
    join sys_dict_type category_type
      on category_type.dict_type = nullif(trim(category_route.config_value), '')
     and category_type.status = '0'
    join sys_dict_data dd
      on dd.dict_type = category_type.dict_type
     and dd.status = '0'
    where category_route.config_key = 'hr.onboarding.dict_type.employeeCategory'
) category
left join hr_onboarding_position_config c
       on c.post_id = p.post_id
      and c.employee_category collate utf8mb4_general_ci
          = category.employee_category collate utf8mb4_general_ci
left join (
    select cr.config_id,
           count(distinct case when r.status = '0' and r.del_flag = '0' then r.role_id end) as active_role_count
    from hr_onboarding_position_config_role cr
    left join sys_role r on r.role_id = cr.role_id
    group by cr.config_id
) default_roles on default_roles.config_id = c.config_id
left join (
    select ur.post_id,
           up.employee_category,
           count(distinct ur.user_id) as affected_employee_count
    from sys_user_post ur
    join sys_user u on u.user_id = ur.user_id and u.del_flag = '0' and u.user_id <> 1
    join sys_user_profile up on up.user_id = u.user_id and coalesce(up.employee_status, '') <> '离职'
    group by ur.post_id, up.employee_category
) usage_count
       on usage_count.post_id = p.post_id
      and usage_count.employee_category collate utf8mb4_general_ci
          = category.employee_category collate utf8mb4_general_ci
where p.status = '0'
order by case
             when c.config_id is null or c.status <> '0' then 0
             when c.account_enabled = '1' and coalesce(default_roles.active_role_count, 0) = 0 then 1
             else 2
         end,
         affected_employee_count desc,
         p.post_sort,
         p.post_id,
         category.employee_category;

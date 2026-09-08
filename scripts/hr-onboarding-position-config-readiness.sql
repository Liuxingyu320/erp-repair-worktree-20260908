/*
 * HR onboarding position-configuration go-live readiness report.
 * Read-only by design. Run with an account that has SELECT privileges only.
 * Result set 1 checks the stable system-config routing keys.
 * Result set 2 checks every active post x active configured employee category.
 */

select routes.field_key,
       routes.routing_key,
       nullif(trim(sc.config_value), '') as resolved_dict_type,
       case
           when sc.config_id is null or nullif(trim(sc.config_value), '') is null
               then 'missing_dictionary_routing'
           when dt.dict_id is null or dt.status <> '0'
               then 'missing_dictionary_routing'
           when coalesce(active_values.active_value_count, 0) = 0
               then 'missing_dictionary_routing'
           else 'ready'
       end as routing_status
from (
    select 'employeeCategory' as field_key,
           'hr.onboarding.dict_type.employeeCategory' as routing_key
    union all select 'sex', 'hr.onboarding.dict_type.sex'
    union all select 'idType', 'hr.onboarding.dict_type.idType'
    union all select 'maritalStatus', 'hr.onboarding.dict_type.maritalStatus'
    union all select 'ethnicity', 'hr.onboarding.dict_type.ethnicity'
    union all select 'workCityLevel', 'hr.onboarding.dict_type.workCityLevel'
    union all select 'contractType', 'hr.onboarding.dict_type.contractType'
    union all select 'socialType', 'hr.onboarding.dict_type.socialType'
    union all select 'probationPeriod', 'hr.onboarding.dict_type.probationPeriod'
) routes
left join sys_config sc
       on sc.config_key = routes.routing_key
left join sys_dict_type dt
       on dt.dict_type = nullif(trim(sc.config_value), '')
left join (
    select dict_type, count(*) as active_value_count
    from sys_dict_data
    where status = '0'
    group by dict_type
) active_values on active_values.dict_type = dt.dict_type
order by routes.field_key;

select p.post_id,
       p.post_name,
       category.employee_category,
       category.employee_category_label,
       c.config_id,
       case when c.config_id is null then 1 else 0 end as missing_mapping,
       case when c.config_id is not null and c.status <> '0' then 1 else 0 end as disabled_mapping,
       case
           when c.config_id is not null
                and c.status = '0'
                and c.account_enabled = '1'
                and coalesce(default_roles.active_role_count, 0) = 0
               then 1 else 0
       end as missing_default_roles,
       c.data_scope_strategy,
       c.contract_type_mode,
       c.default_contract_type,
       c.social_type_mode,
       c.default_social_type,
       c.probation_period_mode,
       c.default_probation_period,
       c.job_grade,
       c.account_enabled,
       coalesce(default_roles.active_role_count, 0) as active_default_role_count,
       case
           when c.config_id is null then 'missing_mapping'
           when c.status <> '0' then 'disabled_mapping'
           when c.account_enabled = '1' and coalesce(default_roles.active_role_count, 0) = 0
               then 'missing_default_roles'
           else 'ready'
       end as readiness_status
from sys_post p
cross join (
    select dd.dict_value as employee_category,
           dd.dict_label as employee_category_label
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
      and c.employee_category = category.employee_category
left join (
    select cr.config_id,
           count(distinct case
               when r.role_id is not null and r.status = '0' and r.del_flag = '0' then r.role_id
           end) as active_role_count
    from hr_onboarding_position_config_role cr
    left join sys_role r on r.role_id = cr.role_id
    group by cr.config_id
) default_roles on default_roles.config_id = c.config_id
where p.status = '0'
order by p.post_sort, p.post_id, category.employee_category;

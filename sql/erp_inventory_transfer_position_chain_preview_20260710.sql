-- 调拨目标门店岗位审批链只读预览。
-- 仅创建当前连接的临时表，不修改任何业务表；兼容 MySQL 5.7。
-- 默认预览优先级最高的启用调拨规则。如需指定规则，可在执行后重新设置 @preview_rule_id。

set @preview_rule_id := (
    select r.rule_id
    from inv_transfer_approval_rule r
    where r.document_type = 'transfer'
      and r.status = '0'
    order by r.priority, r.rule_id
    limit 1
);

set @manager_sort := (
    select min(p.post_sort)
    from sys_post p
    where p.post_code = 'dz'
      and p.status = '0'
);

drop temporary table if exists tmp_transfer_preview_stores;
create temporary table tmp_transfer_preview_stores (
    store_id bigint not null,
    store_name varchar(128) not null,
    ancestors varchar(2000),
    primary key (store_id)
) engine=InnoDB;

insert into tmp_transfer_preview_stores (store_id, store_name, ancestors)
select d.dept_id, d.dept_name, d.ancestors
from sys_dept d
where d.dept_type = 'STORE'
  and d.status = '0'
  and d.del_flag = '0';

drop temporary table if exists tmp_transfer_fixed_candidates;
create temporary table tmp_transfer_fixed_candidates (
    store_id bigint not null,
    user_id bigint not null,
    primary key (store_id, user_id)
) engine=InnoDB;

insert ignore into tmp_transfer_fixed_candidates (store_id, user_id)
select distinct store.store_id, u.user_id
from tmp_transfer_preview_stores store
join inv_transfer_approval_node node
  on node.rule_id = @preview_rule_id
 and node.node_order > 2
 and coalesce(trim(node.post_code), '') <> ''
join sys_post p on p.post_code = node.post_code collate utf8mb4_general_ci and p.status = '0'
join sys_user_post up on up.post_id = p.post_id
join sys_user u on u.user_id = up.user_id and u.status = '0' and u.del_flag = '0'
join sys_user_shop us on us.user_id = u.user_id
join sys_dept scope_dept on scope_dept.dept_id = us.dept_id
left join sys_user_profile profile on profile.user_id = u.user_id
where scope_dept.status = '0'
  and scope_dept.del_flag = '0'
  and scope_dept.dept_type in ('GROUP', 'COMPANY', 'STORE', 'WAREHOUSE')
  and (scope_dept.dept_id = store.store_id
       or find_in_set(scope_dept.dept_id, store.ancestors))
  and (profile.employee_status is null
       or trim(profile.employee_status) = ''
       or trim(profile.employee_status) <> '离职');

drop temporary table if exists tmp_transfer_level4_candidates;
create temporary table tmp_transfer_level4_candidates (
    store_id bigint not null,
    level4_source varchar(32) not null,
    user_id bigint not null,
    user_name varchar(128),
    post_sort int,
    primary key (store_id, user_id)
) engine=InnoDB;

insert ignore into tmp_transfer_level4_candidates (
    store_id, level4_source, user_id, user_name, post_sort
)
select distinct store.store_id,
       '店长' as level4_source,
       u.user_id,
       coalesce(nullif(u.nick_name, ''), u.user_name),
       p.post_sort
from tmp_transfer_preview_stores store
join sys_user u on u.status = '0' and u.del_flag = '0'
join sys_user_post up on up.user_id = u.user_id
join sys_post p on p.post_id = up.post_id and p.status = '0'
left join sys_user_profile profile on profile.user_id = u.user_id
where p.post_code = 'dz'
  and (u.dept_id = store.store_id
       or exists (
           select 1
           from sys_user_shop direct_scope
           where direct_scope.user_id = u.user_id
             and direct_scope.dept_id = store.store_id
       ))
  and (profile.employee_status is null
       or trim(profile.employee_status) = ''
       or trim(profile.employee_status) <> '离职');

drop temporary table if exists tmp_transfer_manager_stores;
create temporary table tmp_transfer_manager_stores (
    store_id bigint not null,
    primary key (store_id)
) engine=InnoDB;

insert ignore into tmp_transfer_manager_stores (store_id)
select distinct manager.store_id
from tmp_transfer_level4_candidates manager;

insert ignore into tmp_transfer_level4_candidates (
    store_id, level4_source, user_id, user_name, post_sort
)
select distinct store.store_id,
       '店长助理兜底' as level4_source,
       u.user_id,
       coalesce(nullif(u.nick_name, ''), u.user_name),
       p.post_sort
from tmp_transfer_preview_stores store
join sys_user u on u.status = '0' and u.del_flag = '0'
join sys_user_post up on up.user_id = u.user_id
join sys_post p on p.post_id = up.post_id and p.status = '0'
left join sys_user_profile profile on profile.user_id = u.user_id
where p.post_code = 'dzzy'
  and not exists (
      select 1
      from tmp_transfer_manager_stores manager
      where manager.store_id = store.store_id
  )
  and (u.dept_id = store.store_id
       or exists (
           select 1
           from sys_user_shop direct_scope
           where direct_scope.user_id = u.user_id
             and direct_scope.dept_id = store.store_id
       ))
  and (profile.employee_status is null
       or trim(profile.employee_status) = ''
       or trim(profile.employee_status) <> '离职');

drop temporary table if exists tmp_transfer_level3_eligible;
create temporary table tmp_transfer_level3_eligible (
    store_id bigint not null,
    user_id bigint not null,
    user_name varchar(128),
    post_sort int not null,
    key idx_tmp_transfer_level3_store_sort (store_id, post_sort),
    key idx_tmp_transfer_level3_store_user (store_id, user_id)
) engine=InnoDB;

insert into tmp_transfer_level3_eligible (store_id, user_id, user_name, post_sort)
select distinct store.store_id,
       u.user_id,
       coalesce(nullif(u.nick_name, ''), u.user_name),
       p.post_sort
from tmp_transfer_preview_stores store
join sys_user_shop us
join sys_dept scope_dept on scope_dept.dept_id = us.dept_id
join sys_user u on u.user_id = us.user_id and u.status = '0' and u.del_flag = '0'
join sys_user_post up on up.user_id = u.user_id
join sys_post p on p.post_id = up.post_id and p.status = '0'
left join sys_user_profile profile on profile.user_id = u.user_id
where (scope_dept.dept_id = store.store_id
       or find_in_set(scope_dept.dept_id, store.ancestors))
  and scope_dept.status = '0'
  and scope_dept.del_flag = '0'
  and scope_dept.dept_type in ('GROUP', 'COMPANY', 'STORE', 'WAREHOUSE')
  and p.post_sort < @manager_sort
  and p.post_code not in ('dz', 'dzzy', 'sijifzr', 'sanjifzr')
  and not exists (
      select 1
      from inv_transfer_approval_node fixed_node
      where fixed_node.rule_id = @preview_rule_id
        and fixed_node.node_order > 2
        and p.post_code = fixed_node.post_code collate utf8mb4_general_ci
  )
  and not exists (
      select 1
      from tmp_transfer_fixed_candidates fixed_user
      where fixed_user.store_id = store.store_id
        and fixed_user.user_id = u.user_id
  )
  and (profile.employee_status is null
       or trim(profile.employee_status) = ''
       or trim(profile.employee_status) <> '离职');

drop temporary table if exists tmp_transfer_level3_candidates;
create temporary table tmp_transfer_level3_candidates (
    store_id bigint not null,
    user_id bigint not null,
    user_name varchar(128),
    post_sort int not null,
    primary key (store_id, user_id)
) engine=InnoDB;

drop temporary table if exists tmp_transfer_level3_selected_sort;
create temporary table tmp_transfer_level3_selected_sort (
    store_id bigint not null,
    post_sort int not null,
    primary key (store_id)
) engine=InnoDB;

insert into tmp_transfer_level3_selected_sort (store_id, post_sort)
select eligible.store_id, max(eligible.post_sort) as post_sort
from tmp_transfer_level3_eligible eligible
group by eligible.store_id;

insert ignore into tmp_transfer_level3_candidates (store_id, user_id, user_name, post_sort)
select eligible.store_id, eligible.user_id, eligible.user_name, eligible.post_sort
from tmp_transfer_level3_eligible eligible
join tmp_transfer_level3_selected_sort selected_sort
  on selected_sort.store_id = eligible.store_id
 and selected_sort.post_sort = eligible.post_sort;

select store.store_id,
       store.store_name,
       level4.level4_source,
       level4.level4_user_ids,
       level4.level4_user_names,
       level3.level3_post_sort,
       level3.level3_user_ids,
       level3.level3_user_names,
       case
           when level4.store_id is null and level3.store_id is null
               then '未找到目标门店四级、三级负责人，本次审批直接进入已配置的固定审批'
           when level4.store_id is null
               then '未找到目标门店四级负责人，本次审批从三级负责人开始'
           when level3.store_id is null
               then '未找到目标门店三级负责人，本次审批跳过三级并进入已配置的固定审批'
           else ''
       end as missing_warning
from tmp_transfer_preview_stores store
left join (
    select candidate.store_id,
           group_concat(distinct candidate.level4_source order by candidate.level4_source separator ',') as level4_source,
           group_concat(distinct candidate.user_id order by candidate.user_id separator ',') as level4_user_ids,
           group_concat(distinct candidate.user_name order by candidate.user_id separator ',') as level4_user_names
    from tmp_transfer_level4_candidates candidate
    group by candidate.store_id
) level4 on level4.store_id = store.store_id
left join (
    select candidate.store_id,
           max(candidate.post_sort) as level3_post_sort,
           group_concat(distinct candidate.user_id order by candidate.user_id separator ',') as level3_user_ids,
           group_concat(distinct candidate.user_name order by candidate.user_id separator ',') as level3_user_names
    from tmp_transfer_level3_candidates candidate
    group by candidate.store_id
) level3 on level3.store_id = store.store_id
order by store.store_id;

drop temporary table if exists tmp_transfer_level3_candidates;
drop temporary table if exists tmp_transfer_level3_selected_sort;
drop temporary table if exists tmp_transfer_level3_eligible;
drop temporary table if exists tmp_transfer_level4_candidates;
drop temporary table if exists tmp_transfer_manager_stores;
drop temporary table if exists tmp_transfer_fixed_candidates;
drop temporary table if exists tmp_transfer_preview_stores;

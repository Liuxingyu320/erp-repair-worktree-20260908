-- 调拨审批：把每条现有规则的前两级转换为目标门店岗位链动态节点。
-- 少于两个节点的旧规则会保留原节点，并在前面补齐两个动态节点，避免丢失原审批人。
-- 兼容 MySQL 5.7；不使用窗口函数。

select n.rule_id,
       n.node_id,
       n.node_order,
       n.node_name,
       n.node_role,
       n.post_code,
       n.post_name
from inv_transfer_approval_node n
order by n.rule_id, n.node_order, n.node_id;

drop temporary table if exists tmp_transfer_approval_rule_node_count;
create temporary table tmp_transfer_approval_rule_node_count (
    rule_id bigint not null,
    node_count int not null,
    primary key (rule_id)
) engine=InnoDB;

insert into tmp_transfer_approval_rule_node_count (rule_id, node_count)
select r.rule_id,
       count(n.node_id) as node_count
from inv_transfer_approval_rule r
left join inv_transfer_approval_node n on n.rule_id = r.rule_id
group by r.rule_id;

drop temporary table if exists tmp_transfer_approval_node_rank;
create temporary table tmp_transfer_approval_node_rank (
    node_id bigint not null,
    rule_id bigint not null,
    sequence_no int not null,
    primary key (node_id),
    key idx_tmp_transfer_approval_rule_seq (rule_id, sequence_no)
) engine=InnoDB;

insert into tmp_transfer_approval_node_rank (node_id, rule_id, sequence_no)
select current_node.node_id,
       current_node.rule_id,
       (
           select count(*)
           from inv_transfer_approval_node previous_node
           where previous_node.rule_id = current_node.rule_id
             and (
                 previous_node.node_order < current_node.node_order
                 or (
                     previous_node.node_order = current_node.node_order
                     and previous_node.node_id <= current_node.node_id
                 )
             )
       ) as sequence_no
from inv_transfer_approval_node current_node;

update inv_transfer_approval_node n
join tmp_transfer_approval_node_rank ranked on ranked.node_id = n.node_id
join tmp_transfer_approval_rule_node_count counted on counted.rule_id = n.rule_id
set n.node_order = ranked.sequence_no,
    n.node_name = case
        when ranked.sequence_no = 1 then '四级负责人（店长/店助）'
        when ranked.sequence_no = 2 then '三级负责人（店长上一级）'
        else n.node_name
    end,
    n.node_role = case
        when ranked.sequence_no = 1 then 'level4_highest'
        when ranked.sequence_no = 2 then 'level3_highest'
        else n.node_role
    end,
    n.post_id = null,
    n.post_code = null,
    n.post_name = null,
    n.approval_mode = 'any_one',
    n.required_count = 1,
    n.update_by = 'system',
    n.update_time = now()
where counted.node_count >= 2
  and ranked.sequence_no in (1, 2);

-- 一节点规则不具备可安全推断的“四级、三级”旧结构，原节点整体后移并原样保留。
update inv_transfer_approval_node n
join tmp_transfer_approval_node_rank ranked on ranked.node_id = n.node_id
join tmp_transfer_approval_rule_node_count counted on counted.rule_id = n.rule_id
set n.node_order = ranked.sequence_no + 2,
    n.update_by = 'system',
    n.update_time = now()
where counted.node_count < 2;

-- 零节点和一节点规则都补齐两个系统动态节点；node_id 使用表的自增主键。
insert into inv_transfer_approval_node (
    rule_id, node_order, node_name, node_role,
    approval_mode, required_count, create_by, create_time, remark
)
select counted.rule_id,
       dynamic_node.node_order,
       dynamic_node.node_name,
       dynamic_node.node_role,
       'any_one',
       1,
       'system',
       now(),
       '系统自动匹配目标门店岗位审批链'
from tmp_transfer_approval_rule_node_count counted
cross join (
    select 1 as node_order,
           '四级负责人（店长/店助）' as node_name,
           'level4_highest' as node_role
    union all
    select 2 as node_order,
           '三级负责人（店长上一级）' as node_name,
           'level3_highest' as node_role
) dynamic_node
where counted.node_count < 2;

select n.rule_id,
       n.node_id,
       n.node_order,
       n.node_name,
       n.node_role,
       n.post_code,
       n.post_name
from inv_transfer_approval_node n
order by n.rule_id, n.node_order, n.node_id;

drop temporary table if exists tmp_transfer_approval_node_rank;
drop temporary table if exists tmp_transfer_approval_rule_node_count;

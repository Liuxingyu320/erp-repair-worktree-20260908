-- 已经用户确认的调拨临时岗位彻底清理脚本。
-- 仅适用于 2026-07-10 审计得到的 49/34/13/2 条任务和 30/4 张单据。
-- 任一数量变化都会回滚；禁止放入 Docker 自动初始化目录。

drop procedure if exists purge_transfer_temporary_posts;
delimiter //
create procedure purge_transfer_temporary_posts()
begin
    declare temporary_post_count int default 0;
    declare referenced_task_count int default 0;
    declare pending_task_count int default 0;
    declare approved_task_count int default 0;
    declare rejected_task_count int default 0;
    declare affected_instance_count int default 0;
    declare running_instance_count int default 0;
    declare submitted_transfer_count int default 0;
    declare cancelled_transfer_count int default 0;
    declare only_temporary_post_user_count int default 0;
    declare active_rule_reference_count int default 0;
    declare remaining_post_count int default 0;
    declare remaining_task_reference_count int default 0;
    declare remaining_user_post_count int default 0;
    declare remaining_pending_task_count int default 0;
    declare detached_history_task_count int default 0;
    declare reset_draft_transfer_count int default 0;
    declare preserved_cancelled_transfer_count int default 0;
    declare closed_instance_count int default 0;
    declare reset_status_log_count int default 0;

    declare exit handler for sqlexception
    begin
        rollback;
        resignal;
    end;

    start transaction;

    drop temporary table if exists tmp_transfer_temporary_posts;
    create temporary table tmp_transfer_temporary_posts (
        post_id bigint not null,
        post_code varchar(64) character set utf8mb4 collate utf8mb4_general_ci not null,
        primary key (post_id),
        unique key uk_tmp_transfer_temporary_post_code (post_code)
    ) engine=InnoDB;

    insert into tmp_transfer_temporary_posts (post_id, post_code)
    select post_id, post_code
    from sys_post
    where post_code in ('sijifzr', 'sanjifzr')
    for update;

    select count(*) into temporary_post_count
    from tmp_transfer_temporary_posts;

    if temporary_post_count <> 2 then
        signal sqlstate '45000'
            set message_text = '临时岗位数量不是2，停止清理';
    end if;

    drop temporary table if exists tmp_transfer_temporary_tasks;
    create temporary table tmp_transfer_temporary_tasks (
        task_id bigint not null,
        instance_id bigint not null,
        transfer_id bigint not null,
        task_status varchar(32) not null,
        original_post_code varchar(64) character set utf8mb4 collate utf8mb4_general_ci,
        primary key (task_id),
        key idx_tmp_transfer_temporary_task_instance (instance_id),
        key idx_tmp_transfer_temporary_task_transfer (transfer_id)
    ) engine=InnoDB;

    insert into tmp_transfer_temporary_tasks (
        task_id, instance_id, transfer_id, task_status, original_post_code
    )
    select distinct task.task_id,
           task.instance_id,
           task.transfer_id,
           task.status,
           temporary_post.post_code
    from inv_transfer_approval_task task
    join tmp_transfer_temporary_posts temporary_post
      on temporary_post.post_id = task.post_id
      or temporary_post.post_code = task.post_code collate utf8mb4_general_ci
    for update;

    select count(*) into referenced_task_count
    from tmp_transfer_temporary_tasks;
    select count(*) into pending_task_count
    from tmp_transfer_temporary_tasks
    where task_status = 'pending';
    select count(*) into approved_task_count
    from tmp_transfer_temporary_tasks
    where task_status = 'approved';
    select count(*) into rejected_task_count
    from tmp_transfer_temporary_tasks
    where task_status = 'rejected';

    if referenced_task_count <> 49 then
        signal sqlstate '45000'
            set message_text = '临时岗位任务数量已变化，停止清理';
    end if;
    if pending_task_count <> 34 then
        signal sqlstate '45000'
            set message_text = '待审批临时任务数量已变化，停止清理';
    end if;
    if approved_task_count <> 13 or rejected_task_count <> 2 then
        signal sqlstate '45000'
            set message_text = '已结束临时任务数量已变化，停止清理';
    end if;

    drop temporary table if exists tmp_transfer_temporary_instances;
    create temporary table tmp_transfer_temporary_instances (
        instance_id bigint not null,
        transfer_id bigint not null,
        instance_status varchar(32) not null,
        transfer_status varchar(20) not null,
        primary key (instance_id),
        unique key uk_tmp_transfer_temporary_transfer (transfer_id)
    ) engine=InnoDB;

    insert into tmp_transfer_temporary_instances (
        instance_id, transfer_id, instance_status, transfer_status
    )
    select distinct approval_instance.instance_id,
           transfer_order.transfer_id,
           approval_instance.status,
           transfer_order.status
    from tmp_transfer_temporary_tasks temporary_task
    join inv_transfer_approval_instance approval_instance
      on approval_instance.instance_id = temporary_task.instance_id
    join inv_transfer_order transfer_order
      on transfer_order.transfer_id = temporary_task.transfer_id
    where temporary_task.task_status = 'pending'
    for update;

    select count(*) into affected_instance_count
    from tmp_transfer_temporary_instances;
    select count(*) into running_instance_count
    from tmp_transfer_temporary_instances
    where instance_status = 'running';
    select count(*) into submitted_transfer_count
    from tmp_transfer_temporary_instances
    where transfer_status = 'submitted';
    select count(*) into cancelled_transfer_count
    from tmp_transfer_temporary_instances
    where transfer_status = 'cancelled';

    if affected_instance_count <> 34 or running_instance_count <> 34 then
        signal sqlstate '45000'
            set message_text = '运行中审批实例数量已变化，停止清理';
    end if;
    if submitted_transfer_count <> 30 then
        signal sqlstate '45000'
            set message_text = '审核中调拨单数量已变化，停止清理';
    end if;
    if cancelled_transfer_count <> 4 then
        signal sqlstate '45000'
            set message_text = '已取消调拨单数量已变化，停止清理';
    end if;

    select count(*) into active_rule_reference_count
    from inv_transfer_approval_node approval_node
    join tmp_transfer_temporary_posts temporary_post
      on temporary_post.post_id = approval_node.post_id
      or temporary_post.post_code = approval_node.post_code collate utf8mb4_general_ci;

    if active_rule_reference_count <> 0 then
        signal sqlstate '45000'
            set message_text = '当前审批规则仍引用临时岗位，停止清理';
    end if;

    select count(*) into only_temporary_post_user_count
    from sys_user_post temporary_user_post
    join tmp_transfer_temporary_posts temporary_post
      on temporary_post.post_id = temporary_user_post.post_id
    where not exists (
        select 1
        from sys_user_post real_user_post
        join sys_post real_post on real_post.post_id = real_user_post.post_id
        where real_user_post.user_id = temporary_user_post.user_id
          and real_post.post_code not in ('sijifzr', 'sanjifzr')
    );

    if only_temporary_post_user_count <> 0 then
        signal sqlstate '45000'
            set message_text = '存在仅配置临时岗位的用户，停止清理';
    end if;

    insert into inv_transfer_status_log (
        transfer_id, from_status, to_status, action,
        operator_id, operator_name, reason, create_time
    )
    select affected.transfer_id,
           'submitted',
           'draft',
           'temporary_post_purge_reset',
           null,
           'system',
           '清理临时三级/四级岗位审批任务，退回草稿后按新审批规则重新提交',
           now()
    from tmp_transfer_temporary_instances affected
    where affected.transfer_status = 'submitted';

    update inv_transfer_order transfer_order
    join tmp_transfer_temporary_instances affected
      on affected.transfer_id = transfer_order.transfer_id
     and affected.transfer_status = 'submitted'
    set transfer_order.status = 'draft',
        transfer_order.approval_instance_id = null,
        transfer_order.workflow_instance_id = null,
        transfer_order.submitted_time = null,
        transfer_order.approved_time = null,
        transfer_order.update_by = 'system',
        transfer_order.update_time = now();

    update inv_transfer_order transfer_order
    join tmp_transfer_temporary_instances affected
      on affected.transfer_id = transfer_order.transfer_id
     and affected.transfer_status = 'cancelled'
    set transfer_order.approval_instance_id = null,
        transfer_order.workflow_instance_id = null,
        transfer_order.update_by = 'system',
        transfer_order.update_time = now();

    update inv_transfer_approval_instance approval_instance
    join tmp_transfer_temporary_instances affected
      on affected.instance_id = approval_instance.instance_id
    set approval_instance.status = 'closed',
        approval_instance.update_by = 'system',
        approval_instance.update_time = now(),
        approval_instance.remark = left(concat_ws(
            '；', nullif(trim(approval_instance.remark), ''),
            '临时三级/四级岗位清理，旧审批实例关闭'
        ), 500);

    delete pending_task
    from inv_transfer_approval_task pending_task
    join tmp_transfer_temporary_tasks temporary_task
      on temporary_task.task_id = pending_task.task_id
     and temporary_task.task_status = 'pending';

    update inv_transfer_approval_task history_task
    join tmp_transfer_temporary_tasks temporary_task
      on temporary_task.task_id = history_task.task_id
     and temporary_task.task_status in ('approved', 'rejected')
    set history_task.remark = left(concat_ws(
            '；', nullif(trim(history_task.remark collate utf8mb4_general_ci), ''),
            concat('原临时岗位编码=', temporary_task.original_post_code,
                   '，岗位主数据已清理')
        ), 500),
        history_task.post_id = null,
        history_task.post_code = '',
        history_task.update_by = 'system',
        history_task.update_time = now();

    delete user_post
    from sys_user_post user_post
    join tmp_transfer_temporary_posts temporary_post
      on temporary_post.post_id = user_post.post_id;

    delete temporary_post
    from sys_post temporary_post
    join tmp_transfer_temporary_posts selected_post
      on selected_post.post_id = temporary_post.post_id;

    select count(*) into remaining_post_count
    from sys_post
    where post_code in ('sijifzr', 'sanjifzr');

    select count(*) into remaining_task_reference_count
    from inv_transfer_approval_task task
    join tmp_transfer_temporary_posts temporary_post
      on temporary_post.post_id = task.post_id
      or temporary_post.post_code = task.post_code collate utf8mb4_general_ci;

    select count(*) into remaining_user_post_count
    from sys_user_post user_post
    join tmp_transfer_temporary_posts temporary_post
      on temporary_post.post_id = user_post.post_id;

    select count(*) into remaining_pending_task_count
    from inv_transfer_approval_task task
    join tmp_transfer_temporary_tasks selected_task
      on selected_task.task_id = task.task_id
     and selected_task.task_status = 'pending';

    select count(*) into detached_history_task_count
    from inv_transfer_approval_task task
    join tmp_transfer_temporary_tasks selected_task
      on selected_task.task_id = task.task_id
     and selected_task.task_status in ('approved', 'rejected')
    where task.post_id is null
      and coalesce(task.post_code, '') = '';

    select count(*) into reset_draft_transfer_count
    from inv_transfer_order transfer_order
    join tmp_transfer_temporary_instances affected
      on affected.transfer_id = transfer_order.transfer_id
     and affected.transfer_status = 'submitted'
    where transfer_order.status = 'draft'
      and transfer_order.approval_instance_id is null
      and transfer_order.workflow_instance_id is null;

    select count(*) into preserved_cancelled_transfer_count
    from inv_transfer_order transfer_order
    join tmp_transfer_temporary_instances affected
      on affected.transfer_id = transfer_order.transfer_id
     and affected.transfer_status = 'cancelled'
    where transfer_order.status = 'cancelled'
      and transfer_order.approval_instance_id is null
      and transfer_order.workflow_instance_id is null;

    select count(*) into closed_instance_count
    from inv_transfer_approval_instance approval_instance
    join tmp_transfer_temporary_instances affected
      on affected.instance_id = approval_instance.instance_id
    where approval_instance.status = 'closed';

    select count(*) into reset_status_log_count
    from inv_transfer_status_log status_log
    join tmp_transfer_temporary_instances affected
      on affected.transfer_id = status_log.transfer_id
     and affected.transfer_status = 'submitted'
    where status_log.action = 'temporary_post_purge_reset';

    if remaining_post_count <> 0 then
        signal sqlstate '45000'
            set message_text = '清理后仍存在临时岗位，事务回滚';
    end if;
    if remaining_task_reference_count <> 0 then
        signal sqlstate '45000'
            set message_text = '清理后仍存在临时岗位任务引用，事务回滚';
    end if;
    if remaining_user_post_count <> 0 then
        signal sqlstate '45000'
            set message_text = '清理后仍存在临时岗位用户关联，事务回滚';
    end if;
    if remaining_pending_task_count <> 0 then
        signal sqlstate '45000'
            set message_text = '清理后仍存在待审批临时任务，事务回滚';
    end if;
    if detached_history_task_count <> 15 then
        signal sqlstate '45000'
            set message_text = '历史临时任务解除引用数量异常，事务回滚';
    end if;
    if reset_draft_transfer_count <> 30 then
        signal sqlstate '45000'
            set message_text = '审核中调拨单退回草稿数量异常，事务回滚';
    end if;
    if preserved_cancelled_transfer_count <> 4 then
        signal sqlstate '45000'
            set message_text = '已取消调拨单保持状态数量异常，事务回滚';
    end if;
    if closed_instance_count <> 34 then
        signal sqlstate '45000'
            set message_text = '旧审批实例关闭数量异常，事务回滚';
    end if;
    if reset_status_log_count <> 30 then
        signal sqlstate '45000'
            set message_text = '调拨单退回草稿日志数量异常，事务回滚';
    end if;

    commit;

    select referenced_task_count as original_task_count,
           pending_task_count as deleted_pending_task_count,
           approved_task_count + rejected_task_count as detached_history_task_count,
           submitted_transfer_count as reset_draft_transfer_count,
           cancelled_transfer_count as preserved_cancelled_transfer_count,
           affected_instance_count as closed_instance_count;
end//
delimiter ;

call purge_transfer_temporary_posts();
drop procedure purge_transfer_temporary_posts;

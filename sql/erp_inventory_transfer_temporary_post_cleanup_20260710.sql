-- 调拨临时三级/四级岗位安全清理。
-- 必须先完成动态规则迁移和真实调拨审批验证；兼容 MySQL 5.7。

select post_id, post_code, post_name, post_sort, status
from sys_post
where post_code in ('sijifzr', 'sanjifzr')
order by post_code;

select up.user_id, p.post_id, p.post_code, p.post_name
from sys_user_post up
join sys_post p on p.post_id = up.post_id
where p.post_code in ('sijifzr', 'sanjifzr')
order by up.user_id, p.post_code;

select n.rule_id, n.node_order, n.node_role, n.post_id, n.post_code, n.post_name
from inv_transfer_approval_node n
where n.post_code in ('sijifzr', 'sanjifzr')
   or n.post_id in (
       select p.post_id from sys_post p where p.post_code in ('sijifzr', 'sanjifzr')
   )
order by n.rule_id, n.node_order;

select t.task_id, t.instance_id, t.node_order, t.post_id, t.post_code, t.post_name, t.status
from inv_transfer_approval_task t
where t.post_code in ('sijifzr', 'sanjifzr')
   or t.post_id in (
       select p.post_id from sys_post p where p.post_code in ('sijifzr', 'sanjifzr')
   )
order by t.task_id;

drop procedure if exists cleanup_transfer_temporary_posts;
delimiter //
create procedure cleanup_transfer_temporary_posts()
begin
    declare exit handler for sqlexception
    begin
        rollback;
        resignal;
    end;

    if exists (
        select 1
        from inv_transfer_approval_node n
        join sys_post p
          on p.post_id = n.post_id
          or p.post_code = n.post_code collate utf8mb4_general_ci
        where p.post_code in ('sijifzr', 'sanjifzr')
    ) then
        signal sqlstate '45000'
            set message_text = '临时岗位仍被调拨审批规则引用，禁止删除';
    end if;

    if exists (
        select 1
        from inv_transfer_approval_task t
        join sys_post p
          on p.post_id = t.post_id
          or p.post_code = t.post_code collate utf8mb4_general_ci
        where p.post_code in ('sijifzr', 'sanjifzr')
    ) then
        signal sqlstate '45000'
            set message_text = '临时岗位仍被历史审批任务引用，禁止删除';
    end if;

    if exists (
        select 1
        from sys_user_post temporary_up
        join sys_post temporary_post on temporary_post.post_id = temporary_up.post_id
        where temporary_post.post_code in ('sijifzr', 'sanjifzr')
          and not exists (
              select 1
              from sys_user_post real_up
              join sys_post real_post on real_post.post_id = real_up.post_id
              where real_up.user_id = temporary_up.user_id
                and real_post.post_code not in ('sijifzr', 'sanjifzr')
          )
    ) then
        signal sqlstate '45000'
            set message_text = '存在仅配置临时岗位的用户，禁止删除';
    end if;

    start transaction;

    delete up
    from sys_user_post up
    join sys_post p on p.post_id = up.post_id
    where p.post_code in ('sijifzr', 'sanjifzr');

    delete p
    from sys_post p
    where p.post_code in ('sijifzr', 'sanjifzr');

    commit;
end//
delimiter ;

call cleanup_transfer_temporary_posts();
drop procedure cleanup_transfer_temporary_posts;

select post_id, post_code, post_name, post_sort, status
from sys_post
where post_code in ('sijifzr', 'sanjifzr')
order by post_code;

select up.user_id, p.post_id, p.post_code, p.post_name
from sys_user_post up
join sys_post p on p.post_id = up.post_id
where p.post_code in ('sijifzr', 'sanjifzr')
order by up.user_id, p.post_code;

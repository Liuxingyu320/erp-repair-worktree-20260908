-- 调拨审批：将启用规则规范为固定四级审批链。
-- 兼容 MySQL 5.7；执行前校验每个启用门店均有有效运营总监和总经理。

drop procedure if exists migrate_transfer_four_level_approval;
delimiter $$

create procedure migrate_transfer_four_level_approval()
begin
    declare v_enabled_rule_count int default 0;
    declare v_missing_store_count int default 0;
    declare v_invalid_rule_count int default 0;
    declare v_missing_store varchar(255) default null;
    declare v_message varchar(512) default null;

    declare exit handler for sqlexception
    begin
        rollback;
        resignal;
    end;

    start transaction;

    select count(*)
      into v_enabled_rule_count
      from inv_transfer_approval_rule r
     where r.status = '0'
       and r.document_type = 'transfer'
     for update;

    if v_enabled_rule_count > 0 then
        select count(*),
               min(concat(target_dept.dept_name, '（', target_dept.dept_id, '）'))
          into v_missing_store_count, v_missing_store
          from sys_dept target_dept
         where target_dept.del_flag = '0'
           and target_dept.status = '0'
           and target_dept.dept_type = 'STORE'
           and not exists (
               select 1
                 from sys_user u
                 join sys_user_post up on up.user_id = u.user_id
                 join sys_post p on p.post_id = up.post_id
                 join sys_user_shop us on us.user_id = u.user_id
                 join sys_dept scope_dept on scope_dept.dept_id = us.dept_id
                 left join sys_user_profile profile on profile.user_id = u.user_id
                where u.del_flag = '0'
                  and u.status = '0'
                  and p.status = '0'
                  and p.post_code = 'yyzj'
                  and scope_dept.del_flag = '0'
                  and scope_dept.status = '0'
                  and scope_dept.dept_type in ('GROUP', 'COMPANY', 'STORE', 'WAREHOUSE')
                  and (
                      target_dept.dept_id = scope_dept.dept_id
                      or find_in_set(scope_dept.dept_id, target_dept.ancestors)
                  )
                  and (
                      profile.employee_status is null
                      or trim(profile.employee_status) = ''
                      or trim(profile.employee_status) <> '离职'
                  )
                  and exists (
                      select 1
                        from sys_user_role ur
                        join sys_role role_info on role_info.role_id = ur.role_id
                        join sys_role_menu rm on rm.role_id = role_info.role_id
                        join sys_menu menu_info on menu_info.menu_id = rm.menu_id
                       where ur.user_id = u.user_id
                         and role_info.del_flag = '0'
                         and role_info.status = '0'
                         and menu_info.status = '0'
                         and menu_info.perms = 'inv:transfer:approve'
                  )
           );

        if v_missing_store_count > 0 then
            set v_message = concat('目标门店未配置有效运营总监，停止迁移：', v_missing_store);
            signal sqlstate '45000' set message_text = v_message;
        end if;

        select count(*),
               min(concat(target_dept.dept_name, '（', target_dept.dept_id, '）'))
          into v_missing_store_count, v_missing_store
          from sys_dept target_dept
         where target_dept.del_flag = '0'
           and target_dept.status = '0'
           and target_dept.dept_type = 'STORE'
           and not exists (
               select 1
                 from sys_user u
                 join sys_user_post up on up.user_id = u.user_id
                 join sys_post p on p.post_id = up.post_id
                 join sys_user_shop us on us.user_id = u.user_id
                 join sys_dept scope_dept on scope_dept.dept_id = us.dept_id
                 left join sys_user_profile profile on profile.user_id = u.user_id
                where u.del_flag = '0'
                  and u.status = '0'
                  and p.status = '0'
                  and p.post_code = 'zjl'
                  and scope_dept.del_flag = '0'
                  and scope_dept.status = '0'
                  and scope_dept.dept_type in ('GROUP', 'COMPANY', 'STORE', 'WAREHOUSE')
                  and (
                      target_dept.dept_id = scope_dept.dept_id
                      or find_in_set(scope_dept.dept_id, target_dept.ancestors)
                  )
                  and (
                      profile.employee_status is null
                      or trim(profile.employee_status) = ''
                      or trim(profile.employee_status) <> '离职'
                  )
                  and exists (
                      select 1
                        from sys_user_role ur
                        join sys_role role_info on role_info.role_id = ur.role_id
                        join sys_role_menu rm on rm.role_id = role_info.role_id
                        join sys_menu menu_info on menu_info.menu_id = rm.menu_id
                       where ur.user_id = u.user_id
                         and role_info.del_flag = '0'
                         and role_info.status = '0'
                         and menu_info.status = '0'
                         and menu_info.perms = 'inv:transfer:approve'
                  )
           );

        if v_missing_store_count > 0 then
            set v_message = concat('目标门店未配置有效总经理，停止迁移：', v_missing_store);
            signal sqlstate '45000' set message_text = v_message;
        end if;

        delete approval_node
          from inv_transfer_approval_node approval_node
          join inv_transfer_approval_rule approval_rule
            on approval_rule.rule_id = approval_node.rule_id
         where approval_rule.status = '0'
           and approval_rule.document_type = 'transfer';

        insert into inv_transfer_approval_node (
            rule_id,
            node_order,
            node_name,
            node_role,
            post_id,
            post_code,
            post_name,
            approval_mode,
            required_count,
            create_by,
            create_time,
            remark
        )
        select approval_rule.rule_id,
               system_node.node_order,
               system_node.node_name,
               system_node.node_role,
               null,
               system_node.post_code,
               system_node.post_name,
               'any_one',
               1,
               'system',
               now(),
               '系统固定调拨四级审批链'
          from inv_transfer_approval_rule approval_rule
          cross join (
              select 1 as node_order,
                     '四级负责人（店长/店助）' as node_name,
                     'level4_highest' as node_role,
                     cast(null as char(64)) as post_code,
                     cast(null as char(64)) as post_name
              union all
              select 2,
                     '三级负责人（店长与高层之间）',
                     'level3_highest',
                     null,
                     null
              union all
              select 3,
                     '运营总监',
                     'operations_director',
                     'yyzj',
                     '运营总监'
              union all
              select 4,
                     '总经理',
                     'general_manager',
                     'zjl',
                     '总经理'
          ) system_node
         where approval_rule.status = '0'
           and approval_rule.document_type = 'transfer';

        update inv_transfer_approval_rule
           set approval_mode = 'all_nodes',
               required_count = 0,
               allow_self_approve = '0',
               update_by = 'system',
               update_time = now()
         where status = '0'
           and document_type = 'transfer';

        select count(*)
          into v_invalid_rule_count
          from inv_transfer_approval_rule approval_rule
         where approval_rule.status = '0'
           and approval_rule.document_type = 'transfer'
           and (
               approval_rule.approval_mode <> 'all_nodes'
               or approval_rule.required_count <> 0
               or approval_rule.allow_self_approve <> '0'
               or (
                   select count(*)
                     from inv_transfer_approval_node approval_node
                    where approval_node.rule_id = approval_rule.rule_id
               ) <> 4
               or (
                   select count(*)
                     from inv_transfer_approval_node approval_node
                    where approval_node.rule_id = approval_rule.rule_id
                      and approval_node.approval_mode = 'any_one'
                      and approval_node.required_count = 1
                      and (
                          (approval_node.node_order = 1 and approval_node.node_role = 'level4_highest')
                          or (approval_node.node_order = 2 and approval_node.node_role = 'level3_highest')
                          or (approval_node.node_order = 3
                              and approval_node.node_role = 'operations_director'
                              and approval_node.post_code = 'yyzj')
                          or (approval_node.node_order = 4
                              and approval_node.node_role = 'general_manager'
                              and approval_node.post_code = 'zjl')
                      )
               ) <> 4
           );

        if v_invalid_rule_count > 0 then
            signal sqlstate '45000' set message_text = '四级审批节点迁移后校验失败';
        end if;
    end if;

    commit;
end$$

delimiter ;

call migrate_transfer_four_level_approval();
drop procedure if exists migrate_transfer_four_level_approval;

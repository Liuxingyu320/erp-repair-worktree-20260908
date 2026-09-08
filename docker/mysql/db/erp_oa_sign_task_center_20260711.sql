-- Single-HR signing task center, immutable task events, and notification outbox.
-- Existing signing packages remain readable and are not backfilled into tasks.

SET @erp_db = DATABASE();

CREATE TABLE IF NOT EXISTS oa_sign_task (
    task_id bigint NOT NULL AUTO_INCREMENT COMMENT '签约任务ID',
    task_no varchar(40) NOT NULL COMMENT '任务编号',
    scenario varchar(20) NOT NULL COMMENT '业务场景',
    employee_id bigint NOT NULL COMMENT '员工用户ID',
    shop_dept_id bigint DEFAULT NULL COMMENT '门店部门ID',
    legal_entity_id bigint DEFAULT NULL COMMENT '法律主体ID',
    assigned_hr_user_id bigint NOT NULL COMMENT '唯一处理HR用户ID',
    source_type varchar(40) NOT NULL COMMENT '来源业务类型',
    source_business_id varchar(64) NOT NULL COMMENT '来源业务ID',
    source_event_version varchar(64) NOT NULL COMMENT '来源事件版本',
    dedupe_key varchar(180) NOT NULL COMMENT '业务去重键',
    status varchar(32) NOT NULL COMMENT '任务状态',
    automation_level varchar(20) NOT NULL DEFAULT 'MANUAL' COMMENT '自动化级别',
    risk_level varchar(20) NOT NULL DEFAULT 'NORMAL' COMMENT '风险等级',
    plan_version_id bigint DEFAULT NULL COMMENT '签约方案版本ID',
    package_id bigint DEFAULT NULL COMMENT '签约包ID',
    confirmed_by bigint DEFAULT NULL COMMENT '确认HR用户ID',
    confirmed_time datetime DEFAULT NULL COMMENT '确认时间',
    confirmed_snapshot_hash varchar(64) DEFAULT NULL COMMENT '确认快照SHA-256',
    sign_deadline datetime DEFAULT NULL COMMENT '签署截止时间',
    failure_code varchar(64) DEFAULT NULL COMMENT '失败代码',
    failure_detail varchar(1000) DEFAULT NULL COMMENT '业务化失败详情',
    retry_count int NOT NULL DEFAULT 0 COMMENT '重试次数',
    next_retry_time datetime DEFAULT NULL COMMENT '下次重试时间',
    version bigint NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    sent_time datetime DEFAULT NULL COMMENT '发送时间',
    completed_time datetime DEFAULT NULL COMMENT '完成时间',
    cancelled_time datetime DEFAULT NULL COMMENT '取消时间',
    PRIMARY KEY (task_id),
    UNIQUE KEY uk_oa_sign_task_no (task_no),
    UNIQUE KEY uk_oa_sign_task_dedupe (dedupe_key),
    KEY idx_oa_sign_task_hr_status (assigned_hr_user_id, status, created_time),
    KEY idx_oa_sign_task_employee (employee_id, scenario, created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单HR签约编排任务';

CREATE TABLE IF NOT EXISTS oa_sign_task_event (
    event_id bigint NOT NULL AUTO_INCREMENT COMMENT '任务事件ID',
    task_id bigint NOT NULL COMMENT '签约任务ID',
    from_status varchar(32) DEFAULT NULL COMMENT '原状态',
    to_status varchar(32) NOT NULL COMMENT '目标状态',
    operator_type varchar(20) NOT NULL COMMENT '操作方类型',
    operator_user_id bigint DEFAULT NULL COMMENT '操作用户ID',
    reason_code varchar(64) DEFAULT NULL COMMENT '原因代码',
    reason_detail varchar(1000) DEFAULT NULL COMMENT '原因详情',
    request_id varchar(64) DEFAULT NULL COMMENT '幂等请求ID',
    ip_address varchar(64) DEFAULT NULL COMMENT 'IP地址',
    user_agent varchar(500) DEFAULT NULL COMMENT '用户代理',
    prev_event_hash varchar(64) DEFAULT NULL COMMENT '前一事件SHA-256',
    event_hash varchar(64) NOT NULL COMMENT '当前事件SHA-256',
    created_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (event_id),
    UNIQUE KEY uk_oa_sign_task_event_request (request_id),
    KEY idx_oa_sign_task_event_task (task_id, created_time, event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='签约任务不可变事件链';

CREATE TABLE IF NOT EXISTS oa_sign_notification_outbox (
    outbox_id bigint NOT NULL AUTO_INCREMENT COMMENT '通知发件箱ID',
    channel varchar(20) NOT NULL COMMENT '通知渠道',
    recipient_user_id bigint NOT NULL COMMENT '接收用户ID',
    business_key varchar(180) NOT NULL COMMENT '通知业务去重键',
    payload_json json NOT NULL COMMENT '非敏感通知载荷',
    status varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '投递状态',
    retry_count int NOT NULL DEFAULT 0 COMMENT '重试次数',
    next_retry_time datetime DEFAULT NULL COMMENT '下次重试时间',
    last_result varchar(1000) DEFAULT NULL COMMENT '最后投递结果',
    last_error varchar(1000) DEFAULT NULL COMMENT '最后错误',
    version bigint NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (outbox_id),
    UNIQUE KEY uk_oa_sign_notification_business (channel, recipient_user_id, business_key),
    KEY idx_oa_sign_notification_due (status, next_retry_time, outbox_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='签约通知可靠发件箱';

CREATE TABLE IF NOT EXISTS oa_sign_task_hr_reassignment (
    reassignment_id bigint NOT NULL AUTO_INCREMENT COMMENT '改派审计ID',
    task_id bigint NOT NULL COMMENT '签约任务ID',
    old_hr_user_id bigint NOT NULL COMMENT '改派前HR用户ID',
    new_hr_user_id bigint NOT NULL COMMENT '改派后HR用户ID',
    task_status varchar(32) NOT NULL COMMENT '改派时任务状态',
    reassigned_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '改派时间',
    PRIMARY KEY (reassignment_id),
    KEY idx_oa_sign_task_hr_reassignment_task (task_id, reassigned_time),
    KEY idx_oa_sign_task_hr_reassignment_hr (old_hr_user_id, new_hr_user_id, reassigned_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='唯一HR在途任务改派审计';

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package' AND COLUMN_NAME = 'task_id') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN task_id bigint DEFAULT NULL COMMENT ''签约任务ID'' AFTER version', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package' AND COLUMN_NAME = 'confirm_status') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN confirm_status varchar(32) DEFAULT NULL COMMENT ''单HR确认状态'' AFTER task_id', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package' AND COLUMN_NAME = 'plan_version_id') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN plan_version_id bigint DEFAULT NULL COMMENT ''方案版本ID'' AFTER confirm_status', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package' AND INDEX_NAME = 'uk_oa_sign_package_task') = 0,
    'ALTER TABLE oa_sign_package ADD UNIQUE KEY uk_oa_sign_package_task (task_id)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;


-- Single-HR task workspace. Business permissions are isolated in one feature-owned role.
INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
    (4600, '合同签约中心', 3000, 11, 'sign-task', 'oa/signTask/index', NULL,
     'OaSignTask', 1, 0, 'C', '0', '0', 'oa:signTask:list', 'form', 'system', NOW(), '单HR合同签约任务工作区')
ON DUPLICATE KEY UPDATE
    menu_name = VALUES(menu_name),
    parent_id = VALUES(parent_id),
    order_num = VALUES(order_num),
    path = VALUES(path),
    component = VALUES(component),
    route_name = VALUES(route_name),
    perms = VALUES(perms),
    icon = VALUES(icon),
    update_time = NOW();

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
    (4601, '签约任务查询', 4600, 1, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'oa:signTask:query', '#', 'system', NOW(), ''),
    (4602, '签约任务校验', 4600, 2, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'oa:signTask:revalidate', '#', 'system', NOW(), ''),
    (4603, '签约任务一次确认', 4600, 3, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'oa:signTask:confirm', '#', 'system', NOW(), ''),
    (4604, '签约任务发送', 4600, 4, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'oa:signTask:send', '#', 'system', NOW(), ''),
    (4605, '签约任务重试', 4600, 5, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'oa:signTask:retry', '#', 'system', NOW(), ''),
    (4606, '签约任务取消', 4600, 6, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'oa:signTask:cancel', '#', 'system', NOW(), ''),
    (4607, '签约技术证据', 4600, 7, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'oa:signTask:technicalEvidence', '#', 'system', NOW(), '仅审计或系统管理员按需授权'),
    (4608, '签约资料列表', 4600, 8, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'oa:signPackage:list', '#', 'system', NOW(), '合同签约中心补资料所需权限'),
    (4609, '签约资料查询', 4600, 9, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'oa:signPackage:query', '#', 'system', NOW(), '合同签约中心补资料所需权限'),
    (4610, '签约资料编辑', 4600, 10, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'oa:signPackage:add', '#', 'system', NOW(), '合同签约中心补资料所需权限')
ON DUPLICATE KEY UPDATE
    menu_name = VALUES(menu_name),
    parent_id = VALUES(parent_id),
    order_num = VALUES(order_num),
    perms = VALUES(perms),
    remark = VALUES(remark),
    update_time = NOW();

-- Track only grants owned by this feature so changing the unique HR can revoke the old grants safely.
CREATE TABLE IF NOT EXISTS sys_sign_hr_menu_grant (
    role_id bigint NOT NULL COMMENT 'HR现有角色ID',
    menu_id bigint NOT NULL COMMENT '本功能管理的菜单权限ID',
    hr_user_id bigint NOT NULL COMMENT '授权时配置的唯一HR用户ID',
    created_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '授权时间',
    PRIMARY KEY (role_id, menu_id),
    KEY idx_sys_sign_hr_menu_grant_user (hr_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单HR签约权限托管记录';

-- Keep the last valid HR while configuration is temporarily absent so a later restore can migrate in-flight tasks.
CREATE TABLE IF NOT EXISTS sys_sign_hr_state (
    state_id tinyint NOT NULL COMMENT '单例状态ID，固定为1',
    hr_user_id bigint DEFAULT NULL COMMENT '最近一次有效唯一HR用户ID',
    managed_role_id bigint DEFAULT NULL COMMENT '本功能专用唯一HR签约角色ID',
    updated_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (state_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='唯一HR最近有效配置状态';

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'sys_sign_hr_state' AND COLUMN_NAME = 'managed_role_id') = 0,
    'ALTER TABLE sys_sign_hr_state ADD COLUMN managed_role_id bigint DEFAULT NULL COMMENT ''本功能专用唯一HR签约角色ID'' AFTER hr_user_id', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'sys_sign_hr_state' AND COLUMN_NAME = 'hr_user_id') = 'NO',
    'ALTER TABLE sys_sign_hr_state MODIFY COLUMN hr_user_id bigint DEFAULT NULL COMMENT ''最近一次有效唯一HR用户ID''', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT IGNORE INTO sys_sign_hr_state (state_id, hr_user_id, managed_role_id)
VALUES (1, NULL, NULL);

-- This procedure is also called by system configuration writes when sign.hr.user-id changes.
-- Permissions are located by stable permission keys under the task-center menu, never by legacy 4520-series IDs.
DROP PROCEDURE IF EXISTS sync_sign_hr_permissions;
DELIMITER $$
CREATE PROCEDURE sync_sign_hr_permissions()
BEGIN
    DECLARE current_hr_user_id bigint DEFAULT NULL;
    DECLARE current_hr_config_value varchar(500) DEFAULT NULL;
    DECLARE previous_hr_user_id bigint DEFAULT NULL;
    DECLARE managed_sign_role_id bigint DEFAULT NULL;
    DECLARE existing_managed_role_id bigint DEFAULT NULL;

    SELECT hr_user_id, managed_role_id
      INTO previous_hr_user_id, managed_sign_role_id
      FROM sys_sign_hr_state
     WHERE state_id = 1
     FOR UPDATE;

    SELECT config.config_value
      INTO current_hr_config_value
      FROM sys_config config
     WHERE config.config_key = 'sign.hr.user-id'
     ORDER BY config.config_id DESC
     LIMIT 1
     FOR UPDATE;

    IF current_hr_config_value REGEXP '^[1-9][0-9]*$' THEN
        SELECT configured_hr.user_id
          INTO current_hr_user_id
          FROM sys_user configured_hr
         WHERE configured_hr.user_id = CAST(current_hr_config_value AS UNSIGNED)
           AND configured_hr.status = '0'
           AND configured_hr.del_flag = '0'
         LIMIT 1
         FOR UPDATE;
    END IF;

    -- Remove only legacy business-menu grants that this feature recorded on shared roles.
    -- Menu 4607 is separately administered technical evidence and must never be revoked here.
    DELETE role_menu
    FROM sys_role_menu role_menu
    JOIN sys_sign_hr_menu_grant managed
      ON managed.role_id = role_menu.role_id
     AND managed.menu_id = role_menu.menu_id
    WHERE managed.menu_id <> 4607;

    DELETE FROM sys_sign_hr_menu_grant;

    IF current_hr_user_id IS NOT NULL THEN
        IF managed_sign_role_id IS NOT NULL THEN
            SELECT role_id
              INTO existing_managed_role_id
              FROM sys_role
             WHERE role_id = managed_sign_role_id
             LIMIT 1
             FOR UPDATE;
            SET managed_sign_role_id = existing_managed_role_id;
        END IF;

        IF managed_sign_role_id IS NULL THEN
            SELECT role_id
              INTO managed_sign_role_id
              FROM sys_role
             WHERE role_key = 'sign_single_hr'
             ORDER BY CASE WHEN del_flag = '0' THEN 0 ELSE 1 END, role_id
             LIMIT 1
             FOR UPDATE;
        END IF;

        IF managed_sign_role_id IS NULL THEN
            INSERT INTO sys_role
                (role_name, role_key, role_sort, data_scope, menu_check_strictly,
                 dept_check_strictly, status, del_flag, create_by, create_time, remark)
            VALUES
                ('唯一HR签约', 'sign_single_hr', 90, '5', 1,
                 1, '0', '0', 'system', NOW(), '合同签约中心功能专用角色，仅关联当前配置HR');
            SET managed_sign_role_id = LAST_INSERT_ID();
        END IF;

        -- SELF is deliberately the narrowest built-in data scope; shop access still comes from the user.
        UPDATE sys_role
           SET role_name = '唯一HR签约',
               role_key = 'sign_single_hr',
               role_sort = 90,
               data_scope = '5',
               menu_check_strictly = 1,
               dept_check_strictly = 1,
               status = '0',
               del_flag = '0',
               update_by = 'system',
               update_time = NOW(),
               remark = '合同签约中心功能专用角色，仅关联当前配置HR'
         WHERE role_id = managed_sign_role_id;

        IF previous_hr_user_id IS NOT NULL AND previous_hr_user_id <> current_hr_user_id THEN
            INSERT INTO oa_sign_task_hr_reassignment
                (task_id, old_hr_user_id, new_hr_user_id, task_status, reassigned_time)
            SELECT task_id, assigned_hr_user_id, current_hr_user_id, status, NOW()
              FROM oa_sign_task
             WHERE assigned_hr_user_id <> current_hr_user_id
               AND status NOT IN ('SIGNED', 'REFUSED', 'EXPIRED', 'CANCELLED', 'NO_ACTION');

            UPDATE oa_sign_task
               SET assigned_hr_user_id = current_hr_user_id,
                   version = version + 1
             WHERE assigned_hr_user_id <> current_hr_user_id
               AND status NOT IN ('SIGNED', 'REFUSED', 'EXPIRED', 'CANCELLED', 'NO_ACTION');
        END IF;

        INSERT INTO sys_sign_hr_state (state_id, hr_user_id, managed_role_id, updated_time)
        VALUES (1, current_hr_user_id, managed_sign_role_id, NOW())
        ON DUPLICATE KEY UPDATE hr_user_id = current_hr_user_id,
            managed_role_id = managed_sign_role_id, updated_time = NOW();

        DELETE FROM sys_user_role
         WHERE role_id = managed_sign_role_id
           AND user_id <> current_hr_user_id;

        INSERT IGNORE INTO sys_user_role (user_id, role_id)
        VALUES (current_hr_user_id, managed_sign_role_id);

        DELETE FROM sys_role_menu
         WHERE role_id = managed_sign_role_id;

        INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
        SELECT managed_sign_role_id, task_menu.menu_id
        FROM sys_menu task_menu
     WHERE (task_menu.menu_id = 4600 OR task_menu.parent_id = 4600)
     AND task_menu.perms IN (
        'oa:signTask:list', 'oa:signTask:query', 'oa:signTask:revalidate',
        'oa:signTask:confirm', 'oa:signTask:send', 'oa:signTask:retry', 'oa:signTask:cancel',
        'oa:signPackage:list', 'oa:signPackage:query', 'oa:signPackage:add',
        'oa:signPackage:template'
     );
    ELSEIF managed_sign_role_id IS NOT NULL THEN
        DELETE FROM sys_user_role WHERE role_id = managed_sign_role_id;
    END IF;
END$$
DELIMITER ;

CALL sync_sign_hr_permissions();

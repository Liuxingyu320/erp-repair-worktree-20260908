-- Immutable published signing plan versions for Phase 3 scenario automation.

SET @erp_db = DATABASE();

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_plan') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_plan'
            AND COLUMN_NAME = 'legal_entity_id') = 0,
    'ALTER TABLE oa_sign_plan ADD COLUMN legal_entity_id bigint(20) DEFAULT NULL COMMENT ''稳定法律主体ID''',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Runtime permission synchronization must keep the plan-configuration permission too.
-- MySQL permits this wrapper to be created before the Phase 2 procedure/tables exist;
-- it is invoked only after the full migration chain has installed them.
DROP PROCEDURE IF EXISTS sync_sign_hr_permissions_with_plan;
DELIMITER $$
CREATE PROCEDURE sync_sign_hr_permissions_with_plan()
BEGIN
    CALL sync_sign_hr_permissions();

    INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
    SELECT state.managed_role_id, 4611
      FROM sys_sign_hr_state state
     WHERE state.state_id = 1
       AND state.managed_role_id IS NOT NULL
       AND state.hr_user_id IS NOT NULL;

    INSERT INTO sys_sign_hr_menu_grant
        (role_id, menu_id, hr_user_id, created_time)
    SELECT state.managed_role_id, role_menu.menu_id, state.hr_user_id, NOW()
      FROM sys_sign_hr_state state
      JOIN sys_role_menu role_menu
        ON role_menu.role_id = state.managed_role_id
       AND role_menu.menu_id = 4611
     WHERE state.state_id = 1
       AND state.managed_role_id IS NOT NULL
       AND state.hr_user_id IS NOT NULL
    ON DUPLICATE KEY UPDATE
        hr_user_id = VALUES(hr_user_id),
        created_time = VALUES(created_time);
END$$
DELIMITER ;


SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_plan') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_plan'
            AND COLUMN_NAME = 'legal_entity_name') = 0,
    'ALTER TABLE oa_sign_plan ADD COLUMN legal_entity_name varchar(160) DEFAULT NULL COMMENT ''法律主体名称''',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_plan') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_plan'
            AND COLUMN_NAME = 'rule_json') = 0,
    'ALTER TABLE oa_sign_plan ADD COLUMN rule_json longtext DEFAULT NULL COMMENT ''方案匹配规则JSON''',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_plan') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_plan'
            AND COLUMN_NAME = 'default_values_json') = 0,
    'ALTER TABLE oa_sign_plan ADD COLUMN default_values_json longtext DEFAULT NULL COMMENT ''方案默认值JSON''',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_plan') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_plan'
            AND COLUMN_NAME = 'sign_deadline_days') = 0,
    'ALTER TABLE oa_sign_plan ADD COLUMN sign_deadline_days int(11) DEFAULT NULL COMMENT ''签署期限天数''',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_plan') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_plan'
            AND COLUMN_NAME = 'reminder_policy_json') = 0,
    'ALTER TABLE oa_sign_plan ADD COLUMN reminder_policy_json longtext DEFAULT NULL COMMENT ''提醒策略JSON''',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_plan') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_plan'
            AND COLUMN_NAME = 'auto_send_condition_json') = 0,
    'ALTER TABLE oa_sign_plan ADD COLUMN auto_send_condition_json longtext DEFAULT NULL COMMENT ''自动发送条件JSON''',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_template') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_template'
            AND COLUMN_NAME = 'signature_position_json') = 0,
    'ALTER TABLE oa_sign_template ADD COLUMN signature_position_json longtext DEFAULT NULL COMMENT ''员工签名定位策略JSON''',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_template') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_template'
            AND COLUMN_NAME = 'company_seal_position_json') = 0,
    'ALTER TABLE oa_sign_template ADD COLUMN company_seal_position_json longtext DEFAULT NULL COMMENT ''企业章定位策略JSON''',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_template') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_template'
            AND COLUMN_NAME = 'match_condition_json') = 0,
    'ALTER TABLE oa_sign_template ADD COLUMN match_condition_json longtext DEFAULT NULL COMMENT ''模板匹配条件JSON''',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_task') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_task'
            AND COLUMN_NAME = 'plan_version_id') = 0,
    'ALTER TABLE oa_sign_task ADD COLUMN plan_version_id bigint(20) DEFAULT NULL COMMENT ''签约方案版本ID''',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package'
            AND COLUMN_NAME = 'plan_version_id') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN plan_version_id bigint(20) DEFAULT NULL COMMENT ''签约方案版本ID''',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS oa_sign_plan_version (
    version_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '签约方案版本ID',
    plan_id bigint(20) NOT NULL COMMENT '来源签约方案ID',
    plan_name varchar(120) NOT NULL COMMENT '方案名称快照',
    version_no int(11) NOT NULL COMMENT '方案内版本号',
    scenario varchar(32) NOT NULL COMMENT '签约场景快照',
    shop_dept_id bigint(20) NOT NULL COMMENT '门店ID快照',
    legal_entity_id bigint(20) NOT NULL COMMENT '稳定法律主体ID快照',
    legal_entity_name varchar(160) NOT NULL COMMENT '法律主体名称快照',
    rule_json longtext NOT NULL COMMENT '规范化规则JSON快照',
    default_values_json longtext NOT NULL COMMENT '规范化默认值JSON快照',
    sign_deadline_days int(11) NOT NULL COMMENT '签署期限天数快照',
    reminder_policy_json longtext NOT NULL COMMENT '提醒策略JSON快照',
    auto_send_condition_json longtext NOT NULL COMMENT '自动发送条件JSON快照',
    publish_status varchar(20) NOT NULL COMMENT '发布状态',
    matching_status varchar(20) NOT NULL DEFAULT 'ENABLED' COMMENT '新任务匹配状态',
    published_by_user_id bigint(20) NOT NULL COMMENT '发布人用户ID',
    published_by varchar(64) NOT NULL COMMENT '发布人账号',
    published_time datetime NOT NULL COMMENT '发布时间',
    version_hash char(64) NOT NULL COMMENT '规范化版本SHA-256',
    create_time datetime NOT NULL COMMENT '创建时间',
    PRIMARY KEY (version_id),
    UNIQUE KEY uk_oa_sign_plan_version_no (plan_id, version_no),
    UNIQUE KEY uk_oa_sign_plan_version_hash (plan_id, version_hash),
    KEY idx_oa_sign_plan_version_match
        (scenario, shop_dept_id, legal_entity_id, publish_status, matching_status),
    CONSTRAINT fk_oa_sign_plan_version_plan
        FOREIGN KEY (plan_id) REFERENCES oa_sign_plan (plan_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB AUTO_INCREMENT=1000000000 DEFAULT CHARSET=utf8mb4 COMMENT='不可变签约方案发布版本';

CREATE TABLE IF NOT EXISTS oa_sign_plan_version_template (
    id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
    plan_version_id bigint(20) NOT NULL COMMENT '签约方案版本ID',
    template_id bigint(20) NOT NULL COMMENT '来源模板ID',
    template_version varchar(32) DEFAULT NULL COMMENT '模板版本快照',
    template_type varchar(64) NOT NULL COMMENT '模板类型快照',
    template_name varchar(120) DEFAULT NULL COMMENT '模板名称快照',
    source_file_url varchar(500) NOT NULL COMMENT '源模板文件地址快照',
    source_file_hash varchar(128) NOT NULL COMMENT '源模板文件SHA-256快照',
    required_placeholders varchar(1000) DEFAULT NULL COMMENT '必需占位符快照',
    sort_order int(11) NOT NULL COMMENT '模板排序快照',
    employee_sign_required char(1) NOT NULL COMMENT '是否要求员工签署',
    signature_position_json longtext DEFAULT NULL COMMENT '员工签名定位策略JSON快照',
    company_seal_position_json longtext DEFAULT NULL COMMENT '企业章定位策略JSON快照',
    match_condition_json longtext NOT NULL COMMENT '模板匹配条件JSON快照',
    create_time datetime NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oa_sign_plan_version_template_type (plan_version_id, template_type),
    KEY idx_oa_sign_plan_version_template_order (plan_version_id, sort_order, id),
    CONSTRAINT fk_oa_sign_plan_version_template_version
        FOREIGN KEY (plan_version_id) REFERENCES oa_sign_plan_version (version_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不可变签约方案模板快照';

-- Phase 2 allowed temporary compatibility IDs before the version table existed. Keep them
-- untouched and allocate all new immutable version IDs strictly above both legacy maxima.
SET @legacy_task_plan_version_max = 0;
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_task') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_task'
            AND COLUMN_NAME = 'plan_version_id') > 0,
    'SELECT COALESCE(MAX(plan_version_id), 0) INTO @legacy_task_plan_version_max FROM oa_sign_task',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @legacy_package_plan_version_max = 0;
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package'
            AND COLUMN_NAME = 'plan_version_id') > 0,
    'SELECT COALESCE(MAX(plan_version_id), 0) INTO @legacy_package_plan_version_max FROM oa_sign_package',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @new_plan_version_auto_increment = GREATEST(1000000000,
    @legacy_task_plan_version_max + 1, @legacy_package_plan_version_max + 1);
SET @sql = CONCAT('ALTER TABLE oa_sign_plan_version AUTO_INCREMENT = ',
    @new_plan_version_auto_increment);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_task') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_task'
            AND COLUMN_NAME = 'plan_version_id') > 0
    AND (SELECT COUNT(*) FROM information_schema.STATISTICS
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_task'
            AND INDEX_NAME = 'idx_oa_sign_task_plan_version') = 0,
    'ALTER TABLE oa_sign_task ADD INDEX idx_oa_sign_task_plan_version (plan_version_id)',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package'
            AND COLUMN_NAME = 'plan_version_id') > 0
    AND (SELECT COUNT(*) FROM information_schema.STATISTICS
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package'
            AND INDEX_NAME = 'idx_oa_sign_package_plan_version') = 0,
    'ALTER TABLE oa_sign_package ADD INDEX idx_oa_sign_package_plan_version (plan_version_id)',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- Keep the one-HR model: add configuration as one more business permission on the managed role.
INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
    (4611, '签约方案配置', 4600, 11, '', NULL, NULL, '', 1, 0, 'F', '0', '0',
     'oa:signPackage:template', '#', 'system', NOW(), '唯一HR维护并发布签约方案版本')
ON DUPLICATE KEY UPDATE
    menu_name = VALUES(menu_name),
    parent_id = VALUES(parent_id),
    order_num = VALUES(order_num),
    perms = VALUES(perms),
    remark = VALUES(remark),
    update_time = NOW();

-- Incremental deployment: grant the new business permission only when the Phase 2
-- single-HR state tables already exist. On a clean bootstrap these statements are no-ops;
-- the later task-center migration creates the managed role with the updated permission list.
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'sys_sign_hr_state') > 0
    AND (SELECT COUNT(*) FROM information_schema.TABLES
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'sys_role_menu') > 0
    AND (SELECT COUNT(*) FROM information_schema.TABLES
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'sys_sign_hr_menu_grant') > 0,
    'INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
       SELECT managed_role_id, 4611
         FROM sys_sign_hr_state
        WHERE state_id = 1
          AND managed_role_id IS NOT NULL
          AND hr_user_id IS NOT NULL',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'sys_sign_hr_state') > 0
    AND (SELECT COUNT(*) FROM information_schema.TABLES
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'sys_role_menu') > 0
    AND (SELECT COUNT(*) FROM information_schema.TABLES
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'sys_sign_hr_menu_grant') > 0,
    'INSERT INTO sys_sign_hr_menu_grant
        (role_id, menu_id, hr_user_id, created_time)
       SELECT managed_role_id, 4611, hr_user_id, NOW()
         FROM sys_sign_hr_state
        WHERE state_id = 1
          AND managed_role_id IS NOT NULL
          AND hr_user_id IS NOT NULL
       ON DUPLICATE KEY UPDATE
          hr_user_id = VALUES(hr_user_id),
          created_time = VALUES(created_time)',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

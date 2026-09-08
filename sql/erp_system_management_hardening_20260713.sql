-- 系统管理加固：R1 权限边界 + R2 显式凭据状态 + R3 审计治理
-- + R4 管理流程优化 + R5 配置防错、薪资/调拨规则版本和独立高危权限。
-- MySQL 5.7 compatible. Safe to re-run.

-- R2：首次执行时为存量账号建立显式状态。重复执行不会重置已进入强制改密流程的账号。
SET @must_change_password_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_user'
      AND column_name = 'must_change_password'
);

SET @must_change_password_ddl := IF(
    @must_change_password_exists = 0,
    'ALTER TABLE sys_user ADD COLUMN must_change_password char(1) NOT NULL DEFAULT ''0'' COMMENT ''是否必须修改密码（0否 1是）'' AFTER pwd_update_date',
    'SELECT ''sys_user.must_change_password already exists'''
);
PREPARE must_change_password_stmt FROM @must_change_password_ddl;
EXECUTE must_change_password_stmt;
DEALLOCATE PREPARE must_change_password_stmt;

-- 只修复空值或非法值；合法的 1 必须保留。
UPDATE sys_user
SET must_change_password = '0'
WHERE must_change_password IS NULL
   OR must_change_password NOT IN ('0', '1');

-- R3：先建立可核验的归档批次账本。自动清理保持关闭，只有外部归档完成且复核后
-- 才允许未来的留存任务按批次执行；本次版本不提供全量清空能力。
CREATE TABLE IF NOT EXISTS sys_audit_archive_batch (
    batch_id bigint NOT NULL AUTO_INCREMENT COMMENT '归档批次ID',
    log_type varchar(32) NOT NULL COMMENT '日志类型：operlog/logininfor',
    cutoff_time datetime NOT NULL COMMENT '归档截止时间（Asia/Shanghai）',
    record_count bigint NOT NULL DEFAULT 0 COMMENT '归档记录数',
    archive_location varchar(512) NOT NULL COMMENT '归档位置（不得包含访问凭据）',
    archive_sha256 char(64) DEFAULT NULL COMMENT '归档文件SHA-256',
    status varchar(20) NOT NULL DEFAULT 'PREPARED' COMMENT 'PREPARED/VERIFIED/FAILED',
    requested_by varchar(64) NOT NULL COMMENT '发起人',
    requested_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发起时间',
    verified_by varchar(64) DEFAULT NULL COMMENT '复核人',
    verified_time datetime DEFAULT NULL COMMENT '复核时间',
    remark varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (batch_id),
    KEY idx_audit_archive_type_cutoff (log_type, cutoff_time),
    KEY idx_audit_archive_status_time (status, requested_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审计日志归档批次账本';

INSERT INTO sys_config
    (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT
    '审计日志自动留存任务开关', 'sys.audit.retention.enabled', 'false', 'Y',
    'system_hardening_20260713', NOW(), '默认关闭；启用前必须接入可靠归档存储、完整性校验和双人复核'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_config WHERE config_key = 'sys.audit.retention.enabled'
);

START TRANSACTION;

SET @role_menu_id := (
    SELECT menu_id
    FROM sys_menu
    WHERE perms = 'system:role:list'
      AND menu_type = 'C'
    ORDER BY menu_id
    LIMIT 1
);

SET @role_data_scope_menu_id := (
    SELECT menu_id
    FROM sys_menu
    WHERE perms = 'system:role:dataScope'
    ORDER BY menu_id
    LIMIT 1
);

SET @role_data_scope_menu_id := COALESCE(
    @role_data_scope_menu_id,
    (SELECT COALESCE(MAX(menu_id), 0) + 1 FROM sys_menu)
);

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT
    @role_data_scope_menu_id, '角色数据范围', @role_menu_id, 7, '', NULL, NULL, '',
    1, 0, 'F', '0', '0', 'system:role:dataScope', '#',
    'system_hardening_20260713', NOW(), '独立控制角色组织数据范围修改'
WHERE @role_menu_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu WHERE perms = 'system:role:dataScope'
  );

-- 最小授权：只给超级管理员角色。其他角色需经过业务负责人逐个确认后再授权。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
JOIN sys_menu m ON m.perms = 'system:role:dataScope'
WHERE r.role_key = 'admin'
  AND r.del_flag = '0';

SET @operlog_parent_menu_id := (
    SELECT menu_id
    FROM sys_menu
    WHERE perms = 'system:operlog:list'
      AND menu_type = 'C'
    ORDER BY menu_id
    LIMIT 1
);

SET @operlog_detail_menu_id := (
    SELECT menu_id
    FROM sys_menu
    WHERE perms = 'system:operlog:detail'
    ORDER BY menu_id
    LIMIT 1
);

SET @operlog_detail_menu_id := COALESCE(
    @operlog_detail_menu_id,
    (SELECT COALESCE(MAX(menu_id), 0) + 1 FROM sys_menu)
);

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT
    @operlog_detail_menu_id, '操作日志详情', @operlog_parent_menu_id, 4, '', NULL, NULL, '',
    1, 0, 'F', '0', '0', 'system:operlog:detail', '#',
    'system_hardening_20260713', NOW(), '独立控制请求参数、响应结果和异常正文查看'
WHERE @operlog_parent_menu_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu WHERE perms = 'system:operlog:detail'
  );

-- 日志正文默认只授权超级管理员；普通日志管理员仍只能查看摘要和导出摘要。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
JOIN sys_menu m ON m.perms = 'system:operlog:detail'
WHERE r.role_key = 'admin'
  AND r.del_flag = '0';

-- 发布前收口：把授权、缓存刷新从通用编辑/删除权限中拆出。
-- 新权限不自动授予普通角色；超级管理员由应用内置全权限语义覆盖。
SET @user_parent_menu_id := (
    SELECT menu_id FROM sys_menu
    WHERE perms = 'system:user:list' AND menu_type = 'C'
    ORDER BY menu_id LIMIT 1
);
SET @user_auth_role_menu_id := (
    SELECT menu_id FROM sys_menu
    WHERE perms = 'system:user:authRole'
    ORDER BY menu_id LIMIT 1
);
SET @user_auth_role_menu_id := COALESCE(
    @user_auth_role_menu_id,
    (SELECT COALESCE(MAX(menu_id), 0) + 1 FROM sys_menu)
);
INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT
    @user_auth_role_menu_id, '用户分配角色', @user_parent_menu_id, 8, '', NULL, NULL, '',
    1, 0, 'F', '0', '0', 'system:user:authRole', '#',
    'system_hardening_20260713', NOW(), '独立控制查看和修改用户角色授权'
WHERE @user_parent_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:user:authRole');

SET @role_auth_user_menu_id := (
    SELECT menu_id FROM sys_menu
    WHERE perms = 'system:role:authUser'
    ORDER BY menu_id LIMIT 1
);
SET @role_auth_user_menu_id := COALESCE(
    @role_auth_user_menu_id,
    (SELECT COALESCE(MAX(menu_id), 0) + 1 FROM sys_menu)
);
INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT
    @role_auth_user_menu_id, '角色分配用户', @role_menu_id, 8, '', NULL, NULL, '',
    1, 0, 'F', '0', '0', 'system:role:authUser', '#',
    'system_hardening_20260713', NOW(), '独立控制查看和修改角色用户授权'
WHERE @role_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:role:authUser');

SET @config_parent_menu_id := (
    SELECT menu_id FROM sys_menu
    WHERE perms = 'system:config:list' AND menu_type = 'C'
    ORDER BY menu_id LIMIT 1
);
SET @config_refresh_menu_id := (
    SELECT menu_id FROM sys_menu
    WHERE perms = 'system:config:refresh'
    ORDER BY menu_id LIMIT 1
);
SET @config_refresh_menu_id := COALESCE(
    @config_refresh_menu_id,
    (SELECT COALESCE(MAX(menu_id), 0) + 1 FROM sys_menu)
);
INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT
    @config_refresh_menu_id, '参数缓存刷新', @config_parent_menu_id, 6, '', NULL, NULL, '',
    1, 0, 'F', '0', '0', 'system:config:refresh', '#',
    'system_hardening_20260713', NOW(), '独立控制参数缓存刷新'
WHERE @config_parent_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:config:refresh');

SET @dict_parent_menu_id := (
    SELECT menu_id FROM sys_menu
    WHERE perms = 'system:dict:list' AND menu_type = 'C'
    ORDER BY menu_id LIMIT 1
);
SET @dict_refresh_menu_id := (
    SELECT menu_id FROM sys_menu
    WHERE perms = 'system:dict:refresh'
    ORDER BY menu_id LIMIT 1
);
SET @dict_refresh_menu_id := COALESCE(
    @dict_refresh_menu_id,
    (SELECT COALESCE(MAX(menu_id), 0) + 1 FROM sys_menu)
);
INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT
    @dict_refresh_menu_id, '字典缓存刷新', @dict_parent_menu_id, 6, '', NULL, NULL, '',
    1, 0, 'F', '0', '0', 'system:dict:refresh', '#',
    'system_hardening_20260713', NOW(), '独立控制字典缓存刷新'
WHERE @dict_parent_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:dict:refresh');

-- 人事续签与转正接口已有独立权限校验，但历史菜单目录缺少对应授权项，
-- 导致非 1 号超级管理员无法通过角色配置获得权限。仅补目录，不默认授予任何普通角色。
SET @hr_employee_parent_menu_id := (
    SELECT menu_id FROM sys_menu
    WHERE perms = 'hr:employee:list' AND menu_type = 'C'
    ORDER BY menu_id LIMIT 1
);
SET @hr_employee_renewal_menu_id := (
    SELECT menu_id FROM sys_menu
    WHERE perms = 'hr:employee:renewal'
    ORDER BY menu_id LIMIT 1
);
SET @hr_employee_renewal_menu_id := COALESCE(
    @hr_employee_renewal_menu_id,
    (SELECT COALESCE(MAX(menu_id), 0) + 1 FROM sys_menu)
);
INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT
    @hr_employee_renewal_menu_id, '员工续签确认', @hr_employee_parent_menu_id, 9, '', NULL, NULL, '',
    1, 0, 'F', '0', '0', 'hr:employee:renewal', '#',
    'system_hardening_20260713', NOW(), '独立控制员工合同续签确认；默认不授权'
WHERE @hr_employee_parent_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'hr:employee:renewal');

SET @hr_employee_regularize_menu_id := (
    SELECT menu_id FROM sys_menu
    WHERE perms = 'hr:employee:regularize'
    ORDER BY menu_id LIMIT 1
);
SET @hr_employee_regularize_menu_id := COALESCE(
    @hr_employee_regularize_menu_id,
    (SELECT COALESCE(MAX(menu_id), 0) + 1 FROM sys_menu)
);
INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT
    @hr_employee_regularize_menu_id, '员工转正确认', @hr_employee_parent_menu_id, 10, '', NULL, NULL, '',
    1, 0, 'F', '0', '0', 'hr:employee:regularize', '#',
    'system_hardening_20260713', NOW(), '独立控制员工转正确认；默认不授权'
WHERE @hr_employee_parent_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'hr:employee:regularize');

COMMIT;

SELECT
    m.menu_id,
    m.parent_id,
    m.perms,
    COUNT(rm.role_id) AS granted_role_count
FROM sys_menu m
LEFT JOIN sys_role_menu rm ON rm.menu_id = m.menu_id
WHERE m.perms IN (
    'system:role:dataScope',
    'system:operlog:detail',
    'system:user:authRole',
    'system:role:authUser',
    'system:config:refresh',
    'system:dict:refresh',
    'hr:employee:renewal',
    'hr:employee:regularize'
)
GROUP BY m.menu_id, m.parent_id, m.perms;

-- ---------------------------------------------------------------------------
-- R5：参数配置元数据。所有 DDL 都先查询 information_schema，可安全重复执行。
-- ---------------------------------------------------------------------------
SET @config_group_code_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_config' AND column_name = 'group_code'
);
SET @config_group_code_ddl := IF(
    @config_group_code_exists = 0,
    'ALTER TABLE sys_config ADD COLUMN group_code varchar(32) NOT NULL DEFAULT ''custom'' COMMENT ''配置分组'' AFTER config_type',
    'SELECT ''sys_config.group_code already exists'''
);
PREPARE config_group_code_stmt FROM @config_group_code_ddl;
EXECUTE config_group_code_stmt;
DEALLOCATE PREPARE config_group_code_stmt;

SET @config_value_type_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_config' AND column_name = 'value_type'
);
SET @config_value_type_ddl := IF(
    @config_value_type_exists = 0,
    'ALTER TABLE sys_config ADD COLUMN value_type varchar(20) NOT NULL DEFAULT ''string'' COMMENT ''值类型'' AFTER group_code',
    'SELECT ''sys_config.value_type already exists'''
);
PREPARE config_value_type_stmt FROM @config_value_type_ddl;
EXECUTE config_value_type_stmt;
DEALLOCATE PREPARE config_value_type_stmt;

SET @config_sensitive_flag_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_config' AND column_name = 'sensitive_flag'
);
SET @config_sensitive_flag_ddl := IF(
    @config_sensitive_flag_exists = 0,
    'ALTER TABLE sys_config ADD COLUMN sensitive_flag char(1) NOT NULL DEFAULT ''N'' COMMENT ''是否敏感（Y是 N否）'' AFTER value_type',
    'SELECT ''sys_config.sensitive_flag already exists'''
);
PREPARE config_sensitive_flag_stmt FROM @config_sensitive_flag_ddl;
EXECUTE config_sensitive_flag_stmt;
DEALLOCATE PREPARE config_sensitive_flag_stmt;

SET @config_validation_rule_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_config' AND column_name = 'validation_rule'
);
SET @config_validation_rule_ddl := IF(
    @config_validation_rule_exists = 0,
    'ALTER TABLE sys_config ADD COLUMN validation_rule varchar(500) DEFAULT NULL COMMENT ''服务端校验规则'' AFTER sensitive_flag',
    'SELECT ''sys_config.validation_rule already exists'''
);
PREPARE config_validation_rule_stmt FROM @config_validation_rule_ddl;
EXECUTE config_validation_rule_stmt;
DEALLOCATE PREPARE config_validation_rule_stmt;

SET @config_display_order_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_config' AND column_name = 'display_order'
);
SET @config_display_order_ddl := IF(
    @config_display_order_exists = 0,
    'ALTER TABLE sys_config ADD COLUMN display_order int NOT NULL DEFAULT 100 COMMENT ''分组内显示顺序'' AFTER validation_rule',
    'SELECT ''sys_config.display_order already exists'''
);
PREPARE config_display_order_stmt FROM @config_display_order_ddl;
EXECUTE config_display_order_stmt;
DEALLOCATE PREPARE config_display_order_stmt;

SET @config_version_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_config' AND column_name = 'version'
);
SET @config_version_ddl := IF(
    @config_version_exists = 0,
    'ALTER TABLE sys_config ADD COLUMN version int NOT NULL DEFAULT 1 COMMENT ''乐观锁版本'' AFTER display_order',
    'SELECT ''sys_config.version already exists'''
);
PREPARE config_version_stmt FROM @config_version_ddl;
EXECUTE config_version_stmt;
DEALLOCATE PREPARE config_version_stmt;

UPDATE sys_config
SET version = 1
WHERE version IS NULL OR version < 1;

-- 注册表内置键回填可信元数据；未注册的系统参数按敏感、不可公开、密码型处理。
UPDATE sys_config
SET group_code = CASE
        WHEN config_key IN ('audit.retention.days', 'sys.audit.retention.enabled') THEN 'audit'
        WHEN config_key IN ('jwt.expiration', 'jwt.secret', 'security.inner.sign.key', 'sys.login.blackIPList') THEN 'security'
        WHEN config_key IN (
            'sys.user.initPassword', 'sys.account.chrtype', 'sys.account.initPasswordModify',
            'sys.account.passwordValidateDays', 'sys.account.registerUser',
            'sys.user.password.expireDays', 'sys.user.password.maxRetryCount',
            'sys.user.password.minLength'
        ) THEN 'account'
        WHEN config_key IN ('sys.index.sideTheme', 'sys.index.skinName') THEN 'appearance'
        WHEN config_key IN ('file.presigned.url.ttl', 'file.upload.maxSize', 'file.upload.whitelist') THEN 'file'
        WHEN config_key = 'pda.scan.enabled' THEN 'inventory'
        WHEN config_key IN (
            'todo.approval.urgent.hours', 'todo.contract.warning.days',
            'todo.contract.urgent.days', 'todo.summary.recent.limit'
        ) THEN 'todo'
        WHEN config_key IN (
            'sign.hr.user-id', 'sign.renewal.decision-days',
            'hr.onboarding.post_entry_due_days', 'hr.onboarding.import_retention_days',
            'hr.employee.no.prefix'
        ) THEN 'hr'
        WHEN config_type = 'Y' THEN 'security'
        ELSE COALESCE(NULLIF(group_code, ''), 'custom')
    END,
    value_type = CASE
        WHEN config_key IN (
            'audit.retention.days', 'jwt.expiration', 'sys.account.passwordValidateDays',
            'sys.user.password.expireDays', 'sys.user.password.maxRetryCount',
            'sys.user.password.minLength', 'file.presigned.url.ttl', 'file.upload.maxSize',
            'todo.approval.urgent.hours', 'todo.contract.warning.days',
            'todo.contract.urgent.days', 'todo.summary.recent.limit',
            'sign.hr.user-id', 'sign.renewal.decision-days',
            'hr.onboarding.post_entry_due_days', 'hr.onboarding.import_retention_days'
        ) THEN 'integer'
        WHEN config_key IN (
            'sys.audit.retention.enabled', 'sys.account.registerUser', 'pda.scan.enabled'
        ) THEN 'boolean'
        WHEN config_key IN (
            'sys.account.chrtype', 'sys.account.initPasswordModify',
            'sys.index.sideTheme', 'sys.index.skinName'
        ) THEN 'enum'
        WHEN config_key IN (
            'jwt.secret', 'security.inner.sign.key', 'sys.login.blackIPList',
            'sys.user.initPassword'
        ) THEN 'password'
        WHEN config_key IN ('file.upload.whitelist', 'hr.employee.no.prefix') THEN 'string'
        WHEN config_type = 'Y' THEN 'password'
        ELSE COALESCE(NULLIF(value_type, ''), 'string')
    END,
    sensitive_flag = CASE
        WHEN config_key IN (
            'jwt.secret', 'security.inner.sign.key', 'sys.login.blackIPList',
            'sys.user.initPassword'
        ) THEN 'Y'
        WHEN config_key IN (
            'audit.retention.days', 'sys.audit.retention.enabled', 'jwt.expiration',
            'sys.account.chrtype', 'sys.account.initPasswordModify',
            'sys.account.passwordValidateDays', 'sys.account.registerUser',
            'sys.user.password.expireDays', 'sys.user.password.maxRetryCount',
            'sys.user.password.minLength', 'sys.index.sideTheme', 'sys.index.skinName',
            'file.presigned.url.ttl', 'file.upload.maxSize', 'file.upload.whitelist',
            'pda.scan.enabled', 'todo.approval.urgent.hours', 'todo.contract.warning.days',
            'todo.contract.urgent.days', 'todo.summary.recent.limit',
            'sign.hr.user-id', 'sign.renewal.decision-days',
            'hr.onboarding.post_entry_due_days', 'hr.onboarding.import_retention_days',
            'hr.employee.no.prefix'
        ) THEN 'N'
        WHEN config_type = 'Y' THEN 'Y'
        ELSE IF(sensitive_flag = 'Y', 'Y', 'N')
    END,
    validation_rule = CASE
        WHEN config_key = 'audit.retention.days' THEN 'min=1;max=3650'
        WHEN config_key = 'jwt.expiration' THEN 'min=5;max=10080'
        WHEN config_key IN ('jwt.secret', 'security.inner.sign.key') THEN 'minLength=32;maxLength=500'
        WHEN config_key = 'sys.login.blackIPList' THEN 'maxLength=500'
        WHEN config_key = 'sys.user.initPassword' THEN 'minLength=8;maxLength=128'
        WHEN config_key = 'sys.account.chrtype' THEN 'enum=0,1,2,3,4'
        WHEN config_key = 'sys.account.initPasswordModify' THEN 'enum=0,1'
        WHEN config_key IN (
            'sys.audit.retention.enabled', 'sys.account.registerUser', 'pda.scan.enabled'
        ) THEN NULL
        WHEN config_key = 'sys.account.passwordValidateDays' THEN 'min=0;max=365'
        WHEN config_key = 'sys.user.password.expireDays' THEN 'min=0;max=3650'
        WHEN config_key = 'sys.user.password.maxRetryCount' THEN 'min=1;max=20'
        WHEN config_key = 'sys.user.password.minLength' THEN 'min=8;max=128'
        WHEN config_key = 'sys.index.sideTheme' THEN 'enum=theme-dark,theme-light'
        WHEN config_key = 'sys.index.skinName' THEN 'enum=skin-blue,skin-green,skin-purple,skin-red,skin-yellow'
        WHEN config_key = 'file.presigned.url.ttl' THEN 'min=30;max=86400'
        WHEN config_key = 'file.upload.maxSize' THEN 'min=1;max=2048'
        WHEN config_key = 'file.upload.whitelist' THEN 'maxLength=500'
        WHEN config_key = 'todo.approval.urgent.hours' THEN 'min=1;max=720'
        WHEN config_key IN ('todo.contract.warning.days', 'todo.contract.urgent.days') THEN 'min=0;max=3650'
        WHEN config_key = 'todo.summary.recent.limit' THEN 'min=1;max=500'
        WHEN config_key = 'sign.hr.user-id' THEN 'min=1'
        WHEN config_key = 'sign.renewal.decision-days' THEN 'min=1;max=365'
        WHEN config_key = 'hr.onboarding.post_entry_due_days' THEN 'min=0;max=365'
        WHEN config_key = 'hr.onboarding.import_retention_days' THEN 'min=1;max=3650'
        WHEN config_key = 'hr.employee.no.prefix' THEN 'minLength=1;maxLength=16'
        WHEN config_type = 'Y' THEN 'maxLength=500'
        ELSE validation_rule
    END,
    display_order = CASE config_key
        WHEN 'audit.retention.days' THEN 10 WHEN 'sys.audit.retention.enabled' THEN 20
        WHEN 'jwt.expiration' THEN 10 WHEN 'jwt.secret' THEN 20
        WHEN 'security.inner.sign.key' THEN 30 WHEN 'sys.login.blackIPList' THEN 40
        WHEN 'sys.user.initPassword' THEN 10 WHEN 'sys.account.chrtype' THEN 20
        WHEN 'sys.account.initPasswordModify' THEN 30 WHEN 'sys.account.passwordValidateDays' THEN 40
        WHEN 'sys.account.registerUser' THEN 50 WHEN 'sys.user.password.expireDays' THEN 60
        WHEN 'sys.user.password.maxRetryCount' THEN 70 WHEN 'sys.user.password.minLength' THEN 80
        WHEN 'sys.index.sideTheme' THEN 10 WHEN 'sys.index.skinName' THEN 20
        WHEN 'file.presigned.url.ttl' THEN 10 WHEN 'file.upload.maxSize' THEN 20
        WHEN 'file.upload.whitelist' THEN 30 WHEN 'pda.scan.enabled' THEN 10
        WHEN 'todo.approval.urgent.hours' THEN 10 WHEN 'todo.contract.warning.days' THEN 20
        WHEN 'todo.contract.urgent.days' THEN 30 WHEN 'todo.summary.recent.limit' THEN 40
        WHEN 'sign.hr.user-id' THEN 10 WHEN 'sign.renewal.decision-days' THEN 20
        WHEN 'hr.onboarding.post_entry_due_days' THEN 30
        WHEN 'hr.onboarding.import_retention_days' THEN 40
        WHEN 'hr.employee.no.prefix' THEN 50
        ELSE COALESCE(display_order, 100)
    END;

SET @config_group_index_exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'sys_config'
      AND index_name = 'idx_sys_config_group_order'
);
SET @config_group_index_ddl := IF(
    @config_group_index_exists = 0,
    'ALTER TABLE sys_config ADD INDEX idx_sys_config_group_order (group_code, display_order, config_id)',
    'SELECT ''idx_sys_config_group_order already exists'''
);
PREPARE config_group_index_stmt FROM @config_group_index_ddl;
EXECUTE config_group_index_stmt;
DEALLOCATE PREPARE config_group_index_stmt;

-- ---------------------------------------------------------------------------
-- R5：调拨审批规则乐观锁，阻止两个管理员静默覆盖或误删新版本。
-- ---------------------------------------------------------------------------
SET @transfer_rule_version_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'inv_transfer_approval_rule'
      AND column_name = 'version'
);
SET @transfer_rule_version_ddl := IF(
    @transfer_rule_version_exists = 0,
    'ALTER TABLE inv_transfer_approval_rule ADD COLUMN version int NOT NULL DEFAULT 1 COMMENT ''乐观锁版本'' AFTER status',
    'SELECT ''inv_transfer_approval_rule.version already exists'''
);
PREPARE transfer_rule_version_stmt FROM @transfer_rule_version_ddl;
EXECUTE transfer_rule_version_stmt;
DEALLOCATE PREPARE transfer_rule_version_stmt;

UPDATE inv_transfer_approval_rule
SET version = 1
WHERE version IS NULL OR version < 1;

-- ---------------------------------------------------------------------------
-- R5：薪资方案乐观锁与只追加修订。基线快照仅含方案和档位业务字段，
-- 不含用户、角色绑定或个人工资结果。
-- ---------------------------------------------------------------------------
SET @salary_version_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_salary_scheme' AND column_name = 'version'
);
SET @salary_version_ddl := IF(
    @salary_version_exists = 0,
    'ALTER TABLE sys_salary_scheme ADD COLUMN version int NOT NULL DEFAULT 1 COMMENT ''业务版本'' AFTER status',
    'SELECT ''sys_salary_scheme.version already exists'''
);
PREPARE salary_version_stmt FROM @salary_version_ddl;
EXECUTE salary_version_stmt;
DEALLOCATE PREPARE salary_version_stmt;

UPDATE sys_salary_scheme
SET version = 1
WHERE version IS NULL OR version < 1;

CREATE TABLE IF NOT EXISTS sys_salary_scheme_revision (
    revision_id bigint NOT NULL AUTO_INCREMENT COMMENT '修订ID',
    scheme_id bigint NOT NULL COMMENT '方案ID；方案删除后仍保留',
    version int NOT NULL COMMENT '业务版本',
    change_type varchar(40) NOT NULL COMMENT 'BASELINE/CREATE/UPDATE/ROLLBACK/EMERGENCY_*',
    change_reason varchar(500) NOT NULL COMMENT '变更原因',
    snapshot_json longtext NOT NULL COMMENT '方案与档位纯业务JSON快照',
    create_by varchar(64) NOT NULL DEFAULT '' COMMENT '操作人',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间（Asia/Shanghai）',
    PRIMARY KEY (revision_id),
    UNIQUE KEY uk_salary_revision_scheme_version (scheme_id, version),
    KEY idx_salary_revision_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='薪资方案只追加修订';

SET @old_group_concat_max_len := @@SESSION.group_concat_max_len;
SET SESSION group_concat_max_len = 1048576;

INSERT INTO sys_salary_scheme_revision
    (scheme_id, version, change_type, change_reason, snapshot_json, create_by, create_time)
SELECT
    s.scheme_id,
    1,
    'BASELINE',
    '系统管理加固迁移生成的存量基线',
    CONCAT(
        '{',
        '"schemeName":', JSON_QUOTE(COALESCE(s.scheme_name, '')), ',',
        '"socialType":', JSON_QUOTE(COALESCE(s.social_type, '')), ',',
        '"effectiveDate":', JSON_QUOTE(COALESCE(s.effective_date, '')), ',',
        '"status":', JSON_QUOTE(COALESCE(s.status, '')), ',',
        '"remark":', IF(s.remark IS NULL, 'null', JSON_QUOTE(s.remark)), ',',
        '"items":[',
        COALESCE((
            SELECT GROUP_CONCAT(
                JSON_OBJECT(
                    'postName', i.post_name,
                    'gradeName', i.grade_name,
                    'regionName', i.region_name,
                    'baseSalary', i.base_salary,
                    'managementAllowance', i.management_allowance,
                    'overtimePay', i.overtime_pay,
                    'rewardAllowance', i.reward_allowance,
                    'fullAttendanceBonus', i.full_attendance_bonus,
                    'socialSubsidy', i.social_subsidy,
                    'commuteSubsidy', i.commute_subsidy,
                    'itemSort', i.item_sort,
                    'remark', i.remark
                )
                ORDER BY i.item_sort, i.item_id SEPARATOR ','
            )
            FROM sys_salary_scheme_item i
            WHERE i.scheme_id = s.scheme_id
        ), ''),
        ']}'
    ),
    'system_hardening_20260713',
    NOW()
FROM sys_salary_scheme s
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_salary_scheme_revision r
    WHERE r.scheme_id = s.scheme_id
      AND r.version = 1
);

SET SESSION group_concat_max_len = @old_group_concat_max_len;

-- 紧急修正独立授权，默认只给超级管理员。
START TRANSACTION;

SET @salary_parent_menu_id := (
    SELECT menu_id
    FROM sys_menu
    WHERE perms = 'system:salary:list'
      AND menu_type = 'C'
    ORDER BY menu_id
    LIMIT 1
);

SET @salary_emergency_menu_id := (
    SELECT menu_id
    FROM sys_menu
    WHERE perms = 'system:salary:emergency'
    ORDER BY menu_id
    LIMIT 1
);

SET @salary_emergency_menu_id := COALESCE(
    @salary_emergency_menu_id,
    (SELECT COALESCE(MAX(menu_id), 0) + 1 FROM sys_menu)
);

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT
    @salary_emergency_menu_id, '薪资紧急修正', @salary_parent_menu_id, 9, '', NULL, NULL, '',
    1, 0, 'F', '0', '0', 'system:salary:emergency', '#',
    'system_hardening_20260713', NOW(), '允许修正已生效薪资方案；必须填写原因并记录修订'
WHERE @salary_parent_menu_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu WHERE perms = 'system:salary:emergency'
  );

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
JOIN sys_menu m ON m.perms = 'system:salary:emergency'
WHERE r.role_key = 'admin'
  AND r.del_flag = '0';

COMMIT;

-- 上线前核对：基线条数必须与现存方案数一致，快照必须全部是合法 JSON。
SELECT
    (SELECT COUNT(*) FROM sys_salary_scheme) AS salary_scheme_count,
    (SELECT COUNT(*)
     FROM sys_salary_scheme_revision r
     JOIN sys_salary_scheme s ON s.scheme_id = r.scheme_id
     WHERE r.version = 1) AS salary_baseline_revision_count,
    (SELECT COUNT(*)
     FROM sys_salary_scheme_revision
     WHERE JSON_VALID(snapshot_json) = 0) AS invalid_salary_snapshot_count;

SELECT
    m.menu_id,
    m.parent_id,
    m.perms,
    COUNT(rm.role_id) AS granted_role_count
FROM sys_menu m
LEFT JOIN sys_role_menu rm ON rm.menu_id = m.menu_id
WHERE m.perms IN (
    'system:role:dataScope',
    'system:operlog:detail',
    'system:salary:emergency',
    'system:user:authRole',
    'system:role:authUser',
    'system:config:refresh',
    'system:dict:refresh',
    'hr:employee:renewal',
    'hr:employee:regularize'
)
GROUP BY m.menu_id, m.parent_id, m.perms;

SELECT
    COUNT(*) AS invalid_config_metadata_count
FROM sys_config
WHERE version < 1
   OR sensitive_flag NOT IN ('Y', 'N')
   OR group_code IS NULL OR group_code = ''
   OR value_type IS NULL OR value_type = '';

SELECT
    COUNT(*) AS invalid_transfer_rule_version_count
FROM inv_transfer_approval_rule
WHERE version IS NULL OR version < 1;

-- 回滚边界：
-- 1. 新增权限可先从 sys_role_menu 撤销，再删除 system:salary:emergency 菜单。
-- 2. 配置/薪资/调拨规则新增列保持向后兼容，默认不在紧急回滚中 DROP，避免丢失并发版本信息。
-- 3. sys_salary_scheme_revision 是审计记录，只追加且不随应用回滚删除。

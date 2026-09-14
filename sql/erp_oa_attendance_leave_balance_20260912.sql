-- B08-A: explicit HR policy only; no national entitlement values or role grants.
-- Deploy order: migrate -> configure HR rules/permissions -> enable
-- oa.attendance-v2.leave.balance.accrual-enabled. Default is false in Java.
CREATE TABLE IF NOT EXISTS oa_attendance_leave_balance_rule (
 rule_id bigint NOT NULL AUTO_INCREMENT, family_id bigint NULL, version int NOT NULL,
 owner_dept_id bigint NOT NULL, legal_entity_id bigint NOT NULL, leave_type_id bigint NOT NULL,
 location_code varchar(64) NOT NULL, name varchar(128) NOT NULL, priority int NOT NULL,
 status varchar(16) NOT NULL, effective_from date NOT NULL, effective_to date NOT NULL,
 config_json text NOT NULL, row_version bigint NOT NULL DEFAULT 0,
 created_by bigint NOT NULL, created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 published_by bigint NULL, published_at datetime NULL,
 PRIMARY KEY(rule_id), UNIQUE KEY uk_leave_balance_rule_version(family_id,version),
 KEY ix_leave_balance_rule_match(legal_entity_id,leave_type_id,location_code,status,effective_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
CREATE TABLE IF NOT EXISTS oa_attendance_leave_balance_tier (
 rule_id bigint NOT NULL, min_years int NOT NULL, max_years_exclusive int NULL,
 amount decimal(20,6) NOT NULL, PRIMARY KEY(rule_id,min_years)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
CREATE TABLE IF NOT EXISTS oa_attendance_leave_work_location (
 mapping_id bigint NOT NULL AUTO_INCREMENT, owner_dept_id bigint NOT NULL,
 legal_entity_id bigint NOT NULL, work_location varchar(160) NOT NULL,
 location_code varchar(64) NOT NULL, row_version bigint NOT NULL DEFAULT 0,
 reason varchar(500) NOT NULL, updated_by bigint NOT NULL, updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(mapping_id), UNIQUE KEY uk_leave_work_location(owner_dept_id,legal_entity_id,work_location)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
CREATE TABLE IF NOT EXISTS oa_attendance_leave_balance_account (
 account_id bigint NOT NULL AUTO_INCREMENT, user_id bigint NOT NULL, leave_type_id bigint NOT NULL,
 row_version bigint NOT NULL DEFAULT 0, PRIMARY KEY(account_id), UNIQUE KEY uk_leave_balance_account(user_id,leave_type_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
CREATE TABLE IF NOT EXISTS oa_attendance_leave_balance_bucket (
 bucket_id bigint NOT NULL AUTO_INCREMENT, account_id bigint NOT NULL, user_id bigint NOT NULL, leave_type_id bigint NOT NULL,
 source_key varchar(160) NOT NULL, source_type varchar(24) NOT NULL, period_year int NOT NULL,
 rule_id bigint NOT NULL, mapping_id bigint NOT NULL, mapping_version bigint NOT NULL,
 owner_dept_id bigint NOT NULL, legal_entity_id bigint NOT NULL, context_fingerprint char(64) NOT NULL,
 expires_on date NOT NULL, expiry_state varchar(32) NOT NULL DEFAULT 'OPEN',
 granted_units bigint NOT NULL DEFAULT 0, rule_granted_units bigint NOT NULL DEFAULT 0, reserved_units bigint NOT NULL DEFAULT 0, consumed_units bigint NOT NULL DEFAULT 0,
 expired_units bigint NOT NULL DEFAULT 0, carried_units bigint NOT NULL DEFAULT 0,
 display_unit varchar(16) NOT NULL, minutes_per_day decimal(20,6) NULL,
 row_version bigint NOT NULL DEFAULT 0, created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(bucket_id), UNIQUE KEY uk_leave_balance_bucket(account_id,source_key), KEY ix_leave_balance_expiry(account_id,expires_on)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
CREATE TABLE IF NOT EXISTS oa_attendance_leave_balance_ledger (
 ledger_id bigint NOT NULL AUTO_INCREMENT, account_id bigint NOT NULL, bucket_id bigint NOT NULL,
 event_key varchar(192) NOT NULL, action varchar(32) NOT NULL, units bigint NOT NULL,
 rule_id bigint NOT NULL, source_key varchar(160) NOT NULL, context_fingerprint char(64) NOT NULL,
 context_json text NOT NULL, operator_user_id bigint NULL, reason varchar(500) NOT NULL, created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(ledger_id), UNIQUE KEY uk_leave_balance_event(account_id,event_key), KEY ix_leave_balance_audit(account_id,ledger_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
CREATE TABLE IF NOT EXISTS oa_attendance_leave_balance_command (
 command_id bigint NOT NULL AUTO_INCREMENT, account_id bigint NOT NULL, command_key varchar(192) NOT NULL,
 fingerprint char(64) NOT NULL, bucket_id bigint NOT NULL, result_units bigint NOT NULL,
 created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(command_id), UNIQUE KEY uk_leave_balance_command(account_id,command_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

SET @leave_balance_parent := (SELECT menu_id FROM sys_menu WHERE perms='oa:attendance:center:list' ORDER BY menu_id LIMIT 1);
INSERT INTO sys_menu (menu_name,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
SELECT '本人假期额度',@leave_balance_parent,80,'',NULL,NULL,'',1,0,'F','0','0','oa:attendance:leave:balance:self','#','system',NOW(),'B08-A 权限由管理员明确配置，本迁移不授予任何角色'
WHERE @leave_balance_parent IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='oa:attendance:leave:balance:self');
INSERT INTO sys_menu (menu_name,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
SELECT '所辖假期额度',@leave_balance_parent,80,'',NULL,NULL,'',1,0,'F','0','0','oa:attendance:leave:balance:read','#','system',NOW(),'B08-A 权限由管理员明确配置，本迁移不授予任何角色'
WHERE @leave_balance_parent IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='oa:attendance:leave:balance:read');
INSERT INTO sys_menu (menu_name,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
SELECT '维护假期额度规则',@leave_balance_parent,80,'',NULL,NULL,'',1,0,'F','0','0','oa:attendance:leave:balance:rule','#','system',NOW(),'B08-A 权限由管理员明确配置，本迁移不授予任何角色'
WHERE @leave_balance_parent IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='oa:attendance:leave:balance:rule');
INSERT INTO sys_menu (menu_name,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
SELECT '审计调整假期额度',@leave_balance_parent,80,'',NULL,NULL,'',1,0,'F','0','0','oa:attendance:leave:balance:adjust','#','system',NOW(),'B08-A 权限由管理员明确配置，本迁移不授予任何角色'
WHERE @leave_balance_parent IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='oa:attendance:leave:balance:adjust');

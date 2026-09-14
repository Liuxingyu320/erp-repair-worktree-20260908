-- B08-C: after existing attendance/leave, A balance and B overtime migrations.
-- Repeatable; no role grants and no rewrite of existing requests/policies.
SET @b08c_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='oa_attendance_leave_type' AND column_name='minutes_per_day'), 'SELECT 1', 'ALTER TABLE oa_attendance_leave_type ADD COLUMN minutes_per_day decimal(12,6) NULL COMMENT ''HR显式每日分钟换算，未配置不猜测''');
PREPARE b08c_stmt FROM @b08c_ddl;
EXECUTE b08c_stmt;
DEALLOCATE PREPARE b08c_stmt;
SET @b08c_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='oa_attendance_leave_request' AND column_name='requested_days'), 'SELECT 1', 'ALTER TABLE oa_attendance_leave_request ADD COLUMN requested_days decimal(12,6) NULL COMMENT ''独立申请天数；历史为空''');
PREPARE b08c_stmt FROM @b08c_ddl;
EXECUTE b08c_stmt;
DEALLOCATE PREPARE b08c_stmt;
SET @b08c_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='oa_attendance_leave_request' AND column_name='quota_policy_json'), 'SELECT 1', 'ALTER TABLE oa_attendance_leave_request ADD COLUMN quota_policy_json longtext NULL COMMENT ''保存时冻结的政策与单位依据''');
PREPARE b08c_stmt FROM @b08c_ddl;
EXECUTE b08c_stmt;
DEALLOCATE PREPARE b08c_stmt;
SET @b08c_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='oa_attendance_leave_request' AND column_name='quota_units'), 'SELECT 1', 'ALTER TABLE oa_attendance_leave_request ADD COLUMN quota_units bigint NULL COMMENT ''精确微分钟，仅需额度的申请''');
PREPARE b08c_stmt FROM @b08c_ddl;
EXECUTE b08c_stmt;
DEALLOCATE PREPARE b08c_stmt;
SET @b08c_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='oa_attendance_leave_request' AND column_name='quota_status'), 'SELECT 1', 'ALTER TABLE oa_attendance_leave_request ADD COLUMN quota_status varchar(32) NULL COMMENT ''额度状态；空为历史无新额度记录''');
PREPARE b08c_stmt FROM @b08c_ddl;
EXECUTE b08c_stmt;
DEALLOCATE PREPARE b08c_stmt;

CREATE TABLE IF NOT EXISTS oa_attendance_leave_quota_allocation (
 allocation_id bigint NOT NULL AUTO_INCREMENT,
 leave_request_id bigint NOT NULL, business_round int NOT NULL,
 account_id bigint NOT NULL, bucket_id bigint NOT NULL, user_id bigint NOT NULL, leave_type_id bigint NOT NULL,
 units bigint NOT NULL, status varchar(20) NOT NULL, source_expires_on date NOT NULL,
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(allocation_id), UNIQUE KEY uk_leave_round_bucket(leave_request_id,business_round,bucket_id),
 KEY idx_quota_bucket(bucket_id,status), KEY idx_quota_account(account_id,leave_request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='请假轮次按原额度来源桶分配';
CREATE TABLE IF NOT EXISTS oa_attendance_leave_quota_event (
 event_id bigint NOT NULL AUTO_INCREMENT,
 leave_request_id bigint NOT NULL, business_round int NOT NULL,
 event_key varchar(160) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 action varchar(24) NOT NULL, fingerprint char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 units bigint NOT NULL, source_json longtext NOT NULL,
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(event_id), UNIQUE KEY uk_quota_event(leave_request_id,business_round,event_key),
 KEY idx_quota_event_request(leave_request_id,business_round,event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='请假额度占用消费释放反向审计';

-- Missing standard types are disabled placeholders until HR configures them.
-- No legal entitlement, paid ratio, proof threshold, or daily minutes is assumed.
INSERT INTO oa_attendance_leave_type(type_code,type_name,unit_mode,pay_policy,paid_ratio,balance_required,attachment_required,min_minutes,step_minutes,allow_cross_day,approval_required,sort_no,status,row_version,create_by,create_time,remark) SELECT 'ANNUAL','年假','DAY','POLICY',0,1,0,1,1,1,1,30,'DISABLED',0,'system',NOW(),'待HR配置后启用；未配置不自动授予额度或法规政策' WHERE NOT EXISTS(SELECT 1 FROM oa_attendance_leave_type WHERE type_code='ANNUAL');
INSERT INTO oa_attendance_leave_type(type_code,type_name,unit_mode,pay_policy,paid_ratio,balance_required,attachment_required,min_minutes,step_minutes,allow_cross_day,approval_required,sort_no,status,row_version,create_by,create_time,remark) SELECT 'COMPENSATORY','调休','DAY','POLICY',0,1,0,1,1,1,1,40,'DISABLED',0,'system',NOW(),'待HR配置后启用；未配置不自动授予额度或法规政策' WHERE NOT EXISTS(SELECT 1 FROM oa_attendance_leave_type WHERE type_code='COMPENSATORY');
INSERT INTO oa_attendance_leave_type(type_code,type_name,unit_mode,pay_policy,paid_ratio,balance_required,attachment_required,min_minutes,step_minutes,allow_cross_day,approval_required,sort_no,status,row_version,create_by,create_time,remark) SELECT 'MARRIAGE','婚假','DAY','POLICY',0,0,0,1,1,1,1,50,'DISABLED',0,'system',NOW(),'待HR配置后启用；未配置不自动授予额度或法规政策' WHERE NOT EXISTS(SELECT 1 FROM oa_attendance_leave_type WHERE type_code='MARRIAGE');
INSERT INTO oa_attendance_leave_type(type_code,type_name,unit_mode,pay_policy,paid_ratio,balance_required,attachment_required,min_minutes,step_minutes,allow_cross_day,approval_required,sort_no,status,row_version,create_by,create_time,remark) SELECT 'BEREAVEMENT','丧假','DAY','POLICY',0,0,0,1,1,1,1,60,'DISABLED',0,'system',NOW(),'待HR配置后启用；未配置不自动授予额度或法规政策' WHERE NOT EXISTS(SELECT 1 FROM oa_attendance_leave_type WHERE type_code='BEREAVEMENT');
INSERT INTO oa_attendance_leave_type(type_code,type_name,unit_mode,pay_policy,paid_ratio,balance_required,attachment_required,min_minutes,step_minutes,allow_cross_day,approval_required,sort_no,status,row_version,create_by,create_time,remark) SELECT 'MATERNITY','产假','DAY','POLICY',0,0,0,1,1,1,1,70,'DISABLED',0,'system',NOW(),'待HR配置后启用；未配置不自动授予额度或法规政策' WHERE NOT EXISTS(SELECT 1 FROM oa_attendance_leave_type WHERE type_code='MATERNITY');
INSERT INTO oa_attendance_leave_type(type_code,type_name,unit_mode,pay_policy,paid_ratio,balance_required,attachment_required,min_minutes,step_minutes,allow_cross_day,approval_required,sort_no,status,row_version,create_by,create_time,remark) SELECT 'PATERNITY','陪产假','DAY','POLICY',0,0,0,1,1,1,1,80,'DISABLED',0,'system',NOW(),'待HR配置后启用；未配置不自动授予额度或法规政策' WHERE NOT EXISTS(SELECT 1 FROM oa_attendance_leave_type WHERE type_code='PATERNITY');
INSERT INTO oa_attendance_leave_type(type_code,type_name,unit_mode,pay_policy,paid_ratio,balance_required,attachment_required,min_minutes,step_minutes,allow_cross_day,approval_required,sort_no,status,row_version,create_by,create_time,remark) SELECT 'CHILDCARE','育儿假','DAY','POLICY',0,0,0,1,1,1,1,90,'DISABLED',0,'system',NOW(),'待HR配置后启用；未配置不自动授予额度或法规政策' WHERE NOT EXISTS(SELECT 1 FROM oa_attendance_leave_type WHERE type_code='CHILDCARE');

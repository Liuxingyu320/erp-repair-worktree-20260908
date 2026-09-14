-- Additive salary provenance. No employee amount, old payslip or signed contract is changed.
CREATE TABLE IF NOT EXISTS sys_employee_salary_source (
 source_id char(36) NOT NULL,
 employee_id bigint NOT NULL,
 previous_source_id char(36) DEFAULT NULL,
 source_type varchar(32) NOT NULL,
 business_id varchar(64) NOT NULL,
 batch_id bigint DEFAULT NULL,
 row_id bigint DEFAULT NULL,
 row_version bigint DEFAULT NULL,
 file_sha256 char(64) DEFAULT NULL,
 command_id varchar(100) NOT NULL,
 request_hash char(64) NOT NULL,
 before_hash char(64) NOT NULL,
 effective_date date NOT NULL,
 operator_user_id bigint NOT NULL,
 operator_name varchar(64) NOT NULL,
 reason varchar(500) NOT NULL,
 verified tinyint(1) NOT NULL DEFAULT 0,
 base_salary decimal(16,2) NOT NULL,
 post_salary decimal(16,2) NOT NULL,
 field_allowance decimal(16,2) NOT NULL,
 performance_salary decimal(16,2) NOT NULL,
 salary_total decimal(16,2) NOT NULL,
 confirmed_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY (source_id),
 UNIQUE KEY uk_salary_source_command (command_id),
 KEY idx_salary_source_employee (employee_id, confirmed_at),
 KEY idx_salary_source_import (batch_id, row_id, row_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不可变合同工资来源及正式变更';
CREATE TABLE IF NOT EXISTS sys_employee_salary_current (
 employee_id bigint NOT NULL,
 source_id char(36) NOT NULL,
 PRIMARY KEY (employee_id),
 UNIQUE KEY uk_salary_current_source (source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工档案当前采用的工资来源';
CREATE TABLE IF NOT EXISTS oa_sign_package_salary_source (
 package_id bigint NOT NULL,
 employee_id bigint NOT NULL,
 source_id char(36) NOT NULL,
 bound_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY (package_id),
 KEY idx_package_salary_source (source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='签约包冻结的工资来源';
-- Enable only after historical reconciliation. New Excel generation always requires verified evidence.
INSERT INTO sys_config(config_name,config_key,config_value,config_type,create_by,create_time,remark)
SELECT '人事历史工资来源强校验','feature.hr.salarySource.strict.enabled','false','Y','migration',sysdate(),
       '历史来源核对并补证后启用；不控制新Excel合同生成的来源校验'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key='feature.hr.salarySource.strict.enabled');
-- Stable compatibility boundary for already prepared, in-flight signature-first contracts.
INSERT INTO sys_config(config_name,config_key,config_value,config_type,create_by,create_time,remark)
SELECT '合同工资来源启用时间','feature.hr.salarySource.startedAt',DATE_FORMAT(sysdate(),'%Y-%m-%d %H:%i:%s'),'Y','migration',sysdate(),
       '仅用于识别启用前已留签名且已生成最终文件的历史合同；重复迁移不移动此时间'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key='feature.hr.salarySource.startedAt');

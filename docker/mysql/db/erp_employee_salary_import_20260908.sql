-- Source trace and before values for explicitly confirmed onboarding Excel salary imports.
-- No historical salary is inferred or overwritten by this migration.
CREATE TABLE IF NOT EXISTS sys_employee_salary_import_audit (
    audit_id bigint NOT NULL AUTO_INCREMENT,
    employee_id bigint NOT NULL,
    source_batch_id bigint NOT NULL,
    source_row_id bigint NOT NULL,
    source_version bigint NOT NULL,
    source_file_sha256 varchar(64) NOT NULL,
    before_json json NOT NULL,
    salary_json json NOT NULL,
    operator_user_id bigint NOT NULL,
    operator_name varchar(64) NOT NULL,
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (audit_id),
    UNIQUE KEY uk_employee_salary_import_source (source_row_id, source_version),
    KEY idx_employee_salary_import_employee (employee_id, audit_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='入职合同Excel工资入档来源审计';

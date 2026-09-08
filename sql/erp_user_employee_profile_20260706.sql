-- 用户员工档案扩展字段
-- 执行顺序：先部署本脚本，再部署后端代码。

CREATE TABLE IF NOT EXISTS sys_user_profile (
    profile_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '档案ID',
    user_id bigint(20) NOT NULL COMMENT '用户ID',
    employee_no varchar(64) DEFAULT NULL COMMENT '工号',
    company_name varchar(100) DEFAULT NULL COMMENT '所属公司',
    dept_level1_name varchar(100) DEFAULT NULL COMMENT '1级部门',
    dept_level2_name varchar(100) DEFAULT NULL COMMENT '2级部门',
    dept_level3_name varchar(100) DEFAULT NULL COMMENT '3级部门',
    store_name varchar(100) DEFAULT NULL COMMENT '4级门店',
    position_names varchar(200) DEFAULT NULL COMMENT '职位',
    job_grade varchar(64) DEFAULT NULL COMMENT '职级',
    department_supervisor varchar(100) DEFAULT NULL COMMENT '部门主管',
    direct_supervisor varchar(100) DEFAULT NULL COMMENT '直属主管',
    direct_supervisor_user_id bigint(20) DEFAULT NULL COMMENT '直属主管用户ID',
    employee_status varchar(32) DEFAULT NULL COMMENT '员工状态',
    employee_category varchar(64) DEFAULT NULL COMMENT '人员类别',
    birth_date date DEFAULT NULL COMMENT '出生日期',
    id_type varchar(64) DEFAULT NULL COMMENT '证件类型',
    id_number varchar(64) DEFAULT NULL COMMENT '证件号码',
    blood_type varchar(16) DEFAULT NULL COMMENT '血型',
    registered_residence varchar(255) DEFAULT NULL COMMENT '户口所在地',
    current_address varchar(255) DEFAULT NULL COMMENT '现居住地址',
    first_education varchar(64) DEFAULT NULL COMMENT '第一学历',
    first_degree varchar(64) DEFAULT NULL COMMENT '第一学位',
    first_graduation_date date DEFAULT NULL COMMENT '毕业时间',
    first_graduation_school varchar(100) DEFAULT NULL COMMENT '第一学历毕业学校',
    first_major varchar(100) DEFAULT NULL COMMENT '第一学历所学专业',
    highest_education varchar(64) DEFAULT NULL COMMENT '最高学历',
    highest_degree varchar(64) DEFAULT NULL COMMENT '最高学位',
    highest_graduation_date date DEFAULT NULL COMMENT '最高学历毕业时间',
    highest_graduation_school varchar(100) DEFAULT NULL COMMENT '最高学历毕业学校',
    highest_major varchar(100) DEFAULT NULL COMMENT '最高学历所学专业',
    political_status varchar(64) DEFAULT NULL COMMENT '政治面貌',
    marital_status varchar(32) DEFAULT NULL COMMENT '婚姻状况',
    nationality varchar(64) DEFAULT NULL COMMENT '国籍',
    foreign_national_flag varchar(8) DEFAULT NULL COMMENT '是否外籍',
    ethnicity varchar(64) DEFAULT NULL COMMENT '民族',
    health_status varchar(64) DEFAULT NULL COMMENT '健康状况',
    emergency_contact varchar(100) DEFAULT NULL COMMENT '紧急联系人',
    emergency_contact_relation varchar(64) DEFAULT NULL COMMENT '与紧急联系人关系',
    emergency_contact_phone varchar(32) DEFAULT NULL COMMENT '紧急联系人电话',
    recruitment_channel varchar(100) DEFAULT NULL COMMENT '招聘渠道',
    office_phone varchar(32) DEFAULT NULL COMMENT '办公电话',
    work_start_date date DEFAULT NULL COMMENT '参加工作时间',
    work_years varchar(32) DEFAULT NULL COMMENT '工龄',
    entry_date date DEFAULT NULL COMMENT '入职时间',
    probation_period varchar(64) DEFAULT NULL COMMENT '试用期',
    probation_start_date date DEFAULT NULL COMMENT '试用期开始日期',
    probation_end_date date DEFAULT NULL COMMENT '试用期结束日期',
    planned_regularization_date date DEFAULT NULL COMMENT '计划转正日期',
    actual_regularization_date date DEFAULT NULL COMMENT '实际转正日期',
    company_years varchar(32) DEFAULT NULL COMMENT '司龄',
    current_position_start_date date DEFAULT NULL COMMENT '本岗位任职日期',
    contract_start_date date DEFAULT NULL COMMENT '现合同起始日',
    contract_end_date date DEFAULT NULL COMMENT '现合同到期日',
    contract_type varchar(32) DEFAULT NULL COMMENT '合同类型',
    contract_term varchar(64) DEFAULT NULL COMMENT '合同期限',
    renewal_count int(11) DEFAULT NULL COMMENT '续签次数',
    work_location varchar(100) DEFAULT NULL COMMENT '工作所在地',
    work_city_level varchar(64) DEFAULT NULL COMMENT '工作所在城市级别',
    attendance_method varchar(64) DEFAULT NULL COMMENT '考勤方式',
    household_type varchar(64) DEFAULT NULL COMMENT '户口性质',
    social_type varchar(32) DEFAULT NULL COMMENT '社保类型',
    social_security_location varchar(100) DEFAULT NULL COMMENT '社保缴纳地',
    housing_fund_location varchar(100) DEFAULT NULL COMMENT '公积金缴纳地',
    leave_date date DEFAULT NULL COMMENT '离职时间',
    bank_name varchar(100) DEFAULT NULL COMMENT '开户银行',
    bank_account varchar(64) DEFAULT NULL COMMENT '银行卡号',
    legal_entity varchar(100) DEFAULT NULL COMMENT '法人单位',
    legal_entity_id bigint DEFAULT NULL COMMENT '稳定法律主体ID',
    legal_entity_code varchar(64) DEFAULT NULL COMMENT '稳定法律主体代码',
    base_salary decimal(16,2) DEFAULT NULL COMMENT '基本工资',
    post_salary decimal(16,2) DEFAULT NULL COMMENT '岗位工资',
    field_allowance decimal(16,2) DEFAULT NULL COMMENT '外勤补贴',
    performance_salary decimal(16,2) DEFAULT NULL COMMENT '绩效工资',
    salary_total decimal(16,2) DEFAULT NULL COMMENT '薪资合计',
    salary_version varchar(32) DEFAULT NULL COMMENT '薪资版本',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime DEFAULT NULL COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (profile_id),
    UNIQUE KEY uk_user_profile_user_id (user_id),
    UNIQUE KEY uk_user_profile_employee_no (employee_no),
    KEY idx_user_profile_employee_status (employee_status),
    KEY idx_user_profile_employee_category (employee_category),
    KEY idx_user_profile_entry_date (entry_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户员工档案表';

SET @erp_db = DATABASE();
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'sys_user_profile') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'sys_user_profile' AND COLUMN_NAME = 'contract_type') = 0,
    'ALTER TABLE sys_user_profile ADD COLUMN contract_type varchar(32) DEFAULT NULL COMMENT ''合同类型'' AFTER contract_end_date',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'sys_user_profile') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'sys_user_profile' AND COLUMN_NAME = 'social_type') = 0,
    'ALTER TABLE sys_user_profile ADD COLUMN social_type varchar(32) DEFAULT NULL COMMENT ''社保类型'' AFTER household_type',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT INTO sys_user_profile (
    user_id,
    employee_category,
    entry_date,
    create_by,
    create_time,
    update_by,
    update_time
)
SELECT
    u.user_id,
    CASE
        WHEN u.remark LIKE '%员工类型:%'
            THEN NULLIF(TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(u.remark, '员工类型:', -1), ';', 1)), '')
        ELSE NULL
    END AS employee_category,
    CASE
        WHEN u.remark LIKE '%入职:%'
            AND TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(u.remark, '入职:', -1), ';', 1)) REGEXP '^[0-9]{4}-[0-9]{2}-[0-9]{2}$'
            THEN STR_TO_DATE(TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(u.remark, '入职:', -1), ';', 1)), '%Y-%m-%d')
        ELSE NULL
    END AS entry_date,
    u.create_by,
    COALESCE(u.create_time, SYSDATE()),
    u.update_by,
    COALESCE(u.update_time, SYSDATE())
FROM sys_user u
WHERE u.del_flag = '0'
  AND (u.remark LIKE '%员工类型:%' OR u.remark LIKE '%入职:%')
ON DUPLICATE KEY UPDATE
    employee_category = COALESCE(VALUES(employee_category), employee_category),
    entry_date = COALESCE(VALUES(entry_date), entry_date),
    update_by = VALUES(update_by),
    update_time = SYSDATE();

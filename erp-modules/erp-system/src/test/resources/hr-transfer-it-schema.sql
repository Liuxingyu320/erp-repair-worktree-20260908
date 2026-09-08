CREATE TABLE sys_legal_entity (
  legal_entity_id BIGINT NOT NULL AUTO_INCREMENT, legal_entity_code VARCHAR(64) NOT NULL,
  legal_entity_name VARCHAR(160) NOT NULL, unified_social_credit_code VARCHAR(32),
  registered_address VARCHAR(255), legal_representative VARCHAR(64), contact_phone VARCHAR(32),
  status CHAR(1) NOT NULL DEFAULT '0', version BIGINT NOT NULL DEFAULT 0,
  create_by VARCHAR(64) DEFAULT '', create_time DATETIME, update_by VARCHAR(64) DEFAULT '',
  update_time DATETIME, remark VARCHAR(500), PRIMARY KEY (legal_entity_id),
  UNIQUE KEY uk_sys_legal_entity_code (legal_entity_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_dept (
  dept_id BIGINT NOT NULL, parent_id BIGINT NOT NULL DEFAULT 0, ancestors VARCHAR(255) DEFAULT '',
  dept_name VARCHAR(100) NOT NULL, order_num INT NOT NULL DEFAULT 0, leader VARCHAR(64), leader_user_id BIGINT,
  phone VARCHAR(32), email VARCHAR(100), status CHAR(1) NOT NULL DEFAULT '0',
  dept_type VARCHAR(32) NOT NULL DEFAULT 'DEPT', legal_entity_id BIGINT, del_flag CHAR(1) NOT NULL DEFAULT '0',
  create_by VARCHAR(64) DEFAULT '', create_time DATETIME, update_by VARCHAR(64) DEFAULT '', update_time DATETIME,
  PRIMARY KEY (dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_user (
  user_id BIGINT NOT NULL AUTO_INCREMENT, dept_id BIGINT, user_name VARCHAR(64) NOT NULL,
  nick_name VARCHAR(64), email VARCHAR(100), avatar VARCHAR(255), phonenumber VARCHAR(32),
  sex CHAR(1), password VARCHAR(255), status CHAR(1) NOT NULL DEFAULT '0',
  del_flag CHAR(1) NOT NULL DEFAULT '0', login_ip VARCHAR(128), login_date DATETIME,
  pwd_update_date DATETIME, credential_state VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  temporary_password_expires_at DATETIME, create_by VARCHAR(64) DEFAULT '', create_time DATETIME,
  update_by VARCHAR(64) DEFAULT '', update_time DATETIME, remark VARCHAR(500),
  PRIMARY KEY (user_id), UNIQUE KEY uk_sys_user_name (user_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_user_profile (
  profile_id BIGINT NOT NULL AUTO_INCREMENT, user_id BIGINT NOT NULL, employee_no VARCHAR(64),
  position_no VARCHAR(96),
  company_name VARCHAR(100), dept_level1_name VARCHAR(100), dept_level2_name VARCHAR(100),
  dept_level3_name VARCHAR(100), store_name VARCHAR(100), position_names VARCHAR(200), job_grade VARCHAR(64),
  department_supervisor VARCHAR(100), direct_supervisor VARCHAR(100), direct_supervisor_user_id BIGINT,
  employee_status VARCHAR(32), employee_category VARCHAR(64), birth_date DATE, id_type VARCHAR(64), id_number VARCHAR(64),
  blood_type VARCHAR(16), registered_residence VARCHAR(255), current_address VARCHAR(255),
  student_status VARCHAR(20), school_name VARCHAR(200), retirement_status VARCHAR(20),
  income_start_year_month CHAR(7),
  first_education VARCHAR(64), first_degree VARCHAR(64), first_graduation_date DATE,
  first_graduation_school VARCHAR(100), first_major VARCHAR(100), highest_education VARCHAR(64),
  highest_degree VARCHAR(64), highest_graduation_date DATE, highest_graduation_school VARCHAR(100),
  highest_major VARCHAR(100), political_status VARCHAR(64), marital_status VARCHAR(32), nationality VARCHAR(64),
  foreign_national_flag VARCHAR(8), ethnicity VARCHAR(64), health_status VARCHAR(64), emergency_contact VARCHAR(100),
  emergency_contact_relation VARCHAR(64), emergency_contact_phone VARCHAR(32), recruitment_channel VARCHAR(100),
  office_phone VARCHAR(32), work_start_date DATE, work_years VARCHAR(32), entry_date DATE,
  probation_period VARCHAR(64), probation_start_date DATE, probation_end_date DATE,
  planned_regularization_date DATE, actual_regularization_date DATE,
  company_years VARCHAR(32), current_position_start_date DATE, contract_start_date DATE, contract_end_date DATE,
  contract_type VARCHAR(32), contract_term VARCHAR(64), renewal_count INT, work_location VARCHAR(100),
  work_city_level VARCHAR(64), attendance_method VARCHAR(64), household_type VARCHAR(64), social_type VARCHAR(32),
  social_security_location VARCHAR(100), housing_fund_location VARCHAR(100), leave_date DATE,
  bank_name VARCHAR(100), bank_account VARCHAR(64), legal_entity VARCHAR(100),
  legal_entity_id BIGINT, legal_entity_code VARCHAR(64),
  base_salary DECIMAL(16,2), post_salary DECIMAL(16,2), field_allowance DECIMAL(16,2),
  performance_salary DECIMAL(16,2), salary_total DECIMAL(16,2), salary_version VARCHAR(32),
  create_by VARCHAR(64) DEFAULT '', create_time DATETIME, update_by VARCHAR(64) DEFAULT '', update_time DATETIME,
  PRIMARY KEY (profile_id), UNIQUE KEY uk_user_profile_user_id (user_id),
  UNIQUE KEY uk_user_profile_employee_no (employee_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_post (
  post_id BIGINT NOT NULL, post_code VARCHAR(64), post_name VARCHAR(100), post_sort INT DEFAULT 0,
  status CHAR(1) DEFAULT '0', create_by VARCHAR(64) DEFAULT '', create_time DATETIME,
  update_by VARCHAR(64) DEFAULT '', update_time DATETIME, remark VARCHAR(500), PRIMARY KEY (post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_role (
  role_id BIGINT NOT NULL, role_name VARCHAR(64), role_key VARCHAR(100), role_sort INT DEFAULT 0,
  data_scope CHAR(1) DEFAULT '1', menu_check_strictly TINYINT DEFAULT 1, dept_check_strictly TINYINT DEFAULT 1,
  status CHAR(1) DEFAULT '0', del_flag CHAR(1) DEFAULT '0', create_by VARCHAR(64) DEFAULT '', create_time DATETIME,
  update_by VARCHAR(64) DEFAULT '', update_time DATETIME, remark VARCHAR(500), PRIMARY KEY (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_user_role (
  user_id BIGINT NOT NULL, role_id BIGINT NOT NULL, PRIMARY KEY (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_user_post (
  user_id BIGINT NOT NULL, post_id BIGINT NOT NULL, PRIMARY KEY (user_id, post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_config (
  config_id BIGINT NOT NULL AUTO_INCREMENT, config_name VARCHAR(100) NOT NULL,
  config_key VARCHAR(100) NOT NULL, config_value VARCHAR(500) NOT NULL, config_type CHAR(1) DEFAULT 'N',
  create_by VARCHAR(64) DEFAULT '', create_time DATETIME, update_by VARCHAR(64) DEFAULT '',
  update_time DATETIME, remark VARCHAR(500), PRIMARY KEY (config_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_menu (
  menu_id BIGINT NOT NULL, menu_name VARCHAR(64) NOT NULL, parent_id BIGINT NOT NULL DEFAULT 0,
  order_num INT NOT NULL DEFAULT 0, path VARCHAR(200), component VARCHAR(255), query VARCHAR(255),
  route_name VARCHAR(64), is_frame INT NOT NULL DEFAULT 1, is_cache INT NOT NULL DEFAULT 0,
  menu_type CHAR(1) NOT NULL DEFAULT '', visible CHAR(1) NOT NULL DEFAULT '0',
  status CHAR(1) NOT NULL DEFAULT '0', perms VARCHAR(100), icon VARCHAR(100),
  create_by VARCHAR(64) DEFAULT '', create_time DATETIME, update_by VARCHAR(64) DEFAULT '',
  update_time DATETIME, remark VARCHAR(500), PRIMARY KEY (menu_id), KEY idx_sys_menu_perms (perms)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_role_menu (
  role_id BIGINT NOT NULL, menu_id BIGINT NOT NULL, PRIMARY KEY (role_id, menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_sign_hr_state (
  state_id TINYINT NOT NULL, hr_user_id BIGINT, managed_role_id BIGINT,
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (state_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_sign_hr_menu_grant (
  role_id BIGINT NOT NULL, menu_id BIGINT NOT NULL, hr_user_id BIGINT NOT NULL,
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (role_id, menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO sys_menu
  (menu_id,menu_name,parent_id,order_num,path,component,route_name,is_frame,is_cache,
   menu_type,visible,status,perms,icon,create_by,create_time)
VALUES
  (5101,'员工档案',0,1,'employee','hr/employee/index','HrEmployee',1,0,
   'C','0','0','hr:employee:list','peoples','system',NOW());

INSERT INTO sys_role(role_id,role_name,role_key,status,del_flag)
VALUES (700,'唯一HR签约','sign_single_hr','0','0');

INSERT INTO sys_sign_hr_state(state_id,hr_user_id,managed_role_id)
VALUES (1,88,700);

DELIMITER $$
CREATE PROCEDURE sync_sign_hr_permissions_with_plan()
BEGIN
  DO 0;
END$$
DELIMITER ;

CREATE TABLE sys_config (
    config_id bigint NOT NULL AUTO_INCREMENT,
    config_name varchar(100) NOT NULL,
    config_key varchar(100) NOT NULL,
    config_value varchar(500) NOT NULL,
    config_type char(1) NOT NULL DEFAULT 'N',
    create_by varchar(64) DEFAULT '',
    create_time datetime DEFAULT NULL,
    update_by varchar(64) DEFAULT '',
    update_time datetime DEFAULT NULL,
    remark varchar(500) DEFAULT NULL,
    PRIMARY KEY (config_id),
    UNIQUE KEY uk_sys_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_menu (
    menu_id bigint NOT NULL,
    menu_name varchar(50) NOT NULL,
    parent_id bigint NOT NULL DEFAULT 0,
    order_num int NOT NULL DEFAULT 0,
    path varchar(200) DEFAULT '',
    component varchar(255) DEFAULT NULL,
    query varchar(255) DEFAULT NULL,
    route_name varchar(50) DEFAULT '',
    is_frame char(1) DEFAULT '1',
    is_cache char(1) DEFAULT '0',
    menu_type char(1) DEFAULT '',
    visible char(1) DEFAULT '0',
    status char(1) DEFAULT '0',
    perms varchar(100) DEFAULT NULL,
    icon varchar(100) DEFAULT '#',
    create_by varchar(64) DEFAULT '',
    create_time datetime DEFAULT NULL,
    update_by varchar(64) DEFAULT '',
    update_time datetime DEFAULT NULL,
    remark varchar(500) DEFAULT '',
    PRIMARY KEY (menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_role (
    role_id bigint NOT NULL,
    role_name varchar(64) NOT NULL,
    role_key varchar(100) NOT NULL,
    status char(1) NOT NULL DEFAULT '0',
    del_flag char(1) NOT NULL DEFAULT '0',
    PRIMARY KEY (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_role_menu (
    role_id bigint NOT NULL,
    menu_id bigint NOT NULL,
    PRIMARY KEY (role_id, menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, menu_type,
     visible, status, perms)
VALUES
    (100, '人事管理', 0, 1, 'hr', NULL, 'M', '0', '0', NULL),
    (101, '员工档案', 100, 1, 'employee', 'hr/employee/index', 'C', '0', '0',
     'hr:employee:list');

INSERT INTO sys_role (role_id, role_name, role_key, status, del_flag)
VALUES
    (1, '管理员', 'admin', '0', '0'),
    (2, '店长', 'store_manager', '0', '0');

INSERT INTO sys_role_menu (role_id, menu_id) VALUES (1, 101);

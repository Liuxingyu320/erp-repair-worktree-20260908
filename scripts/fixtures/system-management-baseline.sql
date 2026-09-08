-- Minimal, non-production fixture for native system-management migration verification.
CREATE TABLE sys_config (
    config_id INT NOT NULL AUTO_INCREMENT,
    config_name VARCHAR(100) NOT NULL DEFAULT '',
    config_key VARCHAR(100) NOT NULL,
    config_value VARCHAR(500) DEFAULT NULL,
    config_type CHAR(1) NOT NULL DEFAULT 'N',
    create_by VARCHAR(64) DEFAULT '',
    create_time DATETIME DEFAULT NULL,
    update_by VARCHAR(64) DEFAULT '',
    update_time DATETIME DEFAULT NULL,
    remark VARCHAR(500) DEFAULT NULL,
    PRIMARY KEY (config_id),
    UNIQUE KEY uk_sys_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_menu (
    menu_id BIGINT NOT NULL AUTO_INCREMENT,
    menu_name VARCHAR(50) NOT NULL,
    parent_id BIGINT NOT NULL DEFAULT 0,
    order_num INT NOT NULL DEFAULT 0,
    path VARCHAR(200) DEFAULT '',
    component VARCHAR(255) DEFAULT NULL,
    query VARCHAR(255) DEFAULT NULL,
    route_name VARCHAR(50) DEFAULT '',
    is_frame CHAR(1) NOT NULL DEFAULT '1',
    is_cache CHAR(1) NOT NULL DEFAULT '0',
    menu_type CHAR(1) NOT NULL DEFAULT '',
    visible CHAR(1) NOT NULL DEFAULT '0',
    status CHAR(1) NOT NULL DEFAULT '0',
    perms VARCHAR(100) DEFAULT NULL,
    icon VARCHAR(100) DEFAULT '#',
    create_by VARCHAR(64) DEFAULT '',
    create_time DATETIME DEFAULT NULL,
    update_by VARCHAR(64) DEFAULT '',
    update_time DATETIME DEFAULT NULL,
    remark VARCHAR(500) DEFAULT '',
    PRIMARY KEY (menu_id),
    KEY idx_sys_menu_perms (perms)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_role_menu (
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_user (
    user_id BIGINT NOT NULL AUTO_INCREMENT,
    pwd_update_date DATETIME DEFAULT NULL,
    del_flag CHAR(1) NOT NULL DEFAULT '0',
    PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO sys_config (config_id, config_name, config_key, config_value, config_type, remark) VALUES
    (1, '旧初始密码', 'sys.user.initPassword', 'fixture-only-not-a-real-password', 'Y', 'native test fixture'),
    (2, '旧首次改密', 'sys.account.initPasswordModify', 'true', 'Y', 'native test fixture');

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
     menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES
    (1, '参数管理', 0, 1, 'config', NULL, NULL, '', '1', '0', 'C', '0', '0', 'system:config:list', '#', 'fixture', NOW(), '', NULL, ''),
    (2, '用户管理', 0, 2, 'user', NULL, NULL, '', '1', '0', 'C', '0', '0', 'system:user:list', '#', 'fixture', NOW(), '', NULL, ''),
    (3, '重置密码', 2, 1, '#', NULL, NULL, '', '1', '0', 'F', '0', '0', 'system:user:resetPwd', '#', 'fixture', NOW(), '', NULL, ''),
    (4, '参数详情', 1, 2, '#', NULL, NULL, '', '1', '0', 'F', '0', '0', 'system:config:query', '#', 'fixture', NOW(), '', NULL, ''),
    (5, '日志详情', 0, 3, '#', NULL, NULL, '', '1', '0', 'F', '0', '0', 'system:operlog:query', '#', 'fixture', NOW(), '', NULL, ''),
    (6, '孤儿登录详情', 0, 4, '#', NULL, NULL, '', '1', '0', 'F', '0', '0', 'system:logininfor:query', '#', 'fixture', NOW(), '', NULL, ''),
    (7, '孤儿薪资导入', 0, 5, '#', NULL, NULL, '', '1', '0', 'F', '0', '0', 'system:salary:import', '#', 'fixture', NOW(), '', NULL, '');

INSERT INTO sys_role_menu (role_id, menu_id) VALUES (10, 3), (10, 6), (10, 7);
INSERT INTO sys_user (user_id, pwd_update_date, del_flag) VALUES (1, NULL, '0');

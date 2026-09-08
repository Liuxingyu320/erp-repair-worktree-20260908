-- Minimal pre-Release-C schema and synthetic data for native MySQL verification.
-- This fixture contains no production data and intentionally has no workflow columns.
CREATE TABLE sys_config (
    config_id INT NOT NULL AUTO_INCREMENT,
    config_name VARCHAR(100) DEFAULT '',
    config_key VARCHAR(100) DEFAULT '',
    config_value VARCHAR(500) DEFAULT '',
    config_type CHAR(1) DEFAULT 'N',
    create_by VARCHAR(64) DEFAULT '',
    create_time DATETIME DEFAULT NULL,
    update_by VARCHAR(64) DEFAULT '',
    update_time DATETIME DEFAULT NULL,
    remark VARCHAR(500) DEFAULT NULL,
    PRIMARY KEY (config_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_menu (
    menu_id BIGINT NOT NULL AUTO_INCREMENT,
    menu_name VARCHAR(50) NOT NULL,
    parent_id BIGINT DEFAULT 0,
    order_num INT DEFAULT 0,
    path VARCHAR(200) DEFAULT '',
    component VARCHAR(255) DEFAULT NULL,
    query VARCHAR(255) DEFAULT NULL,
    route_name VARCHAR(50) DEFAULT '',
    is_frame INT DEFAULT 1,
    is_cache INT DEFAULT 0,
    menu_type CHAR(1) DEFAULT '',
    visible CHAR(1) DEFAULT '0',
    status CHAR(1) DEFAULT '0',
    perms VARCHAR(100) DEFAULT NULL,
    icon VARCHAR(100) DEFAULT '#',
    create_by VARCHAR(64) DEFAULT '',
    create_time DATETIME DEFAULT NULL,
    update_by VARCHAR(64) DEFAULT '',
    update_time DATETIME DEFAULT NULL,
    remark VARCHAR(500) DEFAULT '',
    PRIMARY KEY (menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_role_menu (
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_user (
    user_id BIGINT NOT NULL AUTO_INCREMENT,
    dept_id BIGINT DEFAULT NULL,
    user_name VARCHAR(30) NOT NULL,
    nick_name VARCHAR(30) NOT NULL,
    status CHAR(1) DEFAULT '0',
    del_flag CHAR(1) DEFAULT '0',
    PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_notice (
    notice_id INT NOT NULL AUTO_INCREMENT,
    notice_title VARCHAR(50) NOT NULL,
    notice_type CHAR(1) NOT NULL,
    notice_content LONGBLOB,
    status CHAR(1) DEFAULT '0',
    create_by VARCHAR(64) DEFAULT '',
    create_time DATETIME DEFAULT NULL,
    update_by VARCHAR(64) DEFAULT '',
    update_time DATETIME DEFAULT NULL,
    remark VARCHAR(255) DEFAULT NULL,
    PRIMARY KEY (notice_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_notice_read (
    read_id BIGINT NOT NULL AUTO_INCREMENT,
    notice_id INT NOT NULL,
    user_id BIGINT NOT NULL,
    read_time DATETIME NOT NULL,
    PRIMARY KEY (read_id),
    UNIQUE KEY uk_user_notice (user_id, notice_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
    (107, '通知公告', 1, 8, 'notice', 'system/notice/index', '', '',
     1, 0, 'C', '0', '0', 'system:notice:list', 'message', 'fixture', NOW(), 'native fixture');

INSERT INTO sys_user (user_id, dept_id, user_name, nick_name, status, del_flag) VALUES
    (1, 10, 'active-one', '启用用户一', '0', '0'),
    (2, 10, 'active-two', '启用用户二', '0', '0'),
    (3, 20, 'disabled-reader', '停用但历史已读', '1', '0'),
    (4, 20, 'deleted-user', '已删除用户', '0', '2');

INSERT INTO sys_notice
    (notice_id, notice_title, notice_type, notice_content, status,
     create_by, create_time, update_by, update_time, remark)
VALUES
    (1, '历史在线公告', '2', '<p>历史在线内容</p>', '0',
     'fixture', '2026-07-01 09:00:00', '', NULL, 'must become PUBLISHED'),
    (2, '历史关闭公告', '2', '<p>历史关闭内容</p>', '1',
     'fixture', '2026-07-02 09:00:00', '', NULL, 'must become OFFLINE');

INSERT INTO sys_notice_read (read_id, notice_id, user_id, read_time)
VALUES (1, 1, 3, '2026-07-03 09:00:00');

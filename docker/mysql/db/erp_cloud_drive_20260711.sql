CREATE TABLE IF NOT EXISTS drive_space (
  space_id bigint NOT NULL AUTO_INCREMENT,
  space_key varchar(100) NOT NULL,
  space_type varchar(20) NOT NULL,
  owner_user_id bigint DEFAULT NULL,
  dept_id bigint DEFAULT NULL,
  space_name varchar(120) NOT NULL,
  quota_bytes bigint NOT NULL,
  used_bytes bigint NOT NULL DEFAULT 0,
  status varchar(20) NOT NULL DEFAULT 'ACTIVE',
  version int NOT NULL DEFAULT 0,
  create_by varchar(64) DEFAULT '',
  create_time datetime DEFAULT NULL,
  update_by varchar(64) DEFAULT '',
  update_time datetime DEFAULT NULL,
  remark varchar(500) DEFAULT NULL,
  PRIMARY KEY (space_id),
  UNIQUE KEY uk_drive_space_key (space_key),
  KEY idx_drive_space_owner (owner_user_id, status),
  KEY idx_drive_space_dept (dept_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='云盘空间';

CREATE TABLE IF NOT EXISTS drive_node (
  node_id bigint NOT NULL AUTO_INCREMENT,
  space_id bigint NOT NULL,
  parent_id bigint NOT NULL DEFAULT 0,
  ancestors varchar(1000) NOT NULL DEFAULT '0',
  node_type varchar(20) NOT NULL,
  node_name varchar(200) NOT NULL,
  normalized_name varchar(200) NOT NULL,
  extension varchar(20) DEFAULT NULL,
  storage_key varchar(500) DEFAULT NULL,
  content_type varchar(120) DEFAULT NULL,
  size_bytes bigint NOT NULL DEFAULT 0,
  sha256 varchar(64) DEFAULT NULL,
  status varchar(20) NOT NULL DEFAULT 'ACTIVE',
  active_flag tinyint DEFAULT 1,
  original_parent_id bigint DEFAULT NULL,
  trash_root_id bigint DEFAULT NULL,
  trashed_by bigint DEFAULT NULL,
  trashed_time datetime DEFAULT NULL,
  purge_after datetime DEFAULT NULL,
  version int NOT NULL DEFAULT 0,
  create_by varchar(64) DEFAULT '',
  create_time datetime DEFAULT NULL,
  update_by varchar(64) DEFAULT '',
  update_time datetime DEFAULT NULL,
  remark varchar(500) DEFAULT NULL,
  PRIMARY KEY (node_id),
  UNIQUE KEY uk_drive_node_active_name (space_id, parent_id, normalized_name, active_flag),
  KEY idx_drive_node_parent (space_id, parent_id, status),
  KEY idx_drive_node_ancestors (space_id, status),
  KEY idx_drive_node_trash (space_id, trash_root_id, status, purge_after),
  KEY idx_drive_node_purge (status, purge_after, update_time, trash_root_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='云盘文件节点';

CREATE TABLE IF NOT EXISTS drive_operation_log (
  operation_id bigint NOT NULL AUTO_INCREMENT,
  space_id bigint DEFAULT NULL,
  node_id bigint DEFAULT NULL,
  action varchar(40) NOT NULL,
  operator_user_id bigint NOT NULL,
  operator_dept_id bigint DEFAULT NULL,
  operator_name varchar(64) DEFAULT '',
  request_id varchar(64) DEFAULT NULL,
  ip_address varchar(64) DEFAULT NULL,
  user_agent varchar(500) DEFAULT NULL,
  before_summary varchar(1000) DEFAULT NULL,
  after_summary varchar(1000) DEFAULT NULL,
  result varchar(20) NOT NULL,
  error_code varchar(64) DEFAULT NULL,
  create_time datetime NOT NULL,
  PRIMARY KEY (operation_id),
  KEY idx_drive_log_operator (operator_user_id, create_time),
  KEY idx_drive_log_recent (operator_user_id, result, action, create_time, node_id),
  KEY idx_drive_log_node (node_id, create_time),
  KEY idx_drive_log_space (space_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='云盘操作日志';

INSERT INTO drive_space (
  space_key,
  space_type,
  owner_user_id,
  dept_id,
  space_name,
  quota_bytes,
  used_bytes,
  status,
  version,
  create_by,
  create_time
)
SELECT
  'COMPANY:ROOT',
  'COMPANY',
  NULL,
  NULL,
  '公司公共盘',
  107374182400,
  0,
  'ACTIVE',
  0,
  'system',
  NOW()
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM drive_space WHERE space_key = 'COMPANY:ROOT'
);

-- Register the final cloud-drive menu only after validating stable IDs and the OA anchor.
DROP PROCEDURE IF EXISTS migrate_cloud_drive_menu;
DELIMITER $$
CREATE PROCEDURE migrate_cloud_drive_menu()
BEGIN
  DECLARE v_oa_count int DEFAULT 0;
  DECLARE v_oa_order int DEFAULT 0;
  DECLARE v_drive_exists int DEFAULT 0;

  SELECT COUNT(*), COALESCE(MAX(order_num), 0)
    INTO v_oa_count, v_oa_order
  FROM sys_menu
  WHERE parent_id = 0 AND path = 'oa' AND status = '0';

  IF v_oa_count <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cloud drive migration requires exactly one active root oa menu';
  END IF;

  IF EXISTS (
    SELECT 1 FROM sys_menu
    WHERE menu_id = 9600
      AND (COALESCE(path, '') <> 'drive'
        OR COALESCE(component, '') <> 'drive/index'
        OR COALESCE(perms, '') <> 'drive:access')
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'menu id 9600 is occupied by another route or permission';
  END IF;

  IF EXISTS (
    SELECT 1 FROM sys_menu WHERE path = 'drive' AND menu_id <> 9600
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cloud drive path is already assigned to another menu id';
  END IF;

  IF EXISTS (
    SELECT 1 FROM sys_menu
    WHERE menu_id <> 9600
      AND FIND_IN_SET('drive:access', REPLACE(COALESCE(perms, ''), ' ', '')) > 0
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cloud drive access permission is assigned to another menu id';
  END IF;

  IF EXISTS (
    SELECT 1 FROM sys_menu WHERE menu_id = 9601 AND COALESCE(perms, '') <> 'drive:company:manage'
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'menu id 9601 is occupied by another permission';
  END IF;

  IF EXISTS (
    SELECT 1 FROM sys_menu WHERE menu_id = 9602 AND COALESCE(perms, '') <> 'drive:department:manage'
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'menu id 9602 is occupied by another permission';
  END IF;

  IF EXISTS (
    SELECT 1 FROM sys_menu WHERE menu_id = 9603 AND COALESCE(perms, '') <> 'drive:quota:manage'
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'menu id 9603 is occupied by another permission';
  END IF;

  IF EXISTS (
    SELECT 1 FROM sys_menu
    WHERE menu_id NOT IN (9601, 9602, 9603)
      AND (
        FIND_IN_SET('drive:company:manage', REPLACE(COALESCE(perms, ''), ' ', '')) > 0
        OR FIND_IN_SET('drive:department:manage', REPLACE(COALESCE(perms, ''), ' ', '')) > 0
        OR FIND_IN_SET('drive:quota:manage', REPLACE(COALESCE(perms, ''), ' ', '')) > 0
      )
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cloud drive management permission is assigned to another menu id';
  END IF;

  SELECT COUNT(*) INTO v_drive_exists FROM sys_menu WHERE menu_id = 9600;
  IF v_drive_exists = 0 THEN
    UPDATE sys_menu
    SET order_num = order_num + 1,
        update_by = 'system',
        update_time = NOW()
    WHERE parent_id = 0 AND order_num > v_oa_order;
  END IF;

  INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
  VALUES
    (9600, '云盘', 0, v_oa_order + 1, 'drive', 'drive/index', NULL, 'CloudDrive',
     1, 0, 'C', '0', '0', 'drive:access', 'cloud-drive',
     'system', NOW(), '企业云盘入口')
  ON DUPLICATE KEY UPDATE
    menu_name = VALUES(menu_name),
    parent_id = VALUES(parent_id),
    order_num = VALUES(order_num),
    path = VALUES(path),
    component = VALUES(component),
    query = VALUES(query),
    route_name = VALUES(route_name),
    is_frame = VALUES(is_frame),
    is_cache = VALUES(is_cache),
    menu_type = VALUES(menu_type),
    visible = VALUES(visible),
    status = VALUES(status),
    perms = VALUES(perms),
    icon = VALUES(icon),
    update_by = 'system',
    update_time = NOW(),
    remark = VALUES(remark);

  INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
  VALUES
    (9601, '公司盘管理', 9600, 1, '', NULL, NULL, '',
     1, 0, 'F', '0', '0', 'drive:company:manage', '#', 'system', NOW(), ''),
    (9602, '部门盘管理', 9600, 2, '', NULL, NULL, '',
     1, 0, 'F', '0', '0', 'drive:department:manage', '#', 'system', NOW(), ''),
    (9603, '云盘额度管理', 9600, 3, '', NULL, NULL, '',
     1, 0, 'F', '0', '0', 'drive:quota:manage', '#', 'system', NOW(), '')
  ON DUPLICATE KEY UPDATE
    menu_name = VALUES(menu_name),
    parent_id = VALUES(parent_id),
    order_num = VALUES(order_num),
    path = VALUES(path),
    component = VALUES(component),
    query = VALUES(query),
    route_name = VALUES(route_name),
    is_frame = VALUES(is_frame),
    is_cache = VALUES(is_cache),
    menu_type = VALUES(menu_type),
    visible = VALUES(visible),
    status = VALUES(status),
    perms = VALUES(perms),
    icon = VALUES(icon),
    update_by = 'system',
    update_time = NOW();

  INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
  SELECT role.role_id, menu_ids.menu_id
  FROM sys_role role
  JOIN (
    SELECT 9600 AS menu_id
    UNION ALL SELECT 9601
    UNION ALL SELECT 9602
    UNION ALL SELECT 9603
  ) menu_ids ON 1 = 1
  WHERE role.role_key = 'admin'
    AND role.del_flag = '0'
    AND role.status = '0';
END$$
DELIMITER ;

CALL migrate_cloud_drive_menu();
DROP PROCEDURE IF EXISTS migrate_cloud_drive_menu;

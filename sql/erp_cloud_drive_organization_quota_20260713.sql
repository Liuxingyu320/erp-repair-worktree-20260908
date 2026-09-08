-- Cloud-drive organization provisioning and quota policy control plane.
-- Idempotent, additive and MySQL 5.7 compatible. Apply after erp_cloud_drive_20260711.sql.

SET @drive_schema = DATABASE();

SET @drive_ddl = IF((
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @drive_schema AND TABLE_NAME = 'drive_space'
    AND COLUMN_NAME = 'quota_source_type'
) = 0,
  'ALTER TABLE drive_space ADD COLUMN quota_source_type varchar(24) NOT NULL DEFAULT ''LEGACY'' COMMENT ''额度来源类型'' AFTER used_bytes',
  'DO 0');
PREPARE drive_stmt FROM @drive_ddl; EXECUTE drive_stmt; DEALLOCATE PREPARE drive_stmt;

SET @drive_ddl = IF((
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @drive_schema AND TABLE_NAME = 'drive_space'
    AND COLUMN_NAME = 'quota_source_id'
) = 0,
  'ALTER TABLE drive_space ADD COLUMN quota_source_id bigint DEFAULT NULL COMMENT ''额度来源对象'' AFTER quota_source_type',
  'DO 0');
PREPARE drive_stmt FROM @drive_ddl; EXECUTE drive_stmt; DEALLOCATE PREPARE drive_stmt;

SET @drive_ddl = IF((
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @drive_schema AND TABLE_NAME = 'drive_space'
    AND COLUMN_NAME = 'quota_synced_time'
) = 0,
  'ALTER TABLE drive_space ADD COLUMN quota_synced_time datetime DEFAULT NULL COMMENT ''额度最后对账时间'' AFTER quota_source_id',
  'DO 0');
PREPARE drive_stmt FROM @drive_ddl; EXECUTE drive_stmt; DEALLOCATE PREPARE drive_stmt;

CREATE TABLE IF NOT EXISTS drive_personal_quota_policy (
  policy_id bigint NOT NULL AUTO_INCREMENT,
  subject_type varchar(16) NOT NULL,
  subject_id bigint NOT NULL DEFAULT 0,
  quota_bytes bigint NOT NULL,
  priority int NOT NULL DEFAULT 0,
  expire_time datetime DEFAULT NULL,
  status varchar(16) NOT NULL DEFAULT 'ACTIVE',
  version int NOT NULL DEFAULT 0,
  create_by varchar(64) DEFAULT '',
  create_time datetime DEFAULT NULL,
  update_by varchar(64) DEFAULT '',
  update_time datetime DEFAULT NULL,
  remark varchar(500) DEFAULT NULL,
  PRIMARY KEY (policy_id),
  UNIQUE KEY uk_drive_personal_quota_subject (subject_type, subject_id),
  KEY idx_drive_personal_quota_active (status, expire_time),
  KEY idx_drive_personal_quota_subject (subject_id, subject_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='云盘个人额度策略';

CREATE TABLE IF NOT EXISTS drive_org_type_rule (
  dept_type varchar(20) NOT NULL,
  auto_enable tinyint NOT NULL DEFAULT 0,
  require_active_member tinyint NOT NULL DEFAULT 1,
  default_quota_bytes bigint NOT NULL,
  status varchar(16) NOT NULL DEFAULT 'ACTIVE',
  version int NOT NULL DEFAULT 0,
  create_by varchar(64) DEFAULT '',
  create_time datetime DEFAULT NULL,
  update_by varchar(64) DEFAULT '',
  update_time datetime DEFAULT NULL,
  remark varchar(500) DEFAULT NULL,
  PRIMARY KEY (dept_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='云盘组织类型建盘规则';

CREATE TABLE IF NOT EXISTS drive_org_space_config (
  config_id bigint NOT NULL AUTO_INCREMENT,
  dept_id bigint NOT NULL,
  enabled tinyint NOT NULL DEFAULT 0,
  quota_bytes bigint NOT NULL,
  tree_budget_bytes bigint DEFAULT NULL,
  member_write_mode varchar(24) NOT NULL DEFAULT 'PERMISSION_ONLY',
  lifecycle_status varchar(16) NOT NULL DEFAULT 'ACTIVE',
  config_source varchar(16) NOT NULL DEFAULT 'MANUAL',
  version int NOT NULL DEFAULT 0,
  create_by varchar(64) DEFAULT '',
  create_time datetime DEFAULT NULL,
  update_by varchar(64) DEFAULT '',
  update_time datetime DEFAULT NULL,
  remark varchar(500) DEFAULT NULL,
  PRIMARY KEY (config_id),
  UNIQUE KEY uk_drive_org_space_dept (dept_id),
  KEY idx_drive_org_space_state (enabled, lifecycle_status),
  KEY idx_drive_org_space_source (config_source, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='云盘组织空间配置';

CREATE TABLE IF NOT EXISTS drive_capacity_config (
  config_id bigint NOT NULL,
  physical_capacity_bytes bigint DEFAULT NULL,
  reserve_percent int NOT NULL DEFAULT 20,
  public_pool_bytes bigint NOT NULL DEFAULT 0,
  personal_pool_bytes bigint NOT NULL DEFAULT 0,
  organization_pool_bytes bigint NOT NULL DEFAULT 0,
  enforcement_mode varchar(16) NOT NULL DEFAULT 'WARN',
  version int NOT NULL DEFAULT 0,
  create_by varchar(64) DEFAULT '',
  create_time datetime DEFAULT NULL,
  update_by varchar(64) DEFAULT '',
  update_time datetime DEFAULT NULL,
  remark varchar(500) DEFAULT NULL,
  PRIMARY KEY (config_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='云盘全局容量配置';

CREATE TABLE IF NOT EXISTS drive_upload_reservation (
  reservation_id varchar(36) NOT NULL,
  space_id bigint NOT NULL,
  storage_key varchar(500) NOT NULL,
  reserved_bytes bigint NOT NULL,
  status varchar(20) NOT NULL DEFAULT 'RESERVED',
  expire_time datetime NOT NULL,
  retry_count int NOT NULL DEFAULT 0,
  next_retry_time datetime DEFAULT NULL,
  version int NOT NULL DEFAULT 0,
  create_by varchar(64) DEFAULT '',
  create_time datetime NOT NULL,
  update_by varchar(64) DEFAULT '',
  update_time datetime NOT NULL,
  last_error_code varchar(64) DEFAULT NULL,
  PRIMARY KEY (reservation_id),
  UNIQUE KEY uk_drive_upload_reservation_storage (storage_key),
  KEY idx_drive_upload_reservation_cleanup (status, expire_time, next_retry_time, reservation_id),
  KEY idx_drive_upload_reservation_space (space_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='云盘上传容量预占';

-- Preserve the deployment's most common existing personal quota as the global default.
INSERT INTO drive_personal_quota_policy (
  subject_type, subject_id, quota_bytes, priority, status,
  version, create_by, create_time, remark
)
SELECT 'GLOBAL', 0,
       COALESCE((
         SELECT quota_mode.quota_bytes
         FROM (
           SELECT quota_bytes, COUNT(*) AS quota_count
           FROM drive_space
           WHERE space_type = 'PERSONAL'
           GROUP BY quota_bytes
           ORDER BY quota_count DESC, quota_bytes DESC
           LIMIT 1
         ) quota_mode
       ), 2147483648),
       0, 'ACTIVE', 0, 'system', NOW(), 'migrated global personal quota'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM drive_personal_quota_policy
  WHERE subject_type = 'GLOBAL' AND subject_id = 0
);

-- Keep every existing non-default personal space unchanged by creating an explicit override.
INSERT IGNORE INTO drive_personal_quota_policy (
  subject_type, subject_id, quota_bytes, priority, status,
  version, create_by, create_time, remark
)
SELECT 'USER', s.owner_user_id, s.quota_bytes, 0, 'ACTIVE',
       0, 'system', NOW(), 'migrated personal space override'
FROM drive_space s
JOIN drive_personal_quota_policy global_policy
  ON global_policy.subject_type = 'GLOBAL'
 AND global_policy.subject_id = 0
WHERE s.space_type = 'PERSONAL'
  AND s.owner_user_id IS NOT NULL
  AND s.quota_bytes <> global_policy.quota_bytes;

-- Recommended defaults are recorded but automatic provisioning remains disabled until capacity review.
INSERT IGNORE INTO drive_org_type_rule (
  dept_type, auto_enable, require_active_member, default_quota_bytes,
  status, version, create_by, create_time, remark
) VALUES
  ('GROUP', 0, 1, 21474836480, 'ACTIVE', 0, 'system', NOW(), 'group disks opt in'),
  ('COMPANY', 0, 1, 42949672960, 'ACTIVE', 0, 'system', NOW(), 'recommended 40 GiB; enable after review'),
  ('STORE', 0, 1, 10737418240, 'ACTIVE', 0, 'system', NOW(), 'recommended 10 GiB; enable after review'),
  ('WAREHOUSE', 0, 1, 21474836480, 'ACTIVE', 0, 'system', NOW(), 'warehouse disks opt in');

-- Existing organization spaces become explicit migrated configurations without changing quota or status.
INSERT IGNORE INTO drive_org_space_config (
  dept_id, enabled, quota_bytes, tree_budget_bytes, member_write_mode,
  lifecycle_status, config_source, version, create_by, create_time, remark
)
SELECT s.dept_id, 1, s.quota_bytes, NULL, 'PERMISSION_ONLY',
       CASE WHEN s.status = 'ACTIVE' THEN 'ACTIVE' ELSE 'READ_ONLY' END,
       'MIGRATED', 0, 'system', NOW(), 'migrated existing organization space'
FROM drive_space s
WHERE s.space_type = 'DEPARTMENT'
  AND s.dept_id IS NOT NULL;

-- Seed a warning-only capacity view from today's allocations; physical capacity remains operator supplied.
INSERT INTO drive_capacity_config (
  config_id, physical_capacity_bytes, reserve_percent,
  public_pool_bytes, personal_pool_bytes, organization_pool_bytes,
  enforcement_mode, version, create_by, create_time, remark
)
SELECT 1, NULL, 20,
       COALESCE((SELECT quota_bytes FROM drive_space WHERE space_key = 'COMPANY:ROOT' LIMIT 1), 0),
       COALESCE((
         SELECT SUM(COALESCE(user_policy.quota_bytes, global_policy.quota_bytes))
         FROM sys_user active_user
         JOIN drive_personal_quota_policy global_policy
           ON global_policy.subject_type = 'GLOBAL'
          AND global_policy.subject_id = 0
         LEFT JOIN drive_personal_quota_policy user_policy
           ON user_policy.subject_type = 'USER'
          AND user_policy.subject_id = active_user.user_id
          AND user_policy.status = 'ACTIVE'
          AND (user_policy.expire_time IS NULL OR user_policy.expire_time > NOW())
         WHERE active_user.status = '0' AND active_user.del_flag = '0'
       ), 0),
       COALESCE((SELECT SUM(quota_bytes) FROM drive_org_space_config WHERE enabled = 1), 0),
       'WARN', 0, 'system', NOW(), 'physical capacity must be confirmed before BLOCK mode'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM drive_capacity_config WHERE config_id = 1);

UPDATE drive_space s
LEFT JOIN drive_personal_quota_policy user_policy
  ON user_policy.subject_type = 'USER'
 AND user_policy.subject_id = s.owner_user_id
SET s.quota_source_type = CASE
      WHEN s.space_type = 'COMPANY' THEN 'COMPANY'
      WHEN s.space_type = 'DEPARTMENT' THEN 'ORG_OVERRIDE'
      WHEN s.space_type = 'PERSONAL' AND user_policy.policy_id IS NOT NULL THEN 'USER'
      WHEN s.space_type = 'PERSONAL' THEN 'GLOBAL'
      ELSE 'LEGACY'
    END,
    s.quota_source_id = CASE
      WHEN s.space_type = 'DEPARTMENT' THEN s.dept_id
      WHEN s.space_type = 'PERSONAL' AND user_policy.policy_id IS NOT NULL THEN s.owner_user_id
      ELSE NULL
    END,
    s.quota_synced_time = COALESCE(s.quota_synced_time, NOW())
WHERE s.quota_source_type = 'LEGACY' OR s.quota_synced_time IS NULL;

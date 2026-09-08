-- 调拨单创建人/最近提交人身份快照。
-- MySQL 5.7/8.0 兼容，可重复执行；只新增可空列并安全回填，不删除业务数据。

SET @erp_db := DATABASE();

SET @sql := IF(NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema=@erp_db AND table_name='inv_transfer_order'
      AND column_name='created_by_user_id'),
    'ALTER TABLE inv_transfer_order ADD COLUMN created_by_user_id bigint(20) DEFAULT NULL COMMENT ''创建人用户ID快照'' AFTER create_by',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema=@erp_db AND table_name='inv_transfer_order'
      AND column_name='created_by_name'),
    'ALTER TABLE inv_transfer_order ADD COLUMN created_by_name varchar(64) DEFAULT NULL COMMENT ''创建人姓名快照'' AFTER created_by_user_id',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema=@erp_db AND table_name='inv_transfer_order'
      AND column_name='submitted_by_user_id'),
    'ALTER TABLE inv_transfer_order ADD COLUMN submitted_by_user_id bigint(20) DEFAULT NULL COMMENT ''最近提交人用户ID快照'' AFTER submitted_time',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema=@erp_db AND table_name='inv_transfer_order'
      AND column_name='submitted_by_name'),
    'ALTER TABLE inv_transfer_order ADD COLUMN submitted_by_name varchar(64) DEFAULT NULL COMMENT ''最近提交人姓名快照'' AFTER submitted_by_user_id',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 创建人按历史技术账号精确关联；找不到账号或昵称为空时，姓名保持NULL。
UPDATE inv_transfer_order o
LEFT JOIN sys_user u ON CONVERT(u.user_name USING utf8mb4) COLLATE utf8mb4_general_ci
    = CONVERT(o.create_by USING utf8mb4) COLLATE utf8mb4_general_ci
SET o.created_by_user_id = COALESCE(o.created_by_user_id, u.user_id),
    o.created_by_name = CASE
        WHEN NULLIF(TRIM(o.created_by_name), '') IS NOT NULL
            THEN o.created_by_name
        WHEN NULLIF(TRIM(u.nick_name), '') IS NOT NULL
            THEN TRIM(u.nick_name)
        ELSE NULL
    END
WHERE o.created_by_user_id IS NULL
   OR NULLIF(TRIM(o.created_by_name), '') IS NULL;

-- 取每单最近一次 submit/auto_approve；同一时间以更大的日志ID作为最后记录。
UPDATE inv_transfer_order o
JOIN (
    SELECT latest.transfer_id, latest.operator_id
    FROM inv_transfer_status_log latest
    LEFT JOIN inv_transfer_status_log newer
      ON newer.transfer_id = latest.transfer_id
     AND newer.action IN ('submit', 'auto_approve')
     AND (newer.create_time > latest.create_time
          OR (newer.create_time = latest.create_time
              AND newer.log_id > latest.log_id))
    WHERE latest.action IN ('submit', 'auto_approve')
      AND newer.log_id IS NULL
) last_submit ON last_submit.transfer_id = o.transfer_id
JOIN sys_user u ON u.user_id = last_submit.operator_id
SET o.submitted_by_user_id = COALESCE(o.submitted_by_user_id, u.user_id),
    o.submitted_by_name = CASE
        WHEN NULLIF(TRIM(o.submitted_by_name), '') IS NOT NULL
            THEN o.submitted_by_name
        WHEN NULLIF(TRIM(u.nick_name), '') IS NOT NULL
            THEN TRIM(u.nick_name)
        ELSE NULL
    END
WHERE o.submitted_by_user_id IS NULL
   OR NULLIF(TRIM(o.submitted_by_name), '') IS NULL;

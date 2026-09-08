-- OA固定资产补货可靠发件箱、额度并发保护配套和异常批准下线。
-- MySQL 5.7，可重复执行；历史pending_confirm记录仅保留只读。

CREATE TABLE IF NOT EXISTS oa_fixed_asset_replenishment_outbox (
    outbox_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '发件箱ID',
    repair_id bigint(20) NOT NULL COMMENT '成功上报维修记录ID',
    event_version bigint(20) NOT NULL DEFAULT 1 COMMENT '事件版本',
    payload_json longtext NOT NULL COMMENT '补货事件JSON快照',
    status varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENDING/RETRY/SENT/DEAD',
    retry_count int NOT NULL DEFAULT 0 COMMENT '重试次数',
    next_retry_time datetime DEFAULT NULL COMMENT '下次重试时间',
    last_http_status int DEFAULT NULL COMMENT '最近远程状态码',
    last_error_code varchar(64) DEFAULT NULL COMMENT '稳定错误码',
    target_demand_id bigint(20) DEFAULT NULL COMMENT '库存补货需求ID',
    version bigint(20) NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    sent_time datetime DEFAULT NULL COMMENT '成功投递时间',
    manual_replay_count int NOT NULL DEFAULT 0 COMMENT '人工重投次数',
    manual_replay_by varchar(64) DEFAULT NULL COMMENT '最近人工重投人',
    manual_replay_time datetime DEFAULT NULL COMMENT '最近人工重投时间',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (outbox_id),
    UNIQUE KEY uk_oa_fixed_asset_outbox_repair (repair_id),
    KEY idx_oa_fixed_asset_outbox_due (status, next_retry_time, outbox_id),
    KEY idx_oa_fixed_asset_outbox_stale (status, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='固定资产成功上报补货可靠发件箱';

SET @erp_db := DATABASE();
SET @sql := IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = @erp_db
          AND table_name = 'oa_fixed_asset_replenishment_outbox'
          AND column_name = 'manual_replay_count'
    ),
    'ALTER TABLE oa_fixed_asset_replenishment_outbox ADD COLUMN manual_replay_count int NOT NULL DEFAULT 0 COMMENT ''人工重投次数'' AFTER sent_time',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = @erp_db
          AND table_name = 'oa_fixed_asset_replenishment_outbox'
          AND column_name = 'manual_replay_by'
    ),
    'ALTER TABLE oa_fixed_asset_replenishment_outbox ADD COLUMN manual_replay_by varchar(64) DEFAULT NULL COMMENT ''最近人工重投人'' AFTER manual_replay_count',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = @erp_db
          AND table_name = 'oa_fixed_asset_replenishment_outbox'
          AND column_name = 'manual_replay_time'
    ),
    'ALTER TABLE oa_fixed_asset_replenishment_outbox ADD COLUMN manual_replay_time datetime DEFAULT NULL COMMENT ''最近人工重投时间'' AFTER manual_replay_by',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @erp_db := DATABASE();
SET @sql := IF(
    EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = @erp_db AND table_name = 'oa_fixed_asset_quota'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = @erp_db AND table_name = 'oa_fixed_asset_quota'
          AND index_name = 'uk_oa_fixed_asset_quota_shop_year'
    ),
    'ALTER TABLE oa_fixed_asset_quota ADD UNIQUE KEY uk_oa_fixed_asset_quota_shop_year (shop_dept_id, quota_year)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO sys_config
    (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT 'OE成功上报补货开关', 'feature.oa.oe-replenishment.enabled', 'false', 'Y',
       'system', NOW(), '关闭新事件写入前必须先清空在途发件箱；已生成事件继续重试'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'feature.oa.oe-replenishment.enabled');

SET @fixed_asset_config_menu_id := (
    SELECT menu_id FROM sys_menu
    WHERE perms = 'oa:fixedAsset:config:list'
    ORDER BY menu_id LIMIT 1
);

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT next_id, 'OE补货死信查看', @fixed_asset_config_menu_id, 90,
       '', NULL, NULL, '', '1', '0', 'F', '0', '0',
       'oa:fixedAsset:outbox:list', '#', 'system', NOW(),
       '只返回安全摘要，不暴露发件箱原始载荷'
FROM (SELECT COALESCE(MAX(menu_id), 0) + 1 next_id FROM sys_menu) sequence
WHERE @fixed_asset_config_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'oa:fixedAsset:outbox:list');

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT next_id, 'OE补货死信重投', @fixed_asset_config_menu_id, 91,
       '', NULL, NULL, '', '1', '0', 'F', '0', '0',
       'oa:fixedAsset:outbox:replay', '#', 'system', NOW(),
       '管理员修复原因后人工重投，保留操作人和次数'
FROM (SELECT COALESCE(MAX(menu_id), 0) + 1 next_id FROM sys_menu) sequence
WHERE @fixed_asset_config_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'oa:fixedAsset:outbox:replay');

UPDATE sys_menu
SET menu_name = 'OE补货投递运维', update_by = 'system', update_time = NOW(),
    remark = '安全摘要查看PENDING/RETRY/SENDING/DEAD状态、最老等待和失败原因'
WHERE perms = 'oa:fixedAsset:outbox:list';

UPDATE sys_menu
SET menu_name = 'OE补货投递立即重试', update_by = 'system', update_time = NOW(),
    remark = '仅允许PENDING/RETRY/DEAD基于乐观锁立即重试，保留操作人和次数'
WHERE perms = 'oa:fixedAsset:outbox:replay';

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
JOIN sys_menu m ON m.perms IN (
    'oa:fixedAsset:outbox:list', 'oa:fixedAsset:outbox:replay'
)
WHERE r.status = '0' AND r.del_flag = '0'
  AND (r.role_id = 1 OR lower(r.role_key) = 'admin');

-- 异常批准入口和权限彻底下线，历史记录不删除。
DELETE rm
FROM sys_role_menu rm
JOIN sys_menu m ON m.menu_id = rm.menu_id
WHERE m.perms IN ('oa:fixedAsset:config:approve', 'oa:fixedAsset:repair:confirm');

UPDATE sys_menu
SET visible = '1', status = '1', update_by = 'system', update_time = NOW(),
    remark = '20260713超额仅允许自行购买；异常批准已下线，历史数据保留只读'
WHERE perms IN ('oa:fixedAsset:config:approve', 'oa:fixedAsset:repair:confirm');

-- 上线前一次性处置清单（只读）。
SELECT repair_id, shop_dept_id, oe_item_id, repair_quantity, exception_type,
       status, applicant_id, applicant_name, create_time
FROM oa_fixed_asset_repair
WHERE status = 'pending_confirm'
ORDER BY create_time, repair_id;

-- 上线后对账：成功上报应且仅应有一个发件箱事实。
SELECT r.repair_id, r.shop_dept_id, r.status,
       o.outbox_id, o.status outbox_status, o.retry_count,
       o.target_demand_id, o.last_error_code
FROM oa_fixed_asset_repair r
LEFT JOIN oa_fixed_asset_replenishment_outbox o ON o.repair_id = r.repair_id
WHERE r.status = 'submitted'
ORDER BY r.repair_id DESC
LIMIT 200;

SELECT status, COUNT(*) row_count, MIN(next_retry_time) earliest_retry
FROM oa_fixed_asset_replenishment_outbox
GROUP BY status ORDER BY status;

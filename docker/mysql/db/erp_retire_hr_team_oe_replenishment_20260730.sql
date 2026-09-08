-- 退役“我的团队”和独立 OE 补货链路（MySQL 5.7，可重复执行）。
--
-- 产品决定：
-- 1. “我的团队”不再需要；员工档案、健康证和组织关系继续保留。
-- 2. OE 属于固定资产，只走固定资产上报通道；OE 主数据和固定资产记录继续保留。
-- 3. 不删除发件箱、补货需求、关联调拨或历史业务行，仅终止尚未完成的独立补货队列。

START TRANSACTION;

-- 先撤销角色授权，再删除退役页面下的所有直接子菜单。
DELETE role_menu
FROM sys_role_menu role_menu
JOIN sys_menu child_menu ON child_menu.menu_id = role_menu.menu_id
JOIN sys_menu page_menu ON page_menu.menu_id = child_menu.parent_id
WHERE page_menu.component IN ('hr/team/index', 'inventory/oeReplenishment/index')
   OR page_menu.perms IN ('hr:team:list', 'inv:oeReplenishment:list');

DELETE child_menu
FROM sys_menu child_menu
JOIN sys_menu page_menu ON page_menu.menu_id = child_menu.parent_id
WHERE page_menu.component IN ('hr/team/index', 'inventory/oeReplenishment/index')
   OR page_menu.perms IN ('hr:team:list', 'inv:oeReplenishment:list');

-- 清除页面本身、独立补货运维按钮及其角色授权。
DELETE role_menu
FROM sys_role_menu role_menu
JOIN sys_menu menu_item ON menu_item.menu_id = role_menu.menu_id
WHERE menu_item.component IN ('hr/team/index', 'inventory/oeReplenishment/index')
   OR menu_item.route_name IN ('HrMyTeam', 'InvOeReplenishment')
   OR menu_item.perms IN (
       'hr:team:list',
       'hr:team:store',
       'hr:team:direct',
       'hr:team:healthCertificate',
       'inv:oeReplenishment:list',
       'inv:oeReplenishment:query',
       'inv:oeReplenishment:handle',
       'inv:oeReplenishment:close',
       'oa:fixedAsset:outbox:list',
       'oa:fixedAsset:outbox:replay'
   );

DELETE FROM sys_menu
WHERE component IN ('hr/team/index', 'inventory/oeReplenishment/index')
   OR route_name IN ('HrMyTeam', 'InvOeReplenishment')
   OR perms IN (
       'hr:team:list',
       'hr:team:store',
       'hr:team:direct',
       'hr:team:healthCertificate',
       'inv:oeReplenishment:list',
       'inv:oeReplenishment:query',
       'inv:oeReplenishment:handle',
       'inv:oeReplenishment:close',
       'oa:fixedAsset:outbox:list',
       'oa:fixedAsset:outbox:replay'
   );

-- 新版本不再读取这些开关；旧版本节点缺少配置时也默认关闭。
DELETE FROM sys_config
WHERE config_key IN (
    'feature.hr.team.enabled',
    'feature.inventory.oe-replenishment.enabled',
    'feature.oa.oe-replenishment.enabled'
);

COMMIT;

-- 历史表可能不存在，使用动态语句保证迁移可重复执行。
SET @erp_retire_db := DATABASE();
SET @erp_retire_sql := IF(
    EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = @erp_retire_db
          AND table_name = 'oa_fixed_asset_replenishment_outbox'
    ),
    'UPDATE oa_fixed_asset_replenishment_outbox
        SET status = ''DEAD'',
            next_retry_time = NULL,
            last_http_status = NULL,
            last_error_code = ''FEATURE_RETIRED'',
            version = version + 1,
            update_time = NOW()
      WHERE status IN (''PENDING'', ''SENDING'', ''RETRY'')',
    'SELECT ''oa_fixed_asset_replenishment_outbox not installed'' AS retirement_info'
);
PREPARE erp_retire_stmt FROM @erp_retire_sql;
EXECUTE erp_retire_stmt;
DEALLOCATE PREPARE erp_retire_stmt;

SET @erp_retire_sql := IF(
    EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = @erp_retire_db
          AND table_name = 'inv_oe_replenishment_demand'
    ),
    'UPDATE inv_oe_replenishment_demand
            SET status = ''CLOSED'',
                close_reason = CASE
                    WHEN NULLIF(TRIM(close_reason), '''') IS NULL
                        THEN ''2026-07-30独立OE补货链路退役；OE固定资产改走固定资产上报通道''
                    ELSE LEFT(CONCAT(close_reason, ''；2026-07-30独立OE补货链路退役；OE固定资产改走固定资产上报通道''), 500)
                END,
                update_by = ''system'',
                update_time = NOW(),
                version = version + 1
          WHERE status IN (
              ''PENDING'', ''TRANSFER_CREATED'', ''PARTIAL_SHIPPED'',
              ''SHIPPED'', ''PARTIAL_RECEIVED'', ''DISCREPANCY''
          )',
    'SELECT ''inv_oe_replenishment_demand not installed'' AS retirement_info'
);
PREPARE erp_retire_stmt FROM @erp_retire_sql;
EXECUTE erp_retire_stmt;
DEALLOCATE PREPARE erp_retire_stmt;

-- 明确禁止误删审计历史：本迁移不包含 DROP/TRUNCATE，也不删除上述历史表中的任何业务行。

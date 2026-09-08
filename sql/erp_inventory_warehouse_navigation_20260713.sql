-- 仓储作业导航归并（MySQL 8）
-- 目标：保留“进销存管理”处理销售等业务单据；仓库执行动作统一进入“仓储作业”。
-- 本脚本不删除菜单，旧重复路由仅停用；角色菜单原值写入迁移备份表，便于精确回滚。

CREATE TABLE IF NOT EXISTS sys_role_menu_warehouse_nav_backup_20260713 (
    role_id bigint NOT NULL,
    menu_id bigint NOT NULL,
    backup_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_id, menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='20260713仓储导航迁移角色菜单备份';

CREATE TABLE IF NOT EXISTS sys_menu_warehouse_nav_backup_20260713 (
    menu_id bigint NOT NULL COMMENT '菜单ID',
    menu_name varchar(50) NOT NULL,
    parent_id bigint DEFAULT 0,
    order_num int DEFAULT 0,
    path varchar(200) DEFAULT '',
    component varchar(255) DEFAULT NULL,
    query varchar(255) DEFAULT NULL,
    route_name varchar(50) DEFAULT '',
    visible char(1) DEFAULT '0',
    status char(1) DEFAULT '0',
    icon varchar(100) DEFAULT '#',
    update_by varchar(64) DEFAULT '',
    update_time datetime DEFAULT NULL,
    remark varchar(500) DEFAULT '',
    backup_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='20260713仓储导航迁移菜单原值备份';

-- 菜单快照和角色授权快照必须在同一事务内一次性冻结。
-- 后续重试不得把本迁移新增的授权反向写入“发布前”快照。
START TRANSACTION;

SET @warehouse_nav_snapshot_exists := (
    SELECT CASE WHEN COUNT(*) > 0 THEN 1 ELSE 0 END
    FROM sys_menu_warehouse_nav_backup_20260713
);

INSERT IGNORE INTO sys_menu_warehouse_nav_backup_20260713 (
    menu_id, menu_name, parent_id, order_num, path, component, query,
    route_name, visible, status, icon, update_by, update_time, remark
)
SELECT menu_id, menu_name, parent_id, order_num, path, component, query,
       route_name, visible, status, icon, update_by, update_time, remark
FROM sys_menu
WHERE menu_id IN (
    4000, 4040, 4041, 4042, 4043, 4044,
    4100, 4101, 4102, 4103, 4104, 4105, 4106, 4107, 4108, 4109,
    4300, 4301, 4302, 4303, 4304, 4305, 4306, 4307, 4308,
    4400, 4401, 4402, 4403,
    4450, 4451, 4452, 4453, 4454,
    4460, 4461, 4462, 4463, 4465, 4466, 4467,
    4470, 4471
)
  AND @warehouse_nav_snapshot_exists = 0;

INSERT IGNORE INTO sys_role_menu_warehouse_nav_backup_20260713 (role_id, menu_id)
SELECT role_id, menu_id
FROM sys_role_menu
WHERE menu_id IN (
    4000, 4040, 4041, 4042, 4043, 4044,
    4100, 4101, 4102, 4103, 4104, 4105, 4106, 4107, 4108, 4109,
    4300, 4301, 4302, 4303, 4304, 4305, 4306, 4307, 4308,
    4400, 4401, 4402, 4403,
    4450, 4451, 4452, 4453, 4454,
    4460, 4461, 4462, 4463, 4465, 4466, 4467,
    4470, 4471
)
  AND @warehouse_nav_snapshot_exists = 0;

COMMIT;

START TRANSACTION;

UPDATE sys_menu
SET menu_name = '仓储作业',
    order_num = 4,
    icon = 'cascader',
    visible = '0',
    status = '0',
    update_by = 'system',
    update_time = NOW(),
    remark = '仓库、门店库存执行作业统一入口'
WHERE menu_id = 4308;

-- 门店库存与仓库库存是两个明确上下文，不再使用两个顶级菜单表达同一页面。
UPDATE sys_menu
SET menu_name = '门店库存',
    parent_id = 4308,
    order_num = 5,
    path = 'store-stock',
    component = 'inventory/stock/index',
    query = '{"stockEntry":"store"}',
    route_name = 'StoreStock',
    visible = '0',
    status = '0',
    update_by = 'system',
    update_time = NOW(),
    remark = '仓储作业-门店库存入口'
WHERE menu_id = 4040;

UPDATE sys_menu
SET menu_name = '仓库库存',
    parent_id = 4308,
    order_num = 6,
    path = 'warehouse-stock',
    query = '{"stockEntry":"warehouse"}',
    route_name = 'WarehouseStock',
    visible = '0',
    status = '0',
    update_by = 'system',
    update_time = NOW(),
    remark = '仓储作业-仓库库存入口'
WHERE menu_id = 4450;

-- 调拨和调拨记录保留一套可见路由；原权限子菜单挂到新路由，避免权限能力缩水。
UPDATE sys_menu
SET menu_name = '调拨作业',
    parent_id = 4308,
    order_num = 7,
    path = 'transfer',
    component = 'inventory/transfer/index',
    route_name = 'WarehouseTransfer',
    visible = '0',
    status = '0',
    update_by = 'system',
    update_time = NOW(),
    remark = '门店要货、审批、仓库发货和门店收货统一入口'
WHERE menu_id = 4460;

UPDATE sys_menu
SET parent_id = 4460,
    visible = '0',
    status = '0',
    update_by = 'system',
    update_time = NOW()
WHERE menu_id BETWEEN 4101 AND 4109;

UPDATE sys_menu
SET visible = '1', status = '1', update_by = 'system', update_time = NOW(),
    remark = '已由仓储作业-调拨作业替代，保留用于回滚'
WHERE menu_id = 4100;

UPDATE sys_menu
SET visible = '1', status = '1', update_by = 'system', update_time = NOW(),
    remark = '重复权限菜单，已由4101-4109替代'
WHERE menu_id IN (4461, 4462, 4463);

UPDATE sys_menu
SET menu_name = '调拨记录',
    parent_id = 4308,
    order_num = 8,
    path = 'transfer-records',
    component = 'inventory/transfer/records',
    route_name = 'WarehouseTransferRecords',
    visible = '0',
    status = '0',
    update_by = 'system',
    update_time = NOW(),
    remark = '仓储作业-调拨记录统一入口'
WHERE menu_id = 4465;

UPDATE sys_menu
SET parent_id = 4465,
    visible = '0',
    status = '0',
    update_by = 'system',
    update_time = NOW()
WHERE menu_id IN (4401, 4402, 4403);

UPDATE sys_menu
SET visible = '1', status = '1', update_by = 'system', update_time = NOW(),
    remark = '已由仓储作业-调拨记录替代，保留用于回滚'
WHERE menu_id = 4400;

UPDATE sys_menu
SET visible = '1', status = '1', update_by = 'system', update_time = NOW(),
    remark = '重复权限菜单，已由4401-4403替代'
WHERE menu_id IN (4466, 4467);

UPDATE sys_menu
SET parent_id = 4308,
    order_num = 9,
    path = 'stock-check',
    route_name = 'WarehouseStockCheck',
    visible = '0',
    status = '0',
    update_by = 'system',
    update_time = NOW(),
    remark = '仓储作业-库存盘点入口'
WHERE menu_id = 4300;

UPDATE sys_menu
SET parent_id = 4308,
    order_num = 10,
    path = 'report',
    route_name = 'WarehouseInventoryReport',
    visible = '0',
    status = '0',
    update_by = 'system',
    update_time = NOW(),
    remark = '仓储作业-库存报表入口'
WHERE menu_id = 4470;

-- 把原调拨/记录入口的角色授权映射到统一路由。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, 4460 FROM sys_role_menu WHERE menu_id = 4100;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, 4460 FROM sys_role_menu WHERE menu_id IN (4461, 4462, 4463);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, 4465 FROM sys_role_menu WHERE menu_id = 4400;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, 4465 FROM sys_role_menu WHERE menu_id IN (4466, 4467);

-- 重复权限菜单的角色授权映射到保留的完整权限菜单。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, 4101 FROM sys_role_menu WHERE menu_id = 4461;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, 4105 FROM sys_role_menu WHERE menu_id = 4462;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, 4108 FROM sys_role_menu WHERE menu_id = 4463;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, 4401 FROM sys_role_menu WHERE menu_id = 4466;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, 4403 FROM sys_role_menu WHERE menu_id = 4467;

-- 任何拥有库存、调拨、盘点或报表入口的角色都补齐“仓储作业”父菜单。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT role_id, 4308
FROM sys_role_menu
WHERE menu_id IN (4040, 4450, 4460, 4465, 4300, 4470);

-- 旧路由父菜单和重复权限菜单不再参与授权树。
DELETE FROM sys_role_menu
WHERE menu_id IN (4100, 4400, 4461, 4462, 4463, 4466, 4467);

COMMIT;

-- 验收查询：可见组件路由不应出现重复 component；仓储作业必须包含库存、调拨、盘点和报表。
SELECT component, COUNT(*) AS visible_route_count,
       GROUP_CONCAT(CONCAT(menu_id, ':', menu_name) ORDER BY menu_id) AS routes
FROM sys_menu
WHERE menu_type = 'C' AND visible = '0' AND status = '0'
  AND component IN ('inventory/stock/index', 'inventory/transfer/index',
                    'inventory/transfer/records', 'inventory/stockCheck/index',
                    'inventory/report/index')
GROUP BY component
HAVING COUNT(*) > CASE WHEN component = 'inventory/stock/index' THEN 2 ELSE 1 END;

SELECT menu_id, menu_name, parent_id, order_num, path, component, visible, status
FROM sys_menu
WHERE parent_id = 4308 AND menu_type = 'C'
ORDER BY order_num, menu_id;

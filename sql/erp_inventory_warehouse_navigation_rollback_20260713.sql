-- 仓储作业导航归并回滚（MySQL 8）
-- 仅在应用版本已经回退、且确需恢复旧导航时执行。

-- 兼容早期未生成菜单快照的环境：下方先按旧口径恢复，
-- 若存在发布前快照，再以快照精确覆盖所有可变字段。
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

START TRANSACTION;

-- 清理本次迁移影响范围内的当前授权，再恢复迁移前快照。
DELETE FROM sys_role_menu
WHERE menu_id IN (
    4000, 4040, 4041, 4042, 4043, 4044,
    4100, 4101, 4102, 4103, 4104, 4105, 4106, 4107, 4108, 4109,
    4300, 4301, 4302, 4303, 4304, 4305, 4306, 4307, 4308,
    4400, 4401, 4402, 4403,
    4450, 4451, 4452, 4453, 4454,
    4460, 4461, 4462, 4463, 4465, 4466, 4467,
    4470, 4471
);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, menu_id
FROM sys_role_menu_warehouse_nav_backup_20260713;

UPDATE sys_menu
SET menu_name = '仓库管理', order_num = 4, path = 'cangku',
    visible = '0', status = '0', update_by = 'system', update_time = NOW(), remark = ''
WHERE menu_id = 4308;

UPDATE sys_menu
SET menu_name = '库存管理', parent_id = 4000, order_num = 29, path = 'stock',
    component = 'inventory/stock/index', query = '{"stockEntry":"store"}',
    route_name = 'InventoryStock', visible = '0', status = '0',
    update_by = 'system', update_time = NOW(), remark = ''
WHERE menu_id = 4040;

UPDATE sys_menu
SET menu_name = '库存管理', parent_id = 4308, order_num = 5, path = 'stock',
    query = '{"stockEntry":"warehouse"}', route_name = 'WarehouseStock',
    visible = '0', status = '0', update_by = 'system', update_time = NOW(),
    remark = '仓库管理库存入口'
WHERE menu_id = 4450;

UPDATE sys_menu
SET parent_id = 4000, visible = '0', status = '0', update_by = 'system', update_time = NOW()
WHERE menu_id BETWEEN 4101 AND 4109;
UPDATE sys_menu
SET visible = '0', status = '0', update_by = 'system', update_time = NOW(), remark = ''
WHERE menu_id = 4100;
UPDATE sys_menu
SET visible = '0', status = '0', update_by = 'system', update_time = NOW()
WHERE menu_id IN (4461, 4462, 4463);

UPDATE sys_menu
SET parent_id = 4000, visible = '0', status = '0', update_by = 'system', update_time = NOW()
WHERE menu_id IN (4401, 4402, 4403);
UPDATE sys_menu
SET visible = '0', status = '0', update_by = 'system', update_time = NOW(), remark = ''
WHERE menu_id = 4400;
UPDATE sys_menu
SET visible = '0', status = '0', update_by = 'system', update_time = NOW()
WHERE menu_id IN (4466, 4467);

UPDATE sys_menu
SET menu_name = '调拨管理', parent_id = 4308, order_num = 6, path = 'transfer',
    route_name = 'WarehouseTransfer', visible = '0', status = '0',
    update_by = 'system', update_time = NOW(), remark = '仓库管理调拨处理中入口'
WHERE menu_id = 4460;

UPDATE sys_menu
SET parent_id = 4308, order_num = 9, path = 'transfer-records',
    route_name = 'WarehouseTransferRecords', visible = '0', status = '0',
    update_by = 'system', update_time = NOW(), remark = '仓库管理调拨记录入口'
WHERE menu_id = 4465;

UPDATE sys_menu
SET parent_id = 4000, order_num = 30, path = 'stockCheck', route_name = '',
    visible = '0', status = '0', update_by = 'system', update_time = NOW(), remark = ''
WHERE menu_id = 4300;

UPDATE sys_menu
SET parent_id = 4000, order_num = 95, path = 'report', route_name = 'InventoryReport',
    visible = '0', status = '0', update_by = 'system', update_time = NOW(),
    remark = '进销存经营报表入口'
WHERE menu_id = 4470;

-- 新版发布脚本会保存逐字段原值，以此作为最终恢复结果。
UPDATE sys_menu menu
INNER JOIN sys_menu_warehouse_nav_backup_20260713 backup
        ON backup.menu_id = menu.menu_id
SET menu.menu_name = backup.menu_name,
    menu.parent_id = backup.parent_id,
    menu.order_num = backup.order_num,
    menu.path = backup.path,
    menu.component = backup.component,
    menu.query = backup.query,
    menu.route_name = backup.route_name,
    menu.visible = backup.visible,
    menu.status = backup.status,
    menu.icon = backup.icon,
    menu.update_by = backup.update_by,
    menu.update_time = backup.update_time,
    menu.remark = backup.remark;

COMMIT;

SELECT menu_id, menu_name, parent_id, path, visible, status
FROM sys_menu
WHERE menu_id IN (4000, 4040, 4100, 4300, 4308, 4400, 4450, 4460, 4465, 4470)
ORDER BY menu_id;

-- 用“客户服务卡”替代旧客户管理：同店隔离、全店协作维护、服务记录留痕。
-- MySQL 5.7，可重复执行；旧inv_customer财务字段保留但不再通过新页面和新接口返回。

CREATE TABLE IF NOT EXISTS inv_customer_service_profile (
    customer_id bigint(20) NOT NULL COMMENT '客户ID，与inv_customer一对一',
    photo_node_id bigint(20) DEFAULT NULL COMMENT '客户照片受控云盘节点ID',
    tea_preferences varchar(1000) DEFAULT NULL COMMENT '茶饮喜好',
    preference_tags varchar(500) DEFAULT NULL COMMENT '偏好标签，逗号分隔',
    brewing_service_preferences varchar(1000) DEFAULT NULL COMMENT '冲泡与服务偏好',
    cautions varchar(1000) DEFAULT NULL COMMENT '接待注意事项',
    budget_min decimal(16,2) DEFAULT NULL COMMENT '人均预算下限',
    budget_max decimal(16,2) DEFAULT NULL COMMENT '人均预算上限',
    last_visit_date datetime DEFAULT NULL COMMENT '最近到店时间',
    version bigint(20) NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (customer_id),
    KEY idx_inv_customer_service_last_visit (last_visit_date, customer_id),
    KEY idx_inv_customer_service_photo (photo_node_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户服务卡扩展资料';

CREATE TABLE IF NOT EXISTS inv_customer_service_record (
    record_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '服务记录ID',
    customer_id bigint(20) NOT NULL COMMENT '客户ID',
    service_date datetime NOT NULL COMMENT '到店服务时间',
    service_user_id bigint(20) NOT NULL COMMENT '服务员工用户ID',
    service_user_name varchar(64) NOT NULL COMMENT '服务员工姓名快照',
    party_size int(11) DEFAULT NULL COMMENT '到店人数',
    tea_served varchar(500) DEFAULT NULL COMMENT '本次饮用茶品',
    preference_snapshot varchar(1000) DEFAULT NULL COMMENT '本次偏好快照',
    caution_snapshot varchar(1000) DEFAULT NULL COMMENT '本次注意事项快照',
    service_note varchar(1000) DEFAULT NULL COMMENT '服务备注',
    consumption_amount decimal(16,2) DEFAULT NULL COMMENT '本次消费金额快照',
    shop_dept_id bigint(20) NOT NULL COMMENT '门店ID快照',
    request_key varchar(128) NOT NULL COMMENT '客户端幂等键',
    source_client varchar(32) NOT NULL DEFAULT 'WEB' COMMENT 'WEB/MOBILE/IMPORT',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (record_id),
    UNIQUE KEY uk_inv_customer_service_record_request (customer_id, request_key),
    KEY idx_inv_customer_service_record_customer (customer_id, service_date, record_id),
    KEY idx_inv_customer_service_record_shop (shop_dept_id, service_date),
    KEY idx_inv_customer_service_record_user (service_user_id, service_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户到店服务记录';

CREATE TABLE IF NOT EXISTS inv_customer_service_change_log (
    log_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '变更日志ID',
    customer_id bigint(20) NOT NULL COMMENT '客户ID',
    request_key varchar(128) NOT NULL COMMENT '业务幂等键',
    change_type varchar(32) NOT NULL COMMENT 'CREATE/UPDATE/ADD_RECORD/ARCHIVE',
    changed_fields varchar(1000) DEFAULT NULL COMMENT '变更字段集合',
    before_summary varchar(2000) DEFAULT NULL COMMENT '变更前脱敏摘要',
    after_summary varchar(2000) DEFAULT NULL COMMENT '变更后脱敏摘要',
    operator_user_id bigint(20) NOT NULL COMMENT '操作人用户ID',
    operator_name varchar(64) NOT NULL COMMENT '操作人姓名快照',
    shop_dept_id bigint(20) NOT NULL COMMENT '操作门店ID快照',
    source_client varchar(32) NOT NULL DEFAULT 'WEB' COMMENT 'WEB/MOBILE/IMPORT',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (log_id),
    UNIQUE KEY uk_inv_customer_service_log_request (customer_id, request_key),
    UNIQUE KEY uk_inv_customer_service_log_shop_request_type (shop_dept_id, request_key, change_type),
    KEY idx_inv_customer_service_log_customer (customer_id, create_time, log_id),
    KEY idx_inv_customer_service_log_shop (shop_dept_id, create_time),
    KEY idx_inv_customer_service_log_operator (operator_user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户服务卡变更审计日志';

-- 新建服务卡的幂等键必须在同一门店全局唯一，不能因customer_id尚未生成而失效。
SET @erp_db := DATABASE();
SET @sql := IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = @erp_db
          AND table_name = 'inv_customer_service_change_log'
          AND index_name = 'uk_inv_customer_service_log_shop_request_type'
    ),
    'ALTER TABLE inv_customer_service_change_log ADD UNIQUE KEY uk_inv_customer_service_log_shop_request_type (shop_dept_id, request_key, change_type)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 旧客户先补空服务资料，财务字段不会复制到新服务卡表。
INSERT IGNORE INTO inv_customer_service_profile
    (customer_id, version, create_by, create_time)
SELECT customer_id, 0, 'system', COALESCE(create_time, NOW())
FROM inv_customer;

INSERT INTO sys_config
    (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT '客户服务卡功能开关', 'feature.inventory.customer-service-card.enabled', 'false', 'Y',
       'system', NOW(), '旧客户财务接口保持兼容但不再提供页面入口；新接口只返回服务字段'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_config WHERE config_key = 'feature.inventory.customer-service-card.enabled'
);

SET @customer_page_id := (
    SELECT menu_id FROM sys_menu
    WHERE menu_type = 'C'
      AND (perms IN ('inv:customer:list', 'inv:customerCard:list')
           OR component = 'inventory/customer/index')
    ORDER BY CASE WHEN component = 'inventory/customer/index' THEN 0 ELSE 1 END, menu_id
    LIMIT 1
);

-- 原菜单ID不变，避免丢失已有角色绑定；权限和页面语义切换为客户服务卡。
UPDATE sys_menu
SET menu_name = '客户服务卡', path = 'customer',
    component = 'inventory/customer/index', route_name = 'InventoryCustomerServiceCard',
    visible = '0', status = '0', perms = 'inv:customerCard:list',
    update_by = 'system', update_time = NOW(),
    remark = '同一门店员工共享查看和维护；不展示信用额度、账期、地址等财务档案字段'
WHERE menu_id = @customer_page_id;

-- 旧增删改查和导出按钮停用，防止继续从菜单获得旧财务档案接口权限。
UPDATE sys_menu
SET visible = '1', status = '1', update_by = 'system', update_time = NOW(),
    remark = '已由客户服务卡权限替代'
WHERE parent_id = @customer_page_id
  AND perms IN ('inv:customer:list', 'inv:customer:query', 'inv:customer:add',
                'inv:customer:edit', 'inv:customer:remove', 'inv:customer:export');

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT next_id, '客户安全选项', @customer_page_id, 1, '', NULL, NULL, '',
       '1', '0', 'F', '0', '0', 'inv:customer:option', '#', 'system', NOW(),
       '销售和服务选择器仅返回姓名、编码、手机号和状态'
FROM (SELECT COALESCE(MAX(menu_id), 0) + 1 next_id FROM sys_menu) sequence
WHERE @customer_page_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'inv:customer:option');

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT next_id, '客户服务卡详情', @customer_page_id, 2, '', NULL, NULL, '',
       '1', '0', 'F', '0', '0', 'inv:customerCard:query', '#', 'system', NOW(), ''
FROM (SELECT COALESCE(MAX(menu_id), 0) + 1 next_id FROM sys_menu) sequence
WHERE @customer_page_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'inv:customerCard:query');

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT next_id, '新建客户服务卡', @customer_page_id, 3, '', NULL, NULL, '',
       '1', '0', 'F', '0', '0', 'inv:customerCard:add', '#', 'system', NOW(), ''
FROM (SELECT COALESCE(MAX(menu_id), 0) + 1 next_id FROM sys_menu) sequence
WHERE @customer_page_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'inv:customerCard:add');

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT next_id, '编辑客户服务卡', @customer_page_id, 4, '', NULL, NULL, '',
       '1', '0', 'F', '0', '0', 'inv:customerCard:edit', '#', 'system', NOW(), ''
FROM (SELECT COALESCE(MAX(menu_id), 0) + 1 next_id FROM sys_menu) sequence
WHERE @customer_page_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'inv:customerCard:edit');

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT next_id, '追加客户服务记录', @customer_page_id, 5, '', NULL, NULL, '',
       '1', '0', 'F', '0', '0', 'inv:customerCard:record:add', '#', 'system', NOW(), ''
FROM (SELECT COALESCE(MAX(menu_id), 0) + 1 next_id FROM sys_menu) sequence
WHERE @customer_page_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'inv:customerCard:record:add');

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT next_id, '归档客户服务卡', @customer_page_id, 6, '', NULL, NULL, '',
       '1', '0', 'F', '0', '0', 'inv:customerCard:archive', '#', 'system', NOW(), ''
FROM (SELECT COALESCE(MAX(menu_id), 0) + 1 next_id FROM sys_menu) sequence
WHERE @customer_page_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'inv:customerCard:archive');

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT next_id, '查看客户服务审计', @customer_page_id, 7, '', NULL, NULL, '',
       '1', '0', 'F', '0', '0', 'inv:customerCard:audit', '#', 'system', NOW(),
       '仅返回脱敏变更摘要，不返回请求幂等键及服务备注原文'
FROM (SELECT COALESCE(MAX(menu_id), 0) + 1 next_id FROM sys_menu) sequence
WHERE @customer_page_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'inv:customerCard:audit');

-- 门店一线标准角色、原客户页角色和销售角色均可在应用层当前门店边界内协作维护。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT eligible.role_id, service_menu.menu_id
FROM (
    SELECT role_id FROM sys_role
    WHERE status = '0' AND del_flag = '0'
      AND lower(role_key) IN ('sxs', 'cys', 'dzzy', 'dz', 'zdjl',
                              'intern', 'tea_artist', 'store_assistant',
                              'store_manager', 'shop_manager')
    UNION
    SELECT rm.role_id FROM sys_role_menu rm
    JOIN sys_menu old_menu ON old_menu.menu_id = rm.menu_id
    WHERE old_menu.menu_id = @customer_page_id
       OR old_menu.perms IN ('inv:customer:list', 'inv:sales:list')
) eligible
JOIN sys_menu service_menu ON service_menu.menu_id = @customer_page_id
    OR service_menu.perms IN (
        'inv:customer:option', 'inv:customerCard:query', 'inv:customerCard:add',
        'inv:customerCard:edit', 'inv:customerCard:record:add'
    );

-- 归档会让客户从日常选项中消失，只授予店长/经理和原拥有客户删除权限的角色。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT eligible.role_id, archive_menu.menu_id
FROM (
    SELECT role_id FROM sys_role
    WHERE status = '0' AND del_flag = '0'
      AND lower(role_key) IN ('dz', 'zdjl', 'store_manager', 'shop_manager')
    UNION
    SELECT rm.role_id FROM sys_role_menu rm
    JOIN sys_menu old_remove ON old_remove.menu_id = rm.menu_id
    WHERE old_remove.perms = 'inv:customer:remove'
) eligible
JOIN sys_menu archive_menu ON archive_menu.perms = 'inv:customerCard:archive';

-- 审计记录仅授予门店负责人，不随一线员工的协作维护权限下发。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT manager_role.role_id, audit_menu.menu_id
FROM sys_role manager_role
JOIN sys_menu audit_menu ON audit_menu.perms = 'inv:customerCard:audit'
WHERE manager_role.status = '0' AND manager_role.del_flag = '0'
  AND lower(manager_role.role_key) IN ('dz', 'zdjl', 'store_manager', 'shop_manager');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id FROM sys_menu
WHERE menu_id = @customer_page_id
   OR perms IN (
       'inv:customer:option', 'inv:customerCard:query', 'inv:customerCard:add',
       'inv:customerCard:edit', 'inv:customerCard:record:add', 'inv:customerCard:archive',
       'inv:customerCard:audit'
   );

-- 上线后检查：每个客户仅一份资料、服务记录不跨门店、无重复幂等键。
SELECT c.shop_dept_id, COUNT(*) customer_count,
       SUM(CASE WHEN p.customer_id IS NULL THEN 1 ELSE 0 END) missing_profile_count
FROM inv_customer c
LEFT JOIN inv_customer_service_profile p ON p.customer_id = c.customer_id
GROUP BY c.shop_dept_id ORDER BY c.shop_dept_id;

SELECT r.record_id, r.customer_id, r.shop_dept_id record_shop_dept_id,
       c.shop_dept_id customer_shop_dept_id
FROM inv_customer_service_record r
JOIN inv_customer c ON c.customer_id = r.customer_id
WHERE r.shop_dept_id <> c.shop_dept_id
ORDER BY r.record_id DESC;

SELECT customer_id, request_key, COUNT(*) duplicate_count
FROM inv_customer_service_record
GROUP BY customer_id, request_key HAVING COUNT(*) > 1;

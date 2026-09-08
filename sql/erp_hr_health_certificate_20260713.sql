-- 员工健康证历史、提醒配置和权限（MySQL 5.7，可重复执行）。
-- 首版只新增数据结构；应用回滚时保留本表，旧员工档案不受影响。

CREATE TABLE IF NOT EXISTS hr_employee_health_certificate (
    certificate_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '健康证记录ID',
    user_id bigint(20) NOT NULL COMMENT '员工用户ID',
    dept_id_snapshot bigint(20) DEFAULT NULL COMMENT '提交时组织快照',
    certificate_no varchar(100) DEFAULT NULL COMMENT '健康证编号',
    issued_date date NOT NULL COMMENT '办理日期',
    valid_from date DEFAULT NULL COMMENT '有效期开始日',
    expires_on date NOT NULL COMMENT '到期日',
    issuer_name varchar(128) DEFAULT NULL COMMENT '发证机构',
    attachment_node_id bigint(20) DEFAULT NULL COMMENT '受控云盘节点ID',
    review_status varchar(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PENDING_REVIEW/APPROVED/REJECTED',
    current_flag char(1) DEFAULT NULL COMMENT '当前审核通过证件为Y，历史为NULL',
    reviewed_by_user_id bigint(20) DEFAULT NULL COMMENT '审核人用户ID',
    reviewed_by_name varchar(64) DEFAULT NULL COMMENT '审核人姓名快照',
    reviewed_time datetime DEFAULT NULL COMMENT '审核时间',
    rejection_reason varchar(300) DEFAULT NULL COMMENT '驳回原因',
    version bigint(20) NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    del_flag char(1) NOT NULL DEFAULT '0' COMMENT '删除标志',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (certificate_id),
    UNIQUE KEY uk_hr_health_user_current (user_id, current_flag),
    KEY idx_hr_health_user_history (user_id, del_flag, create_time),
    KEY idx_hr_health_review (review_status, del_flag, create_time),
    KEY idx_hr_health_expiry (current_flag, review_status, expires_on, certificate_id),
    KEY idx_hr_health_dept_snapshot (dept_id_snapshot)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工健康证历史';

INSERT INTO sys_config
    (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT '健康证到期提醒节点', 'todo.health-certificate.warning-days', '30,15,7', 'Y',
       'system', NOW(), 'Asia/Shanghai每日扫描；逗号分隔且节点幂等'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_config WHERE config_key = 'todo.health-certificate.warning-days'
);

INSERT INTO sys_config
    (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT '健康证功能开关', 'feature.hr.health-certificate.enabled', 'false', 'Y',
       'system', NOW(), '新受理默认关闭；历史查询、已有审核和到期提醒继续处理'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_config WHERE config_key = 'feature.hr.health-certificate.enabled'
);

-- MAX(menu_id)+1 statements must run in one migration session during a maintenance window.
SET @hr_parent_id := (
    SELECT parent_id FROM sys_menu
    WHERE menu_type = 'C' AND perms = 'hr:employee:list'
    ORDER BY menu_id LIMIT 1
);
SET @hr_parent_id := COALESCE(@hr_parent_id, 0);

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT next_id, '健康证管理', @hr_parent_id, 18, 'healthCertificate',
       'hr/healthCertificate/index', NULL, 'HrHealthCertificate',
       '1', '0', 'C', '0', '0', 'hr:healthCertificate:self:edit', 'documentation',
       'system', NOW(), '员工本人维护；具有人事权限时同时显示审核台账'
FROM (SELECT COALESCE(MAX(menu_id), 0) + 1 next_id FROM sys_menu) sequence
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE component = 'hr/healthCertificate/index'
);

SET @health_page_id := (
    SELECT menu_id FROM sys_menu
    WHERE component = 'hr/healthCertificate/index'
    ORDER BY menu_id LIMIT 1
);

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT next_id, '提交本人健康证', @health_page_id, 1, '', NULL, NULL, '',
       '1', '0', 'F', '0', '0', 'hr:healthCertificate:self:submit', '#',
       'system', NOW(), ''
FROM (SELECT COALESCE(MAX(menu_id), 0) + 1 next_id FROM sys_menu) sequence
WHERE @health_page_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'hr:healthCertificate:self:submit');

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT next_id, '健康证台账查询', @health_page_id, 2, '', NULL, NULL, '',
       '1', '0', 'F', '0', '0', 'hr:healthCertificate:list', '#',
       'system', NOW(), ''
FROM (SELECT COALESCE(MAX(menu_id), 0) + 1 next_id FROM sys_menu) sequence
WHERE @health_page_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'hr:healthCertificate:list');

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT next_id, '健康证详情查询', @health_page_id, 3, '', NULL, NULL, '',
       '1', '0', 'F', '0', '0', 'hr:healthCertificate:query', '#',
       'system', NOW(), ''
FROM (SELECT COALESCE(MAX(menu_id), 0) + 1 next_id FROM sys_menu) sequence
WHERE @health_page_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'hr:healthCertificate:query');

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT next_id, '健康证审核', @health_page_id, 4, '', NULL, NULL, '',
       '1', '0', 'F', '0', '0', 'hr:healthCertificate:review', '#',
       'system', NOW(), ''
FROM (SELECT COALESCE(MAX(menu_id), 0) + 1 next_id FROM sys_menu) sequence
WHERE @health_page_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'hr:healthCertificate:review');

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT next_id, '健康证到期提醒接收', @health_page_id, 5, '', NULL, NULL, '',
       '1', '0', 'F', '0', '0', 'hr:healthCertificate:remind', '#',
       'system', NOW(), '允许接收本门店员工证件到期提醒'
FROM (SELECT COALESCE(MAX(menu_id), 0) + 1 next_id FROM sys_menu) sequence
WHERE @health_page_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'hr:healthCertificate:remind');

-- 所有有效角色都获得本人维护能力；人事管理能力沿用已有员工档案页面角色。
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
JOIN sys_menu m ON m.menu_id = @health_page_id
                   OR m.perms = 'hr:healthCertificate:self:submit'
WHERE r.status = '0' AND r.del_flag = '0'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu existing
      WHERE existing.role_id = r.role_id AND existing.menu_id = m.menu_id
  );

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT old_rm.role_id, health_menu.menu_id
FROM sys_role_menu old_rm
JOIN sys_menu old_menu ON old_menu.menu_id = old_rm.menu_id
JOIN sys_menu health_menu ON health_menu.perms IN (
    'hr:healthCertificate:list', 'hr:healthCertificate:query',
    'hr:healthCertificate:review', 'hr:healthCertificate:remind'
)
WHERE old_menu.perms = 'hr:employee:list'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu existing
      WHERE existing.role_id = old_rm.role_id AND existing.menu_id = health_menu.menu_id
  );

-- 只读上线预检与对账。
SELECT review_status, current_flag, COUNT(*) row_count
FROM hr_employee_health_certificate
WHERE del_flag = '0'
GROUP BY review_status, current_flag
ORDER BY review_status, current_flag;

SELECT user_id, COUNT(*) current_count
FROM hr_employee_health_certificate
WHERE current_flag = 'Y' AND del_flag = '0'
GROUP BY user_id HAVING COUNT(*) > 1;

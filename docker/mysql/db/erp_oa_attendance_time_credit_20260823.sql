-- Append-only manager adjustment: use same-month overtime minutes to offset
-- later early-leave minutes. Raw punch evidence and daily results are never
-- overwritten by this migration or by the adjustment ledger.

CREATE TABLE IF NOT EXISTS oa_attendance_time_credit_period_lock (
    shop_id bigint(20) NOT NULL COMMENT '门店组织ID',
    salary_month varchar(7) NOT NULL COMMENT '工资月份YYYY-MM',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (shop_id, salary_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='考勤抵扣与工资生成的月份并发锁';

CREATE TABLE IF NOT EXISTS oa_attendance_time_credit_adjustment (
    adjustment_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '抵扣事件ID',
    adjustment_no varchar(40) NOT NULL COMMENT '抵扣事件编号',
    client_request_id varchar(64) NOT NULL COMMENT '客户端幂等请求键',
    request_fingerprint char(64) NOT NULL COMMENT '请求规范载荷SHA-256',
    adjustment_action varchar(16) NOT NULL COMMENT 'APPLY/REVERSE',
    original_adjustment_id bigint(20) DEFAULT NULL COMMENT '撤销所指向的原APPLY事件ID',
    shop_id bigint(20) NOT NULL COMMENT '门店组织ID',
    user_id bigint(20) NOT NULL COMMENT '员工用户ID',
    user_name varchar(64) NOT NULL COMMENT '员工姓名快照',
    salary_month varchar(7) NOT NULL COMMENT '工资月份YYYY-MM',
    source_day_result_id bigint(20) NOT NULL COMMENT '加班来源日结ID',
    source_schedule_id bigint(20) NOT NULL COMMENT '加班来源排班ID',
    source_business_date date NOT NULL COMMENT '加班来源业务日',
    target_day_result_id bigint(20) NOT NULL COMMENT '早退目标日结ID',
    target_schedule_id bigint(20) NOT NULL COMMENT '早退目标排班ID',
    target_business_date date NOT NULL COMMENT '早退目标业务日',
    adjustment_minutes int NOT NULL COMMENT '抵扣分钟数，必须为正数',
    reason varchar(500) NOT NULL COMMENT '店铺管理人员调整原因',
    operator_user_id bigint(20) NOT NULL COMMENT '操作人用户ID',
    operator_name varchar(64) NOT NULL COMMENT '操作人名称快照',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (adjustment_id),
    UNIQUE KEY uk_oa_attendance_time_credit_no (adjustment_no),
    UNIQUE KEY uk_oa_attendance_time_credit_client
        (shop_id, operator_user_id, client_request_id),
    UNIQUE KEY uk_oa_attendance_time_credit_reverse
        (original_adjustment_id, adjustment_action),
    KEY idx_oa_attendance_time_credit_source
        (source_day_result_id, adjustment_action),
    KEY idx_oa_attendance_time_credit_target
        (target_day_result_id, adjustment_action),
    KEY idx_oa_attendance_time_credit_month
        (shop_id, user_id, salary_month, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='OA考勤加班抵扣早退的不可覆盖调整台账';

SET @attendance_time_credit_parent := (
    SELECT menu_id FROM sys_menu
    WHERE perms = 'oa:attendance:center:list'
    ORDER BY menu_id LIMIT 1
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '加班抵扣早退', @attendance_time_credit_parent, 73,
       '', NULL, NULL, '', 1, 0, 'F', '0', '0',
       'oa:attendance:time-credit:manage', '#', 'system', NOW(),
       '门店管理人员在同一工资月内手工使用既有加班分钟抵扣后续早退；追加记录且可撤销'
WHERE @attendance_time_credit_parent IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu
      WHERE perms = 'oa:attendance:time-credit:manage'
  );

-- Head-office and administrator roles keep global attendance capability.
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
JOIN sys_menu m ON m.perms = 'oa:attendance:time-credit:manage'
WHERE r.status = '0' AND r.del_flag = '0'
  AND (r.role_id = 1 OR r.role_key IN ('admin', 'yyzj', 'zjl', 'qyyyzzj'));

-- Store managers receive only the scoped adjustment action. The service still
-- resolves the selected store through ShopScopeService on every request.
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
JOIN sys_menu m ON m.perms = 'oa:attendance:time-credit:manage'
WHERE r.status = '0' AND r.del_flag = '0'
  AND r.role_key IN ('dz', 'yyjl', 'zdjl');

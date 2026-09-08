-- 调拨审批完整性显式修复模板（MySQL 8）
-- 默认 @enable_transfer_approval_repair = 0，不创建表、不写业务数据。
-- 只有在只读预检、逐单人工核对和审批负责人签字后，才把开关改为 1 并录入明确映射。

SET @enable_transfer_approval_repair = 0;
SET @repair_batch_no = CONCAT('TRANSFER-APPROVAL-', DATE_FORMAT(NOW(), '%Y%m%d%H%i%s'));

SET @ddl = IF(@enable_transfer_approval_repair = 1,
    'CREATE TABLE IF NOT EXISTS inv_transfer_approval_repair_request (
        request_id bigint NOT NULL AUTO_INCREMENT,
        transfer_id bigint NOT NULL,
        target_instance_id bigint NOT NULL,
        target_status varchar(20) NOT NULL DEFAULT ''approved'',
        repair_reason varchar(500) NOT NULL,
        reviewed_by varchar(64) NOT NULL,
        reviewed_time datetime NOT NULL,
        executed_batch_no varchar(64) NULL,
        executed_time datetime NULL,
        PRIMARY KEY (request_id),
        UNIQUE KEY uk_transfer_approval_repair (transfer_id, target_instance_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT=''调拨审批显式修复申请''',
    'SELECT ''repair disabled: no table or business data changed'' AS message');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(@enable_transfer_approval_repair = 1,
    'CREATE TABLE IF NOT EXISTS inv_transfer_approval_repair_audit (
        audit_id bigint NOT NULL AUTO_INCREMENT,
        repair_batch_no varchar(64) NOT NULL,
        request_id bigint NOT NULL,
        transfer_id bigint NOT NULL,
        old_instance_id bigint NULL,
        new_instance_id bigint NOT NULL,
        old_status varchar(20) NULL,
        new_status varchar(20) NOT NULL,
        repair_reason varchar(500) NOT NULL,
        reviewed_by varchar(64) NOT NULL,
        executed_time datetime NOT NULL,
        PRIMARY KEY (audit_id),
        KEY idx_transfer_approval_repair_batch (repair_batch_no, transfer_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT=''调拨审批修复审计''',
    'SELECT ''repair disabled: audit table not created'' AS message');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 开启修复前，由负责人手工执行类似下方的明确映射；禁止按单号或时间做模糊匹配。
-- INSERT INTO inv_transfer_approval_repair_request
--     (transfer_id, target_instance_id, target_status, repair_reason, reviewed_by, reviewed_time)
-- VALUES
--     (123, 456, 'approved', '历史迁移遗漏主单关联，经审批轨迹逐项核对', 'reviewer', NOW());

SET @sql = IF(@enable_transfer_approval_repair = 1,
    CONCAT(
      'INSERT INTO inv_transfer_approval_repair_audit ',
      '(repair_batch_no, request_id, transfer_id, old_instance_id, new_instance_id, ',
      ' old_status, new_status, repair_reason, reviewed_by, executed_time) ',
      'SELECT ', QUOTE(@repair_batch_no), ', r.request_id, o.transfer_id, o.approval_instance_id, ',
      ' r.target_instance_id, o.status, r.target_status, r.repair_reason, r.reviewed_by, NOW() ',
      'FROM inv_transfer_approval_repair_request r ',
      'JOIN inv_transfer_order o ON o.transfer_id = r.transfer_id ',
      'JOIN inv_transfer_approval_instance i ON i.instance_id = r.target_instance_id ',
      'WHERE r.executed_time IS NULL AND i.transfer_id = o.transfer_id AND i.status = ''approved'' ',
      'AND (EXISTS (SELECT 1 FROM inv_transfer_approval_task t ',
      '             WHERE t.instance_id = i.instance_id AND t.transfer_id = o.transfer_id ',
      '               AND t.status = ''approved'') ',
      '     OR EXISTS (SELECT 1 FROM inv_transfer_status_log l ',
      '                WHERE l.transfer_id = o.transfer_id AND l.action = ''auto_approve''))'
    ),
    'SELECT ''repair disabled: audit not written'' AS message');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(@enable_transfer_approval_repair = 1,
    CONCAT(
      'UPDATE inv_transfer_order o ',
      'JOIN inv_transfer_approval_repair_request r ON r.transfer_id = o.transfer_id ',
      'JOIN inv_transfer_approval_instance i ON i.instance_id = r.target_instance_id ',
      'SET o.approval_instance_id = r.target_instance_id, o.status = r.target_status, ',
      '    o.update_by = r.reviewed_by, o.update_time = NOW() ',
      'WHERE r.executed_time IS NULL AND i.transfer_id = o.transfer_id AND i.status = ''approved'' ',
      'AND EXISTS (SELECT 1 FROM inv_transfer_approval_repair_audit a ',
      '            WHERE a.repair_batch_no = ', QUOTE(@repair_batch_no),
      '              AND a.request_id = r.request_id)'
    ),
    'SELECT ''repair disabled: transfer order not changed'' AS message');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(@enable_transfer_approval_repair = 1,
    CONCAT(
      'UPDATE inv_transfer_approval_repair_request r ',
      'SET r.executed_batch_no = ', QUOTE(@repair_batch_no), ', r.executed_time = NOW() ',
      'WHERE r.executed_time IS NULL ',
      'AND EXISTS (SELECT 1 FROM inv_transfer_approval_repair_audit a ',
      '            WHERE a.repair_batch_no = ', QUOTE(@repair_batch_no),
      '              AND a.request_id = r.request_id)'
    ),
    'SELECT ''repair disabled: request not marked'' AS message');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT @enable_transfer_approval_repair AS repair_enabled, @repair_batch_no AS repair_batch_no;

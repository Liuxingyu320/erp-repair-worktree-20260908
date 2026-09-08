-- 合同签约：公司主体、部门绑定、按主体印章及最终合同确认。
-- 仅新增表、字段和索引；不覆盖历史合同，不根据自由文本猜测公司。
-- 兼容 MySQL 5.7，可重复执行。

SET @erp_db = DATABASE();

-- 签约方案不再预先绑定公司；公司由员工首次签名后的部门归属解析确定。
SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_plan_version'
                  AND COLUMN_NAME = 'legal_entity_id' AND IS_NULLABLE = 'NO') > 0,
    'ALTER TABLE oa_sign_plan_version MODIFY COLUMN legal_entity_id bigint(20) DEFAULT NULL COMMENT ''历史公司主体快照（新版本不再预绑定）''',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_plan_version'
                  AND COLUMN_NAME = 'legal_entity_name' AND IS_NULLABLE = 'NO') > 0,
    'ALTER TABLE oa_sign_plan_version MODIFY COLUMN legal_entity_name varchar(160) DEFAULT NULL COMMENT ''历史公司名称快照（新版本不再预绑定）''',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS sys_legal_entity (
    legal_entity_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '公司主体ID',
    legal_entity_code varchar(64) NOT NULL COMMENT '公司主体编码',
    legal_entity_name varchar(160) NOT NULL COMMENT '公司法定全称',
    unified_social_credit_code varchar(32) DEFAULT NULL COMMENT '统一社会信用代码',
    registered_address varchar(255) DEFAULT NULL COMMENT '注册地址',
    legal_representative varchar(64) DEFAULT NULL COMMENT '法定代表人',
    contact_phone varchar(32) DEFAULT NULL COMMENT '联系电话',
    status char(1) NOT NULL DEFAULT '0' COMMENT '状态（0启用 1停用）',
    version bigint(20) NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime DEFAULT NULL COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime DEFAULT NULL COMMENT '更新时间',
    remark varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (legal_entity_id),
    UNIQUE KEY uk_sys_legal_entity_code (legal_entity_code),
    KEY idx_sys_legal_entity_name (legal_entity_name),
    KEY idx_sys_legal_entity_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公司法律主体主数据';

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'sys_dept'
                  AND COLUMN_NAME = 'legal_entity_id') = 0,
    'ALTER TABLE sys_dept ADD COLUMN legal_entity_id bigint(20) DEFAULT NULL COMMENT ''合同归属公司主体ID'' AFTER dept_type',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'sys_dept'
                  AND INDEX_NAME = 'idx_sys_dept_legal_entity') = 0,
    'ALTER TABLE sys_dept ADD KEY idx_sys_dept_legal_entity (legal_entity_id)',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_company_seal_config'
                  AND COLUMN_NAME = 'legal_entity_id') = 0,
    'ALTER TABLE oa_company_seal_config ADD COLUMN legal_entity_id bigint(20) DEFAULT NULL COMMENT ''所属公司主体ID'' AFTER seal_id',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_company_seal_config'
                  AND COLUMN_NAME = 'seal_code') = 0,
    'ALTER TABLE oa_company_seal_config ADD COLUMN seal_code varchar(64) DEFAULT NULL COMMENT ''印章编码'' AFTER legal_entity_id',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_company_seal_config'
                  AND COLUMN_NAME = 'seal_type') = 0,
    'ALTER TABLE oa_company_seal_config ADD COLUMN seal_type varchar(32) NOT NULL DEFAULT ''CONTRACT'' COMMENT ''印章类型'' AFTER seal_name',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_company_seal_config'
                  AND COLUMN_NAME = 'is_default') = 0,
    'ALTER TABLE oa_company_seal_config ADD COLUMN is_default char(1) NOT NULL DEFAULT ''N'' COMMENT ''是否默认印章（Y是 N否）'' AFTER seal_type',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_company_seal_config'
                  AND COLUMN_NAME = 'seal_image_hash') = 0,
    'ALTER TABLE oa_company_seal_config ADD COLUMN seal_image_hash varchar(64) DEFAULT NULL COMMENT ''印章图片校验值'' AFTER seal_image_url',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_company_seal_config'
                  AND COLUMN_NAME = 'valid_from') = 0,
    'ALTER TABLE oa_company_seal_config ADD COLUMN valid_from datetime DEFAULT NULL COMMENT ''有效期开始'' AFTER seal_image_hash',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_company_seal_config'
                  AND COLUMN_NAME = 'valid_to') = 0,
    'ALTER TABLE oa_company_seal_config ADD COLUMN valid_to datetime DEFAULT NULL COMMENT ''有效期结束'' AFTER valid_from',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_company_seal_config'
                  AND INDEX_NAME = 'idx_oa_company_seal_entity') = 0,
    'ALTER TABLE oa_company_seal_config ADD KEY idx_oa_company_seal_entity (legal_entity_id, status, is_default)',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package'
                  AND COLUMN_NAME = 'legal_entity_source_dept_id') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN legal_entity_source_dept_id bigint(20) DEFAULT NULL COMMENT ''公司识别来源部门ID'' AFTER legal_entity_name_snapshot',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package'
                  AND COLUMN_NAME = 'legal_entity_resolve_mode') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN legal_entity_resolve_mode varchar(20) DEFAULT NULL COMMENT ''公司识别方式：AUTO/MANUAL'' AFTER legal_entity_source_dept_id',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package'
                  AND COLUMN_NAME = 'legal_entity_credit_code_snapshot') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN legal_entity_credit_code_snapshot varchar(32) DEFAULT NULL COMMENT ''统一社会信用代码快照'' AFTER legal_entity_resolve_mode',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package'
                  AND COLUMN_NAME = 'legal_entity_address_snapshot') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN legal_entity_address_snapshot varchar(255) DEFAULT NULL COMMENT ''注册地址快照'' AFTER legal_entity_credit_code_snapshot',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package'
                  AND COLUMN_NAME = 'legal_representative_snapshot') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN legal_representative_snapshot varchar(64) DEFAULT NULL COMMENT ''法定代表人快照'' AFTER legal_entity_address_snapshot',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package'
                  AND COLUMN_NAME = 'legal_entity_phone_snapshot') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN legal_entity_phone_snapshot varchar(32) DEFAULT NULL COMMENT ''公司联系电话快照'' AFTER legal_representative_snapshot',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package'
                  AND COLUMN_NAME = 'legal_entity_override_reason') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN legal_entity_override_reason varchar(500) DEFAULT NULL COMMENT ''人工改选公司原因'' AFTER legal_entity_phone_snapshot',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package'
                  AND COLUMN_NAME = 'seal_id_snapshot') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN seal_id_snapshot bigint(20) DEFAULT NULL COMMENT ''印章ID快照'' AFTER legal_entity_resolve_mode',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package'
                  AND COLUMN_NAME = 'seal_name_snapshot') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN seal_name_snapshot varchar(100) DEFAULT NULL COMMENT ''印章名称快照'' AFTER seal_id_snapshot',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package'
                  AND COLUMN_NAME = 'seal_image_url_snapshot') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN seal_image_url_snapshot varchar(500) DEFAULT NULL COMMENT ''印章图片快照地址'' AFTER seal_name_snapshot',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package'
                  AND COLUMN_NAME = 'seal_image_hash_snapshot') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN seal_image_hash_snapshot varchar(64) DEFAULT NULL COMMENT ''印章图片快照校验值'' AFTER seal_image_url_snapshot',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package'
                  AND COLUMN_NAME = 'initial_signed_time') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN initial_signed_time datetime DEFAULT NULL COMMENT ''员工首次签名时间'' AFTER signed_time',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package'
                  AND COLUMN_NAME = 'final_document_version') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN final_document_version varchar(64) DEFAULT NULL COMMENT ''最终合同版本'' AFTER document_version',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package'
                  AND COLUMN_NAME = 'final_document_root_hash') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN final_document_root_hash varchar(64) DEFAULT NULL COMMENT ''最终合同文件集合校验值'' AFTER final_document_version',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package'
                  AND COLUMN_NAME = 'final_generated_time') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN final_generated_time datetime DEFAULT NULL COMMENT ''最终合同生成时间'' AFTER final_document_root_hash',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package'
                  AND COLUMN_NAME = 'final_confirmed_time') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN final_confirmed_time datetime DEFAULT NULL COMMENT ''员工最终确认时间'' AFTER final_generated_time',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package'
                  AND COLUMN_NAME = 'final_confirmation_status') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN final_confirmation_status varchar(20) DEFAULT NULL COMMENT ''最终确认状态'' AFTER final_confirmed_time',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package_document'
                  AND COLUMN_NAME = 'final_pdf_url') = 0,
    'ALTER TABLE oa_sign_package_document ADD COLUMN final_pdf_url varchar(500) DEFAULT NULL COMMENT ''最终合同文件地址'' AFTER signed_pdf_hash',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package_document'
                  AND COLUMN_NAME = 'final_pdf_hash') = 0,
    'ALTER TABLE oa_sign_package_document ADD COLUMN final_pdf_hash varchar(64) DEFAULT NULL COMMENT ''最终合同文件校验值'' AFTER final_pdf_url',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package_document'
                  AND COLUMN_NAME = 'final_document_version') = 0,
    'ALTER TABLE oa_sign_package_document ADD COLUMN final_document_version varchar(64) DEFAULT NULL COMMENT ''最终合同版本'' AFTER document_version',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package_document'
                  AND COLUMN_NAME = 'final_read_confirmed') = 0,
    'ALTER TABLE oa_sign_package_document ADD COLUMN final_read_confirmed char(1) NOT NULL DEFAULT ''N'' COMMENT ''最终合同是否确认阅读'' AFTER final_document_version',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS oa_sign_final_confirmation (
    confirmation_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '最终确认ID',
    package_id bigint(20) NOT NULL COMMENT '签约包ID',
    employee_id bigint(20) NOT NULL COMMENT '确认员工用户ID',
    final_document_version varchar(64) NOT NULL COMMENT '最终合同版本',
    document_root_hash varchar(64) NOT NULL COMMENT '最终合同文件集合校验值',
    confirmation_text varchar(500) NOT NULL COMMENT '员工确认原文',
    identity_method varchar(32) NOT NULL DEFAULT 'LOGIN_TOKEN' COMMENT '身份确认方式',
    request_id varchar(64) NOT NULL COMMENT '幂等请求ID',
    ip_address varchar(64) DEFAULT NULL COMMENT '网络地址',
    user_agent varchar(500) DEFAULT NULL COMMENT '设备与浏览器信息',
    confirmed_time datetime NOT NULL COMMENT '确认时间',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (confirmation_id),
    UNIQUE KEY uk_oa_sign_final_confirmation_request (request_id),
    UNIQUE KEY uk_oa_sign_final_confirmation_version (package_id, final_document_version),
    KEY idx_oa_sign_final_confirmation_employee (employee_id, confirmed_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工最终合同确认记录';

CREATE TABLE IF NOT EXISTS oa_sign_final_confirmation_document (
    confirmation_document_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '最终确认文件ID',
    confirmation_id bigint(20) NOT NULL COMMENT '最终确认ID',
    package_id bigint(20) NOT NULL COMMENT '签约包ID',
    document_id bigint(20) NOT NULL COMMENT '签约文件ID',
    final_document_version varchar(64) NOT NULL COMMENT '最终合同版本',
    final_pdf_hash varchar(64) NOT NULL COMMENT '最终合同文件校验值',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (confirmation_document_id),
    UNIQUE KEY uk_oa_sign_final_confirmation_document (confirmation_id, document_id),
    KEY idx_oa_sign_final_confirmation_document_package (package_id, document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工最终确认文件明细';

-- 现有全局印章保留为历史配置，但不自动绑定公司；新流程只允许使用已绑定公司的启用印章。
UPDATE oa_company_seal_config
SET seal_code = CONCAT('SEAL-', seal_id)
WHERE (seal_code IS NULL OR TRIM(seal_code) = '');

-- 新流程无需人工审核：将历史“待确认”任务直接放行到待发送。
UPDATE oa_sign_package p
JOIN oa_sign_task t ON t.task_id = p.task_id
SET p.confirm_status = 'NOT_REQUIRED', p.update_time = NOW()
WHERE t.status = 'WAITING_HR_CONFIRM';

UPDATE oa_sign_task
SET status = 'READY_TO_SEND',
    confirmed_by = NULL,
    confirmed_time = NULL,
    confirmed_snapshot_hash = NULL
WHERE status = 'WAITING_HR_CONFIRM';

-- 保留历史内部角色键以兼容现有授权，只把使用者可见名称改为中文业务称谓。
UPDATE sys_role
SET role_name = '合同签约经办人',
    update_by = 'system',
    update_time = NOW()
WHERE role_key = 'sign_single_hr'
  AND role_name <> '合同签约经办人';

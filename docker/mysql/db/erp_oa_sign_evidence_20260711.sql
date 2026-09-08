-- Immutable signing file evidence metadata. Existing rows intentionally keep NULL hashes.

SET @erp_db = DATABASE();

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package' AND COLUMN_NAME = 'document_version') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN document_version varchar(64) DEFAULT NULL COMMENT ''文档版本'' AFTER signed_time', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package' AND COLUMN_NAME = 'sign_deadline') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN sign_deadline datetime DEFAULT NULL COMMENT ''签署截止时间'' AFTER document_version', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package' AND COLUMN_NAME = 'version') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN version bigint NOT NULL DEFAULT 0 COMMENT ''乐观锁版本'' AFTER sign_deadline', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package_document' AND COLUMN_NAME = 'review_pdf_url') = 0,
    'ALTER TABLE oa_sign_package_document ADD COLUMN review_pdf_url varchar(500) DEFAULT NULL COMMENT ''员工阅读PDF'' AFTER file_hash_after_sign', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package_document' AND COLUMN_NAME = 'review_pdf_hash') = 0,
    'ALTER TABLE oa_sign_package_document ADD COLUMN review_pdf_hash varchar(64) DEFAULT NULL COMMENT ''阅读PDF SHA-256'' AFTER review_pdf_url', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package_document' AND COLUMN_NAME = 'signed_pdf_url') = 0,
    'ALTER TABLE oa_sign_package_document ADD COLUMN signed_pdf_url varchar(500) DEFAULT NULL COMMENT ''独立已签PDF'' AFTER review_pdf_hash', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package_document' AND COLUMN_NAME = 'signed_pdf_hash') = 0,
    'ALTER TABLE oa_sign_package_document ADD COLUMN signed_pdf_hash varchar(64) DEFAULT NULL COMMENT ''已签PDF SHA-256'' AFTER signed_pdf_url', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package_document' AND COLUMN_NAME = 'signature_file_url') = 0,
    'ALTER TABLE oa_sign_package_document ADD COLUMN signature_file_url varchar(500) DEFAULT NULL COMMENT ''签名图片'' AFTER signed_pdf_hash', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package_document' AND COLUMN_NAME = 'signature_hash') = 0,
    'ALTER TABLE oa_sign_package_document ADD COLUMN signature_hash varchar(64) DEFAULT NULL COMMENT ''签名图片 SHA-256'' AFTER signature_file_url', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package_document' AND COLUMN_NAME = 'certificate_hash') = 0,
    'ALTER TABLE oa_sign_package_document ADD COLUMN certificate_hash varchar(64) DEFAULT NULL COMMENT ''签署证书 SHA-256'' AFTER signature_hash', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package_document' AND COLUMN_NAME = 'document_version') = 0,
    'ALTER TABLE oa_sign_package_document ADD COLUMN document_version varchar(64) DEFAULT NULL COMMENT ''文档版本'' AFTER certificate_hash', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_event' AND COLUMN_NAME = 'request_id') = 0,
    'ALTER TABLE oa_sign_event ADD COLUMN request_id varchar(64) DEFAULT NULL COMMENT ''幂等请求ID'' AFTER event_hash', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_event' AND INDEX_NAME = 'uk_oa_sign_event_request') = 0,
    'ALTER TABLE oa_sign_event ADD UNIQUE KEY uk_oa_sign_event_request (event_type, request_id)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS oa_sign_file_evidence (
    evidence_id bigint NOT NULL AUTO_INCREMENT COMMENT '证据ID',
    package_id bigint NOT NULL COMMENT '签约包ID',
    document_id bigint DEFAULT NULL COMMENT '签约文件ID',
    document_version varchar(64) NOT NULL COMMENT '文档版本',
    evidence_type varchar(32) NOT NULL COMMENT '证据类型',
    file_url varchar(500) NOT NULL COMMENT '归档相对路径',
    file_hash varchar(64) NOT NULL COMMENT 'SHA-256',
    file_size bigint NOT NULL COMMENT '文件字节数',
    source_evidence_id bigint DEFAULT NULL COMMENT '来源证据ID',
    generated_time datetime NOT NULL COMMENT '生成时间',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (evidence_id),
    UNIQUE KEY uk_oa_sign_file_evidence_version (document_id, document_version, evidence_type),
    KEY idx_oa_sign_file_evidence_package (package_id, evidence_id),
    KEY idx_oa_sign_file_evidence_source (source_evidence_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='签约文件不可变证据';

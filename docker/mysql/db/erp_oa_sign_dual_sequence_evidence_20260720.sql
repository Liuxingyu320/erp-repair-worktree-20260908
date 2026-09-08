-- Dual signing sequence, task-scoped signature sample and immutable final evidence.
-- MySQL 5.7/8.x compatible; safe to execute repeatedly.

DROP PROCEDURE IF EXISTS add_oa_sign_column_20260720;
DELIMITER $$
CREATE PROCEDURE add_oa_sign_column_20260720(
    IN p_table_name varchar(64),
    IN p_column_name varchar(64),
    IN p_definition text
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND BINARY TABLE_NAME = BINARY p_table_name
           AND BINARY COLUMN_NAME = BINARY p_column_name
    ) THEN
        SET @oa_sign_ddl_20260720 = CONCAT(
            'ALTER TABLE `', REPLACE(p_table_name, '`', '``'),
            '` ADD COLUMN `', REPLACE(p_column_name, '`', '``'), '` ', p_definition
        );
        PREPARE oa_sign_stmt_20260720 FROM @oa_sign_ddl_20260720;
        EXECUTE oa_sign_stmt_20260720;
        DEALLOCATE PREPARE oa_sign_stmt_20260720;
    END IF;
END$$
DELIMITER ;

CALL add_oa_sign_column_20260720('oa_sign_package', 'signing_sequence',
    'varchar(24) DEFAULT NULL COMMENT ''签署顺序 COMPANY_FIRST/SIGNATURE_FIRST''');
CALL add_oa_sign_column_20260720('oa_sign_package', 'signature_sample_file_url',
    'varchar(500) DEFAULT NULL COMMENT ''本任务手写签名样本地址''');
CALL add_oa_sign_column_20260720('oa_sign_package', 'signature_sample_hash',
    'char(64) DEFAULT NULL COMMENT ''本任务手写签名样本SHA-256''');
CALL add_oa_sign_column_20260720('oa_sign_package', 'signature_sample_time',
    'datetime DEFAULT NULL COMMENT ''本任务手写签名留存时间''');
CALL add_oa_sign_column_20260720('oa_sign_package', 'company_frozen_time',
    'datetime DEFAULT NULL COMMENT ''公司主数据和印章快照冻结时间''');
CALL add_oa_sign_column_20260720('oa_sign_package', 'final_archive_root_hash',
    'char(64) DEFAULT NULL COMMENT ''最终归档文件集合SHA-256''');
CALL add_oa_sign_column_20260720('oa_sign_package', 'final_evidence_generated_time',
    'datetime DEFAULT NULL COMMENT ''最终证据页生成时间''');

CALL add_oa_sign_column_20260720('oa_sign_package_document', 'final_content_hash',
    'char(64) DEFAULT NULL COMMENT ''不含证据页的稳定正文SHA-256''');
CALL add_oa_sign_column_20260720('oa_sign_package_document', 'final_archive_pdf_url',
    'varchar(500) DEFAULT NULL COMMENT ''最终确认后的不可变归档PDF地址''');
CALL add_oa_sign_column_20260720('oa_sign_package_document', 'final_archive_pdf_hash',
    'char(64) DEFAULT NULL COMMENT ''最终确认后的归档PDF SHA-256''');
CALL add_oa_sign_column_20260720('oa_sign_package_document', 'final_read_confirmed_time',
    'datetime DEFAULT NULL COMMENT ''员工首次打开当前最终版本时间''');

CALL add_oa_sign_column_20260720('oa_sign_onboard_data_request', 'signing_sequence',
    'varchar(24) NOT NULL DEFAULT ''COMPANY_FIRST'' COMMENT ''员工补资/签名顺序快照''');
CALL add_oa_sign_column_20260720('oa_sign_onboard_data_request', 'fact_snapshot_json',
    'json DEFAULT NULL COMMENT ''员工签名前只读事实快照''');
CALL add_oa_sign_column_20260720('oa_sign_onboard_data_request', 'confirmation_snapshot_version',
    'varchar(32) DEFAULT NULL COMMENT ''员工确认事实与文件清单规则版本''');
CALL add_oa_sign_column_20260720('oa_sign_onboard_data_request', 'confirmation_snapshot_hash',
    'char(64) DEFAULT NULL COMMENT ''员工确认事实与文件清单SHA-256''');
CALL add_oa_sign_column_20260720('oa_sign_onboard_data_request', 'fact_confirmation_text',
    'varchar(120) DEFAULT NULL COMMENT ''员工签名前确认短语''');
CALL add_oa_sign_column_20260720('oa_sign_onboard_data_request', 'signature_request_id',
    'varchar(64) DEFAULT NULL COMMENT ''签名提交幂等请求ID''');
CALL add_oa_sign_column_20260720('oa_sign_onboard_data_request', 'signature_payload_hash',
    'char(64) DEFAULT NULL COMMENT ''签名提交规范负载SHA-256''');
CALL add_oa_sign_column_20260720('oa_sign_onboard_data_request', 'signature_sample_bytes',
    'mediumblob DEFAULT NULL COMMENT ''仅限本请求的手写签名PNG''');
CALL add_oa_sign_column_20260720('oa_sign_onboard_data_request', 'signature_sample_hash',
    'char(64) DEFAULT NULL COMMENT ''本请求手写签名SHA-256''');
CALL add_oa_sign_column_20260720('oa_sign_onboard_data_request', 'signature_sample_time',
    'datetime DEFAULT NULL COMMENT ''本请求手写签名留存时间''');

DROP PROCEDURE IF EXISTS add_oa_sign_column_20260720;

DROP PROCEDURE IF EXISTS add_oa_sign_index_20260720;
DELIMITER $$
CREATE PROCEDURE add_oa_sign_index_20260720(
    IN p_table_name varchar(64),
    IN p_index_name varchar(64),
    IN p_definition text
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE()
           AND BINARY TABLE_NAME = BINARY p_table_name
           AND BINARY INDEX_NAME = BINARY p_index_name
    ) THEN
        SET @oa_sign_index_ddl_20260720 = CONCAT(
            'ALTER TABLE `', REPLACE(p_table_name, '`', '``'),
            '` ADD ', p_definition
        );
        PREPARE oa_sign_index_stmt_20260720 FROM @oa_sign_index_ddl_20260720;
        EXECUTE oa_sign_index_stmt_20260720;
        DEALLOCATE PREPARE oa_sign_index_stmt_20260720;
    END IF;
END$$
DELIMITER ;

CALL add_oa_sign_index_20260720('oa_sign_onboard_data_request',
    'uk_oa_sign_onboard_signature_request',
    'UNIQUE INDEX uk_oa_sign_onboard_signature_request (signature_request_id)');
CALL add_oa_sign_index_20260720('oa_sign_package_document',
    'idx_oa_sign_final_read_version',
    'INDEX idx_oa_sign_final_read_version (package_id, final_document_version, final_read_confirmed)');

DROP PROCEDURE IF EXISTS add_oa_sign_index_20260720;

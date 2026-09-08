-- 系统操作日志历史敏感正文脱敏（单独审批执行，不进入普通结构迁移）。
-- 执行前必须设置：
--   SET @erp_oper_log_redaction_batch = '工单批准的批次ID';
--   SET @erp_oper_log_redaction_ticket = '安全工单号';
--   SET @erp_oper_log_redaction_operator = '执行人标识';

SET @erp_redaction_batch = NULLIF(TRIM(COALESCE(@erp_oper_log_redaction_batch, '')), '');
SET @erp_redaction_ticket = NULLIF(TRIM(COALESCE(@erp_oper_log_redaction_ticket, '')), '');
SET @erp_redaction_operator = NULLIF(TRIM(COALESCE(@erp_oper_log_redaction_operator, '')), '');
SET @erp_redaction_guard_sql = IF(
    @erp_redaction_batch REGEXP '^[A-Za-z0-9._-]{6,64}$'
    AND @erp_redaction_ticket REGEXP '^[A-Za-z0-9._-]{3,64}$'
    AND @erp_redaction_operator REGEXP '^[A-Za-z0-9._@-]{2,64}$',
    'DO 0',
    'SELECT * FROM __erp_oper_log_redaction_approval_missing__'
);
PREPARE erp_redaction_guard_stmt FROM @erp_redaction_guard_sql;
EXECUTE erp_redaction_guard_stmt;
DEALLOCATE PREPARE erp_redaction_guard_stmt;

CREATE TABLE IF NOT EXISTS sys_oper_log_redaction_audit (
    batch_id varchar(64) NOT NULL,
    ticket_no varchar(64) NOT NULL,
    execute_user varchar(64) NOT NULL,
    matched_rows bigint NOT NULL DEFAULT 0,
    minimum_oper_id bigint NULL,
    maximum_oper_id bigint NULL,
    length_checksum decimal(30,0) NOT NULL DEFAULT 0,
    execute_status varchar(16) NOT NULL,
    create_time datetime NOT NULL,
    complete_time datetime NULL,
    PRIMARY KEY (batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志敏感正文脱敏审计（不保存正文）';

SET @erp_redaction_existing = (
    SELECT COUNT(*) FROM sys_oper_log_redaction_audit WHERE batch_id = @erp_redaction_batch
);
SET @erp_redaction_replay_guard = IF(
    @erp_redaction_existing = 0,
    'DO 0',
    'SELECT * FROM __erp_oper_log_redaction_batch_already_used__'
);
PREPARE erp_redaction_replay_stmt FROM @erp_redaction_replay_guard;
EXECUTE erp_redaction_replay_stmt;
DEALLOCATE PREPARE erp_redaction_replay_stmt;

DROP TEMPORARY TABLE IF EXISTS tmp_erp_sensitive_oper_log_ids;
CREATE TEMPORARY TABLE tmp_erp_sensitive_oper_log_ids (
    oper_id bigint NOT NULL,
    PRIMARY KEY (oper_id)
) ENGINE=InnoDB;

INSERT INTO tmp_erp_sensitive_oper_log_ids (oper_id)
SELECT oper_id
FROM sys_oper_log
WHERE LOWER(CONCAT_WS(' ', oper_param, json_result, error_msg)) REGEXP
      'signaturedataurl|password|passwd|secret|token|credential|bankaccount|bankcard|idnumber|idcard|privatekey|accesskey|phonenumber|;base64,|-----begin (rsa )?private key-----|/users/|/home/|/root/'
   OR CONCAT_WS(' ', oper_param, json_result, error_msg) REGEXP '(^|[^0-9])[0-9]{15,19}([^0-9]|$)';

START TRANSACTION;

INSERT INTO sys_oper_log_redaction_audit (
    batch_id, ticket_no, execute_user, matched_rows, minimum_oper_id, maximum_oper_id,
    length_checksum, execute_status, create_time
)
SELECT @erp_redaction_batch,
       @erp_redaction_ticket,
       @erp_redaction_operator,
       COUNT(*),
       MIN(l.oper_id),
       MAX(l.oper_id),
       COALESCE(SUM(
           CRC32(CONCAT(
               l.oper_id, ':',
               COALESCE(CHAR_LENGTH(l.oper_param), 0), ':',
               COALESCE(CHAR_LENGTH(l.json_result), 0), ':',
               COALESCE(CHAR_LENGTH(l.error_msg), 0)
           ))
       ), 0),
       'RUNNING',
       NOW()
FROM tmp_erp_sensitive_oper_log_ids i
JOIN sys_oper_log l ON l.oper_id = i.oper_id;

UPDATE sys_oper_log l
JOIN tmp_erp_sensitive_oper_log_ids i ON i.oper_id = l.oper_id
SET l.oper_param = CASE
        WHEN NULLIF(TRIM(l.oper_param), '') IS NULL THEN l.oper_param
        ELSE '[REDACTED:SENSITIVE_REQUEST_PAYLOAD]'
    END,
    l.json_result = CASE
        WHEN NULLIF(TRIM(l.json_result), '') IS NULL THEN l.json_result
        ELSE '[REDACTED:SENSITIVE_RESPONSE_PAYLOAD]'
    END,
    l.error_msg = CASE
        WHEN NULLIF(TRIM(l.error_msg), '') IS NULL THEN l.error_msg
        ELSE '[REDACTED:SENSITIVE_ERROR_DETAIL]'
    END;

UPDATE sys_oper_log_redaction_audit
SET execute_status = 'COMPLETED', complete_time = NOW()
WHERE batch_id = @erp_redaction_batch;

COMMIT;

SELECT batch_id, ticket_no, execute_user, matched_rows, minimum_oper_id, maximum_oper_id,
       length_checksum, execute_status, create_time, complete_time
FROM sys_oper_log_redaction_audit
WHERE batch_id = @erp_redaction_batch;

DROP TEMPORARY TABLE IF EXISTS tmp_erp_sensitive_oper_log_ids;

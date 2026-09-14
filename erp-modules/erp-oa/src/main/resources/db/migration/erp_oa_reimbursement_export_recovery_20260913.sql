-- Apply before enabling export command recovery. Existing ZIPs and export flags are unchanged.
-- Retain this table and its receipts when rolling back the UI; never recreate archived batches.
CREATE TABLE IF NOT EXISTS oa_reimbursement_export_command (
  actor_id bigint NOT NULL,
  request_id varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  payload_hash char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  batch_id bigint DEFAULT NULL,
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (actor_id, request_id),
  UNIQUE KEY uk_reimbursement_export_command_batch (batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报销导出命令与原归档批次回执';

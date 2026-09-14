-- P04: durable scheduler deletion receipts and technical definition revisions.
-- Preserve these tables/revisions during rollback; never replay without the exact revision.
SET @job_revision_exists = (SELECT COUNT(*) FROM information_schema.columns
 WHERE table_schema=DATABASE() AND table_name='sys_job' AND column_name='revision');
SET @job_revision_sql = IF(@job_revision_exists=0,
 'ALTER TABLE sys_job ADD COLUMN revision VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT ''''', 'SELECT 1');
PREPARE job_revision_stmt FROM @job_revision_sql;
EXECUTE job_revision_stmt;
DEALLOCATE PREPARE job_revision_stmt;
UPDATE sys_job SET revision=UUID() WHERE revision='';
CREATE TABLE IF NOT EXISTS sys_job_delete_batch (
 batch_id varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 actor_id bigint NOT NULL,
 fingerprint char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 create_time datetime(3) NOT NULL,
 PRIMARY KEY(batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS sys_job_delete_intent (
 intent_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 batch_id varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 job_id bigint NOT NULL,
 job_group varchar(64) NOT NULL,
 revision varchar(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 status varchar(24) NOT NULL,
 attempts int NOT NULL DEFAULT 0,
 error_code varchar(64) DEFAULT NULL,
 create_time datetime(3) NOT NULL,
 update_time datetime(3) NOT NULL,
 PRIMARY KEY(intent_id),
 UNIQUE KEY uk_job_delete_batch_job(batch_id,job_id),
 KEY idx_job_delete_pending(status,update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

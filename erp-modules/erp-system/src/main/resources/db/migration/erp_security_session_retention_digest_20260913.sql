-- P01: preserve the current self-service password-change session during outbox retry.
-- Prerequisite: erp_system_management_expand_20260714.sql. Apply before updated system code.
-- Only one-way user-bound session identifiers are stored; never raw internal IDs, tokens or JWTs.
-- Existing rows stay NULL (their existing invalidate-all behavior is retained). No historical backfill.
-- Keep the additive column when rolling code back; old writers omit it and produce NULL.
SET @session_digest_exists = (SELECT COUNT(*) FROM information_schema.columns
 WHERE table_schema=DATABASE() AND table_name='sys_security_session_outbox' AND column_name='retained_session_digest');
SET @session_digest_sql = IF(@session_digest_exists=0,
 'ALTER TABLE sys_security_session_outbox ADD COLUMN retained_session_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL', 'SELECT 1');
PREPARE session_digest_stmt FROM @session_digest_sql;
EXECUTE session_digest_stmt;
DEALLOCATE PREPARE session_digest_stmt;

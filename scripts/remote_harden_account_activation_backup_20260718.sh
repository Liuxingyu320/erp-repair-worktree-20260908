#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

DB="bosserp_stock_state_75c59ee"
BACKUP_DIR="/opt/erp-new-data-backups/account-activation-20260717T221343Z"
SOURCE="$BACKUP_DIR/sys_user-before.sql.gz"
SAFE_SQL="$BACKUP_DIR/sys_user-targeted-rollback.sql"
PASS_FILE="/root/.erp-mysql-root-pass"
MYSQL_CNF="$(mktemp /tmp/account-backup-harden.XXXXXX.cnf)"
STAGE_DB="erp_account_restore_stage_20260717_221343"
STAGE_CREATED=0

cleanup() {
  if [[ "$STAGE_CREATED" == "1" ]]; then
    mysql --defaults-extra-file="$MYSQL_CNF" --protocol=socket \
      -e "DROP DATABASE IF EXISTS \`$STAGE_DB\`" >/dev/null 2>&1 || true
  fi
  if command -v shred >/dev/null 2>&1; then
    shred -u "$MYSQL_CNF" 2>/dev/null || rm -f "$MYSQL_CNF"
  else
    rm -f "$MYSQL_CNF"
  fi
}
trap cleanup EXIT

[[ -d "$BACKUP_DIR" && -f "$SOURCE" && ! -e "$SAFE_SQL" ]]
(cd "$BACKUP_DIR" && sha256sum -c sys_user-before.sql.gz.sha256)

python3 - "$PASS_FILE" "$MYSQL_CNF" <<'PY'
import os, sys
from pathlib import Path
password = Path(sys.argv[1]).read_text(encoding="utf-8").rstrip("\r\n")
if not password or "\n" in password or "\r" in password:
    raise SystemExit("invalid mysql password file")
escaped = password.replace("\\", "\\\\").replace('"', '\\"')
Path(sys.argv[2]).write_text('[client]\nuser=root\npassword="' + escaped + '"\n', encoding="utf-8")
os.chmod(sys.argv[2], 0o600)
PY

if mysql --defaults-extra-file="$MYSQL_CNF" --protocol=socket --batch --skip-column-names \
  -e "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='$STAGE_DB'" \
  | grep -q .; then
  echo "STAGING_DATABASE_ALREADY_EXISTS" >&2
  exit 10
fi
mysql --defaults-extra-file="$MYSQL_CNF" --protocol=socket \
  -e "CREATE DATABASE \`$STAGE_DB\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
STAGE_CREATED=1
gzip -dc "$SOURCE" | mysql --defaults-extra-file="$MYSQL_CNF" --protocol=socket "$STAGE_DB"

stage_state="$(mysql --defaults-extra-file="$MYSQL_CNF" --protocol=socket --batch --raw \
  --skip-column-names "$STAGE_DB" <<'SQL'
SELECT CONCAT_WS('/',COUNT(*),SUM(status='1'),SUM(credential_state='CHANGE_REQUIRED'),
  SUM(temporary_password_expires_at IS NULL))
FROM sys_user WHERE user_id IN (1095,1096,1097,1098,1099,1100);
SQL
)"
[[ "$stage_state" == "6/6/6/6" ]] || {
  echo "STAGING_BACKUP_CONTENT_INVALID actual=$stage_state" >&2; exit 11;
}

rollback_table="rollback_sys_user_20260717_221343"
cat > "$SAFE_SQL" <<EOF
-- Targeted rollback generated from the verified pre-activation snapshot.
-- Run only after explicit approval. This file updates the six target rows and
-- never drops or recreates the production sys_user table.
START TRANSACTION;
CREATE TEMPORARY TABLE \`$rollback_table\` LIKE \`sys_user\`;
EOF

mysqldump --defaults-extra-file="$MYSQL_CNF" --protocol=socket \
  --no-create-info --complete-insert --skip-add-locks --skip-disable-keys --compact \
  --set-gtid-purged=OFF --column-statistics=0 \
  "$STAGE_DB" sys_user --where='user_id IN (1095,1096,1097,1098,1099,1100)' \
  | sed "s/\`sys_user\`/\`$rollback_table\`/g" >> "$SAFE_SQL"

set_clause="$(mysql --defaults-extra-file="$MYSQL_CNF" --protocol=socket --batch --raw \
  --skip-column-names "$DB" <<'SQL'
SET SESSION group_concat_max_len=100000;
SELECT GROUP_CONCAT(CONCAT('target.`',COLUMN_NAME,'`=source.`',COLUMN_NAME,'`')
  ORDER BY ORDINAL_POSITION SEPARATOR ',\n  ')
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_user' AND COLUMN_NAME<>'user_id';
SQL
)"
[[ -n "$set_clause" ]]
cat >> "$SAFE_SQL" <<EOF
UPDATE \`sys_user\` AS target
JOIN \`$rollback_table\` AS source ON source.user_id=target.user_id
SET $set_clause
WHERE target.user_id IN (1095,1096,1097,1098,1099,1100);
COMMIT;
DROP TEMPORARY TABLE IF EXISTS \`$rollback_table\`;
EOF

chmod 600 "$SAFE_SQL"
if grep -Eq '(^|[[:space:]])DROP[[:space:]]+TABLE[[:space:]]+`sys_user`|CREATE[[:space:]]+TABLE[[:space:]]+`sys_user`' "$SAFE_SQL"; then
  echo "UNSAFE_ROLLBACK_SQL_DETECTED" >&2
  exit 12
fi
gzip -9 "$SAFE_SQL"
chmod 600 "$SAFE_SQL.gz"
sha256sum "$SAFE_SQL.gz" > "$SAFE_SQL.gz.sha256"
chmod 600 "$SAFE_SQL.gz.sha256"

cat > "$BACKUP_DIR/RESTORE-WARNING.txt" <<'EOF'
WARNING: sys_user-before.sql.gz is a mysqldump staging artifact that contains
table DDL. NEVER import it directly into the production database.

The approved targeted recovery artifact is:
  sys_user-targeted-rollback.sql.gz

It only restores user IDs 1095-1100 by UPDATE. Applying any rollback still
requires an explicit maintenance decision and a fresh post-change backup.
EOF
chmod 600 "$BACKUP_DIR/RESTORE-WARNING.txt"
cat >> "$BACKUP_DIR/manifest.txt" <<'EOF'
staging_snapshot_warning=NEVER_IMPORT_sys_user-before.sql.gz_DIRECTLY
targeted_rollback=sys_user-targeted-rollback.sql.gz
EOF

echo "ACCOUNT_BACKUP_HARDENED stage_state=$stage_state"
echo "TARGETED_ROLLBACK_OK file=$SAFE_SQL.gz"

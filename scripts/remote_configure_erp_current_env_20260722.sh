#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

CURRENT_LINK="/opt/erp-new"
STAMP="$(date +%Y%m%d%H%M%S)"
BACKUP_DIR="/opt/erp-new-data-backups/erp-current-env-${STAMP}"
LOCK_FILE="/run/lock/erp-current-env-20260722.lock"

exec 9>"$LOCK_FILE"
flock -n 9 || { echo "another ERP environment update is running" >&2; exit 70; }

RELEASE_ROOT="$(readlink -f "$CURRENT_LINK")"
test -d "$RELEASE_ROOT"
ENV_FILE="$RELEASE_ROOT/.env"
test -f "$ENV_FILE"
test ! -L "$ENV_FILE"

KEYS=(
  OA_SIGN_EXCEL_IMPORT_ENABLED
  HR_SIGN_LIFECYCLE_AUTOMATION_ENABLED
  OA_SIGN_EXPIRY_ENABLED
  OA_SIGN_REMINDER_ENABLED
  OA_SIGN_EMERGENCY_CREATE_ENABLED
  PROFILE_COMPLETION_GATE_ENABLED
)
for key in "${KEYS[@]}"; do
  count="$(grep -Ec "^${key}=" "$ENV_FILE" || true)"
  test "$count" -le 1
done

mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"
cp -a "$ENV_FILE" "$BACKUP_DIR/.env.before"
sha256sum "$BACKUP_DIR/.env.before" > "$BACKUP_DIR/.env.before.sha256"

TEMP_FILE="$(mktemp "$RELEASE_ROOT/.env.current-20260722.XXXXXX")"
cleanup() { rm -f -- "$TEMP_FILE"; }
trap cleanup EXIT

awk '
BEGIN {
  desired["OA_SIGN_EXCEL_IMPORT_ENABLED"]="true"
  desired["HR_SIGN_LIFECYCLE_AUTOMATION_ENABLED"]="false"
  desired["OA_SIGN_EXPIRY_ENABLED"]="false"
  desired["OA_SIGN_REMINDER_ENABLED"]="false"
  desired["OA_SIGN_EMERGENCY_CREATE_ENABLED"]="false"
  desired["PROFILE_COMPLETION_GATE_ENABLED"]="true"
}
{
  line=$0
  split(line, pair, "=")
  key=pair[1]
  if (key in desired) {
    if (!seen[key]++) print key "=" desired[key]
    next
  }
  print line
}
END {
  order[1]="OA_SIGN_EXCEL_IMPORT_ENABLED"
  order[2]="HR_SIGN_LIFECYCLE_AUTOMATION_ENABLED"
  order[3]="OA_SIGN_EXPIRY_ENABLED"
  order[4]="OA_SIGN_REMINDER_ENABLED"
  order[5]="OA_SIGN_EMERGENCY_CREATE_ENABLED"
  order[6]="PROFILE_COMPLETION_GATE_ENABLED"
  for (i=1; i<=6; i++) if (!seen[order[i]]) print order[i] "=" desired[order[i]]
}
' "$ENV_FILE" > "$TEMP_FILE"

test -s "$TEMP_FILE"
chown --reference="$ENV_FILE" "$TEMP_FILE"
chmod --reference="$ENV_FILE" "$TEMP_FILE"
mv "$TEMP_FILE" "$ENV_FILE"
trap - EXIT

test "$(grep -Ec '^OA_SIGN_EXCEL_IMPORT_ENABLED=true$' "$ENV_FILE")" = 1
test "$(grep -Ec '^HR_SIGN_LIFECYCLE_AUTOMATION_ENABLED=false$' "$ENV_FILE")" = 1
test "$(grep -Ec '^OA_SIGN_EXPIRY_ENABLED=false$' "$ENV_FILE")" = 1
test "$(grep -Ec '^OA_SIGN_REMINDER_ENABLED=false$' "$ENV_FILE")" = 1
test "$(grep -Ec '^OA_SIGN_EMERGENCY_CREATE_ENABLED=false$' "$ENV_FILE")" = 1
test "$(grep -Ec '^PROFILE_COMPLETION_GATE_ENABLED=true$' "$ENV_FILE")" = 1

echo "ERP_ENV_CONFIG_OK backup=$BACKUP_DIR excel_import=true profile_gate=true automation=false expiry=false reminder=false emergency=false"

#!/usr/bin/env bash
set -euo pipefail

jar=/opt/erp-new/erp/modules/oa/jar/erp-modules-oa.jar
service=erp-new@oa.service
uploaded=/tmp/codex-OaSignPlanVersionServiceImpl-20260724.class
entry=BOOT-INF/classes/com/erp/oa/service/impl/OaSignPlanVersionServiceImpl.class
expected_before=c6416f151757eb6d079ab908b59c26497eeb90d2c25173cef497a91679f10cee
expected_after=ca95779b9e9b60c76219f30e44a0cedfdaf581088c8bbfa56e751dc80a228a6a

[[ -f "$jar" && -s "$uploaded" ]]
actual_before="$(unzip -p "$jar" "$entry" | sha256sum | awk '{print $1}')"
actual_upload="$(sha256sum "$uploaded" | awk '{print $1}')"
[[ "$actual_before" = "$expected_before" ]] || {
  echo "BASELINE_CLASS_HASH_MISMATCH actual=$actual_before"
  exit 2
}
[[ "$actual_upload" = "$expected_after" ]] || {
  echo "UPLOAD_CLASS_HASH_MISMATCH actual=$actual_upload"
  exit 3
}

timestamp="$(date +%Y%m%d%H%M%S)"
backup_dir="/opt/erp-new-backups/onboard-commitment-publish-guard-$timestamp"
mkdir -p "$backup_dir"
cp -a "$jar" "$backup_dir/erp-modules-oa.jar"
sha256sum "$backup_dir/erp-modules-oa.jar" > "$backup_dir/erp-modules-oa.jar.sha256"

stage="$(mktemp -d)"
trap 'rm -rf "$stage"; rm -f "$uploaded"' EXIT
mkdir -p "$stage/$(dirname "$entry")"
cp "$uploaded" "$stage/$entry"
(cd "$stage" && zip -q -u "$jar" "$entry")

unzip -tq "$jar" >/dev/null
actual_after="$(unzip -p "$jar" "$entry" | sha256sum | awk '{print $1}')"
[[ "$actual_after" = "$expected_after" ]] || {
  cp -a "$backup_dir/erp-modules-oa.jar" "$jar"
  echo "UPDATED_CLASS_HASH_MISMATCH actual=$actual_after rollback=completed"
  exit 4
}

systemctl restart "$service"
healthy=0
for _ in $(seq 1 45); do
  if systemctl is-active --quiet "$service" &&
    curl -fsS --max-time 3 http://127.0.0.1:9204/actuator/health |
      grep -q '"status"[[:space:]]*:[[:space:]]*"UP"'; then
    healthy=1
    break
  fi
  sleep 1
done

if [[ "$healthy" != 1 ]]; then
  cp -a "$backup_dir/erp-modules-oa.jar" "$jar"
  systemctl restart "$service"
  echo "OA_HEALTHCHECK_FAILED rollback=completed backup=$backup_dir"
  exit 5
fi

echo "DEPLOY_OK service=$service backup=$backup_dir class_sha256=$actual_after jar_sha256=$(sha256sum "$jar" | awk '{print $1}')"

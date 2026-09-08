#!/usr/bin/env bash
set -euo pipefail

jar=/opt/erp-new/erp/modules/oa/jar/erp-modules-oa.jar
service=erp-new@oa.service
uploaded_dir=/tmp/codex-service-template-v8
entry_dir=BOOT-INF/classes/com/erp/oa/service/impl

main_entry="$entry_dir/OaSignDocumentService.class"
inner_entry="$entry_dir/OaSignDocumentService\$RenderedText.class"
main_upload="$uploaded_dir/OaSignDocumentService.class"
inner_upload="$uploaded_dir/OaSignDocumentService\$RenderedText.class"

expected_main_before=cad973d74149478779d039cd7cfa6d52c47a26e4d2471aad4688f83b801d7197
expected_inner_before=9c4b14dac1c86573bc5ad02b9a956d1f48dc7d031a42979bb718404a770a47c1
expected_main_after=0109f37c5dcdca17f38ac0bacd84266046bf2e1e9a88b5348bb9facced96e899
expected_inner_after=d9c7ecb35cf96c3d57416da682126999c2644e3c2809667cdd8694738a83b3f1

hash_entry() {
  unzip -p "$jar" "$1" | sha256sum | awk '{print $1}'
}

[[ -f "$jar" && -s "$main_upload" && -s "$inner_upload" ]]
[[ "$(hash_entry "$main_entry")" = "$expected_main_before" ]]
[[ "$(hash_entry "$inner_entry")" = "$expected_inner_before" ]]
[[ "$(sha256sum "$main_upload" | awk '{print $1}')" = "$expected_main_after" ]]
[[ "$(sha256sum "$inner_upload" | awk '{print $1}')" = "$expected_inner_after" ]]

timestamp="$(date +%Y%m%d%H%M%S)"
backup_dir="/opt/erp-new-backups/service-template-v8-$timestamp"
mkdir -p "$backup_dir"
cp -a "$jar" "$backup_dir/erp-modules-oa.jar"
sha256sum "$backup_dir/erp-modules-oa.jar" > "$backup_dir/erp-modules-oa.jar.sha256"

stage="$(mktemp -d)"
cleanup() {
  rm -rf "$stage" "$uploaded_dir"
}
trap cleanup EXIT
mkdir -p "$stage/$entry_dir"
cp "$main_upload" "$stage/$main_entry"
cp "$inner_upload" "$stage/$inner_entry"
(cd "$stage" && zip -q -u "$jar" "$main_entry" "$inner_entry")

unzip -tq "$jar" >/dev/null
if [[ "$(hash_entry "$main_entry")" != "$expected_main_after" ||
      "$(hash_entry "$inner_entry")" != "$expected_inner_after" ]]; then
  cp -a "$backup_dir/erp-modules-oa.jar" "$jar"
  echo "UPDATED_CLASS_HASH_MISMATCH rollback=completed"
  exit 4
fi

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

echo "DEPLOY_OK service=$service backup=$backup_dir main_sha256=$expected_main_after"

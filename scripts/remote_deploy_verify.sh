#!/usr/bin/env bash
set -Eeuo pipefail

REMOTE_ROOT="${ERP_REMOTE_ROOT:-/opt/erp-new}"
RESOLVED_ROOT="$(readlink -f "$REMOTE_ROOT")"
MANIFEST="${ERP_RELEASE_MANIFEST:-$RESOLVED_ROOT/release/system-management-release-20260714.json}"
ENV_FILE="${ERP_ENV_FILE:-$RESOLVED_ROOT/.env}"
SHARED_UPLOAD_ROOT="/data/erp-new-data/uploadPath"
FILE_PATH="$SHARED_UPLOAD_ROOT"
SIGN_PACKAGE_STORAGE_ROOT="$SHARED_UPLOAD_ROOT/private/sign-package"
OA_ATTENDANCE_STORAGE_ROOT="$SHARED_UPLOAD_ROOT/private/attendance"
OA_REIMBURSEMENT_STORAGE_ROOT="$SHARED_UPLOAD_ROOT/private/reimbursement"
DRIVE_LOCAL_PATH="$SHARED_UPLOAD_ROOT/private/drive"
SERVICE_PORTS="gateway:8080 auth:9200 monitor:9100 system:9201 job:9203 oa:9204 inventory:9205 file:9300 approval:9206"
failed=0

printf 'date=%s\n' "$(date '+%F %T %z')"
printf 'current=%s\n' "$RESOLVED_ROOT"
test -d "$RESOLVED_ROOT"

if [[ "${ERP_SKIP_FINALIZED_RELEASE_MANIFEST:-false}" == true ]]; then
    printf 'WARNING: finalized release manifest verification skipped for one-time legacy preflight\n' >&2
else
python3 - "$RESOLVED_ROOT" "$MANIFEST" <<'PY'
import hashlib
import json
import re
import sys
import zipfile
from pathlib import Path

root = Path(sys.argv[1]).resolve()
manifest_path = Path(sys.argv[2]).resolve()
release_id = "system-management-20260714"
expected_artifacts = {
    "gateway": ("jar", "docker/erp/gateway/jar/erp-gateway.jar"),
    "auth": ("jar", "docker/erp/auth/jar/erp-auth.jar"),
    "monitor": ("jar", "docker/erp/visual/monitor/jar/erp-visual-monitor.jar"),
    "system": ("jar", "docker/erp/modules/system/jar/erp-modules-system.jar"),
    "file": ("jar", "docker/erp/modules/file/jar/erp-modules-file.jar"),
    "job": ("jar", "docker/erp/modules/job/jar/erp-modules-job.jar"),
    "oa": ("jar", "docker/erp/modules/oa/jar/erp-modules-oa.jar"),
    "inventory": ("jar", "docker/erp/modules/inventory/jar/erp-modules-inventory.jar"),
    "approval": ("jar", "docker/erp/modules/approval/jar/erp-modules-approval.jar"),
    "frontend": ("directory", "docker/nginx/html/dist"),
}
required_critical_classes = {
    "com.erp.system.controller.SysLegalEntityController",
    "com.erp.system.controller.SysConfigController",
    "com.erp.system.controller.SysOperlogController",
    "com.erp.system.controller.SysUserController",
    "com.erp.system.service.support.TemporaryPasswordGenerator",
    "com.erp.system.service.support.UserSessionInvalidationService",
    "com.erp.system.service.support.SystemBuildInfoProvider",
    "com.erp.system.domain.vo.SysOperLogListVo",
    "com.erp.system.domain.vo.SysOperLogDetailVo",
    "com.erp.system.domain.vo.SysOperLogExportVo",
    "com.erp.system.domain.vo.SysUserListVo",
    "com.erp.system.domain.vo.SysUserPiiExportVo",
    "com.erp.system.domain.vo.SysBuildInfoVo",
}
try:
    manifest_path.relative_to(root)
except ValueError as exc:
    raise SystemExit("release manifest must be inside the active release") from exc
manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
if manifest.get("releaseId") != release_id:
    raise SystemExit("active release manifest has an unexpected releaseId")
if manifest.get("status") != "ready":
    raise SystemExit("active release manifest is not finalized as ready")
artifacts = manifest.get("artifacts")
if not isinstance(artifacts, list) or not artifacts:
    raise SystemExit("active release artifact inventory is empty")
build = manifest.get("build")
commit = build.get("commit") if isinstance(build, dict) else None
build_time = build.get("buildTime") if isinstance(build, dict) else None
if not isinstance(commit, str) or not re.fullmatch(r"[0-9a-f]{40}", commit):
    raise SystemExit("active release build commit is not finalized")
ids = [item.get("id") for item in artifacts if isinstance(item, dict)]
by_id = {item.get("id"): item for item in artifacts if isinstance(item, dict)}
if len(ids) != len(artifacts) or len(by_id) != len(artifacts):
    raise SystemExit("active release artifact inventory has invalid or duplicate ids")
if set(by_id) != set(expected_artifacts):
    raise SystemExit("active release artifact inventory is incomplete")
for artifact_id, (kind, packaged) in expected_artifacts.items():
    item = by_id[artifact_id]
    if item.get("type") != kind or item.get("packaged") != packaged:
        raise SystemExit(f"active release artifact contract changed: {artifact_id}")

critical_classes = manifest.get("criticalClasses")
if not isinstance(critical_classes, list) or not required_critical_classes.issubset(critical_classes):
    raise SystemExit("active release critical class contract is incomplete")

def file_sha(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()

def directory_sha(path: Path) -> str:
    digest = hashlib.sha256()
    files = sorted(item for item in path.rglob("*") if item.is_file())
    if not files:
        raise SystemExit(f"artifact directory is empty: {path}")
    for item in files:
        if item.is_symlink():
            raise SystemExit(f"artifact directory contains a symlink: {item}")
        digest.update(item.relative_to(path).as_posix().encode("utf-8"))
        digest.update(b"\0")
        digest.update(bytes.fromhex(file_sha(item)))
    return digest.hexdigest()

for item in artifacts:
    packaged = item.get("packaged")
    kind = item.get("type")
    expected = item.get("sha256")
    if not isinstance(packaged, str) or not packaged.startswith("docker/"):
        raise SystemExit(f"unsafe packaged artifact path: {packaged!r}")
    relative = Path(packaged).relative_to("docker")
    if relative.is_absolute() or ".." in relative.parts:
        raise SystemExit(f"unsafe packaged artifact path: {packaged!r}")
    path = (root / relative).resolve()
    try:
        path.relative_to(root)
    except ValueError as exc:
        raise SystemExit(f"artifact escapes active release: {packaged}") from exc
    if not isinstance(expected, str) or not re.fullmatch(r"[0-9a-f]{64}", expected):
        raise SystemExit(f"invalid artifact hash: {item.get('id')}")
    actual = directory_sha(path) if kind == "directory" else file_sha(path)
    if actual != expected:
        raise SystemExit(f"artifact hash mismatch: {item.get('id')}")
    if kind == "jar":
        try:
            with zipfile.ZipFile(path) as archive:
                names = archive.namelist()
                embedded_commit = None
                for candidate in (
                    "BOOT-INF/classes/META-INF/build-info.properties",
                    "META-INF/build-info.properties",
                ):
                    try:
                        content = archive.read(candidate).decode("utf-8")
                    except KeyError:
                        continue
                    for line in content.splitlines():
                        if line.startswith("build.commit="):
                            embedded_commit = line.split("=", 1)[1].strip()
                            break
                    if embedded_commit:
                        break
                if item.get("id") == "system":
                    for fqcn in manifest.get("criticalClasses", []):
                        class_name = "BOOT-INF/classes/" + fqcn.replace(".", "/") + ".class"
                        if class_name not in names:
                            raise SystemExit(f"system critical class is missing: {fqcn}")
        except (OSError, zipfile.BadZipFile) as exc:
            raise SystemExit(f"invalid executable JAR: {item.get('id')}") from exc
        if embedded_commit != commit:
            raise SystemExit(f"JAR build commit mismatch: {item.get('id')}")
        if len(names) != len(set(names)) or any(
            re.search(r"(?:^|/)(?:\._|[^/]+ [0-9]+\.class$)", name) for name in names
        ):
            raise SystemExit(f"JAR contains duplicate/conflict entries: {item.get('id')}")

if manifest.get("prerequisiteReleaseIds") != ["new-business-20260714"]:
    raise SystemExit("active release prerequisite contract changed")
prerequisite_path = root / "release/new-business-release-20260714.json"
prerequisite = json.loads(prerequisite_path.read_text(encoding="utf-8"))
if prerequisite.get("releaseId") != "new-business-20260714":
    raise SystemExit("invalid prerequisite release manifest: new-business-20260714")

release_info_path = root / "nginx/html/dist/release-info.json"
release_info = json.loads(release_info_path.read_text(encoding="utf-8"))
if release_info != {"commit": commit, "buildTime": build_time}:
    raise SystemExit("frontend release-info differs from active release manifest")

print(
    "REMOTE_RELEASE_ARTIFACTS_OK "
    f"release={manifest.get('releaseId')} artifacts={len(artifacts)}"
)
PY
fi

service_ready() {
    local service="$1"
    local port="$2"
    local body
    systemctl is-active --quiet "erp-new@$service.service" || return 1
    body="$(curl -fsS --max-time 4 "http://127.0.0.1:$port/actuator/health")" || return 1
    printf '%s' "$body" | grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"'
}

business_json_ready() {
    local url="$1"
    local host_header="$2"
    local body
    body="$(curl -fsS --max-time 10 -H "Host: $host_header" "$url")" || return 1
    printf '%s' "$body" | python3 -c '
import json
import sys
try:
    payload = json.load(sys.stdin)
except Exception:
    raise SystemExit(1)
code = payload.get("code") if isinstance(payload, dict) else None
raise SystemExit(0 if code == 200 else 1)
'
}

process_has_environment() {
    local service="$1"
    local expected="$2"
    local pid
    pid="$(systemctl show "erp-new@$service.service" --property=MainPID --value)"
    [[ -n "$pid" && "$pid" != 0 && -r "/proc/$pid/environ" ]] || return 1
    tr '\0' '\n' < "/proc/$pid/environ" | grep -Fqx "$expected"
}

unit_property_has_word() {
    local unit="$1"
    local property="$2"
    local expected="$3"
    local value
    local word
    value="$(systemctl show "$unit" --property="$property" --value)" || return 1
    for word in $value; do
        [[ "$word" == "$expected" ]] && return 0
    done
    return 1
}

unit_exec_start_pre_has() {
    local unit="$1"
    local mode="$2"
    local path="$3"
    local expected="argv[]=/usr/bin/test $mode $path"
    local value
    value="$(systemctl show "$unit" --property=ExecStartPre --value)" || return 1
    awk -v expected="$expected" '
        {
            count = split($0, fields, ";")
            for (field_index = 1; field_index <= count; field_index++) {
                field = fields[field_index]
                gsub(/^[[:space:]]+/, "", field)
                gsub(/[[:space:]]+$/, "", field)
                gsub(/[[:space:]]+/, " ", field)
                if (field == expected) found = 1
            }
        }
        END { exit(found ? 0 : 1) }
    ' <<<"$value"
}

storage_unit_contract_ready() {
    local service="$1"
    local root="$2"
    local unit="erp-new@$service.service"
    unit_property_has_word "$unit" RequiresMountsFor "$root" &&
        unit_exec_start_pre_has "$unit" -d "$root" &&
        unit_exec_start_pre_has "$unit" -w "$root"
}

for entry in $SERVICE_PORTS; do
    service="${entry%%:*}"
    port="${entry#*:}"
    ready=false
    attempts=20
    if [[ "${ERP_ALLOW_LEGACY_SERVICE_SET:-false}" == true \
          && ( "$service" == monitor || "$service" == approval ) ]]; then
        attempts=1
    fi
    for _ in $(seq 1 "$attempts"); do
        if service_ready "$service" "$port"; then
            ready=true
            break
        fi
        sleep 3
    done
    if state="$(systemctl is-active "erp-new@$service.service" 2>/dev/null)"; then
        :
    else
        state="${state:-inactive}"
    fi
    printf '%s=%s port=%s readiness=%s\n' "$service" "$state" "$port" "$ready"
    if [[ "$state" != active || "$ready" != true ]]; then
        if [[ "${ERP_ALLOW_LEGACY_SERVICE_SET:-false}" == true \
              && ( "$service" == monitor || "$service" == approval ) ]]; then
            printf 'WARNING: %s is absent from the legacy preflight service set\n' "$service" >&2
        else
            failed=1
        fi
    fi
done

if [[ ! -r "$ENV_FILE" ]]; then
    printf 'active release environment file is unreadable\n' >&2
    failed=1
else
    while IFS='=' read -r name expected; do
        if [[ "$(grep -Ec "^${name}=" "$ENV_FILE")" -ne 1 ]] ||
           [[ "$(grep -Fxc "${name}=${expected}" "$ENV_FILE")" -ne 1 ]]; then
            printf 'active environment differs from common-upload contract: %s\n' "$name" >&2
            failed=1
        fi
    done <<EOF
ERP_UPLOAD_ROOT=$SHARED_UPLOAD_ROOT
FILE_PATH=$FILE_PATH
SIGN_PACKAGE_STORAGE_ROOT=$SIGN_PACKAGE_STORAGE_ROOT
OA_ATTENDANCE_STORAGE_ROOT=$OA_ATTENDANCE_STORAGE_ROOT
OA_REIMBURSEMENT_STORAGE_ROOT=$OA_REIMBURSEMENT_STORAGE_ROOT
DRIVE_LOCAL_PATH=$DRIVE_LOCAL_PATH
OA_SIGN_EXCEL_IMPORT_ENABLED=true
EOF
fi

for relative_path in uploadPath erp/uploadPath; do
    upload_path="$RESOLVED_ROOT/$relative_path"
    if [[ -L "$upload_path" ]]; then
        upload_target="$(readlink -f "$upload_path")"
        printf '%s=%s\n' "$relative_path" "$upload_target"
        if [[ "$upload_target" != "$SHARED_UPLOAD_ROOT" || ! -d "$upload_target" ]]; then
            printf '%s does not resolve to the shared persistent root\n' "$relative_path" >&2
            failed=1
        fi
    elif [[ "${ERP_ALLOW_LEGACY_UPLOAD_PATH:-false}" == true && -d "$upload_path" ]]; then
        printf 'WARNING: legacy release-local path accepted for read-only preflight only: %s\n' "$upload_path" >&2
    else
        printf '%s must be a symlink to %s\n' "$upload_path" "$SHARED_UPLOAD_ROOT" >&2
        failed=1
    fi
done

for storage_service in oa file; do
    unit="erp-new@$storage_service.service"
    storage_unit_contract_ready "$storage_service" "$SHARED_UPLOAD_ROOT" || {
        printf '%s effective mount/start-pre contract is incomplete\n' "$unit" >&2
        failed=1
    }
    pid="$(systemctl show "$unit" --property=MainPID --value)"
    runtime_cwd="$(readlink -f "/proc/$pid/cwd" 2>/dev/null || true)"
    if [[ "$runtime_cwd" != "$RESOLVED_ROOT" ]]; then
        printf '%s working directory differs from active release: %s\n' "$unit" "$runtime_cwd" >&2
        failed=1
    fi
    for assignment in \
        "ERP_UPLOAD_ROOT=$SHARED_UPLOAD_ROOT" \
        "FILE_PATH=$FILE_PATH" \
        "SIGN_PACKAGE_STORAGE_ROOT=$SIGN_PACKAGE_STORAGE_ROOT" \
        "OA_ATTENDANCE_STORAGE_ROOT=$OA_ATTENDANCE_STORAGE_ROOT" \
        "OA_REIMBURSEMENT_STORAGE_ROOT=$OA_REIMBURSEMENT_STORAGE_ROOT" \
        "DRIVE_LOCAL_PATH=$DRIVE_LOCAL_PATH" \
        "OA_SIGN_EXCEL_IMPORT_ENABLED=true"; do
        process_has_environment "$storage_service" "$assignment" || {
            printf '%s runtime environment differs: %s\n' "$unit" "${assignment%%=*}" >&2
            failed=1
        }
    done
done

if find "$RESOLVED_ROOT" -type f \( -name '._*' -o -name '.DS_Store' -o -name '* [0-9]*.*' \) \
    -print -quit | grep -q .; then
    printf 'remote payload contains conflict or metadata files\n' >&2
    failed=1
fi

HEALTHCHECK_HOST="${ERP_HEALTHCHECK_HOST:-8.152.199.39}"
ACTIVE_FRONTEND_INDEX="$RESOLVED_ROOT/nginx/html/dist/index.html"
SERVED_FRONTEND_INDEX="$(mktemp)"
if curl -fsS --max-time 10 -H "Host: $HEALTHCHECK_HOST" \
    http://127.0.0.1/index.html -o "$SERVED_FRONTEND_INDEX"; then
    if ! cmp -s "$ACTIVE_FRONTEND_INDEX" "$SERVED_FRONTEND_INDEX"; then
        printf 'frontend artifact mismatch: served index.html differs from active release\n' >&2
        failed=1
    fi
else
    printf 'frontend health check failed\n' >&2
    failed=1
fi
rm -f "$SERVED_FRONTEND_INDEX"
business_json_ready http://127.0.0.1/prod-api/code "$HEALTHCHECK_HOST" \
    || { printf 'gateway captcha endpoint returned HTTP success with a failed business code\n' >&2; failed=1; }

exit "$failed"

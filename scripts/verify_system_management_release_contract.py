#!/usr/bin/env python3
"""Fail-closed local/payload contract for system-management Release A."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import zipfile
from datetime import datetime
from pathlib import Path


RELEASE_ID = "system-management-20260714"
EXPECTED_PHASES = [
    "expand-before-code-cutover",
    "permission-cutover-after-code-deploy",
    "finalize-after-zero-shared-credential-audit",
]
EXPECTED_ARTIFACTS = {
    "gateway": ("jar", "erp-gateway/target/erp-gateway.jar", "docker/erp/gateway/jar/erp-gateway.jar"),
    "auth": ("jar", "erp-auth/target/erp-auth.jar", "docker/erp/auth/jar/erp-auth.jar"),
    "monitor": ("jar", "erp-visual/erp-monitor/target/erp-visual-monitor.jar", "docker/erp/visual/monitor/jar/erp-visual-monitor.jar"),
    "system": ("jar", "erp-modules/erp-system/target/erp-modules-system.jar", "docker/erp/modules/system/jar/erp-modules-system.jar"),
    "file": ("jar", "erp-modules/erp-file/target/erp-modules-file.jar", "docker/erp/modules/file/jar/erp-modules-file.jar"),
    "job": ("jar", "erp-modules/erp-job/target/erp-modules-job.jar", "docker/erp/modules/job/jar/erp-modules-job.jar"),
    "oa": ("jar", "erp-modules/erp-oa/target/erp-modules-oa.jar", "docker/erp/modules/oa/jar/erp-modules-oa.jar"),
    "inventory": ("jar", "erp-modules/erp-inventory/target/erp-modules-inventory.jar", "docker/erp/modules/inventory/jar/erp-modules-inventory.jar"),
    "approval": ("jar", "erp-modules/erp-approval/target/erp-modules-approval.jar", "docker/erp/modules/approval/jar/erp-modules-approval.jar"),
    "frontend": ("directory", "erp-ui/dist", "docker/nginx/html/dist"),
}
REQUIRED_CRITICAL_CLASSES = {
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
SAFE_SQL = re.compile(r"[A-Za-z0-9._-]+\.sql")
SAFE_TABLE = re.compile(r"[a-z][a-z0-9_]*")
FULL_SHA = re.compile(r"[0-9a-f]{40}")
SHA256 = re.compile(r"[0-9a-f]{64}")


def fail(message: str) -> None:
    raise ValueError(message)


def repo_path(root: Path, value: object, label: str) -> Path:
    if not isinstance(value, str) or not value:
        fail(f"{label} must be a non-empty repository-relative path")
    relative = Path(value)
    if relative.is_absolute() or ".." in relative.parts:
        fail(f"unsafe {label}: {value!r}")
    path = (root / relative).resolve()
    try:
        path.relative_to(root)
    except ValueError as exc:
        raise ValueError(f"unsafe {label}: {value!r}") from exc
    return path


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def directory_sha256(path: Path) -> str:
    if not path.is_dir():
        fail(f"artifact directory is missing: {path}")
    digest = hashlib.sha256()
    files = sorted(item for item in path.rglob("*") if item.is_file())
    if not files:
        fail(f"artifact directory is empty: {path}")
    for item in files:
        if item.is_symlink():
            fail(f"artifact directory contains a symlink: {item}")
        relative = item.relative_to(path).as_posix().encode("utf-8")
        digest.update(relative)
        digest.update(b"\0")
        digest.update(bytes.fromhex(file_sha256(item)))
    return digest.hexdigest()


def read_build_commit(path: Path) -> str:
    candidates = (
        "BOOT-INF/classes/META-INF/build-info.properties",
        "META-INF/build-info.properties",
    )
    try:
        with zipfile.ZipFile(path) as archive:
            for candidate in candidates:
                try:
                    content = archive.read(candidate).decode("utf-8")
                except KeyError:
                    continue
                for line in content.splitlines():
                    if line.startswith("build.commit="):
                        return line.split("=", 1)[1].strip()
    except (OSError, zipfile.BadZipFile) as exc:
        raise ValueError(f"invalid executable JAR: {path}") from exc
    fail(f"JAR build-info commit is missing: {path}")
    return ""


def validate_migrations(root: Path, manifest: dict) -> int:
    list_path = repo_path(root, manifest.get("migrationList"), "migrationList")
    source_dir = repo_path(root, manifest.get("sourceDirectory"), "sourceDirectory")
    deploy_dir = repo_path(root, manifest.get("deployDirectory"), "deployDirectory")
    expected_deploy = (root / "docker/mysql/releases" / RELEASE_ID).resolve()
    if deploy_dir != expected_deploy:
        fail(f"deployDirectory must be docker/mysql/releases/{RELEASE_ID}")
    if not list_path.is_file():
        fail(f"migration list is missing: {list_path}")
    ordered = [
        line.strip()
        for line in list_path.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.lstrip().startswith("#")
    ]
    if not ordered or len(ordered) != len(set(ordered)):
        fail("migration list must be non-empty and unique")
    migrations = manifest.get("migrations")
    if not isinstance(migrations, list) or [item.get("file") for item in migrations] != ordered:
        fail("manifest migrations must exactly match the ordered migration list")
    if [item.get("phase") for item in migrations] != EXPECTED_PHASES:
        fail("system-management migration phases/order changed")

    for item in migrations:
        name = item.get("file")
        expected = item.get("sha256")
        if not isinstance(name, str) or not SAFE_SQL.fullmatch(name):
            fail(f"unsafe migration filename: {name!r}")
        if not isinstance(expected, str) or not SHA256.fullmatch(expected):
            fail(f"invalid migration sha256: {name}")
        source = source_dir / name
        deploy = deploy_dir / name
        if not source.is_file() or not deploy.is_file():
            fail(f"migration source/deploy pair is incomplete: {name}")
        source_digest = file_sha256(source)
        if source_digest != expected or file_sha256(deploy) != expected:
            fail(f"migration source/deploy hash mismatch: {name}")
        if source.read_bytes() != deploy.read_bytes():
            fail(f"migration source/deploy bytes differ: {name}")
        for key in ("creates", "backupTables"):
            values = item.get(key)
            if not isinstance(values, list) or any(
                not isinstance(value, str) or not SAFE_TABLE.fullmatch(value)
                for value in values
            ):
                fail(f"invalid {key} list for {name}")

    actual = sorted(item.name for item in deploy_dir.glob("*.sql") if item.is_file())
    if actual != sorted(ordered):
        fail("system-management deploy directory contains undeclared SQL")
    return len(ordered)


def validate_critical_classes(root: Path, manifest: dict) -> int:
    classes = manifest.get("criticalClasses")
    if not isinstance(classes, list) or len(classes) != len(set(classes)):
        fail("criticalClasses must be a unique list")
    missing_contract = sorted(REQUIRED_CRITICAL_CLASSES - set(classes))
    if missing_contract:
        fail("criticalClasses misses required entries: " + ", ".join(missing_contract))
    for fqcn in classes:
        if not isinstance(fqcn, str) or not re.fullmatch(r"[A-Za-z_$][\w$]*(\.[A-Za-z_$][\w$]*)+", fqcn):
            fail(f"invalid critical class: {fqcn!r}")
        suffix = "/src/main/java/" + fqcn.replace(".", "/") + ".java"
        matches = [path for path in root.rglob(Path(suffix).name) if path.as_posix().endswith(suffix)]
        if len(matches) != 1:
            fail(f"critical class source must exist exactly once: {fqcn}")
    return len(classes)


def validate_build(manifest: dict, candidate: bool) -> tuple[str, str]:
    build = manifest.get("build")
    if not isinstance(build, dict):
        fail("build metadata is missing")
    commit = build.get("commit")
    build_time = build.get("buildTime")
    for key in ("jdk", "node", "maven", "browser", "playwrightCli"):
        if not isinstance(build.get(key), str) or not build[key].strip():
            fail(f"build.{key} is missing")
    if candidate:
        if not isinstance(commit, str) or not FULL_SHA.fullmatch(commit):
            fail("candidate build commit must be a full lowercase Git SHA")
        if not isinstance(build_time, str) or build_time == "UNSET":
            fail("candidate buildTime is not finalized")
        try:
            datetime.fromisoformat(build_time.replace("Z", "+00:00"))
        except ValueError as exc:
            raise ValueError("candidate buildTime must be ISO-8601") from exc
        if build.get("browser") == "UNSET":
            fail("candidate browser version is not finalized")
    if build.get("playwrightCli") != "0.1.17":
        fail("Playwright CLI version differs from the pinned native browser gate")
    elif commit != "UNSET" and (not isinstance(commit, str) or not FULL_SHA.fullmatch(commit)):
        fail("development build commit must be UNSET or a full lowercase Git SHA")
    return str(commit), str(build_time)


def validate_artifacts(root: Path, manifest: dict, candidate: bool, commit: str, build_time: str) -> int:
    artifacts = manifest.get("artifacts")
    if not isinstance(artifacts, list):
        fail("artifacts must be a list")
    if not artifacts:
        if candidate:
            fail("candidate artifacts are empty")
        return 0
    by_id = {item.get("id"): item for item in artifacts if isinstance(item, dict)}
    if len(by_id) != len(artifacts) or set(by_id) != set(EXPECTED_ARTIFACTS):
        fail("artifact inventory must exactly match all executable services and frontend")
    for artifact_id, (kind, source_value, packaged_value) in EXPECTED_ARTIFACTS.items():
        item = by_id[artifact_id]
        if item.get("type") != kind or item.get("source") != source_value or item.get("packaged") != packaged_value:
            fail(f"artifact path/type changed: {artifact_id}")
        expected = item.get("sha256")
        if not isinstance(expected, str) or not SHA256.fullmatch(expected):
            fail(f"artifact sha256 is invalid: {artifact_id}")
        source = repo_path(root, source_value, f"artifact {artifact_id} source")
        packaged = repo_path(root, packaged_value, f"artifact {artifact_id} packaged")
        digest_function = file_sha256 if kind == "jar" else directory_sha256
        if digest_function(source) != expected or digest_function(packaged) != expected:
            fail(f"artifact source/package/manifest mismatch: {artifact_id}")
        if kind == "jar" and read_build_commit(source) != commit:
            fail(f"JAR build-info commit differs from manifest: {artifact_id}")
    release_info = root / "erp-ui/dist/release-info.json"
    if not release_info.is_file():
        fail("frontend release-info.json is missing")
    info = json.loads(release_info.read_text(encoding="utf-8"))
    if info != {"commit": commit, "buildTime": build_time}:
        fail("frontend release-info.json differs from manifest build metadata")
    return len(artifacts)


def verify(root: Path, candidate: bool = False, manifest_path: Path | None = None) -> dict:
    root = root.resolve()
    manifest_path = (manifest_path or root / "scripts/system-management-release-20260714.json").resolve()
    try:
        manifest_path.relative_to(root)
    except ValueError as exc:
        raise ValueError("release manifest must remain inside the repository") from exc
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if manifest.get("schemaVersion") != 1:
        fail("unsupported system-management manifest schemaVersion")
    if manifest.get("releaseId") != RELEASE_ID:
        fail("unexpected system-management releaseId")
    if manifest.get("executionPolicy") != "manual-phased":
        fail("system-management release must remain manual-phased")
    if manifest.get("prerequisiteReleaseIds") != ["new-business-20260714"]:
        fail("new-business release prerequisite must remain explicit")
    prerequisite = root / "scripts/new-business-release-20260714.json"
    prerequisite_manifest = json.loads(prerequisite.read_text(encoding="utf-8"))
    if prerequisite_manifest.get("releaseId") != "new-business-20260714":
        fail("declared prerequisite manifest is missing")
    if manifest.get("artifactSha256Required") is not True:
        fail("artifact SHA-256 requirement cannot be disabled")
    if candidate and manifest.get("status") != "ready":
        fail("candidate manifest status must be ready")
    if not candidate and manifest.get("status") not in {"development", "ready"}:
        fail("invalid manifest status")

    migration_count = validate_migrations(root, manifest)
    critical_count = validate_critical_classes(root, manifest)
    commit, build_time = validate_build(manifest, candidate)
    artifact_count = validate_artifacts(root, manifest, candidate, commit, build_time)
    return {
        "releaseId": RELEASE_ID,
        "mode": "candidate" if candidate else "source",
        "status": manifest.get("status"),
        "migrationCount": migration_count,
        "criticalClassCount": critical_count,
        "artifactCount": artifact_count,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parent.parent)
    parser.add_argument("--candidate", action="store_true")
    parser.add_argument("--manifest", type=Path)
    args = parser.parse_args()
    result = verify(args.root, args.candidate, args.manifest)
    print(
        "SYSTEM_MANAGEMENT_RELEASE_CONTRACT_OK "
        + " ".join(f"{key}={value}" for key, value in result.items())
    )


if __name__ == "__main__":
    main()

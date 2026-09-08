#!/usr/bin/env python3
"""Build and verify immutable ERP release packages without deploying them."""

from __future__ import annotations

import argparse
import datetime as dt
import gzip
import hashlib
import importlib.util
import ipaddress
import json
import os
import re
import shutil
import stat
import subprocess
import sys
import tarfile
import tempfile
import zipfile
from pathlib import Path, PurePosixPath
from typing import Any, Iterable
from urllib.parse import parse_qs, urlsplit


SCRIPT_DIR = Path(__file__).resolve().parent
DEFAULT_CONTRACT = SCRIPT_DIR / "release-contract.json"
RELEASE_ID_RE = re.compile(r"[a-z0-9][a-z0-9._-]{2,47}")
GIT_COMMIT_RE = re.compile(r"[0-9a-f]{40}")
PLACEHOLDER_RE = re.compile(
    r"(replace-with|change[-_ ]?me|changeme|example|placeholder|your[-_])",
    re.IGNORECASE,
)
SUSPICIOUS_NAME_RE = re.compile(
    r"(?:\s(?:copy|backup|bak|old|副本|\d+)|\((?:copy|\d+)\))(?=\.[^./]+$)|(?:\.bak|\.old|~)$",
    re.IGNORECASE,
)
RELEASE_MANIFEST = PurePosixPath("provenance/release-manifest.json")
TOOLCHAIN_MANIFEST = PurePosixPath("provenance/toolchain.json")
SBOM_MANIFEST = PurePosixPath("provenance/sbom.spdx.json")
CHECKSUM_MANIFEST = PurePosixPath("provenance/SHA256SUMS")
ROLLBACK_PLAN = PurePosixPath("rollback/rollback-plan.json")
ARCHIVE_SUFFIX = ".tar.gz"


class ReleaseError(RuntimeError):
    pass


def fail(message: str) -> None:
    raise ReleaseError(message)


def read_json(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        fail(f"cannot read JSON {path}: {exc}")
    if not isinstance(value, dict):
        fail(f"JSON root must be an object: {path}")
    return value


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def normalized_build_time(value: str) -> tuple[str, int]:
    if not value or not value.endswith("Z"):
        fail("BUILD_TIME must be an explicit UTC ISO-8601 timestamp ending in Z")
    try:
        parsed = dt.datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as exc:
        fail(f"BUILD_TIME is not valid ISO-8601: {exc}")
    if parsed.tzinfo is None or parsed.utcoffset() != dt.timedelta(0):
        fail("BUILD_TIME must use UTC")
    canonical = parsed.astimezone(dt.timezone.utc).replace(microsecond=0)
    canonical_text = canonical.isoformat().replace("+00:00", "Z")
    if canonical_text != value:
        fail(f"BUILD_TIME must use canonical second precision: {canonical_text}")
    return canonical_text, int(canonical.timestamp())


def validate_identity(release_id: str, git_commit: str, build_time: str) -> tuple[str, str, str, int]:
    if not RELEASE_ID_RE.fullmatch(release_id or ""):
        fail("RELEASE_ID must be 3-48 lowercase letters, digits, dot, underscore or hyphen")
    if not GIT_COMMIT_RE.fullmatch(git_commit or ""):
        fail("GIT_COMMIT must be a full 40-character lowercase Git SHA")
    canonical_time, epoch = normalized_build_time(build_time)
    return release_id, git_commit, canonical_time, epoch


def load_contract(path: Path) -> dict[str, Any]:
    contract = read_json(path)
    if contract.get("schemaVersion") != 1:
        fail("unsupported release contract schemaVersion")
    build_toolchain = contract.get("buildToolchain")
    if not isinstance(build_toolchain, dict):
        fail("release contract buildToolchain must be an object")
    expected_toolchain = {
        "mavenCommand": str,
        "mavenVersion": str,
        "javaMajor": int,
        "nodeMajorMin": int,
        "nodeMajorMaxExclusive": int,
    }
    for name, expected_type in expected_toolchain.items():
        value = build_toolchain.get(name)
        if not isinstance(value, expected_type) or isinstance(value, bool):
            fail(f"release contract buildToolchain.{name} is invalid")
    if (
        not build_toolchain["mavenCommand"].startswith("./")
        or not re.fullmatch(r"\d+\.\d+\.\d+", build_toolchain["mavenVersion"])
        or build_toolchain["javaMajor"] < 1
        or build_toolchain["nodeMajorMin"] < 1
        or build_toolchain["nodeMajorMaxExclusive"]
        <= build_toolchain["nodeMajorMin"]
    ):
        fail("release contract buildToolchain values are invalid")
    artifacts = contract.get("artifactMap")
    if not isinstance(artifacts, list) or not artifacts:
        fail("release contract artifactMap must be non-empty")
    seen_components: set[str] = set()
    seen_destinations: set[str] = set()
    for item in artifacts:
        if not isinstance(item, dict):
            fail("release contract artifact entry must be an object")
        component = item.get("component")
        source = item.get("source")
        destination = item.get("destination")
        if not all(isinstance(value, str) and value for value in (component, source, destination)):
            fail("release contract artifact fields are invalid")
        if component in seen_components or destination in seen_destinations:
            fail("release contract artifact components and destinations must be unique")
        if not destination.endswith(".jar") or item.get("kind") != "jar":
            fail(f"unsupported artifact contract for {component}")
        seen_components.add(component)
        seen_destinations.add(destination)
    return contract


def parse_env(path: Path) -> dict[str, str]:
    if not path.is_file():
        fail(f"environment file is missing: {path}")
    values: dict[str, str] = {}
    for number, raw_line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export "):
            line = line[7:].lstrip()
        if "=" not in line:
            fail(f"invalid environment assignment at line {number}")
        name, value = line.split("=", 1)
        name = name.strip()
        if not re.fullmatch(r"[A-Z][A-Z0-9_]*", name):
            fail(f"invalid environment variable name at line {number}")
        if name in values:
            fail(f"duplicate environment variable: {name}")
        value = value.strip()
        if (value.startswith('"') and value.endswith('"')) or (
            value.startswith("'") and value.endswith("'")
        ):
            value = value[1:-1]
        values[name] = value
    return values


def is_within(path: Path, parent: Path) -> bool:
    try:
        path.resolve().relative_to(parent.resolve())
        return True
    except ValueError:
        return False


def validate_production_hostname(value: str, label: str) -> None:
    if PLACEHOLDER_RE.search(value):
        fail(f"{label} still contains a placeholder")
    if value.endswith(".") or len(value) > 253:
        fail(f"{label} must be a canonical DNS hostname")
    try:
        address = ipaddress.ip_address(value)
    except ValueError:
        address = None
    if address is not None:
        fail(f"{label} must use a production DNS hostname, not an IP address")
    labels = value.split(".")
    if len(labels) < 2 or any(
        not re.fullmatch(r"[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?", part)
        for part in labels
    ):
        fail(f"{label} must be a valid fully-qualified DNS hostname")
    if not re.fullmatch(r"[A-Za-z]{2,63}", labels[-1]):
        fail(f"{label} must end in a valid alphabetic top-level domain")
    lowered = value.lower()
    if lowered.endswith(
        (".local", ".internal", ".invalid", ".localhost", ".test", ".example")
    ):
        fail(f"{label} must not use a local or reserved hostname")


def validate_loopback_jdbc_url(value: str) -> None:
    if PLACEHOLDER_RE.search(value) or not value.startswith("jdbc:mysql://"):
        fail("ECS_APP_DATASOURCE_URL must be a non-placeholder MySQL JDBC URL")
    try:
        parsed = urlsplit(value[len("jdbc:") :])
        port = parsed.port
    except ValueError as exc:
        fail(f"ECS_APP_DATASOURCE_URL is invalid: {exc}")
    if (
        parsed.scheme != "mysql"
        or parsed.hostname != "127.0.0.1"
        or port is None
        or not (1 <= port <= 65535)
    ):
        fail("ECS_APP_DATASOURCE_URL must target explicit IPv4 loopback and port")
    if parsed.username is not None or parsed.password is not None:
        fail("ECS_APP_DATASOURCE_URL must not embed database credentials")
    database = parsed.path.removeprefix("/")
    if not re.fullmatch(r"[A-Za-z0-9_-]+", database):
        fail("ECS_APP_DATASOURCE_URL must name one explicit database")
    if parsed.fragment:
        fail("ECS_APP_DATASOURCE_URL must not contain a fragment")
    query = parse_qs(parsed.query, keep_blank_values=True)
    allowed_query = {
        "useUnicode",
        "characterEncoding",
        "zeroDateTimeBehavior",
        "useSSL",
        "allowPublicKeyRetrieval",
        "serverTimezone",
    }
    unknown_query = sorted(set(query) - allowed_query)
    if unknown_query or any(len(values) != 1 for values in query.values()):
        fail("ECS_APP_DATASOURCE_URL contains unsupported or duplicate query options")
    if query.get("allowPublicKeyRetrieval") != ["false"]:
        fail("ECS_APP_DATASOURCE_URL must set allowPublicKeyRetrieval=false")


def validate_nacos_loopback_address(value: str) -> None:
    if value != "127.0.0.1:8848":
        fail("ECS_NACOS_SERVER_ADDR must equal 127.0.0.1:8848")


def validate_https_production_url(value: str) -> None:
    if PLACEHOLDER_RE.search(value):
        fail("FILE_DOMAIN still contains a placeholder")
    try:
        parsed = urlsplit(value)
        _ = parsed.port
    except ValueError as exc:
        fail(f"FILE_DOMAIN is invalid: {exc}")
    if parsed.scheme != "https" or not parsed.hostname:
        fail("FILE_DOMAIN must be an HTTPS production URL")
    if parsed.username is not None or parsed.password is not None:
        fail("FILE_DOMAIN must not embed credentials")
    validate_production_hostname(parsed.hostname, "FILE_DOMAIN hostname")
    if parsed.query or parsed.fragment or ".." in PurePosixPath(parsed.path).parts:
        fail("FILE_DOMAIN must not contain query, fragment or parent traversal")


def validate_domain_allowlist(value: str) -> None:
    if PLACEHOLDER_RE.search(value):
        fail("REFERER_ALLOWED_DOMAINS still contains a placeholder")
    domains = [item.strip() for item in value.split(",")]
    if not domains or any(not item for item in domains) or len(domains) != len(set(domains)):
        fail("REFERER_ALLOWED_DOMAINS must be a non-empty unique comma-separated list")
    for domain in domains:
        validate_production_hostname(domain, "REFERER_ALLOWED_DOMAINS entry")


def validate_environment(
    contract: dict[str, Any],
    env_file: Path,
    repo_root: Path,
    allow_placeholders: bool,
) -> dict[str, Any]:
    values = parse_env(env_file)
    checks: list[dict[str, Any]] = []
    required = contract.get("requiredEnvironment")
    if not isinstance(required, list) or not required:
        fail("release contract requiredEnvironment must be non-empty")
    for item in required:
        if not isinstance(item, dict):
            fail("invalid requiredEnvironment entry")
        name = str(item.get("name", ""))
        rule = str(item.get("type", ""))
        if name not in values:
            fail(f"required environment variable is missing: {name}")
        value = values[name]
        if not value:
            fail(f"required environment variable is empty: {name}")
        if allow_placeholders:
            checks.append({"name": name, "secret": bool(item.get("secret")), "status": "declared"})
            continue
        if rule == "non-placeholder" and PLACEHOLDER_RE.search(value):
            fail(f"required production variable still contains a placeholder: {name}")
        elif rule == "prod-profile" and value != "prod":
            fail("SPRING_PROFILES_ACTIVE must equal prod")
        elif rule == "release-id" and not RELEASE_ID_RE.fullmatch(value):
            fail("RELEASE_ID does not satisfy the release identity contract")
        elif rule == "git-commit" and not GIT_COMMIT_RE.fullmatch(value):
            fail("GIT_COMMIT does not satisfy the release identity contract")
        elif rule == "build-time":
            normalized_build_time(value)
        elif rule == "jdbc-mysql-loopback":
            validate_loopback_jdbc_url(value)
        elif rule == "nacos-loopback-address":
            validate_nacos_loopback_address(value)
        elif rule == "https-production-url":
            validate_https_production_url(value)
        elif rule == "literal-true" and value != "true":
            fail(f"{name} must equal true")
        elif rule == "domain-allowlist":
            validate_domain_allowlist(value)
        elif rule == "persistent-common-upload-directory":
            if value != "/data/erp-new-data/uploadPath":
                fail(f"{name} must equal /data/erp-new-data/uploadPath")
        elif rule == "common-upload-subdirectory":
            expected_storage_paths = {
                "SIGN_PACKAGE_STORAGE_ROOT": "/data/erp-new-data/uploadPath/private/sign-package",
                "OA_ATTENDANCE_STORAGE_ROOT": "/data/erp-new-data/uploadPath/private/attendance",
                "OA_REIMBURSEMENT_STORAGE_ROOT": "/data/erp-new-data/uploadPath/private/reimbursement",
                "DRIVE_LOCAL_PATH": "/data/erp-new-data/uploadPath/private/drive",
            }
            if value != expected_storage_paths.get(name):
                fail(f"{name} does not match the canonical common-upload subdirectory")
        elif rule not in {
            "non-placeholder",
            "non-empty",
            "prod-profile",
            "release-id",
            "git-commit",
            "build-time",
            "jdbc-mysql-loopback",
            "nacos-loopback-address",
            "https-production-url",
            "literal-true",
            "domain-allowlist",
            "persistent-common-upload-directory",
            "common-upload-subdirectory",
        }:
            fail(f"unsupported environment validation type for {name}: {rule}")
        checks.append({"name": name, "secret": bool(item.get("secret")), "status": "valid"})
    return {
        "schemaVersion": 1,
        "environmentFile": str(env_file),
        "mode": "template" if allow_placeholders else "production",
        "checks": checks,
        "secretValuesEmitted": False,
    }


def validate_compose_source(compose_file: Path) -> dict[str, Any]:
    if not compose_file.is_file():
        fail(f"production Compose file is missing: {compose_file}")
    source = compose_file.read_text(encoding="utf-8")
    active_lines = [
        line.split("#", 1)[0]
        for line in source.splitlines()
        if line.split("#", 1)[0].strip()
    ]
    active_source = "\n".join(active_lines)
    hardcoded_nonprod = re.findall(
        r"SPRING_PROFILES_ACTIVE\s*:\s*[\"']?(?:dev|local|test)[\"']?\s*$",
        active_source,
        flags=re.MULTILINE,
    )
    if hardcoded_nonprod:
        fail("production Compose contains a hard-coded non-production Spring profile")
    required_interpolations = [
        "SPRING_PROFILES_ACTIVE",
        "ERP_UPLOAD_ROOT",
        "OA_SIGN_EXCEL_IMPORT_ENABLED",
        "RELEASE_ID",
        "GIT_COMMIT",
        "BUILD_TIME",
    ]
    missing = [
        name
        for name in required_interpolations
        if not re.search(
            rf"\$\{{{re.escape(name)}(?::[?+-][^}}]*)?\}}",
            active_source,
        )
    ]
    if missing:
        fail(
            "production Compose does not interpolate required release variables: "
            + ", ".join(missing)
        )
    common_root = "/data/erp-new-data/uploadPath"
    storage_values = {
        "ERP_UPLOAD_ROOT": common_root,
        "FILE_PATH": common_root,
        "SIGN_PACKAGE_STORAGE_ROOT": common_root + "/private/sign-package",
        "OA_ATTENDANCE_STORAGE_ROOT": common_root + "/private/attendance",
        "OA_REIMBURSEMENT_STORAGE_ROOT": common_root + "/private/reimbursement",
        "DRIVE_LOCAL_PATH": common_root + "/private/drive",
    }
    for name, expected_value in storage_values.items():
        if not re.search(
            rf"(?m)^\s*{re.escape(name)}\s*:\s*{re.escape(expected_value)}\s*$",
            active_source,
        ):
            fail(f"production Compose does not pin {name} to {expected_value}")
    if not re.search(
        r"\$\{ERP_UPLOAD_ROOT(?::[?+-][^}]*)?\}:/data/erp-new-data/uploadPath(?:\s|$)",
        active_source,
    ):
        fail("production Compose does not mount the canonical common-upload root")
    if "./uploadPath" in active_source or "/home/erp/uploadPath" in active_source:
        fail("production Compose contains a release-relative or aliased uploadPath")
    return {
        "schemaVersion": 1,
        "composeFile": str(compose_file),
        "requiredInterpolations": required_interpolations,
        "hardcodedNonProductionProfiles": 0,
        "status": "valid",
    }


def run_text(command: list[str], cwd: Path) -> str:
    try:
        result = subprocess.run(
            command,
            cwd=cwd,
            check=True,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
        )
    except (OSError, subprocess.CalledProcessError) as exc:
        output = getattr(exc, "stdout", "") or ""
        fail(f"command failed: {' '.join(command)}\n{output.strip()}")
    return result.stdout.strip()


def git_value(repo_root: Path, *args: str) -> str:
    return run_text(["git", *args], repo_root)


def require_clean_commit(repo_root: Path, git_commit: str, allow_dirty: bool) -> dict[str, Any]:
    head = git_value(repo_root, "rev-parse", "HEAD")
    if head != git_commit:
        fail(f"GIT_COMMIT does not match repository HEAD ({head})")
    dirty_lines = [
        line
        for line in git_value(repo_root, "status", "--porcelain=v1", "--untracked-files=all").splitlines()
        if line
    ]
    if dirty_lines and not allow_dirty:
        fail("repository is dirty; commit or remove all changes before packaging")
    return {
        "commit": head,
        "branch": git_value(repo_root, "rev-parse", "--abbrev-ref", "HEAD"),
        "clean": not dirty_lines,
        "dirtyPathCount": len(dirty_lines),
    }


def safe_relative(value: str, label: str) -> PurePosixPath:
    path = PurePosixPath(value)
    if path.is_absolute() or ".." in path.parts or not path.parts:
        fail(f"unsafe {label} path: {value}")
    return path


def check_suspicious_names(root: Path) -> None:
    if not root.exists():
        fail(f"required directory is missing: {root}")
    for path in root.rglob("*"):
        if path.is_symlink():
            fail(f"symlinks are forbidden in release inputs: {path}")
        if path.is_file() and SUSPICIOUS_NAME_RE.search(path.name):
            fail(f"duplicate/backup-style filename is forbidden: {path}")


def check_clean_deployment_sources(repo_root: Path, contract: dict[str, Any]) -> None:
    docker_root = repo_root / "docker"
    if (docker_root / ".env").exists():
        fail("docker/.env must never be included in or adjacent to a release build")
    frontend_destination = safe_relative(contract["frontend"]["destination"], "frontend destination")
    old_dist = repo_root / frontend_destination
    if old_dist.exists() and any(old_dist.rglob("*")):
        fail("old docker/nginx/html/dist exists; remove it before clean packaging")
    for artifact in contract["artifactMap"]:
        destination = repo_root / safe_relative(artifact["destination"], "artifact destination")
        siblings = list(destination.parent.glob("*.jar"))
        if siblings:
            fail(f"old Docker JAR directory is not clean: {destination.parent}")
    for relative in contract.get("forbiddenDeploymentDirectories", []):
        path = docker_root / safe_relative(str(relative), "forbidden deployment directory")
        if path.exists() and any(path.rglob("*")):
            fail(f"runtime/generated deployment directory must be clean: {path}")


def validate_artifact_inputs(repo_root: Path, contract: dict[str, Any]) -> list[dict[str, Any]]:
    validated: list[dict[str, Any]] = []
    for artifact in contract["artifactMap"]:
        source_rel = safe_relative(artifact["source"], "artifact source")
        source = repo_root / source_rel
        if not source.is_file() or source.is_symlink():
            fail(f"canonical artifact is missing: {source_rel}")
        jar_siblings = sorted(path.name for path in source.parent.glob("*.jar") if path.is_file())
        if jar_siblings != [source.name]:
            fail(
                f"artifact directory must contain exactly {source.name}; found "
                + (", ".join(jar_siblings) or "none")
            )
        validated.append(
            {
                "component": artifact["component"],
                "source": source_rel.as_posix(),
                "destination": safe_relative(
                    artifact["destination"], "artifact destination"
                ).as_posix(),
                "sourceSha256": sha256_file(source),
            }
        )
    frontend = contract["frontend"]
    source_dir = repo_root / safe_relative(frontend["source"], "frontend source")
    if not source_dir.is_dir() or not (source_dir / "index.html").is_file():
        fail("frontend dist is missing index.html")
    check_suspicious_names(source_dir)
    return validated


def parse_manifest_attributes(raw: bytes) -> dict[str, str]:
    text = raw.decode("utf-8", errors="strict").replace("\r\n", "\n")
    main = text.split("\n\n", 1)[0]
    unfolded: list[str] = []
    for line in main.splitlines():
        if line.startswith(" ") and unfolded:
            unfolded[-1] += line[1:]
        else:
            unfolded.append(line)
    values: dict[str, str] = {}
    for line in unfolded:
        if ": " in line:
            name, value = line.split(": ", 1)
            values[name] = value
    return values


def updated_manifest(raw: bytes, identity: dict[str, str]) -> bytes:
    text = raw.decode("utf-8", errors="strict").replace("\r\n", "\n")
    sections = text.split("\n\n")
    main_lines = sections[0].splitlines()
    managed = {
        "X-ERP-Release-Id",
        "X-ERP-Git-Commit",
        "X-ERP-Build-Time",
    }
    filtered: list[str] = []
    skipping = False
    for line in main_lines:
        if line.startswith(" "):
            if not skipping:
                filtered.append(line)
            continue
        key = line.split(":", 1)[0]
        skipping = key in managed
        if not skipping:
            filtered.append(line)
    attributes = [
        ("X-ERP-Release-Id", identity["releaseId"]),
        ("X-ERP-Git-Commit", identity["gitCommit"]),
        ("X-ERP-Build-Time", identity["buildTime"]),
    ]
    for key, value in attributes:
        physical = f"{key}: {value}"
        if len(physical.encode("ascii")) > 72:
            fail(f"JAR manifest attribute is too long: {key}")
        filtered.append(physical)
    remainder = "\n\n".join(sections[1:]).rstrip("\n")
    output = "\r\n".join(filtered) + "\r\n\r\n"
    if remainder:
        output += remainder.replace("\n", "\r\n") + "\r\n"
    return output.encode("utf-8")


def stamp_jar(path: Path, identity: dict[str, str]) -> None:
    try:
        with zipfile.ZipFile(path, "r") as source_zip:
            infos = source_zip.infolist()
            names = [info.filename for info in infos]
            if len(names) != len(set(names)):
                fail(f"JAR contains duplicate ZIP entries: {path}")
            signature_blocks = [
                name
                for name in names
                if re.fullmatch(
                    r"META-INF/[^/]+\.(?:RSA|DSA|EC)", name, re.IGNORECASE
                )
            ]
            signature_files = [
                name
                for name in names
                if re.fullmatch(r"META-INF/[^/]+\.SF", name, re.IGNORECASE)
            ]
            non_marker_signature_files = [
                name
                for name in signature_files
                if name.upper() != "META-INF/BOOT.SF"
                or source_zip.getinfo(name).file_size != 0
            ]
            if signature_blocks or non_marker_signature_files:
                fail(f"refusing to alter signed JAR: {path}")
            try:
                manifest = source_zip.read("META-INF/MANIFEST.MF")
            except KeyError:
                fail(f"JAR manifest is missing: {path}")
            entries = [
                (info, source_zip.read(info.filename))
                for info in infos
                if info.filename
                not in {"META-INF/MANIFEST.MF", "META-INF/erp-release.json"}
            ]
    except (OSError, zipfile.BadZipFile, UnicodeDecodeError) as exc:
        fail(f"invalid JAR {path}: {exc}")
    stamped_manifest = updated_manifest(manifest, identity)
    release_info = (
        json.dumps(identity, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    ).encode("utf-8")
    timestamp = dt.datetime.fromisoformat(identity["buildTime"].replace("Z", "+00:00"))
    zip_year = max(1980, timestamp.year)
    zip_time = (zip_year, timestamp.month, timestamp.day, timestamp.hour, timestamp.minute, timestamp.second)
    temporary = path.with_suffix(path.suffix + ".stamping")
    try:
        with zipfile.ZipFile(temporary, "w") as destination_zip:
            manifest_info = zipfile.ZipInfo("META-INF/MANIFEST.MF", zip_time)
            manifest_info.compress_type = zipfile.ZIP_DEFLATED
            manifest_info.create_system = 3
            manifest_info.external_attr = (stat.S_IFREG | 0o644) << 16
            destination_zip.writestr(manifest_info, stamped_manifest)
            for info, content in entries:
                destination_zip.writestr(info, content)
            release_info_entry = zipfile.ZipInfo("META-INF/erp-release.json", zip_time)
            release_info_entry.compress_type = zipfile.ZIP_DEFLATED
            release_info_entry.create_system = 3
            release_info_entry.external_attr = (stat.S_IFREG | 0o644) << 16
            destination_zip.writestr(release_info_entry, release_info)
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


def verify_stamped_jar(path: Path, identity: dict[str, str]) -> None:
    try:
        with zipfile.ZipFile(path, "r") as archive:
            manifest = parse_manifest_attributes(archive.read("META-INF/MANIFEST.MF"))
            release_info = json.loads(archive.read("META-INF/erp-release.json"))
            names = archive.namelist()
    except (OSError, zipfile.BadZipFile, KeyError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        fail(f"cannot verify stamped JAR {path}: {exc}")
    if len(names) != len(set(names)):
        fail(f"stamped JAR contains duplicate entries: {path}")
    expected_manifest = {
        "X-ERP-Release-Id": identity["releaseId"],
        "X-ERP-Git-Commit": identity["gitCommit"],
        "X-ERP-Build-Time": identity["buildTime"],
    }
    for name, expected in expected_manifest.items():
        if manifest.get(name) != expected:
            fail(f"JAR release metadata mismatch for {path.name}: {name}")
    if release_info != identity:
        fail(f"JAR embedded release metadata mismatch: {path}")


def validate_frontend_release_info(
    dist: Path, release_info_name: str, identity: dict[str, str], allow_unstamped: bool
) -> dict[str, str]:
    path = dist / release_info_name
    value = read_json(path)
    if value.get("commit") != identity["gitCommit"] and value.get("gitCommit") != identity["gitCommit"]:
        fail("frontend release-info commit differs from GIT_COMMIT")
    if value.get("buildTime") != identity["buildTime"]:
        fail("frontend release-info buildTime differs from BUILD_TIME")
    if not allow_unstamped and value.get("releaseId") != identity["releaseId"]:
        fail("frontend release-info releaseId differs from RELEASE_ID")
    return {
        "releaseId": identity["releaseId"],
        "gitCommit": identity["gitCommit"],
        "buildTime": identity["buildTime"],
    }


def stamp_frontend(dist: Path, release_info_name: str, identity: dict[str, str]) -> None:
    validate_frontend_release_info(dist, release_info_name, identity, allow_unstamped=True)
    write_json(
        dist / release_info_name,
        {
            "releaseId": identity["releaseId"],
            "gitCommit": identity["gitCommit"],
            "commit": identity["gitCommit"],
            "buildTime": identity["buildTime"],
        },
    )
    validate_frontend_release_info(dist, release_info_name, identity, allow_unstamped=False)


def git_tracked_docker_files(repo_root: Path) -> list[Path]:
    output = subprocess.run(
        ["git", "ls-files", "-z", "--", "docker"],
        cwd=repo_root,
        check=True,
        stdout=subprocess.PIPE,
    ).stdout
    paths = [Path(value.decode("utf-8")) for value in output.split(b"\0") if value]
    if not paths:
        fail("no tracked Docker deployment sources found")
    return sorted(paths)


def copy_tracked_docker(repo_root: Path, package_root: Path) -> None:
    for relative in git_tracked_docker_files(repo_root):
        source = repo_root / relative
        if relative.as_posix() == "docker/.env":
            fail("docker/.env must not be tracked or packaged")
        if source.is_symlink() or not source.is_file():
            fail(f"tracked Docker source must be a regular file: {relative}")
        destination = package_root / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, destination)


def package_migration_manifest(
    repo_root: Path, package_root: Path, manifest_path: Path | None
) -> dict[str, Any] | None:
    """Validate and materialize one explicitly approved migration plan."""

    if manifest_path is None:
        return None
    repo_root = repo_root.resolve()
    package_root = package_root.resolve()
    manifest_path = manifest_path.resolve()
    scripts_root = (repo_root / "scripts").resolve()
    try:
        manifest_path.relative_to(scripts_root)
    except ValueError as exc:
        fail("migration manifest must be a tracked file under scripts/")
        raise AssertionError from exc
    if manifest_path.parent != scripts_root or manifest_path.suffix != ".json":
        fail("migration manifest must be a direct JSON child of scripts/")

    contract_path = scripts_root / "release_migration_contract.py"
    spec = importlib.util.spec_from_file_location(
        "erp_release_migration_contract", contract_path
    )
    if spec is None or spec.loader is None:
        fail("cannot load release migration contract")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    try:
        spec.loader.exec_module(module)
        manifest = module.load_and_validate_manifest(
            manifest_path, require_deploy_mirror=False
        )
    except Exception as exc:
        fail(f"migration manifest validation failed: {exc}")
    finally:
        sys.modules.pop(spec.name, None)

    source_dir = (repo_root / manifest["sourceDirectory"]).resolve()
    list_path = (repo_root / manifest["migrationList"]).resolve()
    tracked_inputs = [manifest_path, list_path]
    tracked_inputs.extend(source_dir / item["file"] for item in manifest["migrations"])
    for source in tracked_inputs:
        try:
            relative = source.relative_to(repo_root)
        except ValueError:
            fail(f"migration input escapes repository: {source}")
        try:
            git_value(repo_root, "ls-files", "--error-unmatch", "--", relative.as_posix())
        except ReleaseError:
            fail(f"migration input must be tracked by Git: {relative}")

    release_id = manifest["releaseId"]
    metadata_dir = package_root / "docker/release"
    sql_dir = package_root / "docker/mysql/releases" / release_id
    metadata_dir.mkdir(parents=True, exist_ok=True)
    sql_dir.mkdir(parents=True, exist_ok=True)
    packaged_manifest = metadata_dir / manifest_path.name
    packaged_list = metadata_dir / list_path.name
    shutil.copyfile(manifest_path, packaged_manifest)
    shutil.copyfile(list_path, packaged_list)

    migrations = []
    for item in manifest["migrations"]:
        source = source_dir / item["file"]
        destination = sql_dir / item["file"]
        shutil.copyfile(source, destination)
        actual = sha256_file(destination)
        if actual != item["sha256"]:
            fail(f"packaged migration hash mismatch: {item['file']}")
        migrations.append(
            {
                "file": item["file"],
                "path": destination.relative_to(package_root).as_posix(),
                "sha256": actual,
            }
        )
    return {
        "releaseId": release_id,
        "manifest": packaged_manifest.relative_to(package_root).as_posix(),
        "manifestSha256": sha256_file(packaged_manifest),
        "migrationList": packaged_list.relative_to(package_root).as_posix(),
        "migrationListSha256": sha256_file(packaged_list),
        "migrationCount": manifest["migrationCount"],
        "migrations": migrations,
    }


def verify_packaged_migration_plan(
    package_root: Path, plan: object
) -> dict[str, Any] | None:
    if plan is None:
        return None
    if not isinstance(plan, dict):
        fail("release migrationPlan must be an object or null")
    required = {
        "releaseId",
        "manifest",
        "manifestSha256",
        "migrationList",
        "migrationListSha256",
        "migrationCount",
        "migrations",
    }
    if set(plan) != required:
        fail("release migrationPlan fields differ from the canonical contract")
    release_id = plan.get("releaseId")
    if not isinstance(release_id, str) or not RELEASE_ID_RE.fullmatch(release_id):
        fail("invalid packaged migration releaseId")
    expected_manifest = f"docker/release/{PurePosixPath(str(plan.get('manifest'))).name}"
    expected_list = f"docker/release/{PurePosixPath(str(plan.get('migrationList'))).name}"
    if plan.get("manifest") != expected_manifest or plan.get("migrationList") != expected_list:
        fail("packaged migration metadata paths are not canonical")
    manifest_path = package_root / expected_manifest
    list_path = package_root / expected_list
    if (
        not manifest_path.is_file()
        or sha256_file(manifest_path) != plan.get("manifestSha256")
        or not list_path.is_file()
        or sha256_file(list_path) != plan.get("migrationListSha256")
    ):
        fail("packaged migration metadata hash mismatch")
    manifest = read_json(manifest_path)
    migrations = plan.get("migrations")
    count = plan.get("migrationCount")
    if (
        manifest.get("schemaVersion") != 1
        or manifest.get("releaseId") != release_id
        or manifest.get("executionPolicy") != "automatic"
        or not isinstance(count, int)
        or isinstance(count, bool)
        or count < 1
        or manifest.get("migrationCount") != count
        or not isinstance(migrations, list)
        or len(migrations) != count
        or not isinstance(manifest.get("migrations"), list)
        or len(manifest["migrations"]) != count
    ):
        fail("packaged migration manifest cardinality or policy is invalid")
    ordered = [
        line.strip()
        for line in list_path.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.lstrip().startswith("#")
    ]
    manifest_names = [item.get("file") for item in manifest["migrations"]]
    plan_names = [item.get("file") for item in migrations if isinstance(item, dict)]
    if ordered != manifest_names or ordered != plan_names or len(plan_names) != count:
        fail("packaged migration order differs across list, manifest and release plan")
    for manifest_item, plan_item in zip(manifest["migrations"], migrations):
        name = manifest_item.get("file")
        expected_path = f"docker/mysql/releases/{release_id}/{name}"
        if (
            plan_item.get("path") != expected_path
            or plan_item.get("sha256") != manifest_item.get("sha256")
        ):
            fail(f"packaged migration plan differs from manifest: {name}")
        sql_path = package_root / expected_path
        if not sql_path.is_file() or sha256_file(sql_path) != manifest_item.get("sha256"):
            fail(f"packaged migration SQL hash mismatch: {name}")
    return plan


def tool_version(command: list[str], cwd: Path) -> dict[str, Any]:
    executable_name = command[0]
    if "/" in executable_name:
        candidate = Path(executable_name)
        if not candidate.is_absolute():
            candidate = cwd / candidate
        executable = (
            str(candidate.resolve())
            if candidate.is_file() and os.access(candidate, os.X_OK)
            else None
        )
    else:
        executable = shutil.which(executable_name)
    if executable is None:
        return {"available": False, "command": command}
    result = subprocess.run(
        [executable, *command[1:]],
        cwd=cwd,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )
    return {
        "available": True,
        "command": command,
        "exitCode": result.returncode,
        "output": result.stdout.strip().splitlines()[:8],
    }


def collect_toolchain(repo_root: Path) -> dict[str, Any]:
    maven_command = (
        ["./mvnw", "--version"]
        if (repo_root / "mvnw").is_file()
        and os.access(repo_root / "mvnw", os.X_OK)
        else ["mvn", "--version"]
    )
    return {
        "schemaVersion": 1,
        "java": tool_version(["java", "-version"], repo_root),
        "maven": tool_version(maven_command, repo_root),
        "node": tool_version(["node", "--version"], repo_root),
        "npm": tool_version(["npm", "--version"], repo_root),
        "git": tool_version(["git", "--version"], repo_root),
        "syft": tool_version(["syft", "version"], repo_root),
    }


def tool_output(tool: Any, label: str) -> str:
    if (
        not isinstance(tool, dict)
        or tool.get("available") is not True
        or tool.get("exitCode") != 0
        or not isinstance(tool.get("command"), list)
        or not isinstance(tool.get("output"), list)
    ):
        fail(f"toolchain provenance for {label} is unavailable or invalid")
    return "\n".join(str(line) for line in tool["output"])


def validate_toolchain_manifest(package_root: Path, contract: dict[str, Any]) -> dict[str, Any]:
    value = read_json(package_root / TOOLCHAIN_MANIFEST)
    if value.get("schemaVersion") != 1:
        fail("unsupported toolchain provenance schemaVersion")
    expected = contract["buildToolchain"]
    maven = value.get("maven")
    maven_output = tool_output(maven, "maven")
    expected_maven_command = expected["mavenCommand"]
    if maven["command"][:1] != [expected_maven_command]:
        fail("Maven provenance does not match the build wrapper contract")
    if not re.search(
        rf"(?m)^Apache Maven {re.escape(expected['mavenVersion'])}(?:\s|$)",
        maven_output,
    ):
        fail("Maven provenance version differs from the release contract")
    maven_java = re.search(r"(?m)^Java version:\s*([^,\s]+)", maven_output)
    if (
        maven_java is None
        or int(maven_java.group(1).split(".", 1)[0]) != expected["javaMajor"]
    ):
        fail("Maven provenance does not use the required Java major")

    java_output = tool_output(value.get("java"), "java")
    java_version = re.search(r'version\s+"(\d+)(?:\.|")', java_output)
    if java_version is None or int(java_version.group(1)) != expected["javaMajor"]:
        fail("Java provenance differs from the release contract")

    node_output = tool_output(value.get("node"), "node").strip()
    node_version = re.fullmatch(r"v?(\d+)(?:\.\d+){2}", node_output)
    if node_version is None:
        fail("Node provenance version is invalid")
    node_major = int(node_version.group(1))
    if not (
        expected["nodeMajorMin"]
        <= node_major
        < expected["nodeMajorMaxExclusive"]
    ):
        fail("Node provenance differs from the release contract")
    tool_output(value.get("npm"), "npm")
    tool_output(value.get("git"), "git")
    return {
        "mavenCommand": expected_maven_command,
        "mavenVersion": expected["mavenVersion"],
        "javaMajor": expected["javaMajor"],
        "nodeMajor": node_major,
    }


def iter_regular_files(root: Path) -> Iterable[Path]:
    for path in sorted(root.rglob("*")):
        if path.is_symlink():
            fail(f"release package contains a symlink: {path}")
        if path.is_file():
            yield path


def relative_file_records(root: Path, excluded: set[PurePosixPath] | None = None) -> list[dict[str, Any]]:
    exclusions = excluded or set()
    records: list[dict[str, Any]] = []
    for path in iter_regular_files(root):
        relative = PurePosixPath(path.relative_to(root).as_posix())
        if relative in exclusions:
            continue
        records.append(
            {
                "path": relative.as_posix(),
                "sha256": sha256_file(path),
                "size": path.stat().st_size,
            }
        )
    return records


def spdx_id(path: str) -> str:
    digest = hashlib.sha256(path.encode("utf-8")).hexdigest()[:20]
    return f"SPDXRef-File-{digest}"


def create_spdx(package_root: Path, identity: dict[str, str]) -> dict[str, Any]:
    excluded = {SBOM_MANIFEST, CHECKSUM_MANIFEST}
    records = relative_file_records(package_root, excluded)
    namespace = (
        "https://erp.example.invalid/spdx/"
        + identity["releaseId"]
        + "-"
        + identity["gitCommit"]
    )
    files = [
        {
            "SPDXID": spdx_id(record["path"]),
            "fileName": "./" + record["path"],
            "checksums": [
                {"algorithm": "SHA256", "checksumValue": record["sha256"]}
            ],
            "copyrightText": "NOASSERTION",
        }
        for record in records
    ]
    relationships = [
        {
            "spdxElementId": "SPDXRef-Package",
            "relationshipType": "CONTAINS",
            "relatedSpdxElement": item["SPDXID"],
        }
        for item in files
    ]
    return {
        "spdxVersion": "SPDX-2.3",
        "dataLicense": "CC0-1.0",
        "SPDXID": "SPDXRef-DOCUMENT",
        "name": identity["releaseId"],
        "documentNamespace": namespace,
        "creationInfo": {
            "created": identity["buildTime"],
            "creators": ["Tool: ERP release_tool.py"],
            "licenseListVersion": "3.23",
        },
        "packages": [
            {
                "name": identity["releaseId"],
                "SPDXID": "SPDXRef-Package",
                "versionInfo": identity["gitCommit"],
                "downloadLocation": "NOASSERTION",
                "filesAnalyzed": True,
                "licenseConcluded": "NOASSERTION",
                "licenseDeclared": "NOASSERTION",
                "copyrightText": "NOASSERTION",
            }
        ],
        "files": files,
        "relationships": relationships,
    }


def write_checksums(package_root: Path) -> list[dict[str, Any]]:
    records = relative_file_records(package_root, {CHECKSUM_MANIFEST})
    checksum_path = package_root / CHECKSUM_MANIFEST
    checksum_path.parent.mkdir(parents=True, exist_ok=True)
    checksum_path.write_text(
        "".join(f"{item['sha256']}  {item['path']}\n" for item in records),
        encoding="utf-8",
    )
    return records


def read_checksums(package_root: Path) -> list[tuple[str, PurePosixPath]]:
    path = package_root / CHECKSUM_MANIFEST
    if not path.is_file():
        fail("package SHA256SUMS is missing")
    entries: list[tuple[str, PurePosixPath]] = []
    seen: set[PurePosixPath] = set()
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not line:
            continue
        match = re.fullmatch(r"([0-9a-f]{64})  (.+)", line)
        if not match:
            fail(f"invalid SHA256SUMS line {number}")
        relative = safe_relative(match.group(2), "checksum")
        if relative == CHECKSUM_MANIFEST or relative in seen:
            fail(f"duplicate or recursive checksum entry: {relative}")
        seen.add(relative)
        entries.append((match.group(1), relative))
    expected = {
        PurePosixPath(path.relative_to(package_root).as_posix())
        for path in iter_regular_files(package_root)
        if PurePosixPath(path.relative_to(package_root).as_posix()) != CHECKSUM_MANIFEST
    }
    if seen != expected:
        missing = sorted(str(item) for item in expected - seen)
        extra = sorted(str(item) for item in seen - expected)
        fail(f"SHA256SUMS is incomplete (missing={missing}, extra={extra})")
    return entries


def verify_checksums(package_root: Path) -> None:
    for expected, relative in read_checksums(package_root):
        path = package_root / relative
        if not path.is_file() or sha256_file(path) != expected:
            fail(f"checksum mismatch: {relative}")


def set_tree_metadata(root: Path, epoch: int) -> None:
    for path in sorted(root.rglob("*"), reverse=True):
        if path.is_symlink():
            fail(f"release package contains a symlink: {path}")
        os.utime(path, (epoch, epoch), follow_symlinks=False)
        if path.is_dir():
            path.chmod(0o755)
        elif path.is_file():
            executable = path.suffix in {".sh", ".py"} or path.name in {
                "deploy.sh",
                "deploy-ecs.sh",
                "copy.sh",
            }
            path.chmod(0o755 if executable else 0o644)
    os.utime(root, (epoch, epoch), follow_symlinks=False)
    root.chmod(0o755)


def create_deterministic_archive(package_root: Path, archive_path: Path, epoch: int) -> None:
    temporary = archive_path.with_suffix(archive_path.suffix + ".tmp")
    temporary.unlink(missing_ok=True)
    with temporary.open("wb") as raw:
        with gzip.GzipFile(fileobj=raw, mode="wb", filename="", mtime=epoch) as compressed:
            with tarfile.open(fileobj=compressed, mode="w") as tar:
                paths = [package_root, *sorted(package_root.rglob("*"))]
                for path in paths:
                    if path.is_symlink():
                        fail(f"cannot archive symlink: {path}")
                    arcname = PurePosixPath(package_root.name) / PurePosixPath(
                        path.relative_to(package_root).as_posix()
                    )
                    info = tar.gettarinfo(str(path), arcname=str(arcname))
                    info.uid = 0
                    info.gid = 0
                    info.uname = "root"
                    info.gname = "root"
                    info.mtime = epoch
                    if info.isdir():
                        info.mode = 0o755
                        tar.addfile(info)
                    elif info.isfile():
                        info.mode = path.stat().st_mode & 0o777
                        with path.open("rb") as handle:
                            tar.addfile(info, handle)
                    else:
                        fail(f"unsupported archive entry: {path}")
    os.replace(temporary, archive_path)
    archive_path.chmod(0o444)


def safe_extract_archive(archive_path: Path, destination: Path) -> Path:
    if not archive_path.is_file():
        fail(f"release archive is missing: {archive_path}")
    destination.mkdir(parents=True, exist_ok=True)
    try:
        with tarfile.open(archive_path, "r:gz") as archive:
            members = archive.getmembers()
            roots: set[str] = set()
            seen_paths: set[PurePosixPath] = set()
            for member in members:
                relative = PurePosixPath(member.name)
                if relative.is_absolute() or ".." in relative.parts or not relative.parts:
                    fail("release archive contains an unsafe path")
                if relative in seen_paths:
                    fail("release archive contains a duplicate path")
                seen_paths.add(relative)
                if not member.isdir() and not member.isfile():
                    fail("release archive contains an unsupported entry")
                roots.add(relative.parts[0])
            if len(roots) != 1:
                fail("release archive must contain exactly one release root")

            # Python 3.12 added TarFile.extractall(filter="data"). Production
            # hosts can still provide Python 3.9, so perform the already
            # validated extraction explicitly instead of depending on that
            # version-specific API. Only regular files and directories are
            # materialized; ownership and special permission bits are ignored.
            destination_root = destination.resolve()
            for member in members:
                relative = PurePosixPath(member.name)
                target = destination.joinpath(*relative.parts)
                resolved_target = target.resolve(strict=False)
                if destination_root not in resolved_target.parents:
                    fail("release archive contains an unsafe extraction target")
                if member.isdir():
                    target.mkdir(parents=True, exist_ok=True)
                    target.chmod(member.mode & 0o777)
                    continue
                target.parent.mkdir(parents=True, exist_ok=True)
                if target.exists():
                    fail("release archive path conflicts with an existing entry")
                source = archive.extractfile(member)
                if source is None:
                    fail("release archive file content is unreadable")
                with source, target.open("xb") as handle:
                    shutil.copyfileobj(source, handle)
                target.chmod(member.mode & 0o777)
    except (OSError, tarfile.TarError) as exc:
        fail(f"cannot extract release archive: {exc}")
    root = destination / next(iter(roots))
    if not root.is_dir():
        fail("release archive root is missing")
    return root


def release_identity_from_manifest(package_root: Path) -> dict[str, str]:
    manifest = read_json(package_root / RELEASE_MANIFEST)
    identity = {
        "releaseId": str(manifest.get("releaseId", "")),
        "gitCommit": str(manifest.get("gitCommit", "")),
        "buildTime": str(manifest.get("buildTime", "")),
    }
    validate_identity(identity["releaseId"], identity["gitCommit"], identity["buildTime"])
    return identity


def verify_package_root(
    package_root: Path, contract: dict[str, Any], expected_identity: dict[str, str] | None = None
) -> dict[str, Any]:
    check_suspicious_names(package_root)
    verify_checksums(package_root)
    identity = release_identity_from_manifest(package_root)
    if expected_identity and identity != expected_identity:
        fail("package identity differs from the expected release identity")
    frontend = contract["frontend"]
    frontend_destination = package_root / safe_relative(
        frontend["destination"], "frontend destination"
    )
    validate_frontend_release_info(
        frontend_destination,
        frontend["releaseInfo"],
        identity,
        allow_unstamped=False,
    )
    artifacts: list[dict[str, str]] = []
    for artifact in contract["artifactMap"]:
        destination = package_root / safe_relative(
            artifact["destination"], "artifact destination"
        )
        siblings = sorted(path.name for path in destination.parent.glob("*.jar"))
        if siblings != [destination.name]:
            fail(f"package JAR directory is not canonical: {destination.parent}")
        verify_stamped_jar(destination, identity)
        artifacts.append(
            {
                "component": artifact["component"],
                "path": artifact["destination"],
                "sha256": sha256_file(destination),
            }
        )
    sbom = read_json(package_root / SBOM_MANIFEST)
    if (
        sbom.get("spdxVersion") != "SPDX-2.3"
        or sbom.get("name") != identity["releaseId"]
        or not isinstance(sbom.get("files"), list)
    ):
        fail("SPDX SBOM identity or structure is invalid")
    manifest = read_json(package_root / RELEASE_MANIFEST)
    if manifest.get("artifacts") != artifacts:
        fail("release manifest artifact inventory differs from packaged artifacts")
    migration_plan = verify_packaged_migration_plan(
        package_root, manifest.get("migrationPlan")
    )
    expected_toolchain_reference = {
        "path": TOOLCHAIN_MANIFEST.as_posix(),
        "contract": contract["buildToolchain"],
    }
    if manifest.get("toolchain") != expected_toolchain_reference:
        fail("release manifest toolchain reference differs from the contract")
    toolchain = validate_toolchain_manifest(package_root, contract)
    checksum_count = len(read_checksums(package_root))
    return {
        "schemaVersion": 1,
        "releaseId": identity["releaseId"],
        "gitCommit": identity["gitCommit"],
        "buildTime": identity["buildTime"],
        "artifactCount": len(artifacts),
        "migrationReleaseId": (
            migration_plan["releaseId"] if migration_plan is not None else None
        ),
        "migrationManifestSha256": (
            migration_plan["manifestSha256"]
            if migration_plan is not None
            else None
        ),
        "checksumCount": checksum_count,
        "sbomFileCount": len(sbom["files"]),
        "toolchain": toolchain,
        "status": "verified",
    }


def verify_archive(archive_path: Path, contract: dict[str, Any]) -> dict[str, Any]:
    with tempfile.TemporaryDirectory(prefix="erp-release-verify-") as temporary:
        package_root = safe_extract_archive(archive_path, Path(temporary))
        result = verify_package_root(package_root, contract)
    result["archive"] = str(archive_path)
    result["archiveSha256"] = sha256_file(archive_path)
    return result


def create_release_package(
    repo_root: Path,
    output_dir: Path,
    contract: dict[str, Any],
    identity: dict[str, str],
    epoch: int,
    source_state: dict[str, Any],
    previous_archive: Path | None,
    migration_manifest: Path | None = None,
) -> dict[str, Any]:
    check_clean_deployment_sources(repo_root, contract)
    artifact_inputs = validate_artifact_inputs(repo_root, contract)
    output_dir.mkdir(parents=True, exist_ok=True)
    archive_path = output_dir / f"{identity['releaseId']}{ARCHIVE_SUFFIX}"
    if archive_path.exists():
        fail(f"immutable release archive already exists: {archive_path}")
    previous: dict[str, Any] | None = None
    if previous_archive is not None:
        previous_verification = verify_archive(previous_archive, contract)
        if previous_verification["releaseId"] == identity["releaseId"]:
            fail("previous and current release IDs must differ")
        previous = {
            "releaseId": previous_verification["releaseId"],
            "gitCommit": previous_verification["gitCommit"],
            "buildTime": previous_verification["buildTime"],
            "archive": str(previous_archive.resolve()),
            "archiveSha256": previous_verification["archiveSha256"],
        }
    with tempfile.TemporaryDirectory(prefix="erp-release-build-", dir=output_dir) as temporary:
        package_root = Path(temporary) / identity["releaseId"]
        package_root.mkdir()
        copy_tracked_docker(repo_root, package_root)
        migration_plan = package_migration_manifest(
            repo_root, package_root, migration_manifest
        )
        release_tools = package_root / "release-tools"
        release_tools.mkdir(parents=True)
        shutil.copyfile(Path(__file__).resolve(), release_tools / "release_tool.py")
        shutil.copyfile(DEFAULT_CONTRACT, release_tools / "release-contract.json")
        standard = repo_root / "docs/releases/production-release-standard.md"
        if standard.is_file():
            shutil.copyfile(standard, release_tools / standard.name)

        frontend = contract["frontend"]
        frontend_source = repo_root / safe_relative(frontend["source"], "frontend source")
        frontend_destination = package_root / safe_relative(
            frontend["destination"], "frontend destination"
        )
        frontend_destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copytree(frontend_source, frontend_destination)
        stamp_frontend(frontend_destination, frontend["releaseInfo"], identity)

        artifacts: list[dict[str, str]] = []
        for artifact in contract["artifactMap"]:
            source = repo_root / safe_relative(artifact["source"], "artifact source")
            destination = package_root / safe_relative(
                artifact["destination"], "artifact destination"
            )
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, destination)
            stamp_jar(destination, identity)
            verify_stamped_jar(destination, identity)
            artifacts.append(
                {
                    "component": artifact["component"],
                    "path": artifact["destination"],
                    "sha256": sha256_file(destination),
                }
            )

        migration_records = []
        mysql_root = package_root / "docker/mysql"
        if mysql_root.exists():
            for sql_path in sorted(mysql_root.rglob("*.sql")):
                migration_records.append(
                    {
                        "path": sql_path.relative_to(package_root).as_posix(),
                        "sha256": sha256_file(sql_path),
                    }
                )
        release_manifest = {
            "schemaVersion": 1,
            "releaseId": identity["releaseId"],
            "gitCommit": identity["gitCommit"],
            "buildTime": identity["buildTime"],
            "source": source_state,
            "artifactInputs": artifact_inputs,
            "artifacts": artifacts,
            "frontend": {
                "path": frontend["destination"],
                "releaseInfo": (
                    PurePosixPath(frontend["destination"]) / frontend["releaseInfo"]
                ).as_posix(),
            },
            "databaseMigrations": migration_records,
            "migrationPlan": migration_plan,
            "environmentContract": "docker/.env.example",
            "checksumPolicy": {
                "algorithm": "SHA-256",
                "manifest": CHECKSUM_MANIFEST.as_posix(),
                "selfExcluded": True,
            },
            "sbom": {
                "format": "SPDX-2.3",
                "path": SBOM_MANIFEST.as_posix(),
                "generator": "file-level fallback; syft availability recorded in toolchain.json",
            },
            "toolchain": {
                "path": TOOLCHAIN_MANIFEST.as_posix(),
                "contract": contract["buildToolchain"],
            },
        }
        write_json(package_root / RELEASE_MANIFEST, release_manifest)
        write_json(package_root / TOOLCHAIN_MANIFEST, collect_toolchain(repo_root))
        write_json(
            package_root / ROLLBACK_PLAN,
            {
                "schemaVersion": 1,
                "current": identity,
                "previous": previous,
                "procedure": [
                    "verify archive SHA-256 and internal SHA256SUMS",
                    "materialize current and previous under immutable release directories",
                    "atomically switch current pointer to previous",
                    "run read-only health and business smoke checks",
                    "retain failed release for investigation; never roll back database destructively",
                ],
                "databasePolicy": "forward-fix only unless an independently approved migration rollback exists",
            },
        )
        write_json(package_root / SBOM_MANIFEST, create_spdx(package_root, identity))
        write_checksums(package_root)
        set_tree_metadata(package_root, epoch)
        verification = verify_package_root(package_root, contract, identity)
        create_deterministic_archive(package_root, archive_path, epoch)

    archive_hash = sha256_file(archive_path)
    archive_checksum = output_dir / f"{identity['releaseId']}.SHA256"
    archive_checksum.write_text(
        f"{archive_hash}  {archive_path.name}\n", encoding="utf-8"
    )
    archive_checksum.chmod(0o444)
    state_path = output_dir / f"{identity['releaseId']}.release.json"
    write_json(
        state_path,
        {
            "schemaVersion": 1,
            "current": {
                **identity,
                "archive": archive_path.name,
                "archiveSha256": archive_hash,
            },
            "previous": previous,
            "verification": verification,
        },
    )
    state_path.chmod(0o444)
    archive_verification = verify_archive(archive_path, contract)
    return {
        "archive": str(archive_path),
        "archiveSha256": archive_hash,
        "checksumFile": str(archive_checksum),
        "releaseState": str(state_path),
        "previous": previous,
        "verification": archive_verification,
    }


def stamp_deploy_tree(
    docker_root: Path, contract: dict[str, Any], identity: dict[str, str]
) -> dict[str, Any]:
    if docker_root.name != "docker":
        fail("deploy tree must be a directory named docker")
    frontend = contract["frontend"]
    relative_frontend = PurePosixPath(frontend["destination"])
    if relative_frontend.parts[0] != "docker":
        fail("frontend destination must begin with docker")
    frontend_dir = docker_root.parent / relative_frontend
    check_suspicious_names(frontend_dir)
    stamp_frontend(frontend_dir, frontend["releaseInfo"], identity)
    artifacts: list[dict[str, str]] = []
    for artifact in contract["artifactMap"]:
        relative = PurePosixPath(artifact["destination"])
        if relative.parts[0] != "docker":
            fail("artifact destination must begin with docker")
        path = docker_root.parent / relative
        siblings = sorted(item.name for item in path.parent.glob("*.jar"))
        if siblings != [path.name]:
            fail(f"deploy tree JAR directory is not canonical: {path.parent}")
        stamp_jar(path, identity)
        verify_stamped_jar(path, identity)
        artifacts.append(
            {
                "component": artifact["component"],
                "path": str(path),
                "sha256": sha256_file(path),
            }
        )
    return {
        "schemaVersion": 1,
        **identity,
        "artifacts": artifacts,
        "status": "stamped-and-verified",
    }


def rollback_dry_run(
    current_archive: Path,
    previous_archive: Path,
    contract: dict[str, Any],
    evidence_path: Path,
) -> dict[str, Any]:
    current = verify_archive(current_archive, contract)
    previous = verify_archive(previous_archive, contract)
    if current["releaseId"] == previous["releaseId"]:
        fail("rollback dry-run requires different current and previous releases")
    steps: list[dict[str, str]] = []
    with tempfile.TemporaryDirectory(prefix="erp-rollback-dry-run-") as temporary:
        root = Path(temporary)
        releases = root / "releases"
        releases.mkdir()
        current_root = safe_extract_archive(current_archive, releases)
        previous_root = safe_extract_archive(previous_archive, releases)
        current_pointer = root / "current"
        previous_pointer = root / "previous"
        current_pointer.symlink_to(current_root.relative_to(root))
        previous_pointer.symlink_to(previous_root.relative_to(root))
        steps.append({"step": "activate-current", "releaseId": release_identity_from_manifest(current_pointer)["releaseId"]})
        switched = root / "current.next"
        switched.symlink_to(previous_root.relative_to(root))
        os.replace(switched, current_pointer)
        steps.append({"step": "switch-to-previous", "releaseId": release_identity_from_manifest(current_pointer)["releaseId"]})
        restored = root / "current.next"
        restored.symlink_to(current_root.relative_to(root))
        os.replace(restored, current_pointer)
        steps.append({"step": "restore-current", "releaseId": release_identity_from_manifest(current_pointer)["releaseId"]})
    evidence = {
        "schemaVersion": 1,
        "mode": "filesystem-pointer-dry-run",
        "businessDataTouched": False,
        "current": current,
        "previous": previous,
        "steps": steps,
        "status": "passed",
    }
    write_json(evidence_path, evidence)
    return evidence


def command_validate_env(args: argparse.Namespace) -> dict[str, Any]:
    contract = load_contract(args.contract)
    return validate_environment(
        contract,
        args.env_file.resolve(),
        args.repo_root.resolve(),
        args.template,
    )


def command_package(args: argparse.Namespace) -> dict[str, Any]:
    repo_root = args.repo_root.resolve()
    contract = load_contract(args.contract)
    release_id, git_commit, build_time, epoch = validate_identity(
        args.release_id, args.git_commit, args.build_time
    )
    identity = {
        "releaseId": release_id,
        "gitCommit": git_commit,
        "buildTime": build_time,
    }
    source_state = require_clean_commit(repo_root, git_commit, args.allow_dirty)
    previous_archive = args.previous.resolve() if args.previous else None
    migration_manifest = (
        args.migration_manifest.resolve() if args.migration_manifest else None
    )
    return create_release_package(
        repo_root,
        args.output_dir.resolve(),
        contract,
        identity,
        epoch,
        source_state,
        previous_archive,
        migration_manifest,
    )


def command_verify(args: argparse.Namespace) -> dict[str, Any]:
    return verify_archive(args.archive.resolve(), load_contract(args.contract))


def command_stamp_deploy(args: argparse.Namespace) -> dict[str, Any]:
    release_id, git_commit, build_time, _ = validate_identity(
        args.release_id, args.git_commit, args.build_time
    )
    return stamp_deploy_tree(
        args.docker_dir.resolve(),
        load_contract(args.contract),
        {
            "releaseId": release_id,
            "gitCommit": git_commit,
            "buildTime": build_time,
        },
    )


def command_rollback(args: argparse.Namespace) -> dict[str, Any]:
    return rollback_dry_run(
        args.current.resolve(),
        args.previous.resolve(),
        load_contract(args.contract),
        args.evidence.resolve(),
    )


def command_preflight(args: argparse.Namespace) -> dict[str, Any]:
    contract = load_contract(args.contract)
    repo_root = args.repo_root.resolve()
    environment = validate_environment(
        contract, args.env_file.resolve(), repo_root, allow_placeholders=False
    )
    compose_files = [path.resolve() for path in args.compose_file]
    if len(compose_files) != len(set(compose_files)):
        fail("preflight Compose files must be unique")
    composes = [validate_compose_source(path) for path in compose_files]
    package = verify_archive(args.archive.resolve(), contract)
    values = parse_env(args.env_file.resolve())
    for env_name, package_name in (
        ("RELEASE_ID", "releaseId"),
        ("GIT_COMMIT", "gitCommit"),
        ("BUILD_TIME", "buildTime"),
    ):
        if values[env_name] != package[package_name]:
            fail(f"{env_name} differs between production environment and release archive")
    return {
        "schemaVersion": 1,
        "environment": environment,
        "composes": composes,
        "package": package,
        "releaseIdentityCoherent": True,
        "secretValuesEmitted": False,
        "status": "passed",
    }


def parser() -> argparse.ArgumentParser:
    root = argparse.ArgumentParser(description=__doc__)
    subcommands = root.add_subparsers(dest="command", required=True)

    validate = subcommands.add_parser("validate-env", help="validate production variable contract")
    validate.add_argument("--repo-root", type=Path, default=SCRIPT_DIR.parent.parent)
    validate.add_argument("--contract", type=Path, default=DEFAULT_CONTRACT)
    validate.add_argument("--env-file", type=Path, required=True)
    validate.add_argument("--template", action="store_true", help="validate declarations without secret values")
    validate.set_defaults(handler=command_validate_env)

    package = subcommands.add_parser("package", help="create an immutable release archive")
    package.add_argument("--repo-root", type=Path, default=SCRIPT_DIR.parent.parent)
    package.add_argument("--contract", type=Path, default=DEFAULT_CONTRACT)
    package.add_argument("--output-dir", type=Path, required=True)
    package.add_argument("--release-id", required=True)
    package.add_argument("--git-commit", required=True)
    package.add_argument("--build-time", required=True)
    package.add_argument("--previous", type=Path)
    package.add_argument(
        "--migration-manifest",
        type=Path,
        help="tracked automatic migration manifest to embed in the immutable archive",
    )
    package.add_argument("--allow-dirty", action="store_true", help=argparse.SUPPRESS)
    package.set_defaults(handler=command_package)

    verify = subcommands.add_parser("verify", help="verify an immutable release archive")
    verify.add_argument("--contract", type=Path, default=DEFAULT_CONTRACT)
    verify.add_argument("--archive", type=Path, required=True)
    verify.set_defaults(handler=command_verify)

    stamp = subcommands.add_parser("stamp-deploy-tree", help="stamp and verify a materialized Docker tree")
    stamp.add_argument("--contract", type=Path, default=DEFAULT_CONTRACT)
    stamp.add_argument("--docker-dir", type=Path, required=True)
    stamp.add_argument("--release-id", required=True)
    stamp.add_argument("--git-commit", required=True)
    stamp.add_argument("--build-time", required=True)
    stamp.set_defaults(handler=command_stamp_deploy)

    rollback = subcommands.add_parser("rollback-dry-run", help="verify current/previous pointer switching")
    rollback.add_argument("--contract", type=Path, default=DEFAULT_CONTRACT)
    rollback.add_argument("--current", type=Path, required=True)
    rollback.add_argument("--previous", type=Path, required=True)
    rollback.add_argument("--evidence", type=Path, required=True)
    rollback.set_defaults(handler=command_rollback)

    preflight = subcommands.add_parser(
        "preflight", help="verify environment, Compose source and package identity"
    )
    preflight.add_argument("--repo-root", type=Path, default=SCRIPT_DIR.parent.parent)
    preflight.add_argument("--contract", type=Path, default=DEFAULT_CONTRACT)
    preflight.add_argument("--env-file", type=Path, required=True)
    preflight.add_argument(
        "--compose-file",
        type=Path,
        action="append",
        required=True,
        help="Compose file to validate; repeat to gate multiple deployment variants",
    )
    preflight.add_argument("--archive", type=Path, required=True)
    preflight.set_defaults(handler=command_preflight)
    return root


def main() -> int:
    args = parser().parse_args()
    try:
        result = args.handler(args)
    except ReleaseError as exc:
        print(f"RELEASE_GATE_FAILED: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

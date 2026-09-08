#!/usr/bin/env python3
"""Verify a deploy-host tarball without extracting it."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import tarfile
from pathlib import PurePosixPath


MANIFEST_MEMBER = "docker/release/new-business-release-20260714.json"
LIST_MEMBER = "docker/release/new-business-migrations-20260713.list"
FORBIDDEN_PARTS = {".env", ".DS_Store"}
FORBIDDEN_DIRECTORY_PARTS = {
    "backup",
    "backups",
    "dump",
    "dumps",
    "logs",
    "runtime",
    "seed",
    "temp",
    "tmp",
    "upload",
    "uploads",
    "uploadpath",
}
FORBIDDEN_SUFFIXES = (
    ".7z",
    ".backup",
    ".bak",
    ".csv",
    ".dump",
    ".jks",
    ".key",
    ".keystore",
    ".p12",
    ".pem",
    ".pfx",
    ".rar",
    ".sql.gz",
    ".tar",
    ".tar.gz",
    ".tgz",
    ".tsv",
    ".xls",
    ".xlsx",
    ".zip",
)
PRIVATE_KEY_NAMES = {"id_dsa", "id_ecdsa", "id_ed25519", "id_rsa"}
FINDER_COPY_PATTERN = re.compile(r" \d+(?:\..*)?$")
FORBIDDEN_PREFIXES = (
    "docker/mysql/data/",
    "docker/mysql/logs/",
    "docker/redis/data/",
    "docker/nacos/logs/",
    "docker/nginx/logs/",
    "docker/erp/uploadPath/",
)
REQUIRED_BACKEND_JARS = frozenset(
    {
        "docker/erp/gateway/jar/erp-gateway.jar",
        "docker/erp/auth/jar/erp-auth.jar",
        "docker/erp/visual/monitor/jar/erp-visual-monitor.jar",
        "docker/erp/modules/system/jar/erp-modules-system.jar",
        "docker/erp/modules/file/jar/erp-modules-file.jar",
        "docker/erp/modules/job/jar/erp-modules-job.jar",
        "docker/erp/modules/oa/jar/erp-modules-oa.jar",
        "docker/erp/modules/inventory/jar/erp-modules-inventory.jar",
        "docker/erp/modules/approval/jar/erp-modules-approval.jar",
    }
)


def verify_archive(path) -> dict:
    with tarfile.open(path, "r:gz") as archive:
        members = archive.getmembers()
        names = [member.name.rstrip("/") for member in members]
        name_set = set(names)

        for member, name in zip(members, names):
            pure = PurePosixPath(name)
            if pure.is_absolute() or ".." in pure.parts:
                raise ValueError(f"unsafe archive path: {name}")
            if member.issym() or member.islnk():
                raise ValueError(f"archive links are not allowed: {name}")
            if any(part in FORBIDDEN_PARTS or part.startswith("._") for part in pure.parts):
                raise ValueError(f"forbidden local/runtime file in archive: {name}")
            lowered_parts = tuple(part.casefold() for part in pure.parts)
            if any(part in FORBIDDEN_DIRECTORY_PARTS for part in lowered_parts):
                raise ValueError(f"forbidden data directory in archive: {name}")
            if any(
                part.startswith(".env.") and part != ".env.example"
                for part in lowered_parts
            ):
                raise ValueError(f"forbidden environment file in archive: {name}")
            if any(FINDER_COPY_PATTERN.search(part) for part in pure.parts):
                raise ValueError(f"Finder-style duplicate in archive: {name}")
            lowered_name = name.casefold()
            if lowered_name.endswith(FORBIDDEN_SUFFIXES):
                raise ValueError(f"forbidden data/secret file in archive: {name}")
            if pure.name.casefold() in PRIVATE_KEY_NAMES:
                raise ValueError(f"private-key-like file in archive: {name}")
            if any(name == prefix.rstrip("/") or name.startswith(prefix) for prefix in FORBIDDEN_PREFIXES):
                raise ValueError(f"runtime data in archive: {name}")

        if MANIFEST_MEMBER not in name_set or LIST_MEMBER not in name_set:
            raise ValueError("archive release manifest/list is missing")
        manifest_file = archive.extractfile(MANIFEST_MEMBER)
        list_file = archive.extractfile(LIST_MEMBER)
        if manifest_file is None or list_file is None:
            raise ValueError("archive release metadata is not a regular file")
        manifest = json.loads(manifest_file.read().decode("utf-8"))
        ordered = [
            line.strip()
            for line in list_file.read().decode("utf-8").splitlines()
            if line.strip() and not line.lstrip().startswith("#")
        ]
        migrations = manifest.get("migrations")
        expected_migration_count = manifest.get("migrationCount")
        if (
            not isinstance(expected_migration_count, int)
            or isinstance(expected_migration_count, bool)
            or expected_migration_count < 1
        ):
            raise ValueError("archive manifest migrationCount must be a positive integer")
        if (
            not isinstance(migrations, list)
            or len(migrations) != expected_migration_count
        ):
            raise ValueError(
                "archive manifest migration count differs from migrationCount"
            )
        if [item.get("file") for item in migrations] != ordered:
            raise ValueError("archive migration list and manifest order differ")

        release_id = manifest.get("releaseId")
        sql_prefix = f"docker/mysql/releases/{release_id}/"
        expected_sql = []
        for item in migrations:
            member_name = sql_prefix + item["file"]
            expected_sql.append(member_name)
            if member_name not in name_set:
                raise ValueError(f"archive migration is missing: {item['file']}")
            sql_file = archive.extractfile(member_name)
            if sql_file is None:
                raise ValueError(f"archive migration is not a regular file: {item['file']}")
            actual = hashlib.sha256(sql_file.read()).hexdigest()
            if actual != item.get("sha256"):
                raise ValueError(f"archive migration hash mismatch: {item['file']}")

        actual_release_sql = sorted(
            name for name in names if name.startswith(sql_prefix) and name.endswith(".sql")
        )
        if actual_release_sql != sorted(expected_sql):
            raise ValueError("archive release SQL directory contains undeclared files")
        if "docker/nginx/html/dist/index.html" not in name_set:
            raise ValueError("archive frontend index is missing")
        actual_backend_jars = {
            name
            for name in names
            if name.startswith("docker/erp/") and name.endswith(".jar")
        }
        missing_jars = sorted(REQUIRED_BACKEND_JARS - actual_backend_jars)
        unexpected_jars = sorted(actual_backend_jars - REQUIRED_BACKEND_JARS)
        if missing_jars:
            raise ValueError(
                "archive required backend jars are missing: " + ", ".join(missing_jars)
            )
        if unexpected_jars:
            raise ValueError(
                "archive contains unexpected backend jars: " + ", ".join(unexpected_jars)
            )

        return {
            "releaseId": release_id,
            "migrationCount": len(expected_sql),
            "backendJarCount": len(actual_backend_jars),
            "memberCount": len(members),
            "archiveSha256": hashlib.sha256(path.read_bytes()).hexdigest(),
        }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("archive", type=PurePosixPath)
    parser.add_argument("--json-output")
    args = parser.parse_args()
    from pathlib import Path

    path = Path(str(args.archive)).resolve()
    result = verify_archive(path)
    if args.json_output:
        output = Path(args.json_output)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(
        f"[PASS] release archive {result['releaseId']} contains exactly "
        f"{result['migrationCount']} verified migrations and no forbidden runtime data"
    )


if __name__ == "__main__":
    main()

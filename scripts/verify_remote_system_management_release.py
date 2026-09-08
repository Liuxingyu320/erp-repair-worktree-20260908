#!/usr/bin/env python3
"""Verify an active remote payload against the finalized Release A manifest."""

from __future__ import annotations

import argparse
import json
import re
import zipfile
from pathlib import Path

from verify_system_management_release_contract import (
    EXPECTED_ARTIFACTS,
    FULL_SHA,
    SHA256,
    directory_sha256,
    file_sha256,
    read_build_commit,
)


def remote_artifact_path(root: Path, packaged: object) -> Path:
    if not isinstance(packaged, str):
        raise ValueError("packaged artifact path must be a string")
    relative = Path(packaged)
    if relative.is_absolute() or ".." in relative.parts or not relative.parts or relative.parts[0] != "docker":
        raise ValueError(f"unsafe packaged artifact path: {packaged!r}")
    path = (root / Path(*relative.parts[1:])).resolve()
    try:
        path.relative_to(root)
    except ValueError as exc:
        raise ValueError(f"unsafe packaged artifact path: {packaged!r}") from exc
    return path


def verify(remote_root: Path, manifest_path: Path) -> dict:
    remote_root = remote_root.resolve()
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if manifest.get("releaseId") != "system-management-20260714":
        raise ValueError("unexpected remote releaseId")
    if manifest.get("status") != "ready":
        raise ValueError("remote manifest is not a ready candidate")
    commit = manifest.get("build", {}).get("commit")
    if not isinstance(commit, str) or not FULL_SHA.fullmatch(commit):
        raise ValueError("remote manifest commit is not finalized")
    prerequisites = manifest.get("prerequisiteReleaseIds")
    if prerequisites != ["new-business-20260714"]:
        raise ValueError("remote prerequisite release changed")
    prerequisite_path = remote_root / "release/new-business-release-20260714.json"
    if not prerequisite_path.is_file():
        raise ValueError("remote prerequisite manifest is missing")
    prerequisite = json.loads(prerequisite_path.read_text(encoding="utf-8"))
    if prerequisite.get("releaseId") != "new-business-20260714":
        raise ValueError("remote prerequisite manifest is invalid")

    artifacts = manifest.get("artifacts")
    if not isinstance(artifacts, list):
        raise ValueError("remote artifacts must be a list")
    by_id = {item.get("id"): item for item in artifacts if isinstance(item, dict)}
    if len(by_id) != len(artifacts) or set(by_id) != set(EXPECTED_ARTIFACTS):
        raise ValueError("remote artifact inventory is incomplete")

    system_jar = None
    results = []
    for artifact_id, (kind, _, packaged) in EXPECTED_ARTIFACTS.items():
        item = by_id[artifact_id]
        if item.get("type") != kind or item.get("packaged") != packaged:
            raise ValueError(f"remote artifact path/type changed: {artifact_id}")
        expected = item.get("sha256")
        if not isinstance(expected, str) or not SHA256.fullmatch(expected):
            raise ValueError(f"remote artifact hash is invalid: {artifact_id}")
        path = remote_artifact_path(remote_root, packaged)
        digest = file_sha256(path) if kind == "jar" else directory_sha256(path)
        if digest != expected:
            raise ValueError(f"remote artifact hash mismatch: {artifact_id}")
        if kind == "jar":
            if read_build_commit(path) != commit:
                raise ValueError(f"remote JAR build commit mismatch: {artifact_id}")
            try:
                with zipfile.ZipFile(path) as archive:
                    names = archive.namelist()
            except (OSError, zipfile.BadZipFile) as exc:
                raise ValueError(f"invalid remote JAR: {artifact_id}") from exc
            if len(names) != len(set(names)) or any(
                re.search(r"(?:^|/)(?:\._|[^/]+ [0-9]+\.class$)", name) for name in names
            ):
                raise ValueError(f"remote JAR contains duplicate/conflict entries: {artifact_id}")
            if artifact_id == "system":
                system_jar = path
        results.append((artifact_id, path, digest))

    if system_jar is None:
        raise ValueError("remote system JAR is missing")
    with zipfile.ZipFile(system_jar) as archive:
        names = set(archive.namelist())
    for fqcn in manifest.get("criticalClasses", []):
        class_name = "BOOT-INF/classes/" + fqcn.replace(".", "/") + ".class"
        if class_name not in names:
            raise ValueError(f"remote system critical class is missing: {fqcn}")

    release_info_path = remote_root / "nginx/html/dist/release-info.json"
    release_info = json.loads(release_info_path.read_text(encoding="utf-8"))
    if release_info != {
        "commit": commit,
        "buildTime": manifest.get("build", {}).get("buildTime"),
    }:
        raise ValueError("remote frontend release info differs from manifest")

    return {
        "releaseId": manifest["releaseId"],
        "commit": commit,
        "artifactCount": len(results),
        "artifacts": results,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path("/opt/erp-new"))
    parser.add_argument("--manifest", type=Path)
    parser.add_argument("--print-sha", action="store_true")
    args = parser.parse_args()
    manifest = args.manifest or args.root / "release/system-management-release-20260714.json"
    result = verify(args.root, manifest)
    if args.print_sha:
        for artifact_id, path, digest in result["artifacts"]:
            print(f"{digest}  {artifact_id}  {path}")
    print(
        f"REMOTE_SYSTEM_MANAGEMENT_RELEASE_OK release={result['releaseId']} "
        f"commit={result['commit']} artifacts={result['artifactCount']}"
    )


if __name__ == "__main__":
    main()

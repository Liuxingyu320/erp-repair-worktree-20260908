#!/usr/bin/env python3
"""Generate the immutable candidate manifest after all native artifacts exist."""

from __future__ import annotations

import argparse
import json
import re
import subprocess
from datetime import datetime
from pathlib import Path

from verify_system_management_release_contract import (
    EXPECTED_ARTIFACTS,
    FULL_SHA,
    directory_sha256,
    file_sha256,
    read_build_commit,
)


BROWSER_VERSION = re.compile(r"[0-9]+(?:\.[0-9]+){3}")


def build_candidate(root: Path, template_path: Path, commit: str, build_time: str, browser: str) -> dict:
    root = root.resolve()
    if not FULL_SHA.fullmatch(commit):
        raise ValueError("candidate commit must be a full lowercase Git SHA")
    try:
        datetime.fromisoformat(build_time.replace("Z", "+00:00"))
    except ValueError as exc:
        raise ValueError("candidate build time must be ISO-8601") from exc
    if not BROWSER_VERSION.fullmatch(browser):
        raise ValueError("candidate browser must be a four-part version")

    manifest = json.loads(template_path.read_text(encoding="utf-8"))
    if manifest.get("status") != "development" or manifest.get("artifacts") != []:
        raise ValueError("candidate must be generated from the clean development template")
    if manifest.get("build", {}).get("playwrightCli") != "0.1.17":
        raise ValueError("template Playwright CLI version changed")

    artifacts = []
    for artifact_id, (kind, source_value, packaged_value) in EXPECTED_ARTIFACTS.items():
        source = root / source_value
        packaged = root / packaged_value
        digest_function = file_sha256 if kind == "jar" else directory_sha256
        source_digest = digest_function(source)
        packaged_digest = digest_function(packaged)
        if source_digest != packaged_digest:
            raise ValueError(f"source and packaged artifact differ: {artifact_id}")
        if kind == "jar" and read_build_commit(source) != commit:
            raise ValueError(f"JAR build-info differs from candidate commit: {artifact_id}")
        artifacts.append({
            "id": artifact_id,
            "type": kind,
            "source": source_value,
            "packaged": packaged_value,
            "sha256": source_digest,
        })

    manifest["status"] = "ready"
    manifest["build"]["commit"] = commit
    manifest["build"]["buildTime"] = build_time
    manifest["build"]["browser"] = browser
    manifest["artifacts"] = artifacts
    return manifest


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parent.parent)
    parser.add_argument("--template", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--commit")
    parser.add_argument("--build-time", required=True)
    parser.add_argument("--browser", required=True)
    args = parser.parse_args()
    root = args.root.resolve()
    template = args.template or root / "scripts/system-management-release-20260714.json"
    output = args.output or root / "output/release/system-management-release-20260714.json"
    output = output.resolve()
    try:
        output.relative_to(root)
    except ValueError as exc:
        raise ValueError("candidate output must remain inside the repository") from exc
    commit = args.commit or subprocess.check_output(
        ["git", "-C", str(root), "rev-parse", "HEAD"], text=True
    ).strip()
    candidate = build_candidate(root, template, commit, args.build_time, args.browser)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(candidate, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(
        f"SYSTEM_MANAGEMENT_CANDIDATE_MANIFEST_WRITTEN path={output} "
        f"commit={commit} artifacts={len(candidate['artifacts'])}"
    )


if __name__ == "__main__":
    main()

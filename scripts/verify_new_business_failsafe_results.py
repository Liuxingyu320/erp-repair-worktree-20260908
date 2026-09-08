#!/usr/bin/env python3
"""Fail closed unless every release-manifest integration test really ran."""

from __future__ import annotations

import argparse
import json
import time
import xml.etree.ElementTree as ET
from pathlib import Path


def expected_classes(manifest: dict) -> list[str]:
    classes: list[str] = []
    marker = "/src/test/java/"
    for relative in manifest.get("integrationTests", []):
        if marker not in relative or not relative.endswith(".java"):
            raise ValueError(f"invalid integration test path: {relative!r}")
        class_path = relative.split(marker, 1)[1][:-5]
        classes.append(class_path.replace("/", "."))
    if len(classes) != 6 or len(set(classes)) != 6:
        raise ValueError("release manifest must identify exactly 6 integration tests")
    return classes


def collect_results(root: Path, expected: list[str], since_epoch: float) -> dict:
    reports = sorted(root.glob("erp-modules/*/target/failsafe-reports/TEST-*.xml"))
    suites: dict[str, dict[str, int | str]] = {}
    stale: list[str] = []

    for report in reports:
        if report.stat().st_mtime + 1 < since_epoch:
            stale.append(str(report.relative_to(root)))
            continue
        suite = ET.parse(report).getroot()
        name = suite.attrib.get("name", "")
        if not name and report.name.startswith("TEST-"):
            name = report.name[5:-4]
        suites[name] = {
            "report": str(report.relative_to(root)),
            "tests": int(suite.attrib.get("tests", "0")),
            "failures": int(suite.attrib.get("failures", "0")),
            "errors": int(suite.attrib.get("errors", "0")),
            "skipped": int(suite.attrib.get("skipped", "0")),
        }

    missing: list[str] = []
    invalid: list[dict] = []
    selected: dict[str, dict] = {}
    for class_name in expected:
        result = suites.get(class_name)
        if result is None:
            # Surefire/Failsafe versions sometimes use only the simple class
            # name in the suite attribute, while the report file remains fully
            # qualified. Permit that representation without weakening identity.
            simple = class_name.rsplit(".", 1)[-1]
            matches = [value for key, value in suites.items() if key == simple]
            if len(matches) == 1:
                result = matches[0]
        if result is None:
            missing.append(class_name)
            continue
        selected[class_name] = result
        if (
            result["tests"] <= 0
            or result["failures"] != 0
            or result["errors"] != 0
            or result["skipped"] != 0
        ):
            invalid.append({"class": class_name, **result})

    summary = {
        "checkedAtEpoch": int(time.time()),
        "sinceEpoch": int(since_epoch),
        "expectedCount": len(expected),
        "executedCount": len(selected),
        "tests": sum(int(value["tests"]) for value in selected.values()),
        "failures": sum(int(value["failures"]) for value in selected.values()),
        "errors": sum(int(value["errors"]) for value in selected.values()),
        "skipped": sum(int(value["skipped"]) for value in selected.values()),
        "results": selected,
        "missing": missing,
        "invalid": invalid,
        "staleReportsIgnored": stale,
    }
    if missing or invalid:
        details = []
        if missing:
            details.append("missing=" + ",".join(missing))
        if invalid:
            details.append(
                "invalid=" + ",".join(str(item["class"]) for item in invalid)
            )
        raise RuntimeError("Failsafe release gate failed: " + "; ".join(details))
    return summary


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--since-epoch", type=float, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    root = args.root.resolve()
    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    summary = collect_results(root, expected_classes(manifest), args.since_epoch)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(summary, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(
        f"[PASS] all {len(summary['results'])} release integration tests "
        "executed without failures, errors or skips"
    )


if __name__ == "__main__":
    main()

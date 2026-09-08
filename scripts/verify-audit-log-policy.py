#!/usr/bin/env python3
"""Inventory every Java @Log endpoint and enforce the fail-closed payload policy."""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import asdict, dataclass
from pathlib import Path


METHOD = re.compile(
    r"\b(?:public|protected|private)\s+(?:[\w$.<>?,\[\]\s]+?)\s+(\w+)\s*\(",
    re.MULTILINE,
)
CLASS = re.compile(r"\bclass\s+(\w+)")
INCLUDE = re.compile(r"includeParamNames\s*=\s*\{([^}]*)\}", re.DOTALL)


@dataclass(frozen=True)
class Endpoint:
    file: str
    line: int
    class_name: str
    method: str
    policy: str
    allowed_fields: tuple[str, ...]


def _balanced_annotation(source: str, start: int) -> tuple[str, int]:
    open_index = source.find("(", start)
    if open_index < 0:
        raise ValueError("@Log annotation has no opening parenthesis")
    depth = 0
    quote = ""
    escaped = False
    for index in range(open_index, len(source)):
        char = source[index]
        if quote:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == quote:
                quote = ""
            continue
        if char in ('"', "'"):
            quote = char
        elif char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
            if depth == 0:
                return source[start : index + 1], index + 1
    raise ValueError("unterminated @Log annotation")


def _allowed_fields(annotation: str) -> tuple[str, ...]:
    match = INCLUDE.search(annotation)
    if not match:
        return ()
    return tuple(re.findall(r'"([^"\\]+)"', match.group(1)))


def inspect_source(source: str, relative_file: str) -> tuple[list[Endpoint], list[str]]:
    endpoints: list[Endpoint] = []
    errors: list[str] = []
    for match in re.finditer(r"@Log\s*\(", source):
        try:
            annotation, end = _balanced_annotation(source, match.start())
        except ValueError as exc:
            errors.append(f"{relative_file}:{source.count(chr(10), 0, match.start()) + 1}: {exc}")
            continue
        line = source.count("\n", 0, match.start()) + 1
        compact = re.sub(r"\s+", "", annotation)
        if "isSaveRequestData=true" in compact or "isSaveResponseData=true" in compact:
            errors.append(f"{relative_file}:{line}: payload persistence cannot be enabled")
        if "excludeParamNames=" in compact:
            errors.append(f"{relative_file}:{line}: excludeParamNames is forbidden; use an allowlist")

        method_match = METHOD.search(source, end, min(len(source), end + 2000))
        if not method_match:
            errors.append(f"{relative_file}:{line}: cannot resolve annotated method")
            continue
        classes = list(CLASS.finditer(source, 0, match.start()))
        class_name = classes[-1].group(1) if classes else "<unknown>"
        allowed = _allowed_fields(annotation)
        endpoints.append(
            Endpoint(
                file=relative_file,
                line=line,
                class_name=class_name,
                method=method_match.group(1),
                policy="allowed-fields" if allowed else "metadata-only",
                allowed_fields=allowed,
            )
        )
    return endpoints, errors


def inventory(root: Path) -> tuple[list[Endpoint], list[str]]:
    endpoints: list[Endpoint] = []
    errors: list[str] = []
    for path in sorted(root.glob("erp-*/**/src/main/java/**/*.java")):
        if any(part in {"target", "node_modules"} for part in path.parts):
            continue
        relative = path.relative_to(root).as_posix()
        found, source_errors = inspect_source(path.read_text(encoding="utf-8"), relative)
        endpoints.extend(found)
        errors.extend(source_errors)
    return endpoints, errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parent.parent)
    parser.add_argument("--json", action="store_true", dest="as_json")
    args = parser.parse_args()
    root = args.root.resolve()
    policy_path = root / "scripts/audit-log-policy.json"
    policy = json.loads(policy_path.read_text(encoding="utf-8"))
    if policy.get("schemaVersion") != 1 or policy.get("defaultPolicy") != "metadata-only":
        raise SystemExit("audit log policy must be schema 1 with metadata-only default")

    endpoints, errors = inventory(root)
    endpoint_keys = {f"{item.class_name}.{item.method}" for item in endpoints}
    declared = policy.get("allowedFieldEndpoints", {})
    if not isinstance(declared, dict):
        errors.append("allowedFieldEndpoints must be an object")
    else:
        for key, fields in declared.items():
            if key not in endpoint_keys:
                errors.append(f"policy references missing endpoint: {key}")
            if not isinstance(fields, list) or not fields:
                errors.append(f"allowed-field policy must declare a non-empty list: {key}")

    for relative in policy.get("sensitiveControllerFiles", []):
        path = root / relative
        if not path.is_file():
            errors.append(f"sensitive controller missing: {relative}")
            continue
        text = path.read_text(encoding="utf-8")
        for match in re.finditer(r"@Log\s*\(", text):
            annotation, _ = _balanced_annotation(text, match.start())
            compact = re.sub(r"\s+", "", annotation)
            if "isSaveRequestData=false" not in compact or "isSaveResponseData=false" not in compact:
                line = text.count("\n", 0, match.start()) + 1
                errors.append(f"{relative}:{line}: sensitive endpoint must be explicitly metadata-only")

    report = {
        "schemaVersion": 1,
        "defaultPolicy": "metadata-only",
        "endpointCount": len(endpoints),
        "metadataOnlyCount": sum(item.policy == "metadata-only" for item in endpoints),
        "allowedFieldCount": sum(item.policy == "allowed-fields" for item in endpoints),
        "endpoints": [asdict(item) for item in endpoints],
        "errors": errors,
    }
    if args.as_json:
        json.dump(report, sys.stdout, ensure_ascii=False, indent=2)
        sys.stdout.write("\n")
    else:
        print(
            "AUDIT_LOG_POLICY "
            f"endpoints={report['endpointCount']} metadata_only={report['metadataOnlyCount']} "
            f"allowed_fields={report['allowedFieldCount']} errors={len(errors)}"
        )
        for error in errors:
            print(error, file=sys.stderr)
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())

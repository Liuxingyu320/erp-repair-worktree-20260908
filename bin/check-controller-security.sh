#!/usr/bin/env bash
set -euo pipefail

python3 - <<'PY'
from pathlib import Path

roots = [
    Path("erp-auth/src/main/java"),
    Path("erp-gateway/src/main/java"),
    *Path("erp-modules").glob("*/src/main/java"),
]

mapping_tokens = ("@GetMapping", "@PostMapping", "@PutMapping", "@DeleteMapping", "@RequestMapping")
security_tokens = ("@RequiresLogin", "@RequiresPermissions", "@InnerAuth")
class_tokens = ("class ", "interface ")
excluded_files = {
    "InvBaseController.java",
    "OaBaseController.java",
}
allowed_public_mappings = {
    "erp-auth/src/main/java/com/erp/auth/controller/TokenController.java:@PostMapping(\"login\")":
        "login endpoint must be public",
    "erp-auth/src/main/java/com/erp/auth/controller/TokenController.java:@PostMapping(\"register\")":
        "registration endpoint is intentionally public when enabled by deployment policy",
    "erp-auth/src/main/java/com/erp/auth/controller/TokenController.java:@GetMapping(\"passwordPolicy\")":
        "password policy must be readable before registration",
}


def stripped(line):
    return line.strip()


def is_security_annotation(line):
    return stripped(line).startswith(security_tokens)


def class_has_security(lines, class_index):
    start = max(0, class_index - 40)
    for line in lines[start:class_index]:
        if is_security_annotation(line):
            return True
    return False


def method_has_security(lines, mapping_index):
    start = max(0, mapping_index - 40)
    for index in range(mapping_index - 1, start - 1, -1):
        line = stripped(lines[index])
        if is_security_annotation(line):
            return True
        if line.startswith("@"):
            continue
        if not line:
            continue
        if line.startswith(("public ", "protected ", "private ")):
            break
    return False


violations = []
for root in roots:
    if not root.exists():
        continue
    for path in root.rglob("*Controller.java"):
        if path.name in excluded_files:
            continue
        lines = path.read_text(encoding="utf-8").splitlines()
        class_index = next((i for i, line in enumerate(lines)
                            if any(token in line for token in class_tokens) and "Controller" in line), -1)
        class_secured = class_index >= 0 and class_has_security(lines, class_index)
        for index, line in enumerate(lines):
            line_text = stripped(line)
            if not line_text.startswith(mapping_tokens):
                continue
            if class_index >= 0 and index < class_index:
                continue
            mapping_key = f"{path}:{line_text}"
            if mapping_key in allowed_public_mappings:
                continue
            if class_secured:
                continue
            if not method_has_security(lines, index):
                violations.append(f"{path}:{index + 1}: {line_text}")

if violations:
    print("Controller mappings without @RequiresLogin, @RequiresPermissions, or @InnerAuth:")
    print("\n".join(violations))
    raise SystemExit(1)
PY

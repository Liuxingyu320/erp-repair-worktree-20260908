#!/usr/bin/env python3
"""Build deterministic ERP-NEW_2 phase-1 migration and API inventories.

The script only reads tracked sources inside the isolated worktree and writes
generated CSV/Markdown evidence below docs/erp-new-2-migration/.
"""

from __future__ import annotations

import csv
import hashlib
import re
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


EXPECTED_ROOT = Path("/Users/liuxingyu/.codex/worktrees/353b/ERP-NEW")
ROOT = Path(__file__).resolve().parents[2]
OUTPUT_DIR = ROOT / "docs" / "erp-new-2-migration"
TARGET_OVERRIDES_PATH = OUTPUT_DIR / "target-mapping-overrides.csv"
TARGET_OVERRIDE_FIELDS = [
    "old_page_source",
    "target_disposition",
    "new_route",
    "new_component",
    "automated_tests",
    "confirmation_status",
    "implementation_status",
    "target_evidence",
    "notes",
]
ALLOWED_TARGET_DISPOSITIONS = {"保留", "重设计", "合并", "下线"}
ALLOWED_IMPLEMENTATION_STATUSES = {"未开始", "部分实现", "已实现"}

PERMISSION_RE = re.compile(
    r"(?<![\w-])(?:[a-z][a-z0-9_-]*:){2,5}[a-zA-Z0-9_*-]+(?![\w-])"
)
KNOWN_PERMISSION_PREFIXES = {
    "approval",
    "drive",
    "hr",
    "inv",
    "monitor",
    "oa",
    "system",
    "tool",
}
IMPORT_VIEW_RE = re.compile(
    r"component\s*:\s*\(\)\s*=>\s*import\(\s*['\"]@/views/([^'\"]+)['\"]\s*\)"
)
PATH_RE = re.compile(r"\bpath\s*:\s*(['\"])(.*?)\1")
TITLE_RE = re.compile(r"\btitle\s*:\s*(['\"])(.*?)\1")
API_IMPORT_RE = re.compile(r"['\"]@/api/([^'\"]+)['\"]")
EXPORTED_FUNCTION_RE = re.compile(
    r"\bexport\s+(?:async\s+)?(?:function\s+|const\s+)([A-Za-z_$][\w$]*)"
)
URL_KEY_RE = re.compile(r"\burl\s*:")
METHOD_RE = re.compile(r"\bmethod\s*:\s*['\"]([A-Za-z]+)['\"]", re.IGNORECASE)
CONST_STRING_RE = re.compile(
    r"\bconst\s+([A-Za-z_$][\w$]*)\s*=\s*(['\"])(.*?)\2"
)
EXPORTED_ACTION_RE = re.compile(
    r"\bexport\s+const\s+([A-Za-z_$][\w$]*(?:ACTION|URL)[A-Za-z_$]*)\s*=\s*(['\"])(/.*?)\2"
)
MAPPING_RE = re.compile(
    r"@(Get|Post|Put|Delete|Patch|Request)Mapping(?:\s*\((.*?)\))?",
    re.DOTALL,
)
PUBLIC_METHOD_RE = re.compile(
    r"\bpublic\s+([\w<>,.?\[\] ]+?)\s+([A-Za-z_$][\w$]*)\s*\("
)


@dataclass(frozen=True)
class RouteEvidence:
    route: str
    title: str
    permissions: tuple[str, ...]
    source: str


def fail_if_outside_isolated_root() -> None:
    if ROOT != EXPECTED_ROOT or Path.cwd().resolve() != EXPECTED_ROOT:
        raise SystemExit(
            f"Refusing to run outside isolated worktree: root={ROOT} cwd={Path.cwd()}"
        )


def rel(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def line_number(text: str, offset: int) -> int:
    return text.count("\n", 0, offset) + 1


def permissions_from(text: str) -> list[str]:
    return [
        value
        for value in PERMISSION_RE.findall(text)
        if value.split(":", 1)[0] in KNOWN_PERMISSION_PREFIXES
    ]


def resolve_view_source(view_ref: str) -> str | None:
    base = ROOT / "erp-ui" / "src" / "views" / view_ref
    candidates = [base.with_suffix(".vue"), base / "index.vue"]
    for candidate in candidates:
        if candidate.is_file():
            return rel(candidate)
    return None


def extract_route_evidence() -> dict[str, list[RouteEvidence]]:
    result: dict[str, list[RouteEvidence]] = defaultdict(list)
    sources = [
        ROOT / "erp-ui" / "src" / "router" / "index.js",
        ROOT / "erp-ui" / "src" / "views" / "mobile" / "mobileRouteDefinitions.js",
    ]
    for source in sources:
        text = read_text(source)
        for match in IMPORT_VIEW_RE.finditer(text):
            view_source = resolve_view_source(match.group(1))
            if not view_source:
                continue
            prefix_start = max(0, match.start() - 4000)
            prefix = text[prefix_start : match.start()]
            paths = list(PATH_RE.finditer(prefix))
            route = paths[-1].group(2) if paths else ""
            block_start = prefix_start + (paths[-1].start() if paths else len(prefix))
            block = text[block_start : min(len(text), match.end() + 1800)]
            title_match = TITLE_RE.search(block)
            title = title_match.group(2) if title_match else ""
            permissions = tuple(sorted(set(permissions_from(block))))
            evidence = RouteEvidence(
                route=route,
                title=title,
                permissions=permissions,
                source=f"{rel(source)}:{line_number(text, match.start())}",
            )
            if evidence not in result[view_source]:
                result[view_source].append(evidence)
    return result


def view_component_keys(view_source: str) -> set[str]:
    prefix = "erp-ui/src/views/"
    value = view_source[len(prefix) :]
    no_ext = value.removesuffix(".vue")
    keys = {no_ext}
    if no_ext.endswith("/index"):
        keys.add(no_ext.removesuffix("/index"))
    return keys


def extract_sql_menu_evidence(
    view_sources: Iterable[str],
) -> dict[str, list[RouteEvidence]]:
    key_to_view: dict[str, set[str]] = defaultdict(set)
    for view_source in view_sources:
        for key in view_component_keys(view_source):
            key_to_view[key].add(view_source)

    result: dict[str, list[RouteEvidence]] = defaultdict(list)
    for source in sorted((ROOT / "sql").glob("*.sql")):
        text = read_text(source)
        for match in re.finditer(r"'([^']+)'", text):
            component = match.group(1)
            if component not in key_to_view:
                continue
            row_start = text.rfind("(", max(0, match.start() - 1200), match.start())
            row_end = text.find(")", match.end(), min(len(text), match.end() + 1600))
            context = text[row_start : row_end + 1] if row_start >= 0 and row_end >= 0 else ""
            strings = re.findall(r"'([^']*)'", context)
            title = strings[0] if strings else ""
            route = strings[1] if len(strings) > 1 else ""
            permissions = tuple(sorted(set(permissions_from(context))))
            evidence = RouteEvidence(
                route=route,
                title=title,
                permissions=permissions,
                source=f"{rel(source)}:{line_number(text, match.start())}",
            )
            for view_source in key_to_view[component]:
                if evidence not in result[view_source]:
                    result[view_source].append(evidence)
    return result


def domain_for_view(view_source: str) -> tuple[str, str]:
    parts = Path(view_source).parts
    relative_parts = parts[3:]  # after erp-ui/src/views
    if not relative_parts:
        return "platform", "desktop"
    if relative_parts[0] == "mobile":
        domain = relative_parts[1] if len(relative_parts) > 1 else "platform"
        if domain in {"components", "feature", "profile", "store", "warehouse"}:
            domain = "platform" if domain in {"components", "profile"} else domain
        return domain, "mobile-web"
    domain = relative_parts[0]
    if domain in {
        "login.vue",
        "register.vue",
        "redirect.vue",
        "lock.vue",
        "credential",
        "error",
        "profile-completion",
        "select-shop",
    }:
        domain = "platform"
    if domain in {"dashboard", "index.vue", "index_v1.vue"}:
        domain = "workbench"
    return domain.removesuffix(".vue"), "desktop"


def existing_test_references(view_source: str, tests: dict[str, str]) -> list[str]:
    view_relative = view_source.removeprefix("erp-ui/src/views/").removesuffix(".vue")
    repository_relative = view_source.removeprefix("erp-ui/")
    name = Path(view_source).stem
    path_references = {
        view_source,
        repository_relative,
        f"views/{view_relative}.vue",
    }
    if "/" in view_relative:
        path_references.add(f"{view_relative}.vue")
    references: list[str] = []
    for test_source, text in tests.items():
        exact_path_reference = any(reference in text for reference in path_references)
        named_component_reference = name[:1].isupper() and re.search(
            rf"\b{re.escape(name)}\b", text
        )
        if exact_path_reference or named_component_reference:
            references.append(test_source)
    return references


def classify_view(view_source: str, route_evidence: list[RouteEvidence]) -> str:
    path = Path(view_source)
    if route_evidence:
        return "route-page"
    if "components" in path.parts or (path.stem != "index" and path.stem[:1].isupper()):
        return "component"
    return "unmapped-page-candidate"


def write_csv(path: Path, fieldnames: list[str], rows: Iterable[dict[str, object]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames, lineterminator="\n")
        writer.writeheader()
        for row in rows:
            writer.writerow({field: row.get(field, "") for field in fieldnames})


def read_target_override_rows(
    path: Path = TARGET_OVERRIDES_PATH,
) -> list[dict[str, str]]:
    if not path.is_file():
        return []
    with path.open("r", encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle)
        if reader.fieldnames != TARGET_OVERRIDE_FIELDS:
            raise ValueError(
                "目标映射覆盖层表头不匹配："
                f"expected={TARGET_OVERRIDE_FIELDS} actual={reader.fieldnames}"
            )
        return [dict(row) for row in reader]


def index_target_overrides(
    rows: Iterable[dict[str, str]],
) -> dict[str, dict[str, str]]:
    indexed: dict[str, dict[str, str]] = {}
    for row_number, raw_row in enumerate(rows, 2):
        row = {field: str(raw_row.get(field, "")).strip() for field in TARGET_OVERRIDE_FIELDS}
        source = row["old_page_source"]
        if not source:
            raise ValueError(f"目标映射第 {row_number} 行缺少旧页面键")
        if source in indexed:
            raise ValueError(f"重复旧页面键：{source}")
        if row["target_disposition"] not in ALLOWED_TARGET_DISPOSITIONS:
            raise ValueError(
                f"非法目标处置：{source}={row['target_disposition'] or '<empty>'}"
            )
        if row["implementation_status"] not in ALLOWED_IMPLEMENTATION_STATUSES:
            raise ValueError(
                f"非法实施状态：{source}={row['implementation_status'] or '<empty>'}"
            )
        if row["target_disposition"] in {"合并", "下线"} and "已确认" not in row[
            "confirmation_status"
        ]:
            raise ValueError(f"合并或下线缺少明确确认：{source}")
        required = ["new_route", "new_component", "automated_tests", "confirmation_status"]
        if row["implementation_status"] != "未开始":
            required.append("target_evidence")
        for field in required:
            if not row[field]:
                label = "目标证据" if field == "target_evidence" else field
                raise ValueError(f"{source} 缺少{label}")
        indexed[source] = row
    return indexed


def validate_target_override_files(
    overrides: dict[str, dict[str, str]],
    root: Path = ROOT,
) -> None:
    for source, override in overrides.items():
        for field in ("new_component", "automated_tests", "target_evidence"):
            for value in override[field].split(" | "):
                candidate = value.strip()
                if not candidate:
                    continue
                resolved = (root / candidate).resolve()
                try:
                    resolved.relative_to(root.resolve())
                except ValueError as error:
                    raise ValueError(f"目标证据越出隔离工作树：{source}={candidate}") from error
                if not resolved.is_file():
                    raise ValueError(f"目标证据文件不存在：{source}={candidate}")


def merge_target_overrides(
    matrix_rows: list[dict[str, object]],
    overrides: dict[str, dict[str, str]],
) -> None:
    matched: set[str] = set()
    for matrix_row in matrix_rows:
        source = str(matrix_row["old_page_source"])
        override = overrides.get(source)
        if not override:
            continue
        matched.add(source)
        for field in (
            "target_disposition",
            "new_route",
            "new_component",
            "confirmation_status",
            "implementation_status",
            "target_evidence",
        ):
            matrix_row[field] = override[field]
        matrix_row["target_automated_tests"] = override["automated_tests"]
        matrix_row["target_notes"] = override["notes"]
    orphan_sources = sorted(set(overrides) - matched)
    if orphan_sources:
        raise ValueError(
            "目标映射旧页面不存在于源矩阵：" + " | ".join(orphan_sources)
        )


def build_function_matrix(
    frontend_api_rows: list[dict[str, object]],
) -> tuple[list[dict[str, object]], dict[str, int]]:
    view_files = sorted((ROOT / "erp-ui" / "src" / "views").rglob("*.vue"))
    view_sources = [rel(path) for path in view_files]
    route_map = extract_route_evidence()
    sql_map = extract_sql_menu_evidence(view_sources)
    tests = {
        rel(path): read_text(path)
        for path in sorted((ROOT / "erp-ui" / "test").glob("*.test.js"))
    }
    api_ids_by_module: dict[str, list[str]] = defaultdict(list)
    for api_row in frontend_api_rows:
        api_ids_by_module[str(api_row["module"])].append(str(api_row["frontend_api_id"]))

    rows: list[dict[str, object]] = []
    counts: dict[str, int] = defaultdict(int)
    for index, path in enumerate(view_files, 1):
        source = rel(path)
        text = read_text(path)
        evidence = sorted(
            route_map.get(source, []) + sql_map.get(source, []),
            key=lambda item: (item.route, item.source),
        )
        domain, surface = domain_for_view(source)
        kind = classify_view(source, evidence)
        permissions = sorted(
            set(permissions_from(text)).union(
                permission for item in evidence for permission in item.permissions
            )
        )
        api_modules = sorted(set(API_IMPORT_RE.findall(text)))
        api_ids = sorted(
            api_id
            for api_module in api_modules
            for module_candidate in (
                api_module.removesuffix(".js"),
                api_module.removesuffix(".js") + "/index",
            )
            for api_id in api_ids_by_module.get(module_candidate, [])
        )
        tests_for_view = existing_test_references(source, tests)
        route_values = sorted({item.route for item in evidence if item.route})
        title_values = sorted({item.title for item in evidence if item.title})
        evidence_values = sorted({item.source for item in evidence})
        actions = sorted({permission.split(":")[-1] for permission in permissions})
        exception_markers = [
            marker
            for marker in ("catch", "401", "403", "conflict", "error", "失败", "异常")
            if marker.lower() in text.lower()
        ]
        data_scope = (
            "存在组织/数据范围线索，待契约核验"
            if re.search(r"deptId|shopId|dataScope|organization|currentShop", text, re.IGNORECASE)
            else "待核验"
        )
        counts[kind] += 1
        counts[f"surface:{surface}"] += 1
        rows.append(
            {
                "capability_id": f"VIEW-{index:04d}",
                "domain": domain,
                "surface": surface,
                "source_kind": kind,
                "old_menu_or_route": " | ".join(route_values) or "未在静态路由/增量菜单 SQL 中定位",
                "old_title": " | ".join(title_values) or "待盘点",
                "old_page_source": source,
                "actions": " | ".join(actions) or "待盘点",
                "permissions": " | ".join(permissions) or "待盘点",
                "data_scope": data_scope,
                "frontend_api_modules": " | ".join(api_modules) or "待盘点",
                "frontend_api_ids": " | ".join(api_ids) or "待盘点",
                "exception_branches": " | ".join(exception_markers) or "待盘点",
                "target_disposition": "待确认（保留/重设计/合并/下线）",
                "new_route": "待设计",
                "new_component": "待设计",
                "legacy_automated_tests": " | ".join(tests_for_view) or "待补齐",
                "target_automated_tests": "待补齐",
                "confirmation_status": "待确认",
                "implementation_status": "未开始",
                "target_evidence": "待补齐",
                "target_notes": "待补齐",
                "route_or_menu_evidence": " | ".join(evidence_values) or "无直接入口证据，必须人工确认",
            }
        )
    return rows, dict(counts)


def scan_js_expression(text: str, start: int) -> tuple[str, int]:
    index = start
    while index < len(text) and text[index].isspace():
        index += 1
    expression_start = index
    quote = ""
    escaped = False
    round_depth = 0
    square_depth = 0
    while index < len(text):
        character = text[index]
        if quote:
            if escaped:
                escaped = False
            elif character == "\\":
                escaped = True
            elif character == quote:
                quote = ""
            index += 1
            continue
        if character in "'\"`":
            quote = character
        elif character == "(":
            round_depth += 1
        elif character == ")" and round_depth:
            round_depth -= 1
        elif character == "[":
            square_depth += 1
        elif character == "]" and square_depth:
            square_depth -= 1
        elif round_depth == 0 and square_depth == 0 and character in ",}":
            break
        index += 1
    return text[expression_start:index].strip(), index


def split_js_concat(expression: str) -> list[str]:
    tokens: list[str] = []
    start = 0
    quote = ""
    escaped = False
    depth = 0
    for index, character in enumerate(expression):
        if quote:
            if escaped:
                escaped = False
            elif character == "\\":
                escaped = True
            elif character == quote:
                quote = ""
            continue
        if character in "'\"`":
            quote = character
        elif character in "([":
            depth += 1
        elif character in ")]" and depth:
            depth -= 1
        elif character == "+" and depth == 0:
            tokens.append(expression[start:index].strip())
            start = index + 1
    tokens.append(expression[start:].strip())
    return [token for token in tokens if token]


def dynamic_name(expression: str) -> str:
    encode_match = re.fullmatch(r"encodeURIComponent\(([^)]+)\)", expression.strip())
    if encode_match:
        expression = encode_match.group(1)
    value = re.sub(r"[^A-Za-z0-9_$.-]+", "_", expression.strip()).strip("_")
    return value or "dynamic"


def expression_to_path(expression: str, constants: dict[str, str]) -> str:
    expression = expression.strip()
    template_match = re.fullmatch(r"`([^`]*)`", expression)
    if template_match:
        def replace_template(match: re.Match[str]) -> str:
            value = match.group(1).strip()
            return constants.get(value, "{" + dynamic_name(value) + "}")

        return re.sub(r"\$\{([^}]+)\}", replace_template, template_match.group(1))
    tokens = split_js_concat(expression)
    result = ""
    for token in tokens:
        token = token.strip()
        literal = re.fullmatch(r"(['\"])(.*?)\1", token)
        if literal:
            result += literal.group(2)
        elif token in constants:
            result += constants[token]
        elif re.fullmatch(r"`[^`]*`", token):
            result += expression_to_path(token, constants)
        elif token:
            result += "{" + dynamic_name(token) + "}"
    return result or expression


def extract_frontend_api() -> list[dict[str, object]]:
    rows: list[dict[str, object]] = []
    index = 0
    for source in sorted((ROOT / "erp-ui" / "src" / "api").rglob("*.js")):
        text = read_text(source)
        constants = {
            match.group(1): match.group(3)
            for match in CONST_STRING_RE.finditer(text)
        }
        function_matches = list(EXPORTED_FUNCTION_RE.finditer(text))
        for url_match in URL_KEY_RE.finditer(text):
            index += 1
            function_name = "unknown"
            for function_match in function_matches:
                if function_match.start() > url_match.start():
                    break
                function_name = function_match.group(1)
            expression, expression_end = scan_js_expression(text, url_match.end())
            after_window = text[expression_end : min(len(text), expression_end + 300)]
            method_match = METHOD_RE.search(after_window)
            if not method_match:
                before_window = text[max(0, url_match.start() - 180) : url_match.start()]
                previous_methods = list(METHOD_RE.finditer(before_window))
                method_match = previous_methods[-1] if previous_methods else None
            rows.append(
                {
                    "frontend_api_id": f"FEAPI-{index:04d}",
                    "module": rel(source).removeprefix("erp-ui/src/api/").removesuffix(".js"),
                    "exported_function": function_name,
                    "http_method": method_match.group(1).upper() if method_match else "UNKNOWN",
                    "url_expression": expression,
                    "normalized_path": expression_to_path(expression, constants),
                    "source": f"{rel(source)}:{line_number(text, url_match.start())}",
                    "contract_status": "待与后端/OpenAPI 核验",
                }
            )
        for action_match in EXPORTED_ACTION_RE.finditer(text):
            if any(row["source"].startswith(rel(source) + ":") and row["normalized_path"] == action_match.group(3) for row in rows):
                continue
            index += 1
            rows.append(
                {
                    "frontend_api_id": f"FEAPI-{index:04d}",
                    "module": rel(source).removeprefix("erp-ui/src/api/").removesuffix(".js"),
                    "exported_function": action_match.group(1),
                    "http_method": "INDIRECT",
                    "url_expression": action_match.group(3),
                    "normalized_path": action_match.group(3),
                    "source": f"{rel(source)}:{line_number(text, action_match.start())}",
                    "contract_status": "由上传/下载组件间接调用，待匹配后端方法",
                }
            )
    return rows


def resolve_indirect_frontend_methods(
    frontend_rows: list[dict[str, object]], backend_rows: list[dict[str, object]]
) -> None:
    methods_by_path: dict[str, set[str]] = defaultdict(set)
    for backend_row in backend_rows:
        methods_by_path[normalize_contract_path(str(backend_row["external_path_candidate"]))].add(
            str(backend_row["http_method"])
        )
    for frontend_row in frontend_rows:
        if frontend_row["http_method"] != "INDIRECT":
            continue
        methods = sorted(
            methods_by_path.get(normalize_contract_path(str(frontend_row["normalized_path"])), set())
        )
        if len(methods) == 1:
            frontend_row["http_method"] = methods[0]
            frontend_row["contract_status"] = "间接 action，已定位唯一后端方法候选"
        elif methods:
            frontend_row["http_method"] = "|".join(methods)
            frontend_row["contract_status"] = "间接 action，对应多个后端方法，需人工核验"


def controller_module(source: Path) -> tuple[str, str]:
    source_text = rel(source)
    mappings = {
        "erp-modules/erp-inventory/": ("inventory", "/inventory"),
        "erp-modules/erp-oa/": ("oa", "/oa"),
        "erp-modules/erp-system/": ("system", "/system"),
        "erp-modules/erp-approval/": ("approval", "/approval"),
        "erp-modules/erp-file/": ("file", "/file"),
        "erp-modules/erp-job/": ("job", "/schedule"),
        "erp-auth/": ("auth", "/auth"),
        "erp-visual/erp-monitor/": ("monitor", "/monitor"),
    }
    for prefix, value in mappings.items():
        if source_text.startswith(prefix):
            return value
    return "unknown", ""


def join_url(*parts: str) -> str:
    filtered = [part.strip("/") for part in parts if part and part != "/"]
    return "/" + "/".join(filtered) if filtered else "/"


def annotation_paths(arguments: str | None) -> list[str]:
    if not arguments:
        return [""]
    path_assignment = re.search(
        r"(?:\bvalue|\bpath)\s*=\s*(.*?)(?=,\s*\w+\s*=|$)",
        arguments,
        re.DOTALL,
    )
    candidate = path_assignment.group(1) if path_assignment else arguments
    if not path_assignment and re.search(r"\b(?:method|produces|consumes|headers|params)\s*=", candidate):
        candidate = re.split(
            r",\s*(?:method|produces|consumes|headers|params)\s*=",
            candidate,
            maxsplit=1,
        )[0]
    values = re.findall(r"['\"]([^'\"]*)['\"]", candidate)
    return values or [""]


def extract_backend_contracts() -> list[dict[str, object]]:
    controller_roots = [
        ROOT / "erp-api",
        ROOT / "erp-auth",
        ROOT / "erp-modules",
        ROOT / "erp-visual",
    ]
    sources = sorted(
        path
        for root in controller_roots
        for path in root.rglob("*Controller.java")
        if "/target/" not in path.as_posix()
    )
    rows: list[dict[str, object]] = []
    index = 0
    for source in sources:
        text = read_text(source)
        class_match = re.search(r"\bclass\s+\w+", text)
        if not class_match:
            continue
        class_prefix = text[: class_match.start()]
        class_mappings = list(MAPPING_RE.finditer(class_prefix))
        base_paths = annotation_paths(class_mappings[-1].group(2)) if class_mappings else [""]
        module, gateway_prefix = controller_module(source)
        method_matches = list(PUBLIC_METHOD_RE.finditer(text))
        previous_end = class_match.end()
        for method_match in method_matches:
            annotation_block = text[previous_end : method_match.start()]
            previous_end = method_match.end()
            mappings = list(MAPPING_RE.finditer(annotation_block))
            if not mappings:
                continue
            permissions = sorted(set(permissions_from(annotation_block)))
            idempotent = "@IdempotentSubmit" in annotation_block
            response_type = " ".join(method_match.group(1).split())
            java_method = method_match.group(2)
            for mapping in mappings:
                kind = mapping.group(1)
                methods = [kind.upper()]
                if kind == "Request":
                    request_methods = re.findall(r"RequestMethod\.([A-Z]+)", mapping.group(2) or "")
                    methods = request_methods or ["ANY"]
                for base_path in base_paths:
                    for method_path in annotation_paths(mapping.group(2)):
                        for http_method in methods:
                            index += 1
                            internal_path = join_url(base_path, method_path)
                            rows.append(
                                {
                                    "backend_api_id": f"BEAPI-{index:04d}",
                                    "module": module,
                                    "controller": source.stem,
                                    "java_method": java_method,
                                    "http_method": http_method,
                                    "controller_path": internal_path,
                                    "external_path_candidate": join_url(gateway_prefix, internal_path),
                                    "permissions": " | ".join(permissions) or "未声明/公共接口",
                                    "idempotent_annotation": "yes" if idempotent else "no",
                                    "response_type": response_type,
                                    "data_scope": "待逐接口核验",
                                    "source": f"{rel(source)}:{line_number(text, method_match.start())}",
                                    "openapi_status": "待生成版本化 OpenAPI 基线",
                                }
                            )
    gateway_root = ROOT / "erp-gateway" / "src" / "main" / "java"
    gateway_route_re = re.compile(
        r"RequestPredicates\.(GET|POST|PUT|DELETE|PATCH)\(\s*['\"]([^'\"]+)['\"]\s*\)"
    )
    for source in sorted(gateway_root.rglob("*.java")):
        text = read_text(source)
        for match in gateway_route_re.finditer(text):
            index += 1
            rows.append(
                {
                    "backend_api_id": f"BEAPI-{index:04d}",
                    "module": "gateway",
                    "controller": source.stem,
                    "java_method": "routerFunction",
                    "http_method": match.group(1),
                    "controller_path": match.group(2),
                    "external_path_candidate": match.group(2),
                    "permissions": "公共入口/由网关过滤器控制",
                    "idempotent_annotation": "not-applicable",
                    "response_type": "ServerResponse",
                    "data_scope": "不适用",
                    "source": f"{rel(source)}:{line_number(text, match.start())}",
                    "openapi_status": "网关函数式路由，需纳入统一契约说明",
                }
            )
    return rows


def normalize_contract_path(path: str) -> str:
    value = re.sub(r"\{[^}]+\}", "{}", path)
    value = re.sub(r"/+", "/", value)
    return value.rstrip("/") or "/"


def build_contract_cross_reference(
    frontend_rows: list[dict[str, object]], backend_rows: list[dict[str, object]]
) -> list[dict[str, object]]:
    backend_index: dict[tuple[str, str], list[str]] = defaultdict(list)
    for row in backend_rows:
        key = (
            str(row["http_method"]),
            normalize_contract_path(str(row["external_path_candidate"])),
        )
        backend_index[key].append(str(row["backend_api_id"]))

    result: list[dict[str, object]] = []
    for row in frontend_rows:
        key = (
            str(row["http_method"]),
            normalize_contract_path(str(row["normalized_path"])),
        )
        matches = sorted(backend_index.get(key, []))
        result.append(
            {
                "frontend_api_id": row["frontend_api_id"],
                "http_method": row["http_method"],
                "frontend_path": row["normalized_path"],
                "backend_api_ids": " | ".join(matches),
                "match_status": "exact-candidate" if matches else "unmatched-needs-review",
                "frontend_source": row["source"],
            }
        )
    return result


def build_permission_inventory() -> list[dict[str, object]]:
    groups = {
        "frontend": sorted(
            list((ROOT / "erp-ui" / "src").rglob("*.vue"))
            + list((ROOT / "erp-ui" / "src").rglob("*.js"))
            + list((ROOT / "erp-ui" / "src").rglob("*.ts"))
        ),
        "backend": sorted(
            path
            for base in (ROOT / "erp-auth", ROOT / "erp-modules", ROOT / "erp-visual")
            for path in base.rglob("*.java")
            if "/src/main/java/" in path.as_posix()
        ),
        "sql": sorted((ROOT / "sql").glob("*.sql")),
    }
    inventory: dict[str, dict[str, object]] = defaultdict(
        lambda: {"occurrences": 0, "frontend": set(), "backend": set(), "sql": set()}
    )
    for group, sources in groups.items():
        for source in sources:
            text = read_text(source)
            for match in PERMISSION_RE.finditer(text):
                permission = match.group(0)
                if permission.split(":", 1)[0] not in KNOWN_PERMISSION_PREFIXES:
                    continue
                inventory[permission]["occurrences"] = int(inventory[permission]["occurrences"]) + 1
                cast_sources = inventory[permission][group]
                assert isinstance(cast_sources, set)
                cast_sources.add(f"{rel(source)}:{line_number(text, match.start())}")

    rows: list[dict[str, object]] = []
    for permission in sorted(inventory):
        item = inventory[permission]
        rows.append(
            {
                "permission": permission,
                "occurrences": item["occurrences"],
                "frontend_sources": " | ".join(sorted(item["frontend"])),
                "backend_sources": " | ".join(sorted(item["backend"])),
                "sql_sources": " | ".join(sorted(item["sql"])),
                "alignment_status": "待核验",
            }
        )
    return rows


def build_source_manifest() -> list[dict[str, object]]:
    selected: set[Path] = set()
    patterns = [
        (ROOT / "erp-ui" / "src" / "views", ("*.vue", "*.js", "*.ts")),
        (ROOT / "erp-ui" / "src" / "api", ("*.js", "*.ts")),
        (ROOT / "erp-ui" / "src" / "router", ("*.js", "*.ts")),
        (ROOT / "erp-modules", ("*Controller.java",)),
        (ROOT / "erp-auth", ("*Controller.java",)),
        (ROOT / "erp-visual", ("*Controller.java",)),
        (ROOT / "erp-gateway" / "src" / "main", ("*.java", "*.yml", "*.yaml")),
        (ROOT / "erp-ui-next" / "src" / "api", ("*.ts",)),
        (ROOT / "erp-ui-next" / "src" / "app" / "providers", ("*.ts",)),
        (ROOT / "erp-ui-next" / "src" / "app" / "router", ("*.ts",)),
        (ROOT / "erp-ui-next" / "src" / "app" / "shell", ("*.vue", "*.ts")),
        (ROOT / "erp-ui-next" / "src" / "design-system", ("*.vue", "*.ts")),
        (ROOT / "erp-ui-next" / "src" / "domains", ("*.vue", "*.ts")),
        (ROOT / "erp-ui-next" / "src" / "pages", ("*.vue", "*.ts")),
        (ROOT / "erp-ui-next" / "src" / "platform", ("*.vue", "*.ts")),
        (ROOT / "erp-ui-next" / "src" / "test-support", ("*.ts",)),
        (ROOT / "sql", ("*.sql",)),
    ]
    for base, globs in patterns:
        for pattern in globs:
            selected.update(base.rglob(pattern))
    selected.add(Path(__file__).resolve())
    if TARGET_OVERRIDES_PATH.is_file():
        selected.add(TARGET_OVERRIDES_PATH)
    rows: list[dict[str, object]] = []
    for path in sorted(selected):
        rows.append(
            {
                "source": rel(path),
                "size_bytes": path.stat().st_size,
                "sha256": sha256(path),
            }
        )
    return rows


def write_summary(
    function_rows: list[dict[str, object]],
    function_counts: dict[str, int],
    frontend_rows: list[dict[str, object]],
    backend_rows: list[dict[str, object]],
    cross_rows: list[dict[str, object]],
    permission_rows: list[dict[str, object]],
    manifest_rows: list[dict[str, object]],
) -> None:
    exact_matches = sum(row["match_status"] == "exact-candidate" for row in cross_rows)
    api_linked_views = sum(
        row["frontend_api_ids"] != "待盘点" for row in function_rows
    )
    legacy_test_linked_views = sum(
        row["legacy_automated_tests"] != "待补齐" for row in function_rows
    )
    target_test_linked_views = sum(
        row["target_automated_tests"] != "待补齐" for row in function_rows
    )
    decided_targets = sum(
        row["target_disposition"] in ALLOWED_TARGET_DISPOSITIONS for row in function_rows
    )
    partially_implemented = sum(
        row["implementation_status"] == "部分实现" for row in function_rows
    )
    implemented = sum(row["implementation_status"] == "已实现" for row in function_rows)
    missing_inputs = [
        "docs/erp-pc-mobile-consistency-audit-20260729/01-ERP系统功能地图.md",
    ]
    content = f"""# ERP-NEW_2 阶段 1 盘点说明

> 生成方式：`scripts/erp-new-2/build_phase1_inventory.py`
> 状态：滚动机器盘点；目标映射持续维护，未决项待人工核验与项目负责人确认

## 覆盖结果

- 旧前端 Vue 页面/组件：{len(function_rows)}
- 已定位路由或菜单入口的页面：{function_counts.get('route-page', 0)}
- 组件级条目：{function_counts.get('component', 0)}
- 未定位直接入口、必须人工确认的页面候选：{function_counts.get('unmapped-page-candidate', 0)}
- 桌面条目：{function_counts.get('surface:desktop', 0)}
- 移动网页条目：{function_counts.get('surface:mobile-web', 0)}
- 已关联前端 API 的页面/组件：{api_linked_views}
- 已关联旧前端自动化测试的页面/组件：{legacy_test_linked_views}
- 暂无旧前端自动化测试证据的页面/组件：{len(function_rows) - legacy_test_linked_views}
- 已关联新前端自动化测试的目标条目：{target_test_linked_views}
- 已有明确目标处置：{decided_targets}
- 部分实现：{partially_implemented}
- 已实现：{implemented}
- 目标处置仍待确认：{len(function_rows) - decided_targets}
- 前端 API 调用：{len(frontend_rows)}
- 后端 Controller 端点：{len(backend_rows)}
- 前后端方法与规范化路径精确候选匹配：{exact_matches}
- 待人工核验的前端 API：{len(cross_rows) - exact_matches}
- 权限标识：{len(permission_rows)}
- 纳入哈希清单的证据源文件：{len(manifest_rows)}

## 产物

1. `functional-migration-matrix.csv`：逐个 Vue 页面/组件的迁移矩阵初稿。
2. `frontend-api-baseline.csv`：前端 API 调用基线。
3. `backend-controller-contract-baseline.csv`：后端 Controller 契约基线。
4. `api-contract-cross-reference.csv`：前后端方法与规范化路径候选匹配。
5. `permission-inventory.csv`：前端、后端和 SQL 权限标识来源。
6. `phase1-source-manifest.csv`：盘点输入文件大小与 SHA-256。
7. `contract-review.md`：动态分派、响应结构、幂等、权限、数据范围和类型问题的人工审阅结论。
8. `unmapped-entry-review.md`：未定位直接路由或菜单入口的页面候选及其证据分级。
9. `stage1-review.md`：阶段 1 交付范围、风险和待用户确认事项。
10. `openapi-generation-readiness.md`：版本化 OpenAPI 的现状、隔离生成条件和冻结证据门槛。
11. `target-mapping-overrides.csv`：人工维护、机器校验的新前端目标映射覆盖层。

## 重要限制

- 正式方案引用但当前隔离基线缺少：`{missing_inputs[0]}`。本次不能用受保护原项目补读，已改用当前工作树源码、路由和 SQL 建立证据。
- 动态菜单依赖数据库运行态；当前未连接任何数据库，因此 SQL 只能证明迁移声明，不能证明实际菜单数据。所有无直接入口证据的页面均保留在矩阵中，禁止按“没看见”下线。
- `external_path_candidate` 根据模块网关前缀推导，只是核验候选，不等同于已发布 OpenAPI。
- 只有具备新前端源码和测试证据的条目才允许通过覆盖层写入明确目标处置；未覆盖条目继续保持“待确认”。
- 覆盖层中的“部分实现”不等于领域迁移完成；Mock-only、缺失动作和运行时未接线仍按条目备注阻断完成声明。
- 本盘点不授权下线、合并或删除旧前端；生成器会拒绝没有明确确认的“合并 / 下线”覆盖项。
- 数据范围、异常分支、响应模型和 API 不一致仍需逐项人工核验。

## 下一步检查点

先人工核验 `unmapped-page-candidate`、API 未匹配项和权限单边引用，再提出保留、重设计、合并或下线建议。任何下线、合并或业务规则争议必须由项目负责人逐项确认。
"""
    (OUTPUT_DIR / "README.md").write_text(content, encoding="utf-8")


def main() -> None:
    fail_if_outside_isolated_root()
    target_overrides = index_target_overrides(read_target_override_rows())
    validate_target_override_files(target_overrides)
    frontend_rows = extract_frontend_api()
    function_rows, function_counts = build_function_matrix(frontend_rows)
    merge_target_overrides(function_rows, target_overrides)
    backend_rows = extract_backend_contracts()
    resolve_indirect_frontend_methods(frontend_rows, backend_rows)
    cross_rows = build_contract_cross_reference(frontend_rows, backend_rows)
    permission_rows = build_permission_inventory()
    manifest_rows = build_source_manifest()

    write_csv(
        OUTPUT_DIR / "functional-migration-matrix.csv",
        [
            "capability_id",
            "domain",
            "surface",
            "source_kind",
            "old_menu_or_route",
            "old_title",
            "old_page_source",
            "actions",
            "permissions",
            "data_scope",
            "frontend_api_modules",
            "frontend_api_ids",
            "exception_branches",
            "target_disposition",
            "new_route",
            "new_component",
            "legacy_automated_tests",
            "target_automated_tests",
            "confirmation_status",
            "implementation_status",
            "target_evidence",
            "target_notes",
            "route_or_menu_evidence",
        ],
        function_rows,
    )
    write_csv(
        OUTPUT_DIR / "frontend-api-baseline.csv",
        [
            "frontend_api_id",
            "module",
            "exported_function",
            "http_method",
            "url_expression",
            "normalized_path",
            "source",
            "contract_status",
        ],
        frontend_rows,
    )
    write_csv(
        OUTPUT_DIR / "backend-controller-contract-baseline.csv",
        [
            "backend_api_id",
            "module",
            "controller",
            "java_method",
            "http_method",
            "controller_path",
            "external_path_candidate",
            "permissions",
            "idempotent_annotation",
            "response_type",
            "data_scope",
            "source",
            "openapi_status",
        ],
        backend_rows,
    )
    write_csv(
        OUTPUT_DIR / "api-contract-cross-reference.csv",
        [
            "frontend_api_id",
            "http_method",
            "frontend_path",
            "backend_api_ids",
            "match_status",
            "frontend_source",
        ],
        cross_rows,
    )
    write_csv(
        OUTPUT_DIR / "permission-inventory.csv",
        [
            "permission",
            "occurrences",
            "frontend_sources",
            "backend_sources",
            "sql_sources",
            "alignment_status",
        ],
        permission_rows,
    )
    write_csv(
        OUTPUT_DIR / "phase1-source-manifest.csv",
        ["source", "size_bytes", "sha256"],
        manifest_rows,
    )
    write_summary(
        function_rows,
        function_counts,
        frontend_rows,
        backend_rows,
        cross_rows,
        permission_rows,
        manifest_rows,
    )
    print(
        "generated "
        f"views={len(function_rows)} frontend_api={len(frontend_rows)} "
        f"backend_api={len(backend_rows)} permissions={len(permission_rows)} "
        f"sources={len(manifest_rows)}"
    )


if __name__ == "__main__":
    main()

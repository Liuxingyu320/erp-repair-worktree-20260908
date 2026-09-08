#!/usr/bin/env python3
"""Detect conflicting static ``sys_menu`` ID ownership in migration SQL.

Ownership is checked field by field for ``path``, ``component``,
``route_name``, ``menu_type``, and ``perms``. Repeating the same definition
(for example, under both ``sql/`` and ``docker/mysql/db/``) is harmless;
assigning a different static value to the same numeric menu ID is a conflict.

Strict mode rejects every conflict. Release mode requires an explicit JSON
allowlist and accepts a historical conflict only when its complete conflicting
value set is unchanged. This means adding a third owner to an allowlisted ID is
still a release failure. The allowlist format is::

    {
      "version": 1,
      "conflicts": {
        "4600": {
          "component": [null, "oa/signTask/index"],
          "perms": ["", "oa:signTask:list"]
        }
      }
    }

The scanner intentionally considers only statically knowable numeric IDs and
string/integer/NULL ownership values in INSERT ... VALUES, INSERT ... SET,
INSERT ... SELECT, and UPDATE statements. Dynamic IDs and computed ownership
expressions cannot establish repository-level static ownership and are ignored.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, Mapping, Sequence


ROOT = Path(__file__).resolve().parents[2]
OWNERSHIP_FIELDS = ("parent_id", "path", "component", "route_name", "menu_type", "perms")
StaticValue = str | int | None
ConflictSpec = dict[int, dict[str, tuple[StaticValue, ...]]]


@dataclass(frozen=True)
class Definition:
    """One statically knowable ownership-field assignment."""

    menu_id: int
    field: str
    value: StaticValue
    source: str
    line: int
    statement: str


@dataclass(frozen=True)
class GateResult:
    """Classification of conflicts for a strict or allowlisted release gate."""

    allowed_ids: tuple[int, ...]
    failing_ids: tuple[int, ...]
    stale_allowlist_ids: tuple[int, ...]

    @property
    def passed(self) -> bool:
        return not self.failing_ids and not self.stale_allowlist_ids


_INSERT_PREFIX = re.compile(
    r"^INSERT\s+(?:(?:LOW_PRIORITY|DELAYED|HIGH_PRIORITY|IGNORE)\s+)*"
    r"INTO\s+(?:(?:`?[A-Za-z_][A-Za-z0-9_]*`?)\s*\.\s*)?`?sys_menu`?(?![A-Za-z0-9_])",
    re.IGNORECASE | re.DOTALL,
)
_UPDATE_PREFIX = re.compile(
    r"^UPDATE\s+(?:(?:`?[A-Za-z_][A-Za-z0-9_]*`?)\s*\.\s*)?`?sys_menu`?(?![A-Za-z0-9_])"
    r"(?:\s+(?:AS\s+)?(?!SET\b)`?[A-Za-z_][A-Za-z0-9_]*`?)?\s+SET\b",
    re.IGNORECASE | re.DOTALL,
)
_ASSIGNMENT = re.compile(
    r"^(?:(?:`?[A-Za-z_][A-Za-z0-9_]*`?)\s*\.\s*)?"
    r"`?(menu_id|parent_id|path|component|route_name|menu_type|perms)`?\s*=\s*(.+)$",
    re.IGNORECASE | re.DOTALL,
)
_INTEGER = re.compile(r"[0-9]+")
_QUALIFIED_MENU_ID = (
    r"(?:(?:`?[A-Za-z_][A-Za-z0-9_]*`?)\s*\.\s*)?`?menu_id`?"
)


def _mask_comments(text: str) -> str:
    """Replace SQL comments with spaces while retaining offsets and newlines."""

    chars = list(text)
    quote: str | None = None
    index = 0
    while index < len(chars):
        char = chars[index]
        if quote is not None:
            if char == "\\" and index + 1 < len(chars):
                index += 2
                continue
            if char == quote:
                if index + 1 < len(chars) and chars[index + 1] == quote:
                    index += 2
                    continue
                quote = None
            index += 1
            continue

        if char in ("'", '"', "`"):
            quote = char
            index += 1
            continue
        if char == "/" and index + 1 < len(chars) and chars[index + 1] == "*":
            chars[index] = chars[index + 1] = " "
            index += 2
            while index < len(chars):
                if chars[index] == "*" and index + 1 < len(chars) and chars[index + 1] == "/":
                    chars[index] = chars[index + 1] = " "
                    index += 2
                    break
                if chars[index] != "\n":
                    chars[index] = " "
                index += 1
            continue
        is_dash_comment = (
            char == "-"
            and index + 1 < len(chars)
            and chars[index + 1] == "-"
            and (index + 2 == len(chars) or chars[index + 2].isspace())
        )
        if char == "#" or is_dash_comment:
            while index < len(chars) and chars[index] != "\n":
                chars[index] = " "
                index += 1
            continue
        index += 1
    return "".join(chars)


def _iter_statements(text: str) -> Iterable[tuple[str, int]]:
    """Yield semicolon-delimited SQL statements and their source offsets."""

    start = 0
    quote: str | None = None
    index = 0
    while index < len(text):
        char = text[index]
        if quote is not None:
            if char == "\\" and index + 1 < len(text):
                index += 2
                continue
            if char == quote:
                if index + 1 < len(text) and text[index + 1] == quote:
                    index += 2
                    continue
                quote = None
            index += 1
            continue
        if char in ("'", '"', "`"):
            quote = char
        elif char == ";":
            raw = text[start:index]
            leading = len(raw) - len(raw.lstrip())
            statement = raw[leading:].rstrip()
            if statement:
                yield statement, start + leading
            start = index + 1
        index += 1

    raw = text[start:]
    leading = len(raw) - len(raw.lstrip())
    statement = raw[leading:].rstrip()
    if statement:
        yield statement, start + leading


def _extract_parenthesized(text: str, opening: int) -> tuple[str, int] | None:
    """Return content and exclusive end for the group starting at ``opening``."""

    if opening >= len(text) or text[opening] != "(":
        return None
    depth = 0
    quote: str | None = None
    index = opening
    while index < len(text):
        char = text[index]
        if quote is not None:
            if char == "\\" and index + 1 < len(text):
                index += 2
                continue
            if char == quote:
                if index + 1 < len(text) and text[index + 1] == quote:
                    index += 2
                    continue
                quote = None
            index += 1
            continue
        if char in ("'", '"', "`"):
            quote = char
        elif char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
            if depth == 0:
                return text[opening + 1:index], index + 1
        index += 1
    return None


def _split_top_level(text: str, delimiter: str = ",") -> list[str]:
    parts: list[str] = []
    start = 0
    depth = 0
    quote: str | None = None
    index = 0
    while index < len(text):
        char = text[index]
        if quote is not None:
            if char == "\\" and index + 1 < len(text):
                index += 2
                continue
            if char == quote:
                if index + 1 < len(text) and text[index + 1] == quote:
                    index += 2
                    continue
                quote = None
            index += 1
            continue
        if char in ("'", '"', "`"):
            quote = char
        elif char == "(":
            depth += 1
        elif char == ")":
            depth = max(0, depth - 1)
        elif char == delimiter and depth == 0:
            parts.append(text[start:index].strip())
            start = index + 1
        index += 1
    parts.append(text[start:].strip())
    return parts


def _find_top_level_keyword(text: str, keyword: str, start: int = 0) -> int:
    depth = 0
    quote: str | None = None
    index = start
    upper_keyword = keyword.upper()
    while index < len(text):
        char = text[index]
        if quote is not None:
            if char == "\\" and index + 1 < len(text):
                index += 2
                continue
            if char == quote:
                if index + 1 < len(text) and text[index + 1] == quote:
                    index += 2
                    continue
                quote = None
            index += 1
            continue
        if char in ("'", '"', "`"):
            quote = char
        elif char == "(":
            depth += 1
        elif char == ")":
            depth = max(0, depth - 1)
        elif depth == 0 and text[index:index + len(keyword)].upper() == upper_keyword:
            before = text[index - 1] if index else " "
            after_index = index + len(keyword)
            after = text[after_index] if after_index < len(text) else " "
            if not (before.isalnum() or before == "_") and not (after.isalnum() or after == "_"):
                return index
        index += 1
    return -1


def _unescape_sql_string(token: str) -> str:
    quote = token[0]
    content = token[1:-1]
    result: list[str] = []
    index = 0
    escapes = {"0": "\0", "b": "\b", "n": "\n", "r": "\r", "t": "\t", "Z": "\x1a"}
    while index < len(content):
        char = content[index]
        if char == quote and index + 1 < len(content) and content[index + 1] == quote:
            result.append(quote)
            index += 2
            continue
        if char == "\\" and index + 1 < len(content):
            escaped = content[index + 1]
            result.append(escapes.get(escaped, escaped))
            index += 2
            continue
        result.append(char)
        index += 1
    return "".join(result)


def _static_value(token: str) -> tuple[bool, StaticValue]:
    token = token.strip()
    if token.upper() == "NULL":
        return True, None
    if _INTEGER.fullmatch(token):
        return True, int(token)
    if len(token) >= 2 and token[0] in ("'", '"') and token[-1] == token[0]:
        return True, _unescape_sql_string(token)
    if len(token) >= 3 and token[0] in ("N", "n") and token[1] in ("'", '"') and token[-1] == token[1]:
        return True, _unescape_sql_string(token[1:])
    return False, None


def _static_menu_id(token: str) -> int | None:
    token = token.strip()
    if not _INTEGER.fullmatch(token):
        return None
    return int(token)


def _parse_assignments(text: str) -> dict[str, str]:
    assignments: dict[str, str] = {}
    for part in _split_top_level(text):
        match = _ASSIGNMENT.match(part)
        if match:
            assignments[match.group(1).lower()] = match.group(2).strip()
    return assignments


def _line_number(full_text: str, offset: int) -> int:
    return full_text.count("\n", 0, offset) + 1


def _definitions_from_insert(
    statement: str, statement_offset: int, full_text: str, source: str
) -> list[Definition]:
    match = _INSERT_PREFIX.match(statement)
    if not match:
        return []
    cursor = match.end()
    while cursor < len(statement) and statement[cursor].isspace():
        cursor += 1

    # MySQL also permits INSERT INTO table SET column=value.
    if statement[cursor:cursor + 3].upper() == "SET" and (
        cursor + 3 == len(statement) or not statement[cursor + 3].isalnum()
    ):
        assignments = _parse_assignments(statement[cursor + 3:])
        menu_id = _static_menu_id(assignments.get("menu_id", ""))
        if menu_id is None:
            return []
        line = _line_number(full_text, statement_offset)
        definitions: list[Definition] = []
        for field in OWNERSHIP_FIELDS:
            known, value = _static_value(assignments.get(field, ""))
            if known:
                definitions.append(Definition(menu_id, field, value, source, line, "INSERT"))
        return definitions

    columns_group = _extract_parenthesized(statement, cursor)
    if columns_group is None:
        return []
    columns_text, after_columns = columns_group
    columns = [column.strip().strip("`").lower() for column in _split_top_level(columns_text)]
    try:
        menu_id_index = columns.index("menu_id")
    except ValueError:
        return []
    field_indexes = {field: columns.index(field) for field in OWNERSHIP_FIELDS if field in columns}

    definitions = []

    def append_static_row(values: list[str], row_offset: int, kind: str) -> None:
        if len(values) != len(columns):
            return
        menu_id = _static_menu_id(values[menu_id_index])
        if menu_id is None:
            return
        line = _line_number(full_text, statement_offset + row_offset)
        for field, field_index in field_indexes.items():
            known, value = _static_value(values[field_index])
            if known:
                definitions.append(Definition(menu_id, field, value, source, line, kind))

    values_match = re.match(r"\s*VALUES\b", statement[after_columns:], re.IGNORECASE)
    if values_match:
        cursor = after_columns + values_match.end()
        while cursor < len(statement):
            while cursor < len(statement) and (statement[cursor].isspace() or statement[cursor] == ","):
                cursor += 1
            if cursor >= len(statement) or statement[cursor] != "(":
                break
            row_offset = cursor
            row_group = _extract_parenthesized(statement, cursor)
            if row_group is None:
                break
            row_text, cursor = row_group
            append_static_row(_split_top_level(row_text), row_offset, "INSERT")
        return definitions

    select_match = re.match(r"\s*SELECT\b", statement[after_columns:], re.IGNORECASE)
    if not select_match:
        return []
    select_keyword = after_columns + select_match.start() + len(select_match.group(0))
    while select_keyword <= len(statement):
        union_index = _find_top_level_keyword(statement, "UNION", select_keyword)
        segment_end = union_index if union_index >= 0 else len(statement)
        projection_end = segment_end
        for keyword in ("FROM", "WHERE", "GROUP", "HAVING", "ORDER", "LIMIT", "ON"):
            keyword_index = _find_top_level_keyword(statement, keyword, select_keyword)
            if 0 <= keyword_index < projection_end:
                projection_end = keyword_index
        projection = statement[select_keyword:projection_end].strip()
        if projection.upper().startswith("DISTINCT "):
            projection = projection[len("DISTINCT "):].lstrip()
        append_static_row(_split_top_level(projection), select_keyword, "INSERT_SELECT")
        if union_index < 0:
            break
        cursor = union_index + len("UNION")
        match = re.match(r"\s*(?:ALL|DISTINCT)?\s*SELECT\b", statement[cursor:], re.IGNORECASE)
        if not match:
            break
        select_keyword = cursor + match.end()
    return definitions


def _mask_quoted_content(text: str) -> str:
    chars = list(text)
    quote: str | None = None
    index = 0
    while index < len(chars):
        char = chars[index]
        if quote is None:
            # Backticks quote identifiers, so retain them for `menu_id` matching.
            if char in ("'", '"'):
                quote = char
                chars[index] = " "
            index += 1
            continue
        if char == "\\" and index + 1 < len(chars):
            chars[index] = chars[index + 1] = " "
            index += 2
            continue
        if char == quote:
            chars[index] = " "
            if index + 1 < len(chars) and chars[index + 1] == quote:
                chars[index + 1] = " "
                index += 2
                continue
            quote = None
            index += 1
            continue
        if char != "\n":
            chars[index] = " "
        index += 1
    return "".join(chars)


def _static_where_menu_ids(where: str) -> set[int]:
    masked = _mask_quoted_content(where)
    ids: set[int] = set()
    for match in re.finditer(_QUALIFIED_MENU_ID + r"\s*=\s*([0-9]+)", masked, re.IGNORECASE):
        ids.add(int(match.group(1)))
    for match in re.finditer(
        _QUALIFIED_MENU_ID + r"\s+IN\s*\(\s*([0-9]+(?:\s*,\s*[0-9]+)*)\s*\)",
        masked,
        re.IGNORECASE,
    ):
        ids.update(int(value.strip()) for value in match.group(1).split(","))
    for match in re.finditer(
        _QUALIFIED_MENU_ID + r"\s+BETWEEN\s+([0-9]+)\s+AND\s+([0-9]+)",
        masked,
        re.IGNORECASE,
    ):
        first, last = int(match.group(1)), int(match.group(2))
        if first <= last and last - first <= 10_000:
            ids.update(range(first, last + 1))
    return ids


def _definitions_from_update(
    statement: str, statement_offset: int, full_text: str, source: str
) -> list[Definition]:
    match = _UPDATE_PREFIX.match(statement)
    if not match:
        return []
    where_index = _find_top_level_keyword(statement, "WHERE", match.end())
    if where_index < 0:
        return []
    assignments = _parse_assignments(statement[match.end():where_index])
    static_assignments: dict[str, StaticValue] = {}
    for field in OWNERSHIP_FIELDS:
        known, value = _static_value(assignments.get(field, ""))
        if known:
            static_assignments[field] = value
    if not static_assignments:
        return []
    menu_ids = _static_where_menu_ids(statement[where_index + len("WHERE"):])
    line = _line_number(full_text, statement_offset)
    return [
        Definition(menu_id, field, value, source, line, "UPDATE")
        for menu_id in sorted(menu_ids)
        for field, value in static_assignments.items()
    ]


def scan_sql_text(text: str, source: str = "<memory>") -> list[Definition]:
    """Extract static menu ownership definitions from one SQL document."""

    masked = _mask_comments(text)
    definitions: list[Definition] = []
    for statement, offset in _iter_statements(masked):
        definitions.extend(_definitions_from_insert(statement, offset, masked, source))
        definitions.extend(_definitions_from_update(statement, offset, masked, source))
    return definitions


def collect_sql_files(paths: Sequence[Path]) -> list[Path]:
    """Return a stable, de-duplicated list of SQL files below ``paths``."""

    files: dict[str, Path] = {}
    for raw_path in paths:
        path = raw_path.resolve()
        if not path.exists():
            raise FileNotFoundError(f"scan path does not exist: {raw_path}")
        candidates = [path] if path.is_file() else path.rglob("*.sql")
        for candidate in candidates:
            if candidate.is_file() and candidate.suffix.lower() == ".sql":
                resolved = candidate.resolve()
                files[resolved.as_posix()] = resolved
    return [files[key] for key in sorted(files)]


def scan_paths(paths: Sequence[Path]) -> tuple[list[Path], list[Definition]]:
    files = collect_sql_files(paths)
    definitions: list[Definition] = []
    for path in files:
        definitions.extend(scan_sql_text(path.read_text(encoding="utf-8-sig"), path.as_posix()))
    return files, definitions


def build_conflicts(
    definitions: Iterable[Definition],
) -> dict[int, dict[str, dict[StaticValue, tuple[Definition, ...]]]]:
    index: dict[int, dict[str, dict[StaticValue, list[Definition]]]] = {}
    for definition in definitions:
        field_values = index.setdefault(definition.menu_id, {}).setdefault(definition.field, {})
        field_values.setdefault(definition.value, []).append(definition)

    conflicts: dict[int, dict[str, dict[StaticValue, tuple[Definition, ...]]]] = {}
    for menu_id in sorted(index):
        conflicting_fields = {
            field: {
                value: tuple(sorted(items, key=lambda item: (item.source, item.line, item.statement)))
                for value, items in values.items()
            }
            for field, values in sorted(index[menu_id].items())
            if len(values) > 1
        }
        if conflicting_fields:
            conflicts[menu_id] = conflicting_fields
    return conflicts


def _value_sort_key(value: StaticValue) -> tuple[int, str]:
    if value is None:
        return (0, "")
    if isinstance(value, int):
        return (1, f"{value:020d}")
    return (2, value)


def conflict_spec(
    conflicts: Mapping[int, Mapping[str, Mapping[StaticValue, Sequence[Definition]]]],
) -> ConflictSpec:
    return {
        menu_id: {
            field: tuple(sorted(values, key=_value_sort_key))
            for field, values in sorted(fields.items())
        }
        for menu_id, fields in sorted(conflicts.items())
    }


def allowlist_document(spec: Mapping[int, Mapping[str, Sequence[StaticValue]]]) -> dict[str, object]:
    """Create the canonical versioned JSON document for a conflict specification."""

    return {
        "version": 1,
        "conflicts": {
            str(menu_id): {
                field: list(values)
                for field, values in sorted(fields.items())
            }
            for menu_id, fields in sorted(spec.items())
        },
    }


def load_allowlist(path: Path) -> ConflictSpec:
    try:
        document = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ValueError(f"cannot read allowlist {path}: {exc}") from exc
    if not isinstance(document, dict) or document.get("version") != 1:
        raise ValueError("allowlist must be an object with version 1")
    raw_conflicts = document.get("conflicts")
    if not isinstance(raw_conflicts, dict):
        raise ValueError("allowlist conflicts must be an object")

    result: ConflictSpec = {}
    for raw_menu_id, raw_fields in raw_conflicts.items():
        if not isinstance(raw_menu_id, str) or not raw_menu_id.isdigit():
            raise ValueError(f"allowlist menu ID must be a decimal string: {raw_menu_id!r}")
        menu_id = int(raw_menu_id)
        if str(menu_id) != raw_menu_id:
            raise ValueError(f"allowlist menu ID must be canonical: {raw_menu_id!r}")
        if not isinstance(raw_fields, dict) or not raw_fields:
            raise ValueError(f"allowlist entry {menu_id} must contain conflicting fields")
        fields: dict[str, tuple[StaticValue, ...]] = {}
        for field, raw_values in raw_fields.items():
            if field not in OWNERSHIP_FIELDS:
                raise ValueError(f"allowlist entry {menu_id} has unsupported field {field!r}")
            if not isinstance(raw_values, list) or len(raw_values) < 2:
                raise ValueError(f"allowlist entry {menu_id}.{field} must contain at least two values")
            if any(value is not None and not isinstance(value, (str, int)) for value in raw_values):
                raise ValueError(
                    f"allowlist entry {menu_id}.{field} values must be strings, integers, or null"
                )
            if len(set(raw_values)) != len(raw_values):
                raise ValueError(f"allowlist entry {menu_id}.{field} contains duplicate values")
            canonical = tuple(sorted(raw_values, key=_value_sort_key))
            if tuple(raw_values) != canonical:
                raise ValueError(f"allowlist entry {menu_id}.{field} values are not in canonical order")
            fields[field] = canonical
        result[menu_id] = dict(sorted(fields.items()))
    return dict(sorted(result.items()))


def evaluate_gate(actual: ConflictSpec, mode: str, allowed: ConflictSpec | None = None) -> GateResult:
    if mode == "strict":
        return GateResult((), tuple(sorted(actual)), ())
    if mode != "release":
        raise ValueError(f"unsupported gate mode: {mode}")
    if allowed is None:
        raise ValueError("release mode requires an explicit allowlist")
    allowed_ids = tuple(sorted(menu_id for menu_id, values in actual.items() if allowed.get(menu_id) == values))
    failing_ids = tuple(sorted(menu_id for menu_id in actual if menu_id not in allowed_ids))
    stale_ids = tuple(sorted(menu_id for menu_id in allowed if menu_id not in actual))
    return GateResult(allowed_ids, failing_ids, stale_ids)


def _display_value(value: StaticValue) -> str:
    if value is None:
        return "<NULL>"
    return json.dumps(value, ensure_ascii=False)


def _json_report(
    files: Sequence[Path],
    definitions: Sequence[Definition],
    conflicts: Mapping[int, Mapping[str, Mapping[StaticValue, Sequence[Definition]]]],
    gate: GateResult,
    mode: str,
) -> dict[str, object]:
    allowed = set(gate.allowed_ids)
    failing = set(gate.failing_ids)
    entries = []
    for menu_id, fields in sorted(conflicts.items()):
        field_report: dict[str, object] = {}
        for field, values in sorted(fields.items()):
            field_report[field] = [
                {
                    "value": value,
                    "locations": [
                        {"path": item.source, "line": item.line, "statement": item.statement}
                        for item in definitions_for_value
                    ],
                }
                for value, definitions_for_value in sorted(values.items(), key=lambda item: _value_sort_key(item[0]))
            ]
        entries.append(
            {
                "menu_id": menu_id,
                "disposition": "allowed" if menu_id in allowed else "error" if menu_id in failing else "unknown",
                "fields": field_report,
            }
        )
    return {
        "status": "pass" if gate.passed else "fail",
        "mode": mode,
        "files_scanned": len(files),
        "definitions_scanned": len(definitions),
        "conflicts": entries,
        "stale_allowlist_ids": list(gate.stale_allowlist_ids),
        "allowlist_template": allowlist_document(conflict_spec(conflicts)),
    }


def _print_text_report(
    files: Sequence[Path],
    definitions: Sequence[Definition],
    conflicts: Mapping[int, Mapping[str, Mapping[StaticValue, Sequence[Definition]]]],
    gate: GateResult,
    mode: str,
) -> None:
    print(f"Scanned {len(files)} SQL files and {len(definitions)} static ownership-field definitions.")
    allowed = set(gate.allowed_ids)
    for menu_id, fields in sorted(conflicts.items()):
        disposition = "ALLOWED" if menu_id in allowed else "ERROR"
        print(f"{disposition} menu_id {menu_id}")
        for field, values in sorted(fields.items()):
            print(f"  {field}:")
            for value, items in sorted(values.items(), key=lambda item: _value_sort_key(item[0])):
                locations = ", ".join(f"{item.source}:{item.line}" for item in items)
                print(f"    {_display_value(value)} <- {locations}")
    for menu_id in gate.stale_allowlist_ids:
        print(f"ERROR stale allowlist entry menu_id {menu_id}: no current conflict matches it")
    if gate.passed:
        qualifier = " (historical conflicts explicitly allowlisted)" if gate.allowed_ids else ""
        print(f"PASS: menu ID ownership gate passed in {mode} mode{qualifier}.")
    else:
        print(
            "FAIL: menu ID ownership conflicts are not release-safe "
            f"({len(gate.failing_ids)} conflicting IDs, {len(gate.stale_allowlist_ids)} stale allowlist IDs)."
        )


def _argument_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "paths",
        nargs="*",
        type=Path,
        help="SQL files or directories (default: repository sql/ and docker/mysql/db/)",
    )
    parser.add_argument("--mode", choices=("strict", "release"), default="strict")
    parser.add_argument("--allowlist", type=Path, help="versioned JSON conflict allowlist; required in release mode")
    parser.add_argument("--format", choices=("text", "json"), default="text", dest="output_format")
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = _argument_parser().parse_args(argv)
    if args.mode == "release" and args.allowlist is None:
        print("ERROR: --mode release requires --allowlist", file=sys.stderr)
        return 2
    if args.mode == "strict" and args.allowlist is not None:
        print("ERROR: --allowlist is only valid with --mode release", file=sys.stderr)
        return 2

    scan_roots = args.paths or [ROOT / "sql", ROOT / "docker/mysql/db"]
    try:
        files, definitions = scan_paths(scan_roots)
        conflicts = build_conflicts(definitions)
        actual = conflict_spec(conflicts)
        allowed = load_allowlist(args.allowlist) if args.allowlist is not None else None
        gate = evaluate_gate(actual, args.mode, allowed)
    except (OSError, UnicodeError, ValueError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2

    if args.output_format == "json":
        print(json.dumps(_json_report(files, definitions, conflicts, gate, args.mode), ensure_ascii=False, indent=2))
    else:
        _print_text_report(files, definitions, conflicts, gate, args.mode)
    return 0 if gate.passed else 1


if __name__ == "__main__":
    raise SystemExit(main())

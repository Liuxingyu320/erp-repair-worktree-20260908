#!/usr/bin/env python3
"""Verify the frozen contract-signing Release A worktree scope.

The scope manifest lives in a marked JSON block inside the release scope
document.  The baseline mode validates every discovered dirty signing file,
its Git status, and its SHA-256.  The staged mode is intended for later tasks:
it rejects anything staged outside the frozen Release A allowlist.
"""

from __future__ import annotations

import argparse
import fnmatch
import hashlib
import json
import re
import subprocess
import sys
from pathlib import Path, PurePosixPath
from typing import Any, Iterable


SCOPE_JSON_BEGIN = "<!-- CONTRACT_SIGNING_SCOPE_JSON_BEGIN -->"
SCOPE_JSON_END = "<!-- CONTRACT_SIGNING_SCOPE_JSON_END -->"
SHA256_RE = re.compile(r"[0-9a-f]{64}")
GIT_COMMIT_RE = re.compile(r"[0-9a-f]{40}")
REQUIRED_DENY_CATEGORIES = {
    "artifacts",
    "backups",
    "database-dumps",
    "employee-data",
    "signatures-and-uploads",
    "test-credentials",
}
REQUIRED_LEGACY_ASSERTIONS = {
    "canonical-migration-order",
    "unique-migration-list",
    "source-deploy-byte-identity",
    "feature-flags-default-false",
    "runner-fail-closed",
    "runner-global-lock",
    "runner-copy-drift-guard",
    "runner-explicit-database",
}
CANONICAL_SCOPE_PATH = "docs/releases/20260716-contract-signing-release-scope.md"
REQUIRED_CONTROL_FILES = (
    CANONICAL_SCOPE_PATH,
    "scripts/contract/menu_id_conflicts_20260716.json",
    "scripts/contract/test_verify_contract_signing_scope.py",
    "scripts/contract/test_verify_menu_id_ownership.py",
    "scripts/contract/verify_contract_signing_scope.py",
    "scripts/contract/verify_menu_id_ownership.py",
)

# Keep this hard-coded.  A manifest edit must not be able to widen Release A
# into inventory, approval, cloud-drive, build-output, or deployment domains.
SAFE_RELEASE_A_PREFIXES = (
    "docs/decisions/",
    "docs/releases/",
    "docs/runbooks/",
    "docker/mysql/db/erp_oa_sign_",
    "erp-api/erp-api-oa/",
    "erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysLegalEntity.java",
    "erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysUser.java",
    "erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysUserProfile.java",
    "erp-common/erp-common-core/src/main/java/com/erp/common/core/domain/todo/",
    "erp-common/erp-common-core/src/test/java/com/erp/common/core/domain/todo/",
    "erp-modules/erp-oa/",
    "erp-modules/erp-system/",
    "erp-ui/src/api/oa/",
    "erp-ui/src/api/system/legalEntity.js",
    "erp-ui/src/utils/sign",
    "erp-ui/src/utils/todo",
    "erp-ui/src/views/mobile/signPackage/",
    "erp-ui/src/views/oa/signPackage/",
    "erp-ui/src/views/oa/signTask/",
    "erp-ui/test/contractUsabilityAudit.test.js",
    "erp-ui/test/sign",
    "erp-ui/test/unifiedTodo",
    "scripts/contract-signing-",
    "scripts/contract/",
    "scripts/qa/contract-signing-",
    "scripts/qa/prepare_contract_signing_",
    "scripts/qa/run_contract_signing_",
    "scripts/qa/verify_contract_signing_",
    "scripts/test_contract_signing_",
    "scripts/verify-contract-signing-",
    "sql/erp_oa_sign_",
)


class ScopeVerificationError(ValueError):
    """Raised when the scope document cannot be parsed safely."""


def _run_git(root: Path, *args: str, check: bool = True) -> subprocess.CompletedProcess[bytes]:
    return subprocess.run(
        ["git", "-C", str(root), *args],
        check=check,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )


def _safe_relative_path(value: Any) -> str | None:
    if not isinstance(value, str) or not value or "\\" in value or "\x00" in value:
        return None
    path = PurePosixPath(value)
    if path.is_absolute() or any(part in {"", ".", ".."} for part in path.parts):
        return None
    return value


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def sha256_file(path: Path) -> str:
    return sha256_bytes(path.read_bytes())


def load_scope_text(text: str) -> dict[str, Any]:
    begin = text.find(SCOPE_JSON_BEGIN)
    end = text.find(SCOPE_JSON_END)
    if begin < 0 or end < 0 or end <= begin:
        raise ScopeVerificationError("scope document is missing the marked JSON block")
    payload = text[begin + len(SCOPE_JSON_BEGIN) : end].strip()
    try:
        value = json.loads(payload)
    except json.JSONDecodeError as exc:
        raise ScopeVerificationError(f"scope JSON is invalid: {exc}") from exc
    if not isinstance(value, dict):
        raise ScopeVerificationError("scope JSON root must be an object")
    return value


def load_scope_document(path: Path) -> dict[str, Any]:
    return load_scope_text(path.read_text(encoding="utf-8"))


def load_staged_scope(root: Path, path: Path) -> dict[str, Any]:
    """Load the scope from Git's index, never from an unstaged worktree edit."""

    root = root.resolve()
    path = path.resolve()
    try:
        relative = path.relative_to(root).as_posix()
    except ValueError as exc:
        raise ScopeVerificationError("staged scope document must be inside the repository") from exc
    result = _run_git(root, "show", f":{relative}", check=False)
    if result.returncode != 0:
        raise ScopeVerificationError(
            f"staged mode requires the scope document in Git's index: {relative}"
        )
    try:
        text = result.stdout.decode("utf-8")
    except UnicodeDecodeError as exc:
        raise ScopeVerificationError("staged scope document is not valid UTF-8") from exc
    return load_scope_text(text)


def git_worktree_status(root: Path) -> dict[str, str]:
    """Return path -> M/D/U for porcelain-v1 worktree state."""

    raw = _run_git(root, "status", "--porcelain=v1", "-z", "--untracked-files=all").stdout
    records = raw.split(b"\0")
    result: dict[str, str] = {}
    index = 0
    while index < len(records):
        record = records[index]
        index += 1
        if not record:
            continue
        if len(record) < 4 or record[2:3] != b" ":
            raise ScopeVerificationError("unexpected Git porcelain record")
        xy = record[:2].decode("ascii", "strict")
        path = record[3:].decode("utf-8", "surrogateescape")
        if "R" in xy or "C" in xy:
            # Porcelain -z adds the original path as the following NUL field.
            if index >= len(records) or not records[index]:
                raise ScopeVerificationError("incomplete Git rename/copy record")
            index += 1
        if xy == "??":
            status = "U"
        elif "D" in xy:
            status = "D"
        else:
            status = "M"
        result[path] = status
    return result


def git_staged_paths(root: Path) -> set[str]:
    raw = _run_git(
        root,
        "diff",
        "--cached",
        "--no-renames",
        "--name-only",
        "-z",
        "--diff-filter=ACDMRTUXB",
    ).stdout
    return {
        value.decode("utf-8", "surrogateescape")
        for value in raw.split(b"\0")
        if value
    }


def _head_blob(root: Path, relative: str) -> bytes:
    result = _run_git(root, "show", f"HEAD:{relative}", check=False)
    if result.returncode != 0:
        raise ScopeVerificationError(f"HEAD blob is unavailable for deleted path: {relative}")
    return result.stdout


def _index_blob(root: Path, relative: str) -> bytes | None:
    result = _run_git(root, "show", f":{relative}", check=False)
    return result.stdout if result.returncode == 0 else None


def _compile_discovery_regexes(scope: dict[str, Any], errors: list[str]) -> list[re.Pattern[str]]:
    discovery = scope.get("discovery")
    if not isinstance(discovery, dict):
        errors.append("discovery must be an object")
        return []
    values = discovery.get("pathRegexes")
    if not isinstance(values, list) or not values:
        errors.append("discovery.pathRegexes must be a non-empty array")
        return []
    compiled: list[re.Pattern[str]] = []
    for index, value in enumerate(values):
        if not isinstance(value, str) or not value:
            errors.append(f"discovery.pathRegexes[{index}] must be a non-empty string")
            continue
        try:
            compiled.append(re.compile(value))
        except re.error as exc:
            errors.append(f"invalid discovery regex {value!r}: {exc}")
    return compiled


def _list_of_safe_paths(
    value: Any,
    label: str,
    errors: list[str],
    *,
    allow_empty: bool = False,
) -> list[str]:
    if not isinstance(value, list) or (not allow_empty and not value):
        qualifier = "an array" if allow_empty else "a non-empty array"
        errors.append(f"{label} must be {qualifier}")
        return []
    result: list[str] = []
    for index, item in enumerate(value):
        path = _safe_relative_path(item)
        if path is None:
            errors.append(f"{label}[{index}] is not a safe repository-relative path")
        else:
            result.append(path)
    if len(result) != len(set(result)):
        errors.append(f"{label} contains duplicate paths")
    return result


def _deny_rules(scope: dict[str, Any], errors: list[str]) -> list[dict[str, str]]:
    raw = scope.get("denylist")
    if not isinstance(raw, list) or not raw:
        errors.append("denylist must be a non-empty array")
        return []
    result: list[dict[str, str]] = []
    categories: set[str] = set()
    for index, item in enumerate(raw):
        if not isinstance(item, dict):
            errors.append(f"denylist[{index}] must be an object")
            continue
        category = item.get("category")
        pattern = item.get("pattern")
        reason = item.get("reason")
        if not all(isinstance(value, str) and value for value in (category, pattern, reason)):
            errors.append(f"denylist[{index}] requires category, pattern, and reason")
            continue
        categories.add(category)
        result.append({"category": category, "pattern": pattern, "reason": reason})
    missing = REQUIRED_DENY_CATEGORIES - categories
    if missing:
        errors.append("denylist is missing required categories: " + ", ".join(sorted(missing)))
    return result


def denied_by(path: str, rules: Iterable[dict[str, str]]) -> dict[str, str] | None:
    for rule in rules:
        if fnmatch.fnmatchcase(path, rule["pattern"]):
            return rule
    return None


def validate_scope_structure(scope: dict[str, Any]) -> tuple[list[str], set[str], list[dict[str, str]]]:
    errors: list[str] = []
    if scope.get("schemaVersion") != 1:
        errors.append("schemaVersion must equal 1")
    if scope.get("releaseId") != "contract-signing-release-a-20260716":
        errors.append("releaseId must be contract-signing-release-a-20260716")

    baseline = scope.get("baseline")
    if not isinstance(baseline, dict):
        errors.append("baseline must be an object")
    else:
        if not GIT_COMMIT_RE.fullmatch(str(baseline.get("commit", ""))):
            errors.append("baseline.commit must be a full 40-character Git commit hash")
        if not isinstance(baseline.get("branch"), str) or not baseline.get("branch"):
            errors.append("baseline.branch is required")
        if not isinstance(baseline.get("capturedAt"), str) or not baseline.get("capturedAt"):
            errors.append("baseline.capturedAt is required")

    manifest_control = _safe_relative_path(scope.get("manifestControlFile"))
    if manifest_control != CANONICAL_SCOPE_PATH:
        errors.append(f"manifestControlFile must equal {CANONICAL_SCOPE_PATH}")
    controls = _list_of_safe_paths(scope.get("controlFiles"), "controlFiles", errors)
    if tuple(controls) != REQUIRED_CONTROL_FILES:
        errors.append("controlFiles must exactly match the required release gate controls")
    raw_control_hashes = scope.get("controlFileHashes")
    control_hashes: dict[str, str] = {}
    if not isinstance(raw_control_hashes, dict):
        errors.append("controlFileHashes must be an object")
    else:
        for raw_path, raw_hash in raw_control_hashes.items():
            path = _safe_relative_path(raw_path)
            if path is None:
                errors.append("controlFileHashes contains an unsafe path")
            elif not SHA256_RE.fullmatch(str(raw_hash)):
                errors.append(f"controlFileHashes has an invalid SHA-256: {path}")
            else:
                control_hashes[path] = str(raw_hash)
    # The canonical manifest is self-describing and loaded from Git's index in
    # staged mode; every executable/data control after it is content-pinned.
    expected_control_hashes = set(REQUIRED_CONTROL_FILES[1:])
    if set(control_hashes) != expected_control_hashes:
        errors.append(
            "controlFileHashes keys must exactly match non-manifest controlFiles"
        )
    allowlist = _list_of_safe_paths(scope.get("releaseAAllowlist"), "releaseAAllowlist", errors)
    allowset = set(allowlist)
    for path in controls:
        if path not in allowset:
            errors.append(f"control file is missing from Release A allowlist: {path}")

    rules = _deny_rules(scope, errors)
    for path in allowlist:
        if not path.startswith(SAFE_RELEASE_A_PREFIXES):
            errors.append(f"Release A path is outside the hard-coded OA/System/signing roots: {path}")
        rule = denied_by(path, rules)
        if rule:
            errors.append(f"Release A path is denied by {rule['category']}: {path}")

    _compile_discovery_regexes(scope, errors)
    discovery = scope.get("discovery") if isinstance(scope.get("discovery"), dict) else {}
    _list_of_safe_paths(
        discovery.get("extraPaths", []),
        "discovery.extraPaths",
        errors,
        allow_empty=True,
    )

    inventory = scope.get("inventory")
    if not isinstance(inventory, list) or not inventory:
        errors.append("inventory must be a non-empty array")
        inventory = []
    inventory_paths: list[str] = []
    for index, item in enumerate(inventory):
        if not isinstance(item, dict):
            errors.append(f"inventory[{index}] must be an object")
            continue
        path = _safe_relative_path(item.get("path"))
        if path is None:
            errors.append(f"inventory[{index}].path is unsafe")
            continue
        inventory_paths.append(path)
        if item.get("status") not in {"M", "D", "U"}:
            errors.append(f"inventory status must be M, D, or U: {path}")
        expected_source = "HEAD" if item.get("status") == "D" else "WORKTREE"
        if item.get("hashSource") != expected_source:
            errors.append(f"inventory hashSource must be {expected_source}: {path}")
        if not SHA256_RE.fullmatch(str(item.get("sha256", ""))):
            errors.append(f"inventory sha256 is invalid: {path}")
        if not isinstance(item.get("stage"), str) or not item.get("stage"):
            errors.append(f"inventory stage is required: {path}")
        if not isinstance(item.get("releaseA"), bool):
            errors.append(f"inventory releaseA must be boolean: {path}")
        elif item["releaseA"] != (path in allowset):
            errors.append(f"inventory releaseA disagrees with the explicit allowlist: {path}")
        if item.get("releaseA") and denied_by(path, rules):
            errors.append(f"denied inventory path cannot enter Release A: {path}")
    if len(inventory_paths) != len(set(inventory_paths)):
        errors.append("inventory contains duplicate paths")

    pairs = scope.get("migrationPairs")
    if not isinstance(pairs, list) or not pairs:
        errors.append("migrationPairs must be a non-empty array")
        pairs = []
    declared_pairs: set[tuple[str, str]] = set()
    for index, pair in enumerate(pairs):
        if not isinstance(pair, dict):
            errors.append(f"migrationPairs[{index}] must be an object")
            continue
        source = _safe_relative_path(pair.get("source"))
        deploy = _safe_relative_path(pair.get("deploy"))
        if source is None or deploy is None:
            errors.append(f"migrationPairs[{index}] contains an unsafe path")
            continue
        if not source.startswith("sql/erp_oa_sign_") or not source.endswith(".sql"):
            errors.append(f"invalid Release A source migration: {source}")
        expected_deploy = "docker/mysql/db/" + PurePosixPath(source).name
        if deploy != expected_deploy:
            errors.append(f"migration deploy copy does not mirror source name: {source}")
        if source not in allowset or deploy not in allowset:
            errors.append(f"migration pair is not fully allowlisted: {source}")
        declared_pairs.add((source, deploy))

    expected_sources = {
        path
        for path in allowset
        if path.startswith("sql/erp_oa_sign_") and path.endswith(".sql")
    }
    paired_sources = {source for source, _ in declared_pairs}
    if expected_sources != paired_sources:
        errors.append("migrationPairs differs from the allowlisted Release A SQL set")

    review = scope.get("duplicateTestReview")
    if not isinstance(review, dict):
        errors.append("duplicateTestReview must be an object")
    else:
        legacy = _safe_relative_path(review.get("legacyPath"))
        canonical = _safe_relative_path(review.get("canonicalPath"))
        if legacy is None or canonical is None:
            errors.append("duplicate test paths must be safe repository-relative paths")
        else:
            if legacy in allowset or canonical in allowset:
                errors.append("new-business migration tests must not enter contract-signing Release A")
            if not denied_by(legacy, rules):
                errors.append("legacy duplicate test must be covered by the backup-file denylist")
        if review.get("allAssertionsSuperseded") is not True:
            errors.append("duplicate review must affirm that every legacy assertion is superseded")
        if review.get("resolution") not in {
            "REMOVE_BEFORE_PROJECT_WIDE_TEST",
            "REMOVED_BEFORE_PROJECT_WIDE_TEST",
        }:
            errors.append("duplicate review resolution must require or record removal before project-wide test")
        for key in ("legacySha256", "canonicalSha256"):
            if not SHA256_RE.fullmatch(str(review.get(key, ""))):
                errors.append(f"duplicateTestReview.{key} is invalid")
        evidence = review.get("supersessionEvidence")
        if not isinstance(evidence, list):
            errors.append("duplicate supersessionEvidence must be an array")
        else:
            ids = {
                item.get("id")
                for item in evidence
                if isinstance(item, dict) and isinstance(item.get("id"), str)
            }
            if ids != REQUIRED_LEGACY_ASSERTIONS:
                errors.append("duplicate supersessionEvidence does not cover every legacy assertion group")

    return errors, allowset, rules


def _validate_migration_files(
    scope: dict[str, Any], root: Path, errors: list[str], *, indexed: bool = False
) -> None:
    for pair in scope.get("migrationPairs", []):
        if not isinstance(pair, dict):
            continue
        source_value = _safe_relative_path(pair.get("source"))
        deploy_value = _safe_relative_path(pair.get("deploy"))
        if source_value is None or deploy_value is None:
            continue
        if indexed:
            source_content = _index_blob(root, source_value)
            deploy_content = _index_blob(root, deploy_value)
        else:
            source = root / source_value
            deploy = root / deploy_value
            source_content = source.read_bytes() if source.is_file() else None
            deploy_content = deploy.read_bytes() if deploy.is_file() else None
            if source.exists() and not source.is_file() or deploy.exists() and not deploy.is_file():
                errors.append(f"Release A migration pair must contain regular files: {source_value}")
                continue
        if (source_content is None) != (deploy_content is None):
            errors.append(f"Release A migration pair is incomplete: {source_value}")
        elif source_content is not None and source_content != deploy_content:
            errors.append(f"Release A migration pair differs byte-for-byte: {source_value}")


def _validate_duplicate_review(scope: dict[str, Any], root: Path, errors: list[str]) -> None:
    review = scope.get("duplicateTestReview")
    if not isinstance(review, dict):
        return
    legacy_value = _safe_relative_path(review.get("legacyPath"))
    canonical_value = _safe_relative_path(review.get("canonicalPath"))
    if legacy_value is None or canonical_value is None:
        return
    legacy = root / legacy_value
    canonical = root / canonical_value
    removal_completed = review.get("resolution") == "REMOVED_BEFORE_PROJECT_WIDE_TEST"
    if removal_completed:
        if legacy.exists():
            errors.append(f"legacy duplicate test still exists after recorded removal: {legacy_value}")
    elif not legacy.is_file():
        errors.append(f"legacy duplicate test is missing before its recorded removal: {legacy_value}")
    elif sha256_file(legacy) != review.get("legacySha256"):
        errors.append(f"legacy duplicate test hash drifted: {legacy_value}")
    if not canonical.is_file():
        errors.append(f"canonical migration test is missing: {canonical_value}")
    elif sha256_file(canonical) != review.get("canonicalSha256"):
        errors.append(f"canonical migration test hash drifted: {canonical_value}")
    ignored = _run_git(root, "check-ignore", "-q", "--", legacy_value, check=False)
    if ignored.returncode != 0:
        errors.append(f"legacy duplicate test is no longer ignored by Git: {legacy_value}")


def _validate_control_files(
    scope: dict[str, Any], root: Path, errors: list[str], *, indexed: bool = False
) -> None:
    """Require every control and pin non-manifest controls by content hash."""

    controls = scope["controlFiles"]
    hashes = scope["controlFileHashes"]
    for control in controls:
        content = _index_blob(root, control) if indexed else None
        if not indexed:
            path = root / control
            content = path.read_bytes() if path.is_file() else None
        if content is None:
            errors.append(f"scope control file is missing: {control}")
            continue
        expected_hash = hashes.get(control)
        if expected_hash is not None:
            actual_hash = sha256_bytes(content)
            if actual_hash != expected_hash:
                errors.append(
                    f"control file SHA-256 drift for {control}: {actual_hash}"
                )


def verify_baseline(scope: dict[str, Any], root: Path) -> list[str]:
    errors, _, _ = validate_scope_structure(scope)
    if errors:
        return errors

    root = root.resolve()
    head = _run_git(root, "rev-parse", "HEAD").stdout.decode("ascii").strip()
    if head != scope["baseline"]["commit"]:
        errors.append(f"HEAD differs from frozen baseline: {head}")

    status = git_worktree_status(root)
    controls = set(scope["controlFiles"])
    regexes = _compile_discovery_regexes(scope, errors)
    extras = set(scope["discovery"].get("extraPaths", []))
    discovered = {
        path
        for path in status
        if path not in controls
        and (path in extras or any(regex.fullmatch(path) for regex in regexes))
    }
    inventory = {item["path"]: item for item in scope["inventory"]}
    inventory_paths = set(inventory)
    undeclared = discovered - inventory_paths
    stale = inventory_paths - discovered - controls
    if undeclared:
        errors.append("undeclared dirty signing paths: " + ", ".join(sorted(undeclared)))
    if stale:
        errors.append("inventory paths no longer match signing discovery: " + ", ".join(sorted(stale)))

    for path, item in inventory.items():
        actual_status = status.get(path)
        if actual_status != item["status"]:
            errors.append(
                f"Git status drift for {path}: expected {item['status']}, got {actual_status or 'clean'}"
            )
            continue
        try:
            content = _head_blob(root, path) if item["hashSource"] == "HEAD" else (root / path).read_bytes()
        except (OSError, ScopeVerificationError) as exc:
            errors.append(str(exc))
            continue
        actual_hash = sha256_bytes(content)
        if actual_hash != item["sha256"]:
            errors.append(f"SHA-256 drift for {path}: {actual_hash}")

    _validate_control_files(scope, root, errors)

    _validate_migration_files(scope, root, errors)
    _validate_duplicate_review(scope, root, errors)
    return errors


def verify_static(scope: dict[str, Any], root: Path) -> list[str]:
    """Validate the frozen contract without requiring an unchanged worktree.

    This mode remains useful after an allowlisted implementation task starts:
    baseline mode will intentionally report its hash drift, while static mode
    still checks the exact allowlist, denylist, paired SQL, and duplicate-test
    disposition.
    """

    errors, _, _ = validate_scope_structure(scope)
    if errors:
        return errors
    root = root.resolve()
    _validate_control_files(scope, root, errors)
    _validate_migration_files(scope, root, errors)
    _validate_duplicate_review(scope, root, errors)
    return errors


def verify_staged(scope: dict[str, Any], root: Path) -> list[str]:
    errors, allowset, rules = validate_scope_structure(scope)
    if errors:
        return errors
    root = root.resolve()
    _validate_control_files(scope, root, errors, indexed=True)
    _validate_migration_files(scope, root, errors, indexed=True)
    for path in sorted(git_staged_paths(root)):
        rule = denied_by(path, rules)
        if rule:
            errors.append(f"staged path is denied by {rule['category']}: {path}")
        elif path not in allowset:
            errors.append(f"staged path is outside the frozen Release A allowlist: {path}")
    return errors


def build_parser() -> argparse.ArgumentParser:
    script = Path(__file__).resolve()
    default_root = script.parents[2]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=default_root)
    parser.add_argument(
        "--scope",
        type=Path,
        default=default_root / "docs/releases/20260716-contract-signing-release-scope.md",
    )
    parser.add_argument("--mode", choices=("baseline", "static", "staged"), default="baseline")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        scope = (
            load_staged_scope(args.root, args.scope)
            if args.mode == "staged"
            else load_scope_document(args.scope.resolve())
        )
        if args.mode == "baseline":
            errors = verify_baseline(scope, args.root)
        elif args.mode == "static":
            errors = verify_static(scope, args.root)
        else:
            errors = verify_staged(scope, args.root)
    except (OSError, ScopeVerificationError, subprocess.CalledProcessError) as exc:
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 1
    if errors:
        for error in errors:
            print(f"[FAIL] {error}", file=sys.stderr)
        return 1
    print(
        f"[PASS] contract-signing {args.mode} scope verified: "
        f"{len(scope['inventory'])} frozen files, {len(scope['releaseAAllowlist'])} allowlisted paths"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

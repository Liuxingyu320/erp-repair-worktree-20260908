#!/usr/bin/env python3
"""Fail-closed static verifier for the 20260718 signing artifacts.

Hashes and semantic checks are deliberately bound to one read-only snapshot.
The source artifacts must have the same hash before and after that snapshot is
copied, so a mutable build directory can never be verified accidentally.

JAR checks prove a Java 17 Spring Boot *structure* and validate the selected
classfile structures, annotations and instruction framing.  They deliberately
do not claim full JVM execution: exact-artifact-hash-bound isolated startup and
health evidence remains a separate mandatory release gate.
"""

from __future__ import annotations

import argparse
import gzip
import hashlib
import io
import json
import os
import re
import shutil
import stat
import subprocess
import sys
import tempfile
import time
import zipfile
from html.parser import HTMLParser
from pathlib import Path, PurePosixPath
from typing import BinaryIO, Iterable
from urllib.parse import unquote, urlsplit


RELEASE_ID = "onboard-contract-excel-20260718"
SHA256 = re.compile(r"[0-9a-f]{64}")
GIT_COMMIT = re.compile(r"[0-9a-f]{40}")

# Every budget is intentionally finite.  Real production artifacts are far
# below these limits, while malformed archives/trees fail before unbounded
# allocation or decompression.
MAX_ARTIFACT_FILE_BYTES = 4 * 1024 * 1024 * 1024
MAX_DIST_FILES = 100_000
MAX_DIST_BYTES = 4 * 1024 * 1024 * 1024
MAX_GZIP_DECOMPRESSED_BYTES = 4 * 1024 * 1024 * 1024
MAX_ZIP_ENTRIES = 250_000
MAX_ZIP_ENTRY_BYTES = 512 * 1024 * 1024
MAX_ZIP_DECLARED_BYTES = 4 * 1024 * 1024 * 1024
MAX_ZIP_SCANNED_BYTES = 4 * 1024 * 1024 * 1024
MAX_ZIP_COMPRESSION_RATIO = 500
MAX_NESTED_JAR_BYTES = 512 * 1024 * 1024
MAX_CLASSFILE_BYTES = 16 * 1024 * 1024
MAX_INDEX_HTML_BYTES = 4 * 1024 * 1024
MAX_JS_FILE_BYTES = 64 * 1024 * 1024
MAX_BUILD_INFO_BYTES = 64 * 1024
MAX_PROVENANCE_JSON_BYTES = 64 * 1024
MAX_RELEASE_MANIFEST_BYTES = 1024 * 1024
MAX_MANIFEST_BYTES = 1024 * 1024
MAX_REACHABLE_JS_FILES = 512
MAX_REACHABLE_JS_TOTAL_BYTES = 1024 * 1024 * 1024
MAX_JS_TOKENS = 2_000_000
JS_SYNTAX_TIMEOUT_SECONDS = 10
JS_SYNTAX_TOTAL_TIMEOUT_SECONDS = 120

SPRING_BOOT_MAIN_CLASS = "org.springframework.boot.loader.launch.JarLauncher"
SPRING_BOOT_LAUNCHER_CLASS = (
    "org/springframework/boot/loader/launch/JarLauncher"
)
START_CLASSES = {
    "com/erp/oa": "com.erp.oa.ErpOaApplication",
    "com/erp/system": "com.erp.system.ErpSystemApplication",
}

REST_CONTROLLER = "Lorg/springframework/web/bind/annotation/RestController;"
REQUEST_MAPPING = "Lorg/springframework/web/bind/annotation/RequestMapping;"
GET_MAPPING = "Lorg/springframework/web/bind/annotation/GetMapping;"
POST_MAPPING = "Lorg/springframework/web/bind/annotation/PostMapping;"
REQUIRES_PERMISSIONS = "Lcom/erp/common/security/annotation/RequiresPermissions;"
REQUIRES_LOGIN = "Lcom/erp/common/security/annotation/RequiresLogin;"
INNER_AUTH = "Lcom/erp/common/security/annotation/InnerAuth;"
SEND_PERMISSION = "oa:signTask:send"

OA_METHOD_CONTRACT = (
    ("preview", POST_MAPPING, "/import/preview", REQUIRES_PERMISSIONS, SEND_PERMISSION),
    (
        "generate",
        POST_MAPPING,
        "/import/{batchId}/generate",
        REQUIRES_PERMISSIONS,
        SEND_PERMISSION,
    ),
    ("mine", GET_MAPPING, "/data-request/mine", REQUIRES_LOGIN, None),
)
SYSTEM_METHOD_CONTRACT = (
    ("supplement", POST_MAPPING, "/supplement", INNER_AUTH, None),
)

BUILD_INFO_PATHS = (
    "META-INF/build-info.properties",
    "BOOT-INF/classes/META-INF/build-info.properties",
)
BUILD_PROVENANCE_FIELDS = {
    "build.commit": "candidateCommit",
    "build.releaseId": "releaseId",
    "build.approvedPatchSha256": "approvedPatchSha256",
    "build.approvedSourceManifestSha256": "approvedSourceManifestSha256",
}

# This is the exact superseded-entry set in
# scripts/onboard-contract-excel-deleted-paths-20260718.list.  Keeping the
# contract here makes direct-path verification independent of a mutable source
# checkout or list file.
REMOVED_ENTRY_PATHS = (
    "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaExistingEmployeeOnboardInitiateRequest.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaExistingEmployeeOnboardPreviewRequest.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/OaExistingEmployeeOnboardInitiateItem.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/OaExistingEmployeeOnboardInitiateResult.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/OaExistingEmployeeOnboardPreviewRow.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaExistingEmployeeOnboardService.java",
    "erp-modules/erp-oa/src/test/java/com/erp/oa/controller/OaExistingEmployeeOnboardControllerTest.java",
    "erp-modules/erp-oa/src/test/java/com/erp/oa/mapper/OaExistingEmployeeOnboardMapperBindingTest.java",
    "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaExistingEmployeeOnboardServiceTest.java",
    "erp-ui/src/views/hr/components/HrOnboardContractBatchDialog.vue",
)

REQUIRED_OA_CLASSES = (
    "com/erp/oa/controller/OaSignOnboardImportController",
    "com/erp/oa/service/impl/OaSignOnboardImportService",
    "com/erp/oa/service/impl/OaSignOnboardGenerationService",
    "com/erp/oa/service/impl/OaSignOnboardDataRequestService",
)
REQUIRED_SYSTEM_CLASSES = (
    "com/erp/system/controller/SysSigningProfileSupplementController",
    "com/erp/system/service/ISysSigningProfileSupplementService",
    "com/erp/system/service/impl/SysSigningProfileSupplementServiceImpl",
    "com/erp/system/mapper/SysSignProfileSupplementAuditMapper",
    "com/erp/system/service/support/SigningProfileFactsHash",
)

FORBIDDEN_OA_ROUTES = (b"/onboard/batch/preview", b"/onboard/batch/initiate")
REQUIRED_OA_MARKERS = (
    b"/signTask/onboard",
    b"/import/preview",
    b"/data-request/mine",
    b"/import/{batchId}/generate",
)
REQUIRED_SYSTEM_MARKERS = (b"/user/sign-profile", b"/supplement")
FORBIDDEN_DIST_MARKERS = (
    b"/oa/signTask/onboard/batch/preview",
    b"/oa/signTask/onboard/batch/initiate",
    b"previewOnboardSignTaskBatch",
    b"initiateOnboardSignTaskBatch",
    b"HrOnboardContractBatchDialog",
)
REQUIRED_DIST_MARKERS = (
    b"/oa/signTask/onboard/import/preview",
    b"/oa/signTask/onboard/data-request/mine",
    b"HrSignDataImportDialog",
    "批量处理入职合同".encode("utf-8"),
)
JS_SUFFIXES = (".js", ".mjs", ".cjs")


def fail(message: str) -> None:
    raise ValueError(message)


def _regular_reader(path: Path) -> BinaryIO:
    """Open a non-symlink regular file and detect path substitution."""

    flags = os.O_RDONLY
    if hasattr(os, "O_CLOEXEC"):
        flags |= os.O_CLOEXEC
    if hasattr(os, "O_NOFOLLOW"):
        flags |= os.O_NOFOLLOW
    try:
        fd = os.open(path, flags)
    except OSError as exc:
        raise ValueError(f"artifact file is unreadable or unsafe: {path}") from exc
    opened = os.fstat(fd)
    if not stat.S_ISREG(opened.st_mode):
        os.close(fd)
        fail(f"artifact path is not a regular file: {path}")
    try:
        listed = path.lstat()
    except OSError:
        os.close(fd)
        raise
    if stat.S_ISLNK(listed.st_mode) or (listed.st_dev, listed.st_ino) != (
        opened.st_dev,
        opened.st_ino,
    ):
        os.close(fd)
        fail(f"artifact file changed or is a symlink: {path}")
    return os.fdopen(fd, "rb")


def _stable_file_sha256(path: Path, *, max_bytes: int = MAX_ARTIFACT_FILE_BYTES) -> str:
    digest = hashlib.sha256()
    with _regular_reader(path) as handle:
        before = os.fstat(handle.fileno())
        if before.st_size > max_bytes:
            fail(f"artifact file exceeds byte budget: {path}")
        total = 0
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            total += len(block)
            if total > max_bytes:
                fail(f"artifact file exceeds byte budget: {path}")
            digest.update(block)
        after = os.fstat(handle.fileno())
    identity_before = (
        before.st_dev,
        before.st_ino,
        before.st_size,
        before.st_mtime_ns,
        before.st_ctime_ns,
    )
    identity_after = (
        after.st_dev,
        after.st_ino,
        after.st_size,
        after.st_mtime_ns,
        after.st_ctime_ns,
    )
    if total != before.st_size or identity_before != identity_after:
        fail(f"artifact file changed while hashing: {path}")
    return digest.hexdigest()


def file_sha256(path: Path) -> str:
    path = Path(path)
    if path.is_symlink():
        fail(f"artifact must not be a symlink: {path}")
    return _stable_file_sha256(path)


def _dist_inventory(path: Path) -> list[tuple[str, Path, int]]:
    try:
        root_stat = path.lstat()
    except OSError as exc:
        raise ValueError(f"artifact directory is missing: {path}") from exc
    if stat.S_ISLNK(root_stat.st_mode) or not stat.S_ISDIR(root_stat.st_mode):
        fail(f"artifact directory is missing or is a symlink: {path}")

    files: list[tuple[str, Path, int]] = []
    total_bytes = 0
    stack = [(path, PurePosixPath())]
    while stack:
        directory, relative_dir = stack.pop()
        try:
            with os.scandir(directory) as iterator:
                entries = sorted(iterator, key=lambda entry: entry.name)
        except OSError as exc:
            raise ValueError(f"artifact directory is unreadable: {directory}") from exc
        for entry in entries:
            relative = relative_dir / entry.name
            try:
                item_stat = entry.stat(follow_symlinks=False)
            except OSError as exc:
                raise ValueError(f"artifact entry is unreadable: {entry.path}") from exc
            if stat.S_ISLNK(item_stat.st_mode):
                fail(f"artifact directory contains a symlink: {entry.path}")
            if stat.S_ISDIR(item_stat.st_mode):
                stack.append((Path(entry.path), relative))
                continue
            if not stat.S_ISREG(item_stat.st_mode):
                fail(f"artifact directory contains a non-regular entry: {entry.path}")
            files.append((relative.as_posix(), Path(entry.path), item_stat.st_size))
            total_bytes += item_stat.st_size
            if len(files) > MAX_DIST_FILES:
                fail("frontend dist exceeds total file budget")
            if total_bytes > MAX_DIST_BYTES:
                fail("frontend dist exceeds total byte budget")
    return sorted(files)


def directory_tree_sha256(path: Path) -> str:
    """Return the repository-standard path-and-content SHA256 for a dist tree."""

    path = Path(path)
    files = _dist_inventory(path)
    if not files:
        fail(f"artifact directory is empty: {path}")
    digest = hashlib.sha256()
    for relative, item, _ in files:
        digest.update(relative.encode("utf-8"))
        digest.update(b"\0")
        digest.update(bytes.fromhex(_stable_file_sha256(item, max_bytes=MAX_DIST_BYTES)))
    return digest.hexdigest()


def _copy_regular_file(source: Path, destination: Path, *, max_bytes: int) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    with _regular_reader(source) as source_handle:
        before = os.fstat(source_handle.fileno())
        if before.st_size > max_bytes:
            fail(f"artifact file exceeds byte budget: {source}")
        try:
            with destination.open("xb") as destination_handle:
                total = 0
                for block in iter(lambda: source_handle.read(1024 * 1024), b""):
                    total += len(block)
                    if total > max_bytes:
                        fail(f"artifact file exceeds byte budget: {source}")
                    destination_handle.write(block)
        except OSError as exc:
            raise ValueError(f"cannot create artifact snapshot: {destination}") from exc
        after = os.fstat(source_handle.fileno())
    if total != before.st_size or (
        before.st_dev,
        before.st_ino,
        before.st_size,
        before.st_mtime_ns,
        before.st_ctime_ns,
    ) != (
        after.st_dev,
        after.st_ino,
        after.st_size,
        after.st_mtime_ns,
        after.st_ctime_ns,
    ):
        fail(f"artifact file changed while snapshotting: {source}")


def _copy_dist_snapshot(source: Path, destination: Path) -> None:
    inventory = _dist_inventory(source)
    destination.mkdir()
    for relative, item, _ in inventory:
        _copy_regular_file(item, destination / relative, max_bytes=MAX_DIST_BYTES)


def _make_snapshot_read_only(root: Path) -> None:
    entries = sorted(root.rglob("*"), key=lambda item: len(item.parts), reverse=True)
    for item in entries:
        item.chmod(0o555 if item.is_dir() else 0o444)
    root.chmod(0o555)


def _class_stem_from_source(path: str) -> str | None:
    if not path.endswith(".java"):
        return None
    for marker in ("/src/main/java/", "/src/test/java/"):
        if marker in path:
            return path.split(marker, 1)[1][:-5]
    fail(f"unsupported removed Java path: {path}")
    return None


FORBIDDEN_OA_CLASS_STEMS = tuple(
    stem
    for stem in (_class_stem_from_source(path) for path in REMOVED_ENTRY_PATHS)
    if stem is not None
)


def _archive_name_is_safe(name: str) -> bool:
    if not name or "\0" in name or "\\" in name:
        return False
    if name.startswith(("/", "\\")) or re.match(r"^[A-Za-z]:", name):
        return False
    parts = name[:-1].split("/") if name.endswith("/") else name.split("/")
    return bool(parts) and all(part not in ("", ".", "..") for part in parts)


def _validate_zip_infos(
    archive: zipfile.ZipFile, label: str, budget: dict[str, int]
) -> list[zipfile.ZipInfo]:
    infos = archive.infolist()
    names = [info.filename for info in infos]
    if len(names) != len(set(names)):
        fail(f"archive contains duplicate entries: {label}")
    for info in infos:
        if not _archive_name_is_safe(info.filename):
            fail(f"archive contains unsafe entry path: {label}!{info.filename}")
        if info.flag_bits & 0x1:
            fail(f"archive contains encrypted entry: {label}!{info.filename}")
        if info.file_size < 0 or info.compress_size < 0:
            fail(f"archive contains invalid entry size: {label}!{info.filename}")
        if not info.is_dir() and info.file_size > MAX_ZIP_ENTRY_BYTES:
            fail(f"archive entry exceeds byte budget: {label}!{info.filename}")
        if info.file_size and not info.compress_size:
            fail(f"archive entry has invalid compression ratio: {label}!{info.filename}")
        if (
            info.compress_size
            and info.file_size / info.compress_size > MAX_ZIP_COMPRESSION_RATIO
        ):
            fail(f"archive entry exceeds compression-ratio budget: {label}!{info.filename}")
        budget["entries"] += 1
        budget["declared"] += info.file_size
        if budget["entries"] > MAX_ZIP_ENTRIES:
            fail("archive set exceeds total entry budget")
        if budget["declared"] > MAX_ZIP_DECLARED_BYTES:
            fail("archive set exceeds total uncompressed byte budget")
    return infos


def _scan_stream(
    handle: BinaryIO,
    markers: Iterable[bytes],
    *,
    budget: dict[str, int] | None = None,
    entry_limit: int | None = None,
) -> set[bytes]:
    pending = set(markers)
    found: set[bytes] = set()
    overlap = max((len(marker) for marker in pending), default=1) - 1
    tail = b""
    total = 0
    while True:
        block = handle.read(1024 * 1024)
        if not block:
            break
        total += len(block)
        if entry_limit is not None and total > entry_limit:
            fail("archive entry exceeds scan byte budget")
        if budget is not None:
            budget["scanned"] += len(block)
            if budget["scanned"] > MAX_ZIP_SCANNED_BYTES:
                fail("archive set exceeds total decompression scan budget")
        if pending:
            data = tail + block
            matches = {marker for marker in pending if marker in data}
            found.update(matches)
            pending.difference_update(matches)
            tail = data[-overlap:] if overlap else b""
    return found


def _scan_zip_info(
    archive: zipfile.ZipFile,
    info: zipfile.ZipInfo,
    markers: Iterable[bytes],
    budget: dict[str, int],
) -> set[bytes]:
    try:
        with archive.open(info, "r") as handle:
            return _scan_stream(
                handle, markers, budget=budget, entry_limit=MAX_ZIP_ENTRY_BYTES
            )
    except (OSError, RuntimeError, zipfile.BadZipFile) as exc:
        raise ValueError(f"invalid archive entry: {info.filename}") from exc


def _read_zip_bytes(
    archive: zipfile.ZipFile,
    info: zipfile.ZipInfo,
    budget: dict[str, int],
    *,
    limit: int,
    kind: str,
) -> bytes:
    chunks: list[bytes] = []
    total = 0
    try:
        with archive.open(info, "r") as handle:
            while True:
                block = handle.read(1024 * 1024)
                if not block:
                    break
                total += len(block)
                budget["scanned"] += len(block)
                if total > limit:
                    fail(f"{kind} exceeds byte budget: {info.filename}")
                if budget["scanned"] > MAX_ZIP_SCANNED_BYTES:
                    fail("archive set exceeds total decompression scan budget")
                chunks.append(block)
    except (OSError, RuntimeError, zipfile.BadZipFile) as exc:
        raise ValueError(f"invalid {kind} entry: {info.filename}") from exc
    return b"".join(chunks)


def _read_zip_info(
    archive: zipfile.ZipFile,
    info: zipfile.ZipInfo,
    budget: dict[str, int],
    *,
    limit: int,
) -> bytes:
    return _read_zip_bytes(
        archive, info, budget, limit=limit, kind="nested JAR"
    )


def _parse_build_info(data: bytes, label: str) -> dict[str, str]:
    try:
        text = data.decode("utf-8")
    except UnicodeDecodeError as exc:
        raise ValueError(f"build provenance is not UTF-8: {label}") from exc
    properties: dict[str, str] = {}
    for line_number, raw_line in enumerate(text.splitlines(), 1):
        line = raw_line.strip()
        if not line or line.startswith(("#", "!")):
            continue
        # java.util.Properties decodes backslash escapes (including \uXXXX in
        # keys) and joins continuation lines.  This release format deliberately
        # permits neither: otherwise two distinct literal keys here can become
        # one runtime key, with the later attacker-controlled value winning.
        if "\\" in raw_line or "=" not in line:
            fail(f"ambiguous build provenance property at {label}:{line_number}")
        key, value = (part.strip() for part in line.split("=", 1))
        if not key:
            fail(f"empty build provenance key at {label}:{line_number}")
        if key in properties:
            fail(f"duplicate build provenance key {key!r}: {label}")
        if value.upper() == "UNSET":
            fail(f"UNSET build provenance value for {key!r}: {label}")
        properties[key] = value
    return properties


def _verify_build_provenance(
    archive: zipfile.ZipFile,
    by_name: dict[str, zipfile.ZipInfo],
    budget: dict[str, int],
    expected: dict[str, str],
    label: str,
) -> str:
    present = [path for path in BUILD_INFO_PATHS if path in by_name]
    if len(present) != 1:
        fail(
            f"{label} must contain exactly one approved build-info.properties "
            f"at one allowed path (found {len(present)})"
        )
    entry_name = present[0]
    data = _read_zip_bytes(
        archive,
        by_name[entry_name],
        budget,
        limit=MAX_BUILD_INFO_BYTES,
        kind="build-info.properties",
    )
    properties = _parse_build_info(data, f"{label}!{entry_name}")
    mismatches: list[str] = []
    for property_name, provenance_name in BUILD_PROVENANCE_FIELDS.items():
        actual = properties.get(property_name)
        if actual != expected[provenance_name]:
            mismatches.append(property_name)
    if mismatches:
        fail(
            f"{label} build provenance does not match approved source: "
            + ", ".join(mismatches)
        )
    return entry_name


def _class_stem(name: str, prefixes: Iterable[str]) -> str | None:
    """Return an outer runtime class name, including multi-release entries."""

    if not name.endswith(".class"):
        return None
    stem = name[:-6]
    for prefix in prefixes:
        if stem.startswith(prefix):
            stem = stem[len(prefix) :]
            break
    parts = stem.split("/")
    if len(parts) >= 4 and parts[:2] == ["META-INF", "versions"]:
        if not parts[2].isdigit() or int(parts[2]) < 9:
            return None
        stem = "/".join(parts[3:])
    return stem.split("$", 1)[0]


class _ClassReader:
    """Small bounds-checked reader for one JVM classfile."""

    def __init__(self, data: bytes, label: str) -> None:
        self.data = memoryview(data)
        self.position = 0
        self.label = label

    def take(self, size: int) -> bytes:
        end = self.position + size
        if size < 0 or end > len(self.data):
            fail(f"truncated JVM classfile: {self.label}")
        value = bytes(self.data[self.position : end])
        self.position = end
        return value

    def u1(self) -> int:
        return int.from_bytes(self.take(1), "big")

    def u2(self) -> int:
        return int.from_bytes(self.take(2), "big")

    def u4(self) -> int:
        return int.from_bytes(self.take(4), "big")


def _cp_entry(
    pool: list[tuple[int, object] | None],
    index: int,
    tags: set[int],
    label: str,
) -> tuple[int, object]:
    if index <= 0 or index >= len(pool) or pool[index] is None:
        fail(f"invalid JVM constant-pool reference in {label}")
    entry = pool[index]
    assert entry is not None
    if entry[0] not in tags:
        fail(f"invalid JVM constant-pool type in {label}")
    return entry


def _cp_utf8(
    pool: list[tuple[int, object] | None], index: int, label: str
) -> str:
    entry = _cp_entry(pool, index, {1}, label)
    try:
        return bytes(entry[1]).decode("utf-8")
    except (UnicodeDecodeError, TypeError) as exc:
        raise ValueError(f"invalid JVM UTF-8 constant in {label}") from exc


def _parse_annotation_value(
    reader: _ClassReader,
    pool: list[tuple[int, object] | None],
    budget: dict[str, int],
    depth: int,
) -> object:
    budget["values"] += 1
    if budget["values"] > 100_000 or depth > 32:
        fail(f"JVM annotation exceeds structural budget: {reader.label}")
    tag = chr(reader.u1())
    if tag == "s":
        return _cp_utf8(pool, reader.u2(), reader.label)
    if tag in "BCISZ":
        return _cp_entry(pool, reader.u2(), {3}, reader.label)[1]
    if tag == "J":
        return _cp_entry(pool, reader.u2(), {5}, reader.label)[1]
    if tag == "F":
        return _cp_entry(pool, reader.u2(), {4}, reader.label)[1]
    if tag == "D":
        return _cp_entry(pool, reader.u2(), {6}, reader.label)[1]
    if tag == "e":
        return (
            "enum",
            _cp_utf8(pool, reader.u2(), reader.label),
            _cp_utf8(pool, reader.u2(), reader.label),
        )
    if tag == "c":
        return ("class", _cp_utf8(pool, reader.u2(), reader.label))
    if tag == "@":
        return _parse_annotation(reader, pool, budget, depth + 1)
    if tag == "[":
        return tuple(
            _parse_annotation_value(reader, pool, budget, depth + 1)
            for _ in range(reader.u2())
        )
    fail(f"invalid JVM annotation element tag {tag!r}: {reader.label}")


def _parse_annotation(
    reader: _ClassReader,
    pool: list[tuple[int, object] | None],
    budget: dict[str, int],
    depth: int = 0,
) -> tuple[str, dict[str, object]]:
    descriptor = _cp_utf8(pool, reader.u2(), reader.label)
    if not descriptor.startswith("L") or not descriptor.endswith(";"):
        fail(f"invalid JVM annotation descriptor: {reader.label}")
    elements: dict[str, object] = {}
    for _ in range(reader.u2()):
        name = _cp_utf8(pool, reader.u2(), reader.label)
        if name in elements:
            fail(f"duplicate JVM annotation element {name!r}: {reader.label}")
        elements[name] = _parse_annotation_value(
            reader, pool, budget, depth + 1
        )
    return descriptor, elements


_BYTECODE_FIXED_OPERANDS = {
    0x10: 1, 0x11: 2, 0x12: 1, 0x13: 2, 0x14: 2,
    **{opcode: 1 for opcode in range(0x15, 0x1A)},
    **{opcode: 1 for opcode in range(0x36, 0x3B)},
    0x84: 2, 0xA9: 1,
    **{opcode: 2 for opcode in range(0x99, 0xA9)},
    **{opcode: 2 for opcode in range(0xB2, 0xB9)},
    0xB9: 4, 0xBA: 4, 0xBB: 2, 0xBC: 1, 0xBD: 2,
    0xC0: 2, 0xC1: 2, 0xC5: 3, 0xC6: 2, 0xC7: 2,
    0xC8: 4, 0xC9: 4,
}


def _signed_integer(data: bytes) -> int:
    return int.from_bytes(data, "big", signed=True)


def _validate_bytecode(code: bytes, label: str) -> list[int]:
    offsets: list[int] = []
    branches: list[int] = []
    index = 0
    while index < len(code):
        start = index
        offsets.append(start)
        opcode = code[index]
        index += 1
        if opcode > 0xC9 or opcode in {0xCA, 0xFE, 0xFF}:
            fail(f"invalid or reserved JVM bytecode opcode 0x{opcode:02x}: {label}")
        if opcode == 0xAA:  # tableswitch
            padding = (4 - (index % 4)) % 4
            index += padding
            if index + 12 > len(code):
                fail(f"truncated JVM tableswitch instruction: {label}")
            default = _signed_integer(code[index : index + 4])
            low = _signed_integer(code[index + 4 : index + 8])
            high = _signed_integer(code[index + 8 : index + 12])
            index += 12
            count = high - low + 1
            if count < 0 or count > 100_000 or index + 4 * count > len(code):
                fail(f"invalid JVM tableswitch instruction: {label}")
            branches.append(start + default)
            for cursor in range(count):
                branches.append(
                    start + _signed_integer(code[index + 4 * cursor : index + 4 * cursor + 4])
                )
            index += 4 * count
            continue
        if opcode == 0xAB:  # lookupswitch
            padding = (4 - (index % 4)) % 4
            index += padding
            if index + 8 > len(code):
                fail(f"truncated JVM lookupswitch instruction: {label}")
            default = _signed_integer(code[index : index + 4])
            pairs = _signed_integer(code[index + 4 : index + 8])
            index += 8
            if pairs < 0 or pairs > 100_000 or index + 8 * pairs > len(code):
                fail(f"invalid JVM lookupswitch instruction: {label}")
            branches.append(start + default)
            for cursor in range(pairs):
                offset_position = index + 8 * cursor + 4
                branches.append(
                    start
                    + _signed_integer(code[offset_position : offset_position + 4])
                )
            index += 8 * pairs
            continue
        if opcode == 0xC4:  # wide
            if index >= len(code):
                fail(f"truncated JVM wide instruction: {label}")
            widened = code[index]
            operand_count = 5 if widened == 0x84 else 3
            if widened not in {*range(0x15, 0x1A), *range(0x36, 0x3B), 0x84, 0xA9}:
                fail(f"invalid JVM wide instruction: {label}")
            if index + operand_count > len(code):
                fail(f"truncated JVM wide instruction: {label}")
            index += operand_count
            continue
        operand_count = _BYTECODE_FIXED_OPERANDS.get(opcode, 0)
        if index + operand_count > len(code):
            fail(f"truncated JVM bytecode instruction: {label}")
        if opcode in {*range(0x99, 0xA9), 0xC6, 0xC7}:
            branches.append(start + _signed_integer(code[index : index + 2]))
        elif opcode in {0xC8, 0xC9}:
            branches.append(start + _signed_integer(code[index : index + 4]))
        index += operand_count
    boundaries = set(offsets)
    if any(target not in boundaries for target in branches):
        fail(f"JVM bytecode branch target is not an instruction boundary: {label}")
    return [code[offset] for offset in offsets]


def _validate_code_attribute(
    body: bytes, pool: list[tuple[int, object] | None], label: str
) -> dict[str, object]:
    reader = _ClassReader(body, f"{label}:Code")
    max_stack = reader.u2()
    max_locals = reader.u2()
    code_length = reader.u4()
    if code_length < 1 or code_length > 65_535:
        fail(f"invalid JVM Code attribute length: {label}")
    code = reader.take(code_length)
    opcodes = _validate_bytecode(code, label)
    for _ in range(reader.u2()):
        start_pc = reader.u2()
        end_pc = reader.u2()
        handler_pc = reader.u2()
        catch_type = reader.u2()
        if not (start_pc < end_pc <= code_length and handler_pc < code_length):
            fail(f"invalid JVM Code exception table: {label}")
        if catch_type:
            _cp_entry(pool, catch_type, {7}, label)
    for _ in range(reader.u2()):
        nested_name = _cp_utf8(pool, reader.u2(), reader.label)
        if nested_name == "Code":
            fail(f"nested JVM Code attribute is invalid: {label}")
        reader.take(reader.u4())
    if reader.position != len(reader.data):
        fail(f"trailing data in JVM Code attribute: {label}")
    return {
        "maxStack": max_stack,
        "maxLocals": max_locals,
        "opcodes": opcodes,
    }


def _parse_attributes(
    reader: _ClassReader,
    pool: list[tuple[int, object] | None],
    count: int,
) -> tuple[
    list[tuple[str, dict[str, object], bool]],
    set[str],
    dict[str, object] | None,
]:
    annotations: list[tuple[str, dict[str, object], bool]] = []
    names: set[str] = set()
    annotation_attributes: set[str] = set()
    code_info: dict[str, object] | None = None
    budget = {"values": 0}
    for _ in range(count):
        name = _cp_utf8(pool, reader.u2(), reader.label)
        length = reader.u4()
        body = reader.take(length)
        if name == "Code" and name in names:
            fail(f"duplicate JVM Code attribute: {reader.label}")
        names.add(name)
        if name == "Code":
            code_info = _validate_code_attribute(body, pool, reader.label)
            continue
        if name not in {
            "RuntimeVisibleAnnotations",
            "RuntimeInvisibleAnnotations",
        }:
            continue
        if name in annotation_attributes:
            fail(f"duplicate JVM {name} attribute: {reader.label}")
        annotation_attributes.add(name)
        attribute_reader = _ClassReader(body, f"{reader.label}:{name}")
        visible = name == "RuntimeVisibleAnnotations"
        annotations.extend(
            (*_parse_annotation(attribute_reader, pool, budget), visible)
            for _ in range(attribute_reader.u2())
        )
        if attribute_reader.position != len(attribute_reader.data):
            fail(f"trailing data in JVM {name} attribute: {reader.label}")
    return annotations, names, code_info


def _parse_members(
    reader: _ClassReader,
    pool: list[tuple[int, object] | None],
    count: int,
    *,
    methods: bool,
) -> list[dict[str, object]]:
    members: list[dict[str, object]] = []
    for _ in range(count):
        access_flags = reader.u2()
        name = _cp_utf8(pool, reader.u2(), reader.label)
        descriptor = _cp_utf8(pool, reader.u2(), reader.label)
        if not _valid_descriptor(descriptor, methods=methods):
            fail(f"invalid JVM {'method' if methods else 'field'} descriptor: {reader.label}")
        annotations, attribute_names, code_info = _parse_attributes(
            reader, pool, reader.u2()
        )
        has_code = "Code" in attribute_names
        if methods:
            abstract_or_native = bool(access_flags & (0x0400 | 0x0100))
            if has_code == abstract_or_native:
                fail(f"invalid JVM method Code attribute contract: {reader.label}#{name}")
            if has_code and (
                not isinstance(code_info, dict)
                or not isinstance(code_info.get("maxLocals"), int)
                or code_info["maxLocals"]
                < _method_argument_slots(
                    descriptor, is_static=bool(access_flags & 0x0008)
                )
            ):
                fail(f"JVM method arguments do not fit max_locals: {reader.label}#{name}")
        elif has_code:
            fail(f"JVM field must not contain a Code attribute: {reader.label}#{name}")
        members.append(
            {
                "accessFlags": access_flags,
                "name": name,
                "descriptor": descriptor,
                "annotations": annotations,
                "attributeNames": attribute_names,
                "code": code_info,
            }
        )
    return members


def _descriptor_type(descriptor: str, index: int, *, allow_void: bool) -> int | None:
    if index >= len(descriptor):
        return None
    marker = descriptor[index]
    if marker == "V":
        return index + 1 if allow_void else None
    if marker in "BCDFIJSZ":
        return index + 1
    if marker == "L":
        end = descriptor.find(";", index + 1)
        if end <= index + 1:
            return None
        name = descriptor[index + 1 : end]
        if any(char in name for char in ".;[") or "//" in name:
            return None
        return end + 1
    if marker == "[":
        while index < len(descriptor) and descriptor[index] == "[":
            index += 1
        return _descriptor_type(descriptor, index, allow_void=False)
    return None


def _valid_descriptor(descriptor: str, *, methods: bool) -> bool:
    if not methods:
        return _descriptor_type(descriptor, 0, allow_void=False) == len(descriptor)
    if not descriptor.startswith("("):
        return False
    index = 1
    while index < len(descriptor) and descriptor[index] != ")":
        next_index = _descriptor_type(descriptor, index, allow_void=False)
        if next_index is None:
            return False
        index = next_index
    if index >= len(descriptor) or descriptor[index] != ")":
        return False
    return _descriptor_type(descriptor, index + 1, allow_void=True) == len(descriptor)


def _method_argument_slots(descriptor: str, *, is_static: bool) -> int:
    slots = 0 if is_static else 1
    index = 1
    while descriptor[index] != ")":
        marker = descriptor[index]
        if marker == "[":
            while descriptor[index] == "[":
                index += 1
            if descriptor[index] == "L":
                index = descriptor.index(";", index) + 1
            else:
                index += 1
            slots += 1
        elif marker == "L":
            index = descriptor.index(";", index) + 1
            slots += 1
        else:
            index += 1
            slots += 2 if marker in "JD" else 1
    return slots


def _parse_classfile(data: bytes, expected_name: str, label: str) -> dict[str, object]:
    """Validate one complete classfile and decode class/method annotations."""

    if len(data) > MAX_CLASSFILE_BYTES:
        fail(f"JVM classfile exceeds byte budget: {label}")
    reader = _ClassReader(data, label)
    if reader.u4() != 0xCAFEBABE:
        fail(f"invalid JVM classfile magic: {label}")
    minor = reader.u2()
    major = reader.u2()
    if (major, minor) != (61, 0):
        fail(
            f"JVM classfile is not the approved non-preview Java 17 version: "
            f"{label} (major={major}, minor={minor})"
        )
    constant_pool_count = reader.u2()
    if constant_pool_count <= 1:
        fail(f"invalid JVM constant pool: {label}")
    pool: list[tuple[int, object] | None] = [None] * constant_pool_count
    index = 1
    while index < constant_pool_count:
        tag = reader.u1()
        if tag == 1:
            value: object = reader.take(reader.u2())
        elif tag in (3, 4):
            value = reader.take(4)
        elif tag in (5, 6):
            value = reader.take(8)
            pool[index] = (tag, value)
            index += 2
            if index > constant_pool_count:
                fail(f"invalid two-slot JVM constant: {label}")
            continue
        elif tag in (7, 8, 16, 19, 20):
            value = reader.u2()
        elif tag in (9, 10, 11, 12, 17, 18):
            value = (reader.u2(), reader.u2())
        elif tag == 15:
            value = (reader.u1(), reader.u2())
        else:
            fail(f"unknown JVM constant-pool tag {tag}: {label}")
        pool[index] = (tag, value)
        index += 1

    # Validate all typed references, not only this_class.  This catches a
    # structurally complete but internally malformed decoy classfile.
    for entry in pool[1:]:
        if entry is None:
            continue
        tag, value = entry
        if tag in (7, 8, 16, 19, 20):
            _cp_entry(pool, int(value), {1}, label)
        elif tag in (9, 10, 11):
            class_index, name_type_index = value  # type: ignore[misc]
            _cp_entry(pool, class_index, {7}, label)
            _cp_entry(pool, name_type_index, {12}, label)
        elif tag == 12:
            name_index, descriptor_index = value  # type: ignore[misc]
            _cp_entry(pool, name_index, {1}, label)
            _cp_entry(pool, descriptor_index, {1}, label)
        elif tag == 15:
            reference_kind, reference_index = value  # type: ignore[misc]
            if reference_kind not in range(1, 10):
                fail(f"invalid JVM method-handle kind: {label}")
            _cp_entry(pool, reference_index, {9, 10, 11}, label)
        elif tag in (17, 18):
            _, name_type_index = value  # type: ignore[misc]
            _cp_entry(pool, name_type_index, {12}, label)

    access_flags = reader.u2()
    this_class = _cp_entry(pool, reader.u2(), {7}, label)
    this_name_entry = _cp_entry(pool, int(this_class[1]), {1}, label)
    try:
        this_name = bytes(this_name_entry[1]).decode("ascii")
    except (UnicodeDecodeError, TypeError) as exc:
        raise ValueError(f"invalid JVM internal class name: {label}") from exc
    if this_name != expected_name:
        fail(
            f"JVM classfile internal name does not match archive entry: "
            f"{label} ({this_name!r})"
        )
    super_class = reader.u2()
    if super_class:
        _cp_entry(pool, super_class, {7}, label)
    for _ in range(reader.u2()):
        _cp_entry(pool, reader.u2(), {7}, label)
    _parse_members(reader, pool, reader.u2(), methods=False)  # fields
    methods = _parse_members(reader, pool, reader.u2(), methods=True)
    annotations, _, _ = _parse_attributes(reader, pool, reader.u2())
    if reader.position != len(reader.data):
        fail(f"trailing data after complete JVM classfile: {label}")
    return {
        "utf8Constants": {
            bytes(entry[1])
            for entry in pool[1:]
            if entry is not None and entry[0] == 1
        },
        "annotations": annotations,
        "methods": methods,
        "accessFlags": access_flags,
    }


def _parse_manifest(data: bytes, label: str) -> dict[str, str]:
    if b"\0" in data:
        fail(f"executable JAR manifest contains NUL: {label}")
    try:
        lines = data.decode("utf-8").splitlines()
    except UnicodeDecodeError as exc:
        raise ValueError(f"executable JAR manifest is not UTF-8: {label}") from exc
    unfolded: list[str] = []
    for line in lines:
        if not line:
            break
        if line.startswith(" "):
            if not unfolded:
                fail(f"invalid executable JAR manifest continuation: {label}")
            unfolded[-1] += line[1:]
        else:
            unfolded.append(line)
    attributes: dict[str, str] = {}
    original_names: dict[str, str] = {}
    for line in unfolded:
        if ": " not in line:
            fail(f"invalid executable JAR manifest attribute: {label}")
        name, value = line.split(": ", 1)
        if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_-]*", name):
            fail(f"invalid executable JAR manifest key: {label}")
        normalized = name.lower()
        if normalized in attributes:
            fail(
                f"duplicate executable JAR manifest key "
                f"{original_names[normalized]!r}: {label}"
            )
        attributes[normalized] = value
        original_names[normalized] = name
    return attributes


def _verify_spring_boot_structure(
    archive: zipfile.ZipFile,
    by_name: dict[str, zipfile.ZipInfo],
    budget: dict[str, int],
    package: str,
    label: str,
) -> dict[str, str]:
    manifest_names = [
        name for name in by_name if name.upper() == "META-INF/MANIFEST.MF"
    ]
    if manifest_names != ["META-INF/MANIFEST.MF"]:
        fail(
            f"{label} must contain exactly one executable META-INF/MANIFEST.MF"
        )
    manifest = _parse_manifest(
        _read_zip_bytes(
            archive,
            by_name["META-INF/MANIFEST.MF"],
            budget,
            limit=MAX_MANIFEST_BYTES,
            kind="executable JAR manifest",
        ),
        f"{label}!META-INF/MANIFEST.MF",
    )
    expected_start = START_CLASSES.get(package)
    if expected_start is None:
        fail(f"unsupported Spring Boot JAR structure contract: {package}")
    if manifest.get("main-class") != SPRING_BOOT_MAIN_CLASS:
        fail(f"{label} has an unapproved Spring Boot Main-Class")
    if manifest.get("start-class") != expected_start:
        fail(f"{label} has an unapproved Spring Boot Start-Class")

    runtime_entries = (
        (f"{SPRING_BOOT_LAUNCHER_CLASS}.class", SPRING_BOOT_LAUNCHER_CLASS),
        (
            f"BOOT-INF/classes/{expected_start.replace('.', '/')}.class",
            expected_start.replace(".", "/"),
        ),
    )
    for entry_name, internal_name in runtime_entries:
        info = by_name.get(entry_name)
        if info is None:
            fail(f"{label} misses executable runtime class: {entry_name}")
        class_info = _parse_classfile(
            _read_zip_bytes(
                archive,
                info,
                budget,
                limit=MAX_CLASSFILE_BYTES,
                kind="JVM classfile",
            ),
            internal_name,
            entry_name,
        )
        if not class_info.get("accessFlags", 0) & 0x0001:
            fail(f"{label} executable runtime class is not public: {entry_name}")
        mains = [
            method
            for method in class_info["methods"]
            if method.get("name") == "main"
            and method.get("descriptor") == "([Ljava/lang/String;)V"
            and isinstance(method.get("accessFlags"), int)
            and method["accessFlags"] & 0x0001  # public
            and method["accessFlags"] & 0x0008  # static
            and not method["accessFlags"] & (0x0400 | 0x0100)
            and "Code" in method.get("attributeNames", set())
            and isinstance(method.get("code"), dict)
            and method["code"].get("maxLocals", 0) >= 1
            and method["code"].get("opcodes")
            and method["code"]["opcodes"][-1] == 0xB1
        ]
        if len(mains) != 1:
            fail(
                f"{label} executable runtime class must declare exactly one "
                f"public static concrete main(String[]) method: {entry_name}"
            )
    return {
        "manifestPath": "META-INF/MANIFEST.MF",
        "mainClass": SPRING_BOOT_MAIN_CLASS,
        "startClass": expected_start,
    }


def _annotation_for(
    annotations: object, descriptor: str, label: str
) -> tuple[str, dict[str, object], bool] | None:
    if not isinstance(annotations, list):
        fail(f"invalid decoded JVM annotations: {label}")
    matches = [
        annotation
        for annotation in annotations
        if annotation[0] == descriptor and annotation[2]
    ]
    if len(matches) > 1:
        fail(f"duplicate required JVM annotation {descriptor}: {label}")
    return matches[0] if matches else None


def _annotation_strings(
    annotation: tuple[str, dict[str, object], bool] | None,
    element_names: Iterable[str],
) -> set[str]:
    if annotation is None:
        return set()
    values: set[str] = set()
    for element_name in element_names:
        value = annotation[1].get(element_name)
        if isinstance(value, str):
            values.add(value)
        elif isinstance(value, tuple):
            values.update(item for item in value if isinstance(item, str))
    return values


def _mapping_paths(
    annotations: object, descriptor: str, label: str
) -> set[str]:
    return _annotation_strings(
        _annotation_for(annotations, descriptor, label), ("value", "path")
    )


def _verify_controller_contract(
    class_info: dict[str, object], package: str, label: str
) -> set[bytes]:
    class_annotations = class_info.get("annotations")
    if not class_info.get("accessFlags", 0) & 0x0001:
        fail(f"{label} controller class is not public")
    if _annotation_for(class_annotations, REST_CONTROLLER, label) is None:
        fail(f"{label} is not annotated with @RestController")
    if package == "com/erp/oa":
        base_path = "/signTask/onboard"
        methods_contract = OA_METHOD_CONTRACT
    elif package == "com/erp/system":
        base_path = "/user/sign-profile"
        methods_contract = SYSTEM_METHOD_CONTRACT
    else:
        fail(f"unsupported controller annotation contract: {package}")
    if base_path not in _mapping_paths(
        class_annotations, REQUEST_MAPPING, label
    ):
        fail(f"{label} misses approved class-level @RequestMapping {base_path}")

    methods = class_info.get("methods")
    if not isinstance(methods, list):
        fail(f"invalid decoded JVM methods: {label}")
    found = {base_path.encode("utf-8")}
    for method_name, mapping, route, security, security_value in methods_contract:
        candidates = [method for method in methods if method.get("name") == method_name]
        accepted = False
        for method in candidates:
            access_flags = method.get("accessFlags")
            attribute_names = method.get("attributeNames")
            if (
                not isinstance(access_flags, int)
                or not access_flags & 0x0001  # public
                or access_flags & 0x0008  # static
                or access_flags & (0x0400 | 0x0100)  # abstract or native
                or not isinstance(attribute_names, set)
                or "Code" not in attribute_names
            ):
                continue
            method_label = f"{label}#{method_name}"
            annotations = method.get("annotations")
            if route not in _mapping_paths(annotations, mapping, method_label):
                continue
            security_annotation = _annotation_for(
                annotations, security, method_label
            )
            if security_annotation is None:
                continue
            if security_value is not None and security_value not in _annotation_strings(
                security_annotation, ("value",)
            ):
                continue
            accepted = True
            break
        if not accepted:
            fail(
                f"{label} misses concrete annotated endpoint method "
                f"{method_name} {route} with its required security annotation"
            )
        found.add(route.encode("utf-8"))
    return found


def _verify_nested_libraries(
    archive: zipfile.ZipFile,
    infos: Iterable[zipfile.ZipInfo],
    label: str,
    budget: dict[str, int],
) -> tuple[set[str], set[bytes]]:
    removed: set[str] = set()
    forbidden_routes: set[bytes] = set()
    for info in infos:
        name = info.filename
        if not (
            name.startswith("BOOT-INF/lib/")
            and name.endswith(".jar")
            and "/" not in name[len("BOOT-INF/lib/") :]
        ):
            continue
        nested_bytes = _read_zip_info(
            archive, info, budget, limit=MAX_NESTED_JAR_BYTES
        )
        nested_label = f"{label}!{name}"
        try:
            with zipfile.ZipFile(io.BytesIO(nested_bytes)) as nested:
                nested_infos = _validate_zip_infos(nested, nested_label, budget)
                for nested_info in nested_infos:
                    if nested_info.is_dir():
                        continue
                    stem = _class_stem(
                        nested_info.filename,
                        ("BOOT-INF/classes/", "BOOT-INF/test-classes/", "WEB-INF/classes/"),
                    )
                    if stem in FORBIDDEN_OA_CLASS_STEMS:
                        removed.add(stem)
                    forbidden_routes.update(
                        _scan_zip_info(
                            nested, nested_info, FORBIDDEN_OA_ROUTES, budget
                        )
                    )
        except zipfile.BadZipFile as exc:
            raise ValueError(f"invalid nested executable dependency: {nested_label}") from exc
    return removed, forbidden_routes


def _verify_jar(
    path: Path,
    required_classes: Iterable[str],
    required_markers: Iterable[bytes],
    package: str,
    *,
    expected_provenance: dict[str, str] | None = None,
    check_removed_oa: bool = False,
) -> dict:
    if path.is_symlink() or not path.is_file():
        fail(f"artifact JAR is missing or is a symlink: {path}")
    budget = {"entries": 0, "declared": 0, "scanned": 0}
    required_classes = tuple(required_classes)
    required_markers = tuple(required_markers)
    required_found: set[bytes] = set()
    forbidden_found: set[bytes] = set()
    removed: set[str] = set()
    runtime_classes: set[str] = set()
    build_info_path: str | None = None
    spring_boot_structure: dict[str, str] | None = None
    try:
        with zipfile.ZipFile(path) as archive:
            infos = _validate_zip_infos(archive, path.name, budget)
            by_name = {info.filename: info for info in infos if not info.is_dir()}
            spring_boot_structure = _verify_spring_boot_structure(
                archive, by_name, budget, package, path.name
            )
            if expected_provenance is not None:
                build_info_path = _verify_build_provenance(
                    archive, by_name, budget, expected_provenance, path.name
                )
            controller_classes = [
                stem
                for stem in required_classes
                if "/controller/" in stem and stem.endswith("Controller")
            ]
            if len(controller_classes) != 1:
                fail(f"invalid required controller contract for {path.name}")
            route_controller = controller_classes[0]
            missing_classes: list[str] = []
            for required_class in required_classes:
                entry_name = f"BOOT-INF/classes/{required_class}.class"
                info = by_name.get(entry_name)
                if info is None:
                    missing_classes.append(required_class)
                    continue
                class_bytes = _read_zip_bytes(
                    archive,
                    info,
                    budget,
                    limit=MAX_CLASSFILE_BYTES,
                    kind="JVM classfile",
                )
                class_info = _parse_classfile(class_bytes, required_class, entry_name)
                runtime_classes.add(required_class)
                if required_class == route_controller:
                    required_found.update(
                        _verify_controller_contract(
                            class_info, package, entry_name
                        )
                    )

            for info in infos:
                if info.is_dir():
                    continue
                name = info.filename
                if name.startswith("BOOT-INF/classes/"):
                    stem = _class_stem(name, ("BOOT-INF/classes/",))
                    if stem:
                        runtime_classes.add(stem)
                if check_removed_oa and (
                    name.startswith(("BOOT-INF/classes/", "BOOT-INF/test-classes/"))
                    or _class_stem(name, ()) is not None
                    and name.startswith("META-INF/versions/")
                ):
                    forbidden_found.update(
                        _scan_zip_info(archive, info, FORBIDDEN_OA_ROUTES, budget)
                    )

                if check_removed_oa:
                    old_stem = _class_stem(
                        name, ("BOOT-INF/classes/", "BOOT-INF/test-classes/")
                    )
                    if old_stem in FORBIDDEN_OA_CLASS_STEMS:
                        removed.add(old_stem)

            if check_removed_oa:
                nested_removed, nested_routes = _verify_nested_libraries(
                    archive, infos, path.name, budget
                )
                removed.update(nested_removed)
                forbidden_found.update(nested_routes)
    except (OSError, zipfile.BadZipFile, RuntimeError, NotImplementedError) as exc:
        raise ValueError(f"invalid Spring Boot JAR structure: {path}") from exc

    missing_classes = sorted(missing_classes)
    problems = []
    if missing_classes:
        problems.append(
            "misses required 20260718 classes from BOOT-INF/classes: "
            + ", ".join(missing_classes)
        )
    if removed:
        problems.append("contains superseded onboarding classes: " + ", ".join(sorted(removed)))
    if forbidden_found:
        problems.append(
            "contains superseded preview/initiate routes: "
            + ", ".join(marker.decode("ascii") for marker in sorted(forbidden_found))
        )
    missing_markers = [
        marker.decode("utf-8")
        for marker in required_markers
        if marker not in required_found
    ]
    if missing_markers:
        problems.append(
            "misses required 20260718 route markers from runtime controller annotations: "
            + ", ".join(missing_markers)
        )
    if problems:
        fail(f"{path.name} " + "; ".join(problems))
    result = {
        "classCount": len(runtime_classes),
        "entryCount": budget["entries"],
        "scannedBytes": budget["scanned"],
        "springBootStructure": {
            **(spring_boot_structure or {}),
            "verificationScope": "static-structural-only",
            "requiresIsolatedStartupHealthEvidence": True,
        },
    }
    if build_info_path is not None:
        result["buildInfoPath"] = build_info_path
    return result


class _IndexScriptParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.references: list[str] = []
        self.inline_scripts: list[tuple[str, bool]] = []
        self.base_hrefs: list[str] = []
        self._inline: list[str] | None = None
        self._inline_module = False
        self._inert_stack: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        normalized_tag = tag.lower()
        if normalized_tag in {"template", "noscript"}:
            self._inert_stack.append(normalized_tag)
            return
        if self._inert_stack:
            return
        attribute_names = [name.lower() for name, _ in attrs]
        if normalized_tag in {"script", "base"} and len(attribute_names) != len(
            set(attribute_names)
        ):
            fail(f"frontend index contains duplicate attributes on <{normalized_tag}>")
        attributes = {name.lower(): value for name, value in attrs}
        if normalized_tag == "script":
            if not _script_is_executable(attributes.get("type")):
                self._inline = None
                return
            source = attributes.get("src")
            if source:
                self.references.append(source)
                self._inline = None
            else:
                self._inline = []
                self._inline_module = (
                    (attributes.get("type") or "").strip().lower() == "module"
                )
        elif normalized_tag == "base":
            href = attributes.get("href")
            if href is not None:
                self.base_hrefs.append(href)

    def handle_data(self, data: str) -> None:
        if not self._inert_stack and self._inline is not None:
            self._inline.append(data)

    def handle_endtag(self, tag: str) -> None:
        normalized_tag = tag.lower()
        if normalized_tag in {"template", "noscript"}:
            if not self._inert_stack or self._inert_stack[-1] != normalized_tag:
                fail(f"frontend index has mismatched inert HTML tag </{normalized_tag}>")
            self._inert_stack.pop()
            return
        if self._inert_stack:
            return
        if normalized_tag == "script" and self._inline is not None:
            self.inline_scripts.append(
                ("".join(self._inline), self._inline_module)
            )
            self._inline = None

    def handle_startendtag(
        self, tag: str, attrs: list[tuple[str, str | None]]
    ) -> None:
        normalized_tag = tag.lower()
        if normalized_tag in {"script", "template", "noscript"}:
            fail(f"frontend index uses ambiguous self-closing <{normalized_tag}/> tag")
        super().handle_startendtag(tag, attrs)

    def close(self) -> None:
        super().close()
        if self._inert_stack:
            fail(
                "frontend index has unclosed inert HTML tag <"
                + self._inert_stack[-1]
                + ">"
            )


def _script_is_executable(value: str | None) -> bool:
    if value is None or not value.strip():
        return True
    media_type = value.split(";", 1)[0].strip().lower()
    return media_type in {
        "module",
        "application/javascript",
        "text/ecmascript",
        "application/ecmascript",
        "application/x-ecmascript",
        "application/x-javascript",
        "text/javascript",
        "text/javascript1.0",
        "text/javascript1.1",
        "text/javascript1.2",
        "text/javascript1.3",
        "text/javascript1.4",
        "text/javascript1.5",
        "text/jscript",
        "text/livescript",
        "text/x-ecmascript",
        "text/x-javascript",
    }


def _has_js_suffix(value: str) -> bool:
    return value.lower().endswith(JS_SUFFIXES)


def _normalize_js_reference(
    reference: str, base: PurePosixPath, *, strict: bool
) -> str | None:
    parsed = urlsplit(reference)
    if parsed.scheme or parsed.netloc or reference.startswith("//"):
        if strict:
            fail(f"frontend index references external executable JavaScript: {reference}")
        return None
    decoded = unquote(parsed.path)
    if "\\" in decoded or "\0" in decoded:
        if strict:
            fail(f"frontend index contains unsafe JavaScript reference: {reference}")
        return None
    if not _has_js_suffix(decoded):
        return None
    candidate = PurePosixPath(decoded.lstrip("/")) if decoded.startswith("/") else base / decoded
    if candidate.is_absolute() or any(part in ("", ".", "..") for part in candidate.parts):
        if strict:
            fail(f"frontend index contains unsafe JavaScript reference: {reference}")
        return None
    return candidate.as_posix()


def _explicit_js_references(
    data: bytes, base: PurePosixPath, label: str = "JavaScript dependency discovery"
) -> set[str]:
    """Resolve only syntactic dependency expressions, never comments/strings-as-code."""

    found: set[str] = set()
    tokens = _js_tokens(data, label)

    def add(value: str) -> None:
        normalized = _normalize_js_reference(value, base, strict=False)
        if normalized:
            found.add(normalized)

    for index, (kind, value) in enumerate(tokens):
        if kind == "identifier" and value in {"import", "require"}:
            if (
                index + 2 < len(tokens)
                and tokens[index + 1][1] == "("
                and tokens[index + 2][0] == "string"
            ):
                add(tokens[index + 2][1])
                continue
            if value == "import" and index + 1 < len(tokens):
                if tokens[index + 1][0] == "string":
                    add(tokens[index + 1][1])
                    continue
                for cursor in range(index + 1, min(index + 64, len(tokens))):
                    if tokens[cursor][1] == ";":
                        break
                    if (
                        tokens[cursor] == ("identifier", "from")
                        and cursor + 1 < len(tokens)
                        and tokens[cursor + 1][0] == "string"
                    ):
                        add(tokens[cursor + 1][1])
                        break
        if (
            kind == "identifier"
            and value == "new"
            and index + 3 < len(tokens)
            and tokens[index + 1] in {
                ("identifier", "Worker"),
                ("identifier", "SharedWorker"),
            }
            and tokens[index + 2][1] == "("
            and tokens[index + 3][0] == "string"
        ):
            add(tokens[index + 3][1])
        if (
            value == "."
            and index + 3 < len(tokens)
            and tokens[index + 1] == ("identifier", "src")
            and tokens[index + 2][1] == "="
            and tokens[index + 3][0] == "string"
        ):
            add(tokens[index + 3][1])
    return found


def _runtime_mapped_js(
    data: bytes,
    js_files: set[str],
    label: str = "JavaScript runtime dependency discovery",
) -> set[str]:
    """Resolve hashed Webpack chunks declared by an inline/external runtime."""

    found: set[str] = set()
    tokens = _js_tokens(data, label)
    strings = {value for kind, value in tokens if kind == "string"}
    has_create_script = any(
        tokens[index] == ("identifier", "createElement")
        and index + 2 < len(tokens)
        and tokens[index + 1][1] == "("
        and tokens[index + 2] == ("string", "script")
        for index in range(len(tokens))
    )
    has_src_assignment = any(
        tokens[index][1] == "."
        and index + 2 < len(tokens)
        and tokens[index + 1] == ("identifier", "src")
        and tokens[index + 2][1] == "="
        for index in range(len(tokens))
    )
    has_append_child = ("identifier", "appendChild") in tokens
    if not (
        has_create_script
        and has_src_assignment
        and has_append_child
        and any("static/js/" in value for value in strings)
    ):
        return found
    for relative in js_files:
        filename = PurePosixPath(relative).name
        match = re.fullmatch(r"(.+)\.([0-9a-fA-F]{6,64})\.(?:js|mjs|cjs)", filename)
        if not match:
            continue
        stem, content_hash = match.groups()
        mapped = any(
            tokens[index] == ("string", stem)
            and index + 2 < len(tokens)
            and tokens[index + 1][1] == ":"
            and tokens[index + 2] == ("string", content_hash)
            for index in range(len(tokens))
        )
        if mapped:
            found.add(relative)
    return found


def _check_javascript_syntax(
    data: bytes,
    label: str,
    *,
    mode: str,
    deadline: float,
) -> None:
    if b"\0" in data:
        fail(f"executable JavaScript contains a NUL byte: {label}")
    node = shutil.which("node")
    if node is None:
        fail("Node.js is required for fail-closed frontend syntax verification")
    modes = (mode,) if mode in {"script", "module"} else ("script", "module")
    errors: list[str] = []
    for syntax_mode in modes:
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            fail("frontend JavaScript syntax verification exceeded total timeout")
        command = [node]
        if syntax_mode == "module":
            command.append("--input-type=module")
        command.append("--check")
        try:
            completed = subprocess.run(
                command,
                input=data,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.PIPE,
                check=False,
                timeout=min(JS_SYNTAX_TIMEOUT_SECONDS, remaining),
            )
        except (OSError, subprocess.TimeoutExpired) as exc:
            raise ValueError(
                f"JavaScript syntax verification failed closed: {label}"
            ) from exc
        if completed.returncode == 0:
            return
        errors.append(
            completed.stderr.decode("utf-8", errors="replace")[-1000:].strip()
        )
    detail = errors[-1] if errors else "unknown Node.js parser failure"
    fail(f"invalid executable JavaScript syntax: {label}: {detail}")


def _consume_js_string(text: str, start: int) -> tuple[int, str]:
    quote = text[start]
    index = start + 1
    value: list[str] = []
    escapes = {
        "b": "\b",
        "f": "\f",
        "n": "\n",
        "r": "\r",
        "t": "\t",
        "v": "\v",
        "0": "\0",
    }
    while index < len(text):
        char = text[index]
        if char == quote:
            return index + 1, "".join(value)
        if char != "\\":
            value.append(char)
            index += 1
            continue
        index += 1
        if index >= len(text):
            break
        escaped = text[index]
        if escaped in "\r\n":
            if escaped == "\r" and index + 1 < len(text) and text[index + 1] == "\n":
                index += 1
        elif escaped == "x" and index + 2 < len(text):
            try:
                value.append(chr(int(text[index + 1 : index + 3], 16)))
                index += 2
            except ValueError:
                value.append(escaped)
        elif escaped == "u":
            if index + 1 < len(text) and text[index + 1] == "{":
                end = text.find("}", index + 2)
                try:
                    value.append(chr(int(text[index + 2 : end], 16)))
                    index = end
                except (ValueError, OverflowError):
                    value.append(escaped)
            elif index + 4 < len(text):
                try:
                    value.append(chr(int(text[index + 1 : index + 5], 16)))
                    index += 4
                except ValueError:
                    value.append(escaped)
            else:
                value.append(escaped)
        else:
            value.append(escapes.get(escaped, escaped))
        index += 1
    return len(text), "".join(value)


def _skip_js_template(text: str, start: int) -> tuple[int, str | None]:
    index = start + 1
    value: list[str] = []
    has_expression = False
    while index < len(text):
        char = text[index]
        if char == "\\":
            if index + 1 >= len(text):
                return len(text), None
            escaped = text[index + 1]
            value.append(
                {"n": "\n", "r": "\r", "t": "\t"}.get(escaped, escaped)
            )
            index += 2
            continue
        if char == "`":
            return index + 1, None if has_expression else "".join(value)
        if char == "$" and index + 1 < len(text) and text[index + 1] == "{":
            has_expression = True
            index = _skip_js_template_expression(text, index + 2)
            continue
        value.append(char)
        index += 1
    return len(text), None


def _skip_js_template_expression(text: str, start: int) -> int:
    depth = 1
    index = start
    while index < len(text) and depth:
        char = text[index]
        if char in "'\"":
            index, _ = _consume_js_string(text, index)
            continue
        if char == "`":
            index, _ = _skip_js_template(text, index)
            continue
        if char == "/" and index + 1 < len(text):
            if text[index + 1] == "/":
                newline = text.find("\n", index + 2)
                index = len(text) if newline < 0 else newline + 1
                continue
            if text[index + 1] == "*":
                end = text.find("*/", index + 2)
                index = len(text) if end < 0 else end + 2
                continue
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
        index += 1
    return index


def _js_regex_can_start(tokens: list[tuple[str, str]]) -> bool:
    if not tokens:
        return True
    kind, value = tokens[-1]
    if value in {
        "(", "[", "{", "=", ":", ",", ";", "!", "?", "=>",
        "+", "-", "*", "%", "&", "|", "^", "~", "<", ">",
    }:
        return True
    return kind == "identifier" and value in {
        "return", "throw", "case", "delete", "typeof", "void", "new",
        "in", "instanceof", "yield", "await", "else", "do",
    }


def _skip_js_regex(text: str, start: int) -> int:
    index = start + 1
    in_class = False
    while index < len(text):
        char = text[index]
        if char == "\\":
            index += 2
            continue
        if char in "\r\n":
            return index
        if char == "[":
            in_class = True
        elif char == "]":
            in_class = False
        elif char == "/" and not in_class:
            index += 1
            while index < len(text) and text[index].isalpha():
                index += 1
            return index
        index += 1
    return len(text)


def _js_tokens(data: bytes, label: str) -> list[tuple[str, str]]:
    if b"\0" in data:
        fail(f"executable JavaScript contains a NUL byte: {label}")
    try:
        text = data.decode("utf-8")
    except UnicodeDecodeError as exc:
        raise ValueError(f"executable JavaScript is not UTF-8: {label}") from exc
    tokens: list[tuple[str, str]] = []
    index = 0
    while index < len(text):
        char = text[index]
        if char.isspace():
            index += 1
            continue
        if char == "/" and index + 1 < len(text) and text[index + 1] == "/":
            newline = text.find("\n", index + 2)
            index = len(text) if newline < 0 else newline + 1
            continue
        if char == "/" and index + 1 < len(text) and text[index + 1] == "*":
            end = text.find("*/", index + 2)
            if end < 0:
                fail(f"unterminated JavaScript comment: {label}")
            index = end + 2
            continue
        if char == "/" and _js_regex_can_start(tokens):
            index = _skip_js_regex(text, index)
            tokens.append(("regex", ""))
            continue
        if char in "'\"":
            index, value = _consume_js_string(text, index)
            tokens.append(("string", value))
        elif char == "`":
            index, value = _skip_js_template(text, index)
            if value is not None:
                tokens.append(("string", value))
        elif char.isalpha() or char in "_$":
            end = index + 1
            while end < len(text) and (
                text[end].isalnum() or text[end] in "_$"
            ):
                end += 1
            tokens.append(("identifier", text[index:end]))
            index = end
        else:
            operator = text[index : index + 2]
            if operator in {"=>", "==", "!=", "<=", ">=", "&&", "||", "?.", "??"}:
                tokens.append(("punct", operator))
                index += 2
            else:
                tokens.append(("punct", char))
                index += 1
        if len(tokens) > MAX_JS_TOKENS:
            fail(f"executable JavaScript exceeds token budget: {label}")
    return tokens


def _string_literals(tokens: Iterable[tuple[str, str]]) -> set[bytes]:
    return {
        value.encode("utf-8")
        for kind, value in tokens
        if kind == "string"
    }


def _has_bound_provenance_object(
    tokens: list[tuple[str, str]], expected: dict[str, str]
) -> bool:
    properties: dict[str, object] = {
        "releaseId": expected["releaseId"],
        "candidateCommit": expected["candidateCommit"],
        "approvedPatchSha256": expected["approvedPatchSha256"],
        "approvedSourceManifestSha256": expected[
            "approvedSourceManifestSha256"
        ],
        "signExcelImportEnabled": True,
    }
    objects: dict[int, dict[str, list[object]]] = {}
    stack: list[tuple[int, bool]] = []
    next_object = 1
    for index, token in enumerate(tokens):
        kind, value = token
        if value == "{":
            previous = tokens[index - 1][1] if index else ""
            is_object = previous in {"=", "(", "[", ",", ":", "=>", "return"}
            stack.append((next_object, is_object))
            if is_object:
                objects[next_object] = {}
            next_object += 1
            continue
        if value == "}":
            if stack:
                stack.pop()
            continue
        if not stack or not stack[-1][1] or value not in properties:
            continue
        if kind not in {"identifier", "string"} or index + 2 >= len(tokens):
            continue
        if tokens[index + 1][1] != ":":
            continue
        value_token = tokens[index + 2]
        if value_token[0] == "string":
            property_value: object = value_token[1]
        elif value_token == ("identifier", "true"):
            property_value = True
        elif value_token == ("identifier", "false"):
            property_value = False
        elif (
            value_token[1] == "!"
            and index + 3 < len(tokens)
            and tokens[index + 3][1] in {"0", "1"}
        ):
            property_value = tokens[index + 3][1] == "0"
        else:
            continue
        object_id = stack[-1][0]
        objects[object_id].setdefault(value, []).append(property_value)

    for values in objects.values():
        if all(values.get(key) == [expected_value] for key, expected_value in properties.items()):
            return True
    return False


def _validate_gzip_twins(
    path: Path, inventory: list[tuple[str, Path, int]]
) -> int:
    by_name = {relative: item for relative, item, _ in inventory}
    decompressed = 0
    checked = 0
    for relative, item, _ in inventory:
        if not relative.endswith(".gz"):
            continue
        twin_name = relative[:-3]
        twin = by_name.get(twin_name)
        if twin is None:
            fail(f"compressed frontend asset has no uncompressed twin: {relative}")
        try:
            with gzip.open(item, "rb") as compressed, _regular_reader(twin) as plain:
                while True:
                    compressed_block = compressed.read(1024 * 1024)
                    plain_block = plain.read(1024 * 1024)
                    decompressed += len(compressed_block)
                    if decompressed > MAX_GZIP_DECOMPRESSED_BYTES:
                        fail("frontend gzip set exceeds total decompression budget")
                    if compressed_block != plain_block:
                        fail(f"compressed frontend asset differs from its twin: {relative}")
                    if not compressed_block:
                        break
        except (OSError, EOFError, gzip.BadGzipFile) as exc:
            raise ValueError(f"invalid compressed frontend asset: {item}") from exc
        checked += 1
    return checked


def _read_file_limited(path: Path, *, limit: int, label: str) -> bytes:
    chunks: list[bytes] = []
    total = 0
    with _regular_reader(path) as handle:
        before = os.fstat(handle.fileno())
        if before.st_size > limit:
            fail(f"{label} exceeds byte budget: {path}")
        while True:
            block = handle.read(min(1024 * 1024, limit + 1 - total))
            if not block:
                break
            total += len(block)
            if total > limit:
                fail(f"{label} exceeds byte budget: {path}")
            chunks.append(block)
        after = os.fstat(handle.fileno())
    if total != before.st_size or (
        before.st_dev,
        before.st_ino,
        before.st_size,
        before.st_mtime_ns,
        before.st_ctime_ns,
    ) != (
        after.st_dev,
        after.st_ino,
        after.st_size,
        after.st_mtime_ns,
        after.st_ctime_ns,
    ):
        fail(f"{label} changed while reading: {path}")
    return b"".join(chunks)


def _json_object_without_duplicates(pairs: list[tuple[str, object]]) -> dict:
    value: dict[str, object] = {}
    for key, item in pairs:
        if key in value:
            fail(f"duplicate release provenance JSON key: {key}")
        value[key] = item
    return value


def _release_manifest_without_duplicates(
    pairs: list[tuple[str, object]],
) -> dict:
    value: dict[str, object] = {}
    for key, item in pairs:
        if key in value:
            fail(f"duplicate release manifest JSON key: {key}")
        value[key] = item
    return value


def _verify_frontend_provenance(
    by_name: dict[str, Path], expected: dict[str, str]
) -> None:
    provenance_path = by_name.get("release-provenance.json")
    if provenance_path is None:
        fail("frontend dist release-provenance.json is missing")
    try:
        payload = json.loads(
            _read_file_limited(
                provenance_path,
                limit=MAX_PROVENANCE_JSON_BYTES,
                label="frontend release-provenance.json",
            ).decode("utf-8"),
            object_pairs_hook=_json_object_without_duplicates,
        )
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise ValueError("frontend release-provenance.json is invalid") from exc
    exact = {
        "schemaVersion": 1,
        "releaseId": expected["releaseId"],
        "candidateCommit": expected["candidateCommit"],
        "approvedPatchSha256": expected["approvedPatchSha256"],
        "approvedSourceManifestSha256": expected[
            "approvedSourceManifestSha256"
        ],
        "signExcelImportEnabled": True,
    }
    if (
        not isinstance(payload, dict)
        or type(payload.get("schemaVersion")) is not int
        or payload != exact
    ):
        fail("frontend release-provenance.json does not exactly match approved source")


def _verify_dist(
    path: Path, expected_provenance: dict[str, str] | None = None
) -> dict:
    inventory = _dist_inventory(path)
    by_name = {relative: item for relative, item, _ in inventory}
    index_path = by_name.get("index.html")
    if index_path is None:
        fail(f"frontend dist index.html is missing: {path}")
    if expected_provenance is not None:
        _verify_frontend_provenance(by_name, expected_provenance)
    gzip_count = _validate_gzip_twins(path, inventory)
    try:
        index_text = _read_file_limited(
            index_path, limit=MAX_INDEX_HTML_BYTES, label="frontend index.html"
        ).decode("utf-8")
    except (OSError, UnicodeDecodeError) as exc:
        raise ValueError(f"frontend dist index.html is unreadable: {index_path}") from exc
    parser = _IndexScriptParser()
    try:
        parser.feed(index_text)
        parser.close()
    except ValueError:
        raise
    except Exception as exc:
        raise ValueError(f"frontend dist index.html is invalid: {index_path}") from exc
    if parser.base_hrefs:
        fail(
            "frontend index base href is unsupported for deterministic bundle resolution: "
            + ", ".join(repr(value) for value in parser.base_hrefs)
        )

    js_files = {relative for relative in by_name if _has_js_suffix(relative)}
    reachable: set[str] = set()
    for reference in parser.references:
        normalized = _normalize_js_reference(reference, PurePosixPath(), strict=True)
        if normalized is not None:
            if normalized not in js_files:
                fail(f"frontend index references missing JavaScript bundle: {normalized}")
            reachable.add(normalized)

    inline_bytes = "\n".join(code for code, _ in parser.inline_scripts).encode(
        "utf-8"
    )
    inline_explicit = _explicit_js_references(inline_bytes, PurePosixPath())
    missing_inline = sorted(inline_explicit - js_files)
    if missing_inline:
        fail(
            "frontend inline JavaScript references missing bundle: "
            + ", ".join(missing_inline[:10])
        )
    reachable.update(inline_explicit)
    reachable.update(_runtime_mapped_js(inline_bytes, js_files))

    queue = list(sorted(reachable))
    javascript: dict[str, bytes] = {}
    while queue:
        relative = queue.pop()
        data = _read_file_limited(
            by_name[relative], limit=MAX_JS_FILE_BYTES, label="frontend JavaScript"
        )
        javascript[relative] = data
        base = PurePosixPath(relative).parent
        explicit_candidates = _explicit_js_references(data, base)
        missing_dependencies = sorted(explicit_candidates - js_files)
        if missing_dependencies:
            fail(
                f"frontend JavaScript {relative} references missing bundle: "
                + ", ".join(missing_dependencies[:10])
            )
        candidates = explicit_candidates | _runtime_mapped_js(data, js_files)
        for candidate in candidates:
            if candidate in js_files and candidate not in reachable:
                reachable.add(candidate)
                queue.append(candidate)

    unreferenced = sorted(js_files - reachable)
    if unreferenced:
        fail(
            "frontend dist contains unreferenced executable JavaScript: "
            + ", ".join(unreferenced[:10])
        )
    if not reachable:
        fail("frontend index reaches no executable JavaScript bundle")
    if len(reachable) > MAX_REACHABLE_JS_FILES:
        fail("frontend reachable JavaScript exceeds file-count budget")
    total_javascript_bytes = len(inline_bytes) + sum(
        len(data) for data in javascript.values()
    )
    if total_javascript_bytes > MAX_REACHABLE_JS_TOTAL_BYTES:
        fail("frontend reachable JavaScript exceeds total byte budget")

    syntax_deadline = time.monotonic() + JS_SYNTAX_TOTAL_TIMEOUT_SECONDS
    found: set[bytes] = set()
    for inline_number, (code, is_module) in enumerate(parser.inline_scripts, 1):
        data = code.encode("utf-8")
        label = f"index.html inline script {inline_number}"
        _check_javascript_syntax(
            data,
            label,
            mode="module" if is_module else "script",
            deadline=syntax_deadline,
        )
        found.update(_string_literals(_js_tokens(data, label)))

    provenance_bound = False
    for relative in sorted(reachable):
        data = javascript[relative]
        suffix = PurePosixPath(relative).suffix.lower()
        mode = "module" if suffix == ".mjs" else "script" if suffix == ".cjs" else "auto"
        _check_javascript_syntax(
            data, relative, mode=mode, deadline=syntax_deadline
        )
        tokens = _js_tokens(data, relative)
        found.update(_string_literals(tokens))
        if expected_provenance is not None and _has_bound_provenance_object(
            tokens, expected_provenance
        ):
            provenance_bound = True
    forbidden = [
        marker.decode("utf-8") for marker in FORBIDDEN_DIST_MARKERS if marker in found
    ]
    if forbidden:
        fail("frontend dist contains superseded API/component markers: " + ", ".join(forbidden))
    missing = [
        marker.decode("utf-8") for marker in REQUIRED_DIST_MARKERS if marker not in found
    ]
    if missing:
        fail("frontend dist misses required 20260718 entry markers: " + ", ".join(missing))
    if expected_provenance is not None and not provenance_bound:
        fail(
            "reachable frontend JavaScript does not bind all approved source "
            "provenance properties in one object"
        )
    return {
        "fileCount": len(inventory),
        "reachableJsCount": len(reachable),
        "gzipTwinCount": gzip_count,
        "syntaxCheckedJsCount": len(reachable) + len(parser.inline_scripts),
    }


def _require_sha256(value: object, label: str) -> str:
    if not isinstance(value, str) or not SHA256.fullmatch(value):
        fail(f"{label} expected SHA256 is missing, pending, or invalid")
    return value


def _expected_source_provenance(
    candidate_commit: object,
    patch_sha256: object,
    source_manifest_sha256: object,
) -> dict[str, str] | None:
    supplied = (
        candidate_commit is not None,
        patch_sha256 is not None,
        source_manifest_sha256 is not None,
    )
    if not any(supplied):
        return None
    if not all(supplied):
        fail(
            "candidate commit, approved patch SHA256, and approved source manifest "
            "SHA256 must be supplied together"
        )
    if not isinstance(candidate_commit, str) or not GIT_COMMIT.fullmatch(
        candidate_commit
    ):
        fail("expected candidate commit is missing, UNSET, or invalid")
    return {
        "releaseId": RELEASE_ID,
        "candidateCommit": candidate_commit,
        "approvedPatchSha256": _require_sha256(
            patch_sha256, "approved patch"
        ),
        "approvedSourceManifestSha256": _require_sha256(
            source_manifest_sha256, "approved source manifest"
        ),
    }


def _resolve_path(root: Path, value: object, label: str) -> Path:
    if not isinstance(value, (str, Path)) or not str(value):
        fail(f"{label} path is missing")
    candidate = Path(value)
    return (root / candidate).absolute() if not candidate.is_absolute() else candidate.absolute()


def verify_artifacts(
    oa_jar: Path,
    system_jar: Path,
    frontend_dist: Path,
    expected_oa_sha256: str,
    expected_system_sha256: str,
    expected_frontend_tree_sha256: str,
    *,
    expected_candidate_commit: str | None = None,
    expected_patch_sha256: str | None = None,
    expected_source_manifest_sha256: str | None = None,
) -> dict:
    oa_jar = Path(oa_jar).absolute()
    system_jar = Path(system_jar).absolute()
    frontend_dist = Path(frontend_dist).absolute()
    expected = {
        "OA JAR": _require_sha256(expected_oa_sha256, "OA JAR"),
        "System JAR": _require_sha256(expected_system_sha256, "System JAR"),
        "frontend dist tree": _require_sha256(
            expected_frontend_tree_sha256, "frontend dist tree"
        ),
    }
    expected_provenance = _expected_source_provenance(
        expected_candidate_commit,
        expected_patch_sha256,
        expected_source_manifest_sha256,
    )

    source_pre = {
        "OA JAR": file_sha256(oa_jar),
        "System JAR": file_sha256(system_jar),
        "frontend dist tree": directory_tree_sha256(frontend_dist),
    }
    mismatches = [label for label in expected if source_pre[label] != expected[label]]
    if mismatches:
        fail("immutable artifact hash mismatch: " + ", ".join(mismatches))

    with tempfile.TemporaryDirectory(prefix="onboard-artifact-snapshot-") as temporary:
        snapshot_root = Path(temporary)
        snapshot_oa = snapshot_root / "oa.jar"
        snapshot_system = snapshot_root / "system.jar"
        snapshot_dist = snapshot_root / "dist"
        _copy_regular_file(
            oa_jar, snapshot_oa, max_bytes=MAX_ARTIFACT_FILE_BYTES
        )
        _copy_regular_file(
            system_jar, snapshot_system, max_bytes=MAX_ARTIFACT_FILE_BYTES
        )
        _copy_dist_snapshot(frontend_dist, snapshot_dist)
        _make_snapshot_read_only(snapshot_root)

        snapshot_hashes = {
            "OA JAR": file_sha256(snapshot_oa),
            "System JAR": file_sha256(snapshot_system),
            "frontend dist tree": directory_tree_sha256(snapshot_dist),
        }
        source_post = {
            "OA JAR": file_sha256(oa_jar),
            "System JAR": file_sha256(system_jar),
            "frontend dist tree": directory_tree_sha256(frontend_dist),
        }
        unstable = [
            label
            for label in expected
            if not (
                source_pre[label]
                == source_post[label]
                == snapshot_hashes[label]
                == expected[label]
            )
        ]
        if unstable:
            fail(
                "artifact changed while creating stable snapshot: "
                + ", ".join(unstable)
            )

        oa = _verify_jar(
            snapshot_oa,
            REQUIRED_OA_CLASSES,
            REQUIRED_OA_MARKERS,
            "com/erp/oa",
            expected_provenance=expected_provenance,
            check_removed_oa=True,
        )
        system = _verify_jar(
            snapshot_system,
            REQUIRED_SYSTEM_CLASSES,
            REQUIRED_SYSTEM_MARKERS,
            "com/erp/system",
            expected_provenance=expected_provenance,
        )
        frontend = _verify_dist(snapshot_dist, expected_provenance)

        snapshot_post = {
            "OA JAR": file_sha256(snapshot_oa),
            "System JAR": file_sha256(snapshot_system),
            "frontend dist tree": directory_tree_sha256(snapshot_dist),
        }
        snapshot_mutated = [
            label
            for label in expected
            if not (
                snapshot_post[label]
                == snapshot_hashes[label]
                == source_pre[label]
                == expected[label]
            )
        ]
        if snapshot_mutated:
            fail(
                "read-only artifact snapshot changed during semantic verification: "
                + ", ".join(snapshot_mutated)
            )

        source_final = {
            "OA JAR": file_sha256(oa_jar),
            "System JAR": file_sha256(system_jar),
            "frontend dist tree": directory_tree_sha256(frontend_dist),
        }
        source_mutated = [
            label
            for label in expected
            if source_final[label] != source_pre[label]
            or source_final[label] != expected[label]
        ]
        if source_mutated:
            fail(
                "source artifact changed during semantic verification: "
                + ", ".join(source_mutated)
            )

    result = {
        "releaseId": RELEASE_ID,
        "oaJarSha256": source_pre["OA JAR"],
        "systemJarSha256": source_pre["System JAR"],
        "frontendTreeSha256": source_pre["frontend dist tree"],
        "removedEntryCountChecked": len(REMOVED_ENTRY_PATHS),
        "oa": oa,
        "system": system,
        "frontend": frontend,
    }
    if expected_provenance is not None:
        result["sourceProvenance"] = expected_provenance
    return result


def _manifest_artifact(item: object, artifact_id: str, root: Path) -> tuple[Path, str]:
    if not isinstance(item, dict):
        fail(f"manifest artifact evidence {artifact_id} is missing")
    path = _resolve_path(root, item.get("path"), f"manifest artifact {artifact_id}")
    try:
        path.resolve().relative_to(root.resolve())
    except ValueError:
        fail(f"manifest artifact {artifact_id} escapes release root")
    if artifact_id == "frontendDist":
        tree_hash = item.get("treeSha256")
        regular_hash = item.get("sha256")
        if tree_hash is not None and regular_hash is not None and tree_hash != regular_hash:
            fail("manifest frontend treeSha256 and sha256 disagree")
        expected = _require_sha256(
            tree_hash if tree_hash is not None else regular_hash,
            "manifest frontend dist tree",
        )
    else:
        expected = _require_sha256(item.get("sha256"), f"manifest artifact {artifact_id}")
    return path, expected


def verify_manifest(
    manifest_path: Path,
    root: Path,
    *,
    expected_candidate_commit: str | None = None,
    expected_patch_sha256: str | None = None,
    expected_source_manifest_sha256: str | None = None,
) -> dict:
    root = Path(root).resolve()
    manifest_path = Path(manifest_path).resolve()
    try:
        manifest = json.loads(
            _read_file_limited(
                manifest_path,
                limit=MAX_RELEASE_MANIFEST_BYTES,
                label="release manifest",
            ).decode("utf-8"),
            object_pairs_hook=_release_manifest_without_duplicates,
        )
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise ValueError(f"release manifest is unreadable or invalid: {manifest_path}") from exc
    if not isinstance(manifest, dict):
        fail("release manifest root must be a JSON object")
    if manifest.get("schemaVersion") != 1 or manifest.get("releaseId") != RELEASE_ID:
        fail("unexpected onboard-contract release manifest identity")
    evidence = manifest.get("artifactEvidence")
    if not isinstance(evidence, dict) or evidence.get("status") != "verified":
        fail("manifest immutable artifact inventory/hashes are pending")
    oa_path, oa_hash = _manifest_artifact(evidence.get("oaJar"), "oaJar", root)
    system_path, system_hash = _manifest_artifact(
        evidence.get("systemJar"), "systemJar", root
    )
    frontend_path, frontend_hash = _manifest_artifact(
        evidence.get("frontendDist"), "frontendDist", root
    )
    return verify_artifacts(
        oa_path,
        system_path,
        frontend_path,
        oa_hash,
        system_hash,
        frontend_hash,
        expected_candidate_commit=expected_candidate_commit,
        expected_patch_sha256=expected_patch_sha256,
        expected_source_manifest_sha256=expected_source_manifest_sha256,
    )


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Verify immutable 20260718 OA/System/frontend signing artifacts"
    )
    parser.add_argument(
        "--root", type=Path, default=Path(__file__).resolve().parent.parent
    )
    parser.add_argument("--manifest", type=Path)
    parser.add_argument("--oa-jar", type=Path)
    parser.add_argument("--system-jar", type=Path)
    parser.add_argument("--frontend-dist", type=Path)
    parser.add_argument("--expect-oa-sha256")
    parser.add_argument("--expect-system-sha256")
    parser.add_argument("--expect-frontend-tree-sha256")
    parser.add_argument("--expect-candidate-commit")
    parser.add_argument(
        "--expect-patch-sha256",
        "--expect-approved-patch-sha256",
        dest="expect_patch_sha256",
    )
    parser.add_argument(
        "--expect-source-manifest-sha256",
        "--expect-approved-source-manifest-sha256",
        dest="expect_source_manifest_sha256",
    )
    parser.add_argument("--json-output", type=Path)
    return parser


def main(argv: list[str] | None = None) -> int:
    parser = _build_parser()
    args = parser.parse_args(argv)
    try:
        if args.manifest is not None:
            direct_values = (
                args.oa_jar,
                args.system_jar,
                args.frontend_dist,
                args.expect_oa_sha256,
                args.expect_system_sha256,
                args.expect_frontend_tree_sha256,
            )
            if any(value is not None for value in direct_values):
                fail("--manifest cannot be combined with direct artifact paths/hashes")
            result = verify_manifest(
                args.manifest,
                args.root,
                expected_candidate_commit=args.expect_candidate_commit,
                expected_patch_sha256=args.expect_patch_sha256,
                expected_source_manifest_sha256=args.expect_source_manifest_sha256,
            )
        else:
            required = {
                "--oa-jar": args.oa_jar,
                "--system-jar": args.system_jar,
                "--frontend-dist": args.frontend_dist,
                "--expect-oa-sha256": args.expect_oa_sha256,
                "--expect-system-sha256": args.expect_system_sha256,
                "--expect-frontend-tree-sha256": args.expect_frontend_tree_sha256,
            }
            missing = [name for name, value in required.items() if value is None]
            if missing:
                fail("direct mode is missing required arguments: " + ", ".join(missing))
            root = args.root.resolve()
            result = verify_artifacts(
                _resolve_path(root, args.oa_jar, "OA JAR"),
                _resolve_path(root, args.system_jar, "System JAR"),
                _resolve_path(root, args.frontend_dist, "frontend dist"),
                args.expect_oa_sha256,
                args.expect_system_sha256,
                args.expect_frontend_tree_sha256,
                expected_candidate_commit=args.expect_candidate_commit,
                expected_patch_sha256=args.expect_patch_sha256,
                expected_source_manifest_sha256=args.expect_source_manifest_sha256,
            )
        if args.json_output is not None:
            args.json_output.parent.mkdir(parents=True, exist_ok=True)
            args.json_output.write_text(
                json.dumps(result, ensure_ascii=False, indent=2) + "\n",
                encoding="utf-8",
            )
        print(
            "[PASS] immutable 20260718 signing artifacts are clean and hash-pinned "
            f"oa={result['oaJarSha256']} system={result['systemJarSha256']} "
            f"frontend_tree={result['frontendTreeSha256']}"
        )
        return 0
    except ValueError as exc:
        print(f"[FAIL] immutable 20260718 signing artifact readiness: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())

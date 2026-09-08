#!/usr/bin/env python3

import hashlib
import importlib.util
import gzip
import io
import json
import os
import shutil
import socket
import stat
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest import mock


ROOT = Path(__file__).resolve().parent.parent
SCRIPT = ROOT / "scripts/verify_onboard_contract_excel_artifacts.py"
SPEC = importlib.util.spec_from_file_location("onboard_artifact_verifier", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)

CANDIDATE_COMMIT = "a" * 40
APPROVED_PATCH_SHA256 = "b" * 64
APPROVED_SOURCE_MANIFEST_SHA256 = "c" * 64


def approved_provenance():
    return {
        "releaseId": MODULE.RELEASE_ID,
        "candidateCommit": CANDIDATE_COMMIT,
        "approvedPatchSha256": APPROVED_PATCH_SHA256,
        "approvedSourceManifestSha256": APPROVED_SOURCE_MANIFEST_SHA256,
    }


def frontend_provenance(provenance, enabled=True):
    return {**provenance, "signExcelImportEnabled": enabled}


def build_info_properties(provenance, extra_lines=()):
    lines = [
        "build.commit=" + provenance["candidateCommit"],
        "build.releaseId=" + provenance["releaseId"],
        "build.approvedPatchSha256=" + provenance["approvedPatchSha256"],
        "build.approvedSourceManifestSha256="
        + provenance["approvedSourceManifestSha256"],
    ]
    lines.extend(extra_lines)
    return ("\n".join(lines) + "\n").encode("utf-8")


def class_entry(stem):
    return "BOOT-INF/classes/" + stem + ".class"


class ConstantPool:
    def __init__(self):
        self.entries = []
        self.cache = {}

    def utf8(self, value):
        raw = value.encode("utf-8") if isinstance(value, str) else bytes(value)
        key = (1, raw)
        if key not in self.cache:
            self.entries.append(b"\x01" + len(raw).to_bytes(2, "big") + raw)
            self.cache[key] = len(self.entries)
        return self.cache[key]

    def class_info(self, name):
        name_index = self.utf8(name)
        key = (7, name_index)
        if key not in self.cache:
            self.entries.append(b"\x07" + name_index.to_bytes(2, "big"))
            self.cache[key] = len(self.entries)
        return self.cache[key]


def annotation_bytes(pool, descriptor, elements=None):
    pairs = []
    for name, value in (elements or {}).items():
        if isinstance(value, (tuple, list)):
            encoded = b"[" + len(value).to_bytes(2, "big")
            encoded += b"".join(
                b"s" + pool.utf8(item).to_bytes(2, "big") for item in value
            )
        elif isinstance(value, str):
            encoded = b"s" + pool.utf8(value).to_bytes(2, "big")
        else:
            raise TypeError(value)
        pairs.append(pool.utf8(name).to_bytes(2, "big") + encoded)
    return (
        pool.utf8(descriptor).to_bytes(2, "big")
        + len(pairs).to_bytes(2, "big")
        + b"".join(pairs)
    )


def annotations_attribute(pool, annotations, *, visible=True):
    name = "RuntimeVisibleAnnotations" if visible else "RuntimeInvisibleAnnotations"
    body = len(annotations).to_bytes(2, "big") + b"".join(
        annotation_bytes(pool, descriptor, elements)
        for descriptor, elements in annotations
    )
    return pool.utf8(name).to_bytes(2, "big") + len(body).to_bytes(4, "big") + body


def classfile(
    stem, *, constants=(), class_annotations=(), class_annotations_visible=True, methods=()
):
    """Build a complete Java 17 classfile with real annotation attributes."""

    pool = ConstantPool()
    this_class = pool.class_info(stem)
    super_class = pool.class_info("java/lang/Object")
    for constant in constants:
        pool.utf8(constant)

    encoded_methods = []
    for method in methods:
        name_index = pool.utf8(method["name"])
        descriptor_index = pool.utf8(method.get("descriptor", "()V"))
        access_flags = (
            0x0001
            | (0x0008 if method.get("static") else 0)
            | (0x0400 if method.get("abstract") else 0)
        )
        attributes = []
        if not method.get("abstract"):
            bytecode = method.get("bytecode", b"\xb1")
            code = (
                int(method.get("max_stack", 0)).to_bytes(2, "big")
                + int(method.get("max_locals", 1)).to_bytes(2, "big")
                + len(bytecode).to_bytes(4, "big")
                + bytecode
                + b"\x00\x00\x00\x00"
            )
            attributes.append(
                pool.utf8("Code").to_bytes(2, "big")
                + len(code).to_bytes(4, "big")
                + code
            )
        if method.get("annotations"):
            attributes.append(
                annotations_attribute(
                    pool,
                    method["annotations"],
                    visible=method.get("visible", True),
                )
            )
        encoded_methods.append(
            access_flags.to_bytes(2, "big")
            + name_index.to_bytes(2, "big")
            + descriptor_index.to_bytes(2, "big")
            + len(attributes).to_bytes(2, "big")
            + b"".join(attributes)
        )

    class_attributes = []
    if class_annotations:
        class_attributes.append(
            annotations_attribute(
                pool, class_annotations, visible=class_annotations_visible
            )
        )
    return b"".join(
        (
            b"\xca\xfe\xba\xbe",
            b"\x00\x00\x00\x3d",
            (len(pool.entries) + 1).to_bytes(2, "big"),
            *pool.entries,
            b"\x00\x21",
            this_class.to_bytes(2, "big"),
            super_class.to_bytes(2, "big"),
            b"\x00\x00",
            b"\x00\x00",
            len(encoded_methods).to_bytes(2, "big"),
            *encoded_methods,
            len(class_attributes).to_bytes(2, "big"),
            *class_attributes,
        )
    )


def minimal_classfile(stem, constants=()):
    return classfile(stem, constants=constants)


def executable_classfile(stem):
    return classfile(
        stem,
        methods=[
            {
                "name": "main",
                "descriptor": "([Ljava/lang/String;)V",
                "static": True,
            }
        ],
    )


def mapping_annotation(descriptor, route):
    return (descriptor, {"value": (route,)})


def controller_classfile(stem, markers):
    marker_text = {
        marker.decode("utf-8") if isinstance(marker, bytes) else marker
        for marker in markers
    }
    if stem == MODULE.REQUIRED_OA_CLASSES[0]:
        class_annotations = [(MODULE.REST_CONTROLLER, {})]
        if "/signTask/onboard" in marker_text:
            class_annotations.append(
                mapping_annotation(MODULE.REQUEST_MAPPING, "/signTask/onboard")
            )
        methods = []
        for name, mapping, route, security, security_value in MODULE.OA_METHOD_CONTRACT:
            if route not in marker_text:
                continue
            annotations = [mapping_annotation(mapping, route)]
            elements = {"value": (security_value,)} if security_value else {}
            annotations.append((security, elements))
            methods.append({"name": name, "annotations": annotations})
        return classfile(
            stem, class_annotations=class_annotations, methods=methods
        )
    if stem == MODULE.REQUIRED_SYSTEM_CLASSES[0]:
        class_annotations = [(MODULE.REST_CONTROLLER, {})]
        if "/user/sign-profile" in marker_text:
            class_annotations.append(
                mapping_annotation(MODULE.REQUEST_MAPPING, "/user/sign-profile")
            )
        methods = []
        if "/supplement" in marker_text:
            methods.append(
                {
                    "name": "supplement",
                    "annotations": [
                        mapping_annotation(MODULE.POST_MAPPING, "/supplement"),
                        (MODULE.INNER_AUTH, {}),
                    ],
                }
            )
        return classfile(
            stem, class_annotations=class_annotations, methods=methods
        )
    return minimal_classfile(stem, markers)


def executable_manifest(start_class):
    return (
        "Manifest-Version: 1.0\r\n"
        f"Main-Class: {MODULE.SPRING_BOOT_MAIN_CLASS}\r\n"
        f"Start-Class: {start_class}\r\n\r\n"
    ).encode("utf-8")


class OnboardContractArtifactVerifierTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.oa = self.root / "oa.jar"
        self.system = self.root / "system.jar"
        self.dist = self.root / "dist"
        self.build_valid()

    def tearDown(self):
        self.temp.cleanup()

    def build_jar(self, path, classes, markers=(), extra_entries=None, *, executable=True):
        entries = {}
        if executable:
            if Path(path) == self.oa:
                start_class = "com.erp.oa.ErpOaApplication"
            elif Path(path) == self.system:
                start_class = "com.erp.system.ErpSystemApplication"
            else:
                raise AssertionError(path)
            start_stem = start_class.replace(".", "/")
            entries.update(
                {
                    "META-INF/MANIFEST.MF": executable_manifest(start_class),
                    MODULE.SPRING_BOOT_LAUNCHER_CLASS + ".class": executable_classfile(
                        MODULE.SPRING_BOOT_LAUNCHER_CLASS
                    ),
                    class_entry(start_stem): executable_classfile(start_stem),
                }
            )
        for stem in classes:
            entries[class_entry(stem)] = controller_classfile(
                stem, markers if stem.endswith("Controller") else ()
            )
        entries.update(extra_entries or {})
        with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            for name, content in entries.items():
                archive.writestr(name, content)

    def nested_jar(self, entries):
        content = io.BytesIO()
        with zipfile.ZipFile(content, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            for name, value in entries:
                archive.writestr(name, value)
        return content.getvalue()

    def build_valid(
        self,
        *,
        oa_extra=None,
        system_extra=None,
        oa_markers=None,
        system_markers=None,
        dist_extra=b"",
        provenance=None,
    ):
        oa_entries = {}
        system_entries = {}
        if provenance is not None:
            build_info = build_info_properties(provenance)
            default_path = "BOOT-INF/classes/META-INF/build-info.properties"
            oa_entries[default_path] = build_info
            system_entries[default_path] = build_info
        oa_entries.update(oa_extra or {})
        system_entries.update(system_extra or {})
        self.build_jar(
            self.oa,
            MODULE.REQUIRED_OA_CLASSES,
            MODULE.REQUIRED_OA_MARKERS if oa_markers is None else oa_markers,
            oa_entries,
        )
        self.build_jar(
            self.system,
            MODULE.REQUIRED_SYSTEM_CLASSES,
            MODULE.REQUIRED_SYSTEM_MARKERS if system_markers is None else system_markers,
            system_entries,
        )
        if self.dist.exists():
            shutil.rmtree(self.dist)
        self.dist.mkdir()
        (self.dist / "index.html").write_text(
            '<html><script src="/app.js"></script></html>', encoding="utf-8"
        )
        app_markers = [
            marker.decode("utf-8") for marker in MODULE.REQUIRED_DIST_MARKERS
        ]
        if dist_extra:
            app_markers.append(dist_extra.decode("utf-8"))
        app_lines = [
            "const onboardMarkers="
            + json.dumps(app_markers, ensure_ascii=False, separators=(",", ":"))
            + ";"
        ]
        if provenance is not None:
            frontend = frontend_provenance(provenance)
            app_lines.append(
                "const onboardRelease="
                + json.dumps(frontend, separators=(",", ":"))
                + ";"
            )
            (self.dist / "release-provenance.json").write_text(
                json.dumps(
                    {"schemaVersion": 1, **frontend},
                    separators=(",", ":"),
                ),
                encoding="utf-8",
            )
        (self.dist / "app.js").write_text("\n".join(app_lines), encoding="utf-8")

    def hashes(self):
        return (
            MODULE.file_sha256(self.oa),
            MODULE.file_sha256(self.system),
            MODULE.directory_tree_sha256(self.dist),
        )

    def verify(self):
        return MODULE.verify_artifacts(self.oa, self.system, self.dist, *self.hashes())

    def verify_with_provenance(self, **overrides):
        expected = {
            "expected_candidate_commit": CANDIDATE_COMMIT,
            "expected_patch_sha256": APPROVED_PATCH_SHA256,
            "expected_source_manifest_sha256": APPROVED_SOURCE_MANIFEST_SHA256,
        }
        expected.update(overrides)
        return MODULE.verify_artifacts(
            self.oa,
            self.system,
            self.dist,
            *self.hashes(),
            **expected,
        )

    def test_accepts_clean_hash_pinned_artifacts(self):
        result = self.verify()
        self.assertEqual(MODULE.RELEASE_ID, result["releaseId"])
        self.assertEqual(10, result["removedEntryCountChecked"])
        self.assertEqual(self.hashes()[2], result["frontendTreeSha256"])
        self.assertEqual(
            "com.erp.oa.ErpOaApplication",
            result["oa"]["springBootStructure"]["startClass"],
        )
        self.assertEqual(
            "com.erp.system.ErpSystemApplication",
            result["system"]["springBootStructure"]["startClass"],
        )
        self.assertEqual(1, result["frontend"]["syntaxCheckedJsCount"])

    def test_rejects_non_executable_or_misdirected_spring_boot_jars(self):
        self.build_jar(
            self.oa,
            MODULE.REQUIRED_OA_CLASSES,
            MODULE.REQUIRED_OA_MARKERS,
            executable=False,
        )
        with self.assertRaisesRegex(ValueError, "exactly one executable.*MANIFEST"):
            self.verify()

        bad_manifest = executable_manifest("com.erp.system.ErpSystemApplication")
        self.build_valid(oa_extra={"META-INF/MANIFEST.MF": bad_manifest})
        with self.assertRaisesRegex(ValueError, "unapproved Spring Boot Start-Class"):
            self.verify()

        bad_main = executable_manifest("com.erp.oa.ErpOaApplication").replace(
            MODULE.SPRING_BOOT_MAIN_CLASS.encode("ascii"), b"example.DecoyLauncher"
        )
        self.build_valid(oa_extra={"META-INF/MANIFEST.MF": bad_main})
        with self.assertRaisesRegex(ValueError, "unapproved Spring Boot Main-Class"):
            self.verify()

        self.build_valid(oa_extra={"meta-inf/manifest.mf": bad_manifest})
        with self.assertRaisesRegex(ValueError, "exactly one executable.*MANIFEST"):
            self.verify()

    def test_rejects_missing_or_malformed_boot_launcher_and_start_class(self):
        start = "com/erp/oa/ErpOaApplication"
        self.build_jar(
            self.oa,
            MODULE.REQUIRED_OA_CLASSES,
            MODULE.REQUIRED_OA_MARKERS,
            {
                "META-INF/MANIFEST.MF": executable_manifest(
                    "com.erp.oa.ErpOaApplication"
                ),
                class_entry(start): minimal_classfile(start),
            },
            executable=False,
        )
        with self.assertRaisesRegex(ValueError, "misses executable runtime class.*JarLauncher"):
            self.verify()

        self.build_valid(
            oa_extra={MODULE.SPRING_BOOT_LAUNCHER_CLASS + ".class": b"NOT-CLASS"}
        )
        with self.assertRaisesRegex(ValueError, "invalid JVM classfile magic"):
            self.verify()

        self.build_valid(
            oa_extra={
                MODULE.SPRING_BOOT_LAUNCHER_CLASS + ".class": minimal_classfile(
                    MODULE.SPRING_BOOT_LAUNCHER_CLASS
                )
            }
        )
        with self.assertRaisesRegex(ValueError, "public static concrete main"):
            self.verify()

        zero_locals = classfile(
            MODULE.SPRING_BOOT_LAUNCHER_CLASS,
            methods=[
                {
                    "name": "main",
                    "descriptor": "([Ljava/lang/String;)V",
                    "static": True,
                    "max_locals": 0,
                }
            ],
        )
        self.build_valid(
            oa_extra={MODULE.SPRING_BOOT_LAUNCHER_CLASS + ".class": zero_locals}
        )
        with self.assertRaisesRegex(ValueError, "do not fit max_locals"):
            self.verify()

        invalid_opcode = classfile(
            MODULE.SPRING_BOOT_LAUNCHER_CLASS,
            methods=[
                {
                    "name": "main",
                    "descriptor": "([Ljava/lang/String;)V",
                    "static": True,
                    "bytecode": b"\xff",
                }
            ],
        )
        self.build_valid(
            oa_extra={
                MODULE.SPRING_BOOT_LAUNCHER_CLASS + ".class": invalid_opcode
            }
        )
        with self.assertRaisesRegex(ValueError, "reserved JVM bytecode opcode"):
            self.verify()

        self.build_valid(
            oa_extra={class_entry(start): minimal_classfile(start)}
        )
        with self.assertRaisesRegex(ValueError, "public static concrete main"):
            self.verify()

        self.build_valid(
            oa_extra={
                class_entry(start): minimal_classfile("com/erp/oa/NotTheApplication")
            }
        )
        with self.assertRaisesRegex(ValueError, "internal name does not match"):
            self.verify()

    def test_accepts_artifacts_bound_to_one_approved_source_candidate(self):
        provenance = approved_provenance()
        self.build_valid(provenance=provenance)
        result = self.verify_with_provenance()
        self.assertEqual(provenance, result["sourceProvenance"])
        self.assertEqual(
            "BOOT-INF/classes/META-INF/build-info.properties",
            result["oa"]["buildInfoPath"],
        )
        self.assertEqual(
            "BOOT-INF/classes/META-INF/build-info.properties",
            result["system"]["buildInfoPath"],
        )

    def test_source_provenance_expectations_are_optional_but_atomic(self):
        self.verify()  # Existing direct mode remains intentionally usable.
        with self.assertRaisesRegex(ValueError, "must be supplied together"):
            MODULE.verify_artifacts(
                self.oa,
                self.system,
                self.dist,
                *self.hashes(),
                expected_candidate_commit=CANDIDATE_COMMIT,
            )
        with self.assertRaisesRegex(ValueError, "candidate commit.*invalid"):
            MODULE.verify_artifacts(
                self.oa,
                self.system,
                self.dist,
                *self.hashes(),
                expected_candidate_commit="UNSET",
                expected_patch_sha256=APPROVED_PATCH_SHA256,
                expected_source_manifest_sha256=APPROVED_SOURCE_MANIFEST_SHA256,
            )

    def test_rejects_build_info_with_wrong_commit_or_approved_hash(self):
        self.build_valid(provenance=approved_provenance())
        cases = {
            "commit": {"expected_candidate_commit": "d" * 40},
            "patch": {"expected_patch_sha256": "e" * 64},
            "source manifest": {
                "expected_source_manifest_sha256": "f" * 64
            },
        }
        for label, override in cases.items():
            with self.subTest(label=label):
                with self.assertRaisesRegex(
                    ValueError, "build provenance does not match approved source"
                ):
                    self.verify_with_provenance(**override)

    def test_rejects_duplicate_build_info_duplicate_keys_and_unset_values(self):
        provenance = approved_provenance()
        self.build_valid(
            provenance=provenance,
            oa_extra={
                "META-INF/build-info.properties": build_info_properties(provenance)
            },
        )
        with self.assertRaisesRegex(ValueError, "exactly one approved build-info"):
            self.verify_with_provenance()

        duplicate_key = build_info_properties(
            provenance, ("build.commit=" + provenance["candidateCommit"],)
        )
        self.build_valid(
            provenance=provenance,
            oa_extra={
                "BOOT-INF/classes/META-INF/build-info.properties": duplicate_key
            },
        )
        with self.assertRaisesRegex(ValueError, "duplicate build provenance key"):
            self.verify_with_provenance()

        unset = dict(provenance)
        unset["candidateCommit"] = "UNSET"
        self.build_valid(
            provenance=provenance,
            system_extra={
                "BOOT-INF/classes/META-INF/build-info.properties":
                    build_info_properties(unset)
            },
        )
        with self.assertRaisesRegex(ValueError, "UNSET build provenance value"):
            self.verify_with_provenance()

        escaped_duplicate = build_info_properties(provenance) + (
            "build\\u002ecommit=" + "d" * 40 + "\n"
        ).encode("ascii")
        self.build_valid(
            provenance=provenance,
            oa_extra={
                "BOOT-INF/classes/META-INF/build-info.properties":
                    escaped_duplicate
            },
        )
        with self.assertRaisesRegex(ValueError, "ambiguous build provenance property"):
            self.verify_with_provenance()

    def test_rejects_inexact_frontend_provenance_json(self):
        provenance = approved_provenance()
        frontend = frontend_provenance(provenance)
        invalid_payloads = (
            {"schemaVersion": 1, **frontend, "candidateCommit": "d" * 40},
            {"schemaVersion": 1, **frontend, "unexpected": True},
            {"schemaVersion": True, **frontend},
            {"schemaVersion": 1, **frontend, "signExcelImportEnabled": False},
            {"schemaVersion": 1, **frontend, "signExcelImportEnabled": "true"},
            {"schemaVersion": 1, **provenance},
        )
        for payload in invalid_payloads:
            with self.subTest(payload=payload):
                self.build_valid(provenance=provenance)
                (self.dist / "release-provenance.json").write_text(
                    json.dumps(payload), encoding="utf-8"
                )
                with self.assertRaisesRegex(ValueError, "does not exactly match"):
                    self.verify_with_provenance()

        self.build_valid(provenance=provenance)
        (self.dist / "release-provenance.json").write_text(
            '{"schemaVersion":1,"releaseId":"'
            + MODULE.RELEASE_ID
            + '","releaseId":"'
            + MODULE.RELEASE_ID
            + '","candidateCommit":"'
            + CANDIDATE_COMMIT
            + '","approvedPatchSha256":"'
            + APPROVED_PATCH_SHA256
            + '","approvedSourceManifestSha256":"'
            + APPROVED_SOURCE_MANIFEST_SHA256
            + '"}',
            encoding="utf-8",
        )
        with self.assertRaisesRegex(ValueError, "duplicate release provenance JSON key"):
            self.verify_with_provenance()

    def test_rejects_frontend_json_and_reachable_javascript_disagreement(self):
        provenance = approved_provenance()
        self.build_valid(provenance=provenance)
        visible = {
            key: value
            for key, value in provenance.items()
            if key != "approvedSourceManifestSha256"
        }
        (self.dist / "app.js").write_text(
            "const markers="
            + json.dumps(
                [marker.decode("utf-8") for marker in MODULE.REQUIRED_DIST_MARKERS],
                ensure_ascii=False,
            )
            + ";const onboardRelease="
            + json.dumps(visible)
            + ";",
            encoding="utf-8",
        )
        with self.assertRaisesRegex(
            ValueError, "does not bind all approved source provenance"
        ):
            self.verify_with_provenance()

        marker_source = "const markers=" + json.dumps(
            [marker.decode("utf-8") for marker in MODULE.REQUIRED_DIST_MARKERS],
            ensure_ascii=False,
        ) + ";const onboardRelease="
        invalid_flags = (
            {**provenance, "signExcelImportEnabled": False},
            {**provenance, "signExcelImportEnabled": "true"},
            dict(provenance),
        )
        for payload in invalid_flags:
            with self.subTest(payload=payload):
                self.build_valid(provenance=provenance)
                (self.dist / "app.js").write_text(
                    marker_source + json.dumps(payload) + ";",
                    encoding="utf-8",
                )
                with self.assertRaisesRegex(
                    ValueError, "does not bind all approved source provenance"
                ):
                    self.verify_with_provenance()

        minified = json.dumps(
            frontend_provenance(provenance), separators=(",", ":")
        ).replace('"signExcelImportEnabled":true', '"signExcelImportEnabled":!0')
        self.build_valid(provenance=provenance)
        (self.dist / "app.js").write_text(
            marker_source + minified + ";", encoding="utf-8"
        )
        self.verify_with_provenance()

        for invalid_expression in ("!1", "1"):
            with self.subTest(invalid_expression=invalid_expression):
                self.build_valid(provenance=provenance)
                invalid_minified = json.dumps(
                    frontend_provenance(provenance), separators=(",", ":")
                ).replace(
                    '"signExcelImportEnabled":true',
                    '"signExcelImportEnabled":' + invalid_expression,
                )
                (self.dist / "app.js").write_text(
                    marker_source + invalid_minified + ";", encoding="utf-8"
                )
                with self.assertRaisesRegex(
                    ValueError, "does not bind all approved source provenance"
                ):
                    self.verify_with_provenance()

    def test_removed_entry_contract_matches_the_exact_release_list(self):
        deleted_list = ROOT / "scripts/onboard-contract-excel-deleted-paths-20260718.list"
        listed = tuple(
            line.strip()
            for line in deleted_list.read_text(encoding="utf-8").splitlines()
            if line.strip() and not line.lstrip().startswith("#")
        )
        self.assertEqual(MODULE.REMOVED_ENTRY_PATHS, listed)

    def test_rejects_all_nine_removed_java_entry_classes(self):
        java_paths = [path for path in MODULE.REMOVED_ENTRY_PATHS if path.endswith(".java")]
        self.assertEqual(9, len(java_paths))
        for source_path in java_paths:
            with self.subTest(source_path=source_path):
                stem = MODULE._class_stem_from_source(source_path)
                self.build_valid(oa_extra={class_entry(stem): b"STALE"})
                with self.assertRaisesRegex(ValueError, "superseded onboarding classes"):
                    self.verify()

    def test_rejects_removed_inner_class_and_old_preview_initiate_routes(self):
        service = "com/erp/oa/service/impl/OaExistingEmployeeOnboardService"
        self.build_valid(oa_extra={class_entry(service + "$FactContext"): b"STALE"})
        with self.assertRaisesRegex(ValueError, "superseded onboarding classes"):
            self.verify()
        for route in MODULE.FORBIDDEN_OA_ROUTES:
            with self.subTest(route=route):
                self.build_valid(oa_extra={"BOOT-INF/classes/routes.txt": route})
                with self.assertRaisesRegex(ValueError, "preview/initiate routes"):
                    self.verify()

    def test_rejects_removed_frontend_api_and_component_markers(self):
        self.assertTrue(MODULE.REMOVED_ENTRY_PATHS[-1].endswith("HrOnboardContractBatchDialog.vue"))
        for marker in MODULE.FORBIDDEN_DIST_MARKERS:
            with self.subTest(marker=marker):
                self.build_valid(dist_extra=marker)
                with self.assertRaisesRegex(ValueError, "superseded API/component markers"):
                    self.verify()

    def test_rejects_missing_new_oa_and_system_classes(self):
        missing_oa = MODULE.REQUIRED_OA_CLASSES[0]
        self.build_jar(
            self.oa,
            MODULE.REQUIRED_OA_CLASSES[1:],
            MODULE.REQUIRED_OA_MARKERS,
        )
        with self.assertRaisesRegex(ValueError, "misses required 20260718 classes.*OaSignOnboardImportController"):
            self.verify()

        self.build_valid()
        missing_system = MODULE.REQUIRED_SYSTEM_CLASSES[-1]
        self.build_jar(
            self.system,
            MODULE.REQUIRED_SYSTEM_CLASSES[:-1],
            MODULE.REQUIRED_SYSTEM_MARKERS,
        )
        with self.assertRaisesRegex(ValueError, "misses required 20260718 classes.*SigningProfileFactsHash"):
            self.verify()
        self.assertTrue(missing_oa)
        self.assertTrue(missing_system)

    def test_required_classes_and_routes_must_be_in_runtime_classes(self):
        missing_class = MODULE.REQUIRED_OA_CLASSES[0]
        test_entries = {
            "BOOT-INF/test-classes/" + missing_class + ".class":
                b"\0".join(MODULE.REQUIRED_OA_MARKERS)
        }
        self.build_jar(
            self.oa,
            MODULE.REQUIRED_OA_CLASSES[1:],
            MODULE.REQUIRED_OA_MARKERS,
            test_entries,
        )
        with self.assertRaisesRegex(ValueError, "BOOT-INF/classes.*OaSignOnboardImportController"):
            self.verify()

        missing_route = MODULE.REQUIRED_OA_MARKERS[-1]
        nested = self.nested_jar(
            [("com/erp/oa/controller/Decoy.class", missing_route)]
        )
        self.build_jar(
            self.oa,
            MODULE.REQUIRED_OA_CLASSES,
            MODULE.REQUIRED_OA_MARKERS[:-1],
            {
                "BOOT-INF/test-classes/com/erp/oa/controller/Decoy.class": missing_route,
                "BOOT-INF/lib/decoy.jar": nested,
            },
        )
        with self.assertRaisesRegex(ValueError, "concrete annotated endpoint method"):
            self.verify()

    def test_required_class_is_exact_top_level_and_a_valid_matching_classfile(self):
        required = MODULE.REQUIRED_OA_CLASSES[0]
        inner = required + "$OnlyInner"
        self.build_jar(
            self.oa,
            MODULE.REQUIRED_OA_CLASSES[1:],
            MODULE.REQUIRED_OA_MARKERS,
            {class_entry(inner): minimal_classfile(inner, MODULE.REQUIRED_OA_MARKERS)},
        )
        with self.assertRaisesRegex(
            ValueError, "misses required 20260718 classes.*OaSignOnboardImportController"
        ):
            self.verify()

        malformed_cases = {
            "bad magic": b"NOT-A-CLASS",
            "wrong internal name": minimal_classfile(
                "com/erp/oa/controller/NotTheRequiredController",
                MODULE.REQUIRED_OA_MARKERS,
            ),
            "truncated structure": minimal_classfile(
                required, MODULE.REQUIRED_OA_MARKERS
            )[:-1],
            "trailing bytes": minimal_classfile(
                required, MODULE.REQUIRED_OA_MARKERS
            )
            + b"DECOY",
        }
        for label, content in malformed_cases.items():
            with self.subTest(label=label):
                self.build_jar(
                    self.oa,
                    MODULE.REQUIRED_OA_CLASSES[1:],
                    MODULE.REQUIRED_OA_MARKERS,
                    {class_entry(required): content},
                )
                with self.assertRaisesRegex(
                    ValueError,
                    "JVM classfile|truncated JVM|internal name|trailing data",
                ):
                    self.verify()

    def test_rejects_classfiles_outside_non_preview_java17_contract(self):
        launcher = MODULE.SPRING_BOOT_LAUNCHER_CLASS
        valid = executable_classfile(launcher)
        versions = {
            "too new": valid[:6] + (62).to_bytes(2, "big") + valid[8:],
            "preview": valid[:4]
            + (0xFFFF).to_bytes(2, "big")
            + (61).to_bytes(2, "big")
            + valid[8:],
        }
        for label, content in versions.items():
            with self.subTest(label=label):
                self.build_valid(
                    oa_extra={launcher + ".class": content}
                )
                with self.assertRaisesRegex(
                    ValueError, "approved non-preview Java 17 version"
                ):
                    self.verify()

    def test_required_routes_only_come_from_the_real_controller_constants(self):
        missing_route = MODULE.REQUIRED_OA_MARKERS[-1]
        decoy = "com/erp/oa/controller/DecoyController"
        nested = self.nested_jar(
            [(decoy + ".class", minimal_classfile(decoy, (missing_route,)))]
        )
        self.build_jar(
            self.oa,
            MODULE.REQUIRED_OA_CLASSES,
            MODULE.REQUIRED_OA_MARKERS[:-1],
            {
                class_entry(decoy): minimal_classfile(decoy, (missing_route,)),
                "BOOT-INF/test-classes/" + decoy + ".class": minimal_classfile(
                    decoy, (missing_route,)
                ),
                "BOOT-INF/classes/routes.txt": missing_route,
                "BOOT-INF/lib/decoy.jar": nested,
            },
        )
        with self.assertRaisesRegex(ValueError, "concrete annotated endpoint method"):
            self.verify()

    def test_controller_routes_require_runtime_visible_class_and_method_annotations(self):
        controller = MODULE.REQUIRED_OA_CLASSES[0]
        class_annotations = [
            (MODULE.REST_CONTROLLER, {}),
            mapping_annotation(MODULE.REQUEST_MAPPING, "/signTask/onboard"),
        ]

        route_constants_only = classfile(
            controller,
            constants=MODULE.REQUIRED_OA_MARKERS,
            class_annotations=class_annotations,
        )
        self.build_valid(oa_extra={class_entry(controller): route_constants_only})
        with self.assertRaisesRegex(ValueError, "concrete annotated endpoint method"):
            self.verify()

        invisible_class = classfile(
            controller,
            class_annotations=class_annotations,
            class_annotations_visible=False,
            methods=[],
        )
        self.build_valid(oa_extra={class_entry(controller): invisible_class})
        with self.assertRaisesRegex(ValueError, "not annotated with @RestController"):
            self.verify()

        no_annotations = minimal_classfile(controller, MODULE.REQUIRED_OA_MARKERS)
        self.build_valid(oa_extra={class_entry(controller): no_annotations})
        with self.assertRaisesRegex(ValueError, "not annotated with @RestController"):
            self.verify()

    def test_controller_endpoint_methods_must_be_concrete_and_security_matched(self):
        controller = MODULE.REQUIRED_OA_CLASSES[0]
        class_annotations = [
            (MODULE.REST_CONTROLLER, {}),
            mapping_annotation(MODULE.REQUEST_MAPPING, "/signTask/onboard"),
        ]

        def methods(preview_security=MODULE.SEND_PERMISSION, abstract_preview=False):
            result = []
            for name, mapping, route, security, security_value in MODULE.OA_METHOD_CONTRACT:
                effective = preview_security if name == "preview" else security_value
                elements = {"value": (effective,)} if effective else {}
                result.append(
                    {
                        "name": name,
                        "abstract": abstract_preview and name == "preview",
                        "annotations": [
                            mapping_annotation(mapping, route),
                            (security, elements),
                        ],
                    }
                )
            return result

        abstract = classfile(
            controller,
            class_annotations=class_annotations,
            methods=methods(abstract_preview=True),
        )
        self.build_valid(oa_extra={class_entry(controller): abstract})
        with self.assertRaisesRegex(ValueError, "endpoint method preview"):
            self.verify()

        wrong_permission = classfile(
            controller,
            class_annotations=class_annotations,
            methods=methods(preview_security="oa:signTask:read"),
        )
        self.build_valid(oa_extra={class_entry(controller): wrong_permission})
        with self.assertRaisesRegex(ValueError, "endpoint method preview"):
            self.verify()

        system_controller = MODULE.REQUIRED_SYSTEM_CLASSES[0]
        system_without_inner_auth = classfile(
            system_controller,
            class_annotations=[
                (MODULE.REST_CONTROLLER, {}),
                mapping_annotation(MODULE.REQUEST_MAPPING, "/user/sign-profile"),
            ],
            methods=[
                {
                    "name": "supplement",
                    "annotations": [
                        mapping_annotation(MODULE.POST_MAPPING, "/supplement")
                    ],
                }
            ],
        )
        self.build_valid(
            system_extra={class_entry(system_controller): system_without_inner_auth}
        )
        with self.assertRaisesRegex(ValueError, "endpoint method supplement"):
            self.verify()

    def test_rejects_old_classes_and_routes_in_test_classes_and_nested_libraries(self):
        old_stem = MODULE.FORBIDDEN_OA_CLASS_STEMS[0]
        self.build_valid(
            oa_extra={
                "BOOT-INF/test-classes/" + old_stem + "$Inner.class": b"STALE"
            }
        )
        with self.assertRaisesRegex(ValueError, "superseded onboarding classes"):
            self.verify()

        nested = self.nested_jar(
            [
                (old_stem + ".class", b"STALE"),
                ("routes.txt", MODULE.FORBIDDEN_OA_ROUTES[0]),
            ]
        )
        self.build_valid(oa_extra={"BOOT-INF/lib/stale.jar": nested})
        with self.assertRaisesRegex(
            ValueError, "superseded onboarding classes.*preview/initiate routes"
        ):
            self.verify()

    def test_rejects_superseded_classes_in_multi_release_locations(self):
        old_stem = MODULE.FORBIDDEN_OA_CLASS_STEMS[0]
        for entry in (
            "META-INF/versions/11/" + old_stem + ".class",
            "BOOT-INF/classes/META-INF/versions/17/" + old_stem + "$Inner.class",
        ):
            with self.subTest(entry=entry):
                self.build_valid(oa_extra={entry: b"STALE"})
                with self.assertRaisesRegex(ValueError, "superseded onboarding classes"):
                    self.verify()

        nested = self.nested_jar(
            [("META-INF/versions/11/" + old_stem + ".class", b"STALE")]
        )
        self.build_valid(oa_extra={"BOOT-INF/lib/stale-mr.jar": nested})
        with self.assertRaisesRegex(ValueError, "superseded onboarding classes"):
            self.verify()

    def test_rejects_unsafe_duplicate_and_zip_bomb_entries(self):
        for unsafe in ("/absolute.class", "BOOT-INF/classes/../escape.class"):
            with self.subTest(unsafe=unsafe):
                self.build_valid(oa_extra={unsafe: b"BAD"})
                with self.assertRaisesRegex(ValueError, "unsafe entry path"):
                    self.verify()

        self.build_valid()
        with zipfile.ZipFile(self.oa, "a", compression=zipfile.ZIP_DEFLATED) as archive:
            duplicate = class_entry(MODULE.REQUIRED_OA_CLASSES[0])
            with self.assertWarns(UserWarning):
                archive.writestr(duplicate, b"DUPLICATE")
        with self.assertRaisesRegex(ValueError, "duplicate entries"):
            self.verify()

        self.build_valid(oa_extra={"META-INF/bomb.bin": b"0" * (1024 * 1024)})
        with self.assertRaisesRegex(ValueError, "compression-ratio budget"):
            self.verify()

        nested_duplicate = io.BytesIO()
        with zipfile.ZipFile(
            nested_duplicate, "w", compression=zipfile.ZIP_DEFLATED
        ) as archive:
            archive.writestr("same.txt", b"one")
            with self.assertWarns(UserWarning):
                archive.writestr("same.txt", b"two")
        self.build_valid(
            oa_extra={"BOOT-INF/lib/duplicate.jar": nested_duplicate.getvalue()}
        )
        with self.assertRaisesRegex(ValueError, "duplicate entries"):
            self.verify()

        nested_bomb = self.nested_jar(
            [("bomb.bin", b"0" * (1024 * 1024))]
        )
        self.build_valid(oa_extra={"BOOT-INF/lib/bomb.jar": nested_bomb})
        with self.assertRaisesRegex(ValueError, "compression-ratio budget"):
            self.verify()

    def test_rejects_missing_new_routes_and_dist_entry_markers(self):
        self.build_valid(oa_markers=MODULE.REQUIRED_OA_MARKERS[:-1])
        with self.assertRaisesRegex(ValueError, "concrete annotated endpoint method"):
            self.verify()

        self.build_valid()
        app = self.dist / "app.js"
        app.write_text(
            "const markers="
            + json.dumps(
                [
                    marker.decode("utf-8")
                    for marker in MODULE.REQUIRED_DIST_MARKERS[:-1]
                ],
                ensure_ascii=False,
            )
            + ";",
            encoding="utf-8",
        )
        with self.assertRaisesRegex(ValueError, "misses required 20260718 entry markers"):
            self.verify()

    def test_frontend_requires_reachable_real_javascript(self):
        (self.dist / "index.html").write_text("<html></html>", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "unreferenced executable JavaScript"):
            self.verify()

        self.build_valid()
        (self.dist / "app.js").write_bytes(
            (self.dist / "app.js").read_bytes() + b';var decoy="decoy.js";'
        )
        (self.dist / "decoy.js").write_bytes(b"console.log('not loaded')")
        with self.assertRaisesRegex(ValueError, "unreferenced executable JavaScript.*decoy.js"):
            self.verify()

        self.build_valid()
        (self.dist / "index.html").write_text(
            '<script src="/missing.js"></script>', encoding="utf-8"
        )
        with self.assertRaisesRegex(ValueError, "references missing JavaScript bundle"):
            self.verify()

    def test_frontend_does_not_treat_inert_html_scripts_as_reachable(self):
        for container in ("template", "noscript"):
            with self.subTest(container=container):
                self.build_valid()
                (self.dist / "index.html").write_text(
                    f"<{container}><script src='/app.js'></script></{container}>",
                    encoding="utf-8",
                )
                with self.assertRaisesRegex(
                    ValueError, "unreferenced executable JavaScript"
                ):
                    self.verify()

        self.build_valid()
        (self.dist / "index.html").write_text(
            "<template/><script src='/app.js'></script>", encoding="utf-8"
        )
        with self.assertRaisesRegex(ValueError, "ambiguous self-closing <template"):
            self.verify()

        self.build_valid()
        (self.dist / "index.html").write_text(
            "<template></noscript><script src='/app.js'></script>", encoding="utf-8"
        )
        with self.assertRaisesRegex(ValueError, "mismatched inert HTML tag"):
            self.verify()

        self.build_valid()
        (self.dist / "index.html").write_text(
            "<script type='application/json' type='text/javascript' "
            "src='/app.js'></script>",
            encoding="utf-8",
        )
        with self.assertRaisesRegex(ValueError, "duplicate attributes"):
            self.verify()

        self.build_valid()
        (self.dist / "index.html").write_text(
            "<script src='/missing.js' src='/app.js'></script>",
            encoding="utf-8",
        )
        with self.assertRaisesRegex(ValueError, "duplicate attributes"):
            self.verify()

    def test_frontend_accepts_inline_webpack_runtime_mapped_chunk(self):
        chunk = self.dist / "static/js/chunk-feature.abcdef12.js"
        chunk.parent.mkdir(parents=True)
        chunk.write_bytes(b"console.log('reachable')")
        (self.dist / "index.html").write_text(
            '<script>var x="chunk-feature";'
            'var m={"chunk-feature":"abcdef12"};'
            'var s=document.createElement("script");'
            's.src="static/js/"+x+"."+m[x]+".js";'
            'document.head.appendChild(s);</script>'
            '<script src="/app.js"></script>',
            encoding="utf-8",
        )
        result = self.verify()
        self.assertEqual(2, result["frontend"]["reachableJsCount"])

    def test_frontend_rejects_marker_only_nul_and_invalid_javascript(self):
        (self.dist / "app.js").write_bytes(
            b"\0".join(MODULE.REQUIRED_DIST_MARKERS)
        )
        with self.assertRaisesRegex(ValueError, "NUL byte"):
            self.verify()

        self.build_valid()
        app = self.dist / "app.js"
        app.write_text(app.read_text(encoding="utf-8") + "\nconst = ;", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "invalid executable JavaScript syntax"):
            self.verify()

    def test_frontend_syntax_checks_every_reachable_chunk(self):
        app = self.dist / "app.js"
        app.write_text(
            app.read_text(encoding="utf-8") + '\nimport("./bad.js");',
            encoding="utf-8",
        )
        (self.dist / "bad.js").write_text("function {", encoding="utf-8")
        with self.assertRaisesRegex(
            ValueError, "invalid executable JavaScript syntax.*bad.js"
        ):
            self.verify()

    def test_frontend_comments_and_strings_cannot_forge_chunk_reachability(self):
        feature = self.dist / "feature.js"
        feature_source = "const markers=" + json.dumps(
            [marker.decode("utf-8") for marker in MODULE.REQUIRED_DIST_MARKERS],
            ensure_ascii=False,
        ) + ";"
        fake_imports = (
            "/* import('./feature.js'); */ console.log('live');",
            'const fake="import(\\\"./feature.js\\\")"; console.log(fake);',
        )
        for source in fake_imports:
            with self.subTest(source=source):
                self.build_valid()
                (self.dist / "app.js").write_text(source, encoding="utf-8")
                feature.write_text(feature_source, encoding="utf-8")
                with self.assertRaisesRegex(
                    ValueError, "unreferenced executable JavaScript.*feature.js"
                ):
                    self.verify()

        self.build_valid()
        runtime_chunk = self.dist / "static/js/chunk-feature.abcdef12.js"
        runtime_chunk.parent.mkdir(parents=True)
        runtime_chunk.write_text(feature_source, encoding="utf-8")
        (self.dist / "app.js").write_text(
            "/* webpackJsonp; var p='static/js/'; "
            "var m={'chunk-feature':'abcdef12'}; */ console.log('live');",
            encoding="utf-8",
        )
        with self.assertRaisesRegex(
            ValueError, "unreferenced executable JavaScript.*chunk-feature"
        ):
            self.verify()

    def test_frontend_provenance_must_be_properties_in_one_live_object(self):
        provenance = approved_provenance()
        marker_source = "const markers=" + json.dumps(
            [marker.decode("utf-8") for marker in MODULE.REQUIRED_DIST_MARKERS],
            ensure_ascii=False,
        ) + ";\n"
        invalid_sources = {
            "comment only": marker_source
            + "/* const release="
            + json.dumps(provenance)
            + "; */",
            "values only": marker_source
            + "const values="
            + json.dumps(list(provenance.values()))
            + ";",
            "separate objects": marker_source
            + "\n".join(
                "const p"
                + str(index)
                + "={"
                + json.dumps(key)
                + ":"
                + json.dumps(value)
                + "};"
                for index, (key, value) in enumerate(provenance.items())
            ),
        }
        for label, source in invalid_sources.items():
            with self.subTest(label=label):
                self.build_valid(provenance=provenance)
                (self.dist / "app.js").write_text(source, encoding="utf-8")
                with self.assertRaisesRegex(
                    ValueError, "does not bind all approved source provenance"
                ):
                    self.verify_with_provenance()

    def test_frontend_only_uses_executable_script_sources(self):
        (self.dist / "index.html").write_text(
            '<script type="application/json" src="/app.js"></script>',
            encoding="utf-8",
        )
        with self.assertRaisesRegex(ValueError, "unreferenced executable JavaScript"):
            self.verify()

        self.build_valid()
        (self.dist / "index.html").write_text(
            '<link rel="preload" as="script" href="/app.js">', encoding="utf-8"
        )
        with self.assertRaisesRegex(ValueError, "unreferenced executable JavaScript"):
            self.verify()

        self.build_valid()
        old_marker = MODULE.FORBIDDEN_DIST_MARKERS[0].decode("ascii")
        (self.dist / "index.html").write_text(
            '<script type="application/json">' + old_marker + "</script>"
            '<script type="module" src="/app.js"></script>',
            encoding="utf-8",
        )
        self.verify()

    def test_frontend_rejects_base_href_and_scans_executable_inline_scripts(self):
        (self.dist / "index.html").write_text(
            '<base href="/erp/"><script src="/app.js"></script>', encoding="utf-8"
        )
        with self.assertRaisesRegex(ValueError, "base href is unsupported"):
            self.verify()

        self.build_valid()
        old_marker = MODULE.FORBIDDEN_DIST_MARKERS[0].decode("ascii")
        (self.dist / "index.html").write_text(
            "<script>const oldRoute='" + old_marker + "'</script>"
            '<script src="/app.js"></script>',
            encoding="utf-8",
        )
        with self.assertRaisesRegex(ValueError, "superseded API/component markers"):
            self.verify()

    def test_frontend_index_and_javascript_have_per_file_memory_budgets(self):
        with mock.patch.object(MODULE, "MAX_INDEX_HTML_BYTES", 1):
            with self.assertRaisesRegex(ValueError, "index.html exceeds byte budget"):
                self.verify()

        with mock.patch.object(MODULE, "MAX_JS_FILE_BYTES", 1):
            with self.assertRaisesRegex(ValueError, "JavaScript exceeds byte budget"):
                self.verify()

    def test_frontend_gzip_requires_an_identical_uncompressed_twin(self):
        app = (self.dist / "app.js").read_bytes()
        (self.dist / "app.js.gz").write_bytes(gzip.compress(app))
        self.assertEqual(1, self.verify()["frontend"]["gzipTwinCount"])

        (self.dist / "app.js.gz").write_bytes(gzip.compress(b"different"))
        with self.assertRaisesRegex(ValueError, "differs from its twin"):
            self.verify()

        self.build_valid()
        (self.dist / "orphan.css.gz").write_bytes(gzip.compress(b"orphan"))
        with self.assertRaisesRegex(ValueError, "has no uncompressed twin"):
            self.verify()

    def test_dist_rejects_fifo_socket_and_total_budgets(self):
        fifo = self.dist / "pipe"
        try:
            os.mkfifo(fifo)
        except (AttributeError, OSError):
            self.skipTest("FIFO unavailable")
        with self.assertRaisesRegex(ValueError, "non-regular entry"):
            MODULE.directory_tree_sha256(self.dist)
        fifo.unlink()

        endpoint = self.dist / "endpoint.sock"
        server = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        try:
            server.bind(str(endpoint))
            with self.assertRaisesRegex(ValueError, "non-regular entry"):
                MODULE.directory_tree_sha256(self.dist)
        finally:
            server.close()
            if endpoint.exists():
                endpoint.unlink()

        with mock.patch.object(MODULE, "MAX_DIST_FILES", 1):
            with self.assertRaisesRegex(ValueError, "total file budget"):
                MODULE.directory_tree_sha256(self.dist)

        (self.dist / "app.js.gz").write_bytes(
            gzip.compress((self.dist / "app.js").read_bytes())
        )
        with mock.patch.object(MODULE, "MAX_GZIP_DECOMPRESSED_BYTES", 1):
            with self.assertRaisesRegex(ValueError, "decompression budget"):
                self.verify()

    def test_hash_and_semantic_checks_share_a_stable_read_only_snapshot(self):
        expected = self.hashes()
        original_copy = MODULE._copy_regular_file
        changed = False

        def mutate_after_oa_copy(source, destination, *, max_bytes):
            nonlocal changed
            original_copy(source, destination, max_bytes=max_bytes)
            if Path(source) == self.oa and not changed:
                changed = True
                with self.oa.open("ab") as handle:
                    handle.write(b"MUTATED")

        with mock.patch.object(MODULE, "_copy_regular_file", mutate_after_oa_copy):
            with self.assertRaisesRegex(ValueError, "stable snapshot: OA JAR"):
                MODULE.verify_artifacts(
                    self.oa, self.system, self.dist, *expected
                )

        self.build_valid()
        observed = []
        original_verify_jar = MODULE._verify_jar

        def inspect_snapshot(path, *args, **kwargs):
            observed.append(Path(path))
            self.assertFalse(Path(path).stat().st_mode & stat.S_IWUSR)
            self.assertNotIn(Path(path), (self.oa, self.system))
            return original_verify_jar(path, *args, **kwargs)

        with mock.patch.object(MODULE, "_verify_jar", inspect_snapshot):
            self.verify()
        self.assertEqual(2, len(observed))

    def test_rejects_snapshot_mutation_after_initial_snapshot_hash(self):
        original_verify_jar = MODULE._verify_jar
        changed = False

        def mutate_verified_snapshot(path, *args, **kwargs):
            nonlocal changed
            result = original_verify_jar(path, *args, **kwargs)
            if Path(path).name == "oa.jar" and not changed:
                changed = True
                Path(path).chmod(0o644)
                with Path(path).open("ab") as handle:
                    handle.write(b"POST-HASH-MUTATION")
                Path(path).chmod(0o444)
            return result

        with mock.patch.object(MODULE, "_verify_jar", mutate_verified_snapshot):
            with self.assertRaisesRegex(
                ValueError, "snapshot changed during semantic verification: OA JAR"
            ):
                self.verify()

    def test_rejects_unpinned_and_mismatched_hashes_before_content_checks(self):
        oa_hash, system_hash, dist_hash = self.hashes()
        with self.assertRaisesRegex(ValueError, "expected SHA256 is missing, pending, or invalid"):
            MODULE.verify_artifacts(self.oa, self.system, self.dist, "PENDING", system_hash, dist_hash)
        with self.assertRaisesRegex(ValueError, "immutable artifact hash mismatch: OA JAR"):
            MODULE.verify_artifacts(self.oa, self.system, self.dist, "0" * 64, system_hash, dist_hash)

    def test_manifest_mode_accepts_finalized_inventory_and_blocks_pending(self):
        oa_hash, system_hash, dist_hash = self.hashes()
        manifest = self.root / "release.json"
        payload = {
            "schemaVersion": 1,
            "releaseId": MODULE.RELEASE_ID,
            "artifactEvidence": {
                "status": "verified",
                "oaJar": {"path": "oa.jar", "sha256": oa_hash},
                "systemJar": {"path": "system.jar", "sha256": system_hash},
                "frontendDist": {"path": "dist", "treeSha256": dist_hash},
            },
        }
        manifest.write_text(json.dumps(payload), encoding="utf-8")
        self.assertEqual(MODULE.RELEASE_ID, MODULE.verify_manifest(manifest, self.root)["releaseId"])

        payload["artifactEvidence"]["frontendDist"]["treeSha256"] = "PENDING"
        manifest.write_text(json.dumps(payload), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "expected SHA256 is missing, pending, or invalid"):
            MODULE.verify_manifest(manifest, self.root)

        payload["artifactEvidence"]["status"] = "build-pending"
        manifest.write_text(json.dumps(payload), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "inventory/hashes are pending"):
            MODULE.verify_manifest(manifest, self.root)

    def test_manifest_mode_can_forward_approved_source_provenance(self):
        self.build_valid(provenance=approved_provenance())
        oa_hash, system_hash, dist_hash = self.hashes()
        manifest = self.root / "release.json"
        manifest.write_text(
            json.dumps(
                {
                    "schemaVersion": 1,
                    "releaseId": MODULE.RELEASE_ID,
                    "artifactEvidence": {
                        "status": "verified",
                        "oaJar": {"path": "oa.jar", "sha256": oa_hash},
                        "systemJar": {"path": "system.jar", "sha256": system_hash},
                        "frontendDist": {
                            "path": "dist",
                            "treeSha256": dist_hash,
                        },
                    },
                }
            ),
            encoding="utf-8",
        )
        result = MODULE.verify_manifest(
            manifest,
            self.root,
            expected_candidate_commit=CANDIDATE_COMMIT,
            expected_patch_sha256=APPROVED_PATCH_SHA256,
            expected_source_manifest_sha256=APPROVED_SOURCE_MANIFEST_SHA256,
        )
        self.assertEqual(approved_provenance(), result["sourceProvenance"])

    def test_manifest_rejects_escape_and_frontend_hash_field_conflict(self):
        oa_hash, system_hash, dist_hash = self.hashes()
        manifest = self.root / "release.json"
        payload = {
            "schemaVersion": 1,
            "releaseId": MODULE.RELEASE_ID,
            "artifactEvidence": {
                "status": "verified",
                "oaJar": {"path": "oa.jar", "sha256": oa_hash},
                "systemJar": {"path": "system.jar", "sha256": system_hash},
                "frontendDist": {
                    "path": "dist", "sha256": dist_hash, "treeSha256": dist_hash
                },
            },
        }
        manifest.write_text(json.dumps(payload), encoding="utf-8")
        MODULE.verify_manifest(manifest, self.root)
        payload["artifactEvidence"]["frontendDist"]["treeSha256"] = "1" * 64
        manifest.write_text(json.dumps(payload), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "treeSha256 and sha256 disagree"):
            MODULE.verify_manifest(manifest, self.root)
        payload["artifactEvidence"]["frontendDist"]["treeSha256"] = dist_hash
        payload["artifactEvidence"]["oaJar"]["path"] = "../outside.jar"
        manifest.write_text(json.dumps(payload), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "escapes release root"):
            MODULE.verify_manifest(manifest, self.root)

    def test_manifest_rejects_non_object_and_duplicate_keys_without_traceback(self):
        manifest = self.root / "release.json"
        for scalar in ("null", "[]", '"release"'):
            with self.subTest(scalar=scalar):
                manifest.write_text(scalar, encoding="utf-8")
                with self.assertRaisesRegex(ValueError, "root must be a JSON object"):
                    MODULE.verify_manifest(manifest, self.root)
        manifest.write_text(
            '{"schemaVersion":1,"schemaVersion":1,"releaseId":"'
            + MODULE.RELEASE_ID
            + '"}',
            encoding="utf-8",
        )
        with self.assertRaisesRegex(ValueError, "duplicate release manifest JSON key"):
            MODULE.verify_manifest(manifest, self.root)

    def test_tree_hash_is_path_sensitive_and_rejects_symlinks(self):
        original = MODULE.directory_tree_sha256(self.dist)
        (self.dist / "nested").mkdir()
        (self.dist / "nested/copy.js").write_bytes((self.dist / "app.js").read_bytes())
        self.assertNotEqual(original, MODULE.directory_tree_sha256(self.dist))
        link = self.dist / "linked.js"
        try:
            link.symlink_to(self.dist / "app.js")
        except (OSError, NotImplementedError):
            self.skipTest("symlinks unavailable")
        with self.assertRaisesRegex(ValueError, "contains a symlink"):
            MODULE.directory_tree_sha256(self.dist)

        link.unlink()
        directory_link = self.dist / "linked-dir"
        directory_link.symlink_to(self.dist, target_is_directory=True)
        with self.assertRaisesRegex(ValueError, "contains a symlink"):
            MODULE.directory_tree_sha256(self.dist)

    def test_cli_returns_explicit_nonzero_for_pending_direct_hash(self):
        command = [
            sys.executable,
            str(SCRIPT),
            "--root",
            str(self.root),
            "--oa-jar",
            "oa.jar",
            "--system-jar",
            "system.jar",
            "--frontend-dist",
            "dist",
            "--expect-oa-sha256",
            "PENDING",
            "--expect-system-sha256",
            hashlib.sha256(b"system").hexdigest(),
            "--expect-frontend-tree-sha256",
            hashlib.sha256(b"dist").hexdigest(),
        ]
        completed = subprocess.run(command, text=True, capture_output=True, check=False)
        self.assertEqual(1, completed.returncode)
        self.assertIn("[FAIL]", completed.stderr)
        self.assertIn("pending", completed.stderr)

    def test_cli_accepts_grouped_approved_source_provenance(self):
        self.build_valid(provenance=approved_provenance())
        oa_hash, system_hash, dist_hash = self.hashes()
        command = [
            sys.executable,
            str(SCRIPT),
            "--root",
            str(self.root),
            "--oa-jar",
            "oa.jar",
            "--system-jar",
            "system.jar",
            "--frontend-dist",
            "dist",
            "--expect-oa-sha256",
            oa_hash,
            "--expect-system-sha256",
            system_hash,
            "--expect-frontend-tree-sha256",
            dist_hash,
            "--expect-candidate-commit",
            CANDIDATE_COMMIT,
            "--expect-patch-sha256",
            APPROVED_PATCH_SHA256,
            "--expect-source-manifest-sha256",
            APPROVED_SOURCE_MANIFEST_SHA256,
        ]
        completed = subprocess.run(command, text=True, capture_output=True, check=False)
        self.assertEqual(0, completed.returncode, completed.stderr)
        self.assertIn("[PASS]", completed.stdout)


if __name__ == "__main__":
    unittest.main()

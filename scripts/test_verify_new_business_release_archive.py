#!/usr/bin/env python3

import importlib.util
import io
import json
import tarfile
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
SCRIPT = ROOT / "scripts/verify_new_business_release_archive.py"
SPEC = importlib.util.spec_from_file_location("release_archive", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class ReleaseArchiveTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.archive = Path(self.temp.name) / "release.tar.gz"
        self.manifest = json.loads(
            (ROOT / "scripts/new-business-release-20260714.json").read_text(
                encoding="utf-8"
            )
        )

    def tearDown(self):
        self.temp.cleanup()

    @staticmethod
    def add_bytes(archive, name, content):
        info = tarfile.TarInfo(name)
        info.size = len(content)
        archive.addfile(info, io.BytesIO(content))

    def build(self, *, extra_files=None, omit_first_sql=False, omit_jar=None):
        with tarfile.open(self.archive, "w:gz") as archive:
            manifest_bytes = json.dumps(self.manifest).encode("utf-8")
            self.add_bytes(archive, MODULE.MANIFEST_MEMBER, manifest_bytes)
            release_list = "\n".join(
                item["file"] for item in self.manifest["migrations"]
            ).encode("utf-8") + b"\n"
            self.add_bytes(archive, MODULE.LIST_MEMBER, release_list)
            for index, migration in enumerate(self.manifest["migrations"]):
                if omit_first_sql and index == 0:
                    continue
                content = (ROOT / "sql" / migration["file"]).read_bytes()
                self.add_bytes(
                    archive,
                    f"docker/mysql/releases/{self.manifest['releaseId']}/{migration['file']}",
                    content,
                )
            self.add_bytes(archive, "docker/nginx/html/dist/index.html", b"ok")
            for jar in sorted(MODULE.REQUIRED_BACKEND_JARS):
                if jar != omit_jar:
                    self.add_bytes(archive, jar, b"jar")
            for name, content in (extra_files or {}).items():
                self.add_bytes(archive, name, content)

    def test_accepts_manifest_verified_archive(self):
        self.build()
        result = MODULE.verify_archive(self.archive)
        self.assertEqual(self.manifest["migrationCount"], result["migrationCount"])
        self.assertEqual(9, result["backendJarCount"])

    def test_rejects_missing_approval_jar(self):
        approval = "docker/erp/modules/approval/jar/erp-modules-approval.jar"
        self.build(omit_jar=approval)
        with self.assertRaisesRegex(ValueError, "required backend jars are missing.*approval"):
            MODULE.verify_archive(self.archive)

    def test_rejects_unexpected_stale_jar(self):
        self.build(extra_files={"docker/erp/modules/legacy/jar/erp-legacy.jar": b"stale"})
        with self.assertRaisesRegex(ValueError, "unexpected backend jars.*legacy"):
            MODULE.verify_archive(self.archive)

    def test_rejects_environment_file(self):
        self.build(extra_files={"docker/.env": b"SECRET=value"})
        with self.assertRaisesRegex(ValueError, "forbidden"):
            MODULE.verify_archive(self.archive)

    def test_allows_documented_environment_example(self):
        self.build(extra_files={"docker/.env.example": b"KEY=replace-me"})
        MODULE.verify_archive(self.archive)

    def test_rejects_runtime_data_and_local_copies(self):
        cases = {
            "docker/mysql/seed/customer.sql": "data directory",
            "docker/erp/uploadPath/customer.pdf": "data directory",
            "docker/mysql/backups/prod.sql": "data directory",
            "docker/nginx/html/dist/index 2.html": "Finder-style duplicate",
            "docker/release/users.xlsx": "data/secret file",
            "docker/release/database.sql.gz": "data/secret file",
            "docker/release/signing.p12": "data/secret file",
        }
        for name, message in cases.items():
            with self.subTest(name=name):
                self.build(extra_files={name: b"sensitive"})
                with self.assertRaisesRegex(ValueError, message):
                    MODULE.verify_archive(self.archive)

    def test_rejects_nested_environment_and_private_key_files(self):
        cases = {
            "docker/erp/.env.production": "environment file",
            "docker/erp/id_rsa": "private-key-like",
        }
        for name, message in cases.items():
            with self.subTest(name=name):
                self.build(extra_files={name: b"secret"})
                with self.assertRaisesRegex(ValueError, message):
                    MODULE.verify_archive(self.archive)

    def test_rejects_missing_migration(self):
        self.build(omit_first_sql=True)
        with self.assertRaisesRegex(ValueError, "migration is missing"):
            MODULE.verify_archive(self.archive)

    def test_rejects_manifest_migration_count_drift(self):
        self.manifest["migrationCount"] += 1
        self.build()
        with self.assertRaisesRegex(ValueError, "migration count"):
            MODULE.verify_archive(self.archive)


if __name__ == "__main__":
    unittest.main()

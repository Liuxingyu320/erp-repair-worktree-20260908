import copy
import contextlib
import datetime as dt
import plistlib
import shutil
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

from verify_ios_push_ipa import (inspect_ipa, validate_metadata, verified_profile_contents,
                                 verify_profile_certificate, verify_apple_code_signature)


class SignedPushArtifactTest(unittest.TestCase):
    def setUp(self):
        self.now = dt.datetime(2026, 9, 12, tzinfo=dt.timezone.utc)
        self.info = {"CFBundleIdentifier": "com.erp.mobile"}
        self.signed = {
            "application-identifier": "APPREFIX.com.erp.mobile",
            "com.apple.developer.team-identifier": "TEAM123",
            "aps-environment": "production",
            "get-task-allow": False,
        }
        self.profile = {
            "TeamIdentifier": ["TEAM123"],
            "ApplicationIdentifierPrefix": ["APPREFIX"],
            "Entitlements": copy.deepcopy(self.signed),
            "ExpirationDate": dt.datetime(2027, 1, 1),
            "ProvisionedDevices": ["test-device"],
        }

    def check(self, **extra):
        return validate_metadata(self.info, self.signed, self.profile, "com.erp.mobile",
                                 "TEAM123", now=self.now, **extra)

    def test_valid_profile_with_legacy_prefix_is_accepted_without_claiming_delivery(self):
        result = self.check(device_udid="test-device")
        self.assertTrue(result["ok"])
        self.assertFalse(result["deviceDeliveryVerified"])
        self.assertEqual("ad-hoc", result["distribution"])

    def test_re_signing_must_preserve_bundle_team_and_push(self):
        mutations = [
            ("CFBundleIdentifier", "com.other.app", self.info),
            ("com.apple.developer.team-identifier", "OTHER", self.signed),
            ("aps-environment", "development", self.signed),
            ("get-task-allow", True, self.signed),
        ]
        for key, value, target in mutations:
            with self.subTest(key=key):
                before = target[key]
                target[key] = value
                self.assertFalse(self.check()["ok"])
                target[key] = before

    def test_profile_must_authorize_exact_app_and_environment(self):
        self.profile["Entitlements"]["application-identifier"] = "APPREFIX.*"
        self.assertFalse(self.check()["ok"])
        self.profile["Entitlements"] = copy.deepcopy(self.signed)
        del self.profile["Entitlements"]["aps-environment"]
        self.assertFalse(self.check()["ok"])

    def test_expiry_and_device_scope_are_checked(self):
        self.assertFalse(self.check(device_udid="another-device")["ok"])
        self.profile["ExpirationDate"] = self.now
        self.assertFalse(self.check()["ok"])

    def test_app_store_package_does_not_pass_as_direct_install(self):
        del self.profile["ProvisionedDevices"]
        self.assertFalse(self.check(device_udid="test-device")["ok"])

    def test_archive_inside_symlinked_temporary_parent_reaches_signature_checks(self):
        with tempfile.TemporaryDirectory() as parent:
            parent = Path(parent).resolve()
            real = parent / "real"
            real.mkdir()
            alias = parent / "alias"
            alias.symlink_to(real, target_is_directory=True)
            ipa = parent / "test.ipa"
            with zipfile.ZipFile(ipa, "w") as archive:
                archive.writestr("Payload/App.app/Info.plist", plistlib.dumps(self.info))
                archive.writestr("Payload/App.app/embedded.mobileprovision", "profile fixture")
            self.profile["ExpirationDate"] = dt.datetime(2099, 1, 1)
            self.profile["DeveloperCertificates"] = [b"authorized-leaf-der"]
            with patch("verify_ios_push_ipa.tempfile.TemporaryDirectory",
                       return_value=contextlib.nullcontext(str(alias))), patch(
                    "verify_ios_push_ipa.checked_output",
                    return_value=plistlib.dumps(self.signed)) as commands, patch(
                    "verify_ios_push_ipa.verify_apple_code_signature", return_value=b"authorized-leaf-der"), patch(
                    "verify_ios_push_ipa.verified_profile_contents", return_value=plistlib.dumps(self.profile)):
                result = inspect_ipa(ipa, "com.erp.mobile", "TEAM123", "production")
            self.assertTrue(result["ok"], result["failures"])
            self.assertEqual(1, commands.call_count)
            self.assertTrue(result["profileSignatureVerified"])
            self.assertFalse(result["deviceInstallationVerified"])
            self.assertFalse(result["onlineRevocationVerified"])

    def test_signing_leaf_must_be_explicitly_authorized_by_verified_profile(self):
        for profile in [{}, {"DeveloperCertificates": []},
                        {"DeveloperCertificates": [b"different-certificate"]}]:
            with self.subTest(profile=profile), self.assertRaisesRegex(ValueError, "not authorized"):
                verify_profile_certificate(profile, b"signing-certificate")
        verify_profile_certificate({"DeveloperCertificates": [b"signing-certificate"]}, b"signing-certificate")

    def test_code_requirement_uses_apple_anchor_and_certificate_team(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "app-signing-certificate-0").write_bytes(b"leaf-der")
            with patch("verify_ios_push_ipa.checked_output", return_value=b"") as commands:
                self.assertEqual(b"leaf-der", verify_apple_code_signature(root / "App.app", "TEAM123", root))
            requirement = commands.call_args_list[0].args[0]
            self.assertIn('=anchor apple generic and certificate leaf[subject.OU] = "TEAM123"', requirement)
        with self.assertRaisesRegex(ValueError, "Team ID"):
            verify_apple_code_signature(Path("App.app"), 'TEAM" or true', Path("."))


@unittest.skipUnless(sys.platform == "darwin" and shutil.which("openssl") and shutil.which("codesign"),
                     "real Apple Security and codesign negative tests require macOS")
class RealUntrustedArtifactTest(unittest.TestCase):
    """No developer account, keychain key or notifications: all fixtures are temporary."""

    def run_tool(self, *command):
        result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)
        self.assertEqual(0, result.returncode, "temporary test fixture setup failed: " + command[0])

    def test_unsigned_cms_content_is_rejected_before_using_its_profile(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "profile.plist").write_bytes(plistlib.dumps({"TeamIdentifier": ["FAKETEAM"]}))
            self.run_tool("openssl", "cms", "-data_create", "-binary", "-in", str(root / "profile.plist"),
                          "-outform", "DER", "-out", str(root / "unsigned.mobileprovision"))
            with self.assertRaisesRegex(ValueError, "authenticated Apple signer"):
                verified_profile_contents(root / "unsigned.mobileprovision")

    def test_self_signed_cms_with_apple_signer_name_has_no_apple_trust(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "profile.plist").write_bytes(plistlib.dumps({"TeamIdentifier": ["FAKETEAM"]}))
            self.run_tool("openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-days", "1",
                          "-subj", "/CN=Apple iPhone OS Provisioning Profile Signing/O=Apple Inc.",
                          "-keyout", str(root / "temporary-test.key"), "-out", str(root / "temporary-test.pem"))
            self.run_tool("openssl", "cms", "-sign", "-binary", "-nodetach", "-in", str(root / "profile.plist"),
                          "-signer", str(root / "temporary-test.pem"), "-inkey", str(root / "temporary-test.key"),
                          "-outform", "DER", "-out", str(root / "fake.mobileprovision"))
            with self.assertRaisesRegex(ValueError, "not trusted"):
                verified_profile_contents(root / "fake.mobileprovision")

    def test_ad_hoc_signed_ipa_with_fabricated_profile_is_rejected(self):
        # Regression for the actual exploit: codesign --verify and security
        # cms -D accepted this app before explicit signature trust was added.
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            app = root / "Payload/App.app"
            app.mkdir(parents=True)
            info = {"CFBundleIdentifier": "com.erp.mobile", "CFBundleExecutable": "App",
                    "CFBundleName": "App", "CFBundleVersion": "1", "CFBundlePackageType": "APPL"}
            entitlements = {"application-identifier": "FAKETEAM.com.erp.mobile",
                            "com.apple.developer.team-identifier": "FAKETEAM",
                            "aps-environment": "production", "get-task-allow": False}
            profile = {"TeamIdentifier": ["FAKETEAM"], "ApplicationIdentifierPrefix": ["FAKETEAM"],
                       "Entitlements": entitlements, "ExpirationDate": dt.datetime(2099, 1, 1),
                       "ProvisionsAllDevices": True}
            (app / "Info.plist").write_bytes(plistlib.dumps(info))
            (root / "entitlements.plist").write_bytes(plistlib.dumps(entitlements))
            (root / "profile.plist").write_bytes(plistlib.dumps(profile))
            shutil.copyfile("/usr/bin/true", app / "App")
            (app / "App").chmod(0o755)
            self.run_tool("openssl", "cms", "-data_create", "-binary", "-in", str(root / "profile.plist"),
                          "-outform", "DER", "-out", str(app / "embedded.mobileprovision"))
            self.run_tool("codesign", "--force", "--sign", "-", "--entitlements",
                          str(root / "entitlements.plist"), str(app))
            # Demonstrate that the old checks really accept the negative fixture.
            self.run_tool("codesign", "--verify", "--deep", "--strict", str(app))
            self.run_tool("security", "cms", "-D", "-i", str(app / "embedded.mobileprovision"))
            ipa = root / "fabricated.ipa"
            with zipfile.ZipFile(ipa, "w") as archive:
                for file in app.rglob("*"):
                    if file.is_file():
                        archive.write(file, str(file.relative_to(root)))
            with self.assertRaisesRegex(ValueError, "Apple-issued code signature verification failed"):
                inspect_ipa(ipa, "com.erp.mobile", "FAKETEAM", "production")


if __name__ == "__main__":
    unittest.main()

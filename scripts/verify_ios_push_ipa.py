#!/usr/bin/env python3
"""Check a signed iOS delivery artifact. Does not send notifications."""

import argparse
import ctypes
import datetime as dt
import json
import plistlib
import re
import stat
import subprocess
import tempfile
import zipfile
from pathlib import Path


def validate_metadata(info, signed, profile, bundle_id, team_id, environment="production",
                      now=None, device_udid=None):
    now = now or dt.datetime.now(dt.timezone.utc)
    failures = []
    profile_entitlements = profile.get("Entitlements", {})
    if info.get("CFBundleIdentifier") != bundle_id:
        failures.append("Bundle ID differs from the configured ERP app")
    if signed.get("com.apple.developer.team-identifier") != team_id:
        failures.append("signed Team ID differs from the supplied signing team")
    if team_id not in profile.get("TeamIdentifier", []):
        failures.append("provisioning profile belongs to a different team")
    identifier = signed.get("application-identifier", "")
    prefixes = profile.get("ApplicationIdentifierPrefix", [])
    if identifier not in [prefix + "." + bundle_id for prefix in prefixes]:
        failures.append("application-identifier does not match the app and profile prefix")
    if profile_entitlements.get("application-identifier") != identifier:
        failures.append("profile must authorize this exact push-enabled App ID")
    for name, entitlements in [("signed app", signed), ("profile", profile_entitlements)]:
        if entitlements.get("aps-environment") != environment:
            failures.append(name + " has missing or mismatched APNs environment")
        if environment == "production" and entitlements.get("get-task-allow") is not False:
            failures.append(name + " must disable debugger entitlement for production")
    expiry = profile.get("ExpirationDate")
    if isinstance(expiry, dt.datetime):
        expiry = expiry.replace(tzinfo=expiry.tzinfo or dt.timezone.utc)
    else:
        expiry = None
    if expiry is None or expiry <= now:
        failures.append("provisioning profile is expired or has no valid expiry")
    devices = profile.get("ProvisionedDevices", [])
    distribution = "enterprise" if profile.get("ProvisionsAllDevices") is True else (
        "ad-hoc" if devices else "app-store")
    if device_udid and distribution == "ad-hoc" and device_udid not in devices:
        failures.append("the intended iPhone is not included in this ad-hoc profile")
    if device_udid and distribution == "app-store":
        failures.append("this App Store profile cannot authorize direct installation on the device")
    return {
        "ok": not failures,
        "failures": failures,
        "bundleId": info.get("CFBundleIdentifier"),
        "teamId": signed.get("com.apple.developer.team-identifier"),
        "apnsEnvironment": signed.get("aps-environment"),
        "distribution": distribution,
        "expiresAt": expiry.isoformat() if expiry else None,
        "deviceDeliveryVerified": False,
    }


def checked_output(command, label):
    result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)
    if result.returncode != 0:
        raise ValueError(label + " failed")
    return result.stdout


def verified_profile_contents(filename):
    """Verify CMS signature AND Apple's dedicated iPhone profile signing policy.

    `security cms -D` alone only decodes content. Use the installed Security
    framework's policy rather than trusting certificate names or a caller's CA.
    Apple's policy requires an Apple anchor and the specific profile signing
    chain. Fail closed if a future macOS removes this policy entry point.
    Policy source: https://github.com/apple-oss-distributions/Security/blob/main/OSX/sec/Security/SecPolicy.c
    CMS API: the macOS SDK's Security.framework/Headers/CMSDecoder.h.
    This policy performs no network revocation checks; device acceptance remains
    mandatory and is deliberately not claimed by this local artifact check.
    """
    try:
        security = ctypes.CDLL("/System/Library/Frameworks/Security.framework/Security")
        core = ctypes.CDLL("/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation")
        pointer = ctypes.c_void_p
        pointer_out = ctypes.POINTER(pointer)
        signatures = {
            "CMSDecoderCreate": ([pointer_out], ctypes.c_int32),
            "CMSDecoderUpdateMessage": ([pointer, pointer, ctypes.c_size_t], ctypes.c_int32),
            "CMSDecoderFinalizeMessage": ([pointer], ctypes.c_int32),
            "CMSDecoderGetNumSigners": ([pointer, ctypes.POINTER(ctypes.c_size_t)], ctypes.c_int32),
            "CMSDecoderCopySignerStatus": ([pointer, ctypes.c_size_t, pointer, ctypes.c_ubyte,
                ctypes.POINTER(ctypes.c_uint32), pointer_out, ctypes.POINTER(ctypes.c_int32)], ctypes.c_int32),
            "CMSDecoderCopyContent": ([pointer, pointer_out], ctypes.c_int32),
            "SecPolicyCreateiPhoneProvisioningProfileSigning": ([], pointer),
        }
        for name, (arguments, result) in signatures.items():
            function = getattr(security, name)
            function.argtypes, function.restype = arguments, result
        core.CFRelease.argtypes, core.CFRelease.restype = [pointer], None
        core.CFDataGetLength.argtypes, core.CFDataGetLength.restype = [pointer], ctypes.c_long
        core.CFDataGetBytePtr.argtypes, core.CFDataGetBytePtr.restype = [pointer], pointer
    except (OSError, AttributeError) as error:
        raise ValueError("Apple provisioning-profile trust policy is unavailable on this host") from error

    decoder, policy, content = pointer(), pointer(), pointer()
    try:
        payload = Path(filename).read_bytes()
        if not payload or len(payload) > 16 * 1024**2:
            raise ValueError("provisioning profile is empty or exceeds the inspection size limit")
        raw = ctypes.create_string_buffer(payload)
        if (security.CMSDecoderCreate(ctypes.byref(decoder)) != 0
                or security.CMSDecoderUpdateMessage(decoder, raw, len(payload)) != 0
                or security.CMSDecoderFinalizeMessage(decoder) != 0):
            raise ValueError("provisioning profile is not a valid CMS message")
        count = ctypes.c_size_t()
        if security.CMSDecoderGetNumSigners(decoder, ctypes.byref(count)) != 0 or count.value != 1:
            raise ValueError("provisioning profile must have exactly one authenticated Apple signer")
        policy = pointer(security.SecPolicyCreateiPhoneProvisioningProfileSigning())
        if not policy:
            raise ValueError("Apple provisioning-profile trust policy is unavailable on this host")
        signer_status, certificate_result = ctypes.c_uint32(), ctypes.c_int32()
        result = security.CMSDecoderCopySignerStatus(decoder, 0, policy, True,
                ctypes.byref(signer_status), None, ctypes.byref(certificate_result))
        # kCMSSignerValid == 1, errSecSuccess == 0. Both are required: decoding
        # or a cryptographically self-consistent signature is not Apple trust.
        if result != 0 or signer_status.value != 1 or certificate_result.value != 0:
            raise ValueError("provisioning profile signature is not trusted by Apple's iPhone profile policy")
        if security.CMSDecoderCopyContent(decoder, ctypes.byref(content)) != 0 or not content:
            raise ValueError("authenticated provisioning profile has no embedded content")
        return ctypes.string_at(core.CFDataGetBytePtr(content), core.CFDataGetLength(content))
    finally:
        for resource in (content, policy, decoder):
            if resource:
                core.CFRelease(resource)


def verify_profile_certificate(profile, certificate):
    authorized = profile.get("DeveloperCertificates", [])
    if not certificate or not isinstance(authorized, list) or certificate not in authorized:
        raise ValueError("app signing certificate is not authorized by the authenticated provisioning profile")


def verify_apple_code_signature(app, team_id, extraction_root):
    # The explicit Apple anchor rejects ad-hoc and self-signed code. The OU
    # binds the *certificate*, not just arbitrary signed entitlement strings.
    if not re.fullmatch(r"[A-Za-z0-9]+", team_id):
        raise ValueError("signing Team ID must contain only letters and digits")
    requirement = '=anchor apple generic and certificate leaf[subject.OU] = "' + team_id + '"'
    checked_output(["codesign", "--verify", "--deep", "--strict", "-R", requirement, str(app)],
                   "Apple-issued code signature verification")
    prefix = extraction_root / "app-signing-certificate-"
    checked_output(["codesign", "-d", "--extract-certificates", str(prefix), str(app)],
                   "app signing certificate extraction")
    leaf = Path(str(prefix) + "0")
    if not leaf.is_file():
        raise ValueError("app has no extractable Apple-issued signing certificate")
    return leaf.read_bytes()


def inspect_ipa(filename, bundle_id, team_id, environment, device_udid=None):
    with tempfile.TemporaryDirectory(prefix="erp-ipa-check-") as temp:
        root = Path(temp).resolve()
        with zipfile.ZipFile(filename) as archive:
            if sum(entry.file_size for entry in archive.infolist()) > 2 * 1024**3:
                raise ValueError("IPA exceeds the inspection size limit")
            for entry in archive.infolist():
                target = (root / entry.filename).resolve()
                if not target.is_relative_to(root) or stat.S_ISLNK(entry.external_attr >> 16):
                    raise ValueError("IPA contains an unsafe archive path")
            archive.extractall(root)
            for entry in archive.infolist():
                mode = stat.S_IMODE(entry.external_attr >> 16) & 0o777
                if mode:
                    (root / entry.filename).chmod(mode)
        apps = list((root / "Payload").glob("*.app"))
        if len(apps) != 1:
            raise ValueError("IPA must contain one main application")
        app = apps[0]
        certificate = verify_apple_code_signature(app, team_id, root)
        signed_xml = checked_output(["codesign", "-d", "--entitlements", ":-", str(app)], "signed entitlements")
        signed = plistlib.loads(signed_xml)
        profile = plistlib.loads(verified_profile_contents(app / "embedded.mobileprovision"))
        verify_profile_certificate(profile, certificate)
        info = plistlib.loads((app / "Info.plist").read_bytes())
        result = validate_metadata(info, signed, profile, bundle_id, team_id, environment, device_udid=device_udid)
        result["signatureVerified"] = True
        result["appleSigningChainVerified"] = True
        result["profileSignatureVerified"] = True
        result["signingCertificateAuthorizedByProfile"] = True
        result["onlineRevocationVerified"] = False
        result["deviceInstallationVerified"] = False
        return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("ipa", type=Path)
    parser.add_argument("--bundle-id", required=True)
    parser.add_argument("--team-id", required=True)
    parser.add_argument("--environment", choices=("production", "development"), default="production")
    parser.add_argument("--device-udid")
    args = parser.parse_args()
    try:
        result = inspect_ipa(args.ipa, args.bundle_id, args.team_id, args.environment, args.device_udid)
    except (OSError, ValueError, zipfile.BadZipFile, plistlib.InvalidFileException) as error:
        result = {"ok": False, "failures": [str(error)], "deviceDeliveryVerified": False}
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0 if result["ok"] else 1


if __name__ == "__main__":
    raise SystemExit(main())

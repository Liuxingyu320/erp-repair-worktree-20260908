#!/usr/bin/env python3
"""Call an ERP service with an existing active admin session without exposing tokens."""

import base64
import hashlib
import hmac
import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.request
import zipfile
from pathlib import Path


OA_JAR = Path("/opt/erp-new/erp/modules/oa/jar/erp-modules-oa.jar")
OA_SERVICE = "erp-new@oa.service"


def service_environment():
    output = subprocess.check_output(
        ["systemctl", "show", "-p", "MainPID", "--value", OA_SERVICE],
        universal_newlines=True,
    ).strip()
    raw = Path("/proc/{}/environ".format(int(output))).read_bytes()
    result = {}
    for item in raw.split(b"\0"):
        if b"=" in item:
            key, value = item.split(b"=", 1)
            result[key.decode(errors="ignore")] = value.decode(errors="ignore")
    return result


def bootstrap_text():
    with zipfile.ZipFile(OA_JAR) as jar:
        return jar.read("BOOT-INF/classes/bootstrap.yml").decode()


def default_value(text, name, fallback=""):
    match = re.search(r"\$\{" + re.escape(name) + r":([^}]*)\}", text)
    return match.group(1) if match else fallback


def redis_command(password, *args):
    env = os.environ.copy()
    if password:
        env["REDISCLI_AUTH"] = password
    return subprocess.check_output(
        ["redis-cli", "--raw"] + list(args),
        env=env,
        universal_newlines=True,
        stderr=subprocess.DEVNULL,
    ).strip()


def active_admin_user_key(password):
    members = redis_command(password, "SMEMBERS", "user_login_tokens:1").splitlines()
    candidates = []
    for member in members:
        if not member:
            continue
        if redis_command(password, "EXISTS", "login_tokens:" + member) != "1":
            continue
        ttl_text = redis_command(password, "TTL", "login_tokens:" + member)
        try:
            ttl = int(ttl_text)
        except ValueError:
            ttl = -2
        candidates.append((ttl, member))
    if not candidates:
        raise RuntimeError("No active admin session is available")
    candidates.sort(reverse=True)
    return candidates[0][1]


def b64url(value):
    return base64.urlsafe_b64encode(value).rstrip(b"=")


def jwt_token(secret, user_key):
    header = b64url(json.dumps({"alg": "HS512"}, separators=(",", ":")).encode())
    claims = b64url(
        json.dumps(
            {"user_key": user_key, "user_id": 1, "username": "admin"},
            separators=(",", ":"),
            ensure_ascii=False,
        ).encode()
    )
    signing_input = header + b"." + claims
    signature = b64url(hmac.new(secret.encode(), signing_input, hashlib.sha512).digest())
    return b".".join((header, claims, signature)).decode()


def main():
    request_spec = json.load(sys.stdin)
    method = request_spec.get("method", "GET").upper()
    path = request_spec["path"]
    if not path.startswith("/"):
        raise ValueError("API path must start with /")

    env = service_environment()
    bootstrap = bootstrap_text()
    redis_password = env.get("REDIS_PASSWORD")
    if redis_password is None:
        redis_password = default_value(bootstrap, "REDIS_PASSWORD", "")
    jwt_secret = env.get("ERP_JWT_SECRET") or default_value(
        bootstrap, "ERP_JWT_SECRET", "erp-local-dev-jwt-secret-8c0d4f2b79a1"
    )
    user_key = active_admin_user_key(redis_password)
    token = jwt_token(jwt_secret, user_key)

    body = request_spec.get("body")
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode()
    request = urllib.request.Request(
        "http://127.0.0.1:9204" + path,
        data=data,
        method=method,
        headers={
            "Authorization": "Bearer " + token,
            "user_id": "1",
            "username": "admin",
            "user_key": user_key,
            "Content-Type": "application/json",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            sys.stdout.buffer.write(response.read())
    except urllib.error.HTTPError as error:
        sys.stdout.buffer.write(error.read())
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

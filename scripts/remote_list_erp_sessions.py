#!/usr/bin/env python3
"""List active ERP sessions without exposing token identifiers or cached payloads."""

import importlib.util
import re


spec = importlib.util.spec_from_file_location("remote_erp_api", "/tmp/remote_erp_api.py")
api = importlib.util.module_from_spec(spec)
spec.loader.exec_module(api)

environment = api.service_environment()
bootstrap = api.bootstrap_text()
password = environment.get("REDIS_PASSWORD")
if password is None:
    password = api.default_value(bootstrap, "REDIS_PASSWORD", "")

keys = api.redis_command(password, "--scan", "--pattern", "user_login_tokens:*").splitlines()
for key in sorted(keys):
    user_id = key.rsplit(":", 1)[-1]
    sessions = []
    for member in api.redis_command(password, "SMEMBERS", key).splitlines():
        if not member or api.redis_command(password, "EXISTS", "login_tokens:" + member) != "1":
            continue
        cached = api.redis_command(password, "GET", "login_tokens:" + member)
        username_match = re.search(r'"username"\s*:\s*"([^"]+)"', cached)
        sessions.append(
            {
                "username": username_match.group(1) if username_match else "?",
                "has_template_permission": (
                    "oa:signPackage:template" in cached or "*:*:*" in cached
                ),
                "ttl_seconds": int(api.redis_command(password, "TTL", "login_tokens:" + member)),
            }
        )
    if sessions:
        print(user_id, sessions)

#!/bin/sh
set -eu

python3 - <<'PY'
import os
import sys
from urllib.parse import urlsplit


def fail(message):
    print(f"MOBILE_SESSION_CANARY_CONFIG_FAIL {message}", file=sys.stderr)
    raise SystemExit(1)


def required(name):
    value = os.environ.get(name, "").strip()
    if not value:
        fail(f"{name} is required")
    return value


def boolean(name):
    value = required(name).lower()
    if value not in {"true", "false"}:
        fail(f"{name} must be true or false")
    return value == "true"


def validate_origin(value, *, allow_http):
    if "*" in value:
        fail(f"wildcard origin is forbidden: {value}")
    parsed = urlsplit(value)
    allowed_schemes = {"https"}
    if allow_http:
        allowed_schemes.add("http")
    if (
        parsed.scheme not in allowed_schemes
        or not parsed.hostname
        or parsed.username is not None
        or parsed.password is not None
        or parsed.path not in {"",}
        or parsed.query
        or parsed.fragment
    ):
        fail(f"origin must be an exact scheme/host/optional-port value: {value}")


deploy_env = required("DEPLOY_ENV").lower()
if deploy_env not in {"local", "staging", "production"}:
    fail("DEPLOY_ENV must be local, staging, or production")

release_phase = required("RELEASE_PHASE").lower()
valid_phases = {
    "bearer-baseline",
    "backend-dual",
    "web-cookie-canary",
    "web-cookie-full",
    "rollback-bearer",
}
if release_phase not in valid_phases:
    fail("RELEASE_PHASE is not recognized")

auth_mode = required("AUTH_SESSION_MODE").lower()
gateway_mode = required("GATEWAY_SESSION_MODE").lower()
web_mode = required("WEB_SESSION_MODE").lower()
backend_modes = {"bearer", "dual", "cookie"}
if auth_mode not in backend_modes or gateway_mode not in backend_modes:
    fail("backend session mode must be bearer, dual, or cookie")
if web_mode not in {"bearer", "cookie"}:
    fail("WEB_SESSION_MODE must be bearer or cookie")
if auth_mode != gateway_mode:
    fail("Auth and Gateway session modes must be identical")

auth_revision = required("AUTH_CONFIG_REVISION")
gateway_revision = required("GATEWAY_CONFIG_REVISION")
if auth_revision != gateway_revision:
    fail("Auth and Gateway config revisions must be identical")
if auth_revision == "CHANGE-ME":
    fail("config revision must identify the approved effective config")

if required("COOKIE_NAME") != "ERP_SESSION":
    fail("COOKIE_NAME must remain ERP_SESSION during this migration")
if required("CSRF_COOKIE_NAME") != "XSRF-TOKEN":
    fail("CSRF_COOKIE_NAME must remain XSRF-TOKEN during this migration")

cookie_secure = boolean("COOKIE_SECURE")
cookie_http_only = boolean("COOKIE_HTTP_ONLY")
csrf_cookie_http_only = boolean("CSRF_COOKIE_HTTP_ONLY")
csrf_enabled = boolean("CSRF_ENABLED")
require_origin = boolean("REQUIRE_ORIGIN")
same_site = required("COOKIE_SAME_SITE")
if same_site not in {"Lax", "Strict", "None"}:
    fail("COOKIE_SAME_SITE must be Lax, Strict, or None")
if not cookie_http_only:
    fail("the session Cookie must be HttpOnly")
if csrf_cookie_http_only:
    fail("the CSRF Cookie must remain script-readable for double submit")
if same_site == "None" and not cookie_secure:
    fail("SameSite=None requires Secure=true")

public_origin = required("PUBLIC_ORIGIN")
origin_values = [item.strip() for item in required("ALLOWED_ORIGINS").split(",")]
if not origin_values or any(not item for item in origin_values):
    fail("ALLOWED_ORIGINS must be a non-empty comma-separated exact list")
if len(origin_values) != len(set(origin_values)):
    fail("ALLOWED_ORIGINS contains duplicates")

allow_http = deploy_env == "local"
validate_origin(public_origin, allow_http=allow_http)
for origin in origin_values:
    validate_origin(origin, allow_http=allow_http)
if public_origin not in origin_values:
    fail("PUBLIC_ORIGIN must be present in ALLOWED_ORIGINS")

native_bearer = boolean("NATIVE_BEARER_ENABLED")

if release_phase == "bearer-baseline":
    if (auth_mode, web_mode) != ("bearer", "bearer"):
        fail("bearer-baseline requires backend bearer and Web bearer")
elif release_phase in {"backend-dual", "rollback-bearer"}:
    if (auth_mode, web_mode) != ("dual", "bearer"):
        fail(f"{release_phase} requires backend dual and Web bearer")
elif release_phase in {"web-cookie-canary", "web-cookie-full"}:
    if web_mode != "cookie":
        fail(f"{release_phase} requires Web cookie mode")
    if auth_mode not in {"dual", "cookie"}:
        fail(f"{release_phase} requires a Cookie-capable backend")

if native_bearer and auth_mode == "cookie":
    fail("Native Bearer clients require the backend to remain dual")
if web_mode == "cookie" and (not csrf_enabled or not require_origin):
    fail("Web cookie mode requires CSRF and Origin enforcement")

if deploy_env != "local":
    if not cookie_secure:
        fail("non-local Cookie rollout requires Secure=true")
    if urlsplit(public_origin).scheme != "https":
        fail("non-local PUBLIC_ORIGIN must use https")
    for gate in (
        "EFFECTIVE_CONFIG_SNAPSHOT_RECORDED",
        "MULTI_ACCOUNT_UAT_PASSED",
        "ROLLBACK_ARTIFACT_READY",
        "MONITORING_READY",
    ):
        if not boolean(gate):
            fail(f"{gate} must be true for a non-local rollout")

print(
    "MOBILE_SESSION_CANARY_CONFIG_OK"
    f" env={deploy_env}"
    f" phase={release_phase}"
    f" backend={auth_mode}"
    f" web={web_mode}"
    f" origins={len(origin_values)}"
    f" native_bearer={'true' if native_bearer else 'false'}"
)
PY

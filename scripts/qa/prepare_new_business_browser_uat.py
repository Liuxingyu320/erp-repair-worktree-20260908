#!/usr/bin/env python3
"""Validate browser UAT config and emit safe tab-separated execution rows."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

from new_business_uat_common import UatConfigError, load_config


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    try:
        config = load_config(args.config, root)
        pages = config.get("browserPages")
        if not isinstance(pages, list) or not pages:
            raise UatConfigError("browserPages must be a non-empty array")
        for page in pages:
            if not isinstance(page, dict):
                raise UatConfigError("every browserPage must be an object")
            page_id = str(page.get("id") or "")
            actor_name = str(page.get("actor") or "")
            context_name = str(page.get("context") or "")
            path = str(page.get("path") or "")
            expected = str(page.get("expectedText") or "").strip()
            if not page_id or not all(
                ch.isalnum() or ch in "._-" for ch in page_id
            ):
                raise UatConfigError(
                    "browserPage.id must use safe characters"
                )
            if actor_name not in config["actors"]:
                raise UatConfigError(f"{page_id}: unknown actor")
            if context_name not in config["contexts"]:
                raise UatConfigError(f"{page_id}: unknown context")
            if not path.startswith("/") or "://" in path or "#" in path:
                raise UatConfigError(
                    f"{page_id}: path must be a same-origin absolute path"
                )
            if not expected or any(ch in expected for ch in "\t\r\n"):
                raise UatConfigError(
                    f"{page_id}: expectedText must be one line"
                )
            context = config["contexts"][context_name]
            values = [
                page_id,
                actor_name,
                config["actors"][actor_name]["storageState"],
                str(context["deptId"]),
                str(context["deptName"]),
                str(context["deptType"]),
                path,
                expected,
            ]
            if any(
                "\t" in value or "\n" in value or "\r" in value
                for value in values
            ):
                raise UatConfigError(
                    f"{page_id}: tab/newline is not allowed"
                )
            print("\t".join(values))
    except UatConfigError as exc:
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

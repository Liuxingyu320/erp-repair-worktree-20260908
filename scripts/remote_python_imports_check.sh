#!/usr/bin/env bash
set -euo pipefail

python3 - <<'PY'
for name in ["dataclasses", "typing", "subprocess", "hashlib"]:
    try:
        module = __import__(name)
    except Exception as exc:
        print("%s=missing %r" % (name, exc))
    else:
        print("%s=ok %s" % (name, getattr(module, "__version__", "")))
PY

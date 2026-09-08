#!/usr/bin/env bash
set -euo pipefail

for bin in python python2 python3 python3.6 python3.7 python3.8 python3.9 python3.10 python3.11 python3.12; do
  if command -v "$bin" >/dev/null 2>&1; then
    printf '%s=' "$bin"
    "$bin" --version 2>&1 || true
  fi
done

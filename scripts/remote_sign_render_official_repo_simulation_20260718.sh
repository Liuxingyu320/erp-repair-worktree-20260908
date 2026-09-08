#!/usr/bin/env bash
set -Euo pipefail
umask 077

echo "OFFICIAL_REPO_SIMULATION_BEGIN"
set +e
LC_ALL=C timeout 45s dnf --cacheonly \
  --disablerepo='*' \
  --enablerepo=alinux3-os,alinux3-updates,alinux3-plus,alinux3-powertools,alinux3-module \
  --setopt=install_weak_deps=False \
  install --assumeno \
  libreoffice-writer \
  google-noto-sans-cjk-ttc-fonts \
  poppler-utils 2>&1 | sed 's/^/DNF|official_only|/'
pipeline_rc=("${PIPESTATUS[@]}")
set -e
echo "OFFICIAL_REPO_SIMULATION_END|dnf_or_timeout_rc=${pipeline_rc[0]}|sed_rc=${pipeline_rc[1]}"
echo "OFFICIAL_REPO_SIMULATION_OK"

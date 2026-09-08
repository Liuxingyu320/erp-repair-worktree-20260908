#!/usr/bin/env bash
set -euo pipefail

jar=/opt/erp-new/erp/modules/oa/jar/erp-modules-oa.jar
[[ -f "$jar" ]]

for class in \
  com/erp/oa/service/impl/OaSignPlanVersionServiceImpl.class \
  com/erp/oa/service/impl/OaSignOnboardImportService.class \
  com/erp/oa/service/impl/OaSignTemplateServiceImpl.class \
  com/erp/oa/service/impl/OaSignPackagePreflightValidator.class \
  com/erp/oa/controller/OaSignPackageController.class
do
  hash="$(unzip -p "$jar" "BOOT-INF/classes/$class" | sha256sum | awk '{print $1}')"
  echo "$hash $class"
done

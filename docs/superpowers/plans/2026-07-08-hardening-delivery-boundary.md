# ERP Hardening Delivery Boundary

Date: 2026-07-08

## Purpose

This document separates the current hardening work from pre-existing workspace noise. It is intended to support a clean review, commit, or release package without accidentally including unrelated generated files, deployment artifacts, mobile QA outputs, or sign-package feature work.

No files were staged while creating this boundary.

## Recommended Commit Split

### Commit 1: Build and Security Verification Gates

Scope:
- Restore root Maven aggregation.
- Make controller security scanning deterministic.
- Keep the password policy endpoint public through the gateway.
- Prevent generated artifacts and local duplicate files from re-entering the worktree.

Files:
- `.gitignore`
- `pom.xml`
- `bin/check-controller-security.sh`
- `erp-gateway/src/main/resources/bootstrap.yml`

Suggested staging command:

```bash
git add -- .gitignore pom.xml bin/check-controller-security.sh erp-gateway/src/main/resources/bootstrap.yml
```

### Commit 2: Frontend Auth, Dependency, Toolchain, and Bundle Hardening

Scope:
- Remove reversible password storage in the login page.
- Remove `jsencrypt`.
- Add a full frontend test gate.
- Add production dependency audit gate.
- Upgrade audited production dependencies.
- Track `package-lock.json`.
- Pin the supported frontend Node range to Node 22.
- Adapt highlight.js v11.
- Use tree-shakeable ECharts registration for dashboard charts.
- Document Vue 2 low-severity advisory and frontend toolchain constraints.

Files:
- `erp-ui/.gitignore`
- `erp-ui/.nvmrc`
- `erp-ui/package.json`
- `erp-ui/package-lock.json`
- `erp-ui/scripts/run-node-tests.cjs`
- `erp-ui/src/utils/echarts.js`
- `erp-ui/src/utils/jsencrypt.js`
- `erp-ui/src/views/login.vue`
- `erp-ui/src/views/tool/gen/index.vue`
- `erp-ui/src/views/dashboard/BarChart.vue`
- `erp-ui/src/views/dashboard/LineChart.vue`
- `erp-ui/src/views/dashboard/PieChart.vue`
- `erp-ui/src/views/dashboard/RaddarChart.vue`
- `erp-ui/test/authSecurityHardening.test.js`
- `erp-ui/test/frontendTestGate.test.js`
- `erp-ui/test/mobileAppShell.test.js`
- `docs/frontend-toolchain.md`
- `docs/security/frontend-vue2-low-risk-advisory.md`
- `docs/superpowers/plans/2026-07-08-frontend-dependency-hardening.md`

Suggested staging command:

```bash
git add -- \
  erp-ui/.gitignore \
  erp-ui/.nvmrc \
  erp-ui/package.json \
  erp-ui/package-lock.json \
  erp-ui/scripts/run-node-tests.cjs \
  erp-ui/src/utils/echarts.js \
  erp-ui/src/utils/jsencrypt.js \
  erp-ui/src/views/login.vue \
  erp-ui/src/views/tool/gen/index.vue \
  erp-ui/src/views/dashboard/BarChart.vue \
  erp-ui/src/views/dashboard/LineChart.vue \
  erp-ui/src/views/dashboard/PieChart.vue \
  erp-ui/src/views/dashboard/RaddarChart.vue \
  erp-ui/test/authSecurityHardening.test.js \
  erp-ui/test/frontendTestGate.test.js \
  erp-ui/test/mobileAppShell.test.js \
  docs/frontend-toolchain.md \
  docs/security/frontend-vue2-low-risk-advisory.md \
  docs/superpowers/plans/2026-07-08-frontend-dependency-hardening.md
```

### Commit 3: Remove Local Duplicate Copy Files

Scope:
- Remove tracked Finder-style duplicate copies ending in ` 2.*`.
- Keep the cleanup separate because the diff is large and mostly deletions.

Files:
- `docker/mysql/db/erp_oa_labor_contract_20260612 2.sql`
- `docker/mysql/db/security_admin_privilege_audit_20260613 2.sql`
- `docs/superpowers/plans/2026-06-13-erp-permission-security-remediation-cn 2.md`
- `docs/superpowers/plans/2026-06-13-erp-security-hardening-overall 2.md`
- `docs/superpowers/plans/2026-06-13-transfer-approval-scope-remediation-cn 2.md`
- `erp-ui/src/api/oa/laborContract 2.js`
- `erp-ui/src/views/mobile/contract/index 2.vue`
- `erp-ui/src/views/mobile/mobileRouteDefinitions 2.js`
- `erp-ui/src/views/oa/laborContract/index 2.vue`
- `erp-ui/test/dockerScripts.test 2.js`
- `erp-ui/test/laborContractModule.test 2.js`
- `erp-ui/test/logoutInvalidToken.test 2.js`
- `erp-ui/test/userShopScopeUx.test 2.js`

Suggested staging command:

```bash
git add -- \
  "docker/mysql/db/erp_oa_labor_contract_20260612 2.sql" \
  "docker/mysql/db/security_admin_privilege_audit_20260613 2.sql" \
  "docs/superpowers/plans/2026-06-13-erp-permission-security-remediation-cn 2.md" \
  "docs/superpowers/plans/2026-06-13-erp-security-hardening-overall 2.md" \
  "docs/superpowers/plans/2026-06-13-transfer-approval-scope-remediation-cn 2.md" \
  "erp-ui/src/api/oa/laborContract 2.js" \
  "erp-ui/src/views/mobile/contract/index 2.vue" \
  "erp-ui/src/views/mobile/mobileRouteDefinitions 2.js" \
  "erp-ui/src/views/oa/laborContract/index 2.vue" \
  "erp-ui/test/dockerScripts.test 2.js" \
  "erp-ui/test/laborContractModule.test 2.js" \
  "erp-ui/test/logoutInvalidToken.test 2.js" \
  "erp-ui/test/userShopScopeUx.test 2.js"
```

### Commit 4: Delivery Boundary Document

Scope:
- Add this file so reviewers understand why the working tree contains unrelated changes that were intentionally excluded.

Files:
- `docs/superpowers/plans/2026-07-08-hardening-delivery-boundary.md`

Suggested staging command:

```bash
git add -- docs/superpowers/plans/2026-07-08-hardening-delivery-boundary.md
```

## Explicitly Excluded From This Hardening Scope

Do not include these in the hardening commits without a separate review:

- Modified generated deployment jars under `docker/erp/**/jar/*.jar`.
- Modified bytecode cache: `scripts/__pycache__/erp_employee_importer.cpython-314.pyc`.
- Sign-package and approval-preview work:
  - `erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SignCandidateUser.java`
  - `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaSignBatchPreviewRow.java`
  - `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignBatchServiceImpl.java`
  - `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignBatchServiceImplTest.java`
  - `erp-modules/erp-system/src/main/resources/mapper/system/SysUserMapper.xml`
  - `erp-modules/erp-system/src/test/java/com/erp/system/mapper/SysUserMapperSourceTest.java`
  - `erp-ui/src/views/oa/signPackage/index.vue`
  - `erp-ui/test/signPackageModule.test.js`
  - `docs/superpowers/plans/2026-07-08-sign-package-readiness-preview.md`
- Local mobile platform scaffolding:
  - `erp-ui/android/`
  - `erp-ui/ios/`
- Mobile QA screenshots and reports under `docs/audit-screenshots/`.
- Remote deployment, data audit, and one-off operational scripts under `scripts/remote_*`, `scripts/aliyun_*`, and related generated audit scripts.
- Local-only configuration:
  - `erp-modules/erp-file/src/main/resources/application-local.yml`
- Agent/runtime state:
  - `.claude-flow/`

## Verification Before Staging

Run these before staging or committing the recommended scope:

```bash
cd erp-ui && npm test
cd erp-ui && npm run build:prod
cd erp-ui && npm run audit:prod
mvn -q test
sh bin/check-controller-security.sh
```

Expected result:
- Frontend Node tests pass.
- Frontend production build exits 0. Existing bundle size warnings may remain.
- Production dependency audit exits 0 while reporting only the documented Vue 2 low-severity advisory.
- Maven tests exit 0.
- Controller security scan exits 0.

## Notes

- Use Node 22 for frontend install/build work. The repository now documents this in `erp-ui/.nvmrc` and `docs/frontend-toolchain.md`.
- Use Java 17 for the backend build, matching the Maven properties.
- Do not run destructive cleanup commands against the excluded files unless the user explicitly asks for that cleanup.

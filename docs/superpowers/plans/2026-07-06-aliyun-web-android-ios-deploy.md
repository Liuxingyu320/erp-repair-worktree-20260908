# Aliyun Web Android iOS Deployment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deploy the current ERP code to Aliyun while keeping desktop web unchanged, using a packaged Android shell, and using the web version directly on iPhone.

**Architecture:** The Aliyun server continues to host one ERP web frontend and one `/prod-api` gateway. Desktop browsers and iPhone Safari load the same deployed web app; mobile routing is handled by the existing Vue route guards and `/mobile/*` pages. Android uses the Capacitor shell in `erp-ui/android`; for production use it should load the Aliyun web URL through `CAPACITOR_SERVER_URL` so web updates do not require reinstalling the APK.

**Tech Stack:** Vue 2 + Vue CLI, Capacitor 8, Android Gradle Plugin 8.13, Java 17 for backend, Java 21 for Android Gradle, Spring Boot microservice jars, Nginx, MySQL, Redis, ECS Cloud Assistant, OSS package transfer.

---

## Current Findings

- Frontend app-shell contract passes with `npm run app:verify`.
- Production web build passes with `npm run build:prod`; only webpack asset-size warnings remain.
- Backend modules build after running `mvn clean`; old `target/classes/com 2/...` artifacts can break Spring Boot repackaging if `clean` is skipped.
- Current Android `assembleDebug` fails under local JDK 17 with `无效的源发行版：21`; Android packaging needs JDK 21.
- `deploy-host` currently applies only `erp_inventory_transfer_reference_amount_20260701.sql`; this deployment also needs:
  - `sql/erp_oa_sign_package_20260702.sql`
  - `sql/erp_inventory_store_transfer_receive_permission_20260703.sql`
  - `sql/erp_oa_purchase_permission_alignment_20260704.sql`

## Task 1: Build Server Artifacts Locally

**Files:**
- Read: `erp-common/pom.xml`
- Read: `erp-api/pom.xml`
- Read: `erp-auth/pom.xml`
- Read: `erp-gateway/pom.xml`
- Read: `erp-modules/*/pom.xml`
- Read: `erp-visual/erp-monitor/pom.xml`

- [ ] **Step 1: Install shared libraries**

```bash
cd /Users/liuxingyu/Desktop/备份/ERP-NEW
mvn -f erp-common/pom.xml clean -DskipTests install
mvn -f erp-api/pom.xml clean -DskipTests install
```

Expected: both commands finish with `BUILD SUCCESS`.

- [ ] **Step 2: Package backend services with clean**

```bash
cd /Users/liuxingyu/Desktop/备份/ERP-NEW
for pom in \
  erp-auth/pom.xml \
  erp-gateway/pom.xml \
  erp-modules/erp-system/pom.xml \
  erp-modules/erp-oa/pom.xml \
  erp-modules/erp-inventory/pom.xml \
  erp-modules/erp-file/pom.xml \
  erp-modules/erp-job/pom.xml \
  erp-modules/erp-gen/pom.xml \
  erp-visual/erp-monitor/pom.xml; do
  mvn -f "$pom" clean -DskipTests package
done
```

Expected: every module finishes with `BUILD SUCCESS`.

## Task 2: Build Web And Sync Android Shell

**Files:**
- Read: `erp-ui/package.json`
- Read: `erp-ui/capacitor.config.ts`
- Read: `erp-ui/.env.production`
- Output: `erp-ui/dist`
- Output: `erp-ui/android/app/src/main/assets/public`

- [ ] **Step 1: Verify app-shell contract**

```bash
cd /Users/liuxingyu/Desktop/备份/ERP-NEW/erp-ui
npm run app:verify
```

Expected: command exits 0.

- [ ] **Step 2: Build web for Aliyun**

```bash
cd /Users/liuxingyu/Desktop/备份/ERP-NEW/erp-ui
npm run build:prod
```

Expected: `dist` is generated. Asset-size warnings are acceptable for this deployment.

- [ ] **Step 3: Sync Android as a remote web shell**

Use the current public IP while no domain is configured:

```bash
cd /Users/liuxingyu/Desktop/备份/ERP-NEW/erp-ui
CAPACITOR_SERVER_URL=http://8.152.199.39 npm run app:sync
```

Expected: `android/app/src/main/assets/capacitor.config.json` contains:

```json
"server": {
  "url": "http://8.152.199.39",
  "cleartext": true
}
```

When HTTPS domain is ready, use the HTTPS URL instead:

```bash
cd /Users/liuxingyu/Desktop/备份/ERP-NEW/erp-ui
CAPACITOR_SERVER_URL=https://erp.example.com npm run app:sync
```

## Task 3: Build Android APK

**Files:**
- Read: `erp-ui/android/app/build.gradle`
- Read: `erp-ui/android/variables.gradle`
- Output: `erp-ui/android/app/build/outputs/apk/debug/app-debug.apk`

- [ ] **Step 1: Use JDK 21 for Android Gradle**

Install or select a JDK 21 runtime. Then run:

```bash
cd /Users/liuxingyu/Desktop/备份/ERP-NEW/erp-ui/android
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew assembleDebug
```

Expected: `BUILD SUCCESS` and `app-debug.apk` appears under `app/build/outputs/apk/debug/`.

- [ ] **Step 2: Build release APK for real distribution**

After adding a release keystore to local secure storage and configuring signing in Gradle, run:

```bash
cd /Users/liuxingyu/Desktop/备份/ERP-NEW/erp-ui/android
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew assembleRelease
```

Expected: signed APK appears under `app/build/outputs/apk/release/`.

## Task 4: Prepare Aliyun Deployment Package

**Files:**
- Read/Write: `docker/`
- Read: `docker/copy.sh`
- Read: `scripts/aliyun_ecs_deploy_helper.py`

- [ ] **Step 1: Copy current build artifacts into docker package tree**

```bash
cd /Users/liuxingyu/Desktop/备份/ERP-NEW
sh docker/copy.sh
```

Expected: current jars are copied into `docker/erp/**/jar/`, current web build is copied into `docker/nginx/html/dist`, and SQL files are copied into `docker/mysql/db`.

- [ ] **Step 2: Create a host deployment tarball**

```bash
cd /Users/liuxingyu/Desktop/备份/ERP-NEW
tar --exclude='docker/mysql/data' \
  --exclude='docker/redis/data' \
  --exclude='docker/nginx/logs' \
  --exclude='docker/erp/uploadPath' \
  -czf erp-aliyun-deploy-20260706-ecs-host.tar.gz docker
```

Expected: `erp-aliyun-deploy-20260706-ecs-host.tar.gz` is created.

## Task 5: Deploy To Aliyun ECS

**Files:**
- Read: `scripts/aliyun_ecs_deploy_helper.py`
- Read: `scripts/aliyun_apply_sql_helper.py`
- Read: generated tarball

- [ ] **Step 1: Deploy code package**

Use hidden terminal input for Aliyun credentials and run:

```bash
cd /Users/liuxingyu/Desktop/备份/ERP-NEW
python3 scripts/aliyun_ecs_deploy_helper.py deploy-host \
  --region cn-beijing \
  --instance-id i-2ze2pb4mhep0g9rmecpw \
  --package erp-aliyun-deploy-20260706-ecs-host.tar.gz \
  --cleanup-oss
```

Expected: remote output contains `DEPLOY_OK`.

- [ ] **Step 2: Apply current SQL patches**

Run these three commands after deployment:

```bash
cd /Users/liuxingyu/Desktop/备份/ERP-NEW
python3 scripts/aliyun_apply_sql_helper.py \
  --region cn-beijing \
  --instance-id i-2ze2pb4mhep0g9rmecpw \
  --sql sql/erp_oa_sign_package_20260702.sql \
  --database BossERP_stock_state_75c59ee \
  --backup-table sys_menu \
  --backup-table sys_role_menu \
  --backup-table oa_sign_template \
  --backup-table oa_sign_package \
  --backup-table oa_sign_package_document \
  --backup-table oa_sign_event \
  --cleanup-oss
```

```bash
cd /Users/liuxingyu/Desktop/备份/ERP-NEW
python3 scripts/aliyun_apply_sql_helper.py \
  --region cn-beijing \
  --instance-id i-2ze2pb4mhep0g9rmecpw \
  --sql sql/erp_inventory_store_transfer_receive_permission_20260703.sql \
  --database BossERP_stock_state_75c59ee \
  --backup-table sys_role_menu \
  --cleanup-oss
```

```bash
cd /Users/liuxingyu/Desktop/备份/ERP-NEW
python3 scripts/aliyun_apply_sql_helper.py \
  --region cn-beijing \
  --instance-id i-2ze2pb4mhep0g9rmecpw \
  --sql sql/erp_oa_purchase_permission_alignment_20260704.sql \
  --database BossERP_stock_state_75c59ee \
  --backup-table sys_menu \
  --backup-table sys_role_menu \
  --cleanup-oss
```

Expected: each command prints `SQL_APPLY_OK`.

## Task 6: Verify Production

**Files:**
- Read: `scripts/remote_deploy_verify.sh`
- Read: `scripts/remote_login_recent_probe.sh`

- [ ] **Step 1: Check public web/API**

```bash
curl -fsS http://8.152.199.39/ >/dev/null
curl -fsS http://8.152.199.39/prod-api/code >/dev/null
curl -fsS http://8.152.199.39/mobile/store >/dev/null
```

Expected: all commands exit 0.

- [ ] **Step 2: Check service status on ECS**

```bash
cd /Users/liuxingyu/Desktop/备份/ERP-NEW
python3 scripts/aliyun_ecs_deploy_helper.py run-shell \
  --region cn-beijing \
  --instance-id i-2ze2pb4mhep0g9rmecpw \
  --script-file scripts/remote_deploy_verify.sh \
  --name erp-deploy-verify \
  --timeout 300
```

Expected: gateway, auth, system, oa, inventory, file, job, and gen services are active.

## Usage After Deployment

- Desktop users: open `http://8.152.199.39/` in a desktop browser. The large-screen route remains the existing desktop ERP.
- iPhone users: open `http://8.152.199.39/` in Safari. After login and organization selection, mobile viewport routes go to `/mobile/store` or `/mobile/warehouse`.
- Android users: install the APK. The app shell loads the same Aliyun web deployment; web updates happen on the server without reinstalling the APK.
- Administrators: manage users, roles, posts, departments, store authorizations, and menu permissions from the existing desktop system pages.
- Update cycle: web/backend changes require ECS deployment; Android reinstall is only needed when app ID, app name, native permissions, signing, splash/icon, or `CAPACITOR_SERVER_URL` changes.

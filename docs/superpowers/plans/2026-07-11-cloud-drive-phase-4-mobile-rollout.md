# 企业云盘阶段 4：移动端、菜单与上线 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付移动端浏览、搜索、拍照/选文件上传、预览和下载；增加桌面一级菜单、手机两个入口、统一功能开关、角色权限、容器数据源、备份恢复和生产灰度验证。

**Architecture:** 移动端使用独立轻量页面并复用阶段 3 API，不暴露重命名、移动和回收站管理。`erp-system` 在 `drive.enabled=false` 时过滤云盘路由和 `drive:*` 权限，并通过 `getInfo.driveEnabled` 显式覆盖管理员通配权限；SQL 只为 admin 角色自动授权，普通员工和共享盘管理员通过现有角色管理显式授权。Docker 的 `DRIVE_ENABLED` 是 system/file 两个服务的同源开关。

**Tech Stack:** Vue 2, existing mobile shell/navigation, Capacitor 8, Java 17, Spring Boot configuration properties, MyBatis menu service, MySQL migration SQL, Docker Compose, Node/JUnit source-contract tests.

---

## File map

**Create:**

- `erp-ui/src/views/mobile/drive/index.vue` — 移动端云盘页面。
- `erp-ui/src/views/mobile/drive/mobileDriveState.js` — 移动空间、目录、上传和预览纯函数。
- `erp-ui/test/mobileCloudDrive.test.js` — 路由、权限、入口、功能边界和页面契约。
- `erp-modules/erp-system/src/main/java/com/erp/system/config/DriveFeatureProperties.java` — system 侧同名 `drive.enabled` 配置。
- `erp-modules/erp-system/src/test/java/com/erp/system/sql/CloudDriveMenuSqlSourceTest.java` — 菜单、权限和 admin 授权 SQL 契约。
- `docs/CLOUD_DRIVE_OPERATIONS.md` — 容量、备份、恢复、MinIO 切换、灰度和回滚手册。

**Modify:**

- `erp-ui/src/views/mobile/mobileRouteDefinitions.js` — `/mobile/drive` 和“我的”入口。
- `erp-ui/src/views/mobile/mobileNavigation.js` — `drive:access`、组织上下文可选和工作台快捷入口。
- `erp-ui/src/views/mobile/components/MobileWorkbenchShell.vue` — 云盘图标路径与更多入口显示。
- `erp-ui/src/views/mobile/feature/index.vue` — “我的”动作图标路径。
- `erp-ui/src/store/modules/user.js`, `src/store/getters.js`, and `src/permission.js` — 保存并传递显式 `driveEnabled`，覆盖管理员通配权限。
- `erp-ui/test/mobileAccessBoundary.test.js`.
- `erp-ui/test/mobileAppShell.test.js`.
- `erp-ui/test/mobileFirstScreenHierarchy.test.js`.
- `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysMenuServiceImpl.java` — 关闭时过滤云盘路由和权限。
- `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserController.java` — `getInfo` 返回 `driveEnabled`。
- `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysMenuServiceImplTest.java`.
- `erp-modules/erp-system/src/test/java/com/erp/system/controller/SysUserControllerAuthRoleScopeTest.java`.
- `erp-modules/erp-system/src/main/resources/application-dev.yml` and `bootstrap.yml` — system 侧默认关闭开关。
- `erp-modules/erp-file/src/main/resources/application-dev.yml` and `application-local.yml` — 与 system 相同的环境开关名。
- `sql/erp_cloud_drive_20260711.sql` and mirrored Docker copy — 一级菜单、三个管理权限和 admin role-key 授权。
- `erp-modules/erp-file/src/test/java/com/erp/file/drive/sql/CloudDriveSqlSourceTest.java` — 从“本阶段无菜单”升级为最终菜单契约。
- `docker/docker-compose.yml` — file 服务数据源、MySQL 依赖和共享开关。
- `docker/docker-compose.ecs-host.yml` — file 服务 MySQL 依赖和共享开关。
- `docker/nginx/conf/nginx.conf` and `nginx.host.conf` — API multipart 入口上限和长上传超时。
- `erp-ui/test/dockerScripts.test.js` — 容器配置源契约。
- `docs/superpowers/verification/2026-07-11-cloud-drive.md` — 最终验证证据。

### Task 1: Register the mobile route and both discoverable entries

**Files:** mobile route/navigation/workbench/feature files and `mobileCloudDrive.test.js`.

- [ ] **Step 1: Write failing route and permission tests**

Assert the stable route:

```js
assert.strictEqual(MOBILE_ROUTES.drive, "/mobile/drive")
assert.deepStrictEqual(getMobileRouteRequiredPermissions("/mobile/drive"), ["drive:access"])
assert.strictEqual(isMobileContextOptionalPath("/mobile/drive"), true)
assert.strictEqual(isMobileRouteAllowedForPermissions("/mobile/drive", [], { driveEnabled: true }), false)
assert.strictEqual(isMobileRouteAllowedForPermissions("/mobile/drive", ["drive:access"], { driveEnabled: true }), true)
assert.strictEqual(isMobileRouteAllowedForPermissions("/mobile/drive", ["*:*:*"], { driveEnabled: false }), false)
```

Source assertions must find one “云盘” action in both store and warehouse quick-action overflow, one “云盘” action in `/mobile/mine`, no sixth bottom-nav item, and `MobileWorkbenchShell`/feature icon maps containing a `cloud` path.

- [ ] **Step 2: Run RED**

```bash
cd erp-ui
npm test -- mobileCloudDrive.test.js mobileAccessBoundary.test.js
```

Expected: drive route/permission/entry are absent.

- [ ] **Step 3: Implement the exact route metadata and navigation rules**

Add:

```js
{
  path: '/mobile/drive',
  component: () => import('@/views/mobile/drive/index'),
  hidden: true,
  meta: {
    title: '手机云盘',
    mobileFeature: {
      featureKey: 'drive',
      permissions: ['drive:access'],
      title: '云盘',
      heading: '企业云盘',
      subtitle: '个人文件、公司公共盘和部门资料',
      icon: 'cloud',
      tone: 'blue'
    }
  }
}
```

Add `drive: ["drive:access"]` to `PERMISSIONS`, add `MOBILE_ROUTES.drive` to `mobileContextOptionalPaths`, add `{ label:"云盘", icon:"cloud", tone:"blue", path:MOBILE_ROUTES.drive, permissions:PERMISSIONS.drive, featureFlag:"drive", placement:"more" }` to both context profiles, and add the same permission-guarded/feature-flagged path to mine actions. Extend navigation filtering/route-decision functions with an optional feature-state object; for the drive feature, `driveEnabled !== true` denies the route and hides the item before wildcard permission handling. Other mobile features retain existing behavior.

Extend `isMobileBottomNavItemActive` self-service paths with drive so “我的” remains active while the user is in the cloud drive. Add this cloud path to both icon maps:

```text
M6 5h5l2 2h5a3 3 0 0 1 3 3v7a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3Zm0 2a1 1 0 0 0-1 1v9a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-7a1 1 0 0 0-1-1h-5.8l-2-2H6Z
```

- [ ] **Step 4: Run GREEN**

Expected: route and access-boundary tests pass; bottom navigation remains exactly five items.

- [ ] **Step 5: Commit**

```bash
git add erp-ui/src/views/mobile/mobileRouteDefinitions.js erp-ui/src/views/mobile/mobileNavigation.js erp-ui/src/views/mobile/components/MobileWorkbenchShell.vue erp-ui/src/views/mobile/feature/index.vue erp-ui/test/mobileCloudDrive.test.js erp-ui/test/mobileAccessBoundary.test.js
git commit -m "feat(mobile): register cloud drive entrypoints"
```

### Task 2: Build mobile spaces, browsing, search, and download

**Files:** mobile page/state and mobile tests.

- [ ] **Step 1: Add failing page/state tests**

Test pure state behavior: first visible space selection, folder stack push/pop, clearing search preserves the current logical directory, formatted bytes, previewable extensions, mobile Blob save/share fallback and error messages. Source-contract tests require: `listDriveSpaces`, `listDriveNodes`, `getDriveNode`, `getDriveContent`; space selector; breadcrumb/back control; search input; file/folder list; 44px controls; safe-area bottom padding; no recent, rename, move, trash, purge or quota calls.

- [ ] **Step 2: Run RED**

```bash
npm test -- mobileCloudDrive.test.js
```

Expected: mobile page/state files are absent.

- [ ] **Step 3: Implement the read-only mobile shell**

Mobile state:

```js
data() {
  return {
    spaces: [],
    activeSpaceId: null,
    parentId: 0,
    folderStack: [],
    nodes: [],
    pageNum: 1,
    total: 0,
    keyword: "",
    loading: false,
    errorMessage: "",
    previewNode: null,
    previewObjectUrl: "",
    previewText: ""
  }
}
```

Load spaces from `response.data || []`. Use a native `<select aria-label="选择云盘空间">` or an accessible bottom sheet; changing space clears the stack. Folder rows are buttons and push `{nodeId,nodeName}`; back pops one level, and at root returns to the previous app route. When `parentId != 0`, call `getDriveNode(parentId)`, unwrap `response.data`, and render its ordered breadcrumb names in a horizontally scrollable, labelled breadcrumb region. Search resets `pageNum=1` and calls `listDriveNodes({spaceId,parentId,keyword,pageNum,pageSize:50})` after 300 ms. Page 1 replaces nodes; load-more increments `pageNum` and appends `response.rows || []`; store `Number(response.total || 0)` and expose load-more only when loaded count is below it. The server treats a nonblank keyword as whole-space search; retain `parentId` only so clearing search returns to the same folder, and render each result's `logicalPath` below its name.

Download calls `getDriveContent(nodeId,'download')`, then `saveMobileBlob(blob,nodeName)`. That helper constructs a `File` with the logical name and Blob MIME; when `navigator.canShare({files:[file]})` is true, open the native share/save sheet with `navigator.share`, treating `AbortError` as neutral. If sharing is unavailable or fails for another reason (for example a platform size cap), fall back once to `file-saver`'s `saveAs`. Load the CommonJS helper with `const { parseDriveBlobError, driveErrorMessage } = require("@/views/drive/driveState")`; do not duplicate Blob JSON parsing in the mobile module. The page does not import shop context and must still load when no organization is selected.

- [ ] **Step 4: Run GREEN and viewport QA**

Verify at 390x844 and 360x800. Expected: no horizontal scroll; names wrap or truncate; header/back/search and every row action meet 44px touch target.

- [ ] **Step 5: Commit**

```bash
git add erp-ui/src/views/mobile/drive/index.vue erp-ui/src/views/mobile/drive/mobileDriveState.js erp-ui/test/mobileCloudDrive.test.js
git commit -m "feat(mobile): add cloud drive browsing"
```

### Task 3: Add mobile photo/file upload and safe preview

**Files:** mobile page/state/test.

- [ ] **Step 1: Add failing upload and preview tests**

Require two hidden inputs: general allowed files and `accept="image/*" capture="environment"`; both call the same `uploadDriveFile` API. Prove one active upload at a time, progress status, retained failed file and retry to its original destination even after navigation, refresh after success, safe filename synthesis for a captured JPEG/PNG/HEIC whose browser supplies no extension, image/PDF/text preview, Office/HEIC unsupported fallback, object URL cleanup, and a visible download action in every preview state.

- [ ] **Step 2: Run RED**

Run `mobileCloudDrive.test.js`; expect missing upload/preview handlers.

- [ ] **Step 3: Implement the confirmed mobile boundary**

Do not show upload controls when active space `canWrite=false`. Upload one selected file at a time:

```js
async uploadSelectedFile(file) {
  const targetSpaceId = this.activeSpaceId
  const targetParentId = this.parentId
  this.uploadState = { file, targetSpaceId, targetParentId, progress: 0, status: "uploading", error: "" }
  try {
    await uploadDriveFile(file, targetSpaceId, targetParentId, event => {
      const total = event.total || file.size || 1
      this.uploadState.progress = Math.min(100, Math.round(event.loaded * 100 / total))
    })
    this.uploadState.status = "done"
    await this.loadSpaces()
    if (this.activeSpaceId === targetSpaceId && this.parentId === targetParentId) {
      await this.loadNodes()
    }
  } catch (error) {
    this.uploadState.status = "failed"
    const parsed = await parseDriveBlobError(error)
    this.uploadState.error = driveErrorMessage(parsed.code, parsed.message)
  }
}
```

Retry uses `uploadState.targetSpaceId/targetParentId`, not the current screen. Space/folder changes remain allowed during a long upload, but they never retarget or duplicate it.

Before camera upload only, `prepareCapturedFile` may synthesize `照片-<timestamp>.jpg|png|heic|heif` when the browser gives a recognized image MIME but no extension. It must preserve the original bytes and MIME, never relabel an unknown type, and leave normal file-picker names unchanged. This makes mobile capture compatible with the backend's required-extension policy without pretending to transcode HEIC.

Preview uses one object URL at a time and Vue interpolation for text; never use `v-html`. Add Android/iOS fallback text: “当前设备无法内嵌预览，请下载后查看”. Revoke URL on new preview, close, route leave and `beforeDestroy`.

- [ ] **Step 4: Run GREEN and native-shell regression**

```bash
npm test -- mobileCloudDrive.test.js mobileAppShell.test.js mobileFirstScreenHierarchy.test.js
```

Expected: all pass; no native project file is modified by this task.

- [ ] **Step 5: Commit**

```bash
git add erp-ui/src/views/mobile/drive/index.vue erp-ui/src/views/mobile/drive/mobileDriveState.js erp-ui/test/mobileCloudDrive.test.js
git commit -m "feat(mobile): add cloud drive upload and preview"
```

### Task 4: Gate menu and permissions with `drive.enabled`, then finish SQL

**Files:** system properties/menu service/user-info tests, frontend feature-state/store/guard tests, application configs, both SQL copies, SQL tests.

- [ ] **Step 1: Write failing system-menu and SQL tests**

Extend `SysMenuServiceImplTest` with a root menu `{menuId:9600,path:"drive",component:"drive/index"}`. When disabled, `selectMenuTreeByUserId` omits it and `selectMenuPermsByUserId` removes all `drive:*`; when enabled, both remain. Existing admin-role full-menu behavior must still pass.

Extend `SysUserControllerAuthRoleScopeTest` to assert `getInfo` returns boolean `driveEnabled` for ordinary and wildcard-admin users. Extend `mobileCloudDrive.test.js`/`mobileAccessBoundary.test.js` to prove disabled state hides both workbench/mine entries and rejects direct `/mobile/drive` even with `*:*:*`; enabled state still requires either `drive:access` or wildcard.

`CloudDriveMenuSqlSourceTest` asserts:

```java
assertThat(sql).contains("(9600, '云盘'");
assertThat(sql).contains("'drive', 'drive/index'");
assertThat(sql).contains("'drive:access'");
assertThat(sql).contains("'drive:company:manage'");
assertThat(sql).contains("'drive:department:manage'");
assertThat(sql).contains("'drive:quota:manage'");
assertThat(sql).contains("role_key = 'admin'");
assertThat(sql).doesNotContain("VALUES\n    (1, 9600)");
```

Update phase-1 SQL test to expect final menu content while retaining three-table and mirror checks.

- [ ] **Step 2: Run RED**

```bash
mvn -pl erp-modules/erp-system,erp-modules/erp-file -am -Dtest=SysMenuServiceImplTest,SysUserControllerAuthRoleScopeTest,CloudDriveMenuSqlSourceTest,CloudDriveSqlSourceTest -Dsurefire.failIfNoSpecifiedTests=false test
cd erp-ui
npm test -- mobileCloudDrive.test.js mobileAccessBoundary.test.js
```

Expected: menu properties/gating and final SQL are absent.

- [ ] **Step 3: Implement one shared flag contract**

`DriveFeatureProperties`:

```java
@Component
@ConfigurationProperties(prefix = "drive")
public class DriveFeatureProperties {
    private boolean enabled = false;
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
```

In `SysMenuServiceImpl`, after loading menu permissions:

```java
if (!driveFeatureProperties.isEnabled()) {
    permsSet.removeIf(permission -> permission.startsWith("drive:"));
}
```

Before `getChildPerms`, remove root menus whose `path` is `drive` or `component` is `drive/index` when disabled. Do not hide cloud-drive entries from the administrator's menu-maintenance screen; only runtime routes and effective login permissions are gated.

Inject the same properties into `SysUserController` and add `ajax.put("driveEnabled", driveFeatureProperties.isEnabled())` in `getInfo`. In Vuex, initialize `driveEnabled` to false, set it only from `res.driveEnabled === true`, expose a getter, and reset it on both logout paths. Pass `{driveEnabled: store.getters.driveEnabled}` from `permission.js`, `MobileWorkbenchShell`, and the mine feature action filter into the navigation decision helpers. This explicit bit is mandatory because an administrator's `*:*:*` cannot be neutralized by removing `drive:*` strings.

Finish SQL with stable IDs 9600–9603. Before insert, use a temporary procedure to `SIGNAL SQLSTATE '45000'` if any ID is occupied by a different path/permission or if the active root menu with stable path `oa` is not exactly one row. On first application only, shift later root-menu order numbers by one and insert cloud drive at that OA row's `order_num + 1`; repeated application must not shift again. Insert/update the root menu as a root `C` menu with component `drive/index`, route name `CloudDrive`, icon `cloud-drive`, and `perms='drive:access'`. Insert three `F` children for management permissions. Grant all four menus with `INSERT IGNORE ... SELECT` where `sys_role.role_key='admin'`; never assume `role_id=1`.

Set `drive.enabled: ${DRIVE_ENABLED:false}` in system and file dev/local configuration. Missing property stays false.

- [ ] **Step 4: Run GREEN and apply migration twice**

```bash
mvn -pl erp-modules/erp-system,erp-modules/erp-file -am -Dtest=SysMenuServiceImplTest,SysUserControllerAuthRoleScopeTest,CloudDriveMenuSqlSourceTest,CloudDriveSqlSourceTest -Dsurefire.failIfNoSpecifiedTests=false test
cd erp-ui
npm test -- mobileCloudDrive.test.js mobileAccessBoundary.test.js
cd ..
MYSQL_PWD="$DRIVE_TEST_DB_PASSWORD" mysql -h "$DRIVE_TEST_DB_HOST" -P "$DRIVE_TEST_DB_PORT" -u "$DRIVE_TEST_DB_USER" "$DRIVE_TEST_DB_NAME" < sql/erp_cloud_drive_20260711.sql
MYSQL_PWD="$DRIVE_TEST_DB_PASSWORD" mysql -h "$DRIVE_TEST_DB_HOST" -P "$DRIVE_TEST_DB_PORT" -u "$DRIVE_TEST_DB_USER" "$DRIVE_TEST_DB_NAME" < sql/erp_cloud_drive_20260711.sql
```

Expected: tests pass; two SQL applications do not duplicate menus/roles or shift order twice; disabled runtime returns no cloud route/permission; enabled runtime returns exactly one.

- [ ] **Step 5: Commit**

```bash
git add erp-modules/erp-system/src/main/java/com/erp/system/config/DriveFeatureProperties.java erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysMenuServiceImpl.java erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserController.java erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysMenuServiceImplTest.java erp-modules/erp-system/src/test/java/com/erp/system/controller/SysUserControllerAuthRoleScopeTest.java erp-modules/erp-system/src/test/java/com/erp/system/sql/CloudDriveMenuSqlSourceTest.java erp-modules/erp-system/src/main/resources/application-dev.yml erp-modules/erp-system/src/main/resources/bootstrap.yml erp-modules/erp-file/src/main/resources/application-dev.yml erp-modules/erp-file/src/main/resources/application-local.yml erp-ui/src/store/modules/user.js erp-ui/src/store/getters.js erp-ui/src/permission.js erp-ui/src/views/mobile/mobileNavigation.js erp-ui/src/views/mobile/components/MobileWorkbenchShell.vue erp-ui/src/views/mobile/feature/index.vue erp-ui/test/mobileCloudDrive.test.js erp-ui/test/mobileAccessBoundary.test.js sql/erp_cloud_drive_20260711.sql docker/mysql/db/erp_cloud_drive_20260711.sql erp-modules/erp-file/src/test/java/com/erp/file/drive/sql/CloudDriveSqlSourceTest.java
git commit -m "feat(system): register gated cloud drive menu"
```

### Task 5: Wire container data source, storage volume, and operations runbook

**Files:** both Compose files, Docker source test, operations document.

- [ ] **Step 1: Add failing deployment-contract tests**

Extend `dockerScripts.test.js` to assert both Compose definitions keep `./erp/uploadPath:/home/erp/uploadPath`, file service depends on MySQL, main Compose passes the three `SPRING_DATASOURCE_DYNAMIC_DATASOURCE_MASTER_*` variables, and both shared Java environment anchors expose `DRIVE_ENABLED` default false. Assert each file service receives storage path/type, dedicated private MinIO bucket, file/request limits, all three quota defaults, retention, cleanup cron and `Asia/Shanghai` default zone; neither file service publishes MySQL or MinIO credentials to frontend files. Read both Nginx configs and require the `/prod-api/` location to contain `client_max_body_size 110m`, `proxy_request_buffering off`, `proxy_read_timeout 300s`, and `proxy_send_timeout 300s`.

- [ ] **Step 2: Run RED**

```bash
cd erp-ui
npm test -- dockerScripts.test.js
```

Expected: file service currently lacks datasource environment and MySQL dependency.

- [ ] **Step 3: Implement exact Compose wiring**

In `docker/docker-compose.yml`, add to `erp-modules-file.environment`:

```yaml
SPRING_DATASOURCE_DYNAMIC_DATASOURCE_MASTER_URL: ${APP_DATASOURCE_URL:?set APP_DATASOURCE_URL in .env}
SPRING_DATASOURCE_DYNAMIC_DATASOURCE_MASTER_USERNAME: ${APP_DATASOURCE_USERNAME:?set APP_DATASOURCE_USERNAME in .env}
SPRING_DATASOURCE_DYNAMIC_DATASOURCE_MASTER_PASSWORD: ${APP_DATASOURCE_PASSWORD:?set APP_DATASOURCE_PASSWORD in .env}
```

Add MySQL to its dependencies and keep the upload volume. Add `DRIVE_ENABLED: ${DRIVE_ENABLED:-false}` to each Compose shared Java environment anchor so system and file receive the identical switch. Add these file-only values in both Compose definitions:

```yaml
DRIVE_STORAGE_TYPE: ${DRIVE_STORAGE_TYPE:-local}
DRIVE_LOCAL_PATH: ${DRIVE_LOCAL_PATH:-/home/erp/uploadPath/private/drive}
DRIVE_MINIO_BUCKET: ${DRIVE_MINIO_BUCKET:-erp-drive-private}
DRIVE_MAX_FILE_SIZE: ${DRIVE_MAX_FILE_SIZE:-104857600}
DRIVE_MAX_REQUEST_SIZE: ${DRIVE_MAX_REQUEST_SIZE:-115343360}
SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE: ${DRIVE_MAX_FILE_SIZE:-104857600}
SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE: ${DRIVE_MAX_REQUEST_SIZE:-115343360}
DRIVE_PERSONAL_QUOTA: ${DRIVE_PERSONAL_QUOTA:-2147483648}
DRIVE_DEPARTMENT_QUOTA: ${DRIVE_DEPARTMENT_QUOTA:-21474836480}
DRIVE_COMPANY_QUOTA: ${DRIVE_COMPANY_QUOTA:-107374182400}
DRIVE_TRASH_RETENTION_DAYS: ${DRIVE_TRASH_RETENTION_DAYS:-30}
DRIVE_CLEANUP_CRON: ${DRIVE_CLEANUP_CRON:-0 30 2 * * *}
DRIVE_CLEANUP_ZONE: ${DRIVE_CLEANUP_ZONE:-Asia/Shanghai}
```

The explicit Spring multipart variables mirror the drive values and take precedence over any stale Nacos transport limit. Quote the cron scalar in the actual YAML. Pass existing `MINIO_ENABLED` (default false), `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY` and legacy bucket environment names only to the file service, with empty/default-safe values; local mode must not require MinIO secrets. Keep `DRIVE_MINIO_BUCKET` separate from that legacy bucket. The runbook must set `DRIVE_STORAGE_TYPE=minio` and `MINIO_ENABLED=true` together, pre-create the private drive bucket without anonymous policy, and MinIO mode must fail startup if credentials are absent. In ECS-host Compose, file service uses the existing `MYSQL_USERNAME/MYSQL_PASSWORD` anchor and must depend on healthy MySQL.

In both Nginx files, add the four tested directives only inside the existing `/prod-api/` proxy location. Disabling request buffering streams upload progress toward the gateway instead of first filling an Nginx temp file. The 110 MiB transport/request allowance covers a 100 MiB file plus multipart framing; `DriveFilePolicy` still enforces exactly 100 MiB and legacy `FileUploadUtils` still enforces its existing 50 MiB limit. Do not expose the private storage directory as an Nginx alias.

Write `docs/CLOUD_DRIVE_OPERATIONS.md` with exact, non-secret procedures:

- preflight free space and writable private directory, plus container/JVM multipart temp space for concurrent 110 MiB requests;
- topology rule: local mode supports one file-service instance unless all instances mount the same POSIX-consistent private volume; use MinIO before horizontal scaling;
- apply migration twice and verify counts/menu IDs;
- grant `drive:access` to selected employee roles and management permissions only to named roles;
- backup quiesce: disable new cloud-drive traffic, wait until no `PURGING` batch remains, stop the file service, then take the DB backup plus synchronized archive of `uploadPath/private/drive` at the same recovery point before restarting;
- restore database first, restore objects second, run object/node/used-bytes consistency audit, then enable;
- orphan audit comparing private object inventory to `drive_node.storage_key`, with dry-run output, fingerprint correlation to compensation alerts, quarantine, and explicit operator-confirmed deletion—never delete unmatched objects automatically;
- MinIO procedure that creates `erp-drive-private` (or configured dedicated name) with no anonymous policy, copies and verifies object count/size/hash, then switches both required flags and runs the startup probe;
- for `dev` profiles backed by Nacos, back up and update Data ID `erp-gateway-dev.yml` with `erp-file-drive-api` before `erp-file-static`, reusing that deployment's existing `erp-file-api` URI/service-discovery form rather than copying the local-profile `127.0.0.1` URI, publish it, then verify `/file/drive/spaces`; embedded `bootstrap.yml` covers the `local` profile;
- disable-first rollback preserving tables and objects;
- alerts for disk free space, upload/storage failures, missing object and purge failures.

- [ ] **Step 4: Run GREEN and validate Compose**

```bash
npm test -- dockerScripts.test.js
cd ../docker
docker compose config >/dev/null
docker compose -f docker-compose.ecs-host.yml config >/dev/null
```

Expected: Node test passes and both Compose files render with required environment variables supplied.

- [ ] **Step 5: Commit**

```bash
git add docker/docker-compose.yml docker/docker-compose.ecs-host.yml docker/nginx/conf/nginx.conf docker/nginx/conf/nginx.host.conf erp-ui/test/dockerScripts.test.js docs/CLOUD_DRIVE_OPERATIONS.md
git commit -m "chore(deploy): prepare cloud drive storage and backup"
```

### Task 6: Full regression, native verification, and controlled enablement

**Files:** verification document only unless a test exposes a scoped defect.

- [ ] **Step 1: Run all backend gates**

```bash
mvn -pl erp-modules/erp-file -am test
mvn -pl erp-modules/erp-system -am test
mvn -pl erp-gateway -am test
```

Expected: all reactors `BUILD SUCCESS`, zero failures/errors.

- [ ] **Step 2: Run all frontend and native gates**

```bash
cd erp-ui
npm test
npm run build:prod
npm run app:sync
npm run app:verify
```

Run native sync only in the isolated implementation worktree and inspect `git status` before recording evidence; do not commit generated web assets or incidental native project rewrites. Expected: Node 0 failed, production build and Capacitor sync succeed, native config/assets verify, and the verification script reports no unsafe production origin or unintended native configuration drift.

- [ ] **Step 3: Test the disabled state first**

With `DRIVE_ENABLED=false`, verify desktop menu absent, both mobile entries absent for ordinary and `*:*:*` admin users, `getInfo.driveEnabled=false`, effective non-admin permission sets contain no `drive:*`, direct JSON/content API requests return `DRIVE_DISABLED`, and no existing cloud-drive rows/objects change.

- [ ] **Step 4: Enable in a test environment and execute the acceptance matrix**

Set `DRIVE_ENABLED=true`, restart system/file, grant `drive:access` to one test employee role, management permissions to separate test roles, and execute all 13 acceptance criteria from the design on desktop and 390x844 mobile. Use synthetic files only.

- [ ] **Step 5: Run backup/restore rehearsal**

Upload synthetic files in all three spaces, take the paired database/object backup, restore into a disposable environment, run consistency checks, and prove preview/download and `used_bytes` remain correct. Record duration and every command without credentials.

- [ ] **Step 6: Record final evidence and commit**

Append commit hashes, test counts, build result, disabled-state proof, role matrix, migration double-run, backup/restore result, known non-goals and rollback command to `docs/superpowers/verification/2026-07-11-cloud-drive.md`.

```bash
git add docs/superpowers/verification/2026-07-11-cloud-drive.md
git commit -m "test: verify enterprise cloud drive"
```

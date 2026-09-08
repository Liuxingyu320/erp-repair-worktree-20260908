# 企业云盘总路线图 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 ERP 内交付个人盘、公司公共盘、部门盘、桌面完整操作、移动常用操作、私有存储、服务端鉴权、回收站和可回滚上线能力。

**Architecture:** 直接扩展 `erp-file`，为云盘增加独立数据模型、权限服务和私有 `DriveStorageProvider`，保留现有公共附件接口不变。后端契约稳定后再交付桌面端，最后接入移动端、动态菜单、Compose 数据源、备份和灰度开关。

**Tech Stack:** Java 17, Spring Boot 4, Spring Cloud Gateway, MyBatis XML, MySQL 5.7/8.0 compatible SQL, local filesystem, optional MinIO, Vue 2, Element UI, Capacitor 8, JUnit 5, Mockito, AssertJ, Node source-contract tests.

---

## 1. 计划集

| 顺序 | 计划 | 可独立验收的结果 |
|---|---|---|
| 1 | [阶段 1：安全存储与空间基础](./2026-07-11-cloud-drive-phase-1-foundation.md) | `erp-file` 具备云盘数据源、三张表、私有本地存储、登录用户解析、空间权限、额度和 `/file/drive/spaces` |
| 2 | [阶段 2：文件生命周期与鉴权接口](./2026-07-11-cloud-drive-phase-2-lifecycle.md) | 上传、列表、文件夹、搜索、预览、下载、重命名、同空间移动、最近使用、回收站和清理全部可通过 API 验收 |
| 3 | [阶段 3：桌面云盘](./2026-07-11-cloud-drive-phase-3-desktop.md) | 桌面端完整云盘页面、上传队列、预览、移动、搜索、最近使用、回收站和容量体验 |
| 4 | [阶段 4：移动端、菜单与上线](./2026-07-11-cloud-drive-phase-4-mobile-rollout.md) | 移动端常用能力、一级菜单、功能开关、角色权限、Compose 数据源、备份恢复和全链路验证 |

设计依据：[企业云盘功能设计](../specs/2026-07-11-cloud-drive-design.md)。

## 2. 强制顺序与阶段门

```text
阶段 1：数据与空间
  └─ 阶段 2：文件生命周期 API
      └─ 阶段 3：桌面完整体验
          └─ 阶段 4：移动端与上线
```

- [ ] 每个任务必须先写失败测试并记录 RED 原因，再写最小实现，再运行聚焦测试和阶段回归。
- [ ] 阶段 1 未证明公共 `/file/upload` 回归不变前，不进入文件生命周期实现。
- [ ] 阶段 2 未通过越权下载、路径穿越、额度和补偿测试前，不连接桌面页面。
- [ ] 阶段 3 未完成桌面上传、预览、删除恢复真实流程前，不开放普通员工菜单。
- [ ] 阶段 4 上线前保持 `DRIVE_ENABLED=false`；数据库、后端和前端部署完成后才授权角色并开启。
- [ ] `sql/erp_cloud_drive_20260711.sql` 与 `docker/mysql/db/erp_cloud_drive_20260711.sql` 每次修改后必须用 `cmp` 验证字节一致。
- [ ] 任何阶段都不得迁移现有合同、头像或业务附件，不得改变 `/file/public/**` 静态映射。
- [ ] 任何内容接口只接收 `nodeId`，不得接受或返回 `storageKey`、服务器路径或对象存储凭据。

## 3. 分支、工作区与提交约定

- [ ] 执行前运行 `git status --short`，记录用户已有改动；不得清理或覆盖不属于云盘计划的文件。
- [ ] 在隔离 worktree 中执行，推荐分支 `codex/cloud-drive`；若按阶段拆分，使用 `codex/cloud-drive-phase-N`。
- [ ] 每个任务只提交该任务文件，聚焦测试通过后立即提交。
- [ ] 不提交 `target/`、`dist/`、上传文件、数据库数据目录、日志或浏览器截图缓存。
- [ ] `erp-modules/erp-file/src/main/resources/application-local.yml` 当前是未跟踪本地配置；实施时先保留用户内容，只追加云盘数据源和配置，不覆盖已有值。
- [ ] `docker/copy.sh` 当前已有用户修改，本计划不需要修改该文件。

建议提交序列：

```text
feat(file): enable cloud drive persistence
feat(file): add private drive storage
feat(file): enforce drive space permissions
feat(file): add drive upload and browse APIs
feat(file): secure drive content delivery
feat(file): add drive node lifecycle
feat(file): add drive recycle bin cleanup
feat(ui): add desktop cloud drive
feat(mobile): add cloud drive self service
feat(system): register gated cloud drive menu
chore(deploy): prepare cloud drive storage and backup
test: verify enterprise cloud drive
```

## 4. 跨阶段稳定契约

### 4.1 类型与状态

```java
public final class DriveConstants {
    public static final long ROOT_PARENT_ID = 0L;
    public static final String SPACE_PERSONAL = "PERSONAL";
    public static final String SPACE_COMPANY = "COMPANY";
    public static final String SPACE_DEPARTMENT = "DEPARTMENT";
    public static final String NODE_FILE = "FILE";
    public static final String NODE_FOLDER = "FOLDER";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_TRASHED = "TRASHED";
    public static final String STATUS_PURGING = "PURGING";
    public static final String STATUS_PURGE_FAILED = "PURGE_FAILED";
    private DriveConstants() { }
}
```

### 4.2 权限

```text
drive:access
drive:company:manage
drive:department:manage
drive:quota:manage
```

- 个人盘：所有者读写。
- 公司盘：拥有 `drive:access` 可读；拥有 `drive:company:manage` 可写。
- 部门盘：当前用户 `deptId` 相同可读；同部门且拥有 `drive:department:manage` 可写。
- 系统管理员继续遵循现有 `SecurityUtils.isAdmin()` 与安全绕过开关。

### 4.3 外部 API

```text
GET    /file/drive/spaces
GET    /file/drive/nodes
GET    /file/drive/nodes/{nodeId}
POST   /file/drive/folders
POST   /file/drive/files
PUT    /file/drive/nodes/{nodeId}/name
PUT    /file/drive/nodes/{nodeId}/move
DELETE /file/drive/nodes/{nodeId}
GET    /file/drive/recent
GET    /file/drive/trash
POST   /file/drive/trash/{nodeId}/restore
DELETE /file/drive/trash/{nodeId}
DELETE /file/drive/trash
GET    /file/drive/nodes/{nodeId}/content?mode=preview|download
PUT    /file/drive/spaces/{spaceId}/quota
```

`DELETE /nodes/{nodeId}` 必须携带 `version` 查询参数；重命名、移动、软删除和额度调整都使用乐观锁，陈旧版本返回 `DRIVE_CONCURRENT_MODIFICATION`。

### 4.4 业务错误码

```text
DRIVE_DISABLED
DRIVE_ACCESS_DENIED
DRIVE_SPACE_NOT_FOUND
DRIVE_NODE_NOT_FOUND
DRIVE_NAME_CONFLICT
DRIVE_INVALID_MOVE
DRIVE_QUOTA_EXCEEDED
DRIVE_FILE_TOO_LARGE
DRIVE_FILE_TYPE_REJECTED
DRIVE_PREVIEW_UNSUPPORTED
DRIVE_CONCURRENT_MODIFICATION
DRIVE_STORAGE_UNAVAILABLE
DRIVE_STORAGE_OBJECT_MISSING
```

错误 JSON 使用现有 `AjaxResult`，并增加 `businessCode`；内容下载成功时返回二进制，失败时返回上述 JSON，绝不包含路径、SQL 或堆栈。

### 4.5 配置默认值

```yaml
spring:
  servlet:
    multipart:
      max-file-size: ${DRIVE_MAX_FILE_SIZE:104857600}
      max-request-size: ${DRIVE_MAX_REQUEST_SIZE:115343360}
drive:
  enabled: ${DRIVE_ENABLED:false}
  storage-type: ${DRIVE_STORAGE_TYPE:local}
  local-path: ${DRIVE_LOCAL_PATH:./uploadPath/private/drive}
  minio-bucket: ${DRIVE_MINIO_BUCKET:erp-drive-private}
  max-file-size: ${DRIVE_MAX_FILE_SIZE:104857600}
  personal-quota: ${DRIVE_PERSONAL_QUOTA:2147483648}
  department-quota: ${DRIVE_DEPARTMENT_QUOTA:21474836480}
  company-quota: ${DRIVE_COMPANY_QUOTA:107374182400}
  trash-retention-days: ${DRIVE_TRASH_RETENTION_DAYS:30}
  cleanup-cron: ${DRIVE_CLEANUP_CRON:0 30 2 * * *}
  cleanup-zone: ${DRIVE_CLEANUP_ZONE:Asia/Shanghai}
```

不存在配置时功能必须关闭。云盘 MinIO 桶必须与旧公开附件桶不同且无匿名读取策略。`max-request-size` 只为单文件 multipart 边界预留约 10 MiB 协议开销，云盘业务层仍严格拒绝大于 100 MiB 的文件。回收站文件继续占额度，只有物理对象删除成功或确认已不存在后才释放额度。

## 5. 设计覆盖矩阵

| 设计要求 | 实施位置 |
|---|---|
| 三类空间、额度、当前部门权限 | 阶段 1 任务 4–5 |
| 私有本地存储与 MinIO 可切换 | 阶段 1 任务 3、阶段 2 任务 6 |
| 上传补偿、文件夹、列表、搜索 | 阶段 2 任务 1–2 |
| 安全预览和下载 | 阶段 2 任务 3 |
| 重命名、同空间移动、并发版本 | 阶段 2 任务 4 |
| 最近使用、回收站、30 天清理 | 阶段 2 任务 5 |
| 桌面完整体验 | 阶段 3 全部任务 |
| 移动浏览、搜索、拍照/选文件上传、预览、下载 | 阶段 4 任务 1–3 |
| 一级菜单、角色权限、开关 | 阶段 4 任务 4 |
| Compose 数据源、备份、回滚和真实 QA | 阶段 4 任务 5–6 |

## 6. 全链路验收场景

- [ ] 普通员工首次进入时只创建一个个人盘，并看到公司盘和本人部门盘。
- [ ] 无部门用户只看到个人盘和公司盘；换部门后不能再读取旧部门盘。
- [ ] 普通员工可完整管理个人盘，但修改公司盘或部门盘返回 `DRIVE_ACCESS_DENIED`。
- [ ] 被授权部门管理员只能管理本人部门盘；公司盘管理权限不能隐式获得部门盘管理权限。
- [ ] 上传同名文件自动编号；同名文件夹返回冲突；并发上传不能超过额度。
- [ ] 数据库提交失败后物理对象被补偿删除；存储失败时不生成节点和容量占用。
- [ ] 预览只支持图片、PDF 和安全文本；Office 和压缩包降级为下载。
- [ ] 越权用户即使知道 `nodeId` 也不能预览或下载；响应不泄露 `storageKey`。
- [ ] 文件夹不能移动到自身或后代；跨空间移动被拒绝。
- [ ] 删除批次在回收站只展示一个根节点；恢复、彻底删除作用于完整子树。
- [ ] 回收站 30 天到期后物理文件和元数据清理，容量只在成功清理后释放。
- [ ] 桌面端覆盖全部操作；移动端只显示已确认的常用操作。
- [ ] `DRIVE_ENABLED=false` 时菜单、移动入口和接口全部不可用，已有文件不删除。
- [ ] 现有公共上传、头像、合同和签约附件功能回归通过。

## 7. 总验证命令

```bash
mvn -pl erp-modules/erp-file -am test
mvn -pl erp-modules/erp-system -am test
mvn -pl erp-gateway -am test
cd erp-ui
npm test
npm run build:prod
npm run app:sync
npm run app:verify
```

预期：Maven reactor 全部 `BUILD SUCCESS` 且 0 failures/0 errors；Node 汇总 0 failed；生产构建成功；隔离 worktree 中的 Capacitor 同步和验证成功，且不提交生成资产或非预期原生配置改动。

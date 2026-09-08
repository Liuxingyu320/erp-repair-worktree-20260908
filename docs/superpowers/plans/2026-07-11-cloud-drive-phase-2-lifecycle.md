# 企业云盘阶段 2：文件生命周期与鉴权接口 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在阶段 1 的空间、权限和私有存储基础上，交付完整且可独立验收的云盘 REST API，包括上传补偿、文件夹、列表、搜索、安全预览/下载、重命名、同空间移动、最近使用、回收站、到期清理和 MinIO 切换。

**Architecture:** `DriveNodeService` 维护逻辑目录，`DriveUploadService` 先写私有对象再调用独立事务边界 `DriveUploadPersistence`，从而能捕获数据库提交失败并补偿删除。内容接口从 `nodeId` 反查空间并鉴权；回收站以 `trashRootId` 管理整批子树，事务抢占后交给有界后台执行器；清理仅在物理对象成功删除或确认不存在后释放额度。

**Tech Stack:** Java 17, Spring Boot 4, MyBatis XML, Spring transactions, Spring `Resource`, MinIO Java client, MySQL 5.7/8.0 compatible SQL, JUnit 5, Mockito, AssertJ.

---

## File map

**Create:**

- `erp-modules/erp-file/src/main/java/com/erp/file/drive/domain/dto/DriveFolderCreateRequest.java`.
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/domain/dto/DriveRenameRequest.java`.
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/domain/dto/DriveMoveRequest.java`.
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/domain/vo/DriveNodeVo.java`.
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/domain/vo/DriveBreadcrumbVo.java`.
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/domain/vo/DriveContent.java`.
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/domain/DriveAuditContext.java` — 可安全跨到清理线程的请求/操作者摘要。
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/domain/DrivePurgeClaim.java` — 已提交事务的批次、版本和审计上下文。
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveNamePolicy.java`.
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveFilePolicy.java`.
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveOperationLogService.java`.
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveOperationLogPersistence.java` — 独立新事务写审计行。
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveUploadPersistence.java`.
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveUploadService.java`.
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveNodeService.java`.
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveContentService.java`.
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveTrashService.java`.
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveTrashPersistence.java` — 抢占、失败和最终删除/额度释放事务。
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveTrashPurgeWorker.java` — 有界异步物理清理。
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/storage/MinioDriveStorageProvider.java`.
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/controller/DriveNodeController.java`.
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/controller/DriveContentController.java`.
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/controller/DriveExceptionHandler.java`.
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/task/DriveTrashCleanupTask.java`.
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/config/DriveStartupValidator.java` — 开启时验证私有存储与公司空间。
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/config/DriveAsyncConfig.java` — 专用清理线程池。
- `erp-modules/erp-file/src/test/java/com/erp/file/drive/service/DrivePolicyTest.java`.
- `erp-modules/erp-file/src/test/java/com/erp/file/drive/service/DriveUploadServiceTest.java`.
- `erp-modules/erp-file/src/test/java/com/erp/file/drive/service/DriveNodeServiceTest.java`.
- `erp-modules/erp-file/src/test/java/com/erp/file/drive/service/DriveOperationLogServiceTest.java`.
- `erp-modules/erp-file/src/test/java/com/erp/file/drive/service/DriveContentServiceTest.java`.
- `erp-modules/erp-file/src/test/java/com/erp/file/drive/controller/DriveContentControllerTest.java`.
- `erp-modules/erp-file/src/test/java/com/erp/file/drive/service/DriveTrashServiceTest.java`.
- `erp-modules/erp-file/src/test/java/com/erp/file/drive/service/DriveTrashPersistenceTest.java`.
- `erp-modules/erp-file/src/test/java/com/erp/file/drive/service/DriveTrashPurgeWorkerTest.java`.
- `erp-modules/erp-file/src/test/java/com/erp/file/drive/storage/MinioDriveStorageProviderTest.java`.
- `erp-modules/erp-file/src/test/java/com/erp/file/drive/controller/DriveControllerContractTest.java`.
- `erp-modules/erp-file/src/test/java/com/erp/file/drive/config/DriveStartupValidatorTest.java`.
- `erp-gateway/src/test/java/com/erp/gateway/config/FileDriveRouteConfigTest.java`.

**Modify:**

- `erp-modules/erp-file/src/main/java/com/erp/file/ErpFileApplication.java` — 启用定时任务。
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/mapper/DriveSpaceMapper.java` and XML — 锁、容量和清理支持。
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/mapper/DriveNodeMapper.java` and XML — 列表、名称、路径、搜索、回收站和子树操作。
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/mapper/DriveOperationLogMapper.java` and XML — 最近使用查询。
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveQuotaService.java`.
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveSpaceService.java`.
- `erp-modules/erp-file/src/main/resources/application-dev.yml` and `application-local.yml` — MinIO 云盘选择和清理 cron。
- `erp-gateway/src/main/resources/bootstrap.yml` — 云盘 API 路由优先于静态路由。
- `erp-modules/erp-file/src/test/java/com/erp/file/drive/mapper/DriveMapperBindingTest.java`.
- `erp-modules/erp-file/src/test/java/com/erp/file/controller/SysFileControllerTest.java` — 锁定旧接口权限。

### Task 1: Lock node contracts, name policy, and file policy

**Files:** DTO/VO、两个策略类、mapper 接口/XML、mapper/policy tests。

- [ ] **Step 1: Extend the failing mapper test and add policy tests**

Add mapper assertions:

```java
assertMapped(configuration, DriveNodeMapper.class, "selectActiveChildren");
assertMapped(configuration, DriveNodeMapper.class, "selectActiveSearch");
assertMapped(configuration, DriveNodeMapper.class, "selectActiveById");
assertMapped(configuration, DriveNodeMapper.class, "selectByIdForUpdate");
assertMapped(configuration, DriveNodeMapper.class, "existsActiveName");
assertMapped(configuration, DriveNodeMapper.class, "updateName");
assertMapped(configuration, DriveNodeMapper.class, "updateParent");
assertMapped(configuration, DriveNodeMapper.class, "updateDescendantAncestors");
assertMapped(configuration, DriveNodeMapper.class, "selectPathNodes");
assertMapped(configuration, DriveOperationLogMapper.class, "selectRecentNodes");
assertMapped(configuration, DriveNodeMapper.class, "selectTrashRoots");
assertMapped(configuration, DriveNodeMapper.class, "selectTrashBatch");
assertMapped(configuration, DriveSpaceMapper.class, "selectByIdForUpdate");
```

`DrivePolicyTest` must prove:

```java
assertThat(namePolicy.normalize("  月度报表  ")).isEqualTo("月度报表");
assertThat(namePolicy.normalizedKey("ＡＢＣ.PDF")).isEqualTo("abc.pdf");
assertThat(namePolicy.searchPattern("  预算%_!  ")).isEqualTo("预算!%!_!!");
assertThatThrownBy(() -> namePolicy.normalize("../工资.xlsx"))
        .isInstanceOf(DriveException.class);
assertThat(filePolicy.isPreviewable("pdf", "application/pdf")).isTrue();
assertThat(filePolicy.isPreviewable("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")).isFalse();
assertThatThrownBy(() -> filePolicy.validate("脚本.html", "text/html", 10L));
assertThatThrownBy(() -> filePolicy.validate("伪装.pdf", "text/html", 10L));
assertThatThrownBy(() -> filePolicy.validate("伪装.png", "image/svg+xml", 10L));
assertThatThrownBy(() -> filePolicy.validate("程序.exe", "application/octet-stream", 10L));
assertThatThrownBy(() -> filePolicy.validate("大文件.pdf", "application/pdf", 104857601L));
```

- [ ] **Step 2: Run RED**

```bash
mvn -pl erp-modules/erp-file -am -Dtest=DriveMapperBindingTest,DrivePolicyTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: missing DTOs, VO, mapper methods and policy classes.

- [ ] **Step 3: Implement the exact request and response contracts**

Requests:

```java
public class DriveFolderCreateRequest {
    @NotNull @Positive private Long spaceId;
    @NotNull @Min(0) private Long parentId = DriveConstants.ROOT_PARENT_ID;
    @NotBlank @Size(max = 200) private String name;
}

public class DriveRenameRequest {
    @NotBlank @Size(max = 200) private String name;
    @NotNull @Min(0) private Integer version;
}

public class DriveMoveRequest {
    @NotNull @Min(0) private Long targetParentId;
    @NotNull @Min(0) private Integer version;
}
```

`DriveNodeVo` includes only `nodeId`, `spaceId`, `spaceName`, `parentId`, `nodeType`, `nodeName`, `extension`, `contentType`, `sizeBytes`, `status`, `version`, `createBy`, `createTime`, `updateBy`, `updateTime`, `logicalPath`, `ancestorIds`, `breadcrumbs`, `canWrite`, `canDelete`, `canPreview`; it must not define `storageKey` or `sha256`. `DriveBreadcrumbVo` contains only `nodeId` and `nodeName`.

`DriveNamePolicy` uses `Normalizer.normalize(value.trim(), Form.NFKC)`, rejects empty names, `.`, `..`, `/`, `\\`, control characters and length over 200, and returns a lowercase `Locale.ROOT` key. Search keywords use the same NFKC/lowercase normalization, are capped at 100 characters, and escape `!`, `%`, and `_` to `!!`, `!%`, and `!_`. `DriveFilePolicy` allows `doc,docx,xls,xlsx,ppt,pptx,pdf,txt,csv,jpg,jpeg,png,gif,webp,heic,heif,zip,rar,7z`; HEIC/HEIF are accepted for mobile-photo portability but download-only. Reject active/executable extensions. Preview allows only `jpg,jpeg,png,gif,webp,pdf,txt,csv`.

Add fixed operation constants for `CREATE_FOLDER`, `UPLOAD`, `OPEN`, `PREVIEW`, `DOWNLOAD`, `RENAME`, `MOVE`, `TRASH`, `RESTORE`, `PURGE`, and `CLEANUP`; callers never persist action names supplied by clients.

Extend mapper signatures using explicit `@Param` names. `selectActiveChildren` accepts only service-normalized `sortField=name|size|updated` and `sortDirection=asc|desc`; trim/lowercase inputs and fall back to `updated/desc` on anything else. Add a separate `selectActiveSearch(spaceId, keywordPattern, sortField, sortDirection)` statement. XML uses `<choose>` blocks for the corresponding fixed column/direction fragments; it must never interpolate raw request strings with `${...}`. Every order starts with `node_type='FOLDER' desc`; the default is `update_time desc, node_id desc`. When `keyword` is blank, `list` calls `selectActiveChildren` for the requested parent. When `keyword` is nonblank, it ignores `parentId` for SQL scope and calls `selectActiveSearch`; search uses `normalized_name like concat('%', #{keywordPattern}, '%') escape '!'` across the entire selected space with `status='ACTIVE'`.

`selectPathNodes(spaceId, nodeIds)` accepts a collection and uses a MyBatis `<foreach>` `IN` clause to load all referenced active folder IDs in one query. `DriveNodeService` collects the union of ancestor IDs for the page, maps them by ID, then follows each node's stored ancestor order to build logical paths and breadcrumbs without one SQL query per row.

`DriveFilePolicy` rejects active MIME types (`text/html`, `image/svg+xml`, JavaScript/shell/executable types) even when the extension appears allowed. It must require an extension, lowercase it, and validate both extension and declared content type. Use explicit MIME families: common image extensions require their matching `image/*` type; PDF requires `application/pdf`; text/CSV accept `text/plain`, `text/csv`, `application/csv`, or the common Excel CSV MIME; HEIC/HEIF accept their registered `image/heic|heif|heic-sequence|heif-sequence` types; Office and archives accept their registered MIME or `application/octet-stream` and remain download-only. Blank MIME falls back to `application/octet-stream` only for Office/archive types, never for previewable image/PDF/text. Tests cover every allowed family plus mismatched extension/MIME pairs.

- [ ] **Step 4: Run GREEN**

Expected: mapper statements bind and all exact policy cases pass.

- [ ] **Step 5: Commit**

```bash
git add erp-modules/erp-file/src/main/java/com/erp/file/drive/domain/dto erp-modules/erp-file/src/main/java/com/erp/file/drive/domain/vo/DriveNodeVo.java erp-modules/erp-file/src/main/java/com/erp/file/drive/domain/vo/DriveBreadcrumbVo.java erp-modules/erp-file/src/main/java/com/erp/file/drive/constant/DriveConstants.java erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveNamePolicy.java erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveFilePolicy.java erp-modules/erp-file/src/main/java/com/erp/file/drive/mapper erp-modules/erp-file/src/main/resources/mapper/drive erp-modules/erp-file/src/test/java/com/erp/file/drive/mapper/DriveMapperBindingTest.java erp-modules/erp-file/src/test/java/com/erp/file/drive/service/DrivePolicyTest.java
git commit -m "feat(file): define drive node contracts"
```

### Task 2: Implement folders, listing, and compensated uploads

**Files:** `DriveUploadPersistence`, `DriveUploadService`, `DriveNodeService`, `DriveOperationLogService`, node controller and tests.

- [ ] **Step 1: Write failing upload and node tests**

Cover: list requires readable space; parent must be an active folder in the same space; root parent `0` is valid; folder create requires write and locks/rechecks its non-root parent in the insert transaction; same-name folder conflicts; same-name files receive `(1)`; two concurrent uploads that initially choose the same display name retry the unique-key conflict and both survive with distinct names; if a parent is trashed after upload preflight but before metadata persistence, the transaction rejects it and compensation deletes the stored object; if the parent moves after preflight, the inserted child's ancestors come from the newly locked parent rather than stale preflight data; an already-insufficient quota fails before storage, while a reserve race after storage compensates the key; storage failure performs no DB call; persistence failure deletes the stored key; three failed compensation deletes emit one fingerprint-only alert and never log the raw key; successful upload records SHA-256 and one success log. Also prove a nonblank keyword searches the whole selected space even when `parentId` points at a nested folder, while a blank keyword lists only that folder's direct children. `DriveOperationLogServiceTest` covers safe context capture, truncation, mapper-failure swallowing and structured error output.

The compensation assertion must be explicit:

```java
when(persistence.persist(any(), eq(12L))).thenThrow(new DataIntegrityViolationException("commit"));
assertThatThrownBy(() -> service.upload(file, 4L, 0L, actor));
verify(storage).delete(storageKeyCaptor.getValue());
verify(logService).failure(eq("UPLOAD"), any(), eq(DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE));
```

- [ ] **Step 2: Run RED**

Run `DriveUploadServiceTest,DriveNodeServiceTest,DriveOperationLogServiceTest`; expect missing services.

- [ ] **Step 3: Implement the transaction boundary and APIs**

`DriveUploadPersistence` is a separate Spring bean so its transaction commits before control returns to `DriveUploadService`:

```java
@Service
public class DriveUploadPersistence {
    @Transactional
    public DriveNode persist(DriveNode node, long bytes) {
        if (node.getParentId() != DriveConstants.ROOT_PARENT_ID) {
            DriveNode parent = nodeMapper.selectByIdForUpdate(node.getParentId());
            requireActiveFolderInSpace(parent, node.getSpaceId());
            node.setAncestors(childAncestors(parent));
        } else {
            node.setAncestors("0");
        }
        quotaService.reserve(node.getSpaceId(), bytes);
        nodeMapper.insertNode(node);
        return node;
    }
}
```

`DriveUploadService.upload` sequence is fixed:

```java
public DriveNodeVo upload(MultipartFile file, Long spaceId, Long parentId, DriveActor actor) {
    DriveSpace space = spaceService.requireWritableSpace(spaceId, actor);
    DriveNode parent = nodeService.requireParent(spaceId, parentId);
    filePolicy.validate(file.getOriginalFilename(), file.getContentType(), file.getSize());
    quotaService.preflight(space, file.getSize());
    String originalName = namePolicy.normalizeDisplayName(file.getOriginalFilename());
    String storageKey = storageKey(originalName);
    MessageDigest sha256 = messageDigest("SHA-256");
    try (DigestInputStream input = new DigestInputStream(file.getInputStream(), sha256)) {
        storage.put(storageKey, input);
    } catch (RuntimeException | IOException ex) {
        compensateDelete(storageKey);
        throw translateUploadFailure(ex);
    }

    String hash = HexFormat.of().formatHex(sha256.digest());
    DriveNode node = null;
    try {
        for (int attempt = 0; attempt < 10 && node == null; attempt++) {
            String displayName = nodeService.availableFileName(
                    spaceId, parentId, originalName);
            try {
                node = persistence.persist(
                        fileNode(space, parent, displayName, storageKey, file, hash),
                        file.getSize());
            } catch (DuplicateKeyException ex) {
                if (!isConstraint(ex, "uk_drive_node_active_name")) throw ex;
            }
        }
        if (node == null) {
            throw new DriveException(DriveErrorCodes.DRIVE_NAME_CONFLICT, "同名文件较多，请重试");
        }
    } catch (RuntimeException ex) {
        compensateDelete(storageKey);
        throw translateUploadFailure(ex);
    }
    logService.success("UPLOAD", actor, node, null, summary(node));
    return nodeService.toVo(node, actor);
}
```

The real implementation may split helpers, but must preserve this order and catch the persistence proxy exception outside the transaction. `preflight` avoids writing bytes when the current snapshot is already insufficient, but only the conditional reserve inside the metadata transaction is authoritative under concurrency. The parent recheck inside `DriveUploadPersistence` is likewise authoritative; the earlier `requireParent` is only a fast preflight before writing bytes. Generate keys as `yyyy/MM/<UUID>.<extension>`; never use the original path. A preflight name lookup is not a concurrency guarantee: if `persist` hits the named active-name unique index, query again, recompute the next `(n)` display name and retry only the metadata transaction, reusing the already-written immutable storage object. `isConstraint` must inspect the database constraint/SQL exception chain and must not classify every `DuplicateKeyException` as a name collision. Bound the retry loop at 10 attempts; if all collide, compensate the object and return `DRIVE_NAME_CONFLICT`. Preserve an existing `DriveException` in `translateUploadFailure`; do not turn name/quota/type errors into storage errors. Do not retry quota, storage, or unrelated database failures.

`compensateDelete` makes three bounded delete attempts (immediate, then 50 ms and 200 ms backoff), treating already-missing as success and restoring the interrupt flag if interrupted. If all fail, emit a high-severity structured storage-compensation alert with request ID and a SHA-256 fingerprint of the key—not the key/path itself—then return the original safe business error. The operations runbook includes an object-vs-node orphan audit and manual quarantine/delete procedure; operation-log rows still never contain storage keys.

`DriveNodeService` required methods:

```java
List<DriveNodeVo> list(Long spaceId, Long parentId, String keyword,
        String sortField, String sortDirection, DriveActor actor);
DriveNodeVo detail(Long nodeId, DriveActor actor);
DriveNodeVo createFolder(DriveFolderCreateRequest request, DriveActor actor);
DriveNode requireParent(Long spaceId, Long parentId);
String availableFileName(Long spaceId, Long parentId, String originalName);
DriveNodeVo toVo(DriveNode node, DriveActor actor);
```

Implement folder creation in one transaction: lock/recheck any non-root parent (or lock the space row for virtual root), derive the new folder's ancestors from that locked current row (or `0` for root), then resolve the name and insert the folder. This uses the same active-folder/same-space predicate as upload persistence, so concurrent trash/move cannot leave an active child below an inactive or foreign parent or persist a stale logical path.

`detail` returns `ancestorIds` and ordered `breadcrumbs` ending with the current folder when the node is a folder. Search and recent rows include `spaceName` plus `logicalPath` so the clients can explain where a result lives.

Expose `GET /drive/nodes`, `GET /drive/nodes/{nodeId}`, `POST /drive/folders`, and multipart `POST /drive/files`. Apply `@RequiresPermissions("drive:access")` to every method, `@Validated` to the controller class for query/path constraints, and `@Valid @RequestBody` to JSON requests. Path node IDs and `spaceId` are positive; parent IDs are nonnegative; multipart file is required and nonempty. Every method calls `featureGuard.requireEnabled()` before resolving the actor; the disabled controller test verifies no mapper/storage interaction.

Response shapes are fixed: list uses `TableDataInfo`; detail/folder/upload use `AjaxResult.success(nodeVo)` so the node is under `data`. Never return persistence entities directly.

`GET /drive/nodes` returns the repository's standard `TableDataInfo`, with `rows` and `total`. Accept explicit `pageNum` (`>=1`, default 1) and `pageSize` (`1..100`, default 50); do not accept generic `orderByColumn/isAsc` as the drive sorting contract. After the feature/actor/read checks, call `PageHelper.startPage(pageNum,pageSize)`, execute exactly one children/search query, capture its `Page` metadata, enrich/map only those rows, then return a `Page<DriveNodeVo>` carrying the original page number, size and total. Do not return a plain mapped `ArrayList`, which would silently reduce `total` to the current page size in `getDataTable`. Clear PageHelper in `finally` so an exception cannot leak thread-local pagination into a later request. Controller tests reject a page size over 100 and use two mapped rows with an original total of 237 to assert the response still serializes `total=237`.

`DriveOperationLogService` accepts `X-Request-Id` only after trim and match `[A-Za-z0-9._-]{1,64}`, otherwise creates a UUID. It uses the first `X-Forwarded-For` value only when it parses as IPv4/IPv6, with remote-address fallback, and strips controls before truncating User-Agent to 500 characters. `captureContext(actor)` returns an immutable `DriveAuditContext` containing only actor IDs/names, request ID, IP and sanitized User-Agent so asynchronous purge never reads `ServletUtils` off-request. Tests assert malformed forwarded values fall back and tokens, authorization headers and `storageKey` never enter the context or before/after summaries.

`DriveOperationLogPersistence.insert` runs on a separate bean with `REQUIRES_NEW`. For a success recorded while a mutation transaction is active, `DriveOperationLogService.success` registers one `TransactionSynchronization.afterCommit` callback and writes only after the business commit; on rollback, no success row is inserted. Outside a transaction (upload after its proxy commit, content open, async purge completion), write immediately through the same persistence bean. Failure logging also uses the isolated bean after the business exception boundary. Tests explicitly prove commit inserts once, rollback inserts zero successes, and audit insert failure cannot mark the business transaction rollback-only.

Successful folder creation records `CREATE_FOLDER`; upload records `UPLOAD`. Their validation, permission, quota, storage and conflict exits record the same action with `result=FAILURE` and the stable business code whenever a safe space/node context is available.

Operation logging is best effort for this first version: `success`/`failure` catch mapper exceptions internally, emit one structured server `ERROR` with action, request ID and safe business IDs, and never turn a completed upload/download/mutation into a failed client response. Add a test where the audit insert fails after node persistence and assert the upload still returns success while the structured error is emitted. Recent-use loss under an audit outage is acceptable and must be covered by the operations alert; file metadata and quota correctness are not.

- [ ] **Step 4: Run GREEN and verify no orphan on commit failure**

Run the two service tests and `DriveControllerContractTest`. Expected: successful upload leaves one private object and one node; every simulated failure leaves neither capacity nor object.

- [ ] **Step 5: Commit**

```bash
git add erp-modules/erp-file/src/main/java/com/erp/file/drive/domain/DriveAuditContext.java erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveUploadPersistence.java erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveUploadService.java erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveNodeService.java erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveOperationLogService.java erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveOperationLogPersistence.java erp-modules/erp-file/src/main/java/com/erp/file/drive/controller/DriveNodeController.java erp-modules/erp-file/src/test/java/com/erp/file/drive/service/DriveUploadServiceTest.java erp-modules/erp-file/src/test/java/com/erp/file/drive/service/DriveNodeServiceTest.java erp-modules/erp-file/src/test/java/com/erp/file/drive/service/DriveOperationLogServiceTest.java erp-modules/erp-file/src/test/java/com/erp/file/drive/controller/DriveControllerContractTest.java
git commit -m "feat(file): add drive upload and browse APIs"
```

### Task 3: Deliver authenticated preview and download streams

**Files:** `DriveContent`, content service/controller, exception handler, service/controller tests.

- [ ] **Step 1: Write failing content tests**

Prove: invalid content mode is rejected before service/storage; unknown/trash/folder nodes fail; personal outsider fails before storage read; same-department read succeeds; missing object returns `DRIVE_STORAGE_OBJECT_MISSING`; storage exception or physical-size/metadata mismatch returns `DRIVE_STORAGE_UNAVAILABLE`; PDF preview is inline; DOCX preview returns `DRIVE_PREVIEW_UNSUPPORTED`; DOCX download is attachment; responses include no-store and nosniff.

Controller assertions:

```java
ResponseEntity<Resource> response = controller.content(22L, "preview");
assertThat(response.getHeaders().getContentDisposition().getType()).isEqualTo("inline");
assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
assertThat(response.getHeaders().getContentDisposition().getFilename()).isEqualTo("员工手册.pdf");
```

- [ ] **Step 2: Run RED**

Run `DriveContentServiceTest,DriveContentControllerTest`; expect missing content types.

- [ ] **Step 3: Implement content resolution without path inputs**

```java
public record DriveContent(Resource resource, long size, String fileName,
        String contentType, boolean inline) { }
```

The controller accepts only exact `mode=preview|download` and a positive node ID. `resolve(Long nodeId, String mode, DriveActor actor)` loads `DriveNode`, loads its `DriveSpace`, calls `authorization.requireRead`, checks active file state, validates preview support when `mode=preview`, and calls `storage.open(node.getStorageKey())`. Compare `DriveStoredObject.size` with `node.sizeBytes`; a mismatch is a structured storage-integrity failure and must not be streamed. It returns only `DriveContent`; the controller never sees the key. Record `PREVIEW` or `DOWNLOAD` success only after the object opens and size matches, and record a safe failure for rejected/missing/storage cases without changing the returned business error. `DriveContentController` calls `featureGuard.requireEnabled()` before actor resolution.

Build the response with `ContentDisposition.inline()` or `.attachment()`, UTF-8 filename, declared content length, `Cache-Control: no-store`, `Pragma: no-cache`, and `X-Content-Type-Options: nosniff`. Use `application/octet-stream` when content type is blank or invalid.

Scope `DriveExceptionHandler` only to `com.erp.file.drive.controller` so legacy `SysFileController` keeps its existing exception behavior. It returns:

```java
return ResponseEntity.status(statusFor(ex.getBusinessCode()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(AjaxResult.error(ex.getMessage()).put("businessCode", ex.getBusinessCode()));
```

Use 403 for access denied, 404 for missing space/node/object, 409 for name/move/concurrency, 413 for file too large, 422 for rejected type/unsupported preview, 507 for quota, 503 for storage unavailable, and 503 for disabled.

- [ ] **Step 4: Run GREEN and inspect serialized errors**

Expected: response bodies never contain `storageKey`, `uploadPath`, SQL or exception messages from storage clients.

- [ ] **Step 5: Commit**

```bash
git add erp-modules/erp-file/src/main/java/com/erp/file/drive/domain/vo/DriveContent.java erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveContentService.java erp-modules/erp-file/src/main/java/com/erp/file/drive/controller/DriveContentController.java erp-modules/erp-file/src/main/java/com/erp/file/drive/controller/DriveExceptionHandler.java erp-modules/erp-file/src/test/java/com/erp/file/drive/service/DriveContentServiceTest.java erp-modules/erp-file/src/test/java/com/erp/file/drive/controller/DriveContentControllerTest.java
git commit -m "feat(file): secure drive content delivery"
```

### Task 4: Add rename, same-space move, search, and recent use

**Files:** node/log mapper XML, `DriveNodeService`, operation log service, node controller, node tests.

- [ ] **Step 1: Add failing lifecycle tests**

Cover: rename normalizes and uses version; file rename preserves its original extension while folder rename uses the whole normalized name; update count zero returns `DRIVE_CONCURRENT_MODIFICATION`; target parent must be active and same-space; moving to virtual root `0` succeeds without loading node 0; target-name collision returns `DRIVE_NAME_CONFLICT`; cross-space move fails; folder-to-self and folder-to-descendant fail without substring false positives (node 2 is not ancestor 12); descendant `ancestors` update in the same transaction; search starts at any nested folder but still searches the entire selected space and never crosses into another space; recent results are filtered again through current permissions and exclude trash/missing nodes.

```java
assertThatThrownBy(() -> service.move(folderId,
        new DriveMoveRequest(descendantId, 2), actor))
        .extracting("businessCode")
        .isEqualTo(DriveErrorCodes.DRIVE_INVALID_MOVE);
verify(nodeMapper, never()).updateParent(anyLong(), anyLong(), anyString(), anyInt(), anyString());
```

- [ ] **Step 2: Run RED**

Run `DriveNodeServiceTest`; expect missing rename/move/recent methods.

- [ ] **Step 3: Implement lock-safe lifecycle methods**

Add service contracts:

```java
DriveNodeVo rename(Long nodeId, DriveRenameRequest request, DriveActor actor);
DriveNodeVo move(Long nodeId, DriveMoveRequest request, DriveActor actor);
List<DriveNodeVo> recent(DriveActor actor, int limit);
```

Annotate rename and move with `@Transactional`. For a non-root move, lock source and target node IDs in ascending numeric order, then assign their roles; deterministic ordering prevents two crossing moves from taking opposite row-lock order. `targetParentId=0` is the virtual root, so lock the source then its space row as the target namespace, keep the source space and produce ancestors `0`. Reject target space mismatch before any update and map the active-name unique constraint to `DRIVE_NAME_CONFLICT`. For a file, reject a requested extension different from the stored normalized extension with `DRIVE_FILE_TYPE_REJECTED`; V1 renames only the base name so metadata and bytes cannot claim different formats. For folders, detect a cycle when `target.nodeId == source.nodeId` or the comma-delimited target ancestor tokens contain the exact source ID—never use raw substring matching. Update descendants whose ancestors equal the old prefix or start with `oldPrefix + ','`, replace only that prefix and increment each affected descendant version/update audit fields, then update the root with `where node_id=? and version=?`.

Log `RENAME`, `MOVE`, `OPEN`, `PREVIEW`, and `DOWNLOAD`. Recent SQL must remain MySQL 5.7-compatible: join the log table to a subquery grouped by `node_id` with `max(operation_id)` for the current user, `result='SUCCESS'`, and actions `OPEN/PREVIEW/DOWNLOAD`, then order the joined rows newest first. Clamp the service limit to 1..50 and bind it as a parameter; do not use window functions or `${limit}`. The service rechecks current read permission before returning each VO.

Expose `PUT /nodes/{nodeId}/name`, `PUT /nodes/{nodeId}/move`, and `GET /recent`. Rename/move return `AjaxResult.success(nodeVo)`; recent returns `AjaxResult.success(list)` and never a raw array.

- [ ] **Step 4: Run GREEN**

Expected: lifecycle tests pass and operation logs contain only logical summaries, not physical paths.

- [ ] **Step 5: Commit**

```bash
git add erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveNodeService.java erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveOperationLogService.java erp-modules/erp-file/src/main/java/com/erp/file/drive/controller/DriveNodeController.java erp-modules/erp-file/src/main/java/com/erp/file/drive/mapper erp-modules/erp-file/src/main/resources/mapper/drive erp-modules/erp-file/src/test/java/com/erp/file/drive/service/DriveNodeServiceTest.java
git commit -m "feat(file): add drive node lifecycle"
```

### Task 5: Add recycle-bin batches and safe purge

**Files:** trash service/persistence/worker/task, async config, claim/context, mappers, application scheduling, controller and `DriveControllerContractTest`.

- [ ] **Step 1: Write failing trash tests**

Across `DriveTrashServiceTest`, `DriveTrashPersistenceTest`, and `DriveTrashPurgeWorkerTest`, prove: deleting with a stale node version returns `DRIVE_CONCURRENT_MODIFICATION`; deleting a folder with the current version marks the entire active subtree with one `trashRootId`; list returns only roots and exposes `TRASHED/PURGING/PURGE_FAILED`; restore acts on the whole batch and falls back to root when original parent is missing; restore collision adds `(1)`; restore is rejected after purge has started; trash still consumes quota; purge atomically claims one batch as `PURGING` and the controller returns 202 before worker deletion; missing physical object is idempotent success; one storage deletion failure sets the root batch to `PURGE_FAILED` and releases no quota; successful worker completion removes metadata and releases total bytes once; a failed batch and a `PURGING` batch stale for 15 minutes can be claimed and completed on retry; two workers cannot claim the same current version; executor rejection marks failure; scheduled query uses the configured retention/cron.

- [ ] **Step 2: Run RED**

Run `DriveTrashServiceTest,DriveTrashPersistenceTest,DriveTrashPurgeWorkerTest`; expect missing service/persistence/worker/task and mapper statements.

- [ ] **Step 3: Implement batch semantics**

Required methods:

```java
void trash(Long nodeId, Integer version, DriveActor actor);
List<DriveNodeVo> listTrash(Long spaceId, DriveActor actor);
DriveNodeVo restore(Long trashRootId, DriveActor actor);
DrivePurgeClaim requestPurge(Long trashRootId, DriveActor actor);
int requestEmptyTrash(Long spaceId, DriveActor actor);
int dispatchExpired(int batchLimit);
```

`trash` locks the root, verifies write permission and matching version, and updates root plus descendants to `TRASHED`, `active_flag=NULL`, one `trash_root_id`, original parent, actor, time, `purge_after`, and incremented versions/audit fields. A stale version performs no subtree update. `restore` locks the batch, chooses original parent or `0`, then locks that active parent (or the space row for root) before resolving the collision name. It restores root and descendants to `ACTIVE`, clears trash fields, rebuilds ancestors from the locked target, and increments versions. This namespace lock makes the required automatic `(n)` restore naming deterministic under concurrent restores; the unique index remains the final invariant.

Record `TRASH`, `RESTORE`, and manual `PURGE` success/failure with root node ID, logical names/counts and no physical keys. Scheduled expiration uses a fixed system actor (`userId=0`, `username=system`, no permissions) only for audit attribution and records `CLEANUP` per batch; it does not route that actor through normal end-user authorization. An empty-trash request invokes the same per-batch purge path so each batch is auditable and independently retryable.

Purge sequence:

1. `DriveTrashPersistence.claim` transactionally compare-and-sets the entire batch from `TRASHED`/`PURGE_FAILED` (or stale `PURGING`) to a new `PURGING` version and returns `DrivePurgeClaim`; if the update count is zero, another worker owns it and this call exits without deleting;
2. only after that proxy transaction commits, submit the claim to `DriveTrashPurgeWorker.execute` on `drivePurgeExecutor` and let the HTTP request return;
3. the worker iterates file nodes and calls `storage.delete`; `exists=false` counts as success;
4. if one delete fails, `DriveTrashPersistence.markFailed` changes the claimed version to `PURGE_FAILED` and releases no quota;
5. after all objects are gone, `DriveTrashPersistence.finalizeClaim` transactionally verifies the claim version, deletes batch rows and calls `quota.release(spaceId,totalFileBytes)`; both the claimed-row delete count and space release count must match expectations or the transaction rolls back.

The retry path reuses the same method. A process crash after claiming or after deleting only some objects is recovered because a `PURGING` batch whose `update_time` is older than 15 minutes becomes claimable and missing objects are successes. `restore` is allowed only from `TRASHED`; any other state returns `DRIVE_CONCURRENT_MODIFICATION`. `PURGING` rows render as processing with actions disabled, while `PURGE_FAILED` renders a retry-purge action and no restore action because physical deletion may be partial.

`DriveAsyncConfig` enables async execution and defines only `drivePurgeExecutor`: core 1, max 2, queue 100, caller never runs storage deletion, thread prefix `drive-purge-`, wait up to 30 seconds on shutdown. Submission rejection is caught synchronously, marks the claim `PURGE_FAILED`, emits a structured error, and returns `DRIVE_STORAGE_UNAVAILABLE`; no unbounded or common pool is allowed. Worker exceptions are caught inside the worker and always attempt `markFailed`.

Add `@EnableScheduling` to `ErpFileApplication` and:

```java
@Scheduled(cron = "${drive.cleanup-cron:0 30 2 * * *}",
        zone = "${drive.cleanup-zone:Asia/Shanghai}")
public void cleanup() {
    if (!driveProperties.isEnabled()) return;
    trashService.dispatchExpired(100);
}
```

`dispatchExpired` selects due `TRASHED`, due/retryable `PURGE_FAILED`, and stale `PURGING` root batches in deterministic `purge_after, trash_root_id` order. It uses a system audit context, claims and submits each batch. The claim update is the concurrency boundary, so this remains safe if more than one file-service instance runs the scheduler.

Expose `DELETE /nodes/{nodeId}?version=...`, `GET /trash`, `POST /trash/{trashRootId}/restore`, `DELETE /trash/{trashRootId}`, and `DELETE /trash?spaceId=...`. The soft-delete `version` query parameter is required and nonnegative. Trash list returns `AjaxResult.success(list)`; restore returns the restored root VO in `data` and a message indicating when it fell back to root; soft-delete returns normal success after commit. Purge/empty return HTTP 202 with a safe Ajax payload after all requested batches are claimed/submitted, not after physical deletion; clients observe `PURGING` through `GET /trash`.

- [ ] **Step 4: Run GREEN**

Expected: every trash test passes; quota is unchanged by soft delete and changes once after confirmed purge.

- [ ] **Step 5: Commit**

```bash
git add erp-modules/erp-file/src/main/java/com/erp/file/ErpFileApplication.java erp-modules/erp-file/src/main/java/com/erp/file/drive/domain/DriveAuditContext.java erp-modules/erp-file/src/main/java/com/erp/file/drive/domain/DrivePurgeClaim.java erp-modules/erp-file/src/main/java/com/erp/file/drive/config/DriveAsyncConfig.java erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveTrashService.java erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveTrashPersistence.java erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveTrashPurgeWorker.java erp-modules/erp-file/src/main/java/com/erp/file/drive/task/DriveTrashCleanupTask.java erp-modules/erp-file/src/main/java/com/erp/file/drive/controller/DriveNodeController.java erp-modules/erp-file/src/main/java/com/erp/file/drive/mapper erp-modules/erp-file/src/main/resources/mapper/drive erp-modules/erp-file/src/test/java/com/erp/file/drive/service/DriveTrashServiceTest.java erp-modules/erp-file/src/test/java/com/erp/file/drive/service/DriveTrashPersistenceTest.java erp-modules/erp-file/src/test/java/com/erp/file/drive/service/DriveTrashPurgeWorkerTest.java erp-modules/erp-file/src/test/java/com/erp/file/drive/controller/DriveControllerContractTest.java
git commit -m "feat(file): add drive recycle bin cleanup"
```

### Task 6: Add MinIO parity and the gateway boundary

**Files:** MinIO provider/test, application configs, gateway YAML/test, controller contract and legacy tests.

- [ ] **Step 1: Write failing provider and route tests**

Mock `MinioClient` and assert `putObject`, `getObject`, `statObject`, and `removeObject` always use `drive.minio-bucket` and the exact generated key, never the legacy `minio.bucketName`. Test missing-object delete as success, reject a drive bucket equal to the legacy bucket, and reject an anonymous-read bucket policy. `FileDriveRouteConfigTest` reads gateway YAML and asserts `erp-file-drive-api`, `Path=/file/drive/**`, `StripPrefix=1`, and that its text position precedes `erp-file-static`. `DriveStartupValidatorTest` proves disabled mode performs no probe, while enabled mode calls `storage.validate()` and requires `COMPANY:ROOT` to exist.

- [ ] **Step 2: Run RED**

```bash
mvn -pl erp-modules/erp-file,erp-gateway -am -Dtest=MinioDriveStorageProviderTest,FileDriveRouteConfigTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: provider and route are absent.

- [ ] **Step 3: Implement the conditional provider and route**

Annotate MinIO provider with:

```java
@Service
@ConditionalOnProperty(prefix = "drive", name = "storage-type", havingValue = "minio")
```

Reuse only the existing `MinioClient` credentials/endpoint; object operations use the dedicated `drive.minio-bucket` (default `erp-drive-private`), never `MinioConfig.bucketName`, because the legacy bucket may serve public attachments. MinIO deployment must set both `drive.storage-type=minio` and `minio.enabled=true`. When drive storage selects MinIO but the enabled flag, credentials or client are unavailable, fail application startup with a clear configuration exception rather than silently falling back to local. Never construct a public URL.

Implement `validate()` with `bucketExists`; fail startup when the dedicated bucket is absent rather than creating production storage implicitly. Fetch/parse its bucket-policy JSON and fail closed on an `Effect=Allow` statement whose principal is anonymous (`"*"`) and whose actions include `s3:GetObject`, `s3:ListBucket`, or `s3:*`. A missing bucket policy is the expected private default; malformed/unreadable policy also fails closed. Also fail when the dedicated name equals the legacy public bucket name.

Insert this route before `erp-file-static`:

```yaml
- id: erp-file-drive-api
  uri: http://127.0.0.1:9300
  predicates:
    - Path=/file/drive/**
  filters:
    - StripPrefix=1
```

Do not widen the old `erp-file-api` route and do not add the drive path to anonymous/static ignore rules.

Implement `DriveStartupValidator` as an `ApplicationRunner`:

```java
@Override
public void run(ApplicationArguments args) throws Exception {
    if (!properties.isEnabled()) return;
    storage.validate();
    if (spaceMapper.selectByKey("COMPANY:ROOT") == null) {
        throw new IllegalStateException("cloud drive schema/company space is not ready");
    }
}
```

This startup failure is intentional: when the feature is enabled, a missing table, unwritable directory, missing MinIO bucket or absent company space must stop the file service rather than expose a half-working menu.

- [ ] **Step 4: Run backend regression**

```bash
mvn -pl erp-modules/erp-file -am test
mvn -pl erp-gateway -am test
```

Expected: both reactors succeed; `SysFileControllerTest`, `LocalSysFileServiceImplTest`, `ResourcesConfigTest` and all drive tests pass.

- [ ] **Step 5: Commit**

```bash
git add erp-modules/erp-file/src/main/java/com/erp/file/drive/storage/MinioDriveStorageProvider.java erp-modules/erp-file/src/main/java/com/erp/file/drive/config/DriveStartupValidator.java erp-modules/erp-file/src/test/java/com/erp/file/drive/storage/MinioDriveStorageProviderTest.java erp-modules/erp-file/src/test/java/com/erp/file/drive/config/DriveStartupValidatorTest.java erp-modules/erp-file/src/main/resources/application-dev.yml erp-modules/erp-file/src/main/resources/application-local.yml erp-gateway/src/main/resources/bootstrap.yml erp-gateway/src/test/java/com/erp/gateway/config/FileDriveRouteConfigTest.java erp-modules/erp-file/src/test/java/com/erp/file/controller/SysFileControllerTest.java
git commit -m "feat(file): complete drive storage providers"
```

### Task 7: Phase API acceptance and consistency audit

**Files:** append the backend lifecycle section to `docs/superpowers/verification/2026-07-11-cloud-drive.md`.

- [ ] **Step 1: Run the full backend test gates**

```bash
mvn -pl erp-modules/erp-file -am test
mvn -pl erp-gateway -am test
```

Expected: `BUILD SUCCESS`, zero test failures/errors.

- [ ] **Step 2: Run a real local API smoke and concurrency test**

With a scratch user for each permission profile and `DRIVE_ENABLED=true`, exercise: list spaces, create folder, upload PDF, list, preview, download, rename, move, whole-space search, delete, restore, purge. Assert purge returns 202, poll trash until the batch disappears or becomes `PURGE_FAILED`, and only then run consistency checks. Repeat a personal-file download as a different user and a department-file download as a different department. Expected: both return 403 with `DRIVE_ACCESS_DENIED`.

Then launch two barrier-synchronized uploads of the same synthetic filename into one writable folder with enough quota; both must succeed and list as the base name plus `(1)`. In a disposable personal space, set remaining quota to more than one but less than two copies of another synthetic file and launch two different-name uploads concurrently; exactly one succeeds, the other returns `DRIVE_QUOTA_EXCEEDED`, `used_bytes` increases once, and the losing storage key is absent. Use a shell barrier/background jobs or a small test script, wait for both responses, and record status/business codes without tokens.

- [ ] **Step 3: Prove storage/metadata consistency**

After the smoke flow, compare `drive_space.used_bytes` with the sum of active and trashed file nodes, verify every active/trash file has an existing private object of the same byte length, recompute SHA-256 for the synthetic smoke objects and match metadata, and verify no `storage_key` appears in captured API JSON.

- [ ] **Step 4: Record evidence**

Write exact commands, results, tested identities, object counts, quota totals and known non-goals into the verification document. Do not include tokens, credentials, employee file names or physical paths.

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/verification/2026-07-11-cloud-drive.md
git commit -m "test: verify cloud drive backend lifecycle"
```

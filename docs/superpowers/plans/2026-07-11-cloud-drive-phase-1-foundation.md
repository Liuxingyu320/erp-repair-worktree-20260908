# 企业云盘阶段 1：安全存储与空间基础 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 `erp-file` 在不改变现有公共附件接口的前提下，具备云盘数据源、三张表、私有本地存储、登录用户解析、三类空间权限、额度和可测试的空间查询接口。

**Architecture:** 云盘代码放在 `com.erp.file.drive` 边界内；`DriveStorageProvider` 只处理私有对象，`DriveAuthorizationService` 只处理业务权限，`DriveSpaceService` 负责幂等空间创建，`DriveQuotaService` 用条件更新防止并发超额。现有 `SysFileController`、`ISysFileService` 和 `/file/public/**` 不改行为。

**Tech Stack:** Java 17, Spring Boot 4, MyBatis XML, MySQL 5.7/8.0 compatible SQL, Spring `Resource`, JUnit 5, Mockito, AssertJ.

---

## File map

**Create:**

- `sql/erp_cloud_drive_20260711.sql` — 三张云盘表和唯一公司空间；本阶段不创建菜单。
- `docker/mysql/db/erp_cloud_drive_20260711.sql` — 与根 SQL 字节一致的部署副本。
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/config/DriveProperties.java` — 功能开关、存储、大小、额度和清理配置。
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/constant/DriveConstants.java` — 空间、节点、状态、权限和根目录常量。
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/constant/DriveErrorCodes.java` — 稳定业务错误码。
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/exception/DriveException.java` — 携带 `businessCode` 的业务异常。
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/domain/DriveSpace.java` — 空间持久化模型。
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/domain/DriveNode.java` — 文件/文件夹持久化模型。
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/domain/DriveOperationLog.java` — 操作日志模型。
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/domain/DriveIdentity.java` — 从当前数据库读取的启用用户、部门和部门名称。
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/domain/DriveActor.java` — 当前登录用户不可变上下文。
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/domain/dto/DriveQuotaRequest.java` — 额度和乐观锁请求。
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/domain/vo/DriveSpaceVo.java` — 不泄露内部字段的空间响应。
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/mapper/DriveSpaceMapper.java`.
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/mapper/DriveNodeMapper.java`.
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/mapper/DriveOperationLogMapper.java`.
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/mapper/DriveIdentityMapper.java` — 只读查询当前 `sys_user/sys_dept`，不信任令牌中的旧部门。
- `erp-modules/erp-file/src/main/resources/mapper/drive/DriveSpaceMapper.xml`.
- `erp-modules/erp-file/src/main/resources/mapper/drive/DriveNodeMapper.xml`.
- `erp-modules/erp-file/src/main/resources/mapper/drive/DriveOperationLogMapper.xml`.
- `erp-modules/erp-file/src/main/resources/mapper/drive/DriveIdentityMapper.xml`.
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/storage/DriveStorageProvider.java` — 私有对象存储接口。
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/storage/DriveStoredObject.java` — 已打开对象的 `Resource` 与大小。
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/storage/LocalDriveStorageProvider.java` — 私有本地目录实现。
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveActorResolver.java`.
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveFeatureGuard.java` — 统一返回 `DRIVE_DISABLED` 的关闭开关守卫。
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/filter/DriveFeatureFilter.java` — 在方法权限切面之前拦截关闭状态的 `/drive/**` 请求。
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveAuthorizationService.java`.
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveQuotaService.java`.
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveSpaceService.java`.
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/controller/DriveSpaceController.java`.
- `erp-modules/erp-file/src/test/java/com/erp/file/drive/sql/CloudDriveSqlSourceTest.java`.
- `erp-modules/erp-file/src/test/java/com/erp/file/drive/mapper/DriveMapperBindingTest.java`.
- `erp-modules/erp-file/src/test/java/com/erp/file/drive/config/DrivePropertiesTest.java`.
- `erp-modules/erp-file/src/test/java/com/erp/file/drive/storage/LocalDriveStorageProviderTest.java`.
- `erp-modules/erp-file/src/test/java/com/erp/file/drive/service/DriveAuthorizationServiceTest.java`.
- `erp-modules/erp-file/src/test/java/com/erp/file/drive/filter/DriveFeatureFilterTest.java`.
- `erp-modules/erp-file/src/test/java/com/erp/file/drive/service/DriveSpaceServiceTest.java`.
- `erp-modules/erp-file/src/test/java/com/erp/file/drive/controller/DriveSpaceControllerTest.java`.

**Modify:**

- `erp-modules/erp-file/pom.xml` — 增加 MySQL、数据源、日志和 Swagger 依赖。
- `erp-modules/erp-file/src/main/java/com/erp/file/ErpFileApplication.java` — 启用数据源，不再排除 `DataSourceAutoConfiguration`。
- `erp-modules/erp-file/src/main/resources/bootstrap.yml` — 增加 MyBatis mapper 与别名配置。
- `erp-modules/erp-file/src/main/resources/application-dev.yml` — 增加默认关闭的 `drive.*` 配置。
- `erp-modules/erp-file/src/main/resources/application-local.yml` — 保留现有本地值并追加数据源、MyBatis 和 `drive.*` 配置。
- `erp-modules/erp-file/src/test/java/com/erp/file/service/LocalSysFileServiceImplTest.java` — 锁定旧公共上传回归。
- `erp-modules/erp-file/src/test/java/com/erp/file/config/ResourcesConfigTest.java` — 锁定私有目录不被静态映射。

### Task 1: Enable persistence and create the repeat-safe schema

**Files:** POM、应用入口、三个配置文件、两份 SQL、`CloudDriveSqlSourceTest`。

- [ ] **Step 1: Write the failing SQL and wiring tests**

`CloudDriveSqlSourceTest` 必须检查两份脚本字节一致、三个表存在、公司空间使用 `COMPANY:ROOT`、节点活动名称唯一索引存在，并且本阶段没有 `sys_menu` 写入：

```java
byte[] source = Files.readAllBytes(repo.resolve("sql/erp_cloud_drive_20260711.sql"));
byte[] mirror = Files.readAllBytes(repo.resolve("docker/mysql/db/erp_cloud_drive_20260711.sql"));
assertThat(mirror).isEqualTo(source);
String sql = new String(source, StandardCharsets.UTF_8);
assertThat(sql).contains("CREATE TABLE IF NOT EXISTS drive_space");
assertThat(sql).contains("CREATE TABLE IF NOT EXISTS drive_node");
assertThat(sql).contains("CREATE TABLE IF NOT EXISTS drive_operation_log");
assertThat(sql).contains("uk_drive_node_active_name");
assertThat(sql).contains("idx_drive_node_purge");
assertThat(sql).contains("'COMPANY:ROOT'");
assertThat(sql).doesNotContain("INSERT INTO sys_menu");
```

在同一测试中读取 `ErpFileApplication.java` 和 `pom.xml`，断言应用不再包含 `DataSourceAutoConfiguration.class`，POM 包含 `mysql-connector-j`、`erp-common-datasource`、`erp-common-log` 和 `erp-common-swagger`。

- [ ] **Step 2: Run RED**

```bash
mvn -pl erp-modules/erp-file -am -Dtest=CloudDriveSqlSourceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because the SQL and test class do not exist and the file application still excludes the data source.

- [ ] **Step 3: Add exact schema and data-source wiring**

Use MySQL 5.7-compatible definitions. The required keys are:

```sql
CREATE TABLE IF NOT EXISTS drive_space (
  space_id bigint NOT NULL AUTO_INCREMENT,
  space_key varchar(100) NOT NULL,
  space_type varchar(20) NOT NULL,
  owner_user_id bigint DEFAULT NULL,
  dept_id bigint DEFAULT NULL,
  space_name varchar(120) NOT NULL,
  quota_bytes bigint NOT NULL,
  used_bytes bigint NOT NULL DEFAULT 0,
  status varchar(20) NOT NULL DEFAULT 'ACTIVE',
  version int NOT NULL DEFAULT 0,
  create_by varchar(64) DEFAULT '',
  create_time datetime DEFAULT NULL,
  update_by varchar(64) DEFAULT '',
  update_time datetime DEFAULT NULL,
  remark varchar(500) DEFAULT NULL,
  PRIMARY KEY (space_id),
  UNIQUE KEY uk_drive_space_key (space_key),
  KEY idx_drive_space_owner (owner_user_id, status),
  KEY idx_drive_space_dept (dept_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='云盘空间';

CREATE TABLE IF NOT EXISTS drive_node (
  node_id bigint NOT NULL AUTO_INCREMENT,
  space_id bigint NOT NULL,
  parent_id bigint NOT NULL DEFAULT 0,
  ancestors varchar(1000) NOT NULL DEFAULT '0',
  node_type varchar(20) NOT NULL,
  node_name varchar(200) NOT NULL,
  normalized_name varchar(200) NOT NULL,
  extension varchar(20) DEFAULT NULL,
  storage_key varchar(500) DEFAULT NULL,
  content_type varchar(120) DEFAULT NULL,
  size_bytes bigint NOT NULL DEFAULT 0,
  sha256 varchar(64) DEFAULT NULL,
  status varchar(20) NOT NULL DEFAULT 'ACTIVE',
  active_flag tinyint DEFAULT 1,
  original_parent_id bigint DEFAULT NULL,
  trash_root_id bigint DEFAULT NULL,
  trashed_by bigint DEFAULT NULL,
  trashed_time datetime DEFAULT NULL,
  purge_after datetime DEFAULT NULL,
  version int NOT NULL DEFAULT 0,
  create_by varchar(64) DEFAULT '',
  create_time datetime DEFAULT NULL,
  update_by varchar(64) DEFAULT '',
  update_time datetime DEFAULT NULL,
  remark varchar(500) DEFAULT NULL,
  PRIMARY KEY (node_id),
  UNIQUE KEY uk_drive_node_active_name (space_id, parent_id, normalized_name, active_flag),
  KEY idx_drive_node_parent (space_id, parent_id, status),
  KEY idx_drive_node_ancestors (space_id, status),
  KEY idx_drive_node_trash (space_id, trash_root_id, status, purge_after),
  KEY idx_drive_node_purge (status, purge_after, update_time, trash_root_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='云盘文件节点';

CREATE TABLE IF NOT EXISTS drive_operation_log (
  operation_id bigint NOT NULL AUTO_INCREMENT,
  space_id bigint DEFAULT NULL,
  node_id bigint DEFAULT NULL,
  action varchar(40) NOT NULL,
  operator_user_id bigint NOT NULL,
  operator_dept_id bigint DEFAULT NULL,
  operator_name varchar(64) DEFAULT '',
  request_id varchar(64) DEFAULT NULL,
  ip_address varchar(64) DEFAULT NULL,
  user_agent varchar(500) DEFAULT NULL,
  before_summary varchar(1000) DEFAULT NULL,
  after_summary varchar(1000) DEFAULT NULL,
  result varchar(20) NOT NULL,
  error_code varchar(64) DEFAULT NULL,
  create_time datetime NOT NULL,
  PRIMARY KEY (operation_id),
  KEY idx_drive_log_operator (operator_user_id, create_time),
  KEY idx_drive_log_recent (operator_user_id, result, action, create_time, node_id),
  KEY idx_drive_log_node (node_id, create_time),
  KEY idx_drive_log_space (space_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='云盘操作日志';
```

Insert the company space with `INSERT ... SELECT ... WHERE NOT EXISTS`, `space_name='公司公共盘'`, quota `107374182400`, and no hard-coded `space_id`.

In `ErpFileApplication`, change the annotation to:

```java
@SpringBootApplication
public class ErpFileApplication
```

Add the same datasource dependencies used by `erp-system`. Put this common MyBatis configuration in `bootstrap.yml`:

```yaml
mybatis:
  typeAliasesPackage: com.erp.file.drive.domain
  mapperLocations: classpath:mapper/**/*.xml
```

Preserve all existing local file, referer and MinIO values. Add a local MySQL datasource using the same `MYSQL_USERNAME` and `MYSQL_PASSWORD` environment names as `erp-system`.

- [ ] **Step 4: Run GREEN and apply the SQL twice to a scratch schema**

Run the focused Maven test. Then create a disposable schema and apply `sql/erp_cloud_drive_20260711.sql` twice. Expected: both applications succeed; each table has exactly one matching row in `information_schema.tables`, and `COMPANY:ROOT` remains one data row.

- [ ] **Step 5: Commit**

```bash
git add erp-modules/erp-file/pom.xml erp-modules/erp-file/src/main/java/com/erp/file/ErpFileApplication.java erp-modules/erp-file/src/main/resources/bootstrap.yml erp-modules/erp-file/src/main/resources/application-dev.yml erp-modules/erp-file/src/main/resources/application-local.yml sql/erp_cloud_drive_20260711.sql docker/mysql/db/erp_cloud_drive_20260711.sql erp-modules/erp-file/src/test/java/com/erp/file/drive/sql/CloudDriveSqlSourceTest.java
git commit -m "feat(file): enable cloud drive persistence"
```

### Task 2: Add domain models and mapper bindings

**Files:** three domain models, three mapper interfaces/XML files, `DriveConstants`, and `DriveMapperBindingTest`.

- [ ] **Step 1: Write the failing mapper binding test**

The test must parse all XML resources and assert these fully qualified statements:

```java
assertMapped(configuration, DriveSpaceMapper.class, "selectById");
assertMapped(configuration, DriveSpaceMapper.class, "selectByKey");
assertMapped(configuration, DriveSpaceMapper.class, "insertIgnore");
assertMapped(configuration, DriveSpaceMapper.class, "reserveQuota");
assertMapped(configuration, DriveSpaceMapper.class, "releaseQuota");
assertMapped(configuration, DriveSpaceMapper.class, "updateQuota");
assertMapped(configuration, DriveNodeMapper.class, "selectById");
assertMapped(configuration, DriveNodeMapper.class, "insertNode");
assertMapped(configuration, DriveOperationLogMapper.class, "insertOperation");
assertMapped(configuration, DriveIdentityMapper.class, "selectCurrentIdentity");
```

Use `XMLMapperBuilder` with resource paths under `mapper/drive/` and finish with:

```java
private static void assertMapped(Configuration configuration, Class<?> mapper, String id) {
    assertThat(configuration.hasStatement(mapper.getName() + "." + id)).isTrue();
}
```

- [ ] **Step 2: Run RED**

```bash
mvn -pl erp-modules/erp-file -am -Dtest=DriveMapperBindingTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation fails because domain and mapper types do not exist.

- [ ] **Step 3: Implement exact mapper contracts**

```java
public interface DriveSpaceMapper {
    DriveSpace selectById(Long spaceId);
    DriveSpace selectByKey(String spaceKey);
    int insertIgnore(DriveSpace space);
    int reserveQuota(@Param("spaceId") Long spaceId, @Param("bytes") long bytes);
    int releaseQuota(@Param("spaceId") Long spaceId, @Param("bytes") long bytes);
    int updateQuota(@Param("spaceId") Long spaceId,
            @Param("quotaBytes") long quotaBytes,
            @Param("version") int version,
            @Param("updateBy") String updateBy);
}

public interface DriveNodeMapper {
    DriveNode selectById(Long nodeId);
    int insertNode(DriveNode node);
}

public interface DriveOperationLogMapper {
    int insertOperation(DriveOperationLog operation);
}

public interface DriveIdentityMapper {
    DriveIdentity selectCurrentIdentity(Long userId);
}
```

`reserveQuota` must be one conditional update:

```sql
update drive_space
set used_bytes = used_bytes + #{bytes}, version = version + 1, update_time = now()
where space_id = #{spaceId}
  and status = 'ACTIVE'
  and used_bytes + #{bytes} &lt;= quota_bytes
```

`releaseQuota` must clamp at zero with `greatest(used_bytes - #{bytes}, 0)` and increment version just like reserve. `updateQuota` must use `where space_id=? and version=? and used_bytes <= #{quotaBytes}`, then increment version; after update count zero, reload the row and return `DRIVE_QUOTA_EXCEEDED` if current use exceeds the request, otherwise `DRIVE_CONCURRENT_MODIFICATION`. This prevents an upload racing a quota reduction from leaving `used_bytes > quota_bytes`. `insertIgnore` uses generated keys and `INSERT IGNORE` so concurrent first access remains idempotent.

Create JavaBean fields matching every SQL column. `DriveNode` must not expose `storageKey` to controllers; later controllers return a VO instead of serializing this entity.

`DriveIdentityMapper.xml` must query the live organization on every cloud-drive request:

```sql
select u.user_id as userId, d.dept_id as deptId, d.dept_name as deptName
from sys_user u
left join sys_dept d on d.dept_id = u.dept_id and d.status = '0' and d.del_flag = '0'
where u.user_id = #{userId}
  and u.status = '0'
  and u.del_flag = '0'
```

Select the department ID from the validated join (`d.dept_id as deptId`), not directly from `u.dept_id`; a disabled/deleted department therefore becomes no department rather than retaining access with a null name. This read is intentional: a user moved to another department must lose the old department drive on the next request even if the login token still contains the previous `deptId`.

- [ ] **Step 4: Run GREEN**

Expected: all ten mapper statements bind and the module compiles.

- [ ] **Step 5: Commit**

```bash
git add erp-modules/erp-file/src/main/java/com/erp/file/drive/constant/DriveConstants.java erp-modules/erp-file/src/main/java/com/erp/file/drive/domain erp-modules/erp-file/src/main/java/com/erp/file/drive/mapper erp-modules/erp-file/src/main/resources/mapper/drive erp-modules/erp-file/src/test/java/com/erp/file/drive/mapper/DriveMapperBindingTest.java
git commit -m "feat(file): add cloud drive persistence model"
```

### Task 3: Add feature properties and private local storage

**Files:** `DriveProperties`, storage interface/value/local implementation, configuration/storage tests, existing resource/public-upload tests.

- [ ] **Step 1: Write failing configuration and storage tests**

`DrivePropertiesTest` binds no properties and asserts the secure defaults:

```java
assertThat(properties.isEnabled()).isFalse();
assertThat(properties.getStorageType()).isEqualTo("local");
assertThat(properties.getMinioBucket()).isEqualTo("erp-drive-private");
assertThat(properties.getMaxFileSize()).isEqualTo(104857600L);
assertThat(properties.getPersonalQuota()).isEqualTo(2147483648L);
assertThat(properties.getDepartmentQuota()).isEqualTo(21474836480L);
assertThat(properties.getCompanyQuota()).isEqualTo(107374182400L);
assertThat(properties.getTrashRetentionDays()).isEqualTo(30);
assertThat(properties.getCleanupZone()).isEqualTo("Asia/Shanghai");
```

Also bind the YAML in a context test and assert Spring's multipart ceiling is `104857600` bytes per file and `115343360` bytes per request. The larger request ceiling covers multipart framing only; `DriveFilePolicy` remains the authoritative 100 MiB business limit.

`LocalDriveStorageProviderTest` uses `@TempDir` and proves `put/open/exists/delete`, rejects `../public/escape.txt`, and stores under the configured private root rather than `public/`.

- [ ] **Step 2: Run RED**

```bash
mvn -pl erp-modules/erp-file -am -Dtest=DrivePropertiesTest,LocalDriveStorageProviderTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: missing property and storage classes.

- [ ] **Step 3: Implement focused storage contracts**

```java
public interface DriveStorageProvider {
    void put(String storageKey, InputStream input) throws IOException;
    DriveStoredObject open(String storageKey) throws IOException;
    boolean exists(String storageKey);
    void delete(String storageKey) throws IOException;
    void validate() throws IOException;
}

public record DriveStoredObject(Resource resource, long size) { }
```

`LocalDriveStorageProvider.resolve` must normalize and contain-check every key:

```java
private Path resolve(String storageKey) {
    Path candidate = root.resolve(storageKey).normalize();
    if (!candidate.startsWith(root)) {
        throw new DriveException(DriveErrorCodes.DRIVE_ACCESS_DENIED, "非法存储路径");
    }
    return candidate;
}
```

Write via a sibling `.part` file, then `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`; if atomic move is unsupported, retry with `REPLACE_EXISTING`. Always delete the `.part` file in `finally`.

`validate()` creates the private root when missing, resolves it to an absolute normalized path, and verifies it is a writable directory. It never creates or probes a file under the public directory.

Annotate the provider with:

```java
@Service
@ConditionalOnProperty(prefix = "drive", name = "storage-type", havingValue = "local", matchIfMissing = true)
```

Bind `DriveProperties` with `@ConfigurationProperties(prefix="drive")` and `@Component`. Put the exact defaults from the roadmap in the fields. Add the roadmap's `spring.servlet.multipart` values to both dev and preserved local configuration; use the same `DRIVE_MAX_FILE_SIZE` environment value for transport and business validation, and `DRIVE_MAX_REQUEST_SIZE` only for multipart overhead.

- [ ] **Step 4: Run GREEN plus public-file regression**

```bash
mvn -pl erp-modules/erp-file -am -Dtest=DrivePropertiesTest,LocalDriveStorageProviderTest,LocalSysFileServiceImplTest,ResourcesConfigTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: private storage tests pass; existing public upload still writes only under `public/`; `ResourcesConfig` still maps only `/file/public/**`.

- [ ] **Step 5: Commit**

```bash
git add erp-modules/erp-file/src/main/java/com/erp/file/drive/config erp-modules/erp-file/src/main/java/com/erp/file/drive/storage erp-modules/erp-file/src/main/java/com/erp/file/drive/constant/DriveErrorCodes.java erp-modules/erp-file/src/main/java/com/erp/file/drive/exception erp-modules/erp-file/src/main/resources/application-dev.yml erp-modules/erp-file/src/main/resources/application-local.yml erp-modules/erp-file/src/test/java/com/erp/file/drive/config erp-modules/erp-file/src/test/java/com/erp/file/drive/storage erp-modules/erp-file/src/test/java/com/erp/file/service/LocalSysFileServiceImplTest.java erp-modules/erp-file/src/test/java/com/erp/file/config/ResourcesConfigTest.java
git commit -m "feat(file): add private drive storage"
```

### Task 4: Resolve the login actor and enforce the permission matrix

**Files:** `DriveActor`, `DriveActorResolver`, `DriveFeatureGuard`, `DriveFeatureFilter`, `DriveAuthorizationService`, `DriveConstants`, authorization/filter tests.

- [ ] **Step 1: Write failing permission-matrix tests**

Create actors for owner, same-department employee, department manager, company manager, outsider and admin. Prove these cases:

```java
assertThat(service.canRead(owner, personalSpace(20L))).isTrue();
assertThat(service.canWrite(outsider, personalSpace(20L))).isFalse();
assertThat(service.canRead(employee, companySpace())).isTrue();
assertThat(service.canWrite(companyManager, companySpace())).isTrue();
assertThat(service.canWrite(deptManager, departmentSpace(8L))).isTrue();
assertThat(service.canRead(employee, departmentSpace(99L))).isFalse();
assertThat(service.canWrite(admin, departmentSpace(99L))).isTrue();
```

Also test that `DriveActorResolver` reads `userId`, `username` and permissions from `SecurityUtils.getLoginUser()`, but obtains the current `deptId/deptName` from `DriveIdentityMapper`. Set token department to 8 and mapper department to 9; the resulting actor must use 9. A user whose joined department is disabled resolves with no department and sees no department space; an absent/disabled user identity fails closed. The resolver must not inspect the `Dept-NumId` request header.

`DriveFeatureFilterTest` uses mock servlet requests to prove: disabled `/drive/spaces` returns HTTP 503 JSON with `businessCode=DRIVE_DISABLED` and never invokes the chain; enabled `/drive/spaces` invokes the chain; `/upload` always bypasses the filter. This is required because `@RequiresPermissions` advice runs before controller method bodies.

- [ ] **Step 2: Run RED**

Run `DriveAuthorizationServiceTest,DriveFeatureFilterTest`; expect missing actor/resolver/service/filter types.

- [ ] **Step 3: Implement the stable actor and rules**

```java
public record DriveActor(Long userId, Long deptId, String deptName, String username,
        Set<String> permissions, boolean admin) {
    public boolean hasPermission(String permission) {
        return permissions != null && permissions.contains(permission);
    }
}
```

Use methods:

```java
boolean canRead(DriveActor actor, DriveSpace space);
boolean canWrite(DriveActor actor, DriveSpace space);
void requireRead(DriveActor actor, DriveSpace space);
void requireWrite(DriveActor actor, DriveSpace space);
void requireQuotaManage(DriveActor actor);
```

Every failed requirement throws `new DriveException(DRIVE_ACCESS_DENIED, "无权访问该云盘空间")`. `DriveActorResolver` fails closed when login user, user ID or live `DriveIdentity` is missing.

Add `DriveFeatureGuard.requireEnabled()`:

```java
public void requireEnabled() {
    if (!properties.isEnabled()) {
        throw new DriveException(DriveErrorCodes.DRIVE_DISABLED, "云盘功能尚未开启");
    }
}
```

Every cloud-drive controller calls this before actor resolution or mapper access. The test must verify a disabled request performs no space, node or storage call.

Add an ordered `OncePerRequestFilter` for the internal path prefix `/drive/`. It runs before controller/AOP authorization, writes UTF-8 `application/json` through Jackson with status 503, `Cache-Control: no-store`, and the same safe `AjaxResult`/`businessCode`, and does not resolve a user or touch a mapper. When enabled or when the URI is outside `/drive/**`, it delegates unchanged. Keep `DriveFeatureGuard` in controllers as defense in depth and for direct service/controller tests.

Define `DriveErrorCodes` with the exact stable field names from the roadmap:

```java
public static final String DRIVE_DISABLED = "DRIVE_DISABLED";
public static final String DRIVE_ACCESS_DENIED = "DRIVE_ACCESS_DENIED";
public static final String DRIVE_SPACE_NOT_FOUND = "DRIVE_SPACE_NOT_FOUND";
public static final String DRIVE_NODE_NOT_FOUND = "DRIVE_NODE_NOT_FOUND";
public static final String DRIVE_NAME_CONFLICT = "DRIVE_NAME_CONFLICT";
public static final String DRIVE_INVALID_MOVE = "DRIVE_INVALID_MOVE";
public static final String DRIVE_QUOTA_EXCEEDED = "DRIVE_QUOTA_EXCEEDED";
public static final String DRIVE_FILE_TOO_LARGE = "DRIVE_FILE_TOO_LARGE";
public static final String DRIVE_FILE_TYPE_REJECTED = "DRIVE_FILE_TYPE_REJECTED";
public static final String DRIVE_PREVIEW_UNSUPPORTED = "DRIVE_PREVIEW_UNSUPPORTED";
public static final String DRIVE_CONCURRENT_MODIFICATION = "DRIVE_CONCURRENT_MODIFICATION";
public static final String DRIVE_STORAGE_UNAVAILABLE = "DRIVE_STORAGE_UNAVAILABLE";
public static final String DRIVE_STORAGE_OBJECT_MISSING = "DRIVE_STORAGE_OBJECT_MISSING";
```

`DriveException` stores a nonblank `businessCode`, exposes `getBusinessCode()`, and uses a user-safe message only; internal exception details stay in server logs.

- [ ] **Step 4: Run GREEN**

Expected: the complete matrix and pre-AOP feature-filter tests pass, and no selected-shop context appears in the implementation.

- [ ] **Step 5: Commit**

```bash
git add erp-modules/erp-file/src/main/java/com/erp/file/drive/domain/DriveActor.java erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveActorResolver.java erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveFeatureGuard.java erp-modules/erp-file/src/main/java/com/erp/file/drive/filter/DriveFeatureFilter.java erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveAuthorizationService.java erp-modules/erp-file/src/main/java/com/erp/file/drive/constant/DriveConstants.java erp-modules/erp-file/src/test/java/com/erp/file/drive/service/DriveAuthorizationServiceTest.java erp-modules/erp-file/src/test/java/com/erp/file/drive/filter/DriveFeatureFilterTest.java
git commit -m "feat(file): enforce drive space permissions"
```

### Task 5: Create visible spaces, quota primitives, and the first API

**Files:** `DriveQuotaService`, `DriveSpaceService`, `DriveSpaceVo`, `DriveSpaceController`, service/controller tests.

- [ ] **Step 1: Write failing service and controller tests**

Service tests must prove:

- personal key `PERSONAL:<userId>` is inserted once and reselected;
- department key `DEPARTMENT:<deptId>` is omitted for no-department users;
- company key `COMPANY:ROOT` must already exist or returns `DRIVE_SPACE_NOT_FOUND`;
- returned order is personal, company, department;
- `reserve` throws `DRIVE_QUOTA_EXCEEDED` when mapper update count is zero;
- quota update requires `drive:quota:manage` and `newQuota >= usedBytes`.
- an upload racing a quota reduction cannot commit a quota below the new/current usage; zero update count is classified by reloading the row.

Controller reflection/behavior test:

```java
Method method = DriveSpaceController.class.getMethod("spaces");
RequiresPermissions permission = method.getAnnotation(RequiresPermissions.class);
assertThat(permission.value()).containsExactly("drive:access");
assertThat(DriveSpaceController.class.getAnnotation(RequestMapping.class).value())
        .containsExactly("/drive");
Method quota = DriveSpaceController.class.getMethod(
        "updateQuota", Long.class, DriveQuotaRequest.class);
assertThat(quota.getAnnotation(RequiresPermissions.class).value())
        .containsExactly("drive:access", "drive:quota:manage");
when(properties.isEnabled()).thenReturn(false);
assertThatThrownBy(controller::spaces)
        .extracting("businessCode")
        .isEqualTo(DriveErrorCodes.DRIVE_DISABLED);
verifyNoInteractions(spaceService);
```

- [ ] **Step 2: Run RED**

Run `DriveSpaceServiceTest,DriveSpaceControllerTest`; expect missing services and controller.

- [ ] **Step 3: Implement idempotent space creation and quota methods**

Required service contracts:

```java
public List<DriveSpaceVo> listVisibleSpaces(DriveActor actor);
public DriveSpace requireSpace(Long spaceId);
public DriveSpace requireReadableSpace(Long spaceId, DriveActor actor);
public DriveSpace requireWritableSpace(Long spaceId, DriveActor actor);
public DriveSpaceVo updateQuota(Long spaceId, long quotaBytes, int version, DriveActor actor);
```

`ensureSpace` must call `selectByKey`, `insertIgnore` only when absent, then always `selectByKey` again. Personal space display is exactly `我的文件`, company is `公司公共盘`, and department is `<current deptName>部门盘` with fallback `部门盘`; build the department VO from the current live identity so a later department rename is reflected without changing its stable key. Build `DriveSpaceVo` with `spaceId`, `spaceType`, `spaceName`, `quotaBytes`, `usedBytes`, `version`, `canWrite`, `canManageQuota`; never include owner or department IDs unless the current user needs them for display.

Controller endpoints for this phase:

```java
@RequiresPermissions("drive:access")
@GetMapping("/spaces")
public AjaxResult spaces()

@RequiresPermissions({"drive:access", "drive:quota:manage"})
@PutMapping("/spaces/{spaceId}/quota")
public AjaxResult updateQuota(@PathVariable Long spaceId,
        @Valid @RequestBody DriveQuotaRequest request)
```

Both methods call `featureGuard.requireEnabled()` first. When creating a department space, use the live actor department ID and a stable display name derived from `deptName` (fallback `部门盘`); never use the selected shop header or the cached token department.

Create `DriveQuotaRequest` with positive `quotaBytes` and nonnegative `version` under `domain/dto`.

- [ ] **Step 4: Run GREEN and the phase test set**

```bash
mvn -pl erp-modules/erp-file -am -Dtest=DriveMapperBindingTest,DrivePropertiesTest,LocalDriveStorageProviderTest,DriveAuthorizationServiceTest,DriveFeatureFilterTest,DriveSpaceServiceTest,DriveSpaceControllerTest,CloudDriveSqlSourceTest,LocalSysFileServiceImplTest,ResourcesConfigTest,SysFileControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: zero failures; the legacy controller still requires only `file:upload` and `file:delete`.

- [ ] **Step 5: Commit**

```bash
git add erp-modules/erp-file/src/main/java/com/erp/file/drive/domain/dto/DriveQuotaRequest.java erp-modules/erp-file/src/main/java/com/erp/file/drive/domain/vo/DriveSpaceVo.java erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveQuotaService.java erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveSpaceService.java erp-modules/erp-file/src/main/java/com/erp/file/drive/controller/DriveSpaceController.java erp-modules/erp-file/src/test/java/com/erp/file/drive/service/DriveSpaceServiceTest.java erp-modules/erp-file/src/test/java/com/erp/file/drive/controller/DriveSpaceControllerTest.java
git commit -m "feat(file): expose cloud drive spaces"
```

### Task 6: Phase regression and handoff evidence

**Files:** create `docs/superpowers/verification/2026-07-11-cloud-drive.md`; no new production files.

- [ ] **Step 1: Run the full file-module regression**

```bash
mvn -pl erp-modules/erp-file -am test
```

Expected: `BUILD SUCCESS`, zero failures and zero errors.

- [ ] **Step 2: Check schema copies and forbidden exposure**

```bash
cmp sql/erp_cloud_drive_20260711.sql docker/mysql/db/erp_cloud_drive_20260711.sql
rg -n "private/drive|storageKey" erp-modules/erp-file/src/main/java/com/erp/file/config/ResourcesConfig.java erp-modules/erp-file/src/main/java/com/erp/file/drive/controller
```

Expected: `cmp` exits 0; `ResourcesConfig` does not map the private directory; controller sources do not serialize `storageKey`.

- [ ] **Step 3: Inspect the focused diff**

Run `git diff --check`, `git status --short`, and `git log --oneline --decorate -8`. Expected: no generated JAR/classes/uploads are staged and each task has one focused commit.

- [ ] **Step 4: Record the phase gate**

Create the verification document with a Phase 1 section noting Maven result, SQL double-apply result, public-upload regression, and the commit hashes. Do not begin phase 2 until all four are present.

- [ ] **Step 5: Commit the phase gate**

```bash
git add docs/superpowers/verification/2026-07-11-cloud-drive.md
git commit -m "test: verify cloud drive foundation"
```

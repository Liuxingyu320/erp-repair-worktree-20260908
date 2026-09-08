# ERP Permission And Security Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复单公司多店铺、多权限、多用户场景下已验证的核心安全与越权问题，优先阻断敏感文件公开访问、角色数据范围扩权、部门跨范围写入和管理员语义不一致。

**Architecture:** 第一阶段保留现有 RuoYi/Spring Cloud 架构，不做大规模重构。对高风险点采用“服务端强校验 + 回归测试 + 配置收口”的方式处理：敏感文件走私有下载接口，组织/角色写入前统一校验数据范围，管理员判定统一收敛到安全工具类。

**Tech Stack:** Java 17, Spring Boot, Spring Cloud Gateway, MyBatis XML, JUnit 5, AssertJ, Vue 2, Element UI.

---

## 变更文件总览

- 修改：`erp-gateway/src/main/resources/bootstrap.yml`
  - 从匿名白名单中移除或限制敏感文件路径，避免 `/file/**` 覆盖劳动合同等私有资源。
- 修改：`erp-modules/erp-file/src/main/java/com/erp/file/config/ResourcesConfig.java`
  - 保留普通公开资源映射，避免私有文件目录被静态资源处理器直出。
- 修改：`erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaLaborContractDocumentService.java`
  - 劳动合同、签名、归档文件写入私有目录，不返回 `/file/**` 公开 URL。
- 修改/新增：`erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaLaborContractController.java`
  - 增加鉴权下载入口或复用现有合同详情权限，下载前校验店铺范围和合同访问权限。
- 修改：`erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysRoleServiceImpl.java`
  - `authDataScope` 写入 `sys_role_dept` 前逐个校验 `deptIds`。
- 修改：`erp-modules/erp-system/src/main/java/com/erp/system/controller/SysRoleController.java`
  - `/deptTree/{roleId}` 返回 checkedKeys 前校验角色数据范围。
- 修改：`erp-modules/erp-system/src/main/java/com/erp/system/controller/SysDeptController.java`
  - 新增部门前校验父部门范围，排序前校验所有目标部门。
- 修改：`erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysDeptServiceImpl.java`
  - 将父部门和排序目标校验下沉到 service，防止绕过 controller。
- 修改：`erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysUser.java`
  - 移除 `admin` 角色直接成为超级管理员的隐式路径，或改为调用统一安全开关。
- 修改：`erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysPermissionServiceImpl.java`
  - 生成角色/菜单权限时遵循统一管理员策略。
- 修改测试：
  - `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaLaborContractDocumentServiceTest.java`
  - `erp-gateway/src/test/java/com/erp/gateway/security/IgnoreWhitePropertiesTest.java`
  - `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysRoleServiceImplTest.java`
  - `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysDeptServiceImplTest.java`
  - `erp-common/erp-common-security/src/test/java/com/erp/common/security/utils/SecurityUtilsTest.java`
  - `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysPermissionServiceImplTest.java`

---

## Task 1: P0 私有化劳动合同、签名和印章文件

**目标：** 劳动合同预览、签署归档、签名图片不再通过 `/file/**` 匿名访问；所有下载必须校验登录用户、合同权限和店铺范围。

**Files:**
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaLaborContractDocumentService.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaLaborContractController.java`
- Modify: `erp-gateway/src/main/resources/bootstrap.yml`
- Modify: `erp-modules/erp-file/src/main/java/com/erp/file/config/ResourcesConfig.java`
- Test: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaLaborContractDocumentServiceTest.java`
- Test: `erp-gateway/src/test/java/com/erp/gateway/security/IgnoreWhitePropertiesTest.java`

- [ ] **Step 1: 写失败测试，证明合同服务不应返回公开 `/file/**` 或 `/profile/**` URL**

在 `OaLaborContractDocumentServiceTest` 增加测试：

```java
@Test
@DisplayName("劳动合同归档文件和签名文件不返回公开静态资源URL")
void contractArchiveAndSignatureShouldNotReturnPublicStaticUrls() throws Exception
{
    OaLaborContractDocumentService service = documentService();
    OaLaborContract contract = contract();
    contract.setSignerIp("127.0.0.1");
    contract.setSignerUserAgent("JUnit");
    OaLaborContractSignRequest request = new OaLaborContractSignRequest();
    request.setConfirmed(true);
    request.setSignatureDataUrl(ONE_PIXEL_PNG);

    GeneratedContractFile archive = service.generateSignedArchive(contract,
            template("有社保", "classpath:/templates/labor-contract/social.docx"), sealConfig(), request);

    assertThat(archive.getDocxUrl()).startsWith("/oa/labor-contract/download/");
    assertThat(archive.getSignatureFileUrl()).startsWith("/oa/labor-contract/download/");
    assertThat(archive.getDocxUrl()).doesNotStartWith("/file/");
    assertThat(archive.getSignatureFileUrl()).doesNotStartWith("/file/");
    assertThat(archive.getDocxUrl()).doesNotStartWith("/profile/");
    assertThat(archive.getSignatureFileUrl()).doesNotStartWith("/profile/");
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```bash
./mvnw -pl erp-modules/erp-oa -Dtest=OaLaborContractDocumentServiceTest test
```

Expected: 新增测试失败，当前返回值仍是 `/profile/labor-contract/...` 或文件服务返回的公开 URL。

- [ ] **Step 3: 修改文档生成服务，返回私有下载 URL**

在 `OaLaborContractDocumentService` 中保留本地写文件，但不要把合同、签名、归档上传到公开文件服务。新增私有 URL 生成方法：

```java
private String toPrivateDownloadUrl(OaLaborContract contract, String kind)
{
    return "/oa/labor-contract/download/" + contract.getContractId() + "/" + kind;
}
```

调整调用：

```java
Path output = newOutputPath("archive-" + contract.getContractId() + ".docx");
byte[] bytes = buildContractDocx(contract, template, sealConfig, signatureFile.getPath(), true);
Files.write(output, bytes);
return new GeneratedContractFile(toPrivateDownloadUrl(contract, "archive"), null, sha256(bytes),
        toPrivateDownloadUrl(contract, "signature"));
```

预览文件同理返回：

```java
return new GeneratedContractFile(toPrivateDownloadUrl(contract, "preview"), null, sha256(bytes), null);
```

- [ ] **Step 4: 增加鉴权下载接口**

在 `OaLaborContractController` 增加下载端点，使用现有 `getInfo`/service 范围校验逻辑获取合同后再输出文件：

```java
@RequiresPermissions("oa:laborContract:query")
@GetMapping("/download/{contractId}/{kind}")
public void download(@PathVariable Long contractId, @PathVariable String kind,
        HttpServletRequest request, HttpServletResponse response) throws IOException
{
    OaLaborContract contract = laborContractService.getContractDetail(contractId, resolveShopDeptId(request));
    laborContractService.writeContractFile(contract, kind, response);
}
```

如果当前 `IOaLaborContractService` 没有 `writeContractFile`，新增：

```java
void writeContractFile(OaLaborContract contract, String kind, HttpServletResponse response) throws IOException;
```

`kind` 只允许 `preview`、`archive`、`signature`，其他值抛 `ServiceException("不支持的合同文件类型")`。

- [ ] **Step 5: 收紧网关白名单测试**

更新 `IgnoreWhitePropertiesTest`，不再断言任意 `/file/2026/06/demo.png` 都匿名可读；改为只允许明确的公开资源路径，例如 `/file/public/demo.png`：

```java
assertFalse((Boolean) isWhitelisted.invoke(properties, "/file/labor-contract/2026/06/archive-100.docx"),
        "labor contract files must not be anonymous");
assertTrue((Boolean) isWhitelisted.invoke(properties, "/file/public/demo.png"),
        "explicit public files can remain anonymous");
```

- [ ] **Step 6: 修改 `bootstrap.yml` 白名单**

将：

```yaml
- /file/**
```

改为：

```yaml
- /file/public/**
```

保留：

```yaml
excludes:
  - /file/upload
  - /file/delete
```

- [ ] **Step 7: 跑 OA 与 Gateway 相关测试**

Run:

```bash
./mvnw -pl erp-modules/erp-oa -Dtest=OaLaborContractDocumentServiceTest,OaLaborContractControllerTest test
./mvnw -pl erp-gateway -Dtest=IgnoreWhitePropertiesTest test
```

Expected: 全部 PASS。

- [ ] **Step 8: Commit**

```bash
git add erp-modules/erp-oa erp-gateway/src/main/resources/bootstrap.yml erp-gateway/src/test/java/com/erp/gateway/security/IgnoreWhitePropertiesTest.java
git commit -m "fix(security): require auth for labor contract files"
```

---

## Task 2: P1 修复角色数据范围写入越权

**目标：** `system:role:edit` 用户只能给角色绑定自己可管理范围内的部门/店铺，不能提交任意 `deptIds` 扩权。

**Files:**
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysRoleServiceImpl.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysRoleController.java`
- Test: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysRoleServiceImplTest.java`

- [ ] **Step 1: 写失败测试，验证 `authDataScope` 逐个校验 deptIds**

在 `SysRoleServiceImplTest` 增加：

```java
@Test
@DisplayName("修改角色数据范围前校验所有目标部门范围")
void authDataScopeShouldValidateEachDeptBeforeWriting()
{
    List<String> events = new ArrayList<>();
    SysRoleServiceImpl service = proxiedService(roleScopeMapper(events), userRoleMapper(events),
            userScopeService(events, -1L));
    ReflectionTestUtils.setField(service, "deptService", deptScopeService(events, 202L));

    SysRole role = new SysRole();
    role.setRoleId(20L);
    role.setDeptIds(new Long[] { 101L, 202L });

    assertThatThrownBy(() -> service.authDataScope(role))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("没有权限访问部门数据");

    assertThat(events).contains("dept:101", "dept:202");
    assertThat(events).noneMatch(event -> event.startsWith("write:roleDept"));
}
```

测试 helper：

```java
private static ISysDeptService deptScopeService(List<String> events, Long forbiddenDeptId)
{
    return mapper(ISysDeptService.class, (method, args) -> {
        if ("checkDeptDataScope".equals(method))
        {
            Long deptId = (Long) args[0];
            events.add("dept:" + deptId);
            if (deptId.equals(forbiddenDeptId))
            {
                throw new ServiceException("没有权限访问部门数据！");
            }
            return null;
        }
        throw unexpected(method);
    });
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```bash
./mvnw -pl erp-modules/erp-system -Dtest=SysRoleServiceImplTest#authDataScopeShouldValidateEachDeptBeforeWriting test
```

Expected: FAIL，因为当前 `authDataScope` 没有调用 `deptService.checkDeptDataScope`。

- [ ] **Step 3: 注入 `ISysDeptService` 并实现校验**

在 `SysRoleServiceImpl` 增加字段：

```java
@Autowired
private ISysDeptService deptService;
```

新增方法：

```java
private void checkRoleDeptDataScope(Long[] deptIds)
{
    if (deptIds == null || deptIds.length == 0)
    {
        return;
    }
    for (Long deptId : deptIds)
    {
        if (deptId == null)
        {
            throw new ServiceException("部门ID不能为空");
        }
        deptService.checkDeptDataScope(deptId);
    }
}
```

在 `authDataScope` 写入前调用：

```java
checkRoleDeptDataScope(role.getDeptIds());
roleMapper.updateRole(role);
roleDeptMapper.deleteRoleDeptByRoleId(role.getRoleId());
return insertRoleDept(role);
```

- [ ] **Step 4: 修复 `/deptTree/{roleId}` 越权信息泄露**

在 `SysRoleController.deptTree` 开头增加：

```java
roleService.checkRoleDataScope(roleId);
```

- [ ] **Step 5: 跑角色服务测试**

Run:

```bash
./mvnw -pl erp-modules/erp-system -Dtest=SysRoleServiceImplTest test
```

Expected: PASS。

- [ ] **Step 6: Commit**

```bash
git add erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysRoleServiceImpl.java erp-modules/erp-system/src/main/java/com/erp/system/controller/SysRoleController.java erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysRoleServiceImplTest.java
git commit -m "fix(system): validate role data scope departments"
```

---

## Task 3: P1 修复部门新增和排序越权

**目标：** 新增部门必须校验父部门数据范围；排序必须校验每个目标部门数据范围，并校验 `deptIds` 与 `orderNums` 长度一致。

**Files:**
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysDeptServiceImpl.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysDeptController.java`
- Test: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysDeptServiceImplTest.java`

- [ ] **Step 1: 写失败测试，新增部门前校验父部门范围**

在 `SysDeptServiceImplTest` 增加：

```java
@Test
@DisplayName("新增部门前校验父部门数据范围")
void insertDeptShouldCheckParentDeptScopeBeforeWriting()
{
    List<String> events = new ArrayList<>();
    SysDeptMapper deptMapper = mapper(SysDeptMapper.class, (method, args) -> {
        if ("selectDeptById".equals(method))
        {
            events.add("read-parent:" + args[0]);
            return dept(100L, 0L, "公司", "COMPANY");
        }
        if ("selectDeptList".equals(method))
        {
            SysDept query = (SysDept) args[0];
            events.add("scope:" + query.getDeptId());
            return Collections.emptyList();
        }
        if ("insertDept".equals(method))
        {
            events.add("write:insertDept");
            return 1;
        }
        throw unexpected(method);
    });
    SysDeptServiceImpl service = proxiedDeptService(deptMapper);
    SysDept dept = dept(null, 100L, "越权门店", "STORE");

    assertThatThrownBy(() -> service.insertDept(dept))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("没有权限访问部门数据");
    assertThat(events).contains("scope:100");
    assertThat(events).doesNotContain("write:insertDept");
}
```

- [ ] **Step 2: 写失败测试，排序前逐个校验部门范围**

```java
@Test
@DisplayName("保存部门排序前校验每个目标部门数据范围")
void updateDeptSortShouldCheckEachDeptScopeBeforeWriting()
{
    List<String> events = new ArrayList<>();
    SysDeptMapper deptMapper = mapper(SysDeptMapper.class, (method, args) -> {
        if ("selectDeptList".equals(method))
        {
            SysDept query = (SysDept) args[0];
            events.add("scope:" + query.getDeptId());
            if (Long.valueOf(202L).equals(query.getDeptId()))
            {
                return Collections.emptyList();
            }
            return Collections.singletonList(query);
        }
        if ("updateDeptSort".equals(method))
        {
            events.add("write:sort");
            return 1;
        }
        throw unexpected(method);
    });
    SysDeptServiceImpl service = proxiedDeptService(deptMapper);

    assertThatThrownBy(() -> service.updateDeptSort(new String[] { "101", "202" }, new String[] { "1", "2" }))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("没有权限访问部门数据");
    assertThat(events).containsExactly("scope:101", "scope:202");
    assertThat(events).doesNotContain("write:sort");
}
```

- [ ] **Step 3: 运行测试确认失败**

Run:

```bash
./mvnw -pl erp-modules/erp-system -Dtest=SysDeptServiceImplTest test
```

Expected: 新增测试 FAIL。

- [ ] **Step 4: 在 `insertDept` 中校验父部门范围**

在读取父部门前或读取后调用：

```java
checkDeptDataScope(dept.getParentId());
SysDept info = deptMapper.selectDeptById(dept.getParentId());
```

同时处理父部门不存在：

```java
if (info == null)
{
    throw new ServiceException("上级部门不存在");
}
```

- [ ] **Step 5: 在 `updateDeptSort` 中校验输入和数据范围**

修改为：

```java
if (deptIds == null || orderNums == null || deptIds.length != orderNums.length)
{
    throw new ServiceException("排序参数不正确");
}
for (int i = 0; i < deptIds.length; i++)
{
    Long deptId = Convert.toLong(deptIds[i]);
    if (deptId == null)
    {
        throw new ServiceException("部门ID不能为空");
    }
    checkDeptDataScope(deptId);
}
for (int i = 0; i < deptIds.length; i++)
{
    SysDept dept = new SysDept();
    dept.setDeptId(Convert.toLong(deptIds[i]));
    dept.setOrderNum(Convert.toInt(orderNums[i]));
    deptMapper.updateDeptSort(dept);
}
```

- [ ] **Step 6: 跑部门服务测试**

Run:

```bash
./mvnw -pl erp-modules/erp-system -Dtest=SysDeptServiceImplTest test
```

Expected: PASS。

- [ ] **Step 7: Commit**

```bash
git add erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysDeptServiceImpl.java erp-modules/erp-system/src/main/java/com/erp/system/controller/SysDeptController.java erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysDeptServiceImplTest.java
git commit -m "fix(system): enforce dept scope on create and sort"
```

---

## Task 4: P1 统一管理员语义

**目标：** `admin` 角色、`*:*:*`、`userId == 1` 的超级权限逻辑不再分裂；数据权限、菜单权限、接口鉴权都通过统一策略判断。

**Files:**
- Modify: `erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysUser.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysPermissionServiceImpl.java`
- Modify: `erp-common/erp-common-datascope/src/main/java/com/erp/common/datascope/aspect/DataScopeAspect.java`
- Test: `erp-common/erp-common-security/src/test/java/com/erp/common/security/utils/SecurityUtilsTest.java`
- Test: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysPermissionServiceImplTest.java`

- [ ] **Step 1: 写失败测试，`admin` 角色默认不应直接跳过数据权限**

在合适的测试类增加断言：当 `erp.security.admin-role-bypass-enabled` 未开启时，仅拥有 `admin` roleKey 的用户不应自动得到 `*:*:*`。

```java
@Test
@DisplayName("admin角色默认不自动拥有所有菜单权限")
void adminRoleShouldNotGrantAllPermissionsByDefault()
{
    System.clearProperty("erp.security.admin-role-bypass-enabled");
    SysUser user = new SysUser();
    user.setUserId(200L);
    SysRole adminRole = new SysRole();
    adminRole.setRoleKey("admin");
    user.setRoles(Collections.singletonList(adminRole));

    Set<String> permissions = permissionService.getMenuPermission(user);

    assertThat(permissions).doesNotContain("*:*:*");
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```bash
./mvnw -pl erp-modules/erp-system -Dtest=SysPermissionServiceImplTest test
```

Expected: FAIL，因为当前 `SysUser.isAdmin()` 会把 `admin` roleKey 直接当管理员。

- [ ] **Step 3: 收敛 `SysUser.isAdmin()`**

将 `SysUser.isAdmin()` 改成只保留 legacy userId 判断，或移除该方法在安全判断中的使用：

```java
public boolean isAdmin()
{
    return UserConstants.isAdmin(userId);
}
```

需要保留“admin 角色可绕过”的地方，必须显式使用 `SecurityBypassUtils.adminRoleBypassEnabled()`。

- [ ] **Step 4: 修改权限装载逻辑**

在 `SysPermissionServiceImpl` 中不要直接依赖 `user.isAdmin()` 对 `admin` 角色放权。改为：

```java
if (UserConstants.isAdmin(user.getUserId()))
{
    perms.add(Constants.ALL_PERMISSION);
}
```

如果业务确认需要 `admin` roleKey 放权，则必须同时满足：

```java
SecurityBypassUtils.adminRoleBypassEnabled()
```

- [ ] **Step 5: 修改数据权限切面**

`DataScopeAspect` 当前用 `currentUser.isAdmin()` 判断是否跳过。改为只认统一策略：

```java
if (StringUtils.isNotNull(currentUser) && !UserConstants.isAdmin(currentUser.getUserId()))
{
    ...
}
```

如果要支持开关式 admin role bypass，应通过 `LoginUser` roles + `SecurityBypassUtils.adminRoleBypassEnabled()` 显式判断，不再通过 `SysUser.isAdmin()` 隐式判断。

- [ ] **Step 6: 跑安全和系统权限测试**

Run:

```bash
./mvnw -pl erp-common/erp-common-security -Dtest=SecurityUtilsTest test
./mvnw -pl erp-modules/erp-system -Dtest=SysPermissionServiceImplTest,SysRoleServiceImplTest,SysDeptServiceImplTest test
```

Expected: PASS。

- [ ] **Step 7: Commit**

```bash
git add erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysUser.java erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysPermissionServiceImpl.java erp-common/erp-common-datascope/src/main/java/com/erp/common/datascope/aspect/DataScopeAspect.java erp-common/erp-common-security/src/test/java/com/erp/common/security/utils/SecurityUtilsTest.java erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysPermissionServiceImplTest.java
git commit -m "fix(security): unify administrator bypass semantics"
```

---

## Task 5: P2 生产配置收口

**目标：** 防止 Druid、MinIO、Springdoc、弱密码策略在生产继续裸露或误配。

**Files:**
- Modify: `erp-modules/*/src/main/resources/bootstrap.yml`
- Modify: `erp-modules/erp-file/src/main/resources/application-dev.yml`
- Modify: `erp-common/erp-common-core/src/main/java/com/erp/common/core/constant/UserConstants.java`
- Modify: `erp-auth/src/main/java/com/erp/auth/service/SysLoginService.java`
- Modify: `erp-ui/src/utils/passwordRule.js`
- Modify: `docs/production-security-checklist.md`

- [ ] **Step 1: Druid 监控凭据改为环境变量**

将各模块配置中的：

```yaml
loginUsername: erp
loginPassword: 123456
```

改为：

```yaml
loginUsername: ${DRUID_MONITOR_USERNAME:}
loginPassword: ${DRUID_MONITOR_PASSWORD:}
```

生产部署要求设置强密码；如果为空则通过生产 checklist 禁止上线。

- [ ] **Step 2: 生产关闭 Springdoc API 文档**

在生产配置或 Nacos 配置中设置：

```yaml
springdoc:
  api-docs:
    enabled: false
```

如果需要内网调试，必须通过网关认证或内网 IP 白名单访问。

- [ ] **Step 3: 密码最小长度提升到 8**

修改：

```java
public static final int PASSWORD_MIN_LENGTH = 8;
```

同步修改错误提示：

```java
throw new ServiceException("密码长度必须在8到20个字符之间");
```

同步前端 `passwordRule.js` 的 minLength。

- [ ] **Step 4: MinIO 默认密钥仅保留本地示例，不进入生产**

`application-dev.yml` 可保留本地示例，但 `docs/production-security-checklist.md` 必须明确：

```markdown
- [ ] MinIO access key and secret key are provided by secret store and are not minioadmin/minioadmin.
```

- [ ] **Step 5: 运行认证与前端规则测试**

Run:

```bash
./mvnw -pl erp-auth -Dtest=SysLoginServiceTest test
cd erp-ui && npm test -- --runInBand
```

Expected: 密码策略相关测试 PASS；如果 `SysLoginServiceTest` 不存在，先创建覆盖 7 位密码被拒绝、8 位密码进入后续认证流程的单元测试。

- [ ] **Step 6: Commit**

```bash
git add erp-modules erp-common erp-auth erp-ui docs/production-security-checklist.md
git commit -m "chore(security): harden production defaults"
```

---

## 总体验证

- [ ] **Step 1: 后端关键模块测试**

Run:

```bash
./mvnw -pl erp-common/erp-common-security,erp-common/erp-common-datascope,erp-gateway,erp-modules/erp-system,erp-modules/erp-oa -DskipITs test
```

Expected: PASS。

- [ ] **Step 2: 前端权限相关测试**

Run:

```bash
cd erp-ui && npm test -- --runInBand
```

Expected: PASS。

- [ ] **Step 3: 手工验收清单**

```text
1. 未登录访问 /file/labor-contract/... 返回 401/403 或 404。
2. 登录但无合同所在店铺权限，访问 /oa/labor-contract/download/{id}/archive 返回 403 或业务错误。
3. 有权限用户可下载自己的合同归档文件。
4. 非管理员修改角色数据范围时，提交未授权 deptId 被拒绝。
5. 非管理员新增部门到未授权父部门被拒绝。
6. 非管理员排序未授权部门被拒绝。
7. 默认未开启 bypass 时，admin roleKey 不再隐式拥有所有数据权限。
8. 生产配置中 Druid、MinIO、JWT、Springdoc 均符合 checklist。
```

---

## 实施优先级

1. **当天必须先做：** Task 1，阻断敏感合同和签名文件公开访问。
2. **随后做：** Task 2 和 Task 3，修复多店铺权限越权写入。
3. **再做：** Task 4，统一管理员语义，避免后续继续出现绕过路径。
4. **最后做：** Task 5，完成生产配置和密码策略收口。

## 风险说明

- Task 1 可能影响现有前端合同预览/下载链接，需要同步更新前端调用新下载接口。
- Task 4 可能改变当前 `admin` 角色用户的实际可见菜单和数据范围，执行前要确认是否仍需要保留某个真正的超级管理员账号。
- Task 5 提升密码最小长度后，不应强制老用户立即失效；建议在修改密码、重置密码、注册时执行新策略。


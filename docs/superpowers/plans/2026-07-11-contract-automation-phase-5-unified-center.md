# 合同自动化阶段5：统一合同中心 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让唯一HR和员工分别从一个入口查询新签约包与旧劳动合同，同时保持旧表、旧文件和旧验真接口不变。

**Architecture:** 新增只读统一投影和路由服务，以 `SIGN_PACKAGE`、`LEGACY_LABOR_CONTRACT` 区分来源。列表通过只读 `UNION ALL` 映射公共字段，详情、下载和验真按来源委托现有服务；不复制、不改写旧合同。电脑端整合任务、合同档案、模板与方案、自动化设置四个页签，员工移动端整合待处理和历史合同。

**Tech Stack:** Java 17, Spring Boot 4, MyBatis XML, MySQL 8, Vue 2, Element UI, JUnit 5, Node source tests.

---

## Task 1: 定义统一合同投影与只读查询

**Files:**
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/constant/OaContractSourceType.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaUnifiedContractQuery.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/OaUnifiedContractRecord.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaUnifiedContractMapper.java`
- Create: `erp-modules/erp-oa/src/main/resources/mapper/oa/OaUnifiedContractMapper.xml`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/mapper/OaUnifiedContractMapperSourceTest.java`
- Modify: `erp-modules/erp-oa/src/test/java/com/erp/oa/mapper/OaMapperBindingTest.java`

- [ ] 公共记录字段固定为：

```text
sourceType
recordId
businessNo
employeeId
employeeName
shopDeptId
shopDeptName
scenario
contractType
status
contractStartDate
contractEndDate
sentTime
signedTime
fileCount
certificateNo
verificationCapability
legacy
sortTime
```

- [ ] 来源枚举只允许 `SIGN_PACKAGE` 和 `LEGACY_LABOR_CONTRACT`；验真能力只允许 `FULL` 和 `LEGACY`。
- [ ] Mapper使用一个外层查询包裹 `UNION ALL`：

```sql
SELECT unified.*
FROM (
  SELECT 'SIGN_PACKAGE' AS source_type,
         p.package_id AS record_id,
         p.package_no AS business_no,
         p.employee_id,
         p.employee_name_snapshot AS employee_name,
         p.shop_dept_id,
         p.shop_dept_name,
         p.scenario,
         p.employment_type AS contract_type,
         p.status,
         p.contract_start_date,
         p.contract_end_date,
         p.sent_time,
         p.signed_time,
         (SELECT COUNT(*) FROM oa_sign_package_document d WHERE d.package_id = p.package_id) AS file_count,
         NULL AS certificate_no,
         'FULL' AS verification_capability,
         0 AS legacy,
         COALESCE(p.signed_time, p.sent_time, p.create_time) AS sort_time
  FROM oa_sign_package p
  UNION ALL
  SELECT 'LEGACY_LABOR_CONTRACT',
         c.contract_id,
         c.contract_no,
         c.employee_id,
         c.employee_name,
         c.shop_dept_id,
         c.shop_dept_name,
         'ONBOARD',
         NULL,
         c.status,
         c.contract_start_date,
         c.contract_end_date,
         c.sent_time,
         c.signed_time,
         CASE WHEN COALESCE(c.archive_file_url, c.pdf_file_url, c.preview_file_url) IS NULL THEN 0 ELSE 1 END,
         c.ca_cert_no,
         'LEGACY',
         1,
         COALESCE(c.signed_time, c.sent_time, c.create_time)
  FROM oa_labor_contract c
) unified
```

- [ ] 在外层应用employeeId、shopDeptIds、sourceType、scenario、status、日期和关键字过滤；排序固定为 `sort_time DESC, source_type, record_id DESC`。
- [ ] 任何统一查询都不得更新旧表或新表。Mapper接口只声明select方法。
- [ ] 使用现有PageHelper分页；测试断言外层过滤和排序存在，不能分别分页后在Java内拼接。
- [ ] 运行 `mvn -pl erp-modules/erp-oa -am -Dtest=OaUnifiedContractMapperSourceTest,OaMapperBindingTest test` 后提交 `feat: query unified contract archive projection`。

## Task 2: 实现按来源路由的详情、文件和验真

**Files:**
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/OaUnifiedContractDetail.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/IOaUnifiedContractService.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaUnifiedContractServiceImpl.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaContractCenterController.java`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaUnifiedContractServiceImplTest.java`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/controller/OaContractCenterControllerTest.java`

- [ ] HR接口固定为：

```text
GET /contractCenter/archive/list
GET /contractCenter/archive/{sourceType}/{recordId}
GET /contractCenter/archive/{sourceType}/{recordId}/verify
GET /contractCenter/archive/{sourceType}/{recordId}/files/{fileKind}
```

- [ ] 列表/详情/文件/验真分别要求 `oa:contractCenter:list/query/file/verify`，并使用当前门店或授权门店范围。
- [ ] `SIGN_PACKAGE` 委托 `IOaSignPackageService` 和阶段1验真；`LEGACY_LABOR_CONTRACT` 委托 `IOaLaborContractService` 原详情、下载和验真。
- [ ] 旧合同返回业务状态 `LEGACY_LIMITED` 或原有验真结果，不把缺少新证据表记录误判为篡改。
- [ ] fileKind白名单：

```text
REVIEW_PDF
SIGNED_PDF
CERTIFICATE
LEGACY_PREVIEW
LEGACY_ARCHIVE
LEGACY_CERTIFICATE
```

- [ ] 来源与fileKind不匹配返回400；文件不存在返回业务错误；所有文件仍由原服务完成路径校验和范围鉴权。
- [ ] 默认详情不返回原始hash；`includeTechnical=true` 仍要求 `oa:signTask:technicalEvidence`。
- [ ] 运行聚焦测试后提交 `feat: route unified contract details safely`。

## Task 3: 统一员工“我的签约”和历史合同

**Files:**
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaContractCenterController.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/IOaUnifiedContractService.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaUnifiedContractServiceImpl.java`
- Modify: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaUnifiedContractServiceImplTest.java`
- Modify: `erp-modules/erp-oa/src/test/java/com/erp/oa/controller/OaContractCenterControllerTest.java`

- [ ] 员工接口固定为：

```text
GET /contractCenter/mobile/list
GET /contractCenter/mobile/{sourceType}/{recordId}
GET /contractCenter/mobile/{sourceType}/{recordId}/files/{fileKind}
```

- [ ] 使用 `@RequiresLogin`，employeeId永远取当前登录用户；忽略/拒绝客户端employeeId。
- [ ] 列表分类映射为 `PENDING`、`VIEWED`、`SIGNED`、`REFUSED`、`EXPIRED`、`HISTORY`。
- [ ] 旧 `pending_sign` 合同必须进入PENDING，不能因为新签约包入口上线而隐藏。
- [ ] 新签约包的签署仍走阶段1接口；旧合同的签署仍走旧接口。统一详情只提供明确的 `actionType` 和来源路由，不复制签署逻辑。
- [ ] 他人recordId、伪造sourceType和跨来源fileKind都返回无权限/不存在，不能泄露是否存在。
- [ ] 运行聚焦测试后提交 `feat: unify employee signing archive entries`。

## Task 4: 整合电脑端四页签合同签约中心

**Files:**
- Create: `erp-ui/src/api/oa/contractCenter.js`
- Create: `erp-ui/src/views/oa/contractCenter/index.vue`
- Create: `erp-ui/src/views/oa/contractCenter/ContractArchiveTab.vue`
- Create: `erp-ui/src/views/oa/contractCenter/ContractArchiveDetailDrawer.vue`
- Create: `erp-ui/src/views/oa/contractCenter/ContractVerificationPanel.vue`
- Create: `erp-ui/src/views/oa/contractCenter/ContractTemplatePlanTab.vue`
- Modify: `erp-ui/src/views/oa/signTask/index.vue`
- Modify: `erp-ui/src/views/oa/signPackage/index.vue`
- Modify: `erp-ui/test/signPackageModule.test.js`
- Create: `erp-ui/test/unifiedContractCenter.test.js`
- Create: `sql/erp_oa_contract_center_menu_20260711.sql`
- Create: `docker/mysql/db/erp_oa_contract_center_menu_20260711.sql`

- [ ] 主入口四个页签固定为“签约任务、合同档案、模板与方案、自动化设置”。
- [ ] 签约任务复用阶段2列表/抽屉；自动化设置复用阶段4组件；从现有 `signPackage/index.vue` 提取模板/方案面板，不复制两套维护逻辑。
- [ ] 合同档案默认显示全部来源，可按新签约包/历史劳动合同过滤；每行明确显示“新流程”或“历史合同”。
- [ ] 验真面板只显示业务结论，技术证据按权限折叠；历史合同显示“历史合同，仅支持旧版验真”。
- [ ] 菜单脚本把原“员工签约”和“劳动合同”入口指向统一中心的不同query参数；保留旧组件和API至少一个发布周期，不立即删除。
- [ ] 单HR权限只授予同一角色；模板/自动化配置仍在同一HR中心，不引入第二审核角色。
- [ ] 运行：

```bash
cmp sql/erp_oa_contract_center_menu_20260711.sql docker/mysql/db/erp_oa_contract_center_menu_20260711.sql
cd erp-ui
node test/unifiedContractCenter.test.js
node test/signPackageModule.test.js
node test/laborContractModule.test.js
npm test
npm run build:prod
```

- [ ] 提交 `feat: consolidate hr contract signing center`。

## Task 5: 整合移动端“我的签约”

**Files:**
- Modify: `erp-ui/src/api/oa/contractCenter.js`
- Create: `erp-ui/src/views/mobile/contractCenter/index.vue`
- Modify: `erp-ui/src/views/mobile/signPackage/index.vue`
- Modify: `erp-ui/src/views/mobile/contract/index.vue`
- Modify: `erp-ui/src/views/mobile/mobileRouteDefinitions.js`
- Modify: `erp-ui/src/views/mobile/mobileNavigation.js`
- Create: `erp-ui/test/mobileUnifiedContractCenter.test.js`

- [ ] 单一页面显示待处理、已查看、已签署、已拒签、已过期和历史合同。
- [ ] 行上显示来源和状态；员工无需理解“签约包/旧劳动合同”技术名，但详情保留“历史合同”提示。
- [ ] 待签新包进入现有新签流程；待签旧合同进入原旧签流程；签署完成后刷新统一列表和todo。
- [ ] 推送routeType统一解析到新页面，并携带sourceType/recordId；旧版本推送只有packageId时兼容映射为SIGN_PACKAGE。
- [ ] 旧 `/mobile/sign-package` 和 `/mobile/contract` 路由保留重定向一个版本，不能出现空白页。
- [ ] 运行移动聚焦测试、全量Node测试和Capacitor校验后提交 `feat: unify mobile employee contracts`。

## Task 6: 查询性能、权限和兼容验证

- [ ] 在脱敏测试库各准备至少10,000条新包和10,000条旧合同，执行列表SQL的 `EXPLAIN ANALYZE`。
- [ ] 确认使用现有索引 `oa_sign_package(employee_id/status)`、`oa_sign_package(shop_dept_id/status)`、`oa_labor_contract(employee_id/status)`、`oa_labor_contract(shop_dept_id/status)`；若新包缺索引，只在本阶段菜单脚本外另建同名双份索引迁移并加源测试。
- [ ] 常用第一页查询P95目标小于500ms；关键字模糊查询明确限制日期范围或员工条件，禁止无界导出。
- [ ] 测试普通HR当前门店、跨店授权HR、无权限用户、员工本人、其他员工、技术证据权限六类主体。
- [ ] 对旧表和旧文件执行前后校验：

```sql
SELECT COUNT(*), SUM(CRC32(CONCAT_WS('|', contract_id, status, archive_file_hash, certificate_file_hash)))
FROM oa_labor_contract;
```

上线前后结果必须一致（正常业务新增/签署需从对比窗口排除）。
- [ ] 运行：

```bash
mvn -pl erp-modules/erp-oa -am test
cd erp-ui
npm test
npm run build:prod
npm run app:verify
```

- [ ] 回滚时只恢复旧菜单路由；统一查询是只读层，无需搬回数据。旧入口、旧API、旧文件必须立即可继续使用。

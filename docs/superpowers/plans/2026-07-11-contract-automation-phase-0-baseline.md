# 合同自动化阶段0：基线与数据治理 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 恢复完整测试门禁，统一员工合同/社保字段的机器值，迁移存量档案，并输出自动签约前的数据、模板和旧待签风险清单。

**Architecture:** 前后端共享固定代码值，页面只负责把代码映射成中文；系统模块在所有员工档案写入口统一归一化并拒绝未知值；OA只消费标准代码。数据库迁移先备份再更新，审计SQL保持只读。此阶段不改变签约状态、不生成文件、不发送合同。

**Tech Stack:** Java 17, Spring Boot 4, MyBatis XML, MySQL 8, Vue 2, Element UI, JUnit 5, Node source tests.

---

## Task 1: 锁定测试基线并移除重复副本

**Files:**
- Delete: `erp-ui/test/dockerScripts.test 2.js`
- Delete: `erp-ui/test/laborContractModule.test 2.js`
- Delete: `erp-ui/test/logoutInvalidToken.test 2.js`
- Delete: `erp-ui/test/mobileFeatureMapper.test 2.js`
- Delete: `erp-ui/test/p0UxHardening.test 2.js`
- Delete: `erp-ui/test/userShopScopeUx.test 2.js`
- Delete: `sql/erp_oa_labor_contract_20260612 2.sql`
- Delete: `sql/security_admin_privilege_audit_20260613 2.sql`
- Delete: `docker/mysql/db/erp_oa_labor_contract_20260612 2.sql`
- Delete: `docker/mysql/db/security_admin_privilege_audit_20260613 2.sql`
- Modify: `erp-ui/scripts/run-node-tests.cjs`
- Create: `erp-ui/test/contractAutomationBaseline.test.js`

- [ ] 运行 `git diff --no-index -- "erp-ui/test/dockerScripts.test.js" "erp-ui/test/dockerScripts.test 2.js"`，并对其余五组重复文件逐一比较；若任一副本包含主文件没有的有效断言，先把断言用 `apply_patch` 合并到主文件。
- [ ] 用 `cmp` 核对四个带 ` 2.sql` 的SQL副本与无后缀主文件字节一致；当前四组应全部返回0，随后删除副本，避免部署脚本选择错误文件。
- [ ] 写失败测试，要求测试运行器拒绝所有 `/ [2-9][0-9]*\.js$/` 副本，而不只拒绝 ` 2.js`。

```js
const duplicatePattern = / [2-9][0-9]*\.js$/
assert(!fs.readdirSync(testDir).some(file => duplicatePattern.test(file)))
```

- [ ] 运行 `cd erp-ui && node test/contractAutomationBaseline.test.js`，确认因现有副本而失败。
- [ ] 合并必要断言后删除六个副本；把 `run-node-tests.cjs` 的 `/ 2\.js$/` 改为上面的通用规则。
- [ ] 运行 `cd erp-ui && npm test`，记录真实失败项；本任务只修复重复副本导致的门禁失败，不顺手更改无关业务。
- [ ] 提交：

```bash
git add -- erp-ui/scripts/run-node-tests.cjs erp-ui/test/contractAutomationBaseline.test.js \
  "erp-ui/test/dockerScripts.test 2.js" "erp-ui/test/laborContractModule.test 2.js" \
  "erp-ui/test/logoutInvalidToken.test 2.js" "erp-ui/test/mobileFeatureMapper.test 2.js" \
  "erp-ui/test/p0UxHardening.test 2.js" "erp-ui/test/userShopScopeUx.test 2.js" \
  "sql/erp_oa_labor_contract_20260612 2.sql" "sql/security_admin_privilege_audit_20260613 2.sql" \
  "docker/mysql/db/erp_oa_labor_contract_20260612 2.sql" \
  "docker/mysql/db/security_admin_privilege_audit_20260613 2.sql"
git commit -m "test: restore complete frontend test gate"
```

## Task 2: 建立唯一的签约档案字典

**Files:**
- Create: `erp-api/erp-api-system/src/main/java/com/erp/system/api/constant/SigningProfileCodes.java`
- Create: `erp-ui/src/views/hr/components/signingProfileOptions.js`
- Modify: `erp-ui/src/views/hr/components/HrProfileEditDrawer.vue`
- Modify: `erp-ui/src/views/hr/components/HrProfileDetailDrawer.vue`
- Modify: `erp-ui/src/views/hr/components/hrFieldConfig.js`
- Modify: `erp-ui/src/views/system/user/index.vue`
- Modify: `erp-ui/src/views/system/user/view.vue`
- Create: `erp-ui/test/signingProfileDictionary.test.js`
- Create: `erp-modules/erp-system/src/test/java/com/erp/system/api/domain/SigningProfileCodesTest.java`

- [ ] 先写Java失败测试，断言合同、期限和社保代码集合精确如下，未知值不能被允许。

```java
assertThat(SigningProfileCodes.CONTRACT_TYPES)
    .containsExactlyInAnyOrder("LABOR_CONTRACT", "SERVICE_CONTRACT",
        "INTERNSHIP_AGREEMENT", "OUTSOURCING_CONTRACT");
assertThat(SigningProfileCodes.CONTRACT_TERMS)
    .containsExactlyInAnyOrder("FIXED_TERM", "OPEN_ENDED");
assertThat(SigningProfileCodes.SOCIAL_TYPES)
    .containsExactlyInAnyOrder("SOCIAL_INSURED", "SOCIAL_UNINSURED",
        "DISPATCHED", "PENDING_CONFIRMATION");
```

- [ ] 运行 `mvn -pl erp-modules/erp-system -am -Dtest=SigningProfileCodesTest test`，确认类不存在导致失败。
- [ ] 实现不可实例化的常量类，公开 `Set<String>` 并提供 `isKnownContractType`、`isKnownContractTerm`、`isKnownSocialType`。
- [ ] 写前端失败测试，要求HR编辑、系统用户编辑和详情页都从同一个选项文件导入，不再各自持有中文数组。
- [ ] 建立前端选项：

```js
export const CONTRACT_TYPE_OPTIONS = Object.freeze([
  { value: "LABOR_CONTRACT", label: "劳动合同" },
  { value: "SERVICE_CONTRACT", label: "劳务协议" },
  { value: "INTERNSHIP_AGREEMENT", label: "实习协议" },
  { value: "OUTSOURCING_CONTRACT", label: "外包合同" }
])

export const CONTRACT_TERM_OPTIONS = Object.freeze([
  { value: "FIXED_TERM", label: "固定期限" },
  { value: "OPEN_ENDED", label: "无固定期限" }
])

export const SOCIAL_TYPE_OPTIONS = Object.freeze([
  { value: "SOCIAL_INSURED", label: "缴纳社保" },
  { value: "SOCIAL_UNINSURED", label: "无需缴纳" },
  { value: "DISPATCHED", label: "劳务派遣" },
  { value: "PENDING_CONFIRMATION", label: "待确认" }
])
```

- [ ] 同文件实现 `signingProfileLabel(options, value)`；详情页和列表必须显示中文，接口仍提交代码。
- [ ] 运行：

```bash
mvn -pl erp-modules/erp-system -am -Dtest=SigningProfileCodesTest test
cd erp-ui
node test/signingProfileDictionary.test.js
node test/systemManagementUx.test.js
node test/hrWorkbenchUx.test.js
```

- [ ] 提交：

```bash
git add -- erp-api/erp-api-system/src/main/java/com/erp/system/api/constant/SigningProfileCodes.java \
  erp-modules/erp-system/src/test/java/com/erp/system/api/domain/SigningProfileCodesTest.java \
  erp-ui/src/views/hr/components/signingProfileOptions.js \
  erp-ui/src/views/hr/components/HrProfileEditDrawer.vue \
  erp-ui/src/views/hr/components/HrProfileDetailDrawer.vue \
  erp-ui/src/views/hr/components/hrFieldConfig.js \
  erp-ui/src/views/system/user/index.vue erp-ui/src/views/system/user/view.vue \
  erp-ui/test/signingProfileDictionary.test.js
git commit -m "feat: centralize signing profile dictionaries"
```

## Task 3: 在所有档案写入口归一化并校验

**Files:**
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/service/SigningProfileNormalizer.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysUserServiceImpl.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysUserProfileCompletionServiceImpl.java`
- Create: `erp-modules/erp-system/src/test/java/com/erp/system/service/SigningProfileNormalizerTest.java`
- Modify: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysUserServiceImplTest.java`
- Modify: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysUserProfileCompletionServiceImplTest.java`

- [ ] 写参数化失败测试，固定旧值到新代码的映射：

| 旧值 | 新 `contractType` | 新 `contractTerm` |
| --- | --- | --- |
| 固定期限劳动合同、劳动合同 | `LABOR_CONTRACT` | `FIXED_TERM`（原期限为空时） |
| 无固定期限劳动合同 | `LABOR_CONTRACT` | `OPEN_ENDED`（原期限为空时） |
| 劳务协议、劳务合同 | `SERVICE_CONTRACT` | 保留 |
| 实习协议 | `INTERNSHIP_AGREEMENT` | 保留 |
| 外包合同 | `OUTSOURCING_CONTRACT` | 保留 |

| 旧值 | 新 `socialType` |
| --- | --- |
| 本地社保、异地社保、有社保 | `SOCIAL_INSURED` |
| 无需缴纳、无社保 | `SOCIAL_UNINSURED` |
| 劳务派遣 | `DISPATCHED` |
| 待确认 | `PENDING_CONFIRMATION` |

- [ ] 断言 `本地社保/异地社保` 归一化时不覆盖 `socialSecurityLocation`。
- [ ] 断言空值保持空；非空未知值抛出 `ServiceException("合同类型不受支持: " + value)` 或对应社保错误。
- [ ] 在 `SysUserServiceImpl.updateHrEmployeeProfile`、`SysUserProfileCompletionServiceImpl` 和 `SysUserServiceImpl.importUser` 落库前调用同一个normalizer，禁止控制器各自实现映射。
- [ ] 运行：

```bash
mvn -pl erp-modules/erp-system -am \
  -Dtest=SigningProfileNormalizerTest,SysUserServiceImplTest,SysUserProfileCompletionServiceImplTest test
```

- [ ] 提交：

```bash
git add -- erp-modules/erp-system/src/main/java/com/erp/system/service/SigningProfileNormalizer.java \
  erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysUserServiceImpl.java \
  erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysUserProfileCompletionServiceImpl.java \
  erp-modules/erp-system/src/test/java/com/erp/system/service/SigningProfileNormalizerTest.java \
  erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysUserServiceImplTest.java \
  erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysUserProfileCompletionServiceImplTest.java
git commit -m "fix: normalize signing fields at profile write boundary"
```

## Task 4: 迁移存量档案并保留可恢复快照

**Files:**
- Create: `sql/erp_sign_profile_dictionary_20260711.sql`
- Create: `docker/mysql/db/erp_sign_profile_dictionary_20260711.sql`
- Create: `sql/erp_sign_profile_readiness_audit_20260711.sql`
- Create: `erp-modules/erp-system/src/test/java/com/erp/system/mapper/SigningProfileDictionaryMigrationTest.java`

- [ ] 写失败的源文件测试，要求迁移脚本具有幂等备份表、已知值映射、未知值保护、两份脚本字节一致。
- [ ] 迁移脚本先创建只写一次的快照：

```sql
CREATE TABLE IF NOT EXISTS bak_sys_user_profile_signing_20260711
LIKE sys_user_profile;

INSERT INTO bak_sys_user_profile_signing_20260711
SELECT p.*
FROM sys_user_profile p
LEFT JOIN bak_sys_user_profile_signing_20260711 b ON b.user_id = p.user_id
WHERE b.user_id IS NULL;
```

- [ ] 在更新前执行未知值阻断查询；结果非0时停止部署并人工补映射：

```sql
SELECT user_id, contract_type, social_type
FROM sys_user_profile
WHERE (contract_type IS NOT NULL AND contract_type <> ''
       AND contract_type NOT IN ('固定期限劳动合同','无固定期限劳动合同','劳动合同','劳务协议','劳务合同',
                                 '实习协议','外包合同','LABOR_CONTRACT','SERVICE_CONTRACT',
                                 'INTERNSHIP_AGREEMENT','OUTSOURCING_CONTRACT'))
   OR (social_type IS NOT NULL AND social_type <> ''
       AND social_type NOT IN ('本地社保','异地社保','有社保','无需缴纳','无社保','劳务派遣','待确认',
                               'SOCIAL_INSURED','SOCIAL_UNINSURED','DISPATCHED','PENDING_CONFIRMATION'));
```

- [ ] 使用受限 `CASE` 更新，且只改已知值；劳动合同期限仅在为空时补齐。
- [ ] 审计SQL按门店输出：员工总数、手机号/证件/地址/岗位/职级/法律主体/合同日期/合同类型/社保类型缺失数、未知值数、旧待签合同数。
- [ ] 执行：

```bash
cmp sql/erp_sign_profile_dictionary_20260711.sql \
  docker/mysql/db/erp_sign_profile_dictionary_20260711.sql
mvn -pl erp-modules/erp-system -am \
  -Dtest=SigningProfileDictionaryMigrationTest test
```

- [ ] 在测试数据库事务中执行两次迁移；第二次影响业务行数为0，备份表行数不增加。
- [ ] 提交：

```bash
git add -- sql/erp_sign_profile_dictionary_20260711.sql \
  sql/erp_sign_profile_readiness_audit_20260711.sql \
  docker/mysql/db/erp_sign_profile_dictionary_20260711.sql \
  erp-modules/erp-system/src/test/java/com/erp/system/mapper/SigningProfileDictionaryMigrationTest.java
git commit -m "feat: migrate signing profile values safely"
```

## Task 5: 候选员工接口只输出标准代码和可解释校验

**Files:**
- Modify: `erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SignCandidateUser.java`
- Create: `erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SignReadinessIssue.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysUserServiceImpl.java`
- Modify: `erp-modules/erp-system/src/main/resources/mapper/system/SysUserMapper.xml`
- Modify: `erp-modules/erp-system/src/test/java/com/erp/system/mapper/SysUserMapperSourceTest.java`
- Create: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SignCandidateReadinessTest.java`

- [ ] 写失败测试，要求候选员工包含 `contractTerm`、`legalEntityId` 和 `readinessIssues`，问题结构只使用稳定代码与中文消息：

```java
public class SignReadinessIssue {
    private String code;
    private String field;
    private String message;
}
```

- [ ] 固定问题代码：`MISSING_PHONE`、`INVALID_ID_CARD`、`MISSING_ADDRESS`、`MISSING_POST`、`MISSING_GRADE`、`MISSING_LEGAL_ENTITY`、`INVALID_CONTRACT_DATES`、`UNSUPPORTED_CONTRACT_TYPE`、`UNSUPPORTED_SOCIAL_TYPE`。
- [ ] Mapper只读取标准化字段；service计算问题清单，禁止OA再次猜测中文旧值。
- [ ] 未知值不能静默归为劳务或无社保，必须返回对应 `UNSUPPORTED_*`。
- [ ] 运行：

```bash
mvn -pl erp-api/erp-api-system -am -DskipTests install
mvn -pl erp-modules/erp-system -am \
  -Dtest=SysUserMapperSourceTest,SignCandidateReadinessTest test
```

- [ ] 提交：

```bash
git add -- erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SignCandidateUser.java \
  erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SignReadinessIssue.java \
  erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysUserServiceImpl.java \
  erp-modules/erp-system/src/main/resources/mapper/system/SysUserMapper.xml \
  erp-modules/erp-system/src/test/java/com/erp/system/mapper/SysUserMapperSourceTest.java \
  erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SignCandidateReadinessTest.java
git commit -m "feat: expose signing candidate readiness"
```

## Task 6: 阶段0回归、数据审计与发布门

- [ ] 运行：

```bash
mvn -pl erp-api/erp-api-system -am -DskipTests install
mvn -pl erp-modules/erp-system -am test
mvn -pl erp-modules/erp-oa -am \
  -Dtest=OaSignBatchServiceImplTest,OaSignPackageServiceImplTest test
cd erp-ui
npm test
npm run build:prod
```

- [ ] 使用只读账号执行 `sql/erp_sign_profile_readiness_audit_20260711.sql`，将结果保存到部署工单，不提交员工个人数据。
- [ ] 人工核对未知合同/社保值为0；模板源文件缺失为0；同员工旧待签重复记录形成明确处理清单。
- [ ] 确认 `sign.automation.global.enabled=false`；此阶段上线不得自动创建或发送合同。
- [ ] 回滚演练：从 `bak_sys_user_profile_signing_20260711` 恢复一名测试员工，确认详情页仍正确显示中文标签。
- [ ] 更新路线图阶段0为完成后提交：

```bash
git add -- docs/superpowers/plans/2026-07-11-unified-contract-signing-roadmap.md
git commit -m "docs: record contract automation baseline gate"
```

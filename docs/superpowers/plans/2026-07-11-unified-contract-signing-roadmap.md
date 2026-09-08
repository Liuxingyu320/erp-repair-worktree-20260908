# 统一合同签约与自动发送总路线图 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不迁移旧劳动合同的前提下，以现有员工签约包为执行内核，建立单HR确认、五类人事场景、不可变签署证据、可靠通知、分阶段自动发送和统一合同查询。

**Architecture:** 新增签约任务编排层，接收业务动作或补偿扫描产生的版本化事件；任务层冻结员工、方案和文件快照，并驱动现有 `oa_sign_package`。先完成字典与测试基线，再依次交付证据链、任务中心、五类场景草稿、入职/续签自动发送、统一查询和运营治理。旧 `oa_labor_contract` 只读接入统一视图，不做物理迁移。

**Tech Stack:** Java 17, Spring Boot 4, Spring Cloud OpenFeign, MyBatis XML, MySQL 8, Apache PDFBox 3.0.7 core, Firebase Admin Java SDK 9.9.0, Vue 2, Element UI, Capacitor 8.4.1, JUnit 5, Node source tests.

---

## 1. 计划集

| 顺序 | 计划 | 可交付结果 | 自动发送 | 状态 |
| --- | --- | --- | --- | --- |
| 0 | [阶段0：基线与数据治理](./2026-07-11-contract-automation-phase-0-baseline.md) | 全量测试门禁恢复；档案字典一致；存量风险清单可导出 | 关闭 | **已完成（2026-07-11）** |
| 1 | [阶段1：文件与证据链](./2026-07-11-contract-automation-phase-1-evidence.md) | 员工阅读PDF、已签PDF、证书和验真全部可追溯 | 关闭 | **已完成（2026-07-11）** |
| 2 | [阶段2：任务中心、单HR确认与通知](./2026-07-11-contract-automation-phase-2-task-center.md) | 单一HR在一个中心补资料、确认、发送、处理异常 | 关闭 | **已完成（2026-07-12）** |
| 3 | [阶段3：五类场景自动草稿](./2026-07-11-contract-automation-phase-3-scenarios.md) | 入职、续签、转正、调岗、离职产生幂等草稿 | 关闭 | **进行中（调岗切片已完成，2026-07-13）** |
| 4 | [阶段4：入职与续签自动发送](./2026-07-11-contract-automation-phase-4-auto-send.md) | 影子判断通过后，标准入职/续签逐门店放量 | 受开关控制 | 待执行 |
| 5 | [阶段5：统一合同中心](./2026-07-11-contract-automation-phase-5-unified-center.md) | 新签约包和旧劳动合同统一查询、下载、验真 | 不变 | 待执行 |
| 6 | [阶段6：报表与持续治理](./2026-07-11-contract-automation-phase-6-operations.md) | 可观测指标、异常队列、抽检、备份恢复演练 | 受开关控制 | 待执行 |

阶段0完成门记录：system模块158个测试、OA签约聚焦20个测试、前端101个测试全部通过，生产构建通过（仅保留既有资源体积告警）。只读审计结果为未知合同值0、未知社保值0、启用模板空来源0、同员工重复旧待签0。迁移已在隔离MySQL库双跑并完成单员工快照恢复演练。当前自动化配置行不存在，按本路线图4.4契约等价于 `false`；阶段4创建显式配置前不会自动发送。

阶段1完成门记录：OA模块及依赖157个测试、前端103个测试全部通过，生产构建通过（仅保留既有资源体积告警）。三类现有Office模板均经真实LibreOffice转换、逐页目视检查并生成独立已签PDF和证据页；业务验真覆盖通过、篡改、缺失和旧版受限四类结论，普通HR界面默认隐藏hash。OA运行镜像已提供LibreOffice Writer/Calc与Noto CJK字体，每次转换使用隔离用户目录。自动发送继续关闭。

阶段2完成门记录见[2026-07-12验证记录](../verification/2026-07-12-contract-automation-phase-2.md)。阶段3的调岗切片已于2026-07-13完成：未来日期禁止确认且不产生业务副作用；当天立即生效；过去日期允许同一HR高风险二次确认并分别保存业务生效日和真实操作时间。system事务冻结前后快照并写既有outbox，OA使用统一 `TRANSFER` 规则生成草稿、`NO_ACTION` 或唯一HR补资料任务。没有新增第二审批人、定时生效队列、延迟消息或重复通知体系。验证结果见[调岗日期验证记录](../verification/2026-07-13-contract-transfer-effective-date.md)。阶段3其余未完成场景不因本切片被标记为完成。

设计依据：[统一合同签约与分阶段自动发送设计](../specs/2026-07-11-unified-contract-signing-automation-design.md)。

## 2. 强制依赖与质量门

```text
阶段0
  └─ 阶段1
      └─ 阶段2
          └─ 阶段3
              ├─ 阶段4
              └─ 阶段5
                  └─ 阶段6
```

- [ ] 不允许把阶段4提前到阶段1之前；没有独立已签PDF和证书，自动发送保持关闭。
- [ ] 每个阶段先运行失败测试，再写最小实现，再运行聚焦测试和模块回归。
- [ ] 每个数据库变更同时维护 `sql/` 和 `docker/mysql/db/` 两份同名脚本，并用 `cmp` 校验一致。
- [ ] 所有状态变更使用“当前状态＋version”条件更新；影响外部动作的请求必须携带 `requestId`。
- [ ] 所有文件先写临时目录，计算hash、写库成功后再原子提升到正式目录。
- [ ] 所有新功能默认关闭；关闭开关不能影响已发送合同继续阅读和签署。
- [ ] 每阶段发布后保留数据库脚本版本、应用提交、方案版本和模板版本的对应记录。

## 3. 分支与提交约定

- [ ] 在执行前运行 `git status --short`，记录用户已有改动；不得清理或覆盖不属于本计划的文件。
- [ ] 使用 `codex/contract-automation-phase-N` 分支或隔离 worktree 执行一个阶段。
- [ ] 每个任务完成聚焦测试后提交，提交范围仅包含该任务列出的文件。
- [ ] 每阶段最后只做验证和文档更新，不把未验证的顺手修改混入提交。

建议提交顺序：

```text
test: lock contract automation baseline
fix: normalize signing profile dictionaries
feat: preserve immutable signing evidence
feat: add signing task state machine
feat: deliver targeted signing notifications
feat: create drafts from hr scenarios
feat: enable shadowed onboarding renewal send
feat: unify contract archive queries
feat: expose signing operations metrics
```

## 4. 跨阶段稳定契约

### 4.1 业务场景

```java
public enum SignScenario {
    ONBOARD,
    REGULARIZE,
    TRANSFER,
    OFFBOARD,
    RENEWAL
}
```

### 4.2 任务状态

```java
public enum SignTaskStatus {
    NEW,
    VALIDATING,
    NEEDS_DATA,
    DRAFT_CREATED,
    WAITING_HR_CONFIRM,
    READY_TO_SEND,
    SENDING,
    PENDING_SIGN,
    VIEWED,
    SIGNED,
    NO_ACTION,
    CANCELLED,
    REFUSED,
    EXPIRED,
    FAILED
}
```

### 4.3 单HR权限

业务权限固定为一组，不设计“专员/负责人”双岗：

```text
oa:signTask:list
oa:signTask:query
oa:signTask:edit
oa:signTask:confirm
oa:signTask:send
oa:signTask:retry
oa:signTask:config
oa:signTask:export
```

同一HR可以补资料、生成草稿、确认并发送；系统只记录一次明确确认。技术证据和任务日志使用 `oa:signTask:technicalEvidence` 单独控制，不参与业务审批。

### 4.4 自动化控制

配置键固定为：

```text
sign.automation.global.enabled
sign.automation.shadow.enabled
sign.automation.onboard.enabled
sign.automation.renewal.enabled
sign.automation.shop.<shopDeptId>.enabled
sign.automation.legalEntity.<legalEntityId>.enabled
```

不存在配置时一律按 `false` 处理。

## 5. 全链路验收场景

- [ ] HR确认入职后只产生一个 `ONBOARD` 任务；资料完整时生成正确A1-A6/B1-B3草稿，资料缺失时给出可理解的字段原因。
- [ ] 转正、调岗和离职始终停在 `WAITING_HR_CONFIRM`，即使全局自动发送开启也不能越过。
- [x] 调岗未来日期在system入口直接拒绝，不更新档案、不写生命周期动作或outbox，也不创建OA任务、草稿或通知；本阶段不保存等待未来自动执行的确认任务。
- [x] 调岗当天日期立即更新档案并冻结前后快照；过去日期由同一个HR完成结构化高风险二次确认，`effective_date` 与 `actual_confirm_time` 分列保存。
- [x] 调岗前端以服务端上海业务日提前提示，后端保持最终权威；覆盖昨天/今天/明天、月末/年末/闰日和前端设备时区不一致。
- [ ] 续签扫描只生成“续签决策待办”；HR确认续签后才创建合同草稿。
- [ ] 员工实际阅读的PDF hash与签署请求携带的hash一致；改动一个字节后验真返回“不一致”。
- [ ] 员工签署后生成独立 `signed_pdf`，其中包含签名/标准签署页；不能继续复用阅读PDF路径。
- [ ] HR默认看到“验真通过/不一致/文件缺失/旧版验真”，不显示原始hash。
- [ ] 相同 `requestId`、相同业务事件版本、并发重复点击均只产生一次任务、一次签署和一次通知。
- [ ] FCM不可用时站内待办仍成功，推送进入重试且不回滚业务事务。
- [ ] 关闭自动发送后，新任务进入HR确认；已经发送的合同继续正常签署。
- [ ] 旧劳动合同不改表、不改文件，仍可从统一中心进入原下载和验真接口。

## 6. 发布顺序

- [ ] 开发环境执行每阶段迁移、聚焦测试和全回归。
- [ ] 测试环境使用脱敏的真实模板做逐页PDF视觉检查，覆盖DOCX、XLSX、签名页和中文字体。
- [ ] 生产先部署兼容数据库变更，再部署后端，最后部署前端和移动端。
- [ ] 阶段2上线时任务记录、通知和自动化总开关均为关闭；先验证人工路径。
- [ ] 阶段4先开启影子模式至少7天；硬指标全部通过后只开放一个低风险门店。
- [ ] 每次扩大范围前导出上一个观察窗口的错误命中、重复任务、验真率、人工换方案和发送成功率。

## 7. 回滚总则

- [ ] 第一动作是关闭 `sign.automation.global.enabled` 和场景开关，不删除任务或合同。
- [ ] 停止事件消费和补偿扫描后，把未发送的 `READY_TO_SEND` 任务条件更新回 `WAITING_HR_CONFIRM`。
- [ ] 已发送、已阅读和已签文件保持只读，绝不执行降级覆盖。
- [ ] 通知重试可暂停，但outbox记录必须保留。
- [ ] 数据库回滚只删除尚未被业务使用的新索引/空表；存在业务记录时采用向前修复。
- [ ] 旧合同查询是只读聚合，关闭统一中心后原入口仍可用。

## 8. 全量验证命令

```bash
mvn -pl erp-api/erp-api-system -am -DskipTests install
mvn -pl erp-modules/erp-system -am test
mvn -pl erp-modules/erp-oa -am test
cd erp-ui
npm test
npm run build:prod
npm run app:sync
npm run app:verify
```

预期：所有命令退出码为0；后端报告0 failures/0 errors；Node汇总0 failed；生产构建无密钥扫描告警；Capacitor原生配置校验通过。

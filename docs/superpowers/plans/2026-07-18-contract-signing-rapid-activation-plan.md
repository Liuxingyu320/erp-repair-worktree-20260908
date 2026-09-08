# 合同签约快速可用实施方案

> 日期：2026-07-18
> 推荐路线：R0 内部人工入职签约试点 → 小范围灰度 → Release A 正式扩量
> 首期目标：在不重跑生产迁移、不开放自动发送的前提下，用一个组织、一个 HR、一个法律主体、一个有效印章和一个入职方案跑通 3 个完整签约样本
> 能力边界：首期属于 ERP 内部电子确认与留证，不等同于第三方 CA/CMS/X.509 数字签名

## 一、方案结论

当前问题不是缺少签约主体功能。代码已经覆盖任务生成、文件生成、员工阅读、手写签名、公司盖章、最终确认、文件哈希和通知；真正阻塞是发布候选没有冻结、最新代码未部署、模板/方案未激活、方案作用域口径冲突，以及移动端和文件存储仍存在运行风险。

本方案不继续扩大功能面，先采用以下固定口径：

1. 首期只开放 `ONBOARD` 入职人工签约，不同时开放转正、调岗、续签、离职。
2. 签约方案采用最新“HR 全局方案库”模型，方案的 `shop_dept_id=0`；任务和签约包仍保存员工真实签约组织。
3. 员工签名和公司印章统一使用 `APPENDED_CONFIRMATION_PAGE`，不再处理不稳定的 PDF 绝对坐标。
4. 正常入口固定为“员工档案 → 批量处理入职合同”；应急新建签约包继续隐藏。
5. 生命周期事件自动建签约任务、自动发送、自动提醒、自动过期、应急建包全部保持关闭。
6. 先跑 3 个受控人工样本，满足验收后再扩组织和方案。
7. 如果业务目标是第三方可信电子签，另立服务商集成项目，不把当前内部签署包装为 CA 电子签。

R0 默认选择最简单的 A4 路线“劳动合同 / 无社保 / 2～4 级”，只绑定劳动合同模板 7；如果试点员工不符合该路线，由 HR 改选一条真实适用路线，但首期仍只能发布一条。

## 二、当前基线

| 事实 | 当前状态 | 本方案处理 |
| --- | --- | --- |
| 当前工作区 | 296 项变更，其中 modified/deleted 161、untracked 135 | 先冻结签约最小发布范围，不直接从当前工作区整体发布 |
| 静态发布门禁 | `bash scripts/verify-contract-signing-release.sh --static` 失败；7 个已改 allowlist 文件不在 release files | 逐项纳入或撤回后，门禁必须归零 |
| 生产版本 | 生产仍运行 2026-07-17 基线；2026-07-18 签约修复包未部署 | 只部署签约最小制品，使用不可变目录和旧版本回退点 |
| 数据库 | 四个 Release A SQL 已在生产执行成功，生产为 MySQL 8.0.24 | 不重复执行四个 SQL，只做只读结构后检和账本补录 |
| 模板/方案 | 本地模板已登记，但方案停用、无正式版本、无实际签约记录 | HR 在 UI 启用审批模板并发布一个唯一匹配全局方案 |
| 方案模型 | 旧运行手册按门店建方案；最新代码使用全局方案库 | 以 `OaSignPlanScope` 的全局模型为唯一口径，修订旧手册 |
| 生命周期自动建任务 | System 每 30 秒投递 HR 签约 Outbox，当前没有默认关闭开关；发布全局方案后可能为非试点组织自动建草稿 | 首期增加服务端总开关并默认关闭，人工发起仍走 OA 正常接口 |
| 移动端 | 签署按钮依赖 PDF iframe `load` 事件 | 改为“有效 PDF 获取成功 + 明确阅读确认”，iframe 渲染不再是唯一阻断条件 |
| 文件存储 | OA 写入 `${file.path}/private/sign-package`；生产证据指向 ECS-host 持久目录，Compose 中 OA 仍缺少 uploadPath 挂载 | R0 先验证 ECS-host 持久目录和权限；Compose 一致性修复放入 P1 |
| UAT | 单元测试/构建有绿色记录，但没有真实浏览器/API UAT 证据 | 先产出 3 个试点样本证据，再补完整 Release A UAT |

当前发布门禁缺失的 7 个文件必须逐项决定“纳入本次发布”或“撤回本次改动”：

- `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaSignBatchPreviewRow.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPackagePreflightValidator.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPlanServiceImpl.java`
- `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignBatchServiceImplTest.java`
- `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignPackagePreflightValidatorTest.java`
- `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignPlanServiceImplTest.java`
- `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrOnboardingLifecycleTest.java`

## 三、首期范围

### 3.1 包含

- 一个低风险签约组织。
- 一个非管理员合同 HR。
- 一个法律主体和一个启用、未过期、文件哈希有效的公司印章。
- 一套已审批入职模板。
- 一个唯一匹配且已发布的全局入职方案；默认采用 A4 路线并只绑定模板 7。
- System 生命周期自动建任务保持关闭，试点仅允许 HR 人工发起。
- 员工端阅读、首次签名、HR 盖章、员工最终确认、下载和验真。
- Web、iOS/Capacitor、Android/Capacitor 至少各一次关键路径验证。
- 发布、灰度、监控和前向回退。

### 3.2 不包含

- 自动发送、自动提醒和自动过期。
- HR 生命周期事件自动建签约任务。
- 应急自由建包和按岗位批量建包。
- 转正、调岗、续签、离职全量开放。
- 历史纸质合同迁移。
- 第三方电子签服务商、实名认证、CA 证书、回调验签和第三方签署证书。
- PDF/Word 最后一页的绝对签名、印章坐标。

## 四、目标业务流程

```text
HR 员工档案选择员工
  → 批量处理入职合同
  → 预览方案与缺失资料
  → 人工确认不存在外部已签合同
  → 创建并发送
  → 员工逐份获取文件并确认阅读
  → 员工手写签名
  → HR 选择法律主体和有效印章并生成最终合同
  → 员工逐份获取最终合同并确认
  → SIGNED
  → 下载最终 PDF / 验证哈希 / 检查审计事件
```

正常状态链固定为：

```text
READY_TO_SEND
  → PENDING_SIGN / PART_VIEWED
  → PENDING_COMPANY
  → PENDING_FINAL_CONFIRM
  → SIGNED
```

出现 `NEEDS_DATA`、`PLAN_NOT_FOUND`、`PLAN_CONFLICT`、文件哈希错误、权限错误或持久化失败时停止该样本，不允许通过管理员账号、应急建包或手工改库绕过。

## 五、实施阶段

### G0：业务口径与发布基线冻结（0.5 天）

#### 目标

形成一个可复现、可部署、可回退的入职人工签约候选版本。

#### 工作项

1. 业务和法务书面确认首期定位为“ERP 内部人工签约试点”，不宣称第三方 CA 电子签。
2. 确认试点组织、合同 HR、法律主体、印章、模板、用工路线和测试员工。
3. 以全局方案模型为准，修订旧的按门店创建方案说明：
   - `docs/runbooks/beijing-sign-template-plan-ui-publish-20260718.md`
   - `docs/releases/20260716-contract-signing-release-scope.md`
   - `scripts/contract-signing-release-20260716.json`
4. 审查上述 7 个门禁缺失文件；需要本次签约功能的纳入 release files，不属于本次范围的撤回或移出 allowlist。
5. 新建独立的 `docs/releases/20260718-contract-signing-pilot-r0.md` 发布记录；R0 通过不代表五场景 Release A 已经 `ready`。
6. 冻结 System JAR、OA JAR、前端静态文件和运行配置哈希，不把工作区其他业务域一同打包。
7. 将生产 MySQL 5.7 pending 项更新为 `not-applicable-version-verified`，记录生产 MySQL 8.0.24 的只读证据。
8. 记录四个生产 SQL 已完成，不再次执行。

#### 验证

```bash
bash scripts/verify-contract-signing-release.sh --static
git diff --check
```

#### 完成门

- 静态发布门禁退出码为 0。
- 候选文件清单、Git 基线和制品哈希可复验。
- 当前冻结源码重新执行后端全量测试、前端全量测试和生产构建，不复用冻结前报告代替。
- 试点范围和法律定位已书面确认。
- 没有计划再次执行已完成的四个生产 SQL。

### G1：四个 P0 稳定性改造（0.5～1 天）

#### G1-0：关闭生命周期自动建任务

**涉及文件：**

- `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrSignEventDispatcher.java`
- `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrSignEventCompensationScanner.java`
- `erp-modules/erp-system/src/main/resources/bootstrap.yml`
- `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrSignEventDispatcherTest.java`
- `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrSignEventCompensationScannerTest.java`
- 新增 Spring 条件装配测试

**修改方案：**

1. 新增服务端总开关 `hr.sign.lifecycle-automation.enabled`，使用 `@ConditionalOnProperty` 控制 Dispatcher 和补偿扫描器，`matchIfMissing=false`。
2. 环境变量映射为 `HR_SIGN_LIFECYCLE_AUTOMATION_ENABLED`，所有环境默认 `false`。
3. 手工历史员工发起继续调用 OA 的正常入职接口，不受该开关影响。
4. 生命周期事务仍可保存不可变 action/outbox 证据；关闭期间 Outbox 不得投递到 OA 自动建任务。
5. 不依赖 `auto_send_condition_json` 作为灰度开关；该字段当前只保存快照，没有实际阻止任务创建。
6. 未来启用前必须先盘点所有 `PENDING/RETRY` Outbox，按组织、场景和发生时间审批处理策略；禁止直接开启后一次性回放全部积压。

**验收：**

- 默认配置和显式 `false` 时，System 不调度生命周期签约事件，也不自动补偿投递。
- HR 在 OA 员工档案手工发起仍能创建入职签约任务。
- 发布全局方案后，非试点组织不会仅因生命周期事件自动生成签约草稿。
- 显式 `true` 的行为由测试覆盖，但首期生产不得开启。

#### G1-1：移动端 PDF 阅读门禁兼容

**涉及文件：**

- `erp-ui/src/views/mobile/signPackage/index.vue`
- `erp-ui/src/utils/signPackageFileGate.js`
- `erp-ui/test/signPackageFileGate.test.js`
- 必要时新增 iOS/Capacitor 回归测试文件

**修改方案：**

1. 保留 PDF MIME、文件头、EOF、版本和哈希校验。
2. 将门禁状态拆为“下载并校验成功”“预览渲染成功”“员工已确认阅读”。
3. 签署必要条件改为：当前版本 PDF 下载并校验成功 + 所有必读文件服务端阅读确认成功；iframe `load` 只反映预览状态，不作为唯一解锁条件。
4. 预览失败时提供重新打开、系统浏览器打开或下载查看的兼容路径。
5. 旧版本文件的加载状态不得解锁新版本；切换签约包或文件版本时必须清空状态。

**验收：**

- iOS/Capacitor 不触发 iframe `load` 时，用户仍可通过“成功获取文件 → 明确确认阅读”完成签署。
- 非 PDF、空文件、旧版本、错误哈希和未确认阅读仍然 fail closed。
- 首签和最终确认均覆盖同一规则。

#### G1-2：签约组织初始化和深链竞态

**涉及文件：**

- `erp-ui/src/views/oa/signTask/index.vue`
- `erp-ui/src/views/oa/signPackage/index.vue`
- `erp-ui/src/components/SignScopeSelector/index.vue` 或实际作用域选择组件
- `erp-ui/test/signTaskCenter.test.js`
- 相应签约包页面测试

**修改方案：**

1. 增加明确的 `scopeReady` 状态。
2. 首次列表、指标和深链详情请求必须等待作用域选择器完成初始化。
3. 作用域切换后取消或忽略旧请求，清空旧详情，再加载新范围。
4. 无组织授权时显示明确阻断信息，不以空条件请求全量数据。

**验收：**

- 新会话首次通过任务/签约包深链进入可以稳定打开目标记录。
- 快速切换组织不会显示上一组织数据。
- 无授权 HR 不能读取任何签约记录。

#### G1-3：生产签约文件持久化核验

**涉及文件/配置：**

- `erp-modules/erp-oa/src/main/resources/bootstrap.yml`
- 生产 Nacos/环境变量中的 `file.path`、`SIGN_PACKAGE_STORAGE_ROOT`

**修改方案：**

1. 只读确认生产当前为 ECS-host/systemd 部署，并核对现有 `/opt/erp-new-data/uploadPath` 是否为受备份的持久目录。
2. 将 `file.path` 指向该持久目录，并显式设置 `SIGN_PACKAGE_STORAGE_ROOT=/opt/erp-new-data/uploadPath/private/sign-package`；验证 OA 运行用户可读写。
3. 模板、预览 PDF、员工签名、最终 PDF、证书必须位于同一持久化策略下。
4. 不允许把签名图片和合同正文写入日志、Git 或普通公开目录。
5. Compose 中 OA 缺少 volume 的问题登记为 P1，不作为当前 ECS-host R0 的上线阻塞。

**验收：**

- 生成一份测试 PDF 后重启 OA，文件仍能下载且 SHA-256 不变。
- 容器/进程重新部署后模板仍可读取。
- 目录不可写、空间不足或哈希不符时发送 fail closed。

### G2：部署和单方案激活（0.5 天）

#### 部署顺序

1. 生产只读后检：数据库版本、菜单 9650、push ledger、开放入职任务唯一约束、法律主体、印章、HR 配置和组织权限。
2. 在新不可变目录先部署冻结的 System JAR，验证生命周期自动建任务开关为 false，且没有调用 OA 创建任务。
3. 再部署 OA JAR，检查健康、关键 class 哈希、LibreOffice、中文字体和签约存储目录。
4. 最后部署前端静态文件，核对 release info；三个制品均保留旧版本回退点。
5. HR 退出并重新登录，验证非管理员权限。
6. HR 在 UI 启用一套已审批模板；员工签名和公司印章使用 `APPENDED_CONFIRMATION_PAGE`。
7. 只发布一个唯一匹配的全局入职方案。方案记录使用全局范围，运行任务仍选择真实签约组织。
8. 先选择资料完整的内部/虚构员工；首期不通过放宽规则来掩盖脏数据。
9. 生命周期自动建任务、自动发送、提醒、过期、应急建包保持关闭。

#### 必备配置清单

- [ ] `hr.sign.lifecycle-automation.enabled=false`，并确认调度器未运行。
- [ ] `sign.hr.user-id` 指向唯一有效的非管理员 HR。
- [ ] HR 拥有试点组织显式授权和 `oa:signTask:send` 等必要权限。
- [ ] 法律主体启用且与试点组织关系明确。
- [ ] 印章启用、未过期、图片存在、哈希一致。
- [ ] 模板文件存在、哈希一致、占位符完整、法务已审批。
- [ ] 一个全局方案为 `ENABLED + PUBLISHED`，同一路由不存在第二个匹配版本。
- [ ] 测试员工姓名、电话、证件、地址、组织、岗位、职级、用工类型、社保类型、合同日期和当前模板所需薪酬字段完整。
- [ ] 文件持久目录可读写、可备份且不公开暴露。

#### 完成门

- 远端只读 postcheck 退出码为 0，归档 `report.json` 且 `ready=true`。
- HR 预览目标员工时结果为“可处理”，无 `PLAN_NOT_FOUND`、`PLAN_CONFLICT` 或无关字段导致的 `NEEDS_DATA`。
- 部署和方案激活窗口内没有产生任何非试点组织的自动签约草稿。
- 不使用管理员账号和应急建包完成验收。

### G3：三个完整样本灰度（0.5～1 天）

#### 样本设计

| 样本 | 终端 | 目的 |
| --- | --- | --- |
| 1 | Web/桌面浏览器 | 验证正常主链路、权限、文件和状态 |
| 2 | iOS/Capacitor | 验证 PDF 打开、阅读确认、手写签名和最终确认 |
| 3 | Android/Capacitor | 验证移动端兼容、重复提交和最终文件下载 |

每个样本必须逐项保存：任务 ID、签约包 ID、方案版本 ID、操作时间、状态事件、原始/最终文件哈希、阅读确认、员工首次签名、HR 主体/印章、员工最终确认和验真结果。证据中不得保存合同正文、身份证号、手机号或签名图片。

#### 通过标准

- 3/3 样本到达 `SIGNED`。
- 3/3 最终文件可下载，根哈希和逐文件哈希验证通过。
- OA 重启后 3/3 文件仍可读取。
- 同一请求重试不重复创建任务、签约包、事件或通知。
- 非管理员 HR 只能处理授权组织；员工只能看到自己的签约包。
- 无错误合同、重复任务、错误印章、跨组织泄露或无法回退的状态。

#### 停止扩量条件

任意出现以下情况立即停止：

- 文件缺失、哈希不一致或重启后丢失。
- iOS/Android 无法完成阅读或签署。
- 同一员工出现两个非终态入职任务。
- 方案匹配多条或匹配到错误用工路线。
- HR/员工越权访问。
- 最终 PDF 没有员工签名、公司主体或应有印章。
- 任务、签约包和前端显示状态不一致。

### G4：试点后优化与正式扩量（2～5 天）

#### G4-1：按方案占位符校验资料

**涉及文件：**

- `erp-modules/erp-oa/src/main/java/com/erp/oa/service/rule/OnboardSignScenarioRule.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPackagePreflightValidator.java`
- `erp-modules/erp-oa/src/test/java/com/erp/oa/service/rule/OnboardSignScenarioRuleTest.java`
- `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignPackagePreflightValidatorTest.java`

**修改方案：**

1. 先用员工的用工类型、社保类型和职级路由定位唯一已发布方案。
2. 再读取该方案版本绑定模板的 `required_placeholders`。
3. 只校验该方案真正需要的地址、合同日期、试用期和薪酬字段。
4. 身份、组织、岗位、路由字段仍作为基础强校验。
5. 复用或抽取现有占位符校验逻辑，避免生命周期规则和发送前预检产生两套口径。

**验收：**

- 不需要薪酬字段的方案不会因 `MISSING_SALARY` 被阻断。
- 需要薪酬/地址/日期的模板仍能准确阻断并返回中文可操作提示。
- 零匹配返回 `PLAN_NOT_FOUND`，多匹配返回 `PLAN_CONFLICT`，不自动猜选。

#### G4-2：修复 UAT 工具和真实证据链

**涉及文件：**

- `scripts/qa/contract-signing-uat.example.json`
- `scripts/qa/` 下签约 UAT 执行器及测试
- 网关/OA 中经评审确认的环境身份只读接口
- `docs/releases/contract-signing-uat-runbook.md`

**修改方案：**

1. 将 UAT API 基址与网关 `/oa/**` 路由统一，不能继续以错误路径产生假失败。
2. 实现或替换环境身份校验，确保 UAT 不会误连生产或错误环境。
3. 产出 preflight、API write、browser、database 和 evidence 五类机器可读证据。
4. 完成权限、重复提交、拒签、过期、文件失败、通知失败等异常场景。

#### G4-3：入口和运营体验

- 在任务中心增加醒目的“发起入职签约”引导，跳转到员工档案选择员工。
- 缺失资料直接显示业务字段名称和维护入口。
- 保持应急建包关闭，不以开放高风险入口解决可发现性问题。
- 入职稳定后，按同一门禁逐步增加其余全局用工路线，再评估转正、调岗、续签和离职。

#### G4-4：自动化和部署一致性

- 为生命周期自动化增加组织 allowlist、启用时间点和历史 Outbox 处理策略；默认仍为关闭。
- 启用前按组织、场景、发生时间盘点积压 Outbox，只回放业务批准的事件。
- 增加产品内“签约就绪检查”，展示 HR、组织、主体、印章、模板、方案、存储和开关状态。
- 给 Docker Compose 的 OA 服务补充 `/home/erp/uploadPath` 持久化挂载，保证开发、灾备与生产目录语义一致。

## 六、验证命令

### 后端专项

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./mvnw -pl erp-modules/erp-system -am \
  -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=HrSignEventDispatcherTest,HrSignEventCompensationScannerTest test

JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./mvnw -pl erp-modules/erp-oa -am \
  -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=OnboardSignScenarioRuleTest,OaSignPlanServiceImplTest,OaSignPackagePreflightValidatorTest,OaExistingEmployeeOnboardServiceTest,OaSignTaskServiceImplTest test

JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./mvnw \
  -pl erp-modules/erp-system,erp-modules/erp-oa -am test
```

### 前端专项和构建

```bash
cd erp-ui
node test/signPackageFileGate.test.js
node test/signTaskCenter.test.js
node test/hrExistingEmployeeOnboardContract.test.js
npm test
npm run build:prod
```

### 发布门禁

```bash
cd ..
bash scripts/verify-contract-signing-release.sh --static
git diff --check
```

### 生产只读后检

由授权运维人员使用真实业务输入执行现有 fail-closed 审计脚本：

```bash
LI_MAN_INSURANCE_TYPE_CODE=<已确认值> \
SU_YUYU_INSURANCE_TYPE_CODE=<已确认值> \
bash /opt/erp-new/scripts/remote_audit_beijing_sign_publish_readonly_20260718.sh postcheck
```

要求脚本退出码为 0，并归档 `report.json`；不得把凭据、个人资料或合同正文写入证据。

## 七、责任分工

| 角色 | 必须交付 |
| --- | --- |
| 业务负责人 | 确认首期范围、试点组织、员工和扩量决策 |
| HR | 确认合同经办人、组织权限、员工资料、用工路线和外部合同核验 |
| 法务 | 批准模板、法律主体、印章使用和当前内部签署能力的对外口径 |
| 开发 | 冻结发布范围，完成生命周期开关、移动端、作用域、存储和后续按模板校验改造 |
| 测试 | 执行 Web/iOS/Android 三个样本和异常 UAT，归档脱敏证据 |
| 运维/DBA | 只读后检、制品发布、目录权限、监控、回退点和数据库账本补录 |

测试人和法务确认人不能由同一人代替。管理员账号成功不能替代非管理员 HR 权限验收。

## 八、灰度、监控与回退

### 灰度顺序

```text
1 个内部/虚构员工
  → 3 个完整样本
  → 试点组织 5～10 人
  → 增加其余入职路线
  → 增加第二组织
  → 完整 Release A UAT 和签认
  → 正式扩量
```

### 监控重点

- 生命周期 Outbox 的 `PENDING/RETRY` 积压及其组织、场景和时间分布。
- `NEEDS_DATA` 原因分布、`PLAN_NOT_FOUND`、`PLAN_CONFLICT`。
- 任务与签约包状态不一致。
- 文件下载失败、哈希不一致、磁盘空间和目录权限。
- 通知 Outbox 的 `RETRY/DEAD/SENDING` 积压。
- 同一员工的重复开放任务。
- 移动端 PDF 打开和签署失败率。
- 跨组织或跨员工访问拒绝记录。

### 回退方案

1. 立即停止扩量，保持生命周期自动建任务、自动发送、提醒、过期和应急建包为 false。
2. 停止新匹配或禁用 R0 方案，隐藏员工档案发起入口或撤回试点角色权限，阻断新流量。
3. 如果尚未发送任何真实签约包，可将 System、OA、前端原子切回已验证兼容的旧版本；保留新版本目录和哈希用于分析。
4. 如果已经存在发送中或已签包，不得盲目回到不认识新状态的旧 OA；优先停止新建并做前向修复，只有证明旧制品兼容现有状态后才允许二进制回退。
5. 已产生的任务、签约包、阅读记录、签名、最终文件、事件和通知不得删除或回写旧状态。
6. 数据库只做前向兼容修复，不回滚已执行的四个迁移，不恢复整库覆盖新证据。
7. 文件存储问题优先恢复挂载/权限，不重生成或覆盖已有最终文件。

## 九、工期和完成定义

在模板无需重新法审、已有发布权限且 HR/法务当天提供确认的前提下：

| 时间 | 目标 |
| --- | --- |
| T+0.5 天 | 完成 G0，发布候选和业务口径冻结 |
| T+1 天 | 完成 G1，P0 稳定性改造和专项测试通过 |
| T+1.5 天 | 完成 G2，部署、配置和一个内部样本跑通 |
| T+2 天 | 完成 G3，3 个完整样本通过，进入受控试点 |
| T+3～7 天 | 完成 G4 和完整 UAT，具备扩大入职签约范围的条件 |

“快速可用”完成定义：

- 一个非管理员 HR 可从员工档案发起入职签约。
- 发布全局方案后，非试点组织不会自动产生签约任务。
- Web、iOS、Android 三个样本均完成至 `SIGNED`。
- 最终文件、哈希、审计事件和权限验证全部通过。
- OA 重启/重新部署后文件不丢失。
- 发布候选可复现、可回退，静态门禁和构建为绿色。
- HR、法务、测试和运维完成试点签认。

“正式全量”不与“快速可用”等同。正式全量仍需完成全部目标场景 UAT、异常测试、业务/法务签认和观察期。

## 十、需要立即冻结的三项决策

1. **法律定位：** 首期是否接受 ERP 内部手写签名/印章图片 + 哈希留证；如果必须第三方 CA，立即切换为供应商集成项目。
2. **试点对象：** 明确一个组织、一个合同 HR、一个法律主体/印章和 3 个内部或虚构员工。
3. **方案口径：** 确认以后只维护全局方案库，旧的按门店重复方案不再新建；首期只发布一个唯一匹配入职方案。

这三项确认前可以完成代码和发布候选收口，但不能激活生产试点。

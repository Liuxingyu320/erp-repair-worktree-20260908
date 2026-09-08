# 合同签约双流程、主体匹配与 PDF 一致性修复实施方案

> 日期：2026-07-20
> 状态：本地实施与隔离验证已完成；待真实双流程 UAT 与生产发布授权
> 适用范围：Excel 入职合同导入、签约任务、签约包、公司与印章、薪酬结构确认书、劳动合同模板、电子确认页及未完成测试任务删除
> 关联基线：[入职合同 Excel 一键生成与两阶段签约实施方案](./2026-07-18-onboard-contract-excel-one-click-plan.md)

## 一、目标与结论

本次不重建签约引擎，在现有 Excel 导入、签约任务、签约包、文档版本、公司印章、员工阅读和最终确认能力上完成以下收口：

1. 同时支持“先选公司再签”和“先留签名再选公司”两条流程。
2. 未选择公司时不生成带占位公司的合同 PDF，只展示网页《签约事实确认单》。
3. Excel 使用现有“签约公司、法定代表人、注册地”进行组合模糊匹配；高置信且第一名明显领先第二名时自动选择，否则转 HR 确认。
4. Excel 确认的公司优先于员工部门绑定公司；合同法定信息始终读取公司主数据，Excel 不直接覆盖公司主数据。
5. 公司确定后自动带出唯一有效默认合同章；无有效章、章不唯一或章与公司不一致时阻止生成或发送。
6. 有社保固定匹配《薪酬结构确认书（B版）》，无社保固定匹配《薪酬结构确认书（A版）》，HR 不得直接切换 A/B。
7. 最终确认前不显示“最终合同”“已签署”或“阅读确认已完成”；员工逐份打开文件、确认最终 package root hash 后才能归档。
8. 修复劳动合同附件勾选、签章位置、日期断页、段落断页、表格续页、动态页数和电子确认页语义。
9. 增加管理员批量删除未完成签约任务能力；级联删除签约数据和文件，保留员工档案，不做备份。

本计划覆盖并替代 2026-07-18 方案中以下旧口径：

- “员工先查看含待确认公司的初始合同 PDF”改为网页事实确认单，不产生占位合同 PDF。
- “薪酬 A/B 由 HR 在方案中人工选择”改为按社保状态强制映射。
- “Excel 公司只展示推荐、实际公司只取部门绑定”改为 Excel 组合模糊匹配并优先采用匹配结果。
- 单一“先签后选公司”流程扩展为两种可选签署顺序。

## 二、已确认业务规则

### 2.1 两条签署流程

#### 流程 A：先选公司、再签名

```text
上传 Excel 并匹配员工/套餐
  → 模糊匹配公司
  → 自动或人工确定公司
  → 自动带出唯一有效默认章
  → 冻结公司、印章及合同事实快照
  → 生成完整待发送文件
  → HR 逐份预览并发送
  → 员工逐份打开文件
  → 员工一次完成手写签名和最终确认
  → 生成归档证据页
  → 已完成
```

此流程中员工面对的是公司、印章、薪酬版本和附件均已确定的完整文件，因此不再先采集一次签名样本。

#### 流程 B：先留签名、再选公司

```text
上传 Excel 并匹配员工/套餐
  → 向员工展示网页《签约事实确认单》
  → 员工核对岗位、薪酬、期限、地点和文件清单
  → 留存仅限当前任务使用的手写签名样本
  → 模糊匹配并确定公司
  → 自动带出唯一有效默认章
  → 冻结公司、印章及合同事实快照
  → 将签名样本应用到完整待确认文件
  → HR 逐份预览并发送
  → 员工逐份打开文件并最终确认
  → 生成归档证据页
  → 已完成
```

签名样本不得进入员工全局档案，不得跨任务复用。新签约任务必须重新采集。

### 2.2 员工确认与证据

- 最终确认采用已登录员工身份、明确确认短语和逐份打开文件，不增加短信验证码。
- 逐份阅读只要求每个文件实际打开并由员工确认，不设置最低停留时间，也不强制滚动到底。
- 待最终确认界面在每页覆盖淡色“待员工最终确认”水印；水印由查看器绘制，不写入待确认正文 PDF，确认前禁止下载无水印文件。
- 最终确认只改变状态和访问权限，不改动员工已查看的正文 PDF 字节。
- `package root hash` 按稳定顺序聚合员工实际确认的各正文文件哈希；电子确认页不参与该 root 的自引用计算。
- 确认后生成电子确认页，展示完整 root hash、签名样本采集时间（如有）、最终合同确认时间、员工身份摘要、签约包编号和文档版本。
- 最终归档 PDF 自身的 SHA-256 保存于后台验真数据，不尝试把文件自身哈希写进同一个 PDF。

### 2.3 公司与印章匹配

Excel 模板继续使用现有三列，不新增印章列，也不保存公司别名：

- 签约公司
- 法定代表人
- 注册地

匹配规则：

1. 对三列做确定性文本标准化后，查询全部启用公司主数据。
2. 综合公司名称相似度、法定代表人一致性和注册地址相似度计算候选分值。
3. 第一名达到可配置阈值且与第二名的分差达到可配置安全值时，自动选择第一名。
4. 分值不足、前两名接近或无候选时，进入“待 HR 确认公司”状态，不得生成完整合同。
5. 不保存本次名称与公司的别名对应关系，下次导入重新计算。
6. Excel 匹配公司与部门绑定公司冲突时，以 Excel 匹配结果为准，同时记录 Excel 原值、部门候选、最终公司、分值及处理模式。
7. 法定全称、公司编码、统一社会信用代码、注册地址、法定代表人任一缺失时硬阻断。
8. 合同渲染只使用已选公司主数据快照；Excel 法定代表人和注册地只参与匹配及提示，不写入合同。
9. 禁止把 `legalEntityId` 等内部数字渲染为公司名称；模板变量只接收明确命名的字符串快照。

印章规则：

- 公司存在一枚有效默认合同章时自动选择。
- 没有默认章但仅存在一枚有效合同章时自动选择该章。
- 无有效章、多个默认章、多个有效章且无唯一默认章时，转 HR 选择或修复主数据。
- 生成和发送前再次校验印章归属、状态、有效期及图片 SHA-256。

### 2.4 薪酬版本

- `SOCIAL_INSURED` / 有社保 → B 版。
- `SOCIAL_UNINSURED` / 无社保 → A 版。
- 社保状态为空、无法识别或方案版本与应选版本冲突时阻止生成。
- HR 只能修改社保状态，不能直接选择薪酬 A/B；状态修改后重新匹配方案和文件。
- 规则、模板或已发布方案版本变化时，所有未完成旧预览标记为“方案已过期”，必须重新生成预览。
- 未签错误文件作废后重新生成；已正式签署文件不得覆盖，只能作废并重新签署。

### 2.5 附件、签章和 PDF 版式

- 员工手册正文及其交付规则已由用户明确排除，本轮不创建、不修改、不验收。
- 附件清单不展示版本和哈希，只按当前签约包真实文件绘制方框内对号和文件名。
- 对号使用矢量绘制或已验证字体，不使用带叉的 `☒`。
- 宿舍协议不按实际住宿情况条件化，继续统一纳入；协议开头横线自动填入甲方法定全称。
- 员工签名图放在合同原“乙方签名”位置，公司章放在原“甲方盖章”位置。
- “见附加页”统一改为“签署时间及文件校验信息见本合同末页《电子签署确认页》”。
- 合同期限明确显示“方框内对号 固定期限 / 空方框 无固定期限”；合同和试用期日期不得跨页拆开。
- 标题与首段、合同期限段、15.1—15.3 等逻辑段落尽量整体分页，允许页尾留白。
- 岗位职责表续页重复表头；普通表格行不得跨页，只有单行自身超过整页时才允许受控拆分。
- 最终页数按实际归档页数生成；电子确认页在待确认和确认后均占固定页数，避免确认后改变前文页码。

### 2.6 未完成任务批量删除

- 仅管理员可见和可调用。
- 任务列表支持勾选一条或多条，使用统一“批量删除”按钮。
- 仅允许删除未完成最终确认的任务；`SIGNED` 等正式完成状态必须拒绝硬删除，只能走作废/重签。
- 二次确认弹窗展示任务数量、任务编号和员工，并要求明确确认不可恢复。
- 级联清理任务、签约包、文档、阅读记录、签名样本、事件、证据、导入行绑定和生成文件；保留员工档案、账号及人事资料。
- 不制作备份或回收站副本；保留最小管理员操作日志不视为合同备份。
- 批量处理逐任务隔离结果，返回成功和失败明细，不再以未解释的 HTTP 500 结束整批操作。

### 2.7 明确不修改的范围

以下内容经讨论明确保持现状，不作为本次“已修复”结论：

- 不新增独立送达地址字段，现有空地址风险继续保留。
- 不调整“行政经理”与茶艺、销售、开收市、店长管理职责之间的内容关系。
- 不修改加班补偿、调岗降薪、全面兼职限制条款。
- 不修改离职损失、工资抵扣、交接后结算及仲裁地点条款；不开发离职工资计算功能。
- 不修改监控、肖像、健康及犯罪记录等条款。
- 不改变宿舍协议统一纳入的业务做法。

## 三、实施顺序

### 阶段 0：冻结基线与发布边界

**目标：** 保证历史签署产物不可变，并避免当前脏工作树中的其他业务改动被带入。

**工作：**

- 保存当前模板、已发布方案版本、V1/V2 PDF、签名和印章证据的 ID 与 SHA-256，只读留作回归基线。
- 不修改 `uploadPath/private/sign-package/...` 下任何历史文件。
- 新模板、新方案和新迁移使用新的不可变版本；旧版本只关闭后续匹配，不原地改写。
- 检查当前未提交改动，后续实施按文件 allowlist 分阶段提交。

**验收：** 历史 V1/V2 文件哈希不变；新代码只能创建新版本或新任务。

### 阶段 1：公司模糊匹配与主数据硬门禁

**主要文件：**

- `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignOnboardExcelParser.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignOnboardContractSnapshot.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignOnboardImportService.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignCompanyService.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaDeptScopeMapper.java`
- `erp-modules/erp-oa/src/main/resources/mapper/oa/OaDeptScopeMapper.xml`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPackagePreflightValidator.java`
- `erp-ui/src/views/hr/components/HrSignDataImportDialog.vue`
- `erp-ui/src/views/oa/signPackage/index.vue`

**工作：**

- 新增独立、可单测的公司匹配服务，避免将分值算法散落在导入服务和前端。
- 在导入行中冻结匹配公司 ID、匹配模式、第一/第二分值、阈值版本和公司主数据版本；不依赖页面重新计算。
- 公司匹配算法必须确定性：同一公司主数据版本和同一 Excel 行得到相同结果。
- 阈值和领先分差通过配置管理，缺少配置时采用保守默认值；配置变更使未完成预览过期。
- UI 展示 Excel 原值、匹配公司完整法定信息、匹配分值、部门候选及是否自动确定。
- 完成公司主数据、印章唯一性和印章文件哈希硬门禁。

**测试：**

- 全称接近、名称差异大但法代/地址一致、无候选、低分、并列第一、部门冲突、主数据缺字段。
- 唯一默认章、唯一非默认章、无章、多章、多个默认章、过期章、跨公司章和图片哈希变化。

### 阶段 2：拆分公司冻结、签名采集和最终生成

当前 `OaSignPackageServiceImpl.finalizePackage` 将“员工已留签名、选择公司、选择印章、重新渲染最终文件”耦合在一起，只支持先签后选。必须拆为可独立幂等调用的步骤。

**主要文件：**

- `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignPackage.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/constant/OaSignPackageStatus.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/service/OaSignTaskStateMachine.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/service/IOaSignPackageService.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPackageServiceImpl.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSignPackageController.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaSignPackageFinalizeRequest.java`
- `erp-ui/src/views/mobile/signPackage/index.vue`
- `erp-ui/src/views/oa/signPackage/index.vue`
- `erp-ui/src/api/oa/signPackage.js`

**数据与状态：**

- 增加签署顺序快照：`COMPANY_FIRST` / `SIGNATURE_FIRST`。
- 将“签名样本已采集”“公司印章已冻结”“完整文件已生成”“HR 已发送”“员工已最终确认”作为独立事实保存，不能仅从一个宽泛状态推断。
- 签名优先任务发送时冻结员工可见事实和计划文件清单，并保存快照版本与哈希；公司和印章不参与该哈希，允许后选。提交和生成时均须重算比对，岗位、薪资、期限、地点或文件计划变化后旧签名自动失效并要求重新确认。
- 现有 `initial_signed_time` 不再对新任务显示为正式签署时间；新增或明确映射为“签名样本采集时间”。
- 公司、印章或任一员工权利字段发生变化时，旧待确认版本失效、阅读状态清零并生成新版本。

**接口职责：**

1. 确认网页签约事实并采集任务级签名样本。
2. 解析并冻结公司与印章；允许在签名前或签名后调用。
3. 在合同事实、公司、印章和所需签名均就绪时生成完整候选文件。
4. HR 预览后显式发送。
5. 员工逐份打开当前版本并确认 root hash。

所有写接口必须带 `requestId` 和版本条件，重复调用返回同一结果，禁止生成重复版本。签名提交还须保存载荷哈希：同一 `requestId` 与同一载荷返回既有结果，同一 `requestId` 携带不同载荷必须拒绝。

### 阶段 3：按社保强制匹配薪酬确认书

**主要文件：**

- `erp-modules/erp-oa/src/main/java/com/erp/oa/constant/OaOnboardSalaryVersionPolicy.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/service/rule/OnboardSignScenarioRule.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignOnboardImportService.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignOnboardGenerationService.java`
- `erp-ui/src/views/hr/components/HrSignDataImportDialog.vue`
- `sql/erp_oa_sign_company_salary_policy_20260720.sql`
- `docker/mysql/db/erp_oa_sign_company_salary_policy_20260720.sql`

**工作：**

- 将薪酬版本函数改为由标准化社保状态唯一推导。
- 方案发布门禁校验 A/B 模板与社保条件一致；错误绑定不能发布。
- 通过新的前向迁移发布正确方案版本并关闭旧版本后续匹配，不回改已经执行的 20260719 历史迁移。
- 预览显示“社保状态 → 应选版本 → 实际模板”，不提供 A/B 直接编辑控件。
- 对现有未完成批次做版本过期提示，要求重新预览；不静默替换已预览文件。

**验收矩阵：** 有社保/B、无社保/A、社保为空、冲突方案、规则发布后旧预览过期、HR 修改社保后重新匹配。

### 阶段 4：模板、PDF 和电子确认页修复

**主要文件和产物：**

- `scripts/contract/prepare_onboard_templates_20260718.py`
- `scripts/contract/build_beijing_sign_safe_templates_v5_20260718.py`（复制为新版本生成器，不覆盖 v5 产物）
- `scripts/test_prepare_onboard_templates_20260718.py`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignDocumentService.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignedPdfService.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPlacementPolicyService.java`
- 新的不可变劳动合同及薪酬确认书模板版本
- 新的已发布方案版本

**模板修复：**

- 清空劳动合同页眉中的公司占位符，禁止出现“待 HR 确认”或 `${companyName}`。
- 期限选择、日期段、送达条款、签署页、宿舍协议和岗位表按第 2.5 节调整。
- 附件清单由包内实际文件生成，不使用源模板静态空框。
- 员工手册正文相关工作按用户明确要求排除，不纳入本轮实施与放行判定。
- 签名及盖章定位使用冻结的位置策略，不因正文重排落到错误位置。

**PDF 证据规则：**

- 待确认版本的电子确认页写“待员工最终确认”，不得预写完成状态或最终时间。
- 待确认逐页水印由前端查看器覆盖；服务端归档正文不靠“去水印”再次改写。
- 员工确认后只生成/替换固定页数的证据页，正文文件哈希和 package root 保持不变。
- 证据页展示完整 package root、签名样本采集时间、最终确认时间和版本；归档文件哈希只存后台验真记录。
- 页码在固定证据页占位存在时一次计算，确认后不改变总页数。

**渲染验证：**

- 使用 LibreOffice 渲染普通值、字段上限值和多页岗位职责表。
- 用 Poppler 获取实际页数、逐页文本及 PNG，检查断页、表头、勾选、签名、印章和页码。
- 用真实生成路径验证 PDF，不以 DOCX XML 静态检查替代最终渲染。

### 阶段 5：管理员批量删除未完成任务

**主要文件：**

- `erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSignTaskController.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/service/IOaSignTaskService.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignTaskServiceImpl.java`
- 新建批量删除请求/结果 DTO
- `erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaSignTaskMapper.java`
- `erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignTaskMapper.xml`
- 签约包、文件、事件、证据、导入行相关 mapper
- `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignFileStorageService.java`
- `erp-ui/src/views/oa/signTask/index.vue`
- `erp-ui/src/api/oa/signTask.js`

**工作：**

- 新增管理员专用批量硬删除接口；后端再次校验管理员身份，不能只依赖前端隐藏按钮。
- 锁定所选任务并逐条校验状态；任一正式完成任务不得进入删除 SQL。
- 收集受管理文件路径并校验必须位于签约存储根目录，禁止使用宽目录或未解析路径删除。
- 数据库按外键依赖顺序清理；文件在数据库成功后删除，不归档、不备份。
- 单个任务失败不回滚其他任务，结果中返回明确错误码和说明。
- 保留管理员、时间、任务 ID 和结果的最小操作日志，不记录身份证、薪资、签名图或合同正文。
- 前端支持单选和多选，未选任务或包含完成任务时给出明确提示。

**验收：** 当前截图中的未完成测试任务可由管理员自行选择删除；员工档案仍存在；刷新后无任务、包、文件或导入绑定残留；浏览器控制台不出现 500。

### 阶段 6：兼容处理与发布

- 未完成旧批次：标记方案过期，重新预览后才能生成。
- 未完成旧任务：根据已有事实迁移到可判定状态；公司或版本不可信时阻止继续并要求作废重发。
- 正式已签历史文件：保持不可变，发现错误时作废并新建签约任务。
- 本次不自动删除现有测试记录；删除按钮上线后由管理员选择执行。
- 发布顺序固定为：迁移 → 公司/印章主数据校验 → 新模板候选 → 新方案发布 → 后端 → 前端 → 隔离 UAT → 灰度。
- 回滚只关闭新入口/新流程并回到上一制品；已经产生的签名、确认和文件不得通过数据库回滚删除。

## 四、验证与放行门

### 4.1 自动化测试

后端聚焦测试至少覆盖：

- `OaSignOnboardExcelParserTest`
- `OaSignOnboardImportServiceTest`
- `OaSignCompanyServiceTest`
- `OnboardSignScenarioRuleTest`
- `OaSignPackageServiceImplTest`
- `OaSignFinalConfirmationFlowTest`
- `OaSignDocumentServiceTest`
- `OaSignedPdfServiceTest`
- `OaSignPackagePreflightValidatorTest`
- 新增公司模糊匹配和未完成任务批量删除测试

运行：

```bash
mvn -pl erp-modules/erp-oa -am test -DskipTests=false
```

前端至少更新或新增：

- `erp-ui/test/signTaskCenter.test.js`
- `erp-ui/test/signPackageModule.test.js`
- `erp-ui/test/signPackageFileGate.test.js`
- `erp-ui/test/contractUsabilityAudit.test.js`
- Excel 公司匹配、双流程、水印覆盖层和批量删除测试

运行：

```bash
cd erp-ui
npm test
```

### 4.2 必须通过的端到端场景

1. 公司先选：高置信 Excel 公司 → 默认章 → HR 预览 → 员工逐份打开 → 手写签名并确认 → 归档。
2. 签名先留：事实确认 → 任务级签名样本 → 公司/章 → HR 预览 → 员工逐份确认 → 归档。
3. 公司低置信或前两名接近：不得自动选择，转 HR。
4. Excel 公司与部门公司冲突：采用 Excel 匹配公司，并保留审计来源。
5. 公司主数据缺字段、章无效、章跨公司、章哈希变化：全部硬阻断。
6. 有社保只出现 B 版；无社保只出现 A 版；旧预览必须重新生成。
7. 最终确认前每页显示查看器水印，PDF正文中不存在“已完成”假证据；最终确认后正文哈希不变。
8. 附件清单与实际包文件一致，勾选为方框内对号；员工手册正文不纳入本轮验收。
9. 日期和逻辑段不拆页，岗位表续页有表头，最终页数正确。
10. 管理员可批量删除未完成任务，不能删除已完成任务，员工档案不受影响。

### 4.3 放行阻断条件

出现以下任一情况不得发布：

- PDF 中出现“待 HR 确认”、公司内部 ID、空公司法定字段或公司与印章不一致。
- 员工未确认当前 root hash，但任务已进入完成状态。
- 待确认水印的增加或移除改变员工确认的正文哈希。
- 有社保绑定 A 版或无社保绑定 B 版。
- 附件清单与包内实际文件不一致。
- 归档总页数与页脚不一致，关键日期、条款或表格出现非预期跨页。
- 删除接口能够删除正式完成任务、能够越权调用、产生孤儿文件或返回无说明 500。

## 五、完成定义

只有同时满足以下条件，才能宣告本轮问题已修复：

- 两条签署顺序均通过真实浏览器 UAT。
- 公司、印章、社保薪酬版本和模板文件均由服务端硬门禁保证。
- 员工最终确认绑定的正文 root hash 可由验真接口重新计算。
- 新模板真实渲染版式通过逐页检查。
- 未完成任务批量删除可用且不影响员工档案。
- 旧签署产物未被覆盖，错误历史合同按作废/重签处理。
- “明确不修改的范围”在发布说明中列为保留风险，不能误报为已解决。

## 六、2026-07-20 实施验证记录

- OA 后端全量回归：1045 项测试通过，0 失败、0 错误，16 个 Reactor 模块均构建成功。
- 前端全量回归：218 项 Node 测试通过，0 失败。
- Excel 隔离 UAT：同一批次成功匹配 28 名员工；有社保行显示 B 版，无社保行显示 A 版；公司低置信时转 HR 确认，公司与印章缺失项在预览中明确阻断。
- UAT 首次重放发现未匹配公司行的 `company_dept_conflict` 为空会导致 500；已设置领域默认值并增加回归断言，重启后同批 Excel 重放成功。
- 最终独立审查发现的两个边界已修复：“先留签名、后选公司”的最终化路径现在同样校验完整公司法定主数据；候选重试同时绑定补资请求、签名请求、采集时间、唯一样本证据和候选生成事件。修复后复审未发现 P0/P1 阻断项。
- 隔离合成浏览器 UAT：公司先行完成、签名先行待公司、签名先行最终确认、未完成任务硬删除与已完成任务保护共 4 个场景通过；控制台错误 0，关键 API 非 200 为 0。
- 硬删除 UAT 使用合成任务验证 `requestId` 与 `expectedVersion` 并只删除未完成项；未对真实数据执行删除。
- PDF v6 渲染检查：18 份 PDF、112 页，空白页、占位词/“待 HR 确认”、非 A4 页和页码错误均为 0。
- 员工手册正文和送达地址按用户明确要求排除，未修改，也不作为本轮本地完成判定的阻塞项。
- 仅剩外部放行门：授权后的生产备份/迁移/模板发布/灰度、真实双流程签字 UAT 及 HR/业务/法务签认。
- 隔离 UAT 进程和临时数据库均已清理；未修改历史签署文件，未删除真实签约记录，未发布到生产。

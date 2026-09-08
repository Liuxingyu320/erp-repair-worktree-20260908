# 入职合同 Excel 一键生成与两阶段签约实施方案

> 日期：2026-07-18
> 状态：本地实现和仓库内不可变 `development` 源合同已完成，功能默认关闭；最终 ready 尚缺仓库外签名审批、重构建制品、外部证据和灰度审批
> 目标：删除旧入职合同创建入口，以“员工档案选人 + Excel 签约数据 + 条件化入职套餐”快速完成资料补充、合同生成、预览、单发/群发和完整签署闭环；历史任务和现有签署闭环继续保留。
> 关联基线：[合同签约快速可用实施方案](./2026-07-18-contract-signing-rapid-activation-plan.md)

## 当前实施结果（2026-07-19）

- 已删除旧入职 preview/initiate 入口及专用服务，历史任务、签约包、签署、最终 PDF、哈希和审计闭环继续保留。
- 已完成 HR 选人、Excel 严格解析、逐行预览/编辑、资料补充、HR 审核、生成不发送、单发/所选/全部发送，以及法律主体推荐提示。
- 已完成员工客观事实白名单、System 档案 CAS 同步、多轮资料任务、审批/生成崩溃恢复、同员工开放 ONBOARD 任务数据库唯一约束。
- 已完成九份 v5 候选模板、条件套餐门禁和北京发布只读审计；候选模板仍为停用状态，未替 HR/法务执行审批或发布。
- 前后端新入口和全部自动化开关保持默认关闭；本次未部署生产、未执行生产迁移、未发送真实合同。
- 已新增 20260718 专用 manifest、迁移账本、精确源码/删除/混合路径清单、静态门禁和 readiness 门禁；跟踪 manifest 只是不可变 `development` 源合同，不得改成 `ready` 自证审批，本次两条迁移也未回写 20260716 旧发布控制文件。
- 已修复两个发布级问题：Docker 新库初始化中 System 档案基表现在早于补资迁移；两份 Compose 现在都将 OA Excel 开关仅传入 OA 服务，渲染后默认为 `false`。
- 当前工作区与其他签约/部署改动交织，混合路径只能在 Release A 干净候选上按 hunk 重组。当前 Docker OA JAR 仍是含旧入口的过期制品，已明确禁止部署；未生成伪“可上线”压缩包。
- 当前经保护的 Release A tag/commit 绑定、Source Approval 签名、Build Attestation 签名、内嵌来源的重构建制品和 7 项外部证据均未具备；`--readiness` 必须以退出码 `5` 阻断。
- 外部证据已收紧为 `schemaVersion=2` 精确结构：不接受只填“passed”的结论，必须校验原生 MySQL 5.7/8.0 的迁移、对象与 12 个索引，9 份模板与已发布 plan ID/哈希，同制品隔离启动/健康/浏览器探针，7 级劳动和劳务均命中 `2-9`，员工补资经 HR 审核后同步/重匹配，27 人唯一匹配/发送结果、最终 PDF/哈希/审计链和未过期的单范围灰度窗口。方案发布口径已与运行代码统一：生产只绑定 5 个 `shopDeptId=0` 的全局路由方案 A4/A5/A1/B1/B3，A3 为独立隔离 UAT 专用且不可进生产，生产与隔离快照必须可重算且不同。27 人路由分布固定为 `19/5/1/1/1`，文档数从实际解析模板集汇总为 `107 + 2-10 实际命中数`，不再使用候选模板数乘人数。工作簿原文只保留算法为 `HMAC-SHA256`、key ID 为 `onboard-hr-qa-workbook-hmac-20260718-v1` 的 HMAC，非 PII 类别代码与各聚合结果使用可重算的规范 JSON SHA-256；不携带工作簿、PDF 或审计 payload 原文。

## 一、方案结论

不新建第二套签约引擎。现有任务、签约包、员工阅读、手写签名、HR 选主体盖章、员工最终确认、PDF 下载、哈希和审计事件全部保留复用；新增能力只负责把“选中的员工 + Excel 合同数据 + 条件化模板规则”安全转换为现有签约任务。

最终流程为：

```text
HR 员工档案选择员工
  → 批量处理入职合同
  → 上传 Excel 的“签约数据”sheet
  → 按员工匹配并预览套餐、文件和缺失资料
  ├─ HR 能确认的资料：HR 直接填写
  └─ HR 暂不能确认的个人资料：先发送资料补充任务
       → 员工登录填写个人事实
       → HR 审核
       → 审核通过后同步员工档案并重新匹配套餐
  → HR 确认不存在外部已签合同
  → 一键生成全部可生成合同（不自动发送）
  → 单个预览 / 批量预览
  → 单个发送 / 发送所选 / 一键发送全部可发送项
  → 员工逐份获取初始合同并确认阅读
  → 员工手写签名（此时法律主体允许待确认）
  → HR 选择法律主体和有效印章并生成最终合同
  → 员工逐份获取最终合同并确认
  → SIGNED
  → 下载最终 PDF / 验证哈希 / 检查审计事件
```

“一键生成”和“一键发送”必须分开。生成后先得到 `READY_TO_SEND` 任务及完整预览 PDF，HR 核对后再决定单发或群发，避免上传后直接把错误合同推给员工。

## 二、删除旧创建入口，保留历史事实与签署闭环

### 2.1 保留能力

- 保留历史签约任务、签约包、文档、签名、印章、证据和审计数据。
- 保留员工端阅读、首次签名、HR 最终盖章、员工最终确认、下载及验真接口。
- 保留现有批量发送接口和逐项失败隔离能力。
- 保留历史数据和底层签署结构；旧创建入口代码按删除清单移除。

### 2.2 删除范围

- 删除前端 `HrOnboardContractBatchDialog.vue`、员工档案中的旧入口引用及旧入口专用测试。
- 删除前端 `previewOnboardSignTaskBatch`、`initiateOnboardSignTaskBatch` API 封装。
- 删除后端 `POST /oa/signTask/onboard/batch/preview` 和 `POST /oa/signTask/onboard/batch/initiate`。
- 删除旧入口专用 DTO、VO、预览/发起逻辑及不再被其他调用方使用的测试代码。
- `OaExistingEmployeeOnboardService` 中仍被批量发送使用的能力先迁移到独立服务，再删除旧入口遗留代码；不得连带删除现有 `POST /oa/signTask/batch/send`。
- 生命周期自动建任务、自动发送、自动提醒、自动过期、应急自由建包继续保持关闭。
- 不删除历史任务、签约包、文件、签名、印章、哈希、审计事件或员工端签署接口。

### 2.3 开关

- 新增 `oa.sign.excel-import.enabled=false`，默认关闭新导入入口。
- 前端新增 `VUE_APP_SIGN_EXCEL_IMPORT_ENABLED`，只控制入口展示；后端开关始终为最终门禁。
- 继续保持 `oa.sign.emergency-create.enabled=false`、提醒和过期调度关闭。
- System 侧生命周期自动签约总开关保持 `hr.sign.lifecycle-automation.enabled=false`。

新入口故障时关闭 `oa.sign.excel-import.enabled` 并隐藏新入口；已经生成的任务继续走原有签署闭环，不删任务、不删批次、不删证据。旧入口代码删除后的代码级回退使用上一个不可变发布制品或 Git 回退提交，不在生产环境临时恢复旧接口。

## 三、已确认的业务规则

### 3.1 入职套餐不是固定九份文件

`入职套餐.7z` 是 HR 按条件预组装文件的参考方式，不把 A1/A2 等目录名称直接写入系统。HR 目录编码与项目现有 A1—A6、B1—B3 路由含义冲突，系统继续使用 `OnboardSignScenarioRule` 的现有路由编码。

九份源文件只维护一份；系统根据员工条件和已发布方案版本动态选出适用文件。初始绑定规则为：

| 条件 | 自动加入文件 |
| --- | --- |
| 劳动合同 | `2-3 入职承诺书`、`2-4 劳动合同`、`2-5 员工手册签收确认书`、一份 `2-6 薪酬结构确认书` |
| 劳动合同薪酬版本 | HR 在方案中人工选择 A 或 B，与社保口径独立 |
| 劳务合同 | `2-3 入职承诺书`、`2-7 劳务合同`、`2-8 劳务合同书签收单` |
| 职级达到 7 级及以上 | 无论劳动合同还是劳务合同，加入 `2-9 保密与竞业限制协议` |
| 16—18 周岁且非在校 | 加入 `2-10 非在校生及未成年工入职声明书` |

补充约束：

- `2-4` 与 `2-7` 互斥。
- `2-6` A 版与 B 版互斥，且必须与方案人工选择的薪酬版本一致。
- `2-9` 业务条款不改；仅制作系统占位符版本，并按已确认规则同时支持劳动和劳务员工。
- `2-10` 与职级无关。必须同时满足年龄和非在校条件；员工还需填写“开始以个人劳动收入为主要生活来源的年月”，HR 审核后才能生成。
- 当前规则不支持“有社保 + 劳务合同”。如果 Excel 出现该组合，显示 `UNSUPPORTED_COMBINATION`，不得猜测套餐。
- 模板绑定必须进入已发布方案版本；运行中不得临时修改已经锁定的模板版本。

### 3.2 数据来源优先级

1. HR 在员工档案中选中的 `employeeId` 集合是本批次白名单。
2. Excel 只提供本次合同数据快照，不批量覆盖员工主档。
3. 员工账号、组织范围、员工合同事实、当前任务事实由服务端实时数据决定，Excel 无权覆盖。
4. 身份证号是首选匹配键；手机号只在选中集合内唯一且姓名一致时作为次级匹配；姓名不得单独自动匹配。
5. Excel 中未被 HR 选中的额外行只显示警告并排除；选中员工在 Excel 中缺行、重复行或冲突匹配时阻断。
6. Excel“签约公司、法定代表人、注册地址”只作为后续法律主体推荐和一致性提示，不自动创建或修改法律主体。

### 3.3 地址规则

1. Excel“家庭住址”有值时，进入本次签约快照。
2. Excel 为空时，读取 ERP 员工档案“现住址”。
3. 两处都为空时，进入资料补充流程。
4. 不允许自动使用身份证登记地址替代现住址。
5. 员工补交的现住址经 HR 审核通过后，同时更新员工档案和本次签约快照，并保存修改前后值与审核记录。

### 3.4 资料补充与字段权限

缺资料不再简单阻断整批。每行允许选择两种处理方式：

- HR 直接填写并确认。
- 发送“资料补充任务”给员工；此时不生成、不发送带空字段的合同。

员工可提交的内容只限个人事实：

- 现住址。
- 是否在校及必要的学校信息。
- 是否退休等客观状态和个人声明信息。
- `2-10` 所需的个人声明年月。

员工不能直接选择或修改：

- 劳务人员类型。
- 商业保险类型。
- 合同类型、社保类型。
- 岗位、职级、工作地、城市等级、工时制度。
- 合同、试用期日期及薪资。
- 法律主体和印章。

员工提交后状态进入 `PENDING_HR_REVIEW`。HR 根据员工提交的事实选择最终劳务人员类型、保险类型或驳回补充；HR 未审核通过前不得生成合同。

经 HR 审核通过的个人事实同步员工档案；Excel 导入的合同、薪资、日期数据始终只写签约快照，不回写员工档案。

### 3.5 历史补签

- 允许合同起始日期早于实际电子签署日期。
- 合同起止日期保持 Excel 原值，不改成当天。
- HR 必须勾选“历史补签确认”并填写原因。
- 签约包记录 `historicalSupplement=true`，实际签署时间仍使用系统真实时间。
- 审计事件必须同时记录合同生效日期、实际签署时间、操作人、原因和外部合同核验确认。

### 3.6 薪资校验

硬阻断：

- 金额缺失或为负数。
- 综合工资不等于底薪、岗位津贴、驻外补贴和绩效津贴合计。
- 必填薪资版本无法确定。

可确认警告：

- 薪资低于或高于岗位参考区间。
- HR 填写确认原因后允许生成和发送，审计事件记录异常值、参考区间和确认原因。

### 3.7 法律主体和印章

- 员工首次阅读和手写签名时，法律主体允许为空；初始合同显示“待 HR 确认”，不得保留源模板中写死的公司名称。
- Excel“签约公司”只作为 HR 选择法律主体时的推荐项。
- HR 最终处理时只能选择已启用的法律主体和有效印章。
- HR 选定主体和印章后重新生成全部最终 PDF，产生新的文档版本和哈希。
- 员工必须逐份获取最终合同并确认，之后才能进入 `SIGNED`。
- HR 更换法律主体或印章时，旧最终确认失效，必须生成新版本并由员工重新确认。

### 3.8 外部合同确认

- 生成前必须人工勾选“不存在已签纸质合同或尚未同步的第三方电子合同”。
- 不允许默认勾选。
- 已存在外部合同的员工必须先归档或同步事实，不得通过本入口重复发起。

## 四、当前 Excel 的验证结果

`北京区域0716-2.xlsx` 的 `签约数据` sheet 有 28 行、26 个字段：

- 27 行属于正式批次，1 行标注为实验数据且无法匹配正式员工主表。
- 正式 27 行按项目现有路由分为 5 组：A4 19 行、A5 5 行、A1/B1/B3 各 1 行。
- 身份证格式、手机号格式、薪资分项合计、合同和试用期日期顺序均通过基础校验。
- 社保值为“有/无”，导入时必须转换为系统编码；合同期限“固定期限”转换为 `FIXED_TERM`。
- 当前工作簿交叉检查约有 20 人缺少现住址，需要 HR 或员工补充。
- 2 行劳务合同缺少劳务人员类型和保险类型，需要 HR 审核确认。
- 所有正式合同起始日早于 2026-07-18，按历史补签处理。
- 1 行薪资低于参考区间，按黄色风险提醒处理。
- 工作簿内的实验行不得依赖备注硬删除；只要它不在 HR 选中的员工集合中，就以 `EXTRA_NOT_SELECTED` 警告排除。

验收当前文件时，HR 选中预期 27 人后必须得到 27 个唯一匹配行和 1 个额外行警告，不能生成第 28 个实验任务。

## 五、交互与状态设计

### 5.1 HR 批次页面

新增 `HrSignDataImportDialog.vue`，按以下步骤展示：

1. 已选择员工及签约组织。
2. 上传 Excel，只读取名称精确为 `签约数据` 的 sheet。
3. 显示匹配结果、数据来源、系统路由、方案版本和预计文件清单。
4. 显示缺失字段、硬错误、黄色警告和已有任务事实。
5. HR 可逐行填写 HR 字段、确认警告或发送资料补充任务。
6. 资料补充完成并审核后重新预览；方案或模板版本变化时强制重新预览。
7. “一键生成”只处理全部可生成行，也支持勾选单行/多行生成。
8. 生成后可逐人预览全部 PDF。
9. 支持单个发送、发送所选和一键发送全部可发送项。

导入行状态：

```text
MATCHED
  → NEEDS_HR_DATA
  → WAITING_EMPLOYEE_DATA
  → PENDING_HR_REVIEW
  → READY_TO_GENERATE
  → GENERATING
  → GENERATED
  → SENT / PARTIAL_SENT
```

异常状态包括 `CONFLICT`、`EXCLUDED`、`PLAN_CHANGED_REPREVIEW`、`GENERATE_FAILED` 和 `PROFILE_SYNC_FAILED`。这些是导入行状态，不替换现有签约任务/签约包状态机。

### 5.2 员工资料补充页面

- 员工只能看到本人待补资料任务。
- 页面只渲染服务端下发的可编辑个人字段，不展示或接收 HR 专属字段。
- 提交后不能直接生成合同，先进入 HR 审核。
- HR 驳回时记录原因并允许员工重新提交。
- HR 审核通过并同步档案成功后，OA 重新读取员工签约候选数据并重新匹配方案。

### 5.3 现有签约状态机

合同生成后继续使用现有状态：

```text
READY_TO_SEND
  → PENDING_SIGN / PART_VIEWED
  → PENDING_COMPANY
  → PENDING_FINAL_CONFIRM
  → SIGNED
```

员工第一次签名不是最终完成；只有 HR 生成最终合同且员工逐份确认最终版本后，才能进入 `SIGNED`。

## 六、后端设计

### 6.1 复用点

- `OnboardSignScenarioRule`：继续负责 A1—A6、B1—B3 路由和唯一已发布方案匹配。
- `OaSignTaskOrchestrator`：继续创建任务、锁定方案版本、生成文档和保存不可变快照。
- `OaSignTaskBatchSendService`：继续逐任务调用现有发送服务，支持单发、群发、逐项失败隔离和重放幂等。
- `OaSignPackageServiceImpl` 及现有员工端接口：继续完成阅读、签名、主体/印章、最终确认和证据生成。
- System `listSignCandidates`：继续提供服务端权威员工身份、组织和档案事实。

旧 `OaExistingEmployeeOnboardService.initiate` 随旧入口删除。新导入服务使用专用 `OaOnboardSignEventFactory`，只接收已经完成员工范围、Excel 覆盖值、HR 审核和方案版本校验的不可变快照。

### 6.2 新数据表

新增 `oa_sign_onboard_import_batch`：

- 批次编号、签约组织、选人集合哈希和人数。
- 原文件名、大小、SHA-256、sheet 名。
- 状态、乐观锁版本、各类行数、创建人、过期时间和时间戳。

新增 `oa_sign_onboard_import_row`：

- Excel 源行号和行哈希。
- 匹配后的员工 ID、匹配方式和脱敏展示值。
- 规范化合同快照 JSON。
- 校验状态、错误码、警告码及确认记录。
- 路由码、预计方案版本 ID 和方案版本哈希。
- 资料补充任务 ID、签约任务 ID、签约包 ID。
- 生成/发送状态、版本和时间戳。
- 唯一约束 `(batch_id, source_row_number)` 和 `(batch_id, employee_id)`。

新增 `oa_sign_onboard_data_request`：

- 关联导入行和员工。
- 服务端允许编辑的字段清单。
- 员工提交值、提交时间和版本。
- HR 审核状态、审核人、原因和时间。
- 档案同步状态、同步请求 ID、同步前后哈希。
- 状态：`PENDING_EMPLOYEE`、`SUBMITTED`、`APPROVED`、`REJECTED`、`PROFILE_SYNC_FAILED`、`COMPLETED`、`CANCELLED`。

原 Excel 在受控临时目录解析后删除；数据库只长期保留文件元信息、SHA-256、规范化快照和审计引用。接口和日志不返回/打印完整身份证号、手机号、地址或薪资 payload。

### 6.3 新接口

HR 导入：

- `POST /oa/signTask/onboard/import/preview`：multipart `file`、`employeeIds`。
- `GET /oa/signTask/onboard/import/{batchId}`：刷新批次、行和生成结果。
- `PUT /oa/signTask/onboard/import/{batchId}/rows/{rowId}`：HR 填写允许字段、确认黄色警告。
- `POST /oa/signTask/onboard/import/{batchId}/data-request/send`：给选中缺资料行发送资料补充任务。
- `POST /oa/signTask/onboard/data-request/{requestId}/review`：HR 审核员工提交。
- `POST /oa/signTask/onboard/import/{batchId}/generate`：生成选中或全部可生成行。
- 生成后的单发/群发继续调用 `POST /oa/signTask/batch/send`；单发传一个 `taskId`。

员工资料补充：

- `GET /oa/signTask/onboard/data-request/mine`。
- `GET /oa/signTask/onboard/data-request/{requestId}`。
- `POST /oa/signTask/onboard/data-request/{requestId}/submit`。

System 内部档案同步：

- 在 `RemoteUserService` 增加受 `SecurityConstants.INNER` 保护的幂等档案补充接口。
- 只接受 OA 审核通过的个人事实字段和唯一 `requestId`。
- 同步成功后 OA 必须重新读取 `SignCandidateUser`；未知响应通过同一 `requestId` 重试，禁止重复审计或覆盖 HR 专属字段。

### 6.4 Excel 解析规则

- 只接受 `.xlsx`，最大 10 MB、最多 500 行；一次选人最多 100。
- 按表头名称解析，允许列重排，禁止按固定列序号解析。
- sheet 名必须精确为 `签约数据`。
- 缺失/重复必填表头、公式单元格、非法日期、非法金额或非法枚举均在预览阶段报告。
- 手机号即使 Excel 单元格是数值，也按字符串规范化，防止精度或格式丢失。
- 社保、合同期限、合同类型、职级等中文值转换为系统编码后再参与路由。
- 生成前重新加载员工范围、现有任务事实和方案版本；预览版本与生成版本不一致时返回 `PLAN_CHANGED_REPREVIEW`。

### 6.5 幂等和并发

- 预览使用创建人 + 签约组织 + 选人集合哈希 + 文件哈希复用未过期批次。
- 生成请求必须携带批次版本、行 ID 和 `requestId`。
- 每行独立事务、原子 claim，允许部分成功，单行失败不得回滚其他员工。
- `sourceType=MANUAL_SIGN_EXCEL_IMPORT`，`sourceBusinessId=rowId`，`sourceEventVersion=row.version`。
- 继续依赖现有开放入职任务唯一约束，防止跨批次重复创建同一员工的开放任务。
- 网络结果未知时先 GET 批次/任务事实，再使用同一 `requestId` 重试。

## 七、模板处理

HR 套餐内同名文件 SHA-256 一致，且与此前九份源文件完全一致。源文件不能直接作为系统模板上传：其中仍有下划线空格和写死的公司名称，而当前文档服务要求受支持占位符。

实施时复用并修订：

- `scripts/contract/prepare_onboard_templates_20260718.py`
- `scripts/test_prepare_onboard_templates_20260718.py`

要求：

- 不修改合同业务条款，只把姓名、证件、地址、日期、薪资、公司等字段替换为系统占位符。
- 初始法律主体为空时 `${companyName}` 渲染为“待 HR 确认”。
- 新增并正式支持 `ONBOARD_MINOR_NONSTUDENT_DECLARATION`。
- 为 `2-10` 增加明确的个人声明年月占位符，不再使用语义不确定的 `workStartDate`。
- `2-9` 的模板元数据不得限定为仅劳动合同；职级规则由已发布方案版本控制。
- 所有模板使用 `APPENDED_CONFIRMATION_PAGE` 放置员工签名和公司印章，不使用不稳定的绝对坐标。
- 每份候选 DOCX 必须重新渲染为 PNG 并逐页检查，再登记 SHA-256 和版本。

## 八、预计修改文件

### 8.1 前端

- 修改 `erp-ui/src/views/hr/components/HrEmployeeList.vue`：入口切换到新导入弹窗。
- 删除 `erp-ui/src/views/hr/components/HrOnboardContractBatchDialog.vue` 及其旧入口引用。
- 新增 `erp-ui/src/views/hr/components/HrSignDataImportDialog.vue`：上传、预览、补资料、生成和发送。
- 修改 `erp-ui/src/api/oa/signTask.js`：删除旧 onboard preview/initiate 封装，新增导入、资料补充和审核接口；保留 `sendSignTaskBatch`。
- 新增员工移动端资料补充页面及路由；复用现有移动端认证和通知跳转方式。
- 修改签约任务详情/字典工具，展示导入批次、历史补签、警告确认和资料补充状态。
- 新增前端测试，覆盖大整数 ID、作用域切换、文件/选人变化重新预览、单发/群发和部分失败。

### 8.2 OA 服务

- 修改 `erp-modules/erp-oa/src/main/resources/bootstrap.yml`：新增 Excel 导入功能开关。
- 修改 `erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSignTaskController.java`：删除旧 onboard preview/initiate 端点；保留现有批量发送和任务接口。
- 新增 `OaSignOnboardImportController`、`OaSignOnboardImportService`、`OaSignOnboardExcelParser`。
- 新增导入批次、导入行、资料补充任务的 domain、DTO、VO、mapper 和 XML。
- 新增 `OaOnboardSignEventFactory`，集中构造新导入入口的不可变签约事件。
- 将 `OaExistingEmployeeOnboardService.batchSend` 迁移到独立 `OaSignTaskBatchSendService`，再删除旧入口专用 service、DTO、VO 和测试。
- 修改 `OaSignTemplateType.java`、`OaSignDocumentService.java`、`OaSignPackagePreflightValidator.java`：支持 `2-10` 及新占位符。
- 必要时扩展 `OaSignPackage` 和 mapper，保存模板实际引用但当前包表尚未覆盖的签约快照字段。
- 新增 parser、service、controller、mapper、并发幂等和回归测试。

### 8.3 System 服务及 API

- 修改 `erp-api/erp-api-system/src/main/java/com/erp/system/api/RemoteUserService.java`：增加审核后档案同步接口。
- 修改 `SignCandidateUser` 及候选查询映射，返回经审核的在校/退休等个人事实。
- 扩展员工档案字段和字段注册表，至少支持现住址、当前在校状态及必要说明；新增字段必须进入正常 PII 权限和审计范围。
- 新增只允许 OA 内部调用的幂等档案补充服务，禁止写入合同、薪资、职级和法律主体字段。
- 新增档案同步、权限、幂等和字段白名单测试。

### 8.4 SQL 和发布清单

- 新增 `sql/erp_oa_sign_onboard_import_20260718.sql`。
- 新增员工档案补充字段/审计所需的 System SQL。
- 更新签约发布 allowlist、发布说明和数据库账本；不得重跑已完成的历史签约迁移。

## 九、实施顺序与完成门

### G0：删除旧创建入口并准备模板

1. 删除旧前端弹窗、API 封装、后端 preview/initiate 端点及专用逻辑；确认旧 URL 返回 404 且前端产物中不再包含旧入口。
2. 将批量发送迁移为独立服务并完成回归，确认历史任务仍可继续发送和签署。
3. 将九份源文件转换为禁用状态的系统候选模板。
4. 新增 `2-10` 模板类型和占位符支持。
5. 建立条件化方案绑定并发布受控版本；不使用 HR 文件夹编号作为路由码。

完成门：旧创建 URL 不存在、旧弹窗不进入前端产物、历史任务闭环不受影响；九份模板通过占位符、渲染、哈希和法务/HR 内容确认；所有自动化开关仍关闭。

### G1：Excel 持久化预览

1. 建表并实现 parser、匹配、数据来源优先级和校验。
2. 实现批次 GET、过期清理、创建人/签约范围隔离和 PII 脱敏。
3. 前端完成上传和逐行预览，不接生成。

完成门：当前 Excel 选中正式 27 人时恰好得到 27 个匹配和 1 个额外行警告；HR 主档零写入。

### G2：资料补充与 HR 审核

1. 实现资料补充任务、员工提交和 HR 审核。
2. System 实现字段白名单同步及幂等审计。
3. 同步成功后重新加载候选员工并重新匹配方案。

完成门：员工不能提交 HR 专属字段；审核前不能生成；审核通过后档案与签约快照一致。

### G3：生成、预览和发送

1. 导入快照转换为 `HrSignBusinessEvent`，调用现有规则和编排器。
2. 实现逐行事务、批次部分成功和重试。
3. 接入现有任务详情/PDF 预览及批量发送。

完成门：生成与发送完全分离；同一请求、双击、并发和重放均不得产生重复任务、签约包或通知。

### G4：完整签署闭环

1. 员工首次签署验证法律主体允许待确认。
2. HR 选择主体和印章生成最终合同。
3. 员工逐份确认最终合同后进入 `SIGNED`。
4. 验证最终 PDF、根哈希、单文档哈希和审计事件。

完成门：公司/印章变更会生成新版本并重置最终确认；旧版本不能错误解锁新版本。

### G5：灰度上线

1. 由保护发布系统对同一候选执行单一 `--readiness`，只有仓库外签名审批、源边界、证据和制品来源/语义全部通过才允许继续。
2. 默认关闭，只对白名单 HR 和已审批的一个签约范围启用。
3. 先跑 3 人，至少覆盖劳动合同、劳务合同和员工补资料流程。
4. 再跑当前正式 27 人。
5. 通过后扩大到单批 100 人。

停止条件：错员工、错组织、错套餐、重复任务、错误主体、文档哈希异常、越权或 PII 泄漏任一发生即关闭新入口。

## 十、验证与受信发布流程

### 10.1 开发回归命令

后端：

```bash
./mvnw -pl erp-modules/erp-oa -am test
./mvnw -pl erp-modules/erp-system -am test
```

前端：

```bash
npm --prefix erp-ui test
npm --prefix erp-ui run build:prod
```

模板：

```bash
/Users/liuxingyu/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/bin/python3 \
  scripts/test_prepare_onboard_templates_20260718.py
```

发布门禁：

```bash
bash scripts/verify-onboard-contract-excel-release.sh --static
bash scripts/verify-onboard-contract-excel-release.sh --readiness
```

`verify-contract-signing-release.sh` 仅对应冻结的 20260716 Release A，不与本次 20260718 开发工作区串联执行。本节不固定书写测试数量；实际结果以当次清洁候选和保护 CI 的可追溯输出为准。

### 10.2 仓库内 manifest 不是审批

`scripts/onboard-contract-excel-release-20260718.json` 只能作为仓库内跟踪的不可变 `development` 源合同。它定义 allowlist、删除清单、迁移账本、默认开关、先决 Release A 要求和外部完成门，但不得在同一个候选提交中被改成 `ready` 或用仓库内哈希自证。Source Approval 必须对从 HEAD 读取的该 manifest 原始字节 SHA-256 审批，不得重序列化后替代。

最终 ready 只能由仓库外两份 JSON 及其 OpenSSL SHA-256 detached signature 绑定：

- **Source Approval**：绑定 Release A ref/commit/ready manifest SHA-256、candidate commit、实际 approved patch SHA-256、approved source manifest SHA-256 和审批身份/时间。
- **Build Attestation**：绑定 Source Approval SHA-256、同一源边界、OA/System JAR 和前端 dist 的路径/哈希、实际脱敏证据索引路径/哈希、7 项外部门、构建工具链和构建/审批时间。

两份 JSON 必须由同一受信私钥签名并由同一受信公钥验证。私钥绝不进仓库、不进制品、不进日志；受信公钥文件也从仓库外提供，而公钥原始字节 SHA-256 必须由保护 CI/发布系统经独立信任通道注入。普通开发者在本地设置环境变量，即使值格式正确，也不代表获得审批。

校验器实际读取且仅读取以下 6 个环境变量：

- `ONBOARD_CONTRACT_EXCEL_SOURCE_APPROVAL`
- `ONBOARD_CONTRACT_EXCEL_SOURCE_APPROVAL_SIGNATURE`
- `ONBOARD_CONTRACT_EXCEL_BUILD_ATTESTATION`
- `ONBOARD_CONTRACT_EXCEL_BUILD_ATTESTATION_SIGNATURE`
- `ONBOARD_CONTRACT_EXCEL_TRUSTED_SIGNER_PUBLIC_KEY`
- `ONBOARD_CONTRACT_EXCEL_TRUSTED_SIGNER_SHA256`

前五项是仓库外绝对文件路径，最后一项是由保护发布系统锁定的小写 SHA-256。

### 10.3 三份制品必须逐字绑定源来

必须在 Source Approval 后，从其绑定的干净 candidate commit 和 patch 全量构建 OA JAR、System JAR 和前端 dist，不得用增量 class 覆盖或未受控的旧 dist。

三份制品必须逐字嵌入并反馈相同的：

- `candidate commit`
- `approved patch SHA-256`
- `approved source manifest SHA-256`

OA/System JAR 必须在唯一 build-info 中提供 `build.commit`、`build.releaseId`、`build.approvedPatchSha256` 和 `build.approvedSourceManifestSha256`；前端必须在唯一 `release-provenance.json` 与实际可达、可执行的 JS 中提供同样的值。任一缺失、`UNSET`、不一致或与签名证明不符都阻断发布。

实际构建必须使用发布说明中的专用命令：Maven 显式传入 `-Dbuild.commit`、`-Dbuild.releaseId=onboard-contract-excel-20260718`、`-Dbuild.approvedPatchSha256`、`-Dbuild.approvedSourceManifestSha256`；前端显式传入同一组 `VUE_APP_BUILD_*` 值并执行 `npm --prefix erp-ui run build:onboard-contract`。普通 `build:prod` 不生成本发布要求的 `release-provenance.json`，不能作为候选制品。实际 7 门证据写入 Git 忽略、无符号链接的仓库内输出目录，不覆盖 tracked pending 模板。

### 10.4 单一 `--readiness` 是唯一最终决策点

不得把多次手工子检查拼接成“已 ready”结论。单一 `--readiness` 必须对同一候选、同一组签名证明和同一组制品，一次性完成：

1. Release A 受保护 ref/commit/ready manifest 及哈希校验。
2. HEAD 中源 manifest/清单字节、候选 commit、工作树清洁度、diff 边界和实际 patch SHA-256 校验。
3. 受信公钥哈希、Source Approval/Build Attestation detached signature 和两者交叉绑定校验。
4. 实际脱敏证据索引、每个证据文件和 7 项 `passed` 门的字段/哈希校验。
5. Build Attestation 锁定的制品路径/哈希、JAR 类/路由、前端可达脚本、旧入口零残留和三份制品内嵌来源校验。
6. 对已读取的源、签名证明、证据和制品做稳定性复核，防止校验期间替换。

任一步缺失或不一致都必须失败关闭，统一使用退出码 `5`。

### 10.5 当前验证状态

- 开发回归、模板静态契约、Docker SQL 顺序/副本一致性和 20260718 静态发布门禁已执行；精确测试计数以当次 CI 记录为准，不在本方案写死。
- 旧入职 preview/initiate 运行时引用扫描为空，批量发送接口仍存在。
- 当前未具备可验证的 Release A tag/commit/ready manifest 绑定，未提供仓库外 Source Approval 及 Build Attestation 的 JSON/detached signature，未生成逐字嵌入已审批来源的重构建 OA/System JAR 和前端 dist，7 项外部证据仍为 pending。
- 因此 `--readiness` 当前必须以退出码 `5` 阻断。此前绝不得开启签约开关、执行本次生产迁移、部署候选制品或发送真实合同。
- 生产真实 Excel、真实模板 PDF、签名/印章逐页位置和最终审计证据仍须按 G5 在隔离 UAT 与灰度环境验收。

## 十一、验收清单

- [x] 旧入职 preview/initiate 接口和前端弹窗已删除，历史数据和完整签署闭环仍可用。
- [ ] 当前 Excel 的 27 个正式员工唯一匹配，实验行不生成任务。
- [x] Excel 不批量修改员工档案。
- [x] 员工补交个人事实后必须经过 HR 审核才能同步档案。
- [x] 员工不能修改劳务类型、保险、薪资、合同日期、职级或法律主体。
- [x] 缺地址时不使用身份证地址兜底。
- [x] 历史补签保留原合同日期和真实电子签署时间。
- [x] 薪资数学错误硬阻断，区间异常经 HR 留痕后可继续。
- [x] `2-9` 对所有 7 级及以上员工生效。
- [x] `2-10` 只对 16—18 周岁且非在校员工生效。
- [x] 法律主体允许首次签名后再选择，但最终确认前必须确定主体和有效印章。
- [x] 一键生成不自动发送；支持单发、发送所选和一键发送全部可发送项。
- [x] 同一批次和同一员工的预览、生成、发送重放具备应用 CAS、同源事件幂等和数据库唯一门禁。
- [ ] 每份初始/最终 PDF 均可下载并验证 SHA-256。
- [ ] 审计事件能够还原文件版本、员工阅读、两次确认、HR 审核、主体、印章和操作时间。

未勾选项必须使用当前真实 27 人文件或隔离 UAT 假数据、已审核并发布的九份模板及真实渲染服务完成，不允许仅凭单元测试改为通过。

## 十二、已知风险与处置

- `2-9` 正文提到《劳动合同》，但业务已明确要求所有 7 级及以上员工均加入且不改条款；模板版本和发布记录必须明确记录该业务决定。
- 原始模板含固定公司名称，只能使用占位符候选版本，不能直接启用源 DOCX。
- 员工资料补充跨 OA/System 两个服务，必须使用字段白名单、幂等请求和重读事实，不能依赖分布式事务假成功。
- 当前工作区变更较多，实施和发布必须严格使用签约 allowlist，不能将无关业务域一起打包。
- 如果未来要求第三方 CA 电子签、实名认证或第三方签署证书，应单独立项；本方案不扩张当前内部签署的法律能力描述。

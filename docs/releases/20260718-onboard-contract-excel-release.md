# 20260718 入职合同 Excel 一键签约发布说明

> 当前状态：`development`
> 发布类型：20260716 签署闭环之上的人工分阶段增量
> 生产状态：未就绪、未部署、未开启、未执行迁移、未发送真实合同

## 发布结论

本发布新增“HR 选人 + `签约数据` Excel + 条件套餐”的入职签约入口，并继续使用已有任务、签约包、阅读、手写签名、HR 选法律主体和印章、最终确认、PDF、哈希与审计闭环。

旧 `onboard/batch/preview` 和 `onboard/batch/initiate` 入口已取消，历史任务和证据不删除。现有 `POST /oa/signTask/batch/send` 继续保留。

## 不可变源合同与信任边界

`scripts/onboard-contract-excel-release-20260718.json` 是仓库内跟踪的不可变发布源合同，它必须始终保持 `development`/待审批语义。它只声明源码边界、删除边界、迁移顺序、默认开关和外部完成门，不允许通过把仓库内 manifest 改成 `ready` 来自证审批。

最终 ready 只能由仓库外的两份 JSON 及其各自的 OpenSSL SHA-256 detached signature 证明，两份必须由同一个受信公钥验证：

- **Source Approval** 绑定 Release A ref/commit/manifest SHA-256、candidate commit、approved patch SHA-256、仓库内源 manifest 的原始字节 SHA-256、审批人/角色/时间和 `containsPii=false`。
- **Build Attestation** 绑定 Source Approval 原始字节 SHA-256、同一组源标识、OA/System JAR 与前端 dist 的路径和哈希、实际脱敏证据索引的路径和哈希、7 项全部通过的外部门、构建工具链与构建/审批时间。

签名私钥绝不得进入仓库、候选制品或普通 CI 日志。受信公钥文件也从仓库外提供；其 SHA-256 必须由保护 CI/发布系统通过与候选代码独立的信任通道注入。开发者在本地设置同名环境变量不构成审批。

就绪门只接受以下 6 个实际环境变量：

- `ONBOARD_CONTRACT_EXCEL_SOURCE_APPROVAL`
- `ONBOARD_CONTRACT_EXCEL_SOURCE_APPROVAL_SIGNATURE`
- `ONBOARD_CONTRACT_EXCEL_BUILD_ATTESTATION`
- `ONBOARD_CONTRACT_EXCEL_BUILD_ATTESTATION_SIGNATURE`
- `ONBOARD_CONTRACT_EXCEL_TRUSTED_SIGNER_PUBLIC_KEY`
- `ONBOARD_CONTRACT_EXCEL_TRUSTED_SIGNER_SHA256`

前五个文件路径必须是仓库外的绝对路径；最后一个是受信公钥原始字节的小写 SHA-256。

## 发布边界

- 发布源合同：`scripts/onboard-contract-excel-release-20260718.json`
- 精确源码清单：`scripts/onboard-contract-excel-release-files-20260718.list`
- 迁移账本：`scripts/onboard-contract-excel-migrations-20260718.list`
- 混合文件复核清单：`scripts/onboard-contract-excel-mixed-paths-20260718.list`
- 旧入口删除清单：`scripts/onboard-contract-excel-deleted-paths-20260718.list`
- 静态门禁：`scripts/verify-onboard-contract-excel-release.sh --static`
- 单一就绪编排：`scripts/verify-onboard-contract-excel-release.sh --readiness`
- 签名证明校验：`scripts/verify_onboard_contract_excel_attestations.py`
- 不可变制品语义/来源扫描：`scripts/verify_onboard_contract_excel_artifacts.py`
- 七类证据 v2 业务结构校验：`scripts/verify_onboard_contract_excel_gate_evidence.py`
- 待完成脱敏证据模板：`docs/releases/evidence/20260718-onboard-contract-excel-evidence.json`

当前工作区包含其他业务修改，不允许直接归档或构建。必须先在干净候选分支按精确清单重组功能 hunk，由受信流程锁定 Release A 基线和候选 patch。候选 diff 一旦超出精确源码清单与删除清单，`--readiness` 必须失败。

Release A 不能只用一个文本 release ID 声明代替。正式候选必须将 `refs/tags/contract-signing-release-a-20260716`、对应 commit、该 commit 内状态为 `ready` 的 Release A manifest 及其 SHA-256 完整绑定；任意祖先 commit、空 diff 或未完成的 Release A 均不能通过。

## 构建来源与不可变制品

禁止用增量 class 覆盖代替完整构建。必须从 Source Approval 绑定的 candidate commit 和 patch 在干净环境全量构建 OA JAR、System JAR 和前端 dist。

三份制品必须逐字嵌入并一致绑定：

- `candidate commit`
- `approved patch SHA-256`
- `approved source manifest SHA-256`

OA/System JAR 通过 Spring Boot build-info 提供 `build.commit`、`build.releaseId`、`build.approvedPatchSha256` 和 `build.approvedSourceManifestSha256`；前端同时在 `release-provenance.json` 和可执行且可达的 JS 中提供完全相同的值。任一缺失、`UNSET`、不一致、哈希不匹配、旧类/旧路由残留或文件在校验中变更，都必须失败关闭。

当前 `docker/erp/modules/oa/jar/erp-modules-oa.jar` 是过期制品，内含 20260717 旧入口类和 `preview/initiate` 路由，不得部署。

### 干净候选的专用构建命令

以下命令只能在 Source Approval 已签署且 `HEAD` 等于其中 `candidateCommit` 的干净候选工作树执行。变量值必须逐字取自已验签的 Source Approval；手工填写同名变量不构成审批。

```bash
./mvnw -pl erp-modules/erp-oa,erp-modules/erp-system -am clean package \
  -DskipTests \
  -Dbuild.commit="$CANDIDATE_COMMIT" \
  -Dbuild.releaseId=onboard-contract-excel-20260718 \
  -Dbuild.approvedPatchSha256="$APPROVED_PATCH_SHA256" \
  -Dbuild.approvedSourceManifestSha256="$APPROVED_SOURCE_MANIFEST_SHA256"

cp erp-modules/erp-oa/target/erp-modules-oa.jar \
  docker/erp/modules/oa/jar/erp-modules-oa.jar
cp erp-modules/erp-system/target/erp-modules-system.jar \
  docker/erp/modules/system/jar/erp-modules-system.jar

VUE_APP_BUILD_COMMIT="$CANDIDATE_COMMIT" \
VUE_APP_BUILD_TIME="$BUILD_TIME_ISO8601" \
VUE_APP_BUILD_RELEASE_ID=onboard-contract-excel-20260718 \
VUE_APP_SIGN_EXCEL_IMPORT_ENABLED=true \
VUE_APP_BUILD_APPROVED_PATCH_SHA256="$APPROVED_PATCH_SHA256" \
VUE_APP_BUILD_APPROVED_SOURCE_MANIFEST_SHA256="$APPROVED_SOURCE_MANIFEST_SHA256" \
npm --prefix erp-ui run build:onboard-contract

rsync -a --delete erp-ui/dist/ docker/nginx/html/dist/
```

`build:prod` 不会生成本发布要求的 `release-provenance.json`，不能代替 `build:onboard-contract`。复制前应保留旧制品的可恢复备份；上述复制/同步只允许发生在隔离候选制品区，不得直接覆盖生产目录。

前端入口是编译期开关：源码环境默认仍为 `false`，但用于隔离 UAT、受批单范围灰度和后续发布的专用签名候选制品必须显式以 `VUE_APP_SIGN_EXCEL_IMPORT_ENABLED=true` 构建。`release-provenance.json` 与可达 JS 都必须记录 `signExcelImportEnabled=true`；OA 后端仍是最终授权门，非选定 `SHOP_DEPT` 不能因前端已编译显示能力而绕过范围隔离。

7 项实际证据索引及其文件应写入仓库内、Git 忽略且无符号链接的专用输出目录，再由 Build Attestation 绑定其路径和哈希。不得覆盖仓库内的 pending 证据模板，否则干净工作树门禁会阻断。

### 脱敏证据 v2 契约

实际证据不能只写“已通过”。索引必须使用 `schemaVersion=2`，并同时绑定 candidate commit、approved patch、source manifest、Source Approval、OA JAR、System JAR 和前端 dist 的哈希。每个证据文件必须使用对应的独立审批角色、指定环境和精确结果结构：

- 原生 MySQL：只接受真实 MySQL `5.7.x`/`8.0.x` 版本，两个迁移的固定顺序/哈希、4 个必需对象和 12 个必需索引、至少 18 项通过断言、零失败与演练根哈希，由 `DBA_RELEASE_APPROVER` 在 `isolated-mysql` 审批。
- 模板 HR/法务审批：9 份 `20260718-v5-draft` 文件的名称、类型、大小与哈希，HR 与法务必须为不同审批人，由 `HR_LEGAL_APPROVER` 在 `approval-workflow` 审批。
- 模板登记/方案发布：9 份模板全部为有效 `ENABLED` 版本。生产候选只接受 5 个全局 HR 方案（`shopDeptId=0`），路由精确为 A4/A5/A1/B1/B3；每个方案必须绑定规范化合同类型、社保类型、`2-4/5-6/7-9` 等级段、rule snapshot 哈希、唯一 plan version 哈希和本次 template ID。B1/B3 只保留标准化的 `STUDENT_INTERN/RETIRED_REHIRE`、`COMMERCIAL_ACCIDENT/EMPLOYER_LIABILITY` 代码和由这些脱敏字段可重算的规则 SHA-256，不保留姓名或原始字段。必须同时证明 5 个目标路由都已启用，且路由冲突、非全局活跃方案、额外全局路由和无效/冲突规则均为 0。A3 仅允许作为 `runtimeEnvironment=isolated-uat`、`ISOLATED_UAT_ONLY`、`productionEligible=false` 的第 6 个隔离方案；生产快照中 A3 必须为 0，生产与隔离 UAT 的内容快照哈希必须可重算且不同。由 `SIGN_TEMPLATE_RELEASE_APPROVER` 在字面环境 `staging/isolated-uat` 审批。
- 三人隔离 UAT：必须先证明同一哈希的 OA/System JAR 实际启动且健康探针通过，前端 dist 已服务、bundle 加载、浏览器流程通过且 console 零错误。劳动、劳务、员工补资各 1 个合成数据案例；7 级劳动案例必须命中隔离 A3 方案，7 级劳务案例必须命中生产候选 B3 方案，两者都必须包含 `ONBOARD_CONFIDENTIAL_NONCOMPETE`；劳动合同必须精确命中 HR 在方案中人工选择的 A/B 薪酬版本，且修改社保口径不得自动改变薪酬版本。员工补资案例还要真实触发 `2-10`，证明条件判定、个人收入开始年月的 HR 审核、档案同步和重新匹配。每份实际模板必须与预期的 template ID、version、文件名、type 和源 SHA-256 逐项相等。三案例均需完成初始阅读/手写签名、HR 选主体与有效印章、最终逐份确认并到达 `SIGNED`，且劳务身份不可由员工编辑，由 `QA_BUSINESS_APPROVER` 在 `isolated-uat` 审批。
- 27 人工作簿 UAT：必须是 27 条唯一匹配、0 未匹配、0 歧义、0 外部合同冲突、27 生成/发送成功并排除 1 条实验数据。路由分布必须精确为 `A4=19、A5=5、A1=1、B1=1、B3=1`，并与 5 个全局生产 plan ID/version/rule/scope/哈希逐路由绑定；预览、生成和发送全程的 plan version 锁定数必须为 27、版本漂移数必须为 0。发布方案的 template ID 是候选集，文档数必须由每行实际 `resolvedTemplateSet` 汇总：无条件基数固定为 107，最终精确为 `107 + minorNonstudentPlacementCount`，27 行都必须完成条件判定和解析模板集校验。工作簿原文只留存标明 `HMAC-SHA256` 算法且 key ID 精确为 `onboard-hr-qa-workbook-hmac-20260718-v1` 的 HMAC，密钥不进仓库/证据；条件命中、B1/B3 标准化审核结果和全部聚合结果必须按固定 JSON 编码重算 SHA-256 根。证据不携带工作簿或行明细，由 `HR_QA_BATCH_APPROVER` 在 `one-scope-gray` 审批。
- 最终 PDF/哈希/审计：27 个签约包全部 `SIGNED` 且每包均有已校验文档，最终 expected/document/verified 数量必须与 27 人门中的 expected/generated 数量完全相等。文档哈希、包根哈希、审计链及规定事件/证据类型全部齐备，缺文件、哈希不一致、缺必需事件或证据的数量均为 0，不包含 PDF 或审计 payload 原文，由 `SIGN_EVIDENCE_AUDITOR` 在 `one-scope-gray` 审批。
- 单范围灰度：只能启用 1 个 `SHOP_DEPT` 范围，OA/前端 Excel 入口为开，生命周期、应急创建、过期和提醒自动化均为关，必须证明回退开关可用且设置未过期的生效/到期时间。三人隔离 UAT 必须先完成，27 人发送和最终证据必须在已批准灰度窗口内完成，由 `RELEASE_SCOPE_APPROVER` 在 `one-scope-gray` 审批。

索引、七份证据和 Build Attestation 之间的时间、哈希和源标识必须交叉一致，且所有 JSON 均禁止额外字段、重复键和原始 PII。

## 单一 `--readiness` 编排

`--readiness` 是唯一最终决策点，必须在同一次失败关闭的编排中完成：

1. 验证 Release A 受保护 ref/commit/ready manifest 及其哈希。
2. 从 HEAD 读取不可变 `development` 源 manifest 及精确清单，校验候选 commit、干净工作树、diff 边界和实际 patch SHA-256。
3. 使用仓库外受信公钥依次验证 Source Approval 和 Build Attestation 的 detached signature 及交叉绑定。
4. 核对实际脱敏证据索引及每个证据文件的哈希、主题、审批角色、时间、环境和无 PII 状态，并使用 v2 业务结构证明 7 项门的实际数量、结果根、必需事件与安全开关精确通过。
5. 核对 Build Attestation 绑定的三份制品路径/哈希，并在不执行候选业务代码的前提下，对 Java 17/Spring Boot 结构、控制器类/方法注解、权限、前端可执行可达脚本和三份制品内嵌来源做静态结构校验。实际可运行性必须由绑定同一制品哈希的隔离启动、健康探针和浏览器 UAT 证据证明，静态 parser 本身不声称完整 JVM 验证。
6. 对签名证明、源合同、证据和制品做最终稳定性复核，防止校验期间被替换。

任意一步未完成或不匹配，统一以退出码 `5` 阻断。不得绕过单一编排分别手工执行子校验后声称 ready。

## 迁移顺序

1. 只读核对 20260716 Release A 表、列、索引和已执行账本，不重跑旧迁移。
2. 备份 `oa_sign_task`、`oa_sign_package`、`sys_user_profile`。
3. 人工执行 `erp_oa_sign_onboard_import_20260718.sql`。
4. 人工执行 `erp_system_sign_profile_supplement_20260718.sql`。
5. 核对两项 SHA-256、三张导入表、档案补资审计表、开放 ONBOARD 任务唯一索引。
6. 两项都成功后才部署 System/API、OA 和前端。

Docker 初始化清单已保证 `erp_oa_sign_plan_20260706.sql` 早于 OA 导入迁移、`erp_user_employee_profile_20260706.sql` 早于 System 补资迁移，避免新库在前置表或列尚未存在时失败。

## 开关与回退

下列默认均为 `false`：

- `oa.sign.excel-import.enabled`
- `VUE_APP_SIGN_EXCEL_IMPORT_ENABLED`
- `hr.sign.lifecycle-automation.enabled`
- `oa.sign.emergency-create.enabled`
- `oa.sign.expiry.enabled`
- `oa.sign.reminder.enabled`

首要回退动作是关闭 OA Excel 入口并隐藏前端入口。已生成的任务、导入批次、文件、签名、印章、哈希和审计不删除。

## 模板制品

9 份 `20260718-v5-draft` 候选 DOCX 不属于应用代码包，其文件名、大小和 SHA-256 已固定在源 manifest。当前全部为 `disabled`，没有生成自动注册载荷。HR/法务必须按 `docs/runbooks/beijing-sign-template-plan-ui-publish-20260718.md` 审批、登记、发布和复核方案绑定，禁止猜测模板 ID。

## 当前阻断与外部完成门

当前以下必备条件均未具备：

- 经保护且可验证的 Release A tag/commit/ready manifest 绑定。
- 仓库外 Source Approval JSON 及 detached signature。
- 仓库外 Build Attestation JSON 及 detached signature。
- 从已审批候选源全量重构建、嵌入来源且哈希锁定的 OA/System JAR 和前端 dist。
- 原生 MySQL 隔离迁移演练证据。
- 模板 HR/法务审批及登记/方案发布证据。
- 劳动、劳务、员工补资的隔离 UAT 证据。
- 真实工作簿 27 人唯一匹配和实验数据排除证据。
- 初始/最终 PDF、单文档哈希、根哈希、阅读、签名、HR 审核、主体、印章和两次确认审计证据。
- 一个签约范围灰度开启审批。

仓库内 `docs/releases/evidence/20260718-onboard-contract-excel-evidence.json` 只是 `pending` 且 `containsPii=false` 的模板，不是通过证据；不得伪造 evidence ID、哈希、主题、审批人/角色、时间或环境。

因此当前 `--readiness` 必须以退出码 `5` 失败关闭。在全部条件通过并由受信发布流程重新验证前，绝不得广泛开启签约开关、执行本次广泛生产迁移/部署或超出受批准范围发送真实合同。为产生门禁证据所必需的隔离 UAT 及明确受批准的单 `SHOP_DEPT` 灰度，只能由受信发布流程在证据契约限定内执行，不得由开发环境自行解锁。

任一错员工、错范围、错套餐、重复任务、错法律主体、哈希异常、越权或 PII 泄漏，立即关闭新入口。

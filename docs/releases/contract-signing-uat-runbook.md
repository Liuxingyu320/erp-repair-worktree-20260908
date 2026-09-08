# 合同签约 Release A 真实 UAT 执行手册

本手册对应 `contract-signing-release-a-20260716`。脚本只提供可重复的预检、API 探针和证据门禁；它们不会创建默认管理员、不会创建法务候选模板，也不会默认向数据库写入。

## 1. 不可放宽的验收前提

- 只允许使用可丢弃的隔离 UAT 数据库，`productionDataCopied` 必须为 `false`；禁止复制生产合同、签名图、印章文件或员工敏感值。
- 必须有组织 A/B、五个虚构员工、五场景正式候选方案版本、法律主体和在有效期内且哈希一致的印章。每个法律主体均需有 `LABOR` 和 `SERVICE` 候选合同与法务审批编号。
- 组织 A/B 各使用一个 `sign_single_hr` 专用 HR 账号。账号必须是非管理员，且不能拥有 `oa:signTask:technicalEvidence`。技术证据查看必须由另一个非 HR 账号执行。
- 另准备一个非管理员 `legacyPackageOnly` 账号：它必须保留 OA 父菜单 3000、旧签约包菜单 4520～4526 和 `/oa/sign-package`，但不得拥有任务中心根 9650、`sign_single_hr` 或 `/oa/sign-task`。仅因拥有 `oa:signPackage:*` 绝不能获得 9650。
- 所有预检/API/浏览器/MySQL 汇总及最终证据必须由独立验收签字人使用仓库外私钥生成 detached signature；校验端只接收仓库外公钥及由发布负责人独立固定的公钥文件 SHA-256。源码、配置或证据 JSON 自己声明的公钥不能建立信任。
- 管理员账号、超级权限或“先给全权限再验收”均不能作为成功证据。配置加载器和最终证据校验器都会拒绝管理员角色、`*:*:*` 或 HR/技术证据混授。
- 如生产目标仍为 MySQL 5.7，必须在获授权的非虚拟化 MySQL 5.7 验收库双跑迁移并执行核心查询。MySQL 8、Docker 或静态兼容检查不能替代该门禁。

## 2. 准备仓库外登录状态

九个角色必须分别登录并保存不同的 Playwright `storageState`。登录由验收人在受控浏览器中完成，配置和脚本不接收密码、Token 或 Cookie。

```bash
mkdir -m 700 /secure/erp-contract-uat

# 使用已登录的 Playwright 会话保存，以下仅为路径示例
playwright-cli -s=contract-hr-a state-save \
  /secure/erp-contract-uat/dedicated-hr-org-a.json
chmod 600 /secure/erp-contract-uat/dedicated-hr-org-a.json
```

对 `dedicatedHrOrgA`、`dedicatedHrOrgB`、`technicalEvidenceReviewer`、`legacyPackageOnly` 和五个场景员工重复操作。状态文件必须位于仓库外、权限为 `0600`，且包含对应 UAT 域名的唯一 `Admin-Token` Cookie。

## 3. 填写配置并进行五重环境确认

```bash
cp scripts/qa/contract-signing-uat.example.json \
  scripts/qa/contract-signing-uat.local.json
```

把示例中的全零提交/哈希、`REPLACE_WITH_PATCH`、账号状态路径、组织/员工/任务/签约包编号和法务审批编号替换为真实 UAT 值。`expectedEnvironmentId`、数据库指纹和可信签字公钥指纹由发布负责人从独立渠道提供，不能从身份接口响应复制后再填写。配置文件可以记录假数据编号和哈希，但不能记录员工姓名、电话、证件、合同正文或签名图。

独立签字人把私钥保存在仓库外；验证端只取得公钥。以下命令是一次性 UAT 密钥示例，正式执行应由独立签字人完成并按组织密钥流程保管：

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 \
  -out /secure/erp-contract-uat/attestation-private.pem
openssl pkey -in /secure/erp-contract-uat/attestation-private.pem -pubout \
  -out /secure/erp-contract-uat/attestation-public.pem
chmod 600 /secure/erp-contract-uat/attestation-private.pem
chmod 644 /secure/erp-contract-uat/attestation-public.pem
```

```bash
export ERP_CONTRACT_SIGN_UAT_RUN_ID=contract-sign-uat-001
export ERP_CONTRACT_SIGN_UAT_DATABASE=erp_uat_contract_signing_20260716
export ERP_CONTRACT_SIGN_UAT_ENVIRONMENT_ID=contract-signing-uat-a
export ERP_CONTRACT_SIGN_UAT_DATABASE_FINGERPRINT_SHA256='<独立固定的数据库指纹>'
export ERP_CONTRACT_SIGN_UAT_TRUSTED_SIGNER_SHA256="$(shasum -a 256 \
  /secure/erp-contract-uat/attestation-public.pem | awk '{print $1}')"
export ERP_CONTRACT_SIGN_UAT_ALLOWED_ORG_IDS=910001,920001
export ERP_CONTRACT_SIGN_UAT_COMMIT="$(git rev-parse HEAD)"
export ERP_UAT_APPROVE_BASE_URL=https://erp-contract-uat.example.test
```

上述八个环境变量必须与 JSON 逐字符相等。目标 URL 必须是回环地址，或使用 HTTPS 且域名明确含 `qa/uat/test/stage/dev`。

`environmentIdentityProbe` 必须是需要登录态的只读 `GET`。服务端从实际运行环境返回下列结构；不能读取请求参数后原样回显：

```json
{
  "code": 200,
  "data": {
    "environmentId": "contract-signing-uat-a",
    "database": "erp_uat_contract_signing_20260716",
    "databaseFingerprintSha256": "<真实运行库指纹>",
    "releaseId": "contract-signing-release-a-20260716",
    "runId": "contract-sign-uat-001",
    "candidateCommit": "<40位提交>",
    "isolated": true,
    "productionDataCopied": false
  }
}
```

写模式会在任何写探针之前调用该接口并逐字段、逐类型比较独立期望；接口不可用、任一值不符、3xx 跳转或 HTTP 5xx 都会在首个业务写之前失败。

## 4. 只读预检

```bash
python3 scripts/qa/prepare_contract_signing_uat.py \
  --config scripts/qa/contract-signing-uat.local.json \
  --output output/uat/contract-signing-preflight.json
```

预检会验证角色隔离、两组织、法律主体双合同类型、五场景方案/模板哈希、10 条业务浏览器旅程、旧签约包角色边界、写前身份探针、API 用例和 15 类异常用例。它只在已忽略的 `output/` 下写入脱敏 JSON，`writesExecuted` 始终为 `false`。

## 5. 五场景人工成功链

五个场景必须分别完成一次，不能用同一个已签包的截图重复代替：

| 场景 | 代码 | 必须的业务链 |
| --- | --- | --- |
| 入职 | `ONBOARD` | 任务生成 → 补资料/校验 → 发送 → 员工逐文件加载/阅读 → 首签 → HR 选主体/印章 → 员工逐份读取最终合同 → 最终确认 → 下载/验真 |
| 转正 | `REGULARIZE` | 同上 |
| 调岗 | `TRANSFER` | 同上，并核对调岗前后快照 |
| 续签 | `RENEWAL` | 同上，并核对旧合同/新合同链接 |
| 离职 | `OFFBOARD` | 同上，并核对终止日和历史补录标记 |

桌面 HR 视口至少 `1280×720`；员工端每个场景都必须使用 `390×844`。每条员工旅程记录以下结果：控制台错误为 0、弹窗关闭后焦点返回、文件加载失败有明确提示、刷新/重登后深链可恢复。截图和快照只允许包含虚构 UAT 数据，保存到 `output/playwright/contract-signing/`。

## 6. API 证据：默认只读，写入需双重授权

先执行只读探针：

```bash
python3 scripts/qa/run_contract_signing_api_uat.py \
  --config scripts/qa/contract-signing-uat.local.json \
  --output output/uat/contract-signing-api-read-only.json
```

默认模式会跳过全部 `POST/PUT/PATCH/DELETE`，并在证据中记录 `writeMode=false` 和 `skippedWriteCount`。该产物只用于早期检查，不能通过发布证据门禁。

完成变更窗授权、确认所有写探针只指向可丢弃数据后，由当次验收人设置单次授权：

```bash
export ERP_CONTRACT_SIGN_UAT_WRITE_APPROVAL="$ERP_CONTRACT_SIGN_UAT_RUN_ID"
python3 scripts/qa/run_contract_signing_api_uat.py \
  --allow-write \
  --config scripts/qa/contract-signing-uat.local.json \
  --output output/uat/contract-signing-api-write.json
unset ERP_CONTRACT_SIGN_UAT_WRITE_APPROVAL
```

完整 API 产物必须为 `writeMode=true`、`environmentIdentityVerified=true`、`skippedWriteCount=0`、`failedCount=0`，并覆盖专用 HR 权限、旧签约包角色仍可访问包接口且被任务接口拒绝、跨组织拒绝、五场景签完状态、拒签、过期、替代版本、通知失败/恢复、重复请求和最终文件哈希。带登录态的请求永不跟随跳转，任意 3xx 或 HTTP 5xx 都不能被计为通过。

并发重复探针除成功数边界外，还必须配置 `parallelSuccessIdentityPath` 和只读 `postconditionProbe`：所有成功响应返回同一个稳定 ID，随后按业务键查询得到 `count=1` 且同一 ID。仅配置 `maxSuccess` 或仅看到两个 200 不能证明幂等。明确列出的 HTTP 200/应用业务失败可按“重复/处理中”判断，但 HTTP 5xx 始终失败。证据只保留响应字节数、HTTP/应用状态、耗时、稳定 ID 的 SHA-256 和正文 SHA-256，不保留响应正文、请求正文、Token 或签名。

## 7. 异常、并发与故障注入

仅在获批的隔离环境执行下表操作。每次注入前记录固定夹具哈希，恢复后重验数据和 Outbox 唯一性；不能在生产或共享测试库中断网、改文件或改印章。

| 类别 | 必须证明 |
| --- | --- |
| 权限/主体 | 跨组织访问为 0；错误主体被拒绝；专用 HR 不能查技术证据 |
| 印章/模板 | 印章过期、哈希不一致、坐标越界均不产生最终文件 |
| 文件 | 源文件缺失/篡改均失败；新文件验真率 100% |
| 幂等/并发 | 重复点击、网络超时沿用原 `requestId`；并发过期扫描不产生重复任务/通知 |
| 通知 | 依赖不可用时业务事务不回滚；恢复后人工重入队成功且同业务键不重复 |
| 终态 | 拒签/过期不能原地复活；替代版本有新任务、新包和旧终态关联 |

`faultCases` 必须正好包含 15 类用例。任何一类没有真实执行证据时，不得把它标为 `passed`。

## 8. 制作脱敏证据产物

所有发布证据都保存在 `output/`，最终 JSON 仅引用这些文件的相对/绝对路径、SHA-256、检查数和脱敏结论。

### 8.1 浏览器汇总 JSON

`output/uat/contract-signing-browser.json` 必须包含：

- 顶层 `kind=contract-signing-browser-uat`、`status=passed`、`releaseId`、`runId`、`candidateCommit`、`trustedSignerPublicKeySha256`、带时区的 `completedAt` 和 `checkCount=11`。
- `journeys` 正好 10 条，即每场景各一条 `HR_DESKTOP` 和 `EMPLOYEE_MOBILE`。
- 每条都有与签字预检一致的 `id/scenario/mode/actor/organization`、`status=passed`、`consoleErrorCount=0`、`screenshotCount` 和 `screenshotManifest={path,sha256,byteCount}`。
- 员工条目还必须有 `viewport={"width":390,"height":844}`、`focusReturnPassed=true`、`deepLinkRecoveryPassed=true`、`loadFailureMessagePassed=true`。
- `legacyMenuBoundary` 是第 11 项：`menuTreeIds` 必须为 `[3000,4520,4521,4522,4523,4524,4525,4526]`，`packageRouteAccessible=true`、`taskRouteAccessible=false`，并有独立截图 manifest。

每个截图 manifest 必须是实际文件，固定 `kind/releaseId/runId/candidateCommit/journeyId`，并用 `files=[{path,sha256,byteCount}]` 引用至少一个实际截图。校验器会重算 manifest 和每个截图文件；只填写一个哈希字符串不能通过。manifest 只记录文件路径、大小和哈希；不要把签名图、PDF 正文或员工值复制到 JSON。

### 8.2 MySQL 5.7 汇总 JSON

`output/uat/contract-signing-mysql57.json` 必须包含匹配的发布/运行/提交号、`kind=contract-signing-mysql57-uat`、`status=passed`、`checkCount`，以及：

```json
{
  "engineVersion": "5.7.x",
  "virtualized": false,
  "migrationRunCount": 2,
  "migrationFailureCount": 0,
  "coreQueryCount": 1,
  "coreQueryFailureCount": 0,
  "migrationBundleSha256": "<64位非零哈希>",
  "authorizedBy": "<授权人>",
  "executionLog": {"path": "<实际日志>", "sha256": "<哈希>", "byteCount": 1},
  "executionAttestation": {"path": "<实际证明JSON>", "sha256": "<哈希>", "byteCount": 1},
  "legacyMenuBoundary": {
    "status": "passed",
    "actor": "legacyPackageOnly",
    "oaRootMenuId": 3000,
    "presentMenuIds": [4520, 4521, 4522, 4523, 4524, 4525, 4526],
    "absentMenuIds": [9650],
    "packageRoute": "sign-package",
    "packageComponent": "oa/signPackage/index",
    "taskRoute": "sign-task",
    "packageGrantCount": 7,
    "taskCenterRootGrantCount": 0,
    "databaseCheckCount": 2
  }
}
```

`executionLog` 必须是实际双跑迁移、核心查询和 4520～4526/9650 角色授权查询的 UTF-8 日志。`executionAttestation` 必须绑定同一发布、运行、提交、环境、数据库/迁移指纹、日志哈希和上述边界字段；汇总 JSON、日志或 attestation 任一被修改都会失败。它们均不能记录数据库地址、用户名、密码、连接串或 Token。

### 8.3 最终证据 JSON

最终文件例如 `output/uat/contract-signing-evidence.json`，必须包含：

- `environment`：MySQL 5.7 版本、数据库/迁移哈希、`organizations=["orgA","orgB"]`、`isolated=true`、`productionDataCopied=false`。
- 顶层还必须有独立固定的 `trustedSignerPublicKeySha256`，以及 `attestation={signaturePath,signerPublicKeySha256}`；签名文件对最终 JSON 的原始字节签字。
- `artifacts`：`preflight/api/browser/mysql57` 四个对象，每个包含 `path`、与文件匹配的 `sha256`、与产物匹配的 `checkCount`、`status=passed`、`signaturePath` 和 `signerPublicKeySha256`。四个 JSON 都必须由同一独立可信签字人 detached-sign；API 的 `checkCount` 等于 `probeCount`。
- `actors`：与配置中九个别名完全相同；每个有 `alias/kind/isAdministrator=false/organizations/roleKeys/permissions/permissionMatrixStatus=passed`，组织范围、角色键和权限必须与签字预检产物一致。`legacyPackageOnly.menuIds` 必须精确为 3000、4520～4526，不能含 9650。
- `fixtures.legalEntities`：每个法律主体有组织别名、`legalEntityId`、`sealId`、`contractTypes=["LABOR","SERVICE"]`、主体/印章指纹和 `status=passed`；编号与印章哈希必须和预检产物一致。
- `fixtures.plans`：正好五条，含场景、组织、方案版本编号/哈希、非空模板哈希、法务审批号和 `status=published`。
- `scenarios`：正好五条，`evidenceTypes=["api","browser"]`，且 `chainSteps` 完整包含任务生成、补资料/校验、发送、加载/阅读、首签、主体/印章、最终阅读/确认、下载/验真。每条还有任务/包指纹、文件验真数、逐页位置审查数、`wrongContractCount=0`、验收人和时间。
- `exceptionCases`：与配置中 15 个 `faultCases` 完全相同；每条有 `status=passed`、非空 `evidenceTypes`、正数 `checkCount`、审核人和时间。
- `metrics`：错误合同/重复任务/重复通知/技术证据混授/跨组织越权成功数全部为 0；新文件验真率、签名印章位置通过率、HR 业务权限通过率全部为 100；最终文件/验真数和位置页数与五场景汇总相等；`notificationRecoveryCount>0`。
- `signoff`：`hrOwner`、`legalOwner`、`qaOrBusinessOwner` 为三个不同的人，并含带时区的 `approvedAt` 和 `decision=approved`。

哈希可用下列命令生成：

```bash
shasum -a 256 output/uat/contract-signing-preflight.json
shasum -a 256 output/uat/contract-signing-api-write.json
shasum -a 256 output/uat/contract-signing-browser.json
shasum -a 256 output/uat/contract-signing-mysql57.json
```

独立签字人核对原始文件后，对四个产物签字；最终证据 JSON 先写入将要使用的 `.sig` 路径，再最后签字：

```bash
for file in \
  output/uat/contract-signing-preflight.json \
  output/uat/contract-signing-api-write.json \
  output/uat/contract-signing-browser.json \
  output/uat/contract-signing-mysql57.json \
  output/uat/contract-signing-evidence.json
do
  openssl dgst -sha256 \
    -sign /secure/erp-contract-uat/attestation-private.pem \
    -out "$file.sig" "$file"
done
```

修改任何已签 JSON 后必须由独立签字人重新审核并签字；仅重算 SHA-256 不会通过门禁。

## 9. 执行发布证据门禁

```bash
python3 scripts/qa/verify_contract_signing_evidence.py \
  --evidence output/uat/contract-signing-evidence.json \
  --candidate-commit "$(git rev-parse HEAD)" \
  --trusted-signer-public-key /secure/erp-contract-uat/attestation-public.pem \
  --trusted-signer-sha256 "$ERP_CONTRACT_SIGN_UAT_TRUSTED_SIGNER_SHA256"
```

校验器先用独立固定的公钥文件指纹和 detached signature 验证最终证据及四个产物，再重算截图 manifest/截图、MySQL 日志/attestation 和全部 SHA-256，并校验同一发布、同一 `runId`、同一候选提交和迁移哈希。缺少任一账号、场景、异常用例、旧角色 4520～4526/9650 边界、MySQL 5.7 原始证据或独立签字，均必须失败。

## 10. Release A UAT 完成标准

- 错误合同 0、重复任务 0、重复通知 0。
- 新文件验真率 100%，签名/印章位置已人工逐页通过。
- 两个专用非管理员 HR 完成各自组织业务权限，HR 技术证据混授和跨组织越权成功都为 0。
- 旧签约包专用角色保留 4520～4526 和包路由，同时数据库、菜单树、浏览器与 API 均证明其没有 9650 和任务路由。
- 五场景、拒签、过期、替代版本、通知失败恢复和其余异常用例全部有真实、可重算哈希的证据。
- 写模式身份绑定通过，所有带凭据请求无跳转，HTTP 5xx 为 0；重复业务键后置查询精确为 1。
- HR、法务、测试/业务代表三方为三个不同的人并批准。

任一项未达成时，结论只能是“Release A UAT 未通过”，不得用单元测试、管理员冒烟、MySQL 8 或手工修改证据 JSON 替代。

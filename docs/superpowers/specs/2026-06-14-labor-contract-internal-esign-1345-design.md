# 劳动合同内部线上签约增强方案（只做 1/3/4/5）

## 目标

在不接第三方电子签、不做短信/邮箱 OTP 的前提下，把现有劳动合同线上签约增强为“内部可审计、文件可校验、版本可冻结”的签署系统。

本方案只做：

1. 签署文件版本冻结。
3. 归档 PDF + 完成证明。
4. 审计事件链。
5. 验签/验 hash 页面。

明确不做：

- 不接腾讯电子签、法大大、契约锁、e签宝等第三方。
- 不做短信 OTP、邮箱 OTP、人脸、CA 实名认证。
- 不承诺达到第三方电子签平台同等级司法举证能力。

## 总体架构

保留现有 `internal` 签署模式，在 `erp-oa` 模块内部增加三个能力：

- `ContractSnapshot`：发送时冻结合同文本、模板、企业章、预览文件 hash。
- `ContractEvidence`：签署时基于冻结快照生成归档 PDF 和完成证明 PDF。
- `ContractVerification`：提供 hash 校验接口和管理端验签页面。

核心原则：员工签署时不再只说“我同意”，而是明确同意某一个 `documentVersion` 和 `previewHash`。签署完成后，系统保存归档 PDF、完成证明 PDF 和事件 hash 链。

## 数据库设计

### 1. 扩展 `oa_labor_contract`

新增字段：

- `document_version varchar(64)`：签署版本号，发送合同时生成，例如 `LC-100-20260614153022`。
- `preview_file_hash varchar(128)`：发送时预览文件 hash。
- `archive_file_hash varchar(128)`：签署后归档 PDF hash。
- `certificate_file_url varchar(500)`：完成证明 PDF 下载 URL。
- `certificate_file_hash varchar(128)`：完成证明 PDF hash。
- `template_file_hash varchar(128)`：发送时模板文件 hash。
- `seal_image_hash varchar(128)`：发送时企业章图片 hash。

保留现有 `contract_file_hash`，但语义调整为“当前最终归档文件 hash”；新代码写入时同步写 `archive_file_hash` 和 `contract_file_hash`，兼容旧页面。

### 2. 扩展 `oa_labor_contract_event`

新增字段：

- `document_version varchar(64)`。
- `document_hash varchar(128)`。
- `prev_event_hash varchar(128)`。
- `event_hash varchar(128)`。
- `request_id varchar(64)`。

事件 hash 计算建议：

```text

SHA-256(
  contractId + "|" +
  eventType + "|" +
  operatorId + "|" +
  clientIp + "|" +
  userAgent + "|" +
  documentVersion + "|" +
  documentHash + "|" +
  prevEventHash + "|" +
  createTime
)

```

这样可以形成最小可用的事件链，防止无痕改写事件顺序和关键内容。

## 后端设计

### 1. 发送合同时冻结版本

修改 `OaLaborContractServiceImpl.sendContract`：

- 读取合同、模板、企业章。
- 生成 `documentVersion`。
- 生成预览文件。
- 计算预览文件 hash、模板文件 hash、企业章 hash。
- 写入合同表。
- 记录 `send` 事件，事件中带 `documentVersion` 和 `previewHash`。

如果模板或企业章图片不存在，不允许发送。

### 2. 签署时校验版本

修改 `OaLaborContractSignRequest`：

- 新增 `documentVersion`。
- 新增 `previewFileHash`。

修改 `signContract`：

- 校验员工本人。
- 校验状态为 `pending_sign`。
- 校验 `request.documentVersion == contract.documentVersion`。
- 校验 `request.previewFileHash == contract.previewFileHash`。
- 校验当前磁盘预览文件 hash 仍等于 `contract.previewFileHash`。
- 校验签名图片格式和大小。
- 基于冻结版本生成归档 PDF 和完成证明 PDF。
- 写入 `archiveFileUrl`、`archiveFileHash`、`certificateFileUrl`、`certificateFileHash`。
- 记录 `sign` 事件。

如果 hash 不一致，拒绝签署，提示“合同文件已更新，请重新打开合同后签署”。

### 3. PDF 生成策略

当前项目只有 Apache POI，没有 PDF 库。建议新增轻量 PDF 生成能力，不做 DOCX 转 PDF。

推荐实现：

- 使用 `openhtmltopdf` 或同类 HTML-to-PDF 库。
- 新增 `OaLaborContractPdfService`。
- 基于合同快照数据渲染 HTML，再生成 PDF。

原因：

- 不依赖服务器安装 LibreOffice。
- 生成结果稳定，适合移动端预览。
- 完成证明 PDF 也可复用同一套 HTML-to-PDF。

生成文件：

- `preview.pdf`：员工签署前查看。
- `archive.pdf`：员工签署后归档，包含合同正文、企业章、员工签名图。
- `certificate.pdf`：完成证明，包含合同编号、员工、签署时间、IP、UA、文件 hash、事件列表。

保留 DOCX 生成可作为后台下载原件，但移动端和验签优先使用 PDF。

### 4. 审计事件

事件类型：

- `send`：管理员发送合同。
- `view`：员工打开合同详情时记录一次。
- `download`：下载合同或证明时记录。
- `sign`：员工提交签署。
- `void`：管理员作废合同。
- `verify`：管理员或用户执行 hash 验证。

避免过多噪声：

- `view` 事件同一用户同一合同 10 分钟内只记一次。
- `download` 事件每次下载都记。
- `verify` 事件每次校验都记。

### 5. 文件下载

扩展 `download/{contractId}/{kind}`：

- `preview`：下载预览 PDF。
- `archive`：下载归档 PDF。
- `certificate`：下载完成证明 PDF。
- `signature`：下载员工签名图片，仅管理端允许，员工端不单独暴露。
- `docx-preview`、`docx-archive`：如需保留 DOCX，可作为后台隐藏入口。

下载权限：

- 员工本人可以下载自己的 `preview/archive/certificate`。
- 管理端需有 `oa:laborContract:query` 且在店铺范围内。
- 非本人不得通过带 `Dept-NumId` 绕过员工校验。

## 前端设计

### 1. 移动端签署页

修改 `erp-ui/src/views/mobile/contract/index.vue`：

- 文件展示优先使用 `pdfFileUrl` 或新的 `previewPdfUrl`。
- 打开合同时保存 `documentVersion` 和 `previewFileHash`。
- 签署提交时带上 `documentVersion` 和 `previewFileHash`。
- 签署后展示归档 PDF、完成证明下载入口、最终 hash。

签署按钮文案保持简单：

```text
本人已阅读并确认签署此版本合同
```

### 2. 管理端劳动合同页面

修改 `erp-ui/src/views/oa/laborContract/index.vue`：

- “文件”按钮打开归档 PDF，未签时打开预览 PDF。
- 详情弹窗显示：
  - 签署版本
  - 预览 hash
  - 归档 hash
  - 完成证明 hash
  - 事件链
- 新增“验 hash”按钮。

### 3. 验 hash 页面/弹窗

优先做弹窗，不新增独立菜单：

- 输入/粘贴 hash。
- 或上传 PDF 文件后前端计算 SHA-256。
- 调用后端 `/oa/laborContract/verify`。
- 返回：
  - 是否匹配。
  - 匹配的合同编号、员工、签署时间。
  - 匹配文件类型：预览/归档/完成证明。

如果浏览器 SHA-256 兼容性不足，上传文件到后端计算。

## 接口设计

新增 API：

```http
POST /oa/laborContract/verify
Content-Type: application/json

{
  "hash": "..."
}
```

返回：

```json
{
  "matched": true,
  "contractId": 100,
  "contractNo": "LC20260614001",
  "employeeName": "张三",
  "signedTime": "2026-06-14 15:30:22",
  "fileKind": "archive"
}
```

签署接口请求体扩展：

```json
{
  "confirmed": true,
  "documentVersion": "LC-100-20260614153022",
  "previewFileHash": "...",
  "signatureDataUrl": "data:image/png;base64,..."
}
```

## 测试策略

后端重点测试：

- 发送合同会生成 `documentVersion`、`previewFileHash`、`templateFileHash`、`sealImageHash`。
- 签署请求版本不一致时拒绝。
- 签署请求 preview hash 不一致时拒绝。
- 磁盘预览文件被改后拒绝签署。
- 非 PNG 签名拒绝。
- 超大签名拒绝。
- 签署后生成 archive PDF 和 certificate PDF。
- 事件 hash 链可验证。
- 本人和管理端下载权限边界。
- hash 验证接口能匹配归档 PDF 和完成证明 PDF。

前端重点测试：

- 移动端签署请求包含 `documentVersion` 和 `previewFileHash`。
- 管理端文件按钮优先打开 PDF。
- 验 hash 弹窗调用正确 API。
- 劳动合同模块现有路由和权限不回退。

## 实施顺序

### 阶段 A：数据和版本冻结

1. SQL 增加字段。
2. Domain/Mapper 增加字段。
3. 发送时生成版本和 hash。
4. 签署时校验版本和 hash。

### 阶段 B：签名图片校验

1. 限制 data URL 前缀。
2. 限制 Base64 解码后大小。
3. 使用 `ImageIO` 验证 PNG。
4. 拒绝空白签名。

### 阶段 C：PDF 和完成证明

1. 引入 HTML-to-PDF 依赖。
2. 新增 PDF 生成服务。
3. 生成 preview/archive/certificate。
4. 移动端改用 PDF 预览。

### 阶段 D：事件链

1. 事件表增加 hash 链字段。
2. 统一 `recordEvent` 计算事件 hash。
3. 增加 view/download/verify 事件。
4. 详情页显示事件链。

### 阶段 E：验 hash

1. 后端新增 verify DTO 和接口。
2. Mapper 增加按 hash 查询。
3. 管理端新增验 hash 弹窗。
4. 补测试。

## 验收标准

- 员工签署时，如果预览文件 hash 已变化，签署失败。
- 签署完成后可以下载归档 PDF 和完成证明 PDF。
- 归档 PDF 和完成证明 PDF 的 hash 可以在系统内验证。
- 事件链能显示 send/view/sign/download/verify 等关键动作。
- 非本人不能下载合同文件。
- 管理端无店铺权限不能下载合同文件。
- 签名图片不是 PNG、过大或空白时签署失败。
- 不影响现有劳动合同创建、发送、作废、列表查询。

## 风险和取舍

- 不做 OTP 后，身份强度仍低于第三方电子签；本方案主要提升文件一致性和审计能力。
- HTML-to-PDF 的合同版式需要和现有 DOCX 模板对齐，可能需要一次人工校对。
- 如果必须保持 Word 模板为唯一合同正文来源，后续仍可能需要接入 LibreOffice 或商业 DOCX 转 PDF 服务。
- 当前只建议先做单签署人劳动合同，不扩展多签署人顺序。

## 2026-06-14 执行记录

本次按“只做 1/3/4/5”落地第一版内部增强能力：

- 后端新增文件版本冻结字段、预览/归档/证明 hash、事件 hash 链和 `/oa/laborContract/verify` 验 hash 接口。
- 文档生成保留现有 DOCX，同时生成私有下载的 `preview-pdf`、`archive-pdf`、`certificate` PDF 证据文件。
- 移动端签署提交携带 `documentVersion` 和 `previewFileHash`。
- 管理端详情展示版本、证据 hash 和事件链 hash，并新增验 hash 弹窗。
- 数据库升级脚本：`sql/erp_oa_labor_contract_esign_1345_20260614.sql`。

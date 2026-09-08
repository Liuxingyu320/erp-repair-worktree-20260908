# 合同自动化阶段1：文件与证据链 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让员工阅读的PDF、员工签名、最终已签PDF、签署证书和事件链形成不可变且可验证的证据，HR只看业务结论也能判断文件是否可信。

**Architecture:** 保留DOCX/XLSX作为源文件，统一以转换后的PDF作为阅读与签署对象；每次重新生成都创建新文档版本。文件先在临时目录完成转换、签名和hash，再原子提升到正式目录并落证据表。签署请求携带文档版本和逐文件hash，服务端精确校验；最终PDF使用PDFBox写入签名或标准签署页，绝不复用阅读PDF路径。

**Tech Stack:** Java 17, Spring Boot 4, MyBatis XML, MySQL 8, Apache POI, LibreOffice headless, Apache PDFBox 3.0.7 core, JUnit 5, Vue 2, Node source tests.

**阶段状态：** **已完成（2026-07-11）**

**阶段门记录：** OA模块及依赖共157个测试、前端103个测试全部通过，生产构建通过（仅保留既有资源体积告警）。劳动合同DOCX、入职表XLSX、无签名定位DOCX三类现有模板均经真实LibreOffice生成阅读PDF和独立已签PDF并完成逐页目视检查；已签PDF原合同页与阅读PDF像素一致，仅新增标准证据页。篡改、文件缺失、事件链不一致、LibreOffice不可用和归档回滚均有测试覆盖。OA容器已补齐Writer、Calc、Noto CJK字体与字体缓存，并为每次转换使用独立LibreOffice用户目录。测试产物均在临时目录生成并已清理，未提交真实员工资料或签名。自动发送配置仍不存在，按总路线图契约等价于关闭。

---

## Task 1: 增加证据表、文档版本和请求幂等字段

**Files:**
- Create: `sql/erp_oa_sign_evidence_20260711.sql`
- Create: `docker/mysql/db/erp_oa_sign_evidence_20260711.sql`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignPackage.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignPackageDocument.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignEvent.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignFileEvidence.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaSignPackageMapper.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaSignPackageDocumentMapper.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaSignEventMapper.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaSignFileEvidenceMapper.java`
- Modify: `erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignPackageMapper.xml`
- Modify: `erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignPackageDocumentMapper.xml`
- Modify: `erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignEventMapper.xml`
- Create: `erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignFileEvidenceMapper.xml`
- Modify: `erp-modules/erp-oa/src/test/java/com/erp/oa/mapper/OaMapperBindingTest.java`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/mapper/OaSignEvidenceMigrationTest.java`

- [x] 写失败测试，要求两份SQL一致，所有新增字段都存在，并要求mapper XML为每个字段建立resultMap。
- [x] 扩展 `oa_sign_package`：`document_version varchar(64)`、`sign_deadline datetime`、`version bigint NOT NULL DEFAULT 0`。
- [x] 扩展 `oa_sign_package_document`：

```sql
ALTER TABLE oa_sign_package_document
  ADD COLUMN review_pdf_url varchar(500) DEFAULT NULL,
  ADD COLUMN review_pdf_hash varchar(64) DEFAULT NULL,
  ADD COLUMN signed_pdf_url varchar(500) DEFAULT NULL,
  ADD COLUMN signed_pdf_hash varchar(64) DEFAULT NULL,
  ADD COLUMN signature_file_url varchar(500) DEFAULT NULL,
  ADD COLUMN signature_hash varchar(64) DEFAULT NULL,
  ADD COLUMN certificate_hash varchar(64) DEFAULT NULL,
  ADD COLUMN document_version varchar(64) DEFAULT NULL;
```

- [x] 扩展 `oa_sign_event`：`request_id varchar(64)`；建立唯一索引 `uk_oa_sign_event_request(event_type, request_id)`，允许历史NULL重复。
- [x] 新建 `oa_sign_file_evidence`，字段固定为：`evidence_id`、`package_id`、`document_id`、`document_version`、`evidence_type`、`file_url`、`file_hash`、`file_size`、`source_evidence_id`、`generated_time`、`create_time`。唯一索引为 `(document_id, document_version, evidence_type)`。
- [x] `evidence_type` 只允许应用层枚举：`TEMPLATE_SOURCE`、`RENDERED_SOURCE`、`REVIEW_PDF`、`SIGNATURE_IMAGE`、`SIGNED_PDF`、`SIGN_CERTIFICATE`、`COMPANY_SEAL`。
- [x] 迁移脚本只新增兼容字段和表，不回填伪造hash；旧行保持NULL并由验真返回“旧版证据不完整”。
- [x] 运行：

```bash
cmp sql/erp_oa_sign_evidence_20260711.sql docker/mysql/db/erp_oa_sign_evidence_20260711.sql
mvn -pl erp-modules/erp-oa -am -Dtest=OaSignEvidenceMigrationTest,OaMapperBindingTest test
```

- [x] 提交：

```bash
git add -- sql/erp_oa_sign_evidence_20260711.sql docker/mysql/db/erp_oa_sign_evidence_20260711.sql \
  erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignPackage.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignPackageDocument.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignEvent.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignFileEvidence.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaSignPackageMapper.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaSignPackageDocumentMapper.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaSignEventMapper.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaSignFileEvidenceMapper.java \
  erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignPackageMapper.xml \
  erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignPackageDocumentMapper.xml \
  erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignEventMapper.xml \
  erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignFileEvidenceMapper.xml \
  erp-modules/erp-oa/src/test/java/com/erp/oa/mapper/OaMapperBindingTest.java \
  erp-modules/erp-oa/src/test/java/com/erp/oa/mapper/OaSignEvidenceMigrationTest.java
git commit -m "feat: persist immutable signing evidence metadata"
```

## Task 2: 建立临时生成与原子归档服务

**Files:**
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/config/OaSignFileProperties.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignFileStorageService.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/StagedSignFile.java`
- Modify: `erp-modules/erp-oa/src/main/resources/bootstrap.yml`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignFileStorageServiceTest.java`

- [x] 写失败测试覆盖：路径穿越拒绝、临时文件hash/大小、同版本不可覆盖、同文件系统原子移动、异常清理仅删除临时目录。
- [x] 配置固定为：

```yaml
sign-package:
  storage:
    root-path: ${SIGN_PACKAGE_STORAGE_ROOT:${file.path:./uploadPath}/private/sign-package}
    temp-path: ${SIGN_PACKAGE_TEMP_ROOT:${java.io.tmpdir}/erp-sign-package}
    public-prefix: ${SIGN_PACKAGE_PUBLIC_PREFIX:/profile/private/sign-package}
  pdf:
    converter-command: ${SIGN_PACKAGE_PDF_CONVERTER_COMMAND:libreoffice}
    timeout-seconds: ${SIGN_PACKAGE_PDF_TIMEOUT_SECONDS:60}
```

- [x] `stage(taskId, packageId, documentVersion, filename, bytes)` 必须生成服务端文件名，不能接受客户端路径。
- [x] `promote(stagedFile)` 使用 `Files.move(..., StandardCopyOption.ATOMIC_MOVE)`；目标存在时抛错，不覆盖。
- [x] `resolveAuthorizedFile` 只接收数据库保存的相对路径，并验证 `normalized.startsWith(root)`。
- [x] 运行 `mvn -pl erp-modules/erp-oa -am -Dtest=OaSignFileStorageServiceTest test`。
- [x] 提交：

```bash
git add -- erp-modules/erp-oa/src/main/java/com/erp/oa/config/OaSignFileProperties.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignFileStorageService.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/StagedSignFile.java \
  erp-modules/erp-oa/src/main/resources/bootstrap.yml \
  erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignFileStorageServiceTest.java
git commit -m "feat: stage and atomically archive signing files"
```

## Task 3: 用真实Office转换生成员工阅读PDF

**Files:**
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignDocumentService.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaOfficePdfConverter.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/GeneratedSignDocument.java`
- Modify: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignDocumentServiceTest.java`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaOfficePdfConverterTest.java`

- [x] 写失败测试，要求DOCX和XLSX都产生非空PDF，返回值分别包含源文件hash和阅读PDF hash；转换失败时整个文档生成失败。
- [x] 从 `OaLaborContractDocumentService` 提取可复用的headless转换执行模式；命令参数固定：

```text
libreoffice --headless --convert-to pdf --outdir <staging-dir> <rendered-source>
```

- [x] 禁止 `buildSimplePdf(collectDocxLines(...))` 作为生产回退；转换失败返回 `PDF_CONVERSION_FAILED`，不能发送简化文本PDF。
- [x] `GeneratedSignDocument` 改为明确字段：`sourceFileUrl`、`sourceFileHash`、`reviewPdfUrl`、`reviewPdfHash`、`documentVersion`。
- [x] 文档版本格式固定为 `SP-<packageId>-V<positive integer>`；一次签约包生成的所有文件使用同一版本。
- [x] 用测试脚本模拟LibreOffice，验证超时、非0退出码、未生成目标PDF和文件名带空格。
- [x] 运行：

```bash
mvn -pl erp-modules/erp-oa -am \
  -Dtest=OaSignDocumentServiceTest,OaOfficePdfConverterTest test
```

- [x] 使用真实LibreOffice转换至少一个现有DOCX和一个XLSX模板，使用 `pdfinfo` 确认页数大于0。
- [x] 提交：

```bash
git add -- erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignDocumentService.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaOfficePdfConverter.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/GeneratedSignDocument.java \
  erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl
git commit -m "fix: generate review pdfs with office conversion"
```

## Task 4: 生成真正独立的已签PDF和证据页

**Files:**
- Modify: `erp-modules/erp-oa/pom.xml`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignedPdfService.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/SignedPdfResult.java`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignedPdfServiceTest.java`
- Create: `erp-modules/erp-oa/src/test/resources/signing/sample-review.pdf`

- [x] 在 `pom.xml` 只加入PDFBox core，不加入 `pdfbox-examples`：

```xml
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <version>3.0.7</version>
</dependency>
```

- [x] 运行 `mvn -pl erp-modules/erp-oa dependency:tree -Dincludes=org.apache.pdfbox`，确认没有 `pdfbox-examples`。版本与用法以 [Apache PDFBox 3.0 Getting Started](https://pdfbox.apache.org/3.0/getting-started.html) 为准。
- [x] 写失败测试：输入阅读PDF和签名PNG后，输出PDF路径不同、hash不同、页数不小于输入、提取文本包含签署时间/员工/签约包号；修改输出一个字节后hash变化。
- [x] 有模板签名定位时把签名、企业章写入指定页坐标；没有定位时追加标准签署页。标准签署页必须包含：

```text
签约包编号
文档版本
员工姓名及证件号脱敏值
用人法律主体
服务端签署时间
确认短语
阅读PDF SHA-256
签名图片 SHA-256
企业章 SHA-256（存在时）
```

- [x] 签名定位越界、页码不存在、签名图片不合法时拒绝签署，不能静默追加错误位置。
- [x] 输出写到staging；PDFBox保存成功、可重新打开且页数正确后才提升归档。
- [x] 运行 `mvn -pl erp-modules/erp-oa -am -Dtest=OaSignedPdfServiceTest test`。
- [x] 提交：

```bash
git add -- erp-modules/erp-oa/pom.xml \
  erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignedPdfService.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/SignedPdfResult.java \
  erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignedPdfServiceTest.java \
  erp-modules/erp-oa/src/test/resources/signing/sample-review.pdf
git commit -m "feat: produce signed pdfs with embedded evidence"
```

## Task 5: 收紧阅读与签署接口契约

**Files:**
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaSignDocumentHashRequest.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaSignPackageSignRequest.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/IOaSignPackageService.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPackageServiceImpl.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSignPackageController.java`
- Modify: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignPackageServiceImplTest.java`
- Modify: `erp-modules/erp-oa/src/test/java/com/erp/oa/controller/OaSignPackageControllerTest.java`

- [x] 写失败测试覆盖：精确确认短语、错误员工、错误状态、旧文档版本、漏文件、多文件hash顺序无关、未阅读、重复requestId、并发签署。
- [x] 请求DTO精确为：

```java
@NotBlank
private String documentVersion;

@NotEmpty
private List<OaSignDocumentHashRequest> documentHashes;

@NotBlank
private String signConfirmText;

@NotBlank
private String signatureDataUrl;

@NotBlank
@Size(max = 64)
private String requestId;
```

- [x] 后端必须使用 `"本人确认签署本签约包".equals(signConfirmText)`，禁止 `contains`、trim后模糊匹配或前端单独校验。
- [x] 阅读确认事件保存 `documentVersion` 和 `reviewPdfHash`；签署前按必签文件集合逐项重算磁盘hash并与数据库、请求和阅读事件四方比较。
- [x] 首次签署按以下事务边界执行：插入幂等占位事件 → 生成所有已签PDF和证书到临时目录 → 原子归档 → 写证据行 → 条件更新包状态为 `SIGNED`。任一步失败不把包标记为已签。
- [x] 不再设置 `signedFileUrl = generatedPdfUrl`，不再设置 `fileHashAfterSign = fileHashBeforeSign`。
- [x] `recordEvent` 接收明确 `operatorRole`；HR、系统、员工分别写 `HR`、`SYSTEM`、`EMPLOYEE`，禁止固定写employee。
- [x] 相同 `requestId` 返回第一次结果；不同 `requestId` 对已签包返回“已完成”，不重新生成文件。
- [x] 运行：

```bash
mvn -pl erp-modules/erp-oa -am \
  -Dtest=OaSignPackageServiceImplTest,OaSignPackageControllerTest test
```

- [x] 提交：

```bash
git add -- erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaSignDocumentHashRequest.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaSignPackageSignRequest.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/service/IOaSignPackageService.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPackageServiceImpl.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSignPackageController.java \
  erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignPackageServiceImplTest.java \
  erp-modules/erp-oa/src/test/java/com/erp/oa/controller/OaSignPackageControllerTest.java
git commit -m "fix: bind employee signatures to frozen pdf versions"
```

## Task 6: 生成签约包级证书并实现业务化验真

**Files:**
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/constant/OaSignVerificationStatus.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/OaSignVerificationResult.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignVerificationService.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignDocumentService.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSignPackageController.java`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignVerificationServiceTest.java`
- Modify: `erp-modules/erp-oa/src/test/java/com/erp/oa/controller/OaSignPackageControllerTest.java`

- [x] 固定业务状态：

```java
public enum OaSignVerificationStatus {
    VERIFIED,
    MISMATCH,
    FILE_MISSING,
    LEGACY_LIMITED
}
```

- [x] 写失败测试：完整文件为 `VERIFIED`；篡改、事件链断裂、证书hash不符为 `MISMATCH`；文件不存在为 `FILE_MISSING`；旧记录无证据行为 `LEGACY_LIMITED`。
- [x] 证书记录任务号（阶段2为空时允许）、签约包号、文档版本、员工、法律主体、文件清单、阅读/已签/签名/企业章hash、签署时间、IP、UA、确认短语、HR确认人和事件链根hash。
- [x] 新增 `GET /signPackage/{packageId}/verify`，要求 `oa:signPackage:query` 并继续使用 `resolveShopDeptId` 做组织范围校验。
- [x] 默认响应不返回原始hash，只返回 `status`、`message`、`checkedTime`、`documentResults`。带 `includeTechnical=true` 时额外要求 `oa:signTask:technicalEvidence`。
- [x] 运行：

```bash
mvn -pl erp-modules/erp-oa -am \
  -Dtest=OaSignVerificationServiceTest,OaSignPackageControllerTest test
```

- [x] 提交：

```bash
git add -- erp-modules/erp-oa/src/main/java/com/erp/oa/constant/OaSignVerificationStatus.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/OaSignVerificationResult.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignVerificationService.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignDocumentService.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSignPackageController.java \
  erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignVerificationServiceTest.java \
  erp-modules/erp-oa/src/test/java/com/erp/oa/controller/OaSignPackageControllerTest.java
git commit -m "feat: verify signing evidence in business language"
```

## Task 7: 更新HR端和员工端，默认隐藏hash

**Files:**
- Modify: `erp-ui/src/api/oa/signPackage.js`
- Modify: `erp-ui/src/views/oa/signPackage/index.vue`
- Modify: `erp-ui/src/views/mobile/signPackage/index.vue`
- Modify: `erp-ui/test/signPackageModule.test.js`
- Create: `erp-ui/test/signPackageEvidenceUx.test.js`

- [x] 写失败测试，要求员工签署请求包含 `documentVersion`、逐文档 `reviewPdfHash` 和UUID `requestId`；确认文本必须精确输入。
- [x] 员工打开文档后使用服务端返回的版本/hash调用阅读确认，不从URL或文件名推断。
- [x] HR详情增加“验证文件”按钮，只显示：

```text
文件完整，验真通过
文件与签署记录不一致
文件缺失，暂时无法验证
历史合同，仅支持旧版验真
```

- [x] 技术证据折叠面板只有 `oa:signTask:technicalEvidence` 时渲染；普通HR源码路径不直接显示 `fileHashBeforeSign`、`fileHashAfterSign` 或事件hash。
- [x] 已签文件按钮必须下载 `signedPdfUrl`，阅读按钮下载 `reviewPdfUrl`，两者标签清晰区分。
- [x] 运行：

```bash
cd erp-ui
node test/signPackageModule.test.js
node test/signPackageEvidenceUx.test.js
npm test
```

- [x] 提交：

```bash
git add -- erp-ui/src/api/oa/signPackage.js erp-ui/src/views/oa/signPackage/index.vue \
  erp-ui/src/views/mobile/signPackage/index.vue erp-ui/test/signPackageModule.test.js \
  erp-ui/test/signPackageEvidenceUx.test.js
git commit -m "feat: show business signing verification results"
```

## Task 8: 阶段1视觉、篡改与回滚验证

- [x] 运行完整回归：

```bash
mvn -pl erp-modules/erp-oa -am test
cd erp-ui
npm test
npm run build:prod
```

- [x] 对DOCX劳动合同、XLSX入职表、无签名定位模板各生成一份阅读PDF和已签PDF；用 `pdftoppm -png` 渲染全部页面，逐页检查表格、分页、中文字体、图片、签名和企业章。
- [x] 保存测试产物到临时QA目录，不提交真实员工合同或签名。
- [x] 对已签PDF复制件修改一个字节，验真必须返回 `MISMATCH`；删除复制件，必须返回 `FILE_MISSING`。
- [x] 模拟LibreOffice不可用，发送动作必须失败并保留草稿，不得生成简化PDF。
- [x] 回滚验证：旧签约包仍按旧字段可读；关闭新证据UI后原下载接口仍工作；新生成文件不得删除。
- [x] 确认自动发送开关仍为false，再进入阶段2。

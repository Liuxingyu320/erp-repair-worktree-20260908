# Employee Signing Package Readiness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the employee signing package flow production-ready for the supplied onboarding/offboarding templates: real templates can be uploaded, matched, signed, previewed, downloaded, and audited.

**Architecture:** Keep generated signing files private. Do not expose `/profile/sign-package/**` as unauthenticated static files; serve document and certificate blobs through authenticated `/oa/signPackage/**` endpoints, then let the frontend create browser object URLs for preview/open/download. Keep public file-service static mapping limited to `/file/public/**`, and fix local MinIO startup so template uploads work in development.

**Tech Stack:** Spring Boot, Spring Cloud Gateway, MyBatis, Apache POI, Vue 2, Axios, existing ERP token and shop-scope security.

---

## Current Evidence

- API signing flow works with a valid QA template: package `1`, document `1`, status `signed`.
- Backend sign-package tests pass: `17` tests, `0` failures.
- Supplied files under `/Users/liuxingyu/Desktop/入离调转20260702/入离调转` have `0` `${...}` placeholders, so direct upload is rejected by `OaSignDocumentService.assertTemplateContainsRequiredPlaceholders`.
- Generated files exist under `uploadPath/sign-package/1`, but frontend URLs like `/dev-api/profile/sign-package/1/ONBOARD_COMMITMENT-1.pdf` return JSON `No static resource ...`.
- `erp-file` local startup fails because `MinioConfig.getMinioClient()` builds a client with empty access key and secret key.

## File Structure

- Modify `erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSignPackageController.java`: add authenticated document/certificate download endpoints.
- Modify `erp-modules/erp-oa/src/main/java/com/erp/oa/service/IOaSignPackageService.java`: expose file-resolution methods.
- Modify `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPackageServiceImpl.java`: authorize employee/admin file access and resolve document rows.
- Modify `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignDocumentService.java`: add safe local file resolution for generated signing package files.
- Create `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/OaSignPackageFile.java`: immutable file descriptor for controller streaming.
- Modify `erp-modules/erp-oa/src/main/java/com/erp/oa/constant/OaSignTemplateType.java`: add missing real-template types.
- Modify `erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignTemplateMapper.xml`: stop using substring matching for post-level ranges.
- Modify `erp-modules/erp-file/src/main/java/com/erp/file/config/MinioConfig.java`: make MinIO client conditional.
- Modify `erp-modules/erp-file/src/main/java/com/erp/file/service/MinioSysFileServiceImpl.java`: make MinIO storage service conditional.
- Modify `erp-modules/erp-file/src/main/resources/application-local.yml`: add `minio.enabled: ${MINIO_ENABLED:false}`.
- Modify `erp-ui/src/api/oa/signPackage.js`: add blob download API helpers.
- Modify `erp-ui/src/views/mobile/signPackage/index.vue`: replace raw `/profile` iframe/link usage with authenticated blob preview links.
- Modify `erp-ui/src/views/oa/signPackage/index.vue`: use authenticated blob download for desktop generated files.
- Modify tests:
  - `erp-modules/erp-oa/src/test/java/com/erp/oa/controller/OaSignPackageControllerTest.java`
  - `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignPackageServiceImplTest.java`
  - `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignDocumentServiceTest.java`
  - `erp-modules/erp-file/src/test/java/com/erp/file/config/MinioConfigTest.java`
  - `erp-ui/test/signPackageModule.test.js`

## Template Mapping

Convert these originals into a new folder:

`/Users/liuxingyu/Desktop/入离调转20260702/签约系统模板`

| Source file | Template type | Match fields | Required placeholders |
|---|---|---|---|
| `0 员工档案目录.docx` | `ONBOARD_ARCHIVE_CATALOG` | scenario `onboard` | `${employeeName}`, `${employeeDeptName}`, `${entryDate}`, `${postName}` |
| `01员工手册.docx` | `ONBOARD_HANDBOOK` | scenario `onboard` | none |
| `1-1应聘登记表.xlsx` | `ONBOARD_APPLICATION_FORM` | scenario `onboard` | `${employeeName}`, `${employeeIdCard}`, `${employeePhone}`, `${entryDate}`, `${postName}` |
| `1-8背景调查报告.docx` | `ONBOARD_BACKGROUND_CHECK` | scenario `onboard` | `${employeeName}`, `${postName}` |
| `2-1录用通知书.docx` | `ONBOARD_OFFER_NOTICE` | scenario `onboard` | `${employeeName}`, `${employeePhone}`, `${employeeDeptName}`, `${postName}`, `${entryDate}`, `${baseSalary}`, `${signDate}` |
| `2-3入职承诺书.docx` | `ONBOARD_COMMITMENT` | scenario `onboard` | `${employeeName}`, `${employeeIdCard}`, `${signDate}` |
| `2-4岗位职责确认书（2-4级）.docx` | `ONBOARD_POST_DUTY` | postLevelScope `2-4` | `${employeeName}`, `${postName}`, `${postLevel}`, `${signDate}` |
| `2-4岗位职责确认书（5-6级）.docx` | `ONBOARD_POST_DUTY` | postLevelScope `5-6` | `${employeeName}`, `${postName}`, `${postLevel}`, `${signDate}` |
| `2-4岗位职责确认书（7-8级）.docx` | `ONBOARD_POST_DUTY` | postLevelScope `7-8` | `${employeeName}`, `${postName}`, `${postLevel}`, `${signDate}` |
| `2-5劳动合同.docx` | `ONBOARD_LABOR_CONTRACT` | employmentType `劳动合同` | `${employeeName}`, `${employeeIdCard}`, `${employeePhone}`, `${employeeAddress}`, `${contractStartDate}`, `${contractEndDate}`, `${probationStartDate}`, `${probationEndDate}`, `${postName}`, `${signDate}` |
| `2-6员工手册签收确认书.docx` | `ONBOARD_HANDBOOK_RECEIPT` | scenario `onboard` | `${employeeName}`, `${employeeIdCard}`, `${signDate}` |
| `2-7薪酬结构确认书（A版）.docx` | `ONBOARD_SALARY_CONFIRM` | salaryVersion `A` | `${employeeName}`, `${employeeIdCard}`, `${baseSalary}`, `${postSalary}`, `${fieldAllowance}`, `${salaryTotal}`, `${signDate}` |
| `2-7薪酬结构确认书（B版）.docx` | `ONBOARD_SALARY_CONFIRM` | salaryVersion `B` | `${employeeName}`, `${employeeIdCard}`, `${baseSalary}`, `${postSalary}`, `${fieldAllowance}`, `${salaryTotal}`, `${signDate}` |
| `2-8劳务合同.docx` | `ONBOARD_SERVICE_CONTRACT` | employmentType `劳务合同` | `${employeeName}`, `${employeeIdCard}`, `${employeePhone}`, `${employeeAddress}`, `${servicePersonType}`, `${contractStartDate}`, `${contractEndDate}`, `${postName}`, `${baseSalary}`, `${signDate}` |
| `2-9劳务合同签收单.docx` | `ONBOARD_SERVICE_RECEIPT` | employmentType `劳务合同` | `${employeeName}`, `${employeeIdCard}`, `${servicePersonType}`, `${baseSalary}`, `${postSalary}`, `${fieldAllowance}`, `${salaryTotal}`, `${insuranceType}`, `${signDate}` |
| `2-10保密与竞业限制协议（7级及以上）.docx` | `ONBOARD_CONFIDENTIAL_NONCOMPETE` | postLevelScope `7-8` | `${employeeName}`, `${employeeIdCard}`, `${employeePhone}`, `${employeeAddress}`, `${postName}`, `${postLevel}`, `${contractStartDate}`, `${signDate}` |

## Task 1: Authenticated Signing File Access

**Files:**
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/OaSignPackageFile.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSignPackageController.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/IOaSignPackageService.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPackageServiceImpl.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignDocumentService.java`
- Test: `erp-modules/erp-oa/src/test/java/com/erp/oa/controller/OaSignPackageControllerTest.java`
- Test: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignPackageServiceImplTest.java`
- Test: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignDocumentServiceTest.java`

- [ ] **Step 1: Write controller contract tests**

Add tests asserting these endpoint methods exist and require login:

```java
Method mobileDocument = OaSignPackageController.class.getMethod(
        "mobileDocumentFile", Long.class, Long.class);
assertThat(mobileDocument.getAnnotation(RequiresLogin.class)).isNotNull();

Method mobileCertificate = OaSignPackageController.class.getMethod(
        "mobileCertificateFile", Long.class, Long.class);
assertThat(mobileCertificate.getAnnotation(RequiresLogin.class)).isNotNull();
```

- [ ] **Step 2: Run controller test and confirm failure**

Run:

```bash
cd /Users/liuxingyu/Desktop/备份/ERP-NEW/erp-modules
mvn -pl erp-oa -Dtest=OaSignPackageControllerTest test
```

Expected: compile failure because the two methods do not exist yet.

- [ ] **Step 3: Create file descriptor**

Create `OaSignPackageFile.java`:

```java
package com.erp.oa.domain.vo;

import java.nio.file.Path;

public class OaSignPackageFile
{
    private final Path path;
    private final String fileName;
    private final String contentType;

    public OaSignPackageFile(Path path, String fileName, String contentType)
    {
        this.path = path;
        this.fileName = fileName;
        this.contentType = contentType;
    }

    public Path getPath() { return path; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
}
```

- [ ] **Step 4: Add service interface methods**

Add to `IOaSignPackageService`:

```java
OaSignPackageFile resolveMyDocumentFile(Long packageId, Long documentId);

OaSignPackageFile resolveMyCertificateFile(Long packageId, Long documentId);

OaSignPackageFile resolveScopedDocumentFile(Long packageId, Long documentId, Long selectedShopDeptId);

OaSignPackageFile resolveScopedCertificateFile(Long packageId, Long documentId, Long selectedShopDeptId);
```

- [ ] **Step 5: Add safe generated-file resolver**

Add to `OaSignDocumentService`:

```java
public Path resolveGeneratedSignPackageFile(String fileUrl)
{
    if (StringUtils.isBlank(fileUrl) || !fileUrl.startsWith(localFilePrefix + "/sign-package/"))
    {
        throw new ServiceException("签约文件地址无效");
    }
    Path root = Paths.get(localFilePath).toAbsolutePath().normalize();
    Path file = Paths.get(localFilePath, fileUrl.substring(localFilePrefix.length()))
            .toAbsolutePath().normalize();
    if (!file.startsWith(root) || !Files.exists(file) || !Files.isRegularFile(file))
    {
        throw new ServiceException("签约文件不存在");
    }
    return file;
}
```

- [ ] **Step 6: Implement service authorization and file selection**

In `OaSignPackageServiceImpl`, implement:

```java
@Override
public OaSignPackageFile resolveMyDocumentFile(Long packageId, Long documentId)
{
    assertAndGetMyPackage(packageId);
    OaSignPackageDocument document = assertPackageDocument(packageId, documentId);
    String fileUrl = StringUtils.isNotBlank(document.getGeneratedPdfUrl())
            ? document.getGeneratedPdfUrl() : document.getGeneratedFileUrl();
    return toPackageFile(fileUrl, document.getDocumentName());
}

@Override
public OaSignPackageFile resolveMyCertificateFile(Long packageId, Long documentId)
{
    assertAndGetMyPackage(packageId);
    OaSignPackageDocument document = assertPackageDocument(packageId, documentId);
    if (!YES.equalsIgnoreCase(document.getSigned()) || StringUtils.isBlank(document.getCertificateFileUrl()))
    {
        throw new ServiceException("签署证明尚未生成");
    }
    return toPackageFile(document.getCertificateFileUrl(), document.getDocumentName() + "-签署证明");
}

private OaSignPackageFile toPackageFile(String fileUrl, String displayName)
{
    Path path = documentService.resolveGeneratedSignPackageFile(fileUrl);
    String lowerName = path.getFileName().toString().toLowerCase();
    String contentType = lowerName.endsWith(".pdf")
            ? "application/pdf"
            : "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    String suffix = lowerName.endsWith(".pdf") ? ".pdf" : ".docx";
    return new OaSignPackageFile(path, displayName + suffix, contentType);
}
```

Implement the scoped admin variants with `assertAndGetScopedPackage(packageId, selectedShopDeptId)` before `assertPackageDocument`.

- [ ] **Step 7: Add streaming endpoints**

Add to `OaSignPackageController`:

```java
@RequiresLogin
@GetMapping("/mobile/{packageId}/documents/{documentId}/file")
public ResponseEntity<Resource> mobileDocumentFile(@PathVariable Long packageId, @PathVariable Long documentId)
{
    return streamFile(signPackageService.resolveMyDocumentFile(packageId, documentId));
}

@RequiresLogin
@GetMapping("/mobile/{packageId}/documents/{documentId}/certificate")
public ResponseEntity<Resource> mobileCertificateFile(@PathVariable Long packageId, @PathVariable Long documentId)
{
    return streamFile(signPackageService.resolveMyCertificateFile(packageId, documentId));
}
```

Add admin endpoints under `/signPackage/{packageId}/documents/{documentId}/file` and `/certificate` with `@RequiresPermissions("oa:signPackage:query")`.

- [ ] **Step 8: Run backend tests**

Run:

```bash
cd /Users/liuxingyu/Desktop/备份/ERP-NEW/erp-modules
mvn -pl erp-oa -Dtest=OaSignPackageControllerTest,OaSignPackageServiceImplTest,OaSignDocumentServiceTest test
```

Expected: all tests pass.

## Task 2: Frontend Authenticated Preview and Download

**Files:**
- Modify: `erp-ui/src/api/oa/signPackage.js`
- Modify: `erp-ui/src/views/mobile/signPackage/index.vue`
- Modify: `erp-ui/src/views/oa/signPackage/index.vue`
- Test: `erp-ui/test/signPackageModule.test.js`

- [ ] **Step 1: Add frontend API contract assertions**

In `erp-ui/test/signPackageModule.test.js`, assert these exports exist:

```js
;[
  ["downloadMySignPackageDocument", "url: '/oa/signPackage/mobile/' + packageId + '/documents/' + documentId + '/file'", "responseType: 'blob'"],
  ["downloadMySignPackageCertificate", "url: '/oa/signPackage/mobile/' + packageId + '/documents/' + documentId + '/certificate'", "responseType: 'blob'"],
  ["downloadSignPackageDocument", "url: '/oa/signPackage/' + packageId + '/documents/' + documentId + '/file'", "responseType: 'blob'"],
  ["downloadSignPackageCertificate", "url: '/oa/signPackage/' + packageId + '/documents/' + documentId + '/certificate'", "responseType: 'blob'"]
].forEach(([fnName, urlSnippet, responseTypeSnippet]) => {
  assert.ok(apiSource.includes(`export function ${fnName}`), `${fnName} should be exported`)
  assert.ok(apiSource.includes(urlSnippet), `${fnName} should call ${urlSnippet}`)
  assert.ok(apiSource.includes(responseTypeSnippet), `${fnName} should request blob data`)
})
```

- [ ] **Step 2: Run frontend test and confirm failure**

Run:

```bash
cd /Users/liuxingyu/Desktop/备份/ERP-NEW/erp-ui
npm test -- signPackageModule.test.js
```

Expected: failure because the API helpers do not exist.

- [ ] **Step 3: Add blob API helpers**

Add to `erp-ui/src/api/oa/signPackage.js`:

```js
export function downloadMySignPackageDocument(packageId, documentId) {
  return request({
    url: '/oa/signPackage/mobile/' + packageId + '/documents/' + documentId + '/file',
    method: 'get',
    responseType: 'blob'
  })
}

export function downloadMySignPackageCertificate(packageId, documentId) {
  return request({
    url: '/oa/signPackage/mobile/' + packageId + '/documents/' + documentId + '/certificate',
    method: 'get',
    responseType: 'blob'
  })
}
```

Add the same pattern for desktop admin downloads without `/mobile`.

- [ ] **Step 4: Replace mobile raw file URLs with object URLs**

In `MobileSignPackage`, add state:

```js
documentObjectUrl: "",
certificateObjectUrl: "",
fileLoading: false
```

Add cleanup:

```js
revokeObjectUrls() {
  ;[this.documentObjectUrl, this.certificateObjectUrl].forEach(url => {
    if (url) URL.revokeObjectURL(url)
  })
  this.documentObjectUrl = ""
  this.certificateObjectUrl = ""
}
```

Load blobs when `activeDocument` changes and after `openPackage`/`confirmRead`:

```js
loadActiveDocumentFiles() {
  this.revokeObjectUrls()
  if (!this.selectedPackage || !this.activeDocument) return
  const packageId = this.selectedPackage.packageId
  const documentId = this.activeDocument.documentId
  this.fileLoading = true
  downloadMySignPackageDocument(packageId, documentId).then(blob => {
    this.documentObjectUrl = URL.createObjectURL(new Blob([blob], { type: "application/pdf" }))
  }).finally(() => {
    this.fileLoading = false
  })
  if (this.activeDocument.certificateFileUrl) {
    downloadMySignPackageCertificate(packageId, documentId).then(blob => {
      this.certificateObjectUrl = URL.createObjectURL(new Blob([blob], { type: "application/pdf" }))
    })
  }
}
```

Use `documentObjectUrl` in the iframe and open link. Use `certificateObjectUrl` for the certificate link.

- [ ] **Step 5: Run frontend tests**

Run:

```bash
cd /Users/liuxingyu/Desktop/备份/ERP-NEW/erp-ui
npm test -- signPackageModule.test.js
```

Expected: all assertions pass.

## Task 3: Local File Service Startup

**Files:**
- Modify: `erp-modules/erp-file/src/main/java/com/erp/file/config/MinioConfig.java`
- Modify: `erp-modules/erp-file/src/main/java/com/erp/file/service/MinioSysFileServiceImpl.java`
- Modify: `erp-modules/erp-file/src/main/resources/application-local.yml`
- Test: `erp-modules/erp-file/src/test/java/com/erp/file/config/MinioConfigTest.java`

- [ ] **Step 1: Write conditional configuration test**

Create `MinioConfigTest.java` with `ApplicationContextRunner` asserting no `MinioClient` bean exists when `minio.enabled=false`, and one exists when `minio.enabled=true` with credentials.

- [ ] **Step 2: Make MinIO conditional**

Annotate `MinioConfig`:

```java
@ConditionalOnProperty(prefix = "minio", name = "enabled", havingValue = "true")
```

Annotate `MinioSysFileServiceImpl` with the same condition.

- [ ] **Step 3: Set local default**

Add to `application-local.yml`:

```yaml
minio:
  enabled: ${MINIO_ENABLED:false}
```

Keep existing URL/accessKey/secretKey/bucketName keys below it.

- [ ] **Step 4: Verify file module starts**

Run:

```bash
cd /Users/liuxingyu/Desktop/备份/ERP-NEW/erp-modules
mvn -pl erp-file -Dtest=MinioConfigTest,LocalSysFileServiceImplTest test
java -jar erp-file/target/erp-modules-file.jar --spring.profiles.active=local
```

Expected: tests pass and service listens on `9300` without MinIO credentials.

## Task 4: Real Template Type and Matching Coverage

**Files:**
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/constant/OaSignTemplateType.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignTemplateServiceImpl.java`
- Modify: `erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignTemplateMapper.xml`
- Modify: `erp-ui/src/views/oa/signPackage/index.vue`
- Test: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignTemplateServiceImplTest.java`
- Test: `erp-modules/erp-oa/src/test/java/com/erp/oa/constant/OaSignTemplateTypeTest.java`
- Test: `erp-ui/test/signPackageModule.test.js`

- [ ] **Step 1: Add missing types**

Add `ONBOARD_OFFER_NOTICE` and `ONBOARD_CONFIDENTIAL_NONCOMPETE` to `OaSignTemplateType` using the placeholders in the template mapping table.

- [ ] **Step 2: Fix post-level range matching**

Remove the mapper condition:

```xml
<if test="postLevelSnapshot != null and postLevelSnapshot != ''">
    and (post_level_scope is null or post_level_scope = '' or locate(#{postLevelSnapshot}, post_level_scope) > 0)
</if>
```

Filter in `OaSignTemplateServiceImpl.matchTemplates` and `sendPackage` path with a Java parser that supports `2-4`, `5-6`, `7-8`, `7,8`, and `7、8`.

- [ ] **Step 3: Add range parser tests**

Test cases:

```java
assertThat(matchesPostLevelScope("2-4", "3")).isTrue();
assertThat(matchesPostLevelScope("2-4", "5")).isFalse();
assertThat(matchesPostLevelScope("7-8", "7")).isTrue();
assertThat(matchesPostLevelScope("7、8", "8")).isTrue();
assertThat(matchesPostLevelScope("", "8")).isTrue();
```

- [ ] **Step 4: Update desktop type list fallback**

Add the two new types to the local fallback `templateTypeOptions` in `erp-ui/src/views/oa/signPackage/index.vue` so the UI remains usable if type API fails.

- [ ] **Step 5: Verify**

Run:

```bash
cd /Users/liuxingyu/Desktop/备份/ERP-NEW/erp-modules
mvn -pl erp-oa -Dtest=OaSignTemplateTypeTest,OaSignTemplateServiceImplTest test

cd /Users/liuxingyu/Desktop/备份/ERP-NEW/erp-ui
npm test -- signPackageModule.test.js
```

Expected: all tests pass.

## Task 5: Convert and Register the Real Templates

**Files:**
- Input: `/Users/liuxingyu/Desktop/入离调转20260702/入离调转/*`
- Output: `/Users/liuxingyu/Desktop/入离调转20260702/签约系统模板/*`
- Optional helper: `scripts/sign_template_placeholder_audit.py`

- [ ] **Step 1: Create converted-template folder**

Run:

```bash
mkdir -p /Users/liuxingyu/Desktop/入离调转20260702/签约系统模板
```

- [ ] **Step 2: Copy originals before editing**

Run:

```bash
cp /Users/liuxingyu/Desktop/入离调转20260702/入离调转/* /Users/liuxingyu/Desktop/入离调转20260702/签约系统模板/
```

- [ ] **Step 3: Insert placeholders in Word and Excel**

Use the template mapping table above. Replace each employee-specific blank/value position with the exact `${name}` token, preserving surrounding legal text and layout. Do not split a token across multiple Word runs; type the whole token in one continuous run.

- [ ] **Step 4: Audit placeholders**

Run a script that reads `.docx` and `.xlsx` text and verifies every required placeholder exists for its mapped type. The command must fail if any required placeholder is absent.

- [ ] **Step 5: Upload/register templates**

Start the local stack and use the desktop `员工签约 -> 模板管理` page to upload each converted file with:

- status `0`
- scenario/match fields from the template mapping table
- sort order matching the source filename order

- [ ] **Step 6: Confirm all templates are active**

Query:

```sql
select template_type, template_name, employment_type, post_level_scope, salary_version, status
from oa_sign_template
order by sort_order asc, template_id asc;
```

Expected: all converted templates appear with `status = '0'`.

## Task 6: End-to-End Acceptance

**Files:**
- Optional script: `scripts/sign-package-e2e-qa.cjs`

- [ ] **Step 1: Run backend tests**

```bash
cd /Users/liuxingyu/Desktop/备份/ERP-NEW/erp-modules
mvn -pl erp-oa -Dtest=OaSignPackageControllerTest,OaSignPackageServiceImplTest,OaSignTemplateServiceImplTest,OaSignDocumentServiceTest,OaSignTemplateTypeTest test
mvn -pl erp-file -Dtest=MinioConfigTest,LocalSysFileServiceImplTest test
```

Expected: both commands pass.

- [ ] **Step 2: Run frontend tests**

```bash
cd /Users/liuxingyu/Desktop/备份/ERP-NEW/erp-ui
npm test -- signPackageModule.test.js
```

Expected: pass.

- [ ] **Step 3: Run real signing flow**

Create one package for each important branch:

- 劳动合同 + 社保 + 岗位等级 `3` + 薪酬版本 `A`
- 劳动合同 + 社保 + 岗位等级 `7` + 薪酬版本 `B`
- 劳务合同 + 岗位等级 `5`

For each package, verify:

- matched documents include the expected role/salary/confidential variants
- send succeeds
- mobile employee can preview every PDF
- employee can confirm read
- employee can sign once
- certificate can be opened
- database package status is `signed`
- document rows have `read_confirmed='Y'`, `signed='Y'`
- event chain includes create, generate, send, read, sign

- [ ] **Step 4: Browser QA**

Open:

```text
http://localhost:1025/mobile/sign-package
```

Expected:

- no console errors or warnings
- iframe displays a PDF instead of blank JSON
- `打开` opens the generated PDF
- `证明` opens the certificate PDF after signing

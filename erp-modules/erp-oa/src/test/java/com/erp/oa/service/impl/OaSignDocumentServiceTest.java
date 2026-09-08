package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import java.nio.file.Path;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.config.OaSignFileProperties;
import com.erp.oa.constant.OaSignTemplateType;
import com.erp.oa.constant.OaSignSigningSequence;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignPackageDocument;
import com.erp.oa.domain.OaSignTemplate;
import com.erp.oa.domain.dto.OaSignPackageSignRequest;
import com.erp.oa.domain.vo.StagedSignFile;

@DisplayName("员工签约文档服务")
class OaSignDocumentServiceTest
{
    @TempDir
    Path tempDir;

    @Test
    @DisplayName("公网上传完整URL可映射到本地公开文件")
    void shouldReadConfiguredFileFromAbsolutePublicUrl() throws Exception
    {
        OaSignDocumentService service = new OaSignDocumentService();
        ReflectionTestUtils.setField(service, "localFilePath", tempDir.toString());
        ReflectionTestUtils.setField(service, "localFilePrefix", "/profile");
        ReflectionTestUtils.setField(service, "localPublicFilePrefix", "/file/public");
        Path image = tempDir.resolve("public/2026/07/19/seal.jpg");
        java.nio.file.Files.createDirectories(image.getParent());
        byte[] expected = "seal-image".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        java.nio.file.Files.write(image, expected);

        assertThat(service.readConfiguredFileBytes(
                "http://localhost:8080/file/public/2026/07/19/seal.jpg"))
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("网关前缀的公网上传URL可映射到本地公开文件")
    void shouldReadConfiguredFileFromGatewayPublicUrl() throws Exception
    {
        OaSignDocumentService service = new OaSignDocumentService();
        ReflectionTestUtils.setField(service, "localFilePath", tempDir.toString());
        ReflectionTestUtils.setField(service, "localFilePrefix", "/profile");
        ReflectionTestUtils.setField(service, "localPublicFilePrefix", "/file/public");
        ReflectionTestUtils.setField(service, "localFileGatewayPrefix", "/prod-api");
        Path image = tempDir.resolve("public/2026/07/23/seal.png");
        java.nio.file.Files.createDirectories(image.getParent());
        byte[] expected = "gateway-seal-image".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        java.nio.file.Files.write(image, expected);

        assertThat(service.readConfiguredFileBytes(
                "/prod-api/file/public/2026/07/23/seal.png"))
                .isEqualTo(expected);
        assertThat(service.readConfiguredFileBytes(
                "http://erp.example/prod-api/file/public/2026/07/23/seal.png"))
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("网关前缀的公网上传URL仍拒绝路径穿越")
    void shouldRejectGatewayPublicUrlPathTraversal() throws Exception
    {
        OaSignDocumentService service = new OaSignDocumentService();
        ReflectionTestUtils.setField(service, "localFilePath", tempDir.resolve("uploadPath").toString());
        ReflectionTestUtils.setField(service, "localFilePrefix", "/profile");
        ReflectionTestUtils.setField(service, "localPublicFilePrefix", "/file/public");
        ReflectionTestUtils.setField(service, "localFileGatewayPrefix", "/prod-api");
        java.nio.file.Files.writeString(tempDir.resolve("secret.txt"), "secret");

        assertThatThrownBy(() -> service.readConfiguredFileBytes(
                "/prod-api/file/public/../../secret.txt"))
                .isInstanceOf(ServiceException.class)
                .hasMessage("配置文件不存在");
    }

    @Test
    @DisplayName("公网上传URL不得越界读取文件根目录之外的文件")
    void shouldRejectPublicUrlPathTraversal() throws Exception
    {
        OaSignDocumentService service = new OaSignDocumentService();
        ReflectionTestUtils.setField(service, "localFilePath", tempDir.resolve("uploadPath").toString());
        ReflectionTestUtils.setField(service, "localFilePrefix", "/profile");
        ReflectionTestUtils.setField(service, "localPublicFilePrefix", "/file/public");
        java.nio.file.Files.writeString(tempDir.resolve("secret.txt"), "secret");

        assertThatThrownBy(() -> service.readConfiguredFileBytes(
                "http://localhost:8080/file/public/../../secret.txt"))
                .isInstanceOf(ServiceException.class)
                .hasMessage("配置文件不存在");
    }

    @Test
    @DisplayName("docx 模板缺少文件类型必填占位符时拒绝保存")
    void shouldRejectDocxTemplateMissingRequiredPlaceholders() throws Exception
    {
        OaSignDocumentService service = documentService();
        Path file = tempDir.resolve("commitment.docx");
        try (XWPFDocument document = new XWPFDocument(); OutputStream output = java.nio.file.Files.newOutputStream(file))
        {
            document.createParagraph().createRun().setText("${employeeName} ${employeePhone}");
            document.write(output);
        }

        assertThatThrownBy(() -> service.assertTemplateContainsRequiredPlaceholders(
                OaSignTemplateType.ONBOARD_COMMITMENT, file.toString()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("${身份证号}")
                .hasMessageContaining("${签署日期}");
    }

    @Test
    @DisplayName("入职承诺书不强制手机号岗位等无关占位符")
    void shouldAllowCommitmentTemplateWithActualRequiredPlaceholders() throws Exception
    {
        OaSignDocumentService service = documentService();
        Path file = tempDir.resolve("commitment-ok.docx");
        try (XWPFDocument document = new XWPFDocument(); OutputStream output = java.nio.file.Files.newOutputStream(file))
        {
            document.createParagraph().createRun().setText("${员工姓名} ${身份证号} ${签署日期}");
            document.write(output);
        }

        assertThatNoException().isThrownBy(() -> service.assertTemplateContainsRequiredPlaceholders(
                OaSignTemplateType.ONBOARD_COMMITMENT, file.toString()));
    }

    @Test
    @DisplayName("docx 模板校验应覆盖页眉页脚中的占位符")
    void shouldValidateDocxPlaceholdersInHeadersAndFooters() throws Exception
    {
        OaSignDocumentService service = documentService();
        Path file = tempDir.resolve("commitment-header.docx");
        try (XWPFDocument document = new XWPFDocument(); OutputStream output = java.nio.file.Files.newOutputStream(file))
        {
            document.createParagraph().createRun().setText("${employeeName} ${employeeIdCard}");
            XWPFHeader header = document.createHeader(org.apache.poi.wp.usermodel.HeaderFooterType.DEFAULT);
            header.createParagraph().createRun().setText("${signDate}");
            document.write(output);
        }

        assertThatNoException().isThrownBy(() -> service.assertTemplateContainsRequiredPlaceholders(
                OaSignTemplateType.ONBOARD_COMMITMENT, file.toString()));
    }

    @Test
    @DisplayName("写死其他公司的模板不得用于当前法律主体")
    void shouldRejectTemplateHardcodedForAnotherLegalEntity() throws Exception
    {
        OaSignDocumentService service = documentService();
        Path file = tempDir.resolve("wrong-legal-entity.docx");
        try (XWPFDocument document = new XWPFDocument();
                OutputStream output = java.nio.file.Files.newOutputStream(file))
        {
            document.createParagraph().createRun().setText("甲方：舟山茗汇文化传播有限公司");
            document.write(output);
        }

        assertThatThrownBy(() -> service.assertTemplateLegalEntityCompatible(
                OaSignTemplateType.ONBOARD_LABOR_CONTRACT, file.toString(),
                "上海示例餐饮有限公司"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("写死了其他公司名称")
                .hasMessageContaining("公司名称占位符");
    }

    @Test
    @DisplayName("动态主体匹配与同名固定主体模板可以正常使用")
    void shouldAllowDynamicOrMatchingLegalEntityTemplate() throws Exception
    {
        OaSignDocumentService service = documentService();
        Path dynamic = tempDir.resolve("dynamic-legal-entity.docx");
        Path matching = tempDir.resolve("matching-legal-entity.docx");
        try (XWPFDocument document = new XWPFDocument();
                OutputStream output = java.nio.file.Files.newOutputStream(dynamic))
        {
            document.createParagraph().createRun().setText("甲方：${companyName}");
            document.write(output);
        }
        try (XWPFDocument document = new XWPFDocument();
                OutputStream output = java.nio.file.Files.newOutputStream(matching))
        {
            document.createParagraph().createRun().setText("甲方：上海示例餐饮有限公司");
            document.write(output);
        }

        assertThatNoException().isThrownBy(() -> service.assertTemplateLegalEntityCompatible(
                OaSignTemplateType.ONBOARD_LABOR_CONTRACT, dynamic.toString(),
                "上海示例餐饮有限公司"));
        assertThatNoException().isThrownBy(() -> service.assertTemplateLegalEntityCompatible(
                OaSignTemplateType.ONBOARD_LABOR_CONTRACT, matching.toString(),
                "上海示例餐饮有限公司"));
    }

    @Test
    @DisplayName("模板同时包含当前主体和其他固定公司时仍拒绝使用")
    void shouldRejectMixedHardcodedLegalEntities() throws Exception
    {
        OaSignDocumentService service = documentService();
        Path file = tempDir.resolve("mixed-legal-entities.docx");
        try (XWPFDocument document = new XWPFDocument();
                OutputStream output = java.nio.file.Files.newOutputStream(file))
        {
            document.createParagraph().createRun().setText("通用主体：${companyName}");
            document.createParagraph().createRun().setText("甲方：上海示例餐饮有限公司");
            document.createParagraph().createRun().setText("盖章方：舟山茗汇文化传播有限公司");
            document.write(output);
        }

        assertThatThrownBy(() -> service.assertTemplateLegalEntityCompatible(
                OaSignTemplateType.ONBOARD_LABOR_CONTRACT, file.toString(),
                "上海示例餐饮有限公司"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("写死了其他公司名称");
    }

    @Test
    @DisplayName("xlsx 模板按单元格内容校验占位符")
    void shouldValidateXlsxTemplatePlaceholders() throws Exception
    {
        OaSignDocumentService service = documentService();
        Path file = tempDir.resolve("application.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook(); OutputStream output = java.nio.file.Files.newOutputStream(file))
        {
            workbook.createSheet("登记表").createRow(0).createCell(0)
                    .setCellValue("${employeeName} ${employeeIdCard} ${employeePhone} ${entryDate}");
            workbook.write(output);
        }

        assertThatThrownBy(() -> service.assertTemplateContainsRequiredPlaceholders(
                OaSignTemplateType.ONBOARD_APPLICATION_FORM, file.toString()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("${岗位名称}");
    }

    @Test
    @DisplayName("发送签约包时将docx模板占位符渲染成员工文件")
    void shouldRenderDocxTemplateWithPackagePlaceholdersAndPdfPreview() throws Exception
    {
        OaSignDocumentService service = documentService();
        Path file = tempDir.resolve("labor.docx");
        try (XWPFDocument document = new XWPFDocument(); OutputStream output = java.nio.file.Files.newOutputStream(file))
        {
            document.createParagraph().createRun().setText(
                    "${员工姓名} ${employeeIdCard} ${employeePhone} ${employeeAddress} "
                            + "${postName} ${contractStartDate} ${contractEndDate} ${probationStartDate} "
                            + "${probationEndDate} ${servicePersonType} ${baseSalary} ${postSalary} "
                            + "${fieldAllowance} ${salaryTotal} ${insuranceType}");
            document.write(output);
        }
        OaSignTemplate template = new OaSignTemplate();
        template.setTemplateType(OaSignTemplateType.ONBOARD_LABOR_CONTRACT);
        template.setTemplateId(10L);
        template.setFileUrl(file.toString());
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(100L);
        signPackage.setEmployeeNameSnapshot("张三");
        signPackage.setEmployeeIdCardSnapshot("TEST-ID-****-1234");
        signPackage.setEmployeePhoneSnapshot("13800000000");
        signPackage.setEmployeeAddressSnapshot("杭州市西湖区文三路1号");
        signPackage.setContractTermCodeSnapshot("FIXED_TERM");
        signPackage.setPostNameSnapshot("店长");
        signPackage.setContractStartDate("2026-07-01");
        signPackage.setContractEndDate("2029-06-30");
        signPackage.setProbationStartDate("2026-07-01");
        signPackage.setProbationEndDate("2026-12-31");
        signPackage.setServicePersonType("退休返聘");
        signPackage.setBaseSalary(new BigDecimal("3000.00"));
        signPackage.setPostSalary(new BigDecimal("500.00"));
        signPackage.setFieldAllowance(new BigDecimal("800.00"));
        signPackage.setSalaryTotal(new BigDecimal("4300.00"));
        signPackage.setInsuranceType("雇主责任险");

        GeneratedSignDocument generated = service.renderPackageDocument(signPackage, template);

        String renderedText;
        try (XWPFDocument document = new XWPFDocument(java.nio.file.Files.newInputStream(publicFile(generated.getSourceFileUrl()))))
        {
            renderedText = document.getParagraphs().get(0).getText();
        }
        assertThat(renderedText)
                .contains("张三", "TEST-ID-****-1234", "13800000000", "杭州市西湖区文三路1号",
                        "店长", "2026-07-01", "2029-06-30", "2026-12-31", "退休返聘",
                        "3000", "500", "800", "4300", "雇主责任险")
                .doesNotContain("${员工姓名}");
        assertThat(generated.getSourceFileHash()).hasSize(64);
        assertThat(generated.getReviewPdfHash()).hasSize(64).isNotEqualTo(generated.getSourceFileHash());
        assertThat(generated.getDocumentVersion()).isEqualTo("SP-100-V1");
        assertThat(java.nio.file.Files.readString(publicFile(generated.getReviewPdfUrl())
                , java.nio.charset.StandardCharsets.ISO_8859_1))
                .startsWith("%PDF-1.4");
    }

    @Test
    @DisplayName("exact-v7 源模板按 database-max 快照经正式文档服务渲染并唯一定位签署栏")
    void shouldRenderExactV7DatabaseMaxAndLocateSigningAnchors() throws Exception
    {
        Path templateFile = publishableExactV7Template();
        assertThat(templateFile).isRegularFile();
        assertThat(org.apache.commons.codec.digest.DigestUtils.sha256Hex(
                java.nio.file.Files.newInputStream(templateFile)))
                .isEqualTo("1226728ec0e703d513efda83dc5578ee4cdd65cb3e7d0e7cb5afee813f466558");

        OaSignTemplate template = new OaSignTemplate();
        template.setTemplateId(2026072109L);
        template.setTemplateType(OaSignTemplateType.ONBOARD_LABOR_CONTRACT);
        template.setTemplateVersion("20260721-v7");
        template.setFileUrl(templateFile.toString());
        template.setFileHash("1226728ec0e703d513efda83dc5578ee4cdd65cb3e7d0e7cb5afee813f466558");

        OaSignPackage signPackage = databaseMaxLaborPackage();
        GeneratedSignDocument generated = exactV7DocumentService().renderPackageDocument(
                signPackage, template, List.of(
                        OaSignTemplateType.ONBOARD_LABOR_CONTRACT,
                        OaSignTemplateType.ONBOARD_HANDBOOK_RECEIPT,
                        OaSignTemplateType.ONBOARD_SALARY_CONFIRM));
        Path pdf = publicFile(generated.getReviewPdfUrl());
        try (org.apache.pdfbox.pdmodel.PDDocument document =
                org.apache.pdfbox.Loader.loadPDF(pdf.toFile()))
        {
            int pages = document.getNumberOfPages();
            int primary = uniquePageContaining(document, "甲方盖章", "乙方签名", "甲方代表");
            int attachment = uniquePageContaining(document, "附件清单", "员工手册", "签名");
            int dormitory = uniquePageContaining(document, "职工宿舍免责协议书", "签名", "日期");
            int confirmation = uniquePageContaining(document, "确认人", "签名", "身份证号码", "日期");

            assertThat(List.of(primary, attachment, dormitory, confirmation))
                    .allMatch(page -> page > 0)
                    .isSorted();
            assertThat(normalizedPdfText(document))
                    .contains("签署完成后可在系统查看签署凭证及文件校验信息。")
                    .doesNotContain("见本合同末页《电子签署确认页》");
            System.out.printf("EXACT_V7_DATABASE_MAX_RESULT pages=%d primary=%d attachment=%d "
                            + "dormitory=%d confirmation=%d%n",
                    pages, primary, attachment, dormitory, confirmation);
            printSigningAnchorPages(document);
        }
        OaSignLaborPlacementProfileRegistry.PlacementProfile profile =
                new OaSignLaborAnchorPlacementResolver().resolve(pdf, false,
                        signPackage.getLegalRepresentativeSnapshot(), true, false);
        assertThat(profile.expectedBodyPageCount()).isEqualTo(23);
        assertThat(profile.signaturePlacements())
                .extracting(OaSignLaborPlacementProfileRegistry.PlacementRect::pageNumber)
                .containsExactly(17, 18, 21, 23);
        assertThat(profile.sealPlacements())
                .extracting(OaSignLaborPlacementProfileRegistry.PlacementRect::pageNumber)
                .containsExactly(17);
        assertThat(profile.textOverlays()).isEmpty();
        assertExactV7SequencePolicies(pdf, generated.getReviewPdfHash(), signPackage,
                23, List.of(17, 18, 21, 23));
    }

    @Test
    @DisplayName("exact-v7常规长度数据经正式文档服务渲染并直接通过动态锚点")
    void shouldRenderExactV7NormalAndResolveDynamicAnchors() throws Exception
    {
        Path templateFile = publishableExactV7Template();
        OaSignTemplate template = exactV7Template(templateFile);
        template.setFileHash(
                "1226728ec0e703d513efda83dc5578ee4cdd65cb3e7d0e7cb5afee813f466558");
        OaSignPackage signPackage = normalLaborPackage();

        GeneratedSignDocument generated = exactV7DocumentService().renderPackageDocument(
                signPackage, template, List.of(
                        OaSignTemplateType.ONBOARD_LABOR_CONTRACT,
                        OaSignTemplateType.ONBOARD_HANDBOOK_RECEIPT));
        Path pdf = publicFile(generated.getReviewPdfUrl());
        OaSignLaborPlacementProfileRegistry.PlacementProfile profile =
                new OaSignLaborAnchorPlacementResolver().resolve(pdf, false,
                        signPackage.getLegalRepresentativeSnapshot(), true, false);
        assertThat(profile.expectedBodyPageCount()).isEqualTo(18);
        assertThat(profile.signaturePlacements())
                .extracting(OaSignLaborPlacementProfileRegistry.PlacementRect::pageNumber)
                .containsExactly(14, 14, 16, 18);
        assertThat(profile.sealPlacements())
                .extracting(OaSignLaborPlacementProfileRegistry.PlacementRect::pageNumber)
                .containsExactly(14);
        assertThat(profile.textOverlays()).isEmpty();
        try (org.apache.pdfbox.pdmodel.PDDocument document =
                org.apache.pdfbox.Loader.loadPDF(pdf.toFile()))
        {
            assertThat(normalizedPdfText(document))
                    .contains("签署完成后可在系统查看签署凭证及文件校验信息。")
                    .doesNotContain("见本合同末页《电子签署确认页》");
        }

        assertExactV7SequencePolicies(pdf, generated.getReviewPdfHash(), signPackage,
                18, List.of(14, 14, 16, 18));
    }

    @Test
    @DisplayName("甲方代表字段为空而同名仅在附近其他行时不得误判已渲染")
    void shouldRejectRepresentativeNameOutsideLocalRepresentativeField() throws Exception
    {
        OaSignPackage signPackage = normalLaborPackage();
        GeneratedSignDocument generated = exactV7DocumentService().renderPackageDocument(
                signPackage, exactV7Template(publishableExactV7Template()), List.of(
                        OaSignTemplateType.ONBOARD_COMMITMENT,
                        OaSignTemplateType.ONBOARD_LABOR_CONTRACT,
                        OaSignTemplateType.ONBOARD_HANDBOOK_RECEIPT,
                        OaSignTemplateType.ONBOARD_SALARY_CONFIRM));
        Path renderedDocx = publicFile(generated.getSourceFileUrl());
        Path modifiedDocx = tempDir.resolve("representative-name-nearby-not-in-field.docx");
        try (XWPFDocument document = new XWPFDocument(
                java.nio.file.Files.newInputStream(renderedDocx));
                OutputStream output = java.nio.file.Files.newOutputStream(modifiedDocx))
        {
            replaceUniqueParagraphText(document,
                    "甲方代表：" + signPackage.getLegalRepresentativeSnapshot(),
                    "甲方代表：____________");
            replaceUniqueParagraphText(document,
                    "签署完成后可在系统查看签署凭证及文件校验信息。",
                    "签署完成后可在系统查看签署凭证及文件校验信息。 "
                            + signPackage.getLegalRepresentativeSnapshot());
            document.write(output);
        }
        OaSignFileProperties properties = new OaSignFileProperties();
        properties.getPdf().setConverterCommand(resolveOfficeCommand());
        properties.getPdf().setTimeoutSeconds(180);
        Path nearbyOnly = new OaOfficePdfConverter(properties).convert(modifiedDocx,
                tempDir.resolve("representative-nearby-pdf"));
        OaSignLaborAnchorPlacementResolver resolver =
                new OaSignLaborAnchorPlacementResolver();

        try (org.apache.pdfbox.pdmodel.PDDocument document =
                org.apache.pdfbox.Loader.loadPDF(nearbyOnly.toFile()))
        {
            assertThat(normalizedPdfText(document))
                    .contains(signPackage.getLegalRepresentativeSnapshot(),
                            "甲方代表:____________");
        }
        assertThat(resolver.resolve(nearbyOnly, false,
                signPackage.getLegalRepresentativeSnapshot(), true, true).textOverlays())
                .extracting(OaSignLaborPlacementProfileRegistry.TextRect::field)
                .contains("companyLegalRepresentative");
        assertThatThrownBy(() -> resolver.resolve(nearbyOnly, false,
                signPackage.getLegalRepresentativeSnapshot(), true, false))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("冻结甲方代表未落入劳动合同签署栏");
    }

    @Test
    @DisplayName("员工手册勾选只读取第3项同行最近标记而不误取第2项")
    void shouldRejectCheckedSecondItemWhenHandbookItemIsUnchecked() throws Exception
    {
        Path templateFile = publishableExactV7Template();
        OaSignTemplate template = exactV7Template(templateFile);
        template.setFileHash(
                "1226728ec0e703d513efda83dc5578ee4cdd65cb3e7d0e7cb5afee813f466558");
        OaSignPackage signPackage = normalLaborPackage();

        GeneratedSignDocument generated = exactV7DocumentService().renderPackageDocument(
                signPackage, template, List.of(
                        OaSignTemplateType.ONBOARD_LABOR_CONTRACT,
                        OaSignTemplateType.ONBOARD_SALARY_CONFIRM));
        Path pdf = publicFile(generated.getReviewPdfUrl());
        OaSignLaborAnchorPlacementResolver resolver =
                new OaSignLaborAnchorPlacementResolver();

        assertThatThrownBy(() -> resolver.resolve(pdf, false,
                signPackage.getLegalRepresentativeSnapshot(), true, false))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("员工手册附件勾选未落入核准局部区域");
        assertThat(resolver.resolve(pdf, false,
                signPackage.getLegalRepresentativeSnapshot(), false, false)
                .signaturePlacements()).hasSize(4);
    }

    @Test
    @DisplayName("v7实际源字节被替换时不信任数据库中的正确hash")
    void shouldRejectTamperedExactV7BytesEvenWhenRecordedHashIsApproved() throws Exception
    {
        Path approved = publishableExactV7Template();
        byte[] tamperedBytes = java.nio.file.Files.readAllBytes(approved);
        tamperedBytes[tamperedBytes.length - 1] ^= 0x01;
        Path tampered = tempDir.resolve("tampered-v7.docx");
        java.nio.file.Files.write(tampered, tamperedBytes);

        OaSignTemplate template = exactV7Template(tampered);
        template.setFileHash(
                "1226728ec0e703d513efda83dc5578ee4cdd65cb3e7d0e7cb5afee813f466558");

        assertThatThrownBy(() -> documentService().renderPackageDocument(
                databaseMaxLaborPackage(), template,
                List.of(OaSignTemplateType.ONBOARD_LABOR_CONTRACT)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("模板源文件与冻结指纹不一致");
    }

    @Test
    @DisplayName("final/retry快照未携数据库hash时仍按v7实际源字节失败关闭")
    void shouldRejectTamperedExactV7BytesWhenRerenderSnapshotHasNoFileHash() throws Exception
    {
        Path approved = publishableExactV7Template();
        byte[] tamperedBytes = java.nio.file.Files.readAllBytes(approved);
        tamperedBytes[tamperedBytes.length - 2] ^= 0x01;
        Path tampered = tempDir.resolve("tampered-v7-rerender.docx");
        java.nio.file.Files.write(tampered, tamperedBytes);

        OaSignTemplate snapshotTemplate = exactV7Template(tampered);
        assertThat(snapshotTemplate.getFileHash()).isNull();

        assertThatThrownBy(() -> documentService().renderPackageDocument(
                databaseMaxLaborPackage(), snapshotTemplate,
                List.of(OaSignTemplateType.ONBOARD_LABOR_CONTRACT)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("劳动合同v7源文件指纹不匹配");
    }

    @Test
    @DisplayName("final/retry快照无数据库hash时仍可由核准v7实际源字节完成渲染")
    void shouldRenderApprovedExactV7BytesWhenRerenderSnapshotHasNoFileHash() throws Exception
    {
        Path approved = publishableExactV7Template();
        OaSignTemplate snapshotTemplate = exactV7Template(approved);
        assertThat(snapshotTemplate.getFileHash()).isNull();

        OaSignPackage signPackage = normalLaborPackage();
        assertThat(org.apache.commons.codec.digest.DigestUtils.sha256Hex(
                java.nio.file.Files.newInputStream(approved)))
                .isEqualTo("1226728ec0e703d513efda83dc5578ee4cdd65cb3e7d0e7cb5afee813f466558");
        GeneratedSignDocument generated = exactV7DocumentService().renderPackageDocument(
                signPackage, snapshotTemplate, List.of(
                        OaSignTemplateType.ONBOARD_COMMITMENT,
                        OaSignTemplateType.ONBOARD_LABOR_CONTRACT,
                        OaSignTemplateType.ONBOARD_HANDBOOK_RECEIPT,
                        OaSignTemplateType.ONBOARD_SALARY_CONFIRM));
        Path pdf = publicFile(generated.getReviewPdfUrl());

        assertThat(generated.getSourceFileHash()).matches("[0-9a-f]{64}");
        OaSignLaborPlacementProfileRegistry.PlacementProfile profile =
                new OaSignLaborAnchorPlacementResolver().resolve(pdf, false,
                        signPackage.getLegalRepresentativeSnapshot(), true, true);
        assertThat(profile.expectedBodyPageCount()).isEqualTo(18);
        assertThat(profile.signaturePlacements())
                .extracting(OaSignLaborPlacementProfileRegistry.PlacementRect::pageNumber)
                .containsExactly(14, 14, 16, 18);

        Path ambiguous = tempDir.resolve("exact-v7-ambiguous-primary-anchor.pdf");
        try (org.apache.pdfbox.pdmodel.PDDocument duplicated =
                org.apache.pdfbox.Loader.loadPDF(pdf.toFile()))
        {
            duplicated.importPage(duplicated.getPage(13));
            duplicated.save(ambiguous.toFile());
        }
        assertThatThrownBy(() -> new OaSignLaborAnchorPlacementResolver().resolve(
                ambiguous, false, signPackage.getLegalRepresentativeSnapshot(), true, true))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("锚点必须唯一");
    }

    @Test
    @DisplayName("合同期限代码同时渲染A或B选择和互斥勾选框")
    void shouldRenderDynamicContractTermSelectionAndMarks() throws Exception
    {
        OaSignDocumentService service = documentService();
        Path file = tempDir.resolve("labor-contract-term.docx");
        try (XWPFDocument document = new XWPFDocument();
                OutputStream output = java.nio.file.Files.newOutputStream(file))
        {
            document.createParagraph().createRun().setText(
                    "第${contractTermSelection}种 ${contractTermFixedMark}固定期限 "
                            + "${contractTermOpenEndedMark}无固定期限");
            document.write(output);
        }
        OaSignTemplate template = new OaSignTemplate();
        template.setTemplateType(OaSignTemplateType.ONBOARD_LABOR_CONTRACT);
        template.setTemplateId(13L);
        template.setFileUrl(file.toString());

        OaSignPackage fixedTerm = new OaSignPackage();
        fixedTerm.setPackageId(114L);
        fixedTerm.setContractTermCodeSnapshot("FIXED_TERM");
        GeneratedSignDocument fixedGenerated = service.renderPackageDocument(fixedTerm, template);
        try (XWPFDocument rendered = new XWPFDocument(
                java.nio.file.Files.newInputStream(publicFile(fixedGenerated.getSourceFileUrl()))))
        {
            assertThat(rendered.getParagraphs().get(0).getText())
                    .isEqualTo("第A种 ☑固定期限 □无固定期限");
        }

        OaSignPackage openEnded = new OaSignPackage();
        openEnded.setPackageId(115L);
        openEnded.setContractTermCodeSnapshot("OPEN_ENDED");
        GeneratedSignDocument openGenerated = service.renderPackageDocument(openEnded, template);
        try (XWPFDocument rendered = new XWPFDocument(
                java.nio.file.Files.newInputStream(publicFile(openGenerated.getSourceFileUrl()))))
        {
            assertThat(rendered.getParagraphs().get(0).getText())
                    .isEqualTo("第B种 □固定期限 ☑无固定期限");
        }
    }

    @Test
    @DisplayName("劳务签收单按人员类型和保险类型渲染互斥勾选框并拒绝无效保险")
    void shouldRenderServiceReceiptSelectionMarksAndRejectInvalidInsurance() throws Exception
    {
        OaSignDocumentService service = documentService();
        Path file = tempDir.resolve("service-receipt-selections.docx");
        try (XWPFDocument document = new XWPFDocument();
                OutputStream output = java.nio.file.Files.newOutputStream(file))
        {
            document.createParagraph().createRun().setText(
                    "${serviceStudentMark}在校实习生 ${serviceRetiredMark}退休返聘人员 "
                            + "${insuranceCommercialAccidentMark}商业意外保险 "
                            + "${insuranceEmployerLiabilityMark}雇主责任险");
            document.write(output);
        }
        OaSignTemplate template = new OaSignTemplate();
        template.setTemplateType(OaSignTemplateType.ONBOARD_SERVICE_RECEIPT);
        template.setTemplateId(14L);
        template.setFileUrl(file.toString());

        OaSignPackage retired = new OaSignPackage();
        retired.setPackageId(116L);
        retired.setServicePersonType("RETIRED_REHIRE");
        retired.setInsuranceType("EMPLOYER_LIABILITY");
        GeneratedSignDocument generated = service.renderPackageDocument(retired, template);
        try (XWPFDocument rendered = new XWPFDocument(
                java.nio.file.Files.newInputStream(publicFile(generated.getSourceFileUrl()))))
        {
            assertThat(rendered.getParagraphs().get(0).getText())
                    .isEqualTo("□在校实习生 ☑退休返聘人员 □商业意外保险 ☑雇主责任险");
        }

        OaSignPackage invalid = new OaSignPackage();
        invalid.setPackageId(117L);
        invalid.setServicePersonType("退休返聘");
        invalid.setInsuranceType("无");
        assertThatThrownBy(() -> service.renderPackageDocument(invalid, template))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("保险类型必须为商业意外保险或雇主责任险");
    }

    @Test
    @DisplayName("劳动合同附件方框按实际包文件勾选并保留专用字体")
    void shouldRenderActualPackageAttachmentChecklistWithRealCheckboxRuns() throws Exception
    {
        OaSignDocumentService service = documentService();
        Path file = tempDir.resolve("labor-attachment-checklist.docx");
        try (XWPFDocument document = new XWPFDocument();
                OutputStream output = java.nio.file.Files.newOutputStream(file))
        {
            for (String marker : List.of(
                    "attachmentDormitoryMark",
                    "attachmentDutyMark",
                    "attachmentHandbookMark",
                    "attachmentSalaryMark"))
            {
                org.apache.poi.xwpf.usermodel.XWPFParagraph paragraph = document.createParagraph();
                org.apache.poi.xwpf.usermodel.XWPFRun markerRun = paragraph.createRun();
                markerRun.setFontFamily("DejaVu Sans");
                markerRun.setText("${" + marker + "}");
                paragraph.createRun().setText(" checklist item");
            }
            document.write(output);
        }
        OaSignTemplate template = new OaSignTemplate();
        template.setTemplateType(OaSignTemplateType.ONBOARD_LABOR_CONTRACT);
        template.setTemplateId(11L);
        template.setFileUrl(file.toString());
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(111L);
        signPackage.setContractTermCodeSnapshot("FIXED_TERM");

        GeneratedSignDocument generated = service.renderPackageDocument(signPackage, template,
                List.of(OaSignTemplateType.ONBOARD_LABOR_CONTRACT,
                        OaSignTemplateType.ONBOARD_SALARY_CONFIRM));

        try (XWPFDocument rendered = new XWPFDocument(
                java.nio.file.Files.newInputStream(publicFile(generated.getSourceFileUrl()))))
        {
            assertThat(rendered.getParagraphs()).extracting(
                    org.apache.poi.xwpf.usermodel.XWPFParagraph::getText)
                    .containsExactly("☑ checklist item", "☑ checklist item",
                            "□ checklist item", "☑ checklist item")
                    .allSatisfy(text -> assertThat(text).doesNotContain("${"));
            assertThat(rendered.getParagraphs())
                    .allSatisfy(paragraph -> assertThat(paragraph.getRuns().get(0).getFontFamily())
                            .isEqualTo("DejaVu Sans"));
        }

        OaSignPackage laborOnlyPackage = new OaSignPackage();
        laborOnlyPackage.setPackageId(113L);
        laborOnlyPackage.setContractTermCodeSnapshot("FIXED_TERM");
        GeneratedSignDocument laborOnly = service.renderPackageDocument(laborOnlyPackage, template,
                List.of(OaSignTemplateType.ONBOARD_LABOR_CONTRACT));
        try (XWPFDocument rendered = new XWPFDocument(
                java.nio.file.Files.newInputStream(publicFile(laborOnly.getSourceFileUrl()))))
        {
            assertThat(rendered.getParagraphs()).extracting(
                    org.apache.poi.xwpf.usermodel.XWPFParagraph::getText)
                    .containsExactly("☑ checklist item", "☑ checklist item",
                            "□ checklist item", "□ checklist item")
                    .allSatisfy(text -> assertThat(text).doesNotContain("${"));
        }
    }

    @Test
    @DisplayName("员工手册签收确认书也会勾选劳动合同附件中的员工手册")
    void shouldMarkHandbookWhenOnlyReceiptTemplateIsIncluded() throws Exception
    {
        OaSignDocumentService service = documentService();
        Path file = tempDir.resolve("labor-handbook-receipt-checklist.docx");
        try (XWPFDocument document = new XWPFDocument();
                OutputStream output = java.nio.file.Files.newOutputStream(file))
        {
            document.createParagraph().createRun().setText("${attachmentHandbookMark}");
            document.write(output);
        }
        OaSignTemplate template = new OaSignTemplate();
        template.setTemplateType(OaSignTemplateType.ONBOARD_LABOR_CONTRACT);
        template.setTemplateId(114L);
        template.setFileUrl(file.toString());
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(114L);
        signPackage.setContractTermCodeSnapshot("FIXED_TERM");

        GeneratedSignDocument generated = service.renderPackageDocument(signPackage, template,
                List.of(OaSignTemplateType.ONBOARD_LABOR_CONTRACT,
                        OaSignTemplateType.ONBOARD_HANDBOOK_RECEIPT));

        try (XWPFDocument rendered = new XWPFDocument(
                java.nio.file.Files.newInputStream(publicFile(generated.getSourceFileUrl()))))
        {
            assertThat(rendered.getParagraphs()).extracting(
                    org.apache.poi.xwpf.usermodel.XWPFParagraph::getText)
                    .containsExactly("☑");
        }
    }

    @Test
    @DisplayName("合同预览优先使用法律主体冻结的甲方代表而非推荐值")
    void shouldRenderFrozenLegalRepresentativeBeforeEmployeeSigns() throws Exception
    {
        OaSignDocumentService service = documentService();
        Path file = tempDir.resolve("labor-legal-representative.docx");
        try (XWPFDocument document = new XWPFDocument();
                OutputStream output = java.nio.file.Files.newOutputStream(file))
        {
            document.createParagraph().createRun().setText("甲方代表：${companyLegalRepresentative}");
            document.write(output);
        }
        OaSignTemplate template = new OaSignTemplate();
        template.setTemplateType(OaSignTemplateType.ONBOARD_LABOR_CONTRACT);
        template.setTemplateId(115L);
        template.setFileUrl(file.toString());
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(115L);
        signPackage.setContractTermCodeSnapshot("FIXED_TERM");
        signPackage.setLegalEntityNameSnapshot("冻结主体甲公司");
        signPackage.setRecommendedCompanySnapshot("推荐主体乙公司");
        signPackage.setLegalRepresentativeSnapshot("冻结代表甲");
        signPackage.setRecommendedLegalRepresentativeSnapshot("推荐代表乙");

        GeneratedSignDocument generated = service.renderPackageDocument(signPackage, template,
                List.of(OaSignTemplateType.ONBOARD_LABOR_CONTRACT));

        try (XWPFDocument rendered = new XWPFDocument(
                java.nio.file.Files.newInputStream(publicFile(generated.getSourceFileUrl()))))
        {
            assertThat(rendered.getParagraphs()).extracting(
                    org.apache.poi.xwpf.usermodel.XWPFParagraph::getText)
                    .containsExactly("甲方代表：冻结代表甲")
                    .doesNotContain("推荐代表乙", "推荐主体乙公司");
        }
    }

    @Test
    @DisplayName("法律主体已冻结但缺少代表人时不使用推荐代表")
    void shouldFailClosedWhenFrozenLegalRepresentativeIsMissing() throws Exception
    {
        OaSignDocumentService service = documentService();
        Path file = tempDir.resolve("labor-missing-legal-representative.docx");
        try (XWPFDocument document = new XWPFDocument();
                OutputStream output = java.nio.file.Files.newOutputStream(file))
        {
            document.createParagraph().createRun().setText("甲方代表：${companyLegalRepresentative}");
            document.write(output);
        }
        OaSignTemplate template = new OaSignTemplate();
        template.setTemplateType(OaSignTemplateType.ONBOARD_LABOR_CONTRACT);
        template.setTemplateId(116L);
        template.setFileUrl(file.toString());
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(116L);
        signPackage.setContractTermCodeSnapshot("FIXED_TERM");
        signPackage.setLegalEntityIdSnapshot(301L);
        signPackage.setRecommendedLegalRepresentativeSnapshot("推荐代表");

        assertThatThrownBy(() -> service.renderPackageDocument(signPackage, template,
                List.of(OaSignTemplateType.ONBOARD_LABOR_CONTRACT)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("冻结甲方代表缺失");
    }

    @Test
    @DisplayName("文档渲染后仍有未赋值占位符时拒绝生成PDF")
    void shouldRejectRenderedDocxWithUnresolvedPlaceholder() throws Exception
    {
        OaSignDocumentService service = documentService();
        Path file = tempDir.resolve("unresolved-placeholder.docx");
        try (XWPFDocument document = new XWPFDocument();
                OutputStream output = java.nio.file.Files.newOutputStream(file))
        {
            document.createParagraph().createRun().setText("${unknownRuntimeValue}");
            document.write(output);
        }
        OaSignTemplate template = new OaSignTemplate();
        template.setTemplateType(OaSignTemplateType.ONBOARD_COMMITMENT);
        template.setTemplateId(12L);
        template.setFileUrl(file.toString());
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(112L);

        assertThatThrownBy(() -> service.renderPackageDocument(signPackage, template))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("模板渲染后仍含未赋值占位符")
                .hasMessageContaining("${unknownRuntimeValue}");
    }

    @Test
    @DisplayName("转正薪资材料渲染实际转正日五项金额和薪资版本")
    void shouldRenderRegularizationSalarySnapshotPlaceholders() throws Exception
    {
        OaSignDocumentService service = documentService();
        Path file = tempDir.resolve("regularize-salary.docx");
        try (XWPFDocument document = new XWPFDocument();
                OutputStream output = java.nio.file.Files.newOutputStream(file))
        {
            document.createParagraph().createRun().setText(
                    "${employeeName} ${employeeIdCard} ${actualRegularizationDate} "
                            + "${baseSalary} ${postSalary} ${fieldAllowance} "
                            + "${performanceSalary} ${salaryTotal} ${salaryVersion}");
            document.write(output);
        }
        OaSignTemplate template = new OaSignTemplate();
        template.setTemplateType(OaSignTemplateType.REGULARIZE_SALARY_CONFIRM);
        template.setTemplateId(25L);
        template.setFileUrl(file.toString());
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(125L);
        signPackage.setEmployeeNameSnapshot("张三");
        signPackage.setEmployeeIdCardSnapshot("TEST-ID-****-1234");
        signPackage.setActualRegularizationDate("2026-07-10");
        signPackage.setBaseSalary(new BigDecimal("5000.00"));
        signPackage.setPostSalary(new BigDecimal("2000.00"));
        signPackage.setFieldAllowance(new BigDecimal("300.00"));
        signPackage.setPerformanceSalary(new BigDecimal("700.00"));
        signPackage.setSalaryTotal(new BigDecimal("8000.00"));
        signPackage.setSalaryVersion("2026-V1");

        GeneratedSignDocument generated = service.renderPackageDocument(signPackage, template);

        try (XWPFDocument document = new XWPFDocument(
                java.nio.file.Files.newInputStream(publicFile(generated.getSourceFileUrl()))))
        {
            assertThat(document.getParagraphs().get(0).getText())
                    .contains("张三", "TEST-ID-****-1234", "2026-07-10",
                            "5000", "2000", "300", "700", "8000", "2026年第1版")
                    .doesNotContain("${actualRegularizationDate}", "${salaryVersion}");
        }
    }

    @Test
    @DisplayName("续签模板渲染原合同日期类型和续签次数快照")
    void shouldRenderRenewalSafetySnapshotPlaceholders() throws Exception
    {
        OaSignDocumentService service = documentService();
        Path file = tempDir.resolve("renewal-labor.docx");
        try (XWPFDocument document = new XWPFDocument();
                OutputStream output = java.nio.file.Files.newOutputStream(file))
        {
            document.createParagraph().createRun().setText(
                    "${companyName} ${previousContractEndDate} ${previousEmploymentType} "
                            + "${previousRenewalCount} ${renewalCount} ${contractStartDate} ${contractEndDate}");
            document.write(output);
        }
        OaSignTemplate template = new OaSignTemplate();
        template.setTemplateType(OaSignTemplateType.RENEWAL_LABOR_CONTRACT);
        template.setTemplateId(125L);
        template.setFileUrl(file.toString());
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(225L);
        signPackage.setLegalEntityNameSnapshot("上海示例餐饮有限公司");
        signPackage.setPreviousContractEndDate("2026-06-30");
        signPackage.setPreviousEmploymentType("劳动合同");
        signPackage.setPreviousRenewalCount(1);
        signPackage.setRenewalCount(2);
        signPackage.setContractStartDate("2026-07-01");
        signPackage.setContractEndDate("2029-06-30");

        GeneratedSignDocument generated = service.renderPackageDocument(signPackage, template);

        try (XWPFDocument document = new XWPFDocument(
                java.nio.file.Files.newInputStream(publicFile(generated.getSourceFileUrl()))))
        {
            assertThat(document.getParagraphs().get(0).getText())
                    .contains("上海示例餐饮有限公司", "2026-06-30", "劳动合同",
                            "1", "2", "2026-07-01", "2029-06-30")
                    .doesNotContain("${previousContractEndDate}", "${renewalCount}");
        }
    }

    @Test
    @DisplayName("公司名称占位符只渲染冻结法律主体名称")
    void shouldRenderCompanyNameFromLegalEntitySnapshot() throws Exception
    {
        OaSignDocumentService service = documentService();
        Path file = tempDir.resolve("leave-certificate.docx");
        try (XWPFDocument document = new XWPFDocument();
                OutputStream output = java.nio.file.Files.newOutputStream(file))
        {
            document.createParagraph().createRun().setText("${companyName}");
            document.write(output);
        }
        OaSignTemplate template = new OaSignTemplate();
        template.setTemplateType(OaSignTemplateType.OFFBOARD_LEAVE_CERTIFICATE);
        template.setTemplateId(26L);
        template.setFileUrl(file.toString());
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(126L);
        signPackage.setShopDeptName("徐汇门店");
        signPackage.setLegalEntityNameSnapshot("上海示例餐饮有限公司");

        GeneratedSignDocument generated = service.renderPackageDocument(signPackage, template);

        try (XWPFDocument document = new XWPFDocument(
                java.nio.file.Files.newInputStream(publicFile(generated.getSourceFileUrl()))))
        {
            assertThat(document.getParagraphs().get(0).getText())
                    .isEqualTo("上海示例餐饮有限公司")
                    .doesNotContain("徐汇门店");
        }
    }

    @Test
    @DisplayName("未选公司时不得把内部待确认状态渲染进合同")
    void shouldNotRenderPendingCompanyPlaceholder() throws Exception
    {
        OaSignDocumentService service = documentService();
        Path file = tempDir.resolve("pending-company.docx");
        try (XWPFDocument document = new XWPFDocument();
                OutputStream output = java.nio.file.Files.newOutputStream(file))
        {
            document.createParagraph().createRun().setText("甲方：${companyName}");
            document.write(output);
        }
        OaSignTemplate template = new OaSignTemplate();
        template.setTemplateType(OaSignTemplateType.ONBOARD_CONFIDENTIAL_NONCOMPETE);
        template.setTemplateId(126L);
        template.setFileUrl(file.toString());
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(226L);

        GeneratedSignDocument generated = service.renderPackageDocument(signPackage, template);

        try (XWPFDocument document = new XWPFDocument(
                java.nio.file.Files.newInputStream(publicFile(generated.getSourceFileUrl()))))
        {
            assertThat(document.getParagraphs().get(0).getText())
                    .isEqualTo("甲方：")
                    .doesNotContain("待 HR 确认");
        }
    }

    @Test
    @DisplayName("未成年非在校声明渲染个人劳动收入起始年月")
    void shouldRenderMinorDeclarationIncomeStartYearMonth() throws Exception
    {
        OaSignDocumentService service = documentService();
        Path file = tempDir.resolve("minor-declaration.docx");
        try (XWPFDocument document = new XWPFDocument();
                OutputStream output = java.nio.file.Files.newOutputStream(file))
        {
            document.createParagraph().createRun().setText(
                    "${employeeName} ${employeeIdCard} ${incomeStartYearMonth} ${signDate}");
            document.write(output);
        }
        OaSignTemplate template = new OaSignTemplate();
        template.setTemplateType(OaSignTemplateType.ONBOARD_MINOR_NONSTUDENT_DECLARATION);
        template.setTemplateId(127L);
        template.setFileUrl(file.toString());
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(227L);
        signPackage.setEmployeeNameSnapshot("小李");
        signPackage.setEmployeeIdCardSnapshot("TEST-ID-****-1234");
        signPackage.setIncomeStartYearMonth("2025-09");

        GeneratedSignDocument generated = service.renderPackageDocument(signPackage, template);

        try (XWPFDocument document = new XWPFDocument(
                java.nio.file.Files.newInputStream(publicFile(generated.getSourceFileUrl()))))
        {
            assertThat(document.getParagraphs().get(0).getText())
                    .contains("小李", "TEST-ID-****-1234", "2025-09")
                    .doesNotContain("${incomeStartYearMonth}");
        }
    }

    @Test
    @DisplayName("离职结算材料只渲染冻结离职快照占位符")
    void shouldRenderOffboardingSettlementSnapshotPlaceholders() throws Exception
    {
        OaSignDocumentService service = documentService();
        Path file = tempDir.resolve("offboard-settlement.docx");
        try (XWPFDocument document = new XWPFDocument();
                OutputStream output = java.nio.file.Files.newOutputStream(file))
        {
            document.createParagraph().createRun().setText(
                    "${employeeName} ${employeeIdCard} ${leaveDate} "
                            + "${salarySettlementStatus} ${compensationAmount} "
                            + "${compensationNote} ${signDate}");
            document.write(output);
        }
        OaSignTemplate template = new OaSignTemplate();
        template.setTemplateType(OaSignTemplateType.OFFBOARD_SETTLEMENT);
        template.setTemplateId(35L);
        template.setFileUrl(file.toString());
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(135L);
        signPackage.setEmployeeNameSnapshot("张三");
        signPackage.setEmployeeIdCardSnapshot("TEST-ID-****-1234");
        signPackage.setLeaveDate("2026-12-31");
        signPackage.setSalarySettlementStatus("PENDING");
        signPackage.setCompensationAmount(new BigDecimal("20000.00"));
        signPackage.setCompensationNote("双方协商补偿");

        GeneratedSignDocument generated = service.renderPackageDocument(signPackage, template);

        try (XWPFDocument document = new XWPFDocument(
                java.nio.file.Files.newInputStream(publicFile(generated.getSourceFileUrl()))))
        {
            assertThat(document.getParagraphs().get(0).getText())
                    .contains("张三", "TEST-ID-****-1234", "2026-12-31",
                            "待完成", "20000", "双方协商补偿")
                    .doesNotContain("${salarySettlementStatus}",
                            "${compensationAmount}", "${compensationNote}");
        }
    }

    @Test
    @DisplayName("发送签约包时将xlsx模板占位符渲染成员工文件")
    void shouldRenderXlsxTemplateWithPackagePlaceholders() throws Exception
    {
        OaSignDocumentService service = documentService();
        Path file = tempDir.resolve("application.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook(); OutputStream output = java.nio.file.Files.newOutputStream(file))
        {
            workbook.createSheet("登记表").createRow(0).createCell(0)
                    .setCellValue("${employeeName} ${employeeIdCard} ${employeePhone} ${entryDate} ${postName}");
            workbook.write(output);
        }
        OaSignTemplate template = new OaSignTemplate();
        template.setTemplateType(OaSignTemplateType.ONBOARD_APPLICATION_FORM);
        template.setTemplateId(20L);
        template.setFileUrl(file.toString());
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(101L);
        signPackage.setEmployeeNameSnapshot("王五");
        signPackage.setEmployeeIdCardSnapshot("TEST-ID-****-1234");
        signPackage.setEmployeePhoneSnapshot("13700000000");
        signPackage.setEntryDate("2026-07-08");
        signPackage.setPostNameSnapshot("运营经理");

        GeneratedSignDocument generated = service.renderPackageDocument(signPackage, template);

        Path rendered = publicFile(generated.getSourceFileUrl());
        try (Workbook workbook = WorkbookFactory.create(java.nio.file.Files.newInputStream(rendered)))
        {
            String renderedText = workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue();
            assertThat(renderedText)
                    .contains("王五", "TEST-ID-****-1234", "13700000000", "2026-07-08", "运营经理")
                    .doesNotContain("${employeeName}");
        }
        assertThat(generated.getSourceFileHash()).hasSize(64);
        assertThat(generated.getReviewPdfUrl()).endsWith(".pdf");
        assertThat(generated.getReviewPdfHash()).hasSize(64).isNotEqualTo(generated.getSourceFileHash());
        assertThat(generated.getDocumentVersion()).isEqualTo("SP-101-V1");
        assertThat(publicFile(generated.getReviewPdfUrl())).isNotEmptyFile();
    }

    @Test
    @DisplayName("Office转换失败时整个员工文档生成失败且不归档源文件")
    void shouldFailWholeDocumentWhenPdfConversionFails() throws Exception
    {
        OaSignDocumentService service = documentService("exit 9");
        Path file = tempDir.resolve("failed.docx");
        try (XWPFDocument document = new XWPFDocument(); OutputStream output = java.nio.file.Files.newOutputStream(file))
        {
            document.createParagraph().createRun().setText("${employeeName}");
            document.write(output);
        }
        OaSignTemplate template = new OaSignTemplate();
        template.setTemplateType(OaSignTemplateType.ONBOARD_COMMITMENT);
        template.setTemplateId(30L);
        template.setFileUrl(file.toString());
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(102L);
        signPackage.setEmployeeNameSnapshot("赵六");

        assertThatThrownBy(() -> service.renderPackageDocument(signPackage, template))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("PDF_CONVERSION_FAILED");
        Path archiveRoot = tempDir.resolve("private/sign-package");
        if (java.nio.file.Files.exists(archiveRoot))
        {
            try (java.util.stream.Stream<Path> files = java.nio.file.Files.walk(archiveRoot))
            {
                assertThat(files.filter(java.nio.file.Files::isRegularFile)).isEmpty();
            }
        }
    }

    @Test
    @DisplayName("阅读PDF归档失败时立即按路径和hash清理已归档源文件")
    void shouldDiscardFirstPromotedFileWhenSecondPromotionFails() throws Exception
    {
        Path templateFile = tempDir.resolve("partial-promote.docx");
        try (XWPFDocument document = new XWPFDocument();
                OutputStream output = java.nio.file.Files.newOutputStream(templateFile))
        {
            document.createParagraph().createRun().setText("${employeeName}");
            document.write(output);
        }
        Path convertedPdf = tempDir.resolve("converted.pdf");
        java.nio.file.Files.writeString(convertedPdf, "%PDF-1.4");
        OaSignFileStorageService storage = mock(OaSignFileStorageService.class);
        OaOfficePdfConverter converter = mock(OaOfficePdfConverter.class);
        OaSignDocumentService service = new OaSignDocumentService();
        ReflectionTestUtils.setField(service, "localFilePath", tempDir.toString());
        ReflectionTestUtils.setField(service, "localFilePrefix", "/profile");
        ReflectionTestUtils.setField(service, "fileStorageService", storage);
        ReflectionTestUtils.setField(service, "officePdfConverter", converter);
        StagedSignFile source = staged("source.docx", "source/path.docx", "source-hash");
        StagedSignFile review = staged("review.pdf", "review/path.pdf", "review-hash");
        String type = OaSignTemplateType.ONBOARD_COMMITMENT;
        when(storage.stage(isNull(), eq(900L), eq("SP-900-V1"),
                eq(type + "-30.docx"), any(byte[].class))).thenReturn(source);
        when(converter.convert(source.getTempPath(), source.getStagingDirectory())).thenReturn(convertedPdf);
        when(storage.stage(isNull(), eq(900L), eq("SP-900-V1"),
                eq(type + "-30.pdf"), any(byte[].class))).thenReturn(review);
        when(storage.promote(source)).thenAnswer(invocation -> {
            source.markPromoted(tempDir.resolve("archive/source.docx"),
                    "/profile/private/sign-package/source/path.docx");
            return source;
        });
        when(storage.promote(review)).thenThrow(new ServiceException("第二个归档失败"));
        OaSignTemplate template = new OaSignTemplate();
        template.setTemplateId(30L);
        template.setTemplateType(type);
        template.setFileUrl(templateFile.toString());
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(900L);
        signPackage.setEmployeeNameSnapshot("测试员工");

        assertThatThrownBy(() -> service.renderPackageDocument(signPackage, template))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("第二个归档失败");

        verify(storage).discardUncommitted("source/path.docx", "source-hash");
    }

    @Test
    @DisplayName("事务回滚清理同时覆盖生成源文件和阅读PDF")
    void shouldDiscardBothGeneratedFilesOnRollback()
    {
        OaSignFileStorageService storage = mock(OaSignFileStorageService.class);
        OaSignDocumentService service = new OaSignDocumentService();
        ReflectionTestUtils.setField(service, "fileStorageService", storage);
        GeneratedSignDocument generated = new GeneratedSignDocument(
                "/source.docx", "source-hash", "/review.pdf", "review-hash", "SP-1-V1",
                "source/path.docx", 10L, "review/path.pdf", 20L);

        service.discardUncommitted(generated);

        verify(storage).discardUncommitted("source/path.docx", "source-hash");
        verify(storage).discardUncommitted("review/path.pdf", "review-hash");
    }

    @Test
    @DisplayName("签约包签署时生成签名图片和证明PDF")
    void shouldGenerateSignCertificateWithSignatureImage() throws Exception
    {
        OaSignDocumentService service = documentService();
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(200L);
        signPackage.setPackageNo("SP202607020001");
        signPackage.setDocumentVersion("SP-200-V1");
        signPackage.setEmployeeNameSnapshot("李四");
        signPackage.setEmployeePhoneSnapshot("13900000000");
        signPackage.setEmployeeIdCardSnapshot("TEST-ID-****-1234");
        signPackage.setScenario("onboard");
        signPackage.setEmploymentType("劳动合同");
        signPackage.setPostNameSnapshot("营业员");
        OaSignPackageDocument document = new OaSignPackageDocument();
        document.setDocumentName("入职承诺书");
        document.setTemplateType(OaSignTemplateType.ONBOARD_COMMITMENT);
        document.setEmployeeSignRequired("Y");
        document.setFileHashBeforeSign("doc-hash");
        document.setReviewPdfHash("review-hash");
        document.setSignedPdfHash("signed-hash");
        document.setSignatureHash("signature-hash");
        document.setDocumentVersion("SP-200-V1");
        OaSignPackageSignRequest request = new OaSignPackageSignRequest();
        request.setSignConfirmText("本人确认签署本签约包");
        request.setSignatureDataUrl(signatureDataUrl());

        GeneratedSignDocument generated = service.generateSignCertificate(null, signPackage, request,
                Arrays.asList(document), "127.0.0.1", "JUnit", "hr", "event-root-hash");

        assertThat(generated.getFileUrl()).startsWith("/profile/private/sign-package/");
        assertThat(generated.getSha256()).hasSize(64);
        assertThat(generated.getSourceArchiveRelativePath()).isNotBlank();
        assertThat(generated.getSourceFileSize()).isPositive();
        assertThat(java.nio.file.Files.exists(tempDir.resolve("sign-package/200/signature.png"))).isFalse();
        assertThat(publicFile(generated.getFileUrl())).isNotEmptyFile();
    }

    @Test
    @DisplayName("签署证明已移动但归档清理失败时立即按路径和hash删除")
    void shouldDiscardPromotedCertificateWhenPromotionCleanupFails()
    {
        OaSignFileStorageService storage = mock(OaSignFileStorageService.class);
        OaSignDocumentService service = new OaSignDocumentService();
        ReflectionTestUtils.setField(service, "fileStorageService", storage);
        StagedSignFile certificate = staged("certificate.pdf", "certificate/path.pdf", "certificate-hash");
        when(storage.stage(eq(9L), eq(200L), eq("SP-200-V1"),
                eq("sign-certificate.pdf"), any(byte[].class))).thenReturn(certificate);
        when(storage.promote(certificate)).thenAnswer(invocation -> {
            certificate.markPromoted(tempDir.resolve("archive/certificate.pdf"),
                    "/profile/private/sign-package/certificate/path.pdf");
            throw new ServiceException("归档后清理临时目录失败");
        });
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(200L);
        signPackage.setDocumentVersion("SP-200-V1");
        OaSignPackageSignRequest request = new OaSignPackageSignRequest();
        request.setSignConfirmText("本人确认签署本签约包");

        assertThatThrownBy(() -> service.generateSignCertificate(9L, signPackage, request,
                List.of(), "127.0.0.1", "JUnit", "hr", "event-root-hash"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("归档后清理临时目录失败");

        verify(storage).discardUncommitted("certificate/path.pdf", "certificate-hash");
    }

    @Test
    @DisplayName("签署证明归档后发生运行时异常也清理归档文件")
    void shouldDiscardPromotedCertificateOnRuntimeFailure()
    {
        CertificateFailureFixture fixture = certificateFailureFixture(
                new IllegalStateException("运行时归档失败"));

        assertThatThrownBy(fixture::generate)
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("运行时归档失败");
        verify(fixture.storage()).discardUncommitted("certificate/path.pdf", "certificate-hash");
    }

    @Test
    @DisplayName("签署证明归档后发生Error也清理归档文件并原样抛出")
    void shouldDiscardPromotedCertificateOnError()
    {
        CertificateFailureFixture fixture = certificateFailureFixture(new AssertionError("严重归档失败"));

        assertThatThrownBy(fixture::generate)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("严重归档失败");
        verify(fixture.storage()).discardUncommitted("certificate/path.pdf", "certificate-hash");
    }

    @Test
    @DisplayName("签约包文件解析仅允许访问生成目录内的真实文件")
    void shouldResolveOnlyGeneratedSignPackageFiles() throws Exception
    {
        OaSignDocumentService service = documentService();
        Path file = tempDir.resolve("sign-package/300/document.pdf");
        java.nio.file.Files.createDirectories(file.getParent());
        java.nio.file.Files.writeString(file, "%PDF-1.4");

        assertThat(service.resolveGeneratedSignPackageFile("/profile/sign-package/300/document.pdf"))
                .isEqualTo(file.toAbsolutePath().normalize());
        assertThatThrownBy(() -> service.resolveGeneratedSignPackageFile("/profile/public/avatar.png"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("签约文件地址无效");
        assertThatThrownBy(() -> service.resolveGeneratedSignPackageFile("/profile/sign-package/300/../missing.pdf"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("签约文件不存在");

        OaSignFileProperties properties = new OaSignFileProperties();
        properties.getStorage().setRootPath(tempDir.resolve("private/sign-package").toString());
        properties.getStorage().setTempPath(tempDir.resolve("staging-extra").toString());
        properties.getStorage().setPublicPrefix("/profile/private/sign-package");
        OaSignFileStorageService storage = new OaSignFileStorageService(properties);
        com.erp.oa.domain.vo.StagedSignFile managed = storage.promote(storage.stage(null, 300L,
                "SP-300-V1", "managed.pdf", "%PDF-1.4 managed".getBytes()));
        assertThat(service.resolveGeneratedSignPackageFile(managed.getPublicUrl()))
                .isEqualTo(managed.getArchivePath());
    }

    private OaSignDocumentService documentService() throws Exception
    {
        return documentService("""
                base=$(basename "$source")
                base=${base%.*}
                printf '%%PDF-1.4\n%%fake\n' > "$outdir/$base.pdf"
                """);
    }

    private OaSignDocumentService documentService(String converterBody) throws Exception
    {
        Path converterScript = tempDir.resolve("fake-libreoffice-" + java.util.UUID.randomUUID() + ".sh");
        java.nio.file.Files.writeString(converterScript, """
                #!/bin/sh
                outdir=""
                source=""
                while [ "$#" -gt 0 ]; do
                  case "$1" in
                    --outdir) shift; outdir="$1" ;;
                    *) source="$1" ;;
                  esac
                  shift
                done
                mkdir -p "$outdir"
                """ + converterBody + "\n");
        assertThat(converterScript.toFile().setExecutable(true)).isTrue();

        OaSignFileProperties properties = new OaSignFileProperties();
        properties.getStorage().setRootPath(tempDir.resolve("private/sign-package").toString());
        properties.getStorage().setTempPath(tempDir.resolve("staging").toString());
        properties.getStorage().setPublicPrefix("/profile/private/sign-package");
        properties.getPdf().setConverterCommand(converterScript.toString());
        properties.getPdf().setTimeoutSeconds(5);
        OaSignDocumentService service = new OaSignDocumentService();
        ReflectionTestUtils.setField(service, "localFilePath", tempDir.toString());
        ReflectionTestUtils.setField(service, "localFilePrefix", "/profile");
        ReflectionTestUtils.setField(service, "fileStorageService", new OaSignFileStorageService(properties));
        ReflectionTestUtils.setField(service, "officePdfConverter", new OaOfficePdfConverter(properties));
        OaPdfPageNumberService pageNumberService = mock(OaPdfPageNumberService.class);
        when(pageNumberService.stampDynamicPageNumbers(any(Path.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ReflectionTestUtils.setField(service, "pdfPageNumberService", pageNumberService);
        return service;
    }

    private OaSignDocumentService exactV7DocumentService()
    {
        OaSignFileProperties properties = new OaSignFileProperties();
        properties.getStorage().setRootPath(tempDir.resolve("private/sign-package").toString());
        properties.getStorage().setTempPath(tempDir.resolve("staging").toString());
        properties.getStorage().setPublicPrefix("/profile/private/sign-package");
        properties.getPdf().setConverterCommand(resolveOfficeCommand());
        properties.getPdf().setTimeoutSeconds(180);
        OaSignDocumentService service = new OaSignDocumentService();
        ReflectionTestUtils.setField(service, "localFilePath", tempDir.toString());
        ReflectionTestUtils.setField(service, "localFilePrefix", "/profile");
        ReflectionTestUtils.setField(service, "fileStorageService", new OaSignFileStorageService(properties));
        ReflectionTestUtils.setField(service, "officePdfConverter", new OaOfficePdfConverter(properties));
        ReflectionTestUtils.setField(service, "pdfPageNumberService", new OaPdfPageNumberService());
        return service;
    }

    private void replaceUniqueParagraphText(XWPFDocument document, String expected,
            String replacement)
    {
        List<XWPFParagraph> matches = allParagraphs(document).stream()
                .filter(paragraph -> paragraph.getText().contains(expected)).toList();
        assertThat(matches).as("exact-v7 test paragraph: " + expected).hasSize(1);
        XWPFParagraph paragraph = matches.get(0);
        String updated = paragraph.getText().replace(expected, replacement);
        while (paragraph.getRuns().size() > 1)
        {
            paragraph.removeRun(paragraph.getRuns().size() - 1);
        }
        if (paragraph.getRuns().isEmpty())
        {
            paragraph.createRun().setText(updated);
        }
        else
        {
            paragraph.getRuns().get(0).setText(updated, 0);
        }
    }

    private List<XWPFParagraph> allParagraphs(XWPFDocument document)
    {
        List<XWPFParagraph> result = new java.util.ArrayList<>(document.getParagraphs());
        document.getTables().forEach(table -> collectParagraphs(table, result));
        return result;
    }

    private void collectParagraphs(XWPFTable table, List<XWPFParagraph> target)
    {
        for (var row : table.getRows())
        {
            for (XWPFTableCell cell : row.getTableCells())
            {
                target.addAll(cell.getParagraphs());
                cell.getTables().forEach(nested -> collectParagraphs(nested, target));
            }
        }
    }

    private void assertExactV7SequencePolicies(Path pdf, String reviewPdfHash,
            OaSignPackage sourcePackage, int expectedPages,
            List<Integer> expectedSignaturePages)
    {
        OaSignPlacementPolicyService placement = new OaSignPlacementPolicyService();
        for (String signingSequence : List.of(OaSignSigningSequence.SIGNATURE_FIRST,
                OaSignSigningSequence.COMPANY_FIRST))
        {
            OaSignPlacementPolicyService.GeneratedPlacementPolicies policies =
                    placement.validateAndFreezeGeneratedPositions(
                            OaSignTemplateType.ONBOARD_LABOR_CONTRACT, "20260721-v7",
                            "1226728ec0e703d513efda83dc5578ee4cdd65cb3e7d0e7cb5afee813f466558",
                            OaSignPlacementPolicyService.APPENDED_CONFIRMATION_PAGE,
                            OaSignPlacementPolicyService.APPENDED_CONFIRMATION_PAGE,
                            pdf, reviewPdfHash, true, true,
                            sourcePackage.getLegalRepresentativeSnapshot(), true,
                            signingSequence);
            String sequencePolicy = OaSignSigningSequence.COMPANY_FIRST.equals(signingSequence)
                    ? "COMPANY_FIRST_STABLE_BODY_DISPLAY_EXPORT"
                    : "SIGNATURE_FIRST_BODY_PLACEMENT";
            assertThat(policies.profileId())
                    .isEqualTo(OaSignLaborAnchorPlacementResolver.PROFILE_ID);
            assertThat(policies.signaturePositionJson())
                    .contains("\"mode\":\"PLACED_MULTI\"",
                            "\"expectedBodyPageCount\":" + expectedPages,
                            "\"profileId\":\"labor-v7-anchor-relative-v1\"",
                            "\"signingSequencePolicy\":\"" + sequencePolicy + "\"")
                    .doesNotContain("textOverlays");

            OaSignPackageDocument document = new OaSignPackageDocument();
            document.setDocumentId(9000L + expectedPages);
            document.setTemplateType(OaSignTemplateType.ONBOARD_LABOR_CONTRACT);
            document.setTemplateVersionSnapshot("20260721-v7");
            document.setReviewPdfHash(reviewPdfHash);
            document.setEmployeeSignRequired("Y");
            document.setCompanySealRequired("Y");
            document.setDocumentPolicyMode(OaSignPlacementPolicyService.SNAPSHOT_V1);
            document.setSignaturePositionJson(policies.signaturePositionJson());
            document.setCompanySealPositionJson(policies.companySealPositionJson());
            OaSignPackage signPackage = new OaSignPackage();
            signPackage.setSigningSequence(signingSequence);
            placement.assertSigningSequenceMatches(signPackage, document);

            OaSignedPdfService.PdfImagePlacement exportPlacement =
                    placement.resolveFinalExportSignaturePlacement(document);
            assertThat(exportPlacement.expanded())
                    .extracting(OaSignedPdfService.PdfImagePlacement::getPageNumber)
                    .containsExactlyElementsOf(expectedSignaturePages);
            if (OaSignSigningSequence.COMPANY_FIRST.equals(signingSequence))
            {
                assertThat(placement.resolveSignaturePlacement(document)).isNull();
            }
            else
            {
                assertThat(placement.resolveSignaturePlacement(document).expanded())
                        .extracting(OaSignedPdfService.PdfImagePlacement::getPageNumber)
                        .containsExactlyElementsOf(expectedSignaturePages);
            }
            assertThat(placement.resolveCompanySealPlacement(document).expanded()).hasSize(1);
            assertThat(placement.resolveDisplayTextPlacements(document)).isEmpty();
            assertThat(placement.resolveFinalExportExpectedBodyPageCount(document))
                    .isEqualTo(expectedPages);
        }
    }

    private String resolveOfficeCommand()
    {
        String configured = System.getProperty("sign.test.office.command");
        if (configured == null || configured.isBlank())
        {
            configured = System.getenv("SIGN_TEST_OFFICE_COMMAND");
        }
        if (configured != null && !configured.isBlank())
        {
            Path executable = Path.of(configured).toAbsolutePath().normalize();
            org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.Files.isExecutable(executable),
                    "exact-v7 正式服务渲染未执行：SIGN_TEST_OFFICE_COMMAND 不可执行");
            return executable.toString();
        }
        String path = System.getenv("PATH");
        if (path != null)
        {
            for (String directory : path.split(java.io.File.pathSeparator))
            {
                for (String name : List.of("soffice", "libreoffice", "soffice.exe",
                        "libreoffice.exe"))
                {
                    Path executable = Path.of(directory, name).toAbsolutePath().normalize();
                    if (java.nio.file.Files.isExecutable(executable))
                    {
                        return executable.toString();
                    }
                }
            }
        }
        org.junit.jupiter.api.Assumptions.assumeTrue(false,
                "exact-v7 正式服务渲染未执行：请在 PATH 提供 soffice/libreoffice，"
                        + "或设置 SIGN_TEST_OFFICE_COMMAND");
        return "soffice";
    }

    private OaSignPackage databaseMaxLaborPackage()
    {
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(2026072199L);
        signPackage.setPackageNo("SP-EXACT-V7-DATABASE-MAX");
        signPackage.setDocumentVersion("SP-2026072199-V1");
        signPackage.setScenario("onboard");
        signPackage.setEmploymentType("劳动合同");
        signPackage.setEmployeeNameSnapshot(fixedWidth("压力测试姓名", 64));
        signPackage.setEmployeePhoneSnapshot("1".repeat(32));
        signPackage.setEmployeeIdCardSnapshot("9".repeat(32));
        signPackage.setEmployeeAddressSnapshot(fixedWidth("压力测试送达地址", 200));
        signPackage.setLegalEntityIdSnapshot(4L);
        signPackage.setLegalEntityNameSnapshot(fixedWidth("压力测试法律主体公司名称", 160));
        signPackage.setLegalEntityAddressSnapshot(fixedWidth("压力测试法律主体注册地址", 200));
        signPackage.setLegalRepresentativeSnapshot(fixedWidth("压力测试法定代表人", 64));
        signPackage.setPostNameSnapshot(fixedWidth("压力测试岗位", 64));
        signPackage.setPostLevelSnapshot("99");
        signPackage.setContractTermCodeSnapshot("FIXED_TERM");
        signPackage.setContractStartDate("9999-12-31");
        signPackage.setContractEndDate("9999-12-31");
        signPackage.setProbationStartDate("9999-12-31");
        signPackage.setProbationEndDate("9999-12-31");
        signPackage.setIncomeStartYearMonth("9999年12月");
        signPackage.setBaseSalary(new BigDecimal("99999999999999.99"));
        signPackage.setPostSalary(new BigDecimal("99999999999999.99"));
        signPackage.setFieldAllowance(new BigDecimal("99999999999999.99"));
        signPackage.setPerformanceSalary(new BigDecimal("99999999999999.99"));
        signPackage.setSalaryTotal(new BigDecimal("99999999999999.99"));
        return signPackage;
    }

    private OaSignPackage normalLaborPackage()
    {
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(2026072101L);
        signPackage.setPackageNo("SP-EXACT-V7-NORMAL-QA");
        signPackage.setDocumentVersion("SP-2026072101-V1");
        signPackage.setScenario("onboard");
        signPackage.setEmploymentType("劳动合同");
        signPackage.setEmployeeNameSnapshot("合成员工甲");
        signPackage.setEmployeePhoneSnapshot("18800000000");
        signPackage.setEmployeeIdCardSnapshot("000000********0000");
        signPackage.setEmployeeAddressSnapshot("合成测试地址");
        signPackage.setLegalEntityIdSnapshot(999L);
        signPackage.setLegalEntityNameSnapshot("合成法律主体有限公司");
        signPackage.setLegalEntityAddressSnapshot("合成注册地址");
        signPackage.setLegalRepresentativeSnapshot("合成代表甲");
        signPackage.setPostNameSnapshot("合成岗位");
        signPackage.setPostLevelSnapshot("1");
        signPackage.setContractTermCodeSnapshot("FIXED_TERM");
        signPackage.setContractStartDate("2026-08-09");
        signPackage.setContractEndDate("2029-08-08");
        signPackage.setProbationStartDate("2026-08-09");
        signPackage.setProbationEndDate("2026-11-08");
        signPackage.setBaseSalary(new BigDecimal("5000.00"));
        signPackage.setPostSalary(new BigDecimal("1000.00"));
        signPackage.setSalaryTotal(new BigDecimal("6000.00"));
        return signPackage;
    }

    private OaSignTemplate exactV7Template(Path file)
    {
        OaSignTemplate template = new OaSignTemplate();
        template.setTemplateId(2026072109L);
        template.setTemplateType(OaSignTemplateType.ONBOARD_LABOR_CONTRACT);
        template.setTemplateVersion("20260721-v7");
        template.setFileUrl(file.toString());
        return template;
    }

    private int uniquePageContaining(org.apache.pdfbox.pdmodel.PDDocument document,
            String... anchors) throws Exception
    {
        List<Integer> matches = new java.util.ArrayList<>();
        org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
        for (int page = 1; page <= document.getNumberOfPages(); page++)
        {
            stripper.setStartPage(page);
            stripper.setEndPage(page);
            String text = stripper.getText(document).replaceAll("\\s+", "");
            if (Arrays.stream(anchors).allMatch(text::contains))
            {
                matches.add(page);
            }
        }
        assertThat(matches).as("anchors=" + String.join("/", anchors)).hasSize(1);
        return matches.get(0);
    }

    private String normalizedPdfText(org.apache.pdfbox.pdmodel.PDDocument document)
            throws Exception
    {
        return java.text.Normalizer.normalize(
                new org.apache.pdfbox.text.PDFTextStripper().getText(document),
                java.text.Normalizer.Form.NFKC).replaceAll("\\s+", "");
    }

    private void printSigningAnchorPages(org.apache.pdfbox.pdmodel.PDDocument document) throws Exception
    {
        org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
        for (int page = 1; page <= document.getNumberOfPages(); page++)
        {
            stripper.setStartPage(page);
            stripper.setEndPage(page);
            String text = stripper.getText(document).replaceAll("\\s+", "");
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile(".{0,24}签名.{0,24}").matcher(text);
            List<String> anchors = new java.util.ArrayList<>();
            while (matcher.find())
            {
                anchors.add(matcher.group());
            }
            if (!anchors.isEmpty())
            {
                System.out.printf("EXACT_V7_SIGNING_ANCHORS page=%d count=%d snippets=%s%n",
                        page, anchors.size(), anchors);
            }
        }
    }

    private Path publishableExactV7Template() throws Exception
    {
        Path target = tempDir.resolve("publishable-onboard-20260721-v7.docx");
        if (!java.nio.file.Files.exists(target))
        {
            try (java.io.InputStream input = getClass().getResourceAsStream(
                    "/oa/sign/templates/onboard-20260721-v7/09_ONBOARD_LABOR_CONTRACT.docx"))
            {
                assertThat(input).as("publishable exact-v7 template asset").isNotNull();
                java.nio.file.Files.copy(input, target);
            }
        }
        assertThat(org.apache.commons.codec.digest.DigestUtils.sha256Hex(
                java.nio.file.Files.newInputStream(target)))
                .isEqualTo("1226728ec0e703d513efda83dc5578ee4cdd65cb3e7d0e7cb5afee813f466558");
        return target;
    }

    private Path repositoryFile(String relative)
    {
        Path current = repositoryRoot();
        return current.resolve(relative).normalize();
    }

    private Path repositoryRoot()
    {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null)
        {
            if (java.nio.file.Files.isRegularFile(current.resolve("mvnw"))
                    && java.nio.file.Files.isRegularFile(current.resolve("pom.xml")))
            {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("无法定位ERP仓库根目录");
    }

    private String fixedWidth(String seed, int width)
    {
        return seed.repeat((width / seed.length()) + 1).substring(0, width);
    }

    private Path publicFile(String fileUrl)
    {
        return tempDir.resolve(fileUrl.substring("/profile/".length()));
    }

    private StagedSignFile staged(String filename, String relativePath, String hash)
    {
        Path directory = tempDir.resolve("stage-" + filename);
        return new StagedSignFile(directory, directory.resolve(filename), relativePath,
                filename, hash, 10L);
    }

    private CertificateFailureFixture certificateFailureFixture(Throwable failure)
    {
        OaSignFileStorageService storage = mock(OaSignFileStorageService.class);
        OaSignDocumentService service = new OaSignDocumentService();
        ReflectionTestUtils.setField(service, "fileStorageService", storage);
        StagedSignFile certificate = staged("certificate.pdf", "certificate/path.pdf", "certificate-hash");
        when(storage.stage(eq(9L), eq(200L), eq("SP-200-V1"),
                eq("sign-certificate.pdf"), any(byte[].class))).thenReturn(certificate);
        when(storage.promote(certificate)).thenAnswer(invocation -> {
            certificate.markPromoted(tempDir.resolve("archive/certificate.pdf"),
                    "/profile/private/sign-package/certificate/path.pdf");
            throw failure;
        });
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(200L);
        signPackage.setDocumentVersion("SP-200-V1");
        OaSignPackageSignRequest request = new OaSignPackageSignRequest();
        request.setSignConfirmText("本人确认签署本签约包");
        return new CertificateFailureFixture(service, storage, signPackage, request);
    }

    private record CertificateFailureFixture(OaSignDocumentService service,
            OaSignFileStorageService storage, OaSignPackage signPackage,
            OaSignPackageSignRequest request)
    {
        private GeneratedSignDocument generate()
        {
            return service.generateSignCertificate(9L, signPackage, request,
                    List.of(), "127.0.0.1", "JUnit", "hr", "event-root-hash");
        }
    }

    private String signatureDataUrl() throws Exception
    {
        BufferedImage image = new BufferedImage(320, 120, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try
        {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(Color.BLACK);
            graphics.setStroke(new java.awt.BasicStroke(6f));
            graphics.drawLine(30, 80, 110, 35);
            graphics.drawLine(110, 35, 190, 88);
            graphics.drawLine(190, 88, 280, 30);
        }
        finally
        {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
    }
}

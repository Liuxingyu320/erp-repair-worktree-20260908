package com.erp.oa.service.impl;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;
import com.erp.oa.constant.OaSignTemplateType;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignPackageDocument;
import com.erp.oa.domain.OaSignTemplate;
import com.erp.oa.domain.dto.OaSignPackageSignRequest;
import com.erp.oa.domain.vo.StagedSignFile;
import com.erp.system.api.constant.SigningProfileCodes;

@Service
public class OaSignDocumentService
{
    private static final int PDF_IMAGE_WIDTH = 1240;
    private static final int PDF_IMAGE_HEIGHT = 1754;
    private static final Pattern SALARY_VERSION_PATTERN = Pattern.compile("(?i)([0-9]{4})[-_ ]*V([0-9]+)");
    private static final Pattern LEVEL_PATTERN = Pattern.compile("(?i)P([0-9]+)");
    private static final Pattern UNRESOLVED_PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{[^{}]+}");
    private static final String CHECKED_BOX = "☑";
    private static final String UNCHECKED_BOX = "□";
    private static final String ATTACHMENT_DORMITORY_MARK = "attachmentDormitoryMark";
    private static final String ATTACHMENT_DUTY_MARK = "attachmentDutyMark";
    private static final String ATTACHMENT_HANDBOOK_MARK = "attachmentHandbookMark";
    private static final String ATTACHMENT_SALARY_MARK = "attachmentSalaryMark";
    private static final String ATTACHMENT_SERVICE_RECEIPT_MARK = "attachmentServiceReceiptMark";
    private static final String SERVICE_STUDENT_MARK = "serviceStudentMark";
    private static final String SERVICE_RETIRED_MARK = "serviceRetiredMark";
    private static final String INSURANCE_COMMERCIAL_ACCIDENT_MARK = "insuranceCommercialAccidentMark";
    private static final String INSURANCE_EMPLOYER_LIABILITY_MARK = "insuranceEmployerLiabilityMark";
    private static final String LABOR_V7_TEMPLATE_VERSION = "20260721-v7";
    private static final String LABOR_V7_SOURCE_SHA256 =
            "1226728ec0e703d513efda83dc5578ee4cdd65cb3e7d0e7cb5afee813f466558";
    private static final Map<String, String> DICTIONARY_LABELS = Map.ofEntries(
            Map.entry("LABOR_CONTRACT", "劳动合同"),
            Map.entry("SERVICE_CONTRACT", "劳务合同"),
            Map.entry("INTERNSHIP_AGREEMENT", "实习协议"),
            Map.entry("OUTSOURCING_CONTRACT", "外包合同"),
            Map.entry("SOCIAL_INSURED", "有社保"),
            Map.entry("SOCIAL_UNINSURED", "无社保"),
            Map.entry("NO_SOCIAL", "无社保"),
            Map.entry("DISPATCHED", "劳务派遣"),
            Map.entry("PENDING_CONFIRMATION", "待确认"),
            Map.entry("STUDENT_INTERN", "在校实习生"),
            Map.entry("RETIRED_REHIRE", "退休返聘"),
            Map.entry("OTHER", "其他"),
            Map.entry("COMMERCIAL_ACCIDENT", "商业意外保险"),
            Map.entry("EMPLOYER_LIABILITY", "雇主责任险"),
            Map.entry("VOLUNTARY_EXPECTED", "正常主动离职"),
            Map.entry("VOLUNTARY_UNEXPECTED", "突发主动离职"),
            Map.entry("TERMINATION", "协商解除"),
            Map.entry("DISCIPLINARY_TERMINATION", "违纪解除"),
            Map.entry("DISPUTED_TERMINATION", "争议解除"),
            Map.entry("COMPLETED", "已完成"),
            Map.entry("PENDING", "待完成"),
            Map.entry("NOT_APPLICABLE", "不适用"),
            Map.entry("REQUIRED", "需要执行"),
            Map.entry("LOW", "低风险"),
            Map.entry("NORMAL", "常规"),
            Map.entry("MEDIUM", "关注"),
            Map.entry("HIGH", "高风险"),
            Map.entry("REVIEW_REQUIRED", "需重点处理"),
            Map.entry("ONBOARD", "入职"),
            Map.entry("REGULARIZE", "转正"),
            Map.entry("TRANSFER", "调岗"),
            Map.entry("RENEWAL", "续签"),
            Map.entry("RENEW", "续签"),
            Map.entry("OFFBOARD", "离职"),
            Map.entry("A", "甲版"),
            Map.entry("B", "乙版"));
    private static final Map<String, String> PLACEHOLDER_LABELS = Map.ofEntries(
            Map.entry("employeeName", "员工姓名"),
            Map.entry("employeeIdCard", "身份证号"),
            Map.entry("employeePhone", "手机号码"),
            Map.entry("employeeAddress", "现居地址"),
            Map.entry("employeeDeptName", "所属部门"),
            Map.entry("postName", "岗位名称"),
            Map.entry("postLevel", "岗位等级"),
            Map.entry("employmentType", "用工类型"),
            Map.entry("contractType", "合同类型"),
            Map.entry("socialType", "社保类型"),
            Map.entry("servicePersonType", "劳务人员类型"),
            Map.entry("insuranceType", "保险类型"),
            Map.entry("scenario", "签约场景"),
            Map.entry("entryDate", "入职日期"),
            Map.entry("contractTermSelection", "合同期限选项"),
            Map.entry("contractTermFixedMark", "固定期限勾选标记"),
            Map.entry("contractTermOpenEndedMark", "无固定期限勾选标记"),
            Map.entry("contractStartDate", "合同开始日期"),
            Map.entry("contractEndDate", "合同结束日期"),
            Map.entry("previousContractEndDate", "原合同结束日期"),
            Map.entry("previousEmploymentType", "原合同类型"),
            Map.entry("previousRenewalCount", "原续签次数"),
            Map.entry("renewalCount", "续签次数"),
            Map.entry("probationStartDate", "试用期开始日期"),
            Map.entry("probationEndDate", "试用期结束日期"),
            Map.entry("actualRegularizationDate", "实际转正日期"),
            Map.entry("transferEffectiveDate", "调岗生效日期"),
            Map.entry("beforeDeptName", "调岗前部门"),
            Map.entry("afterDeptName", "调岗后部门"),
            Map.entry("beforePostName", "调岗前岗位"),
            Map.entry("afterPostName", "调岗后岗位"),
            Map.entry("workStartDate", "工作开始日期"),
            Map.entry("incomeStartYearMonth", "个人劳动收入主要来源起始年月"),
            Map.entry("workEndDate", "工作结束日期"),
            Map.entry("leaveDate", "离职日期"),
            Map.entry("leaveReason", "离职原因"),
            Map.entry("offboardingType", "离职类型"),
            Map.entry("salarySettlementStatus", "薪资结算状态"),
            Map.entry("assetHandoverStatus", "资产交接状态"),
            Map.entry("nonCompeteDecision", "竞业限制决定"),
            Map.entry("compensationAmount", "补偿金额"),
            Map.entry("compensationNote", "补偿说明"),
            Map.entry("baseSalary", "基本工资"),
            Map.entry("postSalary", "岗位工资"),
            Map.entry("fieldAllowance", "外勤补贴"),
            Map.entry("performanceSalary", "绩效工资"),
            Map.entry("salaryTotal", "薪资合计"),
            Map.entry("salaryVersion", "薪资版本"),
            Map.entry("companyName", "公司法定全称"),
            Map.entry("companyCode", "公司内部编码"),
            Map.entry("companyCreditCode", "统一社会信用代码"),
            Map.entry("companyAddress", "公司注册地址"),
            Map.entry("companyLegalRepresentative", "法定代表人"),
            Map.entry("companyPhone", "公司联系电话"),
            Map.entry(ATTACHMENT_DORMITORY_MARK, "职工宿舍协议附件标记"),
            Map.entry(ATTACHMENT_DUTY_MARK, "岗位职责附件标记"),
            Map.entry(ATTACHMENT_HANDBOOK_MARK, "员工手册附件标记"),
            Map.entry(ATTACHMENT_SALARY_MARK, "薪酬确认书附件标记"),
            Map.entry(ATTACHMENT_SERVICE_RECEIPT_MARK, "劳务合同书签收单附件标记"),
            Map.entry(SERVICE_STUDENT_MARK, "在校实习生勾选标记"),
            Map.entry(SERVICE_RETIRED_MARK, "退休返聘人员勾选标记"),
            Map.entry(INSURANCE_COMMERCIAL_ACCIDENT_MARK, "商业意外保险勾选标记"),
            Map.entry(INSURANCE_EMPLOYER_LIABILITY_MARK, "雇主责任险勾选标记"),
            Map.entry("signDate", "签署日期"));
    private static final Pattern LEGAL_ENTITY_LITERAL_PATTERN = Pattern.compile(
            "[\\p{IsHan}A-Za-z0-9（）()\u00b7]{4,80}(?:有限责任公司|股份有限公司|有限公司)");

    @Value("${file.path:./uploadPath}")
    private String localFilePath;

    @Value("${file.prefix:/profile}")
    private String localFilePrefix;

    @Value("${file.public-url-prefix:/file/public}")
    private String localPublicFilePrefix;

    @Value("${file.gateway-prefix:/prod-api}")
    private String localFileGatewayPrefix;

    @Autowired
    private OaSignFileStorageService fileStorageService;

    @Autowired
    private OaOfficePdfConverter officePdfConverter;

    @Autowired
    private OaPdfPageNumberService pdfPageNumberService;

    public void assertTemplateContainsRequiredPlaceholders(String templateType, String templateFileUrl)
    {
        OaSignTemplateType.Option type = OaSignTemplateType.require(templateType);
        if (StringUtils.isBlank(templateFileUrl))
        {
            throw new ServiceException("模板文件不能为空");
        }
        String text = collectTemplateText(type, templateFileUrl);
        List<String> missing = new ArrayList<>();
        for (String placeholder : type.getRequiredPlaceholders())
        {
            String token = "${" + placeholder + "}";
            String chineseToken = "${" + placeholderLabel(placeholder) + "}";
            if (!text.contains(token) && !text.contains(chineseToken))
            {
                missing.add(chineseToken);
            }
        }
        if (!missing.isEmpty())
        {
            throw new ServiceException(type.getLabel() + "模板缺少占位符：" + String.join("、", missing));
        }
    }

    /**
     * Prevents a template containing one hard-coded employer from being used for another
     * legal entity. Generic documents remain compatible, while reusable templates should
     * use ${companyName}.
     */
    public void assertTemplateLegalEntityCompatible(String templateType, String templateFileUrl,
            String legalEntityName)
    {
        OaSignTemplateType.Option type = OaSignTemplateType.require(templateType);
        if (StringUtils.isBlank(legalEntityName))
        {
            return;
        }
        String text = collectTemplateText(type, templateFileUrl);
        String normalizedLegalEntityName = legalEntityName.trim();
        // A document may mention the expected employer and still contain a second,
        // accidentally hard-coded company. Remove reusable/expected literals and scan
        // everything else instead of returning on the first acceptable name.
        Matcher matcher = LEGAL_ENTITY_LITERAL_PATTERN.matcher(
                text.replace("${companyName}", "")
                        .replace("${" + placeholderLabel("companyName") + "}", "")
                        .replace(normalizedLegalEntityName, ""));
        if (matcher.find())
        {
            throw new ServiceException(type.getLabel()
                    + "模板写死了其他公司名称，请改用公司名称占位符或选择与模板一致的公司");
        }
    }

    public String calculateFileUrlSha256(String fileUrl)
    {
        if (StringUtils.isBlank(fileUrl))
        {
            return null;
        }
        try (InputStream input = resolveInputStream(fileUrl))
        {
            if (input == null)
            {
                return null;
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int len;
            while ((len = input.read(buffer)) != -1)
            {
                digest.update(buffer, 0, len);
            }
            return toHex(digest.digest());
        }
        catch (IOException | NoSuchAlgorithmException e)
        {
            throw new ServiceException("计算模板文件校验值失败: " + e.getMessage());
        }
    }

    public GeneratedSignDocument renderPackageDocument(OaSignPackage signPackage, OaSignTemplate template)
    {
        return renderPackageDocument(signPackage, template,
                template == null ? List.of() : List.of(template.getTemplateType()));
    }

    /**
     * Render one package document with attachment checklist marks derived exclusively from
     * the templates selected for this package.  The two embedded labor-contract appendices
     * are checked only when the labor contract is present; separately delivered handbook
     * and salary documents follow their own actual template types.
     */
    public GeneratedSignDocument renderPackageDocument(OaSignPackage signPackage, OaSignTemplate template,
            Collection<String> actualPackageTemplateTypes)
    {
        OaSignTemplateType.Option type = OaSignTemplateType.require(template.getTemplateType());
        StagedSignFile sourceFile = null;
        StagedSignFile reviewPdf = null;
        Path convertedPdf = null;
        try (InputStream input = resolveInputStream(template.getFileUrl()))
        {
            if (input == null)
            {
                throw new ServiceException("模板文件不存在");
            }
            byte[] templateBytes = input.readAllBytes();
            String actualTemplateSourceHash = sha256(templateBytes);
            validateTemplateSourceBytes(template, actualTemplateSourceHash);
            Map<String, String> values = placeholderValues(signPackage, actualPackageTemplateTypes);
            byte[] renderedSource;
            if ("xlsx".equalsIgnoreCase(type.getFileFormat()))
            {
                renderedSource = renderXlsx(new ByteArrayInputStream(templateBytes), values);
            }
            else
            {
                renderedSource = renderDocx(new ByteArrayInputStream(templateBytes), values,
                        template, actualTemplateSourceHash);
            }
            String documentVersion = resolveDocumentVersion(signPackage);
            String sourceName = template.getTemplateType() + "-" + template.getTemplateId() + "."
                    + type.getFileFormat();
            sourceFile = fileStorageService.stage(null, signPackage.getPackageId(), documentVersion,
                    sourceName, renderedSource);
            convertedPdf = officePdfConverter.convert(sourceFile.getTempPath(), sourceFile.getStagingDirectory());
            if (OaSignTemplateType.ONBOARD_LABOR_CONTRACT.equalsIgnoreCase(template.getTemplateType()))
            {
                if (pdfPageNumberService == null)
                {
                    throw new ServiceException("劳动合同PDF页码服务未配置");
                }
                convertedPdf = pdfPageNumberService.stampDynamicPageNumbers(convertedPdf);
            }
            reviewPdf = fileStorageService.stage(null, signPackage.getPackageId(), documentVersion,
                    template.getTemplateType() + "-" + template.getTemplateId() + ".pdf",
                    Files.readAllBytes(convertedPdf));
            Files.deleteIfExists(convertedPdf);
            convertedPdf = null;

            fileStorageService.promote(sourceFile);
            fileStorageService.promote(reviewPdf);
            return new GeneratedSignDocument(sourceFile.getPublicUrl(), sourceFile.getFileHash(),
                    reviewPdf.getPublicUrl(), reviewPdf.getFileHash(), documentVersion,
                    sourceFile.getArchiveRelativePath(), sourceFile.getFileSize(),
                    reviewPdf.getArchiveRelativePath(), reviewPdf.getFileSize());
        }
        catch (ServiceException e)
        {
            discardPromoted(reviewPdf, e);
            discardPromoted(sourceFile, e);
            throw e;
        }
        catch (IOException e)
        {
            ServiceException failure = new ServiceException("生成签约文件失败")
                    .setDetailMessage(e.getMessage());
            discardPromoted(reviewPdf, failure);
            discardPromoted(sourceFile, failure);
            throw failure;
        }
        catch (RuntimeException | Error e)
        {
            discardPromoted(reviewPdf, e);
            discardPromoted(sourceFile, e);
            throw e;
        }
        finally
        {
            deleteFileQuietly(convertedPdf);
            cleanupStagedQuietly(sourceFile);
            cleanupStagedQuietly(reviewPdf);
        }
    }

    private String resolveDocumentVersion(OaSignPackage signPackage)
    {
        if (signPackage == null || signPackage.getPackageId() == null || signPackage.getPackageId() <= 0)
        {
            throw new ServiceException("签约包编号不合法");
        }
        String expectedPrefix = "SP-" + signPackage.getPackageId() + "-V";
        String documentVersion = signPackage.getDocumentVersion();
        if (StringUtils.isBlank(documentVersion))
        {
            documentVersion = expectedPrefix + "1";
            signPackage.setDocumentVersion(documentVersion);
        }
        String sequence = documentVersion.startsWith(expectedPrefix)
                ? documentVersion.substring(expectedPrefix.length()) : "";
        if (!sequence.matches("[1-9][0-9]*"))
        {
            throw new ServiceException("文档版本格式不合法");
        }
        return documentVersion;
    }

    public GeneratedSignDocument generateSignCertificate(OaSignPackage signPackage,
            OaSignPackageSignRequest signRequest, List<OaSignPackageDocument> documents)
    {
        return generateSignCertificate(null, signPackage, signRequest, documents,
                null, null, null, null);
    }

    public GeneratedSignDocument generateSignCertificate(OaSignPackage signPackage,
            OaSignPackageSignRequest signRequest, List<OaSignPackageDocument> documents,
            String signerIp, String signerUserAgent)
    {
        return generateSignCertificate(null, signPackage, signRequest, documents,
                signerIp, signerUserAgent, null, null);
    }

    public GeneratedSignDocument generateSignCertificate(Long taskId, OaSignPackage signPackage,
            OaSignPackageSignRequest signRequest, List<OaSignPackageDocument> documents,
            String signerIp, String signerUserAgent, String hrConfirmedBy, String eventRootHash)
    {
        StagedSignFile certificate = null;
        try
        {
            if (signPackage == null || signPackage.getPackageId() == null)
            {
                throw new ServiceException("签约包信息不能为空");
            }
            String documentVersion = resolveDocumentVersion(signPackage);
            byte[] certificateBytes = buildSimplePdf(certificateLines(taskId, signPackage, signRequest,
                    documents, signerIp, signerUserAgent, hrConfirmedBy, eventRootHash));
            certificate = fileStorageService.stage(taskId, signPackage.getPackageId(), documentVersion,
                    "sign-certificate.pdf", certificateBytes);
            fileStorageService.promote(certificate);
            return new GeneratedSignDocument(certificate.getPublicUrl(), certificate.getFileHash(),
                    null, null, documentVersion, certificate.getArchiveRelativePath(),
                    certificate.getFileSize(), null, 0L);
        }
        catch (ServiceException e)
        {
            discardPromoted(certificate, e);
            throw e;
        }
        catch (RuntimeException e)
        {
            ServiceException failure = new ServiceException("生成签约包签署证明失败: " + e.getMessage());
            discardPromoted(certificate, failure);
            throw failure;
        }
        catch (Error e)
        {
            discardPromoted(certificate, e);
            throw e;
        }
        finally
        {
            cleanupStagedQuietly(certificate);
        }
    }

    public void discardUncommitted(GeneratedSignDocument generated)
    {
        if (generated == null)
        {
            return;
        }
        RuntimeException cleanupFailure = null;
        cleanupFailure = discardGeneratedPath(generated.getSourceArchiveRelativePath(),
                generated.getSourceFileHash(), cleanupFailure);
        cleanupFailure = discardGeneratedPath(generated.getReviewPdfArchiveRelativePath(),
                generated.getReviewPdfHash(), cleanupFailure);
        if (cleanupFailure != null)
        {
            throw cleanupFailure;
        }
    }

    private RuntimeException discardGeneratedPath(String archiveRelativePath, String expectedHash,
            RuntimeException previousFailure)
    {
        if (StringUtils.isBlank(archiveRelativePath))
        {
            return previousFailure;
        }
        try
        {
            fileStorageService.discardUncommitted(archiveRelativePath, expectedHash);
            return previousFailure;
        }
        catch (RuntimeException cleanupFailure)
        {
            if (previousFailure == null)
            {
                return cleanupFailure;
            }
            previousFailure.addSuppressed(cleanupFailure);
            return previousFailure;
        }
    }

    private void discardPromoted(StagedSignFile stagedFile, Throwable originalFailure)
    {
        if (stagedFile == null || stagedFile.getArchivePath() == null)
        {
            return;
        }
        try
        {
            fileStorageService.discardUncommitted(stagedFile.getArchiveRelativePath(),
                    stagedFile.getFileHash());
        }
        catch (RuntimeException cleanupFailure)
        {
            originalFailure.addSuppressed(cleanupFailure);
        }
    }

    private List<String> certificateLines(Long taskId, OaSignPackage signPackage,
            OaSignPackageSignRequest signRequest, List<OaSignPackageDocument> documents,
            String signerIp, String signerUserAgent, String hrConfirmedBy, String eventRootHash)
    {
        List<String> lines = new ArrayList<>();
        lines.add("员工首次签名记录");
        lines.add("任务编号: " + value(taskId == null ? null : String.valueOf(taskId)));
        lines.add("员工: " + value(signPackage.getEmployeeNameSnapshot()));
        lines.add("手机号: " + value(signPackage.getEmployeePhoneSnapshot()));
        lines.add("身份证: " + value(signPackage.getEmployeeIdCardSnapshot()));
        lines.add("合同公司: " + (StringUtils.isBlank(signPackage.getLegalEntityNameSnapshot())
                ? "首次签名后确定" : signPackage.getLegalEntityNameSnapshot()));
        lines.add("签署时间: " + value(signPackage.getSignedTime() == null ? null
                : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(signPackage.getSignedTime())));
        lines.add("签署确认: " + value(signRequest.getSignConfirmText()));
        lines.add("合同经办记录: 已由系统留存");
        lines.add("签署文件:");
        for (OaSignPackageDocument document : documents == null ? List.<OaSignPackageDocument>of() : documents)
        {
            String typeLabel;
            try
            {
                typeLabel = OaSignTemplateType.require(document.getTemplateType()).getLabel();
            }
            catch (ServiceException ignored)
            {
                typeLabel = "未知文件类型";
            }
            lines.add("- " + value(document.getDocumentName()) + "（" + typeLabel + "）");
            lines.add("  阅读文件: 已留存");
            lines.add("  员工签名: " + (StringUtils.isBlank(document.getSignatureHash())
                    ? "未留存" : "已留存"));
        }
        return lines;
    }

    private String collectTemplateText(OaSignTemplateType.Option type, String fileUrl)
    {
        try (InputStream input = resolveInputStream(fileUrl))
        {
            if (input == null)
            {
                throw new ServiceException("模板文件不存在");
            }
            if ("xlsx".equalsIgnoreCase(type.getFileFormat()))
            {
                return collectXlsxText(input);
            }
            return collectDocxText(input);
        }
        catch (IOException e)
        {
            throw new ServiceException("读取模板文件失败: " + e.getMessage());
        }
    }

    private String collectDocxText(InputStream input) throws IOException
    {
        try (XWPFDocument document = new XWPFDocument(input))
        {
            StringBuilder text = new StringBuilder();
            for (XWPFParagraph paragraph : document.getParagraphs())
            {
                text.append(paragraph.getText()).append('\n');
            }
            for (XWPFTable table : document.getTables())
            {
                appendTableText(text, table);
            }
            for (XWPFHeader header : document.getHeaderList())
            {
                appendParagraphText(text, header.getParagraphs());
                for (XWPFTable table : header.getTables())
                {
                    appendTableText(text, table);
                }
            }
            for (XWPFFooter footer : document.getFooterList())
            {
                appendParagraphText(text, footer.getParagraphs());
                for (XWPFTable table : footer.getTables())
                {
                    appendTableText(text, table);
                }
            }
            return text.toString();
        }
    }

    private void appendParagraphText(StringBuilder text, List<XWPFParagraph> paragraphs)
    {
        for (XWPFParagraph paragraph : paragraphs)
        {
            text.append(paragraph.getText()).append('\n');
        }
    }

    private void appendTableText(StringBuilder text, XWPFTable table)
    {
        for (XWPFTableRow row : table.getRows())
        {
            for (XWPFTableCell cell : row.getTableCells())
            {
                for (XWPFParagraph paragraph : cell.getParagraphs())
                {
                    text.append(paragraph.getText()).append('\n');
                }
                for (XWPFTable nested : cell.getTables())
                {
                    appendTableText(text, nested);
                }
            }
        }
    }

    private String collectXlsxText(InputStream input) throws IOException
    {
        try (Workbook workbook = WorkbookFactory.create(input))
        {
            DataFormatter formatter = new DataFormatter();
            StringBuilder text = new StringBuilder();
            for (Sheet sheet : workbook)
            {
                for (Row row : sheet)
                {
                    for (Cell cell : row)
                    {
                        text.append(cellText(cell, formatter)).append('\n');
                    }
                }
            }
            return text.toString();
        }
    }

    private byte[] renderDocx(InputStream input, Map<String, String> values,
            OaSignTemplate template, String actualTemplateSourceHash) throws IOException
    {
        try (XWPFDocument document = new XWPFDocument(input);
                ByteArrayOutputStream output = new ByteArrayOutputStream())
        {
            for (XWPFParagraph paragraph : document.getParagraphs())
            {
                replaceParagraphText(paragraph, values);
            }
            for (XWPFTable table : document.getTables())
            {
                replaceTableText(table, values);
            }
            for (XWPFHeader header : document.getHeaderList())
            {
                for (XWPFParagraph paragraph : header.getParagraphs())
                {
                    replaceParagraphText(paragraph, values);
                }
                for (XWPFTable table : header.getTables())
                {
                    replaceTableText(table, values);
                }
            }
            for (XWPFFooter footer : document.getFooterList())
            {
                for (XWPFParagraph paragraph : footer.getParagraphs())
                {
                    replaceParagraphText(paragraph, values);
                }
                for (XWPFTable table : footer.getTables())
                {
                    replaceTableText(table, values);
                }
            }
            renderFrozenLaborRepresentative(document, template, actualTemplateSourceHash,
                    values.get("companyLegalRepresentative"));
            assertNoUnresolvedPlaceholders(document);
            document.write(output);
            return output.toByteArray();
        }
    }

    /**
     * The immutable v7 source predates the signing-row placeholder and still contains one
     * literal blank after “甲方代表”.  New package documents must nevertheless contain the
     * package's frozen representative before the employee signs.  This compatibility step is
     * deliberately bound to the exact reviewed template version and the SHA-256 of the bytes
     * opened for this render.  An altered or later template must provide its own reviewed
     * generation rule instead of inheriting this text edit.
     */
    private void renderFrozenLaborRepresentative(XWPFDocument document,
            OaSignTemplate template, String actualTemplateSourceHash,
            String legalRepresentative)
    {
        if (template == null
                || !OaSignTemplateType.ONBOARD_LABOR_CONTRACT.equalsIgnoreCase(
                        template.getTemplateType())
                || !LABOR_V7_TEMPLATE_VERSION.equals(
                        StringUtils.trim(template.getTemplateVersion())))
        {
            return;
        }
        if (!LABOR_V7_SOURCE_SHA256.equalsIgnoreCase(
                StringUtils.trim(actualTemplateSourceHash)))
        {
            throw new ServiceException("劳动合同v7源文件指纹不匹配，已拒绝生成");
        }
        if (StringUtils.isBlank(legalRepresentative))
        {
            throw new ServiceException("冻结甲方代表缺失");
        }
        String marker = "甲方代表：____________";
        String replacement = "甲方代表：" + StringUtils.trim(legalRepresentative);
        int replacements = replaceLiteral(document.getParagraphs(), document.getTables(),
                marker, replacement);
        for (XWPFHeader header : document.getHeaderList())
        {
            replacements += replaceLiteral(header.getParagraphs(), header.getTables(),
                    marker, replacement);
        }
        for (XWPFFooter footer : document.getFooterList())
        {
            replacements += replaceLiteral(footer.getParagraphs(), footer.getTables(),
                    marker, replacement);
        }
        if (replacements != 1)
        {
            throw new ServiceException("劳动合同甲方代表签署栏必须唯一，实际命中"
                    + replacements + "处");
        }
        String oldNotice = "签署时间及文件校验信息见本合同末页《电子签署确认页》。";
        String displayNotice = "签署完成后可在系统查看签署凭证及文件校验信息。";
        int noticeReplacements = replaceLiteral(document.getParagraphs(), document.getTables(),
                oldNotice, displayNotice);
        for (XWPFHeader header : document.getHeaderList())
        {
            noticeReplacements += replaceLiteral(header.getParagraphs(), header.getTables(),
                    oldNotice, displayNotice);
        }
        for (XWPFFooter footer : document.getFooterList())
        {
            noticeReplacements += replaceLiteral(footer.getParagraphs(), footer.getTables(),
                    oldNotice, displayNotice);
        }
        if (noticeReplacements != 1)
        {
            throw new ServiceException("劳动合同签署证据说明必须唯一，实际命中"
                    + noticeReplacements + "处");
        }
    }

    private void validateTemplateSourceBytes(OaSignTemplate template,
            String actualTemplateSourceHash)
    {
        if (!isSha256(actualTemplateSourceHash))
        {
            throw new ServiceException("模板源文件指纹计算失败");
        }
        if (StringUtils.isNotBlank(template.getFileHash())
                && !actualTemplateSourceHash.equalsIgnoreCase(
                        StringUtils.trim(template.getFileHash())))
        {
            throw new ServiceException("模板源文件与冻结指纹不一致，已拒绝生成");
        }
        if (OaSignTemplateType.ONBOARD_LABOR_CONTRACT.equalsIgnoreCase(
                template.getTemplateType())
                && LABOR_V7_TEMPLATE_VERSION.equals(
                        StringUtils.trim(template.getTemplateVersion()))
                && !LABOR_V7_SOURCE_SHA256.equalsIgnoreCase(actualTemplateSourceHash))
        {
            throw new ServiceException("劳动合同v7源文件指纹不匹配，已拒绝生成");
        }
    }

    private boolean isSha256(String value)
    {
        return value != null && value.matches("(?i)^[0-9a-f]{64}$");
    }

    private String sha256(byte[] bytes)
    {
        try
        {
            return toHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private int replaceLiteral(List<XWPFParagraph> paragraphs, List<XWPFTable> tables,
            String marker, String replacement)
    {
        int replacements = 0;
        for (XWPFParagraph paragraph : paragraphs)
        {
            for (XWPFRun run : paragraph.getRuns())
            {
                String text = run.text();
                int occurrences = countOccurrences(text, marker);
                if (occurrences > 0)
                {
                    run.setText(text.replace(marker, replacement), 0);
                    replacements += occurrences;
                }
            }
            if (paragraph.getText().contains(marker))
            {
                throw new ServiceException("劳动合同甲方代表签署栏跨文本片段，未获准改写");
            }
        }
        for (XWPFTable table : tables)
        {
            for (XWPFTableRow row : table.getRows())
            {
                for (XWPFTableCell cell : row.getTableCells())
                {
                    replacements += replaceLiteral(cell.getParagraphs(), cell.getTables(),
                            marker, replacement);
                }
            }
        }
        return replacements;
    }

    private int countOccurrences(String text, String marker)
    {
        if (StringUtils.isEmpty(text) || StringUtils.isEmpty(marker))
        {
            return 0;
        }
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(marker, offset)) >= 0)
        {
            count++;
            offset += marker.length();
        }
        return count;
    }

    private void replaceTableText(XWPFTable table, Map<String, String> values)
    {
        for (XWPFTableRow row : table.getRows())
        {
            for (XWPFTableCell cell : row.getTableCells())
            {
                for (XWPFParagraph paragraph : cell.getParagraphs())
                {
                    replaceParagraphText(paragraph, values);
                }
                for (XWPFTable nested : cell.getTables())
                {
                    replaceTableText(nested, values);
                }
            }
        }
    }

    private void replaceParagraphText(XWPFParagraph paragraph, Map<String, String> values)
    {
        // Replace placeholders contained by one run first.  This preserves the
        // dedicated DejaVu Sans run used by real checkbox glyphs in the v6
        // contract checklist.  Only a token split across multiple runs falls
        // back to paragraph reconstruction below.
        for (XWPFRun run : paragraph.getRuns())
        {
            String originalRunText = run.text();
            String replacedRunText = replacePlaceholders(originalRunText, values);
            if (!originalRunText.equals(replacedRunText))
            {
                run.setText(replacedRunText, 0);
            }
        }
        String afterRunReplacement = paragraph.getText();
        String replaced = replacePlaceholders(afterRunReplacement, values);
        if (afterRunReplacement == null || afterRunReplacement.equals(replaced))
        {
            return;
        }
        List<XWPFRun> runs = paragraph.getRuns();
        for (int i = runs.size() - 1; i >= 0; i--)
        {
            paragraph.removeRun(i);
        }
        paragraph.createRun().setText(replaced, 0);
    }

    private void assertNoUnresolvedPlaceholders(XWPFDocument document)
    {
        String unresolved = findUnresolvedPlaceholder(document.getParagraphs(), document.getTables());
        if (unresolved == null)
        {
            for (XWPFHeader header : document.getHeaderList())
            {
                unresolved = findUnresolvedPlaceholder(header.getParagraphs(), header.getTables());
                if (unresolved != null)
                {
                    break;
                }
            }
        }
        if (unresolved == null)
        {
            for (XWPFFooter footer : document.getFooterList())
            {
                unresolved = findUnresolvedPlaceholder(footer.getParagraphs(), footer.getTables());
                if (unresolved != null)
                {
                    break;
                }
            }
        }
        if (unresolved != null)
        {
            throw new ServiceException("模板渲染后仍含未赋值占位符：" + unresolved);
        }
    }

    private String findUnresolvedPlaceholder(List<XWPFParagraph> paragraphs, List<XWPFTable> tables)
    {
        for (XWPFParagraph paragraph : paragraphs)
        {
            Matcher matcher = UNRESOLVED_PLACEHOLDER_PATTERN.matcher(paragraph.getText());
            if (matcher.find())
            {
                return matcher.group();
            }
        }
        for (XWPFTable table : tables)
        {
            String unresolved = findUnresolvedPlaceholder(table);
            if (unresolved != null)
            {
                return unresolved;
            }
        }
        return null;
    }

    private String findUnresolvedPlaceholder(XWPFTable table)
    {
        for (XWPFTableRow row : table.getRows())
        {
            for (XWPFTableCell cell : row.getTableCells())
            {
                String unresolved = findUnresolvedPlaceholder(cell.getParagraphs(), cell.getTables());
                if (unresolved != null)
                {
                    return unresolved;
                }
            }
        }
        return null;
    }

    private byte[] renderXlsx(InputStream input, Map<String, String> values) throws IOException
    {
        Map<String, String> escapedValues = xmlEscapedValues(values);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipInputStream zipInput = new ZipInputStream(input);
                ZipOutputStream zipOutput = new ZipOutputStream(output))
        {
            ZipEntry entry;
            while ((entry = zipInput.getNextEntry()) != null)
            {
                ZipEntry outputEntry = new ZipEntry(entry.getName());
                outputEntry.setTime(entry.getTime());
                zipOutput.putNextEntry(outputEntry);
                if (!entry.isDirectory())
                {
                    byte[] bytes = zipInput.readAllBytes();
                    if (entry.getName().endsWith(".xml"))
                    {
                        String xml = new String(bytes, StandardCharsets.UTF_8);
                        zipOutput.write(replacePlaceholders(xml, escapedValues).getBytes(StandardCharsets.UTF_8));
                    }
                    else
                    {
                        zipOutput.write(bytes);
                    }
                }
                zipOutput.closeEntry();
                zipInput.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private Map<String, String> xmlEscapedValues(Map<String, String> values)
    {
        Map<String, String> escaped = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet())
        {
            escaped.put(entry.getKey(), escapeXml(entry.getValue()));
        }
        return escaped;
    }

    private String escapeXml(String value)
    {
        if (value == null)
        {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String cellText(Cell cell, DataFormatter formatter)
    {
        if (cell == null)
        {
            return null;
        }
        if (cell.getCellType() == CellType.STRING)
        {
            return cell.getStringCellValue();
        }
        return formatter.formatCellValue(cell);
    }

    private byte[] buildSimplePdf(List<String> lines)
    {
        List<byte[]> pageImages = renderPdfPageJpegs(lines);
        byte[] contentStream = "q\n595 0 0 842 0 0 cm\n/Im1 Do\nQ\n".getBytes(StandardCharsets.ISO_8859_1);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try
        {
            writeAscii(out, "%PDF-1.4\n");
            List<Integer> offsets = new ArrayList<>();
            offsets.add(0);
            offsets.add(writeObject(out, "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n"));
            StringBuilder kids = new StringBuilder();
            for (int i = 0; i < pageImages.size(); i++)
            {
                kids.append(3 + i * 3).append(" 0 R ");
            }
            offsets.add(writeObject(out, "2 0 obj\n<< /Type /Pages /Kids [" + kids
                    + "] /Count " + pageImages.size() + " >>\nendobj\n"));
            for (int i = 0; i < pageImages.size(); i++)
            {
                int pageObject = 3 + i * 3;
                int contentObject = pageObject + 1;
                int imageObject = pageObject + 2;
                byte[] imageBytes = pageImages.get(i);
                offsets.add(writeObject(out, pageObject + " 0 obj\n"
                        + "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
                        + "/Resources << /XObject << /Im1 " + imageObject + " 0 R >> >> "
                        + "/Contents " + contentObject + " 0 R >>\nendobj\n"));
                offsets.add(writeStreamObject(out, contentObject, "<< /Length " + contentStream.length + " >>",
                        contentStream));
                offsets.add(writeStreamObject(out, imageObject, "<< /Type /XObject /Subtype /Image /Width "
                        + PDF_IMAGE_WIDTH + " /Height " + PDF_IMAGE_HEIGHT
                        + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length "
                        + imageBytes.length + " >>", imageBytes));
            }
            int xrefOffset = out.size();
            writeAscii(out, "xref\n0 " + offsets.size() + "\n");
            writeAscii(out, "0000000000 65535 f \n");
            for (int i = 1; i < offsets.size(); i++)
            {
                writeAscii(out, String.format("%010d 00000 n \n", offsets.get(i)));
            }
            writeAscii(out, "trailer\n<< /Size " + offsets.size() + " /Root 1 0 R >>\nstartxref\n"
                    + xrefOffset + "\n%%EOF\n");
            return out.toByteArray();
        }
        catch (IOException e)
        {
            throw new ServiceException("生成签约阅读文件失败: " + e.getMessage());
        }
    }

    private List<byte[]> renderPdfPageJpegs(List<String> lines)
    {
        List<byte[]> pages = new ArrayList<>();
        Font bodyFont = preferredChineseFont(Font.PLAIN, 24);
        BufferedImage metricsImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D metricsGraphics = metricsImage.createGraphics();
        int x = 90;
        int y = 110;
        List<RenderedText> currentPage = new ArrayList<>();
        try
        {
            metricsGraphics.setFont(bodyFont);
            FontMetrics metrics = metricsGraphics.getFontMetrics();
            for (String line : lines)
            {
                for (String wrapped : wrapLine(line, metrics, PDF_IMAGE_WIDTH - x * 2))
                {
                    if (y + metrics.getHeight() > PDF_IMAGE_HEIGHT - 90 && !currentPage.isEmpty())
                    {
                        pages.add(renderPdfPageJpeg(currentPage));
                        currentPage = new ArrayList<>();
                        y = 110;
                    }
                    currentPage.add(new RenderedText(wrapped, bodyFont, x, y));
                    y += metrics.getHeight() + 8;
                }
            }
            if (!currentPage.isEmpty())
            {
                pages.add(renderPdfPageJpeg(currentPage));
            }
            if (pages.isEmpty())
            {
                pages.add(renderPdfPageJpeg(Arrays.asList(new RenderedText("", bodyFont, x, y))));
            }
            return pages;
        }
        finally
        {
            metricsGraphics.dispose();
        }
    }

    private byte[] renderPdfPageJpeg(List<RenderedText> elements)
    {
        BufferedImage image = new BufferedImage(PDF_IMAGE_WIDTH, PDF_IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try
        {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, PDF_IMAGE_WIDTH, PDF_IMAGE_HEIGHT);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(Color.BLACK);
            for (RenderedText element : elements)
            {
                graphics.setFont(element.font);
                graphics.drawString(element.text, element.x, element.y);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", out);
            return out.toByteArray();
        }
        catch (IOException e)
        {
            throw new ServiceException("生成签约文件预览图片失败: " + e.getMessage());
        }
        finally
        {
            graphics.dispose();
        }
    }

    private Font preferredChineseFont(int style, int size)
    {
        List<String> preferredFonts = Arrays.asList("PingFang SC", "STHeiti", "Heiti SC", "Songti SC",
                "Noto Sans CJK SC", "Microsoft YaHei", "SimSun", "WenQuanYi Micro Hei", "SansSerif");
        List<String> available = Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames());
        for (String name : preferredFonts)
        {
            if (available.contains(name))
            {
                return new Font(name, style, size);
            }
        }
        return new Font(Font.SANS_SERIF, style, size);
    }

    private List<String> wrapLine(String line, FontMetrics metrics, int maxWidth)
    {
        List<String> wrapped = new ArrayList<>();
        String source = value(line);
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < source.length(); i++)
        {
            char ch = source.charAt(i);
            String candidate = current.toString() + ch;
            if (current.length() > 0 && metrics.stringWidth(candidate) > maxWidth)
            {
                wrapped.add(current.toString());
                current = new StringBuilder();
            }
            current.append(ch);
        }
        if (current.length() > 0)
        {
            wrapped.add(current.toString());
        }
        return wrapped;
    }

    private int writeObject(ByteArrayOutputStream out, String object) throws IOException
    {
        int offset = out.size();
        writeAscii(out, object);
        return offset;
    }

    private int writeStreamObject(ByteArrayOutputStream out, int number, String dictionary, byte[] stream)
            throws IOException
    {
        int offset = out.size();
        writeAscii(out, number + " 0 obj\n" + dictionary + "\nstream\n");
        out.write(stream);
        writeAscii(out, "\nendstream\nendobj\n");
        return offset;
    }

    private void writeAscii(ByteArrayOutputStream out, String text) throws IOException
    {
        out.write(text.getBytes(StandardCharsets.ISO_8859_1));
    }

    private String replacePlaceholders(String text, Map<String, String> values)
    {
        if (text == null)
        {
            return "";
        }
        String replaced = text;
        for (Map.Entry<String, String> entry : values.entrySet())
        {
            replaced = replaced.replace("${" + entry.getKey() + "}", entry.getValue());
            replaced = replaced.replace("${" + placeholderLabel(entry.getKey()) + "}",
                    entry.getValue());
        }
        return replaced;
    }

    private static String placeholderLabel(String placeholder)
    {
        return PLACEHOLDER_LABELS.getOrDefault(placeholder, "未登记字段");
    }

    private Map<String, String> placeholderValues(OaSignPackage signPackage,
            Collection<String> actualPackageTemplateTypes)
    {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("employeeName", value(signPackage.getEmployeeNameSnapshot()));
        values.put("employeeIdCard", value(signPackage.getEmployeeIdCardSnapshot()));
        values.put("employeePhone", value(signPackage.getEmployeePhoneSnapshot()));
        values.put("employeeAddress", value(signPackage.getEmployeeAddressSnapshot()));
        values.put("employeeDeptName", value(signPackage.getDeptNameSnapshot()));
        values.put("postName", value(signPackage.getPostNameSnapshot()));
        values.put("postLevel", dictionaryValue(signPackage.getPostLevelSnapshot()));
        values.put("employmentType", dictionaryValue(signPackage.getEmploymentType()));
        values.put("contractType", dictionaryValue(signPackage.getEmploymentType()));
        values.put("socialType", dictionaryValue(signPackage.getSocialType()));
        values.put("servicePersonType", dictionaryValue(signPackage.getServicePersonType()));
        values.put("insuranceType", dictionaryValue(signPackage.getInsuranceType()));
        values.put("scenario", dictionaryValue(signPackage.getScenario()));
        values.put("entryDate", value(signPackage.getEntryDate()));
        values.put("contractStartDate", value(signPackage.getContractStartDate()));
        values.put("contractEndDate", value(signPackage.getContractEndDate()));
        values.put("previousContractEndDate", value(signPackage.getPreviousContractEndDate()));
        values.put("previousEmploymentType", dictionaryValue(signPackage.getPreviousEmploymentType()));
        values.put("previousRenewalCount", value(signPackage.getPreviousRenewalCount() == null
                ? null : String.valueOf(signPackage.getPreviousRenewalCount())));
        values.put("renewalCount", value(signPackage.getRenewalCount() == null
                ? null : String.valueOf(signPackage.getRenewalCount())));
        values.put("probationStartDate", value(signPackage.getProbationStartDate()));
        values.put("probationEndDate", value(signPackage.getProbationEndDate()));
        values.put("actualRegularizationDate", value(signPackage.getActualRegularizationDate()));
        values.put("transferEffectiveDate", value(signPackage.getTransferEffectiveDate()));
        values.put("beforeDeptName", value(signPackage.getBeforeDeptNameSnapshot()));
        values.put("afterDeptName", value(signPackage.getDeptNameSnapshot()));
        values.put("beforePostName", value(signPackage.getBeforePostNameSnapshot()));
        values.put("afterPostName", value(signPackage.getPostNameSnapshot()));
        values.put("workStartDate", value(signPackage.getWorkStartDate()));
        values.put("incomeStartYearMonth", value(signPackage.getIncomeStartYearMonth()));
        values.put("workEndDate", value(signPackage.getWorkEndDate()));
        values.put("leaveDate", value(signPackage.getLeaveDate()));
        values.put("leaveReason", value(signPackage.getLeaveReason()));
        values.put("offboardingType", dictionaryValue(signPackage.getOffboardingType()));
        values.put("salarySettlementStatus", dictionaryValue(signPackage.getSalarySettlementStatus()));
        values.put("assetHandoverStatus", dictionaryValue(signPackage.getAssetHandoverStatus()));
        values.put("nonCompeteDecision", dictionaryValue(signPackage.getNonCompeteDecision()));
        values.put("compensationAmount", money(signPackage.getCompensationAmount()));
        values.put("compensationNote", value(signPackage.getCompensationNote()));
        values.put("baseSalary", money(signPackage.getBaseSalary()));
        values.put("postSalary", money(signPackage.getPostSalary()));
        values.put("fieldAllowance", money(signPackage.getFieldAllowance()));
        values.put("performanceSalary", money(signPackage.getPerformanceSalary()));
        values.put("salaryTotal", money(signPackage.getSalaryTotal()));
        values.put("salaryVersion", dictionaryValue(signPackage.getSalaryVersion()));
        values.put("companyName", value(firstNonBlank(signPackage.getLegalEntityNameSnapshot(),
                signPackage.getRecommendedCompanySnapshot())));
        values.put("companyCode", "内部编码不在合同中展示");
        values.put("companyCreditCode", value(signPackage.getLegalEntityCreditCodeSnapshot()));
        values.put("companyAddress", value(firstNonBlank(signPackage.getLegalEntityAddressSnapshot(),
                signPackage.getRecommendedRegisteredAddressSnapshot())));
        String legalRepresentative = signPackage.getLegalEntityIdSnapshot() == null
                ? firstNonBlank(signPackage.getLegalRepresentativeSnapshot(),
                        signPackage.getRecommendedLegalRepresentativeSnapshot())
                : signPackage.getLegalRepresentativeSnapshot();
        if (signPackage.getLegalEntityIdSnapshot() != null
                && StringUtils.isBlank(legalRepresentative))
        {
            throw new ServiceException("冻结甲方代表缺失");
        }
        values.put("companyLegalRepresentative", value(legalRepresentative));
        values.put("companyPhone", value(signPackage.getLegalEntityPhoneSnapshot()));
        boolean laborContractIncluded = containsTemplateType(actualPackageTemplateTypes,
                OaSignTemplateType.ONBOARD_LABOR_CONTRACT);
        boolean serviceContractIncluded = containsTemplateType(actualPackageTemplateTypes,
                OaSignTemplateType.ONBOARD_SERVICE_CONTRACT);
        boolean serviceReceiptIncluded = containsTemplateType(actualPackageTemplateTypes,
                OaSignTemplateType.ONBOARD_SERVICE_RECEIPT);
        String contractTermCode = normalizedContractTermCode(
                signPackage.getContractTermCodeSnapshot());
        if (laborContractIncluded && !SigningProfileCodes.isKnownContractTerm(contractTermCode))
        {
            throw new ServiceException("劳动合同缺少有效的合同期限类型快照");
        }
        boolean fixedTerm = SigningProfileCodes.FIXED_TERM.equals(contractTermCode);
        boolean openEnded = SigningProfileCodes.OPEN_ENDED.equals(contractTermCode);
        values.put("contractTermSelection", fixedTerm ? "A" : openEnded ? "B" : "");
        values.put("contractTermFixedMark", checkboxMark(fixedTerm));
        values.put("contractTermOpenEndedMark", checkboxMark(openEnded));
        values.put(ATTACHMENT_DORMITORY_MARK,
                checkboxMark(laborContractIncluded || serviceContractIncluded));
        values.put(ATTACHMENT_DUTY_MARK,
                checkboxMark(laborContractIncluded || serviceContractIncluded));
        boolean handbookIncluded = containsTemplateType(actualPackageTemplateTypes,
                OaSignTemplateType.ONBOARD_HANDBOOK)
                || containsTemplateType(actualPackageTemplateTypes,
                        OaSignTemplateType.ONBOARD_HANDBOOK_RECEIPT);
        values.put(ATTACHMENT_HANDBOOK_MARK, checkboxMark(handbookIncluded));
        values.put(ATTACHMENT_SALARY_MARK, checkboxMark(containsTemplateType(
                actualPackageTemplateTypes, OaSignTemplateType.ONBOARD_SALARY_CONFIRM)));
        values.put(ATTACHMENT_SERVICE_RECEIPT_MARK, checkboxMark(containsTemplateType(
                actualPackageTemplateTypes, OaSignTemplateType.ONBOARD_SERVICE_RECEIPT)));

        if (serviceContractIncluded || serviceReceiptIncluded)
        {
            String servicePersonType = normalizedServicePersonType(signPackage.getServicePersonType());
            if (servicePersonType == null)
            {
                throw new ServiceException("劳务人员类型必须为在校实习生或退休返聘人员");
            }
            values.put(SERVICE_STUDENT_MARK, checkboxMark("STUDENT_INTERN".equals(servicePersonType)));
            values.put(SERVICE_RETIRED_MARK, checkboxMark("RETIRED_REHIRE".equals(servicePersonType)));
        }
        else
        {
            values.put(SERVICE_STUDENT_MARK, UNCHECKED_BOX);
            values.put(SERVICE_RETIRED_MARK, UNCHECKED_BOX);
        }
        if (serviceReceiptIncluded)
        {
            String insuranceType = normalizedInsuranceType(signPackage.getInsuranceType());
            if (insuranceType == null)
            {
                throw new ServiceException("保险类型必须为商业意外保险或雇主责任险");
            }
            values.put(INSURANCE_COMMERCIAL_ACCIDENT_MARK,
                    checkboxMark("COMMERCIAL_ACCIDENT".equals(insuranceType)));
            values.put(INSURANCE_EMPLOYER_LIABILITY_MARK,
                    checkboxMark("EMPLOYER_LIABILITY".equals(insuranceType)));
        }
        else
        {
            values.put(INSURANCE_COMMERCIAL_ACCIDENT_MARK, UNCHECKED_BOX);
            values.put(INSURANCE_EMPLOYER_LIABILITY_MARK, UNCHECKED_BOX);
        }
        values.put("signDate", new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
        return values;
    }

    private String normalizedContractTermCode(String value)
    {
        return StringUtils.isBlank(value) ? null
                : value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private String normalizedServicePersonType(String value)
    {
        if (StringUtils.isBlank(value))
        {
            return null;
        }
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        if ("STUDENT_INTERN".equals(normalized) || "在校实习生".equals(value.trim()))
        {
            return "STUDENT_INTERN";
        }
        if ("RETIRED_REHIRE".equals(normalized)
                || "退休返聘".equals(value.trim())
                || "退休返聘人员".equals(value.trim()))
        {
            return "RETIRED_REHIRE";
        }
        return null;
    }

    private String normalizedInsuranceType(String value)
    {
        if (StringUtils.isBlank(value))
        {
            return null;
        }
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        if ("COMMERCIAL_ACCIDENT".equals(normalized) || "商业意外保险".equals(value.trim()))
        {
            return "COMMERCIAL_ACCIDENT";
        }
        if ("EMPLOYER_LIABILITY".equals(normalized) || "雇主责任险".equals(value.trim()))
        {
            return "EMPLOYER_LIABILITY";
        }
        return null;
    }

    private String checkboxMark(boolean included)
    {
        return included ? CHECKED_BOX : UNCHECKED_BOX;
    }

    private boolean containsTemplateType(Collection<String> actualPackageTemplateTypes, String expected)
    {
        if (actualPackageTemplateTypes == null || StringUtils.isBlank(expected))
        {
            return false;
        }
        for (String actual : actualPackageTemplateTypes)
        {
            if (expected.equalsIgnoreCase(StringUtils.trim(actual)))
            {
                return true;
            }
        }
        return false;
    }

    private String firstNonBlank(String preferred, String fallback)
    {
        return StringUtils.isNotBlank(preferred) ? preferred : fallback;
    }

    private String dictionaryValue(String source)
    {
        if (StringUtils.isBlank(source))
        {
            return "-";
        }
        String text = source.trim();
        String code = text.toUpperCase(java.util.Locale.ROOT);
        String label = DICTIONARY_LABELS.get(code);
        if (label != null)
        {
            return label;
        }
        Matcher salaryVersion = SALARY_VERSION_PATTERN.matcher(text);
        if (salaryVersion.matches())
        {
            return salaryVersion.group(1) + "年第" + salaryVersion.group(2) + "版";
        }
        Matcher level = LEVEL_PATTERN.matcher(text);
        if (level.matches())
        {
            return "第" + level.group(1) + "级";
        }
        if (text.matches(".*[A-Za-z].*"))
        {
            return "未登记中文名称";
        }
        return text;
    }

    private InputStream resolveInputStream(String fileUrl) throws IOException
    {
        if (StringUtils.isBlank(fileUrl))
        {
            return null;
        }
        if (fileUrl.startsWith("classpath:"))
        {
            return new ClassPathResource(fileUrl.substring("classpath:".length())).getInputStream();
        }
        Path path = resolveFilePath(fileUrl);
        if (path != null && Files.exists(path) && Files.isRegularFile(path))
        {
            return new FileInputStream(path.toFile());
        }
        return null;
    }

    public Path resolveGeneratedSignPackageFile(String fileUrl)
    {
        if (fileStorageService != null && fileStorageService.isManagedPublicUrl(fileUrl))
        {
            return fileStorageService.resolveAuthorizedPublicUrl(fileUrl);
        }
        String prefix = localFilePrefix.endsWith("/") ? localFilePrefix.substring(0, localFilePrefix.length() - 1)
                : localFilePrefix;
        if (StringUtils.isBlank(fileUrl) || !fileUrl.startsWith(prefix + "/sign-package/"))
        {
            throw new ServiceException("签约文件地址无效");
        }
        Path signPackageRoot = Paths.get(localFilePath, "sign-package").toAbsolutePath().normalize();
        Path file = Paths.get(localFilePath, fileUrl.substring(prefix.length())).toAbsolutePath().normalize();
        if (!file.startsWith(signPackageRoot) || !Files.exists(file) || !Files.isRegularFile(file))
        {
            throw new ServiceException("签约文件不存在");
        }
        return file;
    }

    /** 读取已由经办人配置并保存的本地文件，例如公司印章图片。 */
    public byte[] readConfiguredFileBytes(String fileUrl)
    {
        try (InputStream input = resolveInputStream(fileUrl))
        {
            if (input == null)
            {
                throw new ServiceException("配置文件不存在");
            }
            byte[] bytes = input.readAllBytes();
            if (bytes.length == 0 || bytes.length > 5 * 1024 * 1024)
            {
                throw new ServiceException("配置文件内容不合法");
            }
            return bytes;
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (IOException e)
        {
            throw new ServiceException("读取配置文件失败").setDetailMessage(e.getMessage());
        }
    }

    private Path resolveFilePath(String fileUrl)
    {
        String pathReference = fileUrl;
        if (fileUrl.startsWith("http://") || fileUrl.startsWith("https://"))
        {
            try
            {
                pathReference = new URI(fileUrl).getPath();
            }
            catch (URISyntaxException e)
            {
                return null;
            }
        }
        pathReference = stripConfiguredGatewayPrefix(pathReference);
        if (pathReference != null && pathReference.startsWith(localFilePrefix + "/"))
        {
            Path root = Paths.get(localFilePath).toAbsolutePath().normalize();
            Path candidate = root.resolve(pathReference.substring(localFilePrefix.length() + 1))
                    .normalize();
            return candidate.startsWith(root) ? candidate : null;
        }
        if (pathReference != null && pathReference.startsWith(localPublicFilePrefix + "/"))
        {
            Path publicRoot = Paths.get(localFilePath, "public").toAbsolutePath().normalize();
            Path candidate = publicRoot.resolve(
                    pathReference.substring(localPublicFilePrefix.length() + 1)).normalize();
            return candidate.startsWith(publicRoot) ? candidate : null;
        }
        Path direct = Paths.get(pathReference);
        if (direct.isAbsolute() || Files.exists(direct))
        {
            return direct;
        }
        return Paths.get(localFilePath, fileUrl);
    }

    private String stripConfiguredGatewayPrefix(String pathReference)
    {
        if (StringUtils.isBlank(pathReference) || StringUtils.isBlank(localPublicFilePrefix))
        {
            return pathReference;
        }
        String gatewayPrefix = StringUtils.isBlank(localFileGatewayPrefix)
                ? "/prod-api" : localFileGatewayPrefix.trim();
        if (!gatewayPrefix.startsWith("/"))
        {
            gatewayPrefix = "/" + gatewayPrefix;
        }
        while (gatewayPrefix.endsWith("/") && gatewayPrefix.length() > 1)
        {
            gatewayPrefix = gatewayPrefix.substring(0, gatewayPrefix.length() - 1);
        }
        String publicPrefix = localPublicFilePrefix.startsWith("/")
                ? localPublicFilePrefix : "/" + localPublicFilePrefix;
        String gatewayPublicPrefix = gatewayPrefix + publicPrefix + "/";
        if (pathReference.startsWith(gatewayPublicPrefix))
        {
            return pathReference.substring(gatewayPrefix.length());
        }
        return pathReference;
    }

    private void cleanupStagedQuietly(StagedSignFile stagedFile)
    {
        try
        {
            fileStorageService.cleanup(stagedFile);
        }
        catch (ServiceException ignored)
        {
            // Preserve the generation result; a later staging sweep can retry cleanup.
        }
    }

    private void deleteFileQuietly(Path file)
    {
        if (file == null)
        {
            return;
        }
        try
        {
            Files.deleteIfExists(file);
        }
        catch (IOException ignored)
        {
            // The containing staging-directory cleanup remains the fallback.
        }
    }

    private String toPublicUrl(Path output)
    {
        Path root = Paths.get(localFilePath).toAbsolutePath().normalize();
        Path absolute = output.toAbsolutePath().normalize();
        String relative = root.relativize(absolute).toString().replace('\\', '/');
        String prefix = localFilePrefix.endsWith("/") ? localFilePrefix.substring(0, localFilePrefix.length() - 1) : localFilePrefix;
        return prefix + "/" + relative;
    }

    private String sha256(Path path) throws IOException
    {
        try (InputStream input = Files.newInputStream(path))
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int len;
            while ((len = input.read(buffer)) != -1)
            {
                digest.update(buffer, 0, len);
            }
            return toHex(digest.digest());
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new ServiceException("计算签约文件校验值失败: " + e.getMessage());
        }
    }

    private String value(String value)
    {
        return value == null ? "" : value;
    }

    private String money(BigDecimal value)
    {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private String toHex(byte[] bytes)
    {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes)
        {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    private static class RenderedText
    {
        private final String text;
        private final Font font;
        private final int x;
        private final int y;

        private RenderedText(String text, Font font, int x, int y)
        {
            this.text = text;
            this.font = font;
            this.x = x;
            this.y = y;
        }
    }
}

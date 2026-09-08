package com.erp.oa.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.BodyElementType;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;
import com.erp.oa.domain.OaCompanySealConfig;
import com.erp.oa.domain.OaLaborContract;
import com.erp.oa.domain.OaLaborContractTemplate;
import com.erp.oa.domain.dto.OaLaborContractSignRequest;

@Service
public class OaLaborContractDocumentService
{
    private static final String MODULE_DIR = "labor-contract";
    private static final int MAX_SIGNATURE_BYTES = 1024 * 1024;
    private static final int MIN_SIGNATURE_WIDTH = 120;
    private static final int MIN_SIGNATURE_HEIGHT = 40;
    private static final int MIN_SIGNATURE_INK_PIXELS = 80;
    private static final int PDF_IMAGE_WIDTH = 1240;
    private static final int PDF_IMAGE_HEIGHT = 1754;
    private static final Set<String> REQUIRED_TEMPLATE_PLACEHOLDERS = new LinkedHashSet<>(Arrays.asList(
            "employeeName", "employeeIdCard", "employeePhone", "contractStartDate", "contractEndDate", "postName"));

    @Value("${file.path:./uploadPath}")
    private String localFilePath;

    @Value("${file.prefix:/profile}")
    private String localFilePrefix;

    @Value("${labor-contract.pdf.converter-command:}")
    private String pdfConverterCommand;

    public void assertTemplateContainsRequiredPlaceholders(String templateFileUrl)
    {
        if (StringUtils.isBlank(templateFileUrl))
        {
            throw new ServiceException("合同模板文件不能为空");
        }
        try (InputStream input = resolveInputStream(templateFileUrl))
        {
            if (input == null)
            {
                throw new ServiceException("合同模板文件不存在");
            }
            try (XWPFDocument document = new XWPFDocument(input))
            {
                String text = collectDocumentText(document);
                List<String> missing = new ArrayList<>();
                for (String placeholder : REQUIRED_TEMPLATE_PLACEHOLDERS)
                {
                    if (!text.contains("${" + placeholder + "}"))
                    {
                        missing.add("${" + placeholder + "}");
                    }
                }
                if (!missing.isEmpty())
                {
                    throw new ServiceException("合同模板缺少合同占位符：" + String.join("、", missing));
                }
            }
        }
        catch (IOException e)
        {
            throw new ServiceException("读取合同模板失败: " + e.getMessage());
        }
    }

    public OaLaborContractTemplate freezeTemplateSnapshot(OaLaborContract contract, OaLaborContractTemplate template)
    {
        if (template == null || StringUtils.isBlank(template.getTemplateFileUrl()))
        {
            throw new ServiceException("合同模板不存在");
        }
        try
        {
            Path output = newOutputPath(contract, "template.docx");
            try (InputStream input = resolveInputStream(template.getTemplateFileUrl()))
            {
                if (input == null)
                {
                    throw new ServiceException("合同模板文件不存在");
                }
                Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
            }
            OaLaborContractTemplate frozen = copyTemplate(template);
            frozen.setTemplateFileUrl(output.toAbsolutePath().toString());
            return frozen;
        }
        catch (IOException e)
        {
            throw new ServiceException("冻结合同模板失败: " + e.getMessage());
        }
    }

    public OaCompanySealConfig freezeSealSnapshot(OaLaborContract contract, OaCompanySealConfig sealConfig)
    {
        if (sealConfig == null || StringUtils.isBlank(sealConfig.getSealImageUrl()))
        {
            throw new ServiceException("请先配置启用状态的企业章");
        }
        try
        {
            byte[] bytes;
            try (InputStream input = resolveInputStream(sealConfig.getSealImageUrl()))
            {
                if (input == null)
                {
                    throw new ServiceException("企业章图片不存在");
                }
                bytes = input.readAllBytes();
            }
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null)
            {
                throw new ServiceException("企业章图片格式无效");
            }
            Path output = newOutputPath(contract, "seal.png");
            ImageIO.write(image, "png", output.toFile());
            OaCompanySealConfig frozen = copySeal(sealConfig);
            frozen.setSealImageUrl(output.toAbsolutePath().toString());
            return frozen;
        }
        catch (IOException e)
        {
            throw new ServiceException("冻结企业章失败: " + e.getMessage());
        }
    }

    public OaLaborContractTemplate loadFrozenTemplateSnapshot(OaLaborContract contract, OaLaborContractTemplate template)
    {
        Path snapshot = snapshotPath(contract, "template.docx");
        if (!Files.exists(snapshot) || !Files.isRegularFile(snapshot))
        {
            throw new ServiceException("合同模板快照不存在，请重新发送合同");
        }
        OaLaborContractTemplate frozen = copyTemplate(template);
        frozen.setTemplateFileUrl(snapshot.toAbsolutePath().toString());
        return frozen;
    }

    public OaCompanySealConfig loadFrozenSealSnapshot(OaLaborContract contract)
    {
        Path snapshot = snapshotPath(contract, "seal.png");
        if (!Files.exists(snapshot) || !Files.isRegularFile(snapshot))
        {
            throw new ServiceException("企业章快照不存在，请重新发送合同");
        }
        OaCompanySealConfig frozen = new OaCompanySealConfig();
        frozen.setSealName("冻结企业章");
        frozen.setSealImageUrl(snapshot.toAbsolutePath().toString());
        frozen.setStatus("0");
        return frozen;
    }

    public GeneratedContractFile generatePreview(OaLaborContract contract, OaLaborContractTemplate template,
            OaCompanySealConfig sealConfig)
    {
        try
        {
            Path output = newOutputPath(contract, "preview.docx");
            byte[] bytes = buildContractDocx(contract, template, sealConfig, null, false);
            Files.write(output, bytes);
            byte[] pdfBytes = buildPdf(output, bytes, previewPdfLines(contract, template, sealConfig),
                    pdfEvidenceImages(sealConfig, null));
            Files.write(newOutputPath(contract, "preview.pdf"), pdfBytes);
            String docxUrl = toPrivateDownloadUrl(contract, "preview");
            String pdfUrl = toPrivateDownloadUrl(contract, "preview-pdf");
            return new GeneratedContractFile(docxUrl, pdfUrl, sha256(pdfBytes), null);
        }
        catch (IOException e)
        {
            throw new ServiceException("生成合同预览失败: " + e.getMessage());
        }
    }

    public GeneratedContractFile generateSignedArchive(OaLaborContract contract, OaLaborContractTemplate template,
            OaCompanySealConfig sealConfig, OaLaborContractSignRequest request)
    {
        try
        {
            StoredFile signatureFile = writeSignatureImage(contract, request.getSignatureDataUrl());
            Path output = newOutputPath(contract, "archive.docx");
            byte[] bytes = buildContractDocx(contract, template, sealConfig, signatureFile.getPath(), true);
            Files.write(output, bytes);
            byte[] archivePdfBytes = buildPdf(output, bytes, archivePdfLines(contract, template, sealConfig),
                    pdfEvidenceImages(sealConfig, signatureFile.getPath()));
            Files.write(newOutputPath(contract, "archive.pdf"), archivePdfBytes);
            String archiveHash = sha256(archivePdfBytes);
            contract.setArchiveFileHash(archiveHash);
            byte[] certificatePdfBytes = buildSimplePdf(certificatePdfLines(contract));
            Files.write(newOutputPath(contract, "certificate.pdf"), certificatePdfBytes);
            String docxUrl = toPrivateDownloadUrl(contract, "archive");
            String pdfUrl = toPrivateDownloadUrl(contract, "archive-pdf");
            String certificateUrl = toPrivateDownloadUrl(contract, "certificate");
            return new GeneratedContractFile(docxUrl, pdfUrl, archiveHash, signatureFile.getUrl(),
                    certificateUrl, sha256(certificatePdfBytes));
        }
        catch (IOException e)
        {
            throw new ServiceException("归档签署合同失败: " + e.getMessage());
        }
    }

    public String calculateFileUrlSha256(String fileUrl)
    {
        try (InputStream input = resolveInputStream(fileUrl))
        {
            if (input == null)
            {
                throw new ServiceException("证据文件不存在");
            }
            return sha256(input.readAllBytes());
        }
        catch (IOException e)
        {
            throw new ServiceException("计算证据文件哈希失败: " + e.getMessage());
        }
    }

    public String calculateStoredFileSha256(Long contractId, String kind)
    {
        try
        {
            Path file = resolveContractFile(contractId, kind);
            if (!Files.exists(file) || !Files.isRegularFile(file))
            {
                throw new ServiceException("合同文件不存在");
            }
            return sha256(Files.readAllBytes(file));
        }
        catch (IOException e)
        {
            throw new ServiceException("计算合同文件哈希失败: " + e.getMessage());
        }
    }

    private byte[] buildContractDocx(OaLaborContract contract, OaLaborContractTemplate template,
            OaCompanySealConfig sealConfig, Path signaturePath, boolean signed) throws IOException
    {
        try (XWPFDocument document = openTemplate(template))
        {
            Map<String, String> values = placeholderValues(contract);
            replacePlaceholders(document, values);
            appendEvidencePage(document, contract, template, sealConfig, signaturePath, signed);
            try (ByteArrayOutputStream out = new ByteArrayOutputStream())
            {
                document.write(out);
                return out.toByteArray();
            }
        }
    }

    private XWPFDocument openTemplate(OaLaborContractTemplate template) throws IOException
    {
        String templateUrl = template == null ? null : template.getTemplateFileUrl();
        if (StringUtils.isBlank(templateUrl))
        {
            return new XWPFDocument();
        }
        try (InputStream input = resolveInputStream(templateUrl))
        {
            if (input == null)
            {
                return new XWPFDocument();
            }
            return new XWPFDocument(input);
        }
    }

    private InputStream resolveInputStream(String fileUrl) throws IOException
    {
        if (StringUtils.isBlank(fileUrl))
        {
            return null;
        }
        if (fileUrl.startsWith("classpath:"))
        {
            String resourcePath = fileUrl.substring("classpath:".length());
            ClassPathResource resource = new ClassPathResource(resourcePath);
            return resource.exists() ? resource.getInputStream() : null;
        }
        Path path = resolveLocalPath(fileUrl);
        if (path != null && Files.exists(path))
        {
            return new FileInputStream(path.toFile());
        }
        return null;
    }

    private String collectDocumentText(XWPFDocument document)
    {
        StringBuilder builder = new StringBuilder();
        collectParagraphText(builder, document.getParagraphs());
        collectTableText(builder, document.getTables());
        for (XWPFHeader header : document.getHeaderList())
        {
            collectParagraphText(builder, header.getParagraphs());
            collectTableText(builder, header.getTables());
        }
        for (XWPFFooter footer : document.getFooterList())
        {
            collectParagraphText(builder, footer.getParagraphs());
            collectTableText(builder, footer.getTables());
        }
        return builder.toString();
    }

    private void collectTableText(StringBuilder builder, List<XWPFTable> tables)
    {
        for (XWPFTable table : tables)
        {
            for (XWPFTableRow row : table.getRows())
            {
                for (XWPFTableCell cell : row.getTableCells())
                {
                    collectParagraphText(builder, cell.getParagraphs());
                    collectTableText(builder, cell.getTables());
                }
            }
        }
    }

    private void collectParagraphText(StringBuilder builder, List<XWPFParagraph> paragraphs)
    {
        for (XWPFParagraph paragraph : paragraphs)
        {
            builder.append(paragraph.getText()).append('\n');
        }
    }

    private Path resolveLocalPath(String fileUrl)
    {
        if (StringUtils.isBlank(fileUrl))
        {
            return null;
        }
        String normalized = fileUrl;
        int profileIndex = normalized.indexOf(localFilePrefix + "/");
        if (profileIndex >= 0)
        {
            normalized = normalized.substring(profileIndex + localFilePrefix.length());
            if (normalized.startsWith("/"))
            {
                normalized = normalized.substring(1);
            }
            return Paths.get(localFilePath, normalized);
        }
        int fileIndex = normalized.indexOf("/file/");
        if (fileIndex >= 0)
        {
            normalized = normalized.substring(fileIndex + "/file/".length());
            return Paths.get(localFilePath, normalized);
        }
        if (normalized.startsWith("/"))
        {
            return Paths.get(normalized);
        }
        return Paths.get(normalized);
    }

    private void replacePlaceholders(XWPFDocument document, Map<String, String> values)
    {
        replacePlaceholdersInParagraphs(document.getParagraphs(), values);
        replacePlaceholdersInTables(document.getTables(), values);
        for (XWPFHeader header : document.getHeaderList())
        {
            replacePlaceholdersInParagraphs(header.getParagraphs(), values);
            replacePlaceholdersInTables(header.getTables(), values);
        }
        for (XWPFFooter footer : document.getFooterList())
        {
            replacePlaceholdersInParagraphs(footer.getParagraphs(), values);
            replacePlaceholdersInTables(footer.getTables(), values);
        }
    }

    private void replacePlaceholdersInTables(List<XWPFTable> tables, Map<String, String> values)
    {
        for (XWPFTable table : tables)
        {
            for (XWPFTableRow row : table.getRows())
            {
                for (XWPFTableCell cell : row.getTableCells())
                {
                    replacePlaceholdersInParagraphs(cell.getParagraphs(), values);
                    replacePlaceholdersInTables(cell.getTables(), values);
                }
            }
        }
    }

    private void replacePlaceholdersInParagraphs(List<XWPFParagraph> paragraphs, Map<String, String> values)
    {
        for (XWPFParagraph paragraph : paragraphs)
        {
            String text = paragraph.getText();
            if (StringUtils.isBlank(text) || !text.contains("${"))
            {
                continue;
            }
            String replaced = text;
            for (Map.Entry<String, String> entry : values.entrySet())
            {
                replaced = replaced.replace("${" + entry.getKey() + "}", entry.getValue());
            }
            if (!replaced.equals(text))
            {
                for (int i = paragraph.getRuns().size() - 1; i >= 0; i--)
                {
                    paragraph.removeRun(i);
                }
                paragraph.createRun().setText(replaced);
            }
        }
    }

    private Map<String, String> placeholderValues(OaLaborContract contract)
    {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("employeeName", safe(contract.getEmployeeName()));
        values.put("employeeIdCard", safe(contract.getEmployeeIdCard()));
        values.put("employeePhone", safe(contract.getEmployeePhone()));
        values.put("employeeDeptName", safe(contract.getEmployeeDeptName()));
        values.put("shopDeptName", safe(contract.getShopDeptName()));
        values.put("postName", safe(contract.getPostName()));
        values.put("socialType", safe(contract.getSocialType()));
        values.put("contractStartDate", safe(contract.getContractStartDate()));
        values.put("contractEndDate", safe(contract.getContractEndDate()));
        values.put("probationStartDate", safe(contract.getProbationStartDate()));
        values.put("probationEndDate", safe(contract.getProbationEndDate()));
        values.put("baseSalary", money(contract.getBaseSalary()));
        values.put("managementAllowance", money(contract.getManagementAllowance()));
        values.put("overtimePay", money(contract.getOvertimePay()));
        values.put("rewardAllowance", money(contract.getRewardAllowance()));
        values.put("fullAttendanceBonus", money(contract.getFullAttendanceBonus()));
        values.put("socialSubsidy", money(contract.getSocialSubsidy()));
        values.put("commuteSubsidy", money(contract.getCommuteSubsidy()));
        values.put("totalSalary", money(contract.getTotalSalary()));
        return values;
    }

    private void appendEvidencePage(XWPFDocument document, OaLaborContract contract, OaLaborContractTemplate template,
            OaCompanySealConfig sealConfig, Path signaturePath, boolean signed)
    {
        XWPFParagraph title = document.createParagraph();
        XWPFRun titleRun = title.createRun();
        titleRun.setBold(true);
        titleRun.setText(signed ? "线上签署归档页" : "线上签署预览页");

        addLine(document, "合同编号: " + safe(contract.getContractNo()));
        addLine(document, "员工: " + safe(contract.getEmployeeName()) + " / " + safe(contract.getEmployeePhone()));
        addLine(document, "身份证: " + safe(contract.getEmployeeIdCard()));
        addLine(document, "岗位/店铺: " + safe(contract.getPostName()) + " / " + safe(contract.getShopDeptName()));
        addLine(document, "合同期限: " + safe(contract.getContractStartDate()) + " 至 " + safe(contract.getContractEndDate()));
        addLine(document, "社保口径: " + safe(contract.getSocialType()));
        addLine(document, "模板: " + (template == null ? "" : safe(template.getTemplateName())));
        addLine(document, "工资合计: " + money(contract.getTotalSalary()));
        addLine(document, "甲方章: " + (sealConfig == null ? "未配置" : safe(sealConfig.getSealName())));
        addPictureOrText(document, sealConfig == null ? null : resolveLocalPath(sealConfig.getSealImageUrl()), "甲方预置章");
        if (signed)
        {
            addLine(document, "员工签署IP: " + safe(contract.getSignerIp()));
            addLine(document, "员工签署UA: " + safe(contract.getSignerUserAgent()));
            addLine(document, "签署身份: ERP登录态绑定员工ID " + safe(String.valueOf(contract.getEmployeeId())));
            addPictureOrText(document, signaturePath, "员工手写签名");
        }
    }

    private void addLine(XWPFDocument document, String text)
    {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.createRun().setText(text);
    }

    private void addPictureOrText(XWPFDocument document, Path imagePath, String label)
    {
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.setText(label + ": ");
        if (imagePath == null || !Files.exists(imagePath))
        {
            run.setText("[未找到图片]");
            return;
        }
        try (InputStream input = Files.newInputStream(imagePath))
        {
            run.addPicture(input, pictureType(imagePath), imagePath.getFileName().toString(),
                    Units.toEMU(120), Units.toEMU(60));
        }
        catch (Exception e)
        {
            run.setText("[图片插入失败]");
        }
    }

    private int pictureType(Path imagePath)
    {
        String fileName = imagePath.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg"))
        {
            return Document.PICTURE_TYPE_JPEG;
        }
        return Document.PICTURE_TYPE_PNG;
    }

    private StoredFile writeSignatureImage(OaLaborContract contract, String signatureDataUrl) throws IOException
    {
        if (StringUtils.isBlank(signatureDataUrl))
        {
            throw new ServiceException("签名不能为空");
        }
        if (!signatureDataUrl.startsWith("data:image/png;base64,"))
        {
            throw new ServiceException("签名图片必须为PNG格式");
        }
        String base64 = signatureDataUrl.substring("data:image/png;base64,".length());
        byte[] bytes;
        try
        {
            bytes = Base64.getDecoder().decode(base64.getBytes(StandardCharsets.UTF_8));
        }
        catch (IllegalArgumentException e)
        {
            throw new ServiceException("签名图片Base64无效");
        }
        if (bytes.length == 0)
        {
            throw new ServiceException("签名不能为空");
        }
        if (bytes.length > MAX_SIGNATURE_BYTES)
        {
            throw new ServiceException("签名图片不能超过1MB");
        }
        BufferedImage signatureImage = ImageIO.read(new ByteArrayInputStream(bytes));
        if (signatureImage == null)
        {
            throw new ServiceException("签名图片必须为有效PNG");
        }
        validateSignatureImage(signatureImage);
        Path output = newOutputPath(contract, "signature.png");
        Files.write(output, bytes);
        return new StoredFile(output, toPrivateDownloadUrl(contract, "signature"));
    }

    private void validateSignatureImage(BufferedImage image)
    {
        if (image.getWidth() < MIN_SIGNATURE_WIDTH || image.getHeight() < MIN_SIGNATURE_HEIGHT)
        {
            throw new ServiceException("签名图片尺寸过小，请重新签名");
        }
        int inkPixels = 0;
        for (int y = 0; y < image.getHeight(); y++)
        {
            for (int x = 0; x < image.getWidth(); x++)
            {
                int argb = image.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha < 20)
                {
                    continue;
                }
                int red = (argb >>> 16) & 0xFF;
                int green = (argb >>> 8) & 0xFF;
                int blue = argb & 0xFF;
                if ((255 - red) + (255 - green) + (255 - blue) > 90)
                {
                    inkPixels++;
                }
            }
        }
        if (inkPixels < MIN_SIGNATURE_INK_PIXELS)
        {
            throw new ServiceException("签名笔迹过少或为空白，请重新签名");
        }
    }

    Path resolveContractFile(Long contractId, String kind)
    {
        if (contractId == null)
        {
            throw new ServiceException("合同ID不能为空");
        }
        String fileName;
        if ("preview".equals(kind))
        {
            fileName = "preview.docx";
        }
        else if ("preview-pdf".equals(kind))
        {
            fileName = "preview.pdf";
        }
        else if ("archive".equals(kind))
        {
            fileName = "archive.docx";
        }
        else if ("archive-pdf".equals(kind))
        {
            fileName = "archive.pdf";
        }
        else if ("certificate".equals(kind))
        {
            fileName = "certificate.pdf";
        }
        else if ("signature".equals(kind))
        {
            fileName = "signature.png";
        }
        else
        {
            throw new ServiceException("不支持的合同文件类型");
        }
        return Paths.get(localFilePath, MODULE_DIR, String.valueOf(contractId), fileName);
    }

    private Path newOutputPath(OaLaborContract contract, String fileName) throws IOException
    {
        Path dir = Paths.get(localFilePath, MODULE_DIR, String.valueOf(contract.getContractId()));
        Files.createDirectories(dir);
        return dir.resolve(fileName);
    }

    private Path snapshotPath(OaLaborContract contract, String fileName)
    {
        if (contract == null || contract.getContractId() == null)
        {
            throw new ServiceException("合同ID不能为空");
        }
        return Paths.get(localFilePath, MODULE_DIR, String.valueOf(contract.getContractId()), fileName);
    }

    private String toPrivateDownloadUrl(OaLaborContract contract, String kind)
    {
        return "/oa/laborContract/download/" + contract.getContractId() + "/" + kind;
    }

    private OaLaborContractTemplate copyTemplate(OaLaborContractTemplate template)
    {
        OaLaborContractTemplate copy = new OaLaborContractTemplate();
        if (template != null)
        {
            copy.setTemplateId(template.getTemplateId());
            copy.setTemplateName(template.getTemplateName());
            copy.setSocialType(template.getSocialType());
            copy.setTemplateVersion(template.getTemplateVersion());
            copy.setTemplateFileUrl(template.getTemplateFileUrl());
            copy.setStatus(template.getStatus());
            copy.setBuiltIn(template.getBuiltIn());
            copy.setRemark(template.getRemark());
        }
        return copy;
    }

    private OaCompanySealConfig copySeal(OaCompanySealConfig sealConfig)
    {
        OaCompanySealConfig copy = new OaCompanySealConfig();
        if (sealConfig != null)
        {
            copy.setSealId(sealConfig.getSealId());
            copy.setSealName(sealConfig.getSealName());
            copy.setSealImageUrl(sealConfig.getSealImageUrl());
            copy.setStatus(sealConfig.getStatus());
            copy.setRemark(sealConfig.getRemark());
        }
        return copy;
    }

    private List<PdfImage> pdfEvidenceImages(OaCompanySealConfig sealConfig, Path signaturePath)
    {
        List<PdfImage> images = new ArrayList<>();
        Path sealPath = sealConfig == null ? null : resolveLocalPath(sealConfig.getSealImageUrl());
        if (sealPath != null)
        {
            images.add(new PdfImage("甲方预置章", sealPath, 240, 160));
        }
        if (signaturePath != null)
        {
            images.add(new PdfImage("员工手写签名", signaturePath, 360, 160));
        }
        return images;
    }

    private List<String> docxTextLines(byte[] docxBytes, List<String> fallback)
    {
        List<String> lines = new ArrayList<>();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docxBytes)))
        {
            for (IBodyElement element : document.getBodyElements())
            {
                if (element.getElementType() == BodyElementType.PARAGRAPH)
                {
                    addNonBlankLine(lines, ((XWPFParagraph) element).getText());
                }
                else if (element.getElementType() == BodyElementType.TABLE)
                {
                    addTableLines(lines, (XWPFTable) element);
                }
            }
        }
        catch (IOException e)
        {
            return fallback;
        }
        return lines.isEmpty() ? fallback : lines;
    }

    private void addTableLines(List<String> lines, XWPFTable table)
    {
        for (XWPFTableRow row : table.getRows())
        {
            List<String> cells = new ArrayList<>();
            for (XWPFTableCell cell : row.getTableCells())
            {
                String text = normalizeText(cell.getText());
                if (StringUtils.isNotBlank(text))
                {
                    cells.add(text);
                }
            }
            addNonBlankLine(lines, String.join("    ", cells));
        }
    }

    private void addNonBlankLine(List<String> lines, String text)
    {
        String normalized = normalizeText(text);
        if (StringUtils.isNotBlank(normalized))
        {
            lines.add(normalized);
        }
    }

    private String normalizeText(String text)
    {
        return safe(text).replaceAll("\\s+", " ").trim();
    }

    private List<String> previewPdfLines(OaLaborContract contract, OaLaborContractTemplate template,
            OaCompanySealConfig sealConfig)
    {
        List<String> lines = commonEvidenceLines("劳动合同预览", contract, template, sealConfig);
        lines.add("状态: 待员工签署");
        return lines;
    }

    private List<String> archivePdfLines(OaLaborContract contract, OaLaborContractTemplate template,
            OaCompanySealConfig sealConfig)
    {
        List<String> lines = commonEvidenceLines("劳动合同归档", contract, template, sealConfig);
        lines.add("签署账号ID: " + safe(String.valueOf(contract.getEmployeeId())));
        lines.add("签署IP: " + safe(contract.getSignerIp()));
        lines.add("签署UA: " + safe(contract.getSignerUserAgent()));
        lines.add("签署版本: " + safe(contract.getDocumentVersion()));
        lines.add("预览哈希: " + safe(contract.getPreviewFileHash()));
        return lines;
    }

    private List<String> certificatePdfLines(OaLaborContract contract)
    {
        List<String> lines = new ArrayList<>();
        lines.add("劳动合同签署完成证明");
        lines.add("合同ID: " + safe(String.valueOf(contract.getContractId())));
        lines.add("合同编号: " + safe(contract.getContractNo()));
        lines.add("员工: " + safe(contract.getEmployeeName()));
        lines.add("员工账号ID: " + safe(String.valueOf(contract.getEmployeeId())));
        lines.add("签署版本: " + safe(contract.getDocumentVersion()));
        lines.add("预览哈希: " + safe(contract.getPreviewFileHash()));
        lines.add("归档哈希: " + safe(contract.getArchiveFileHash()));
        lines.add("签署IP: " + safe(contract.getSignerIp()));
        lines.add("签署UA: " + safe(contract.getSignerUserAgent()));
        lines.add("签署时间: " + safe(String.valueOf(contract.getSignedTime())));
        return lines;
    }

    private List<String> commonEvidenceLines(String title, OaLaborContract contract, OaLaborContractTemplate template,
            OaCompanySealConfig sealConfig)
    {
        List<String> lines = new ArrayList<>();
        lines.add(title);
        lines.add("合同ID: " + safe(String.valueOf(contract.getContractId())));
        lines.add("合同编号: " + safe(contract.getContractNo()));
        lines.add("员工: " + safe(contract.getEmployeeName()));
        lines.add("手机号: " + safe(contract.getEmployeePhone()));
        lines.add("身份证: " + safe(contract.getEmployeeIdCard()));
        lines.add("岗位: " + safe(contract.getPostName()));
        lines.add("店铺: " + safe(contract.getShopDeptName()));
        lines.add("合同期限: " + safe(contract.getContractStartDate()) + " 至 " + safe(contract.getContractEndDate()));
        lines.add("社保口径: " + safe(contract.getSocialType()));
        lines.add("模板: " + (template == null ? "" : safe(template.getTemplateName())));
        lines.add("企业章: " + (sealConfig == null ? "" : safe(sealConfig.getSealName())));
        lines.add("工资合计: " + money(contract.getTotalSalary()));
        return lines;
    }

    private byte[] buildPdf(Path docxPath, byte[] docxBytes, List<String> fallbackLines, List<PdfImage> images)
            throws IOException
    {
        byte[] convertedPdf = convertDocxToPdf(docxPath);
        if (convertedPdf != null)
        {
            return convertedPdf;
        }
        return buildSimplePdf(docxTextLines(docxBytes, fallbackLines), images);
    }

    private byte[] convertDocxToPdf(Path docxPath) throws IOException
    {
        String command = StringUtils.isBlank(pdfConverterCommand) ? null : pdfConverterCommand.trim();
        if (StringUtils.isBlank(command))
        {
            return null;
        }
        Path outputDir = Files.createTempDirectory("labor-contract-pdf-");
        Path logFile = outputDir.resolve("converter.log");
        try
        {
            ProcessBuilder builder = new ProcessBuilder(command, "--headless", "--convert-to", "pdf",
                    "--outdir", outputDir.toString(), docxPath.toString());
            builder.redirectErrorStream(true);
            builder.redirectOutput(logFile.toFile());
            Process process = builder.start();
            boolean completed;
            try
            {
                completed = process.waitFor(60, TimeUnit.SECONDS);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                throw new ServiceException("DOCX转PDF被中断");
            }
            if (!completed)
            {
                process.destroyForcibly();
                throw new ServiceException("DOCX转PDF超时");
            }
            if (process.exitValue() != 0)
            {
                throw new ServiceException("DOCX转PDF失败: " + converterLog(logFile));
            }
            Path pdfFile = resolveConvertedPdf(outputDir, docxPath);
            if (pdfFile == null || !Files.exists(pdfFile))
            {
                throw new ServiceException("DOCX转PDF失败: 未生成PDF文件");
            }
            return Files.readAllBytes(pdfFile);
        }
        finally
        {
            deleteDirectoryQuietly(outputDir);
        }
    }

    private Path resolveConvertedPdf(Path outputDir, Path docxPath) throws IOException
    {
        String fileName = docxPath.getFileName().toString();
        int extensionIndex = fileName.lastIndexOf('.');
        String baseName = extensionIndex >= 0 ? fileName.substring(0, extensionIndex) : fileName;
        Path expected = outputDir.resolve(baseName + ".pdf");
        if (Files.exists(expected))
        {
            return expected;
        }
        try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(outputDir, "*.pdf"))
        {
            for (Path file : stream)
            {
                return file;
            }
        }
        return null;
    }

    private String converterLog(Path logFile)
    {
        try
        {
            if (!Files.exists(logFile))
            {
                return "";
            }
            String text = Files.readString(logFile, StandardCharsets.UTF_8);
            return text.length() > 200 ? text.substring(0, 200) : text;
        }
        catch (IOException e)
        {
            return "";
        }
    }

    private void deleteDirectoryQuietly(Path directory)
    {
        if (directory == null || !Files.exists(directory))
        {
            return;
        }
        try
        {
            try (java.util.stream.Stream<Path> paths = Files.walk(directory))
            {
                paths.sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try
                            {
                                Files.deleteIfExists(path);
                            }
                            catch (IOException ignored)
                            {
                            }
                        });
            }
        }
        catch (IOException ignored)
        {
        }
    }

    private byte[] buildSimplePdf(List<String> lines)
    {
        return buildSimplePdf(lines, new ArrayList<>());
    }

    private byte[] buildSimplePdf(List<String> lines, List<PdfImage> images)
    {
        List<byte[]> pageImages = renderEvidencePageJpegs(lines, images);
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
            throw new ServiceException("生成PDF失败: " + e.getMessage());
        }
    }

    private List<byte[]> renderEvidencePageJpegs(List<String> lines, List<PdfImage> images)
    {
        List<byte[]> pages = new ArrayList<>();
        List<PdfImage> pendingImages = readablePdfImages(images);
        Font titleFont = preferredChineseFont(Font.BOLD, 34);
        Font bodyFont = preferredChineseFont(Font.PLAIN, 24);
        BufferedImage metricsImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D metricsGraphics = metricsImage.createGraphics();
        int x = 130;
        int y = 150;
        List<RenderedElement> currentPage = new ArrayList<>();
        try
        {
            for (int i = 0; i < lines.size(); i++)
            {
                Font font = i == 0 ? titleFont : bodyFont;
                metricsGraphics.setFont(font);
                FontMetrics metrics = metricsGraphics.getFontMetrics();
                for (String wrapped : wrapLine(lines.get(i), metrics, PDF_IMAGE_WIDTH - x * 2))
                {
                    if (y + metrics.getHeight() > PDF_IMAGE_HEIGHT - 100 && !currentPage.isEmpty())
                    {
                        pages.add(renderEvidencePageJpeg(currentPage));
                        currentPage = new ArrayList<>();
                        y = 150;
                    }
                    currentPage.add(RenderedElement.text(wrapped, font, x, y));
                    y += metrics.getHeight() + 8;
                }
                PdfImage matchedImage = removeImageForLine(lines.get(i), pendingImages);
                if (matchedImage != null)
                {
                    RenderedElement renderedImage = renderedImage(matchedImage, x, y);
                    if (renderedImage != null)
                    {
                        if (y + renderedImage.getHeight() > PDF_IMAGE_HEIGHT - 100 && !currentPage.isEmpty())
                        {
                            pages.add(renderEvidencePageJpeg(currentPage));
                            currentPage = new ArrayList<>();
                            y = 150;
                            renderedImage = renderedImage(matchedImage, x, y);
                        }
                        currentPage.add(renderedImage);
                        y += renderedImage.getHeight() + 18;
                    }
                }
                if (i == 0)
                {
                    y += 18;
                }
            }
            for (PdfImage image : pendingImages)
            {
                metricsGraphics.setFont(bodyFont);
                FontMetrics metrics = metricsGraphics.getFontMetrics();
                for (String wrapped : wrapLine(image.getLabel() + ": ", metrics, PDF_IMAGE_WIDTH - x * 2))
                {
                    if (y + metrics.getHeight() > PDF_IMAGE_HEIGHT - 100 && !currentPage.isEmpty())
                    {
                        pages.add(renderEvidencePageJpeg(currentPage));
                        currentPage = new ArrayList<>();
                        y = 150;
                    }
                    currentPage.add(RenderedElement.text(wrapped, bodyFont, x, y));
                    y += metrics.getHeight() + 8;
                }
                RenderedElement renderedImage = renderedImage(image, x, y);
                if (renderedImage != null)
                {
                    if (y + renderedImage.getHeight() > PDF_IMAGE_HEIGHT - 100 && !currentPage.isEmpty())
                    {
                        pages.add(renderEvidencePageJpeg(currentPage));
                        currentPage = new ArrayList<>();
                        y = 150;
                        renderedImage = renderedImage(image, x, y);
                    }
                    currentPage.add(renderedImage);
                    y += renderedImage.getHeight() + 18;
                }
            }
            if (!currentPage.isEmpty())
            {
                pages.add(renderEvidencePageJpeg(currentPage));
            }
            if (pages.isEmpty())
            {
                pages.add(renderEvidencePageJpeg(Arrays.asList(RenderedElement.text("", bodyFont, x, y))));
            }
            return pages;
        }
        finally
        {
            metricsGraphics.dispose();
        }
    }

    private List<PdfImage> readablePdfImages(List<PdfImage> images)
    {
        List<PdfImage> readable = new ArrayList<>();
        if (images == null)
        {
            return readable;
        }
        for (PdfImage image : images)
        {
            if (image.getPath() != null && Files.exists(image.getPath()) && Files.isRegularFile(image.getPath()))
            {
                readable.add(image);
            }
        }
        return readable;
    }

    private PdfImage removeImageForLine(String line, List<PdfImage> images)
    {
        String normalized = normalizeText(line);
        for (int i = 0; i < images.size(); i++)
        {
            PdfImage image = images.get(i);
            if (normalized.startsWith(image.getLabel() + ":") || normalized.startsWith(image.getLabel() + "："))
            {
                return images.remove(i);
            }
        }
        return null;
    }

    private RenderedElement renderedImage(PdfImage image, int x, int y)
    {
        try
        {
            BufferedImage source = ImageIO.read(image.getPath().toFile());
            if (source == null)
            {
                return null;
            }
            double scale = Math.min((double) image.getMaxWidth() / source.getWidth(),
                    (double) image.getMaxHeight() / source.getHeight());
            int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
            return RenderedElement.image(source, x, y, width, height);
        }
        catch (IOException e)
        {
            return null;
        }
    }

    private byte[] renderEvidencePageJpeg(List<RenderedElement> elements)
    {
        BufferedImage image = new BufferedImage(PDF_IMAGE_WIDTH, PDF_IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try
        {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, PDF_IMAGE_WIDTH, PDF_IMAGE_HEIGHT);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            for (RenderedElement element : elements)
            {
                if (element.isImage())
                {
                    graphics.drawImage(element.getImage(), element.getX(), element.getY(),
                            element.getWidth(), element.getHeight(), null);
                }
                else
                {
                    graphics.setFont(element.getFont());
                    graphics.setColor(Color.BLACK);
                    graphics.drawString(element.getText(), element.getX(), element.getY());
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", out);
            return out.toByteArray();
        }
        catch (IOException e)
        {
            throw new ServiceException("生成PDF图片失败: " + e.getMessage());
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
        String source = safe(line);
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

    private static class PdfImage
    {
        private final String label;
        private final Path path;
        private final int maxWidth;
        private final int maxHeight;

        private PdfImage(String label, Path path, int maxWidth, int maxHeight)
        {
            this.label = label;
            this.path = path;
            this.maxWidth = maxWidth;
            this.maxHeight = maxHeight;
        }

        private String getLabel()
        {
            return label;
        }

        private Path getPath()
        {
            return path;
        }

        private int getMaxWidth()
        {
            return maxWidth;
        }

        private int getMaxHeight()
        {
            return maxHeight;
        }
    }

    private static class RenderedElement
    {
        private final String text;
        private final Font font;
        private final BufferedImage image;
        private final int x;
        private final int y;
        private final int width;
        private final int height;

        private RenderedElement(String text, Font font, BufferedImage image, int x, int y, int width, int height)
        {
            this.text = text;
            this.font = font;
            this.image = image;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        private static RenderedElement text(String text, Font font, int x, int y)
        {
            return new RenderedElement(text, font, null, x, y, 0, 0);
        }

        private static RenderedElement image(BufferedImage image, int x, int y, int width, int height)
        {
            return new RenderedElement(null, null, image, x, y, width, height);
        }

        private boolean isImage()
        {
            return image != null;
        }

        private String getText()
        {
            return text;
        }

        private Font getFont()
        {
            return font;
        }

        private BufferedImage getImage()
        {
            return image;
        }

        private int getX()
        {
            return x;
        }

        private int getY()
        {
            return y;
        }

        private int getWidth()
        {
            return width;
        }

        private int getHeight()
        {
            return height;
        }
    }

    private String sha256(byte[] bytes)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash)
            {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new ServiceException("计算合同文件哈希失败");
        }
    }

    private String safe(String value)
    {
        return value == null ? "" : value;
    }

    private String money(BigDecimal value)
    {
        return value == null ? "0.00" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static class StoredFile
    {
        private final Path path;
        private final String url;

        private StoredFile(Path path, String url)
        {
            this.path = path;
            this.url = url;
        }

        private Path getPath()
        {
            return path;
        }

        private String getUrl()
        {
            return url;
        }
    }
}

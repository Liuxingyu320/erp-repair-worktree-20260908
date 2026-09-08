package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.BodyElementType;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.oa.domain.OaCompanySealConfig;
import com.erp.oa.domain.OaLaborContract;
import com.erp.oa.domain.OaLaborContractTemplate;
import com.erp.oa.domain.dto.OaLaborContractSignRequest;

@DisplayName("OA劳动合同文档生成")
class OaLaborContractDocumentServiceTest
{
    private static final String ONE_PIXEL_PNG =
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII=";

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("有社保和无社保内置模板都能生成预览文件")
    void shouldGeneratePreviewFromBuiltInTemplates() throws Exception
    {
        OaLaborContractDocumentService service = documentService();

        GeneratedContractFile socialPreview = service.generatePreview(contract(), template("有社保",
                "classpath:/templates/labor-contract/social.docx"), sealConfig());
        GeneratedContractFile noSocialPreview = service.generatePreview(contract(), template("无社保",
                "classpath:/templates/labor-contract/no-social.docx"), sealConfig());

        assertGeneratedDocx(service, socialPreview, "preview");
        assertGeneratedDocx(service, noSocialPreview, "preview");
    }

    @Test
    @DisplayName("预览PDF不能把中文证据内容渲染成问号")
    void shouldRenderChineseEvidencePdfWithoutQuestionMarkPlaceholders() throws Exception
    {
        OaLaborContractDocumentService service = documentService();

        service.generatePreview(contract(), template("有社保",
                "classpath:/templates/labor-contract/social.docx"), sealConfig());

        Path pdf = service.resolveContractFile(100L, "preview-pdf");
        String pdfSource = new String(Files.readAllBytes(pdf), StandardCharsets.ISO_8859_1);
        assertThat(pdfSource).doesNotContain("????");
    }

    @Test
    @DisplayName("签署归档会写入签名图片并计算归档哈希")
    void shouldGenerateSignedArchiveAndSignatureImage() throws Exception
    {
        OaLaborContractDocumentService service = documentService();
        OaLaborContract contract = contract();
        contract.setSignerIp("127.0.0.1");
        contract.setSignerUserAgent("JUnit");
        OaLaborContractSignRequest request = new OaLaborContractSignRequest();
        request.setConfirmed(true);
        request.setSignatureDataUrl(redSignatureDataUrl());

        GeneratedContractFile archive = service.generateSignedArchive(contract,
                template("有社保", "classpath:/templates/labor-contract/social.docx"), sealConfig(), request);

        assertGeneratedDocx(service, archive, "archive");
        assertThat(archive.getSignatureFileUrl()).isEqualTo("/oa/laborContract/download/100/signature");
        assertThat(service.resolveContractFile(contract.getContractId(), "signature")).exists();
    }

    @Test
    @DisplayName("签署归档PDF应渲染员工手写签名")
    void signedArchivePdfShouldRenderSignatureImage() throws Exception
    {
        OaLaborContractDocumentService service = documentService();
        OaLaborContract contract = contract();
        contract.setSignerIp("127.0.0.1");
        contract.setSignerUserAgent("JUnit");
        OaLaborContractSignRequest request = new OaLaborContractSignRequest();
        request.setConfirmed(true);
        request.setSignatureDataUrl(redSignatureDataUrl());

        service.generateSignedArchive(contract, template("有社保",
                "classpath:/templates/labor-contract/social.docx"), sealConfig(), request);

        byte[] pdfBytes = Files.readAllBytes(service.resolveContractFile(contract.getContractId(), "archive-pdf"));
        assertThat(pdfContainsRedPixels(pdfBytes)).isTrue();
    }

    @Test
    @DisplayName("劳动合同归档文件和签名文件不返回公开静态资源URL")
    void contractArchiveAndSignatureShouldNotReturnPublicStaticUrls() throws Exception
    {
        OaLaborContractDocumentService service = documentService();
        OaLaborContract contract = contract();
        contract.setSignerIp("127.0.0.1");
        contract.setSignerUserAgent("JUnit");
        OaLaborContractSignRequest request = new OaLaborContractSignRequest();
        request.setConfirmed(true);
        request.setSignatureDataUrl(redSignatureDataUrl());

        GeneratedContractFile archive = service.generateSignedArchive(contract,
                template("有社保", "classpath:/templates/labor-contract/social.docx"), sealConfig(), request);

        assertThat(archive.getDocxUrl()).startsWith("/oa/laborContract/download/");
        assertThat(archive.getSignatureFileUrl()).startsWith("/oa/laborContract/download/");
        assertThat(archive.getDocxUrl()).doesNotStartWith("/file/");
        assertThat(archive.getSignatureFileUrl()).doesNotStartWith("/file/");
        assertThat(archive.getDocxUrl()).doesNotStartWith("/profile/");
        assertThat(archive.getSignatureFileUrl()).doesNotStartWith("/profile/");
    }

    @Test
    @DisplayName("上传文件服务URL可映射回本地文件计算哈希")
    void shouldCalculateHashFromFileServiceUrl() throws Exception
    {
        OaLaborContractDocumentService service = documentService();
        Path upload = tempDir.resolve("public/2026/06/14/seal.png");
        Files.createDirectories(upload.getParent());
        byte[] bytes = "seal-image".getBytes();
        Files.write(upload, bytes);

        String hash = service.calculateFileUrlSha256("http://localhost:8080/file/public/2026/06/14/seal.png");

        assertThat(hash).isEqualTo(sha256(bytes));
    }

    @Test
    @DisplayName("模板占位符替换应覆盖表格和拆分run")
    void shouldReplacePlaceholdersInsideTablesAndSplitRuns() throws Exception
    {
        OaLaborContractDocumentService service = documentService();
        Path templateFile = tempDir.resolve("table-template.docx");
        writePlaceholderTemplate(templateFile);

        service.generatePreview(contract(), template("自定义", templateFile.toString()), sealConfig());

        String text = readDocxText(service.resolveContractFile(100L, "preview"));
        assertThat(text).contains("员工: 张三");
        assertThat(text).contains("岗位: 茶艺师");
        assertThat(text).contains("工资: 2660.00");
        assertThat(text).doesNotContain("${employeeName}");
        assertThat(text).doesNotContain("${postName}");
        assertThat(text).doesNotContain("${baseSalary}");
    }

    @Test
    @DisplayName("配置DOCX转PDF命令时应优先使用真实转换结果")
    void shouldPreferConfiguredDocxToPdfConverter() throws Exception
    {
        OaLaborContractDocumentService service = documentService();
        Path converter = fakePdfConverter();
        ReflectionTestUtils.setField(service, "pdfConverterCommand", converter.toString());

        GeneratedContractFile generated = service.generatePreview(contract(), template("有社保",
                "classpath:/templates/labor-contract/social.docx"), sealConfig());

        byte[] pdfBytes = Files.readAllBytes(service.resolveContractFile(100L, "preview-pdf"));
        assertThat(new String(pdfBytes, StandardCharsets.ISO_8859_1)).contains("converted by fake office");
        assertThat(generated.getSha256()).isEqualTo(sha256(pdfBytes));
    }

    @Test
    @DisplayName("签名图片过小或空白时拒绝签署归档")
    void shouldRejectTinyOrBlankSignatureImage() throws Exception
    {
        OaLaborContractDocumentService service = documentService();
        OaLaborContractSignRequest tiny = new OaLaborContractSignRequest();
        tiny.setConfirmed(true);
        tiny.setSignatureDataUrl(ONE_PIXEL_PNG);

        assertThatThrownBy(() -> service.generateSignedArchive(contract(),
                template("有社保", "classpath:/templates/labor-contract/social.docx"), sealConfig(), tiny))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("签名图片");

        OaLaborContractSignRequest blank = new OaLaborContractSignRequest();
        blank.setConfirmed(true);
        blank.setSignatureDataUrl(blankSignatureDataUrl());

        assertThatThrownBy(() -> service.generateSignedArchive(contract(),
                template("有社保", "classpath:/templates/labor-contract/social.docx"), sealConfig(), blank))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("签名");
    }

    private OaLaborContractDocumentService documentService()
    {
        OaLaborContractDocumentService service = new OaLaborContractDocumentService();
        ReflectionTestUtils.setField(service, "localFilePath", tempDir.toString());
        ReflectionTestUtils.setField(service, "localFilePrefix", "/profile");
        return service;
    }

    private void assertGeneratedDocx(OaLaborContractDocumentService service, GeneratedContractFile generated, String kind)
            throws Exception
    {
        assertThat(generated.getDocxUrl()).isEqualTo("/oa/laborContract/download/100/" + kind);
        assertThat(generated.getSha256()).hasSize(64);
        Path file = service.resolveContractFile(100L, kind);
        assertThat(file).exists();
        assertThat(Files.size(file)).isGreaterThan(0);
    }

    private OaLaborContract contract()
    {
        OaLaborContract contract = new OaLaborContract();
        contract.setContractId(100L);
        contract.setEmployeeId(88L);
        contract.setEmployeeName("张三");
        contract.setEmployeePhone("13800000000");
        contract.setEmployeeIdCard("330100199001010011");
        contract.setShopDeptName("湖滨店");
        contract.setPostName("茶艺师");
        contract.setSocialType("有社保");
        contract.setContractStartDate("2026-05-01");
        contract.setContractEndDate("2028-04-30");
        contract.setProbationStartDate("2026-05-01");
        contract.setProbationEndDate("2026-06-30");
        contract.setBaseSalary(new BigDecimal("2660.00"));
        contract.setFullAttendanceBonus(new BigDecimal("300.00"));
        contract.setOvertimePay(new BigDecimal("800.00"));
        contract.setCommuteSubsidy(new BigDecimal("200.00"));
        contract.setTotalSalary(new BigDecimal("3960.00"));
        return contract;
    }

    private OaLaborContractTemplate template(String socialType, String fileUrl)
    {
        OaLaborContractTemplate template = new OaLaborContractTemplate();
        template.setTemplateId(1L);
        template.setTemplateName(socialType + "模板");
        template.setSocialType(socialType);
        template.setTemplateFileUrl(fileUrl);
        template.setStatus("0");
        return template;
    }

    private OaCompanySealConfig sealConfig()
    {
        OaCompanySealConfig seal = new OaCompanySealConfig();
        seal.setSealId(1L);
        seal.setSealName("默认企业章");
        seal.setSealImageUrl("/profile/labor-contract/seal.png");
        seal.setStatus("0");
        return seal;
    }

    private String sha256(byte[] bytes) throws Exception
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

    private String redSignatureDataUrl() throws Exception
    {
        BufferedImage image = new BufferedImage(220, 80, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try
        {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(Color.RED);
            graphics.fillRect(20, 28, 180, 24);
        }
        finally
        {
            graphics.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
    }

    private boolean pdfContainsRedPixels(byte[] pdfBytes) throws Exception
    {
        for (int i = 0; i < pdfBytes.length - 1; i++)
        {
            if ((pdfBytes[i] & 0xFF) == 0xFF && (pdfBytes[i + 1] & 0xFF) == 0xD8)
            {
                int end = findJpegEnd(pdfBytes, i + 2);
                if (end > i && imageContainsRedPixels(pdfBytes, i, end + 2))
                {
                    return true;
                }
            }
        }
        return false;
    }

    private int findJpegEnd(byte[] bytes, int start)
    {
        for (int i = start; i < bytes.length - 1; i++)
        {
            if ((bytes[i] & 0xFF) == 0xFF && (bytes[i + 1] & 0xFF) == 0xD9)
            {
                return i;
            }
        }
        return -1;
    }

    private boolean imageContainsRedPixels(byte[] bytes, int start, int end) throws Exception
    {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes, start, end - start));
        if (image == null)
        {
            return false;
        }
        int redPixels = 0;
        for (int y = 0; y < image.getHeight(); y++)
        {
            for (int x = 0; x < image.getWidth(); x++)
            {
                Color color = new Color(image.getRGB(x, y));
                if (color.getRed() > 150 && color.getGreen() < 120 && color.getBlue() < 120)
                {
                    redPixels++;
                }
            }
        }
        return redPixels > 50;
    }

    private void writePlaceholderTemplate(Path templateFile) throws Exception
    {
        try (XWPFDocument document = new XWPFDocument())
        {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.createRun().setText("员工: ${employee");
            paragraph.createRun().setText("Name}");
            XWPFTable table = document.createTable(2, 1);
            table.getRow(0).getCell(0).setText("岗位: ${postName}");
            table.getRow(1).getCell(0).setText("工资: ${baseSalary}");
            try (ByteArrayOutputStream out = new ByteArrayOutputStream())
            {
                document.write(out);
                Files.write(templateFile, out.toByteArray());
            }
        }
    }

    private String readDocxText(Path file) throws Exception
    {
        StringBuilder text = new StringBuilder();
        try (XWPFDocument document = new XWPFDocument(Files.newInputStream(file)))
        {
            for (IBodyElement element : document.getBodyElements())
            {
                if (element.getElementType() == BodyElementType.PARAGRAPH)
                {
                    text.append(((XWPFParagraph) element).getText()).append('\n');
                }
                else if (element.getElementType() == BodyElementType.TABLE)
                {
                    appendTableText(text, (XWPFTable) element);
                }
            }
        }
        return text.toString();
    }

    private void appendTableText(StringBuilder text, XWPFTable table)
    {
        for (XWPFTableRow row : table.getRows())
        {
            for (XWPFTableCell cell : row.getTableCells())
            {
                text.append(cell.getText()).append('\n');
            }
        }
    }

    private Path fakePdfConverter() throws Exception
    {
        Path script = tempDir.resolve("fake-office.sh");
        Files.writeString(script, "#!/bin/sh\n"
                + "outdir=\"\"\n"
                + "input=\"\"\n"
                + "previous=\"\"\n"
                + "for arg in \"$@\"; do\n"
                + "  if [ \"$previous\" = \"--outdir\" ]; then outdir=\"$arg\"; fi\n"
                + "  input=\"$arg\"\n"
                + "  previous=\"$arg\"\n"
                + "done\n"
                + "base=$(basename \"$input\" .docx)\n"
                + "printf '%s\\n' '%PDF-1.4' 'converted by fake office' '%%EOF' > \"$outdir/$base.pdf\"\n",
                StandardCharsets.UTF_8);
        assertThat(script.toFile().setExecutable(true)).isTrue();
        return script;
    }

    private String blankSignatureDataUrl() throws Exception
    {
        BufferedImage image = new BufferedImage(220, 80, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try
        {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        }
        finally
        {
            graphics.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
    }
}

package com.erp.oa.service.invoice;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import com.erp.oa.config.OaReimbursementProperties;

@Service
public class OaLocalInvoiceRecognitionEngine
        implements OaInvoiceRecognitionEngine
{
    private static final long MAX_OFD_XML_BYTES = 2L * 1024L * 1024L;
    private static final long MAX_OFD_TOTAL_XML_BYTES = 8L * 1024L * 1024L;

    private final OaReimbursementProperties properties;
    private final OaInvoiceTextParser parser;
    private volatile String resolvedCommand;
    private volatile boolean commandResolved;

    public OaLocalInvoiceRecognitionEngine(
            OaReimbursementProperties properties,
            OaInvoiceTextParser parser)
    {
        this.properties = properties;
        this.parser = parser;
    }

    @Override
    public String engine()
    {
        return "local";
    }

    @Override
    public String provider()
    {
        return "tesseract";
    }

    @Override
    public boolean available()
    {
        return properties.getOcr().getLocal().isEnabled();
    }

    public boolean imageOcrAvailable()
    {
        return available() && resolveCommand() != null;
    }

    @Override
    public String unavailableReason()
    {
        if (!properties.getOcr().getLocal().isEnabled())
        {
            return "本地识别已关闭";
        }
        return resolveCommand() == null
                ? "未找到Tesseract，本地仍可读取文本型PDF/OFD"
                : "";
    }

    @Override
    public OaInvoiceRecognitionResult recognize(Path path, String extension)
    {
        if (!available())
        {
            return failure("unconfigured", unavailableReason());
        }
        if (path == null || !Files.isRegularFile(path))
        {
            return failure("failed", "发票文件不存在");
        }
        String type = extension == null ? ""
                : extension.toLowerCase(Locale.ROOT).replace(".", "");
        try
        {
            return switch (type)
            {
                case "pdf" -> recognizePdf(path);
                case "ofd" -> recognizeOfd(path);
                case "png", "jpg", "jpeg" -> recognizeImage(path);
                default -> failure("failed", "本地识别不支持该发票格式");
            };
        }
        catch (Exception exception)
        {
            return failure("failed", "本地识别失败："
                    + safeMessage(exception));
        }
    }

    private OaInvoiceRecognitionResult recognizePdf(Path path)
            throws Exception
    {
        try (PDDocument document = Loader.loadPDF(path.toFile()))
        {
            if (document.getNumberOfPages() <= 0)
            {
                return failure("failed", "PDF没有可识别页面");
            }
            String text = new PDFTextStripper().getText(document);
            OaInvoiceRecognitionResult nativeResult =
                    parser.parse(text, "pdfbox");
            if (nativeResult.hasRecognizedFields())
            {
                nativeResult.setMessage("已读取电子发票内嵌文字");
                return nativeResult;
            }
            if (!imageOcrAvailable())
            {
                nativeResult.setStatus("unconfigured");
                nativeResult.setMessage(
                        "PDF没有可提取文字，且未找到Tesseract图像识别程序");
                return nativeResult;
            }
            PDFRenderer renderer = new PDFRenderer(document);
            int dpi = Math.max(120, Math.min(400,
                    properties.getOcr().getLocal().getRenderDpi()));
            BufferedImage image = renderer.renderImageWithDPI(
                    0, dpi, ImageType.RGB);
            Path rendered = temporary(".png");
            try
            {
                if (!ImageIO.write(image, "png", rendered.toFile()))
                {
                    return failure("failed", "无法生成PDF识别图像");
                }
                return tesseract(rendered);
            }
            finally
            {
                Files.deleteIfExists(rendered);
            }
        }
    }

    private OaInvoiceRecognitionResult recognizeOfd(Path path)
            throws Exception
    {
        String text = extractOfdText(path);
        OaInvoiceRecognitionResult result = parser.parse(text, "ofd-text");
        if (!result.hasRecognizedFields())
        {
            result.setMessage(
                    "OFD没有可提取的结构化文字，请改用云端识别或人工修正");
        }
        else
        {
            result.setMessage("已读取OFD电子发票文字");
        }
        return result;
    }

    private OaInvoiceRecognitionResult recognizeImage(Path path)
            throws Exception
    {
        if (!imageOcrAvailable())
        {
            return failure("unconfigured",
                    "未找到Tesseract图像识别程序");
        }
        return tesseract(path);
    }

    private OaInvoiceRecognitionResult tesseract(Path input)
            throws Exception
    {
        Path output = temporary(".txt");
        Path errors = temporary(".log");
        try
        {
            List<String> command = new ArrayList<>();
            command.add(resolveCommand());
            command.add(input.toString());
            command.add("stdout");
            command.add("-l");
            command.add(properties.getOcr().getLocal().getLanguages());
            command.add("--psm");
            command.add("6");
            Process process = new ProcessBuilder(command)
                    .redirectOutput(output.toFile())
                    .redirectError(errors.toFile())
                    .start();
            int seconds = Math.max(5, Math.min(120,
                    properties.getOcr().getLocal().getTimeoutSeconds()));
            if (!process.waitFor(seconds, TimeUnit.SECONDS))
            {
                process.destroyForcibly();
                return failure("failed", "本地识别超时");
            }
            String text = Files.readString(output, StandardCharsets.UTF_8);
            if (process.exitValue() != 0)
            {
                String error = Files.readString(errors,
                        StandardCharsets.UTF_8);
                return failure("failed", "Tesseract识别失败："
                        + limit(error.trim(), 240));
            }
            OaInvoiceRecognitionResult result =
                    parser.parse(text, "tesseract");
            if (result.hasRecognizedFields())
            {
                result.setMessage("本地Tesseract识别完成"
                        + ("partial".equals(result.getStatus())
                                ? "，请核对缺失字段" : ""));
            }
            return result;
        }
        finally
        {
            Files.deleteIfExists(output);
            Files.deleteIfExists(errors);
        }
    }

    private String extractOfdText(Path path) throws Exception
    {
        StringBuilder text = new StringBuilder();
        long total = 0;
        int entries = 0;
        try (ZipInputStream zip = new ZipInputStream(
                Files.newInputStream(path)))
        {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null && entries++ < 512)
            {
                String name = entry.getName().toLowerCase(Locale.ROOT);
                if (entry.isDirectory() || !name.endsWith(".xml"))
                {
                    continue;
                }
                byte[] xml = readLimited(zip, MAX_OFD_XML_BYTES);
                total += xml.length;
                if (total > MAX_OFD_TOTAL_XML_BYTES)
                {
                    throw new IOException("OFD展开内容超过安全上限");
                }
                appendTextCodes(xml, text);
                if (text.length() >= 100_000)
                {
                    break;
                }
            }
        }
        return limit(text.toString(), 100_000);
    }

    private byte[] readLimited(ZipInputStream input, long maximum)
            throws IOException
    {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        long total = 0;
        while ((read = input.read(buffer)) != -1)
        {
            total += read;
            if (total > maximum)
            {
                throw new IOException("OFD单个XML超过安全上限");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private void appendTextCodes(byte[] xml, StringBuilder target)
            throws Exception
    {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities",
                false);
        XMLStreamReader reader = factory.createXMLStreamReader(
                new ByteArrayInputStream(xml), StandardCharsets.UTF_8.name());
        boolean textCode = false;
        try
        {
            while (reader.hasNext() && target.length() < 100_000)
            {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT
                        && "TextCode".equals(reader.getLocalName()))
                {
                    textCode = true;
                }
                else if (event == XMLStreamConstants.END_ELEMENT
                        && "TextCode".equals(reader.getLocalName()))
                {
                    textCode = false;
                    target.append('\n');
                }
                else if (textCode
                        && (event == XMLStreamConstants.CHARACTERS
                                || event == XMLStreamConstants.CDATA))
                {
                    target.append(reader.getText());
                }
            }
        }
        finally
        {
            reader.close();
        }
    }

    private synchronized String resolveCommand()
    {
        if (commandResolved)
        {
            return resolvedCommand;
        }
        commandResolved = true;
        String configured = properties.getOcr().getLocal().getCommand();
        if (configured != null && !configured.isBlank()
                && !"auto".equalsIgnoreCase(configured))
        {
            resolvedCommand = executable(configured);
            return resolvedCommand;
        }
        for (String candidate : List.of(
                "/opt/homebrew/bin/tesseract",
                "/usr/local/bin/tesseract",
                "/usr/bin/tesseract"))
        {
            resolvedCommand = executable(candidate);
            if (resolvedCommand != null)
            {
                return resolvedCommand;
            }
        }
        String path = System.getenv("PATH");
        if (path != null)
        {
            for (String directory : path.split(
                    PatternHolder.PATH_SEPARATOR))
            {
                resolvedCommand = executable(
                        Paths.get(directory, "tesseract").toString());
                if (resolvedCommand != null)
                {
                    return resolvedCommand;
                }
            }
        }
        return null;
    }

    private String executable(String candidate)
    {
        try
        {
            Path path = Paths.get(candidate).toAbsolutePath().normalize();
            return Files.isRegularFile(path) && Files.isExecutable(path)
                    ? path.toString() : null;
        }
        catch (RuntimeException ignored)
        {
            return null;
        }
    }

    private Path temporary(String suffix) throws IOException
    {
        Path root = Paths.get(properties.getTempRoot()).toAbsolutePath()
                .normalize();
        Files.createDirectories(root);
        return Files.createTempFile(root, "invoice-ocr-", suffix);
    }

    private OaInvoiceRecognitionResult failure(String status,
            String message)
    {
        return OaInvoiceRecognitionResult.failure("local", "local",
                provider(), status, message);
    }

    private String safeMessage(Exception exception)
    {
        String value = exception.getMessage();
        return value == null || value.isBlank()
                ? exception.getClass().getSimpleName()
                : limit(value, 240);
    }

    private static String limit(String value, int maximum)
    {
        if (value == null || value.length() <= maximum)
        {
            return value;
        }
        return value.substring(0, maximum);
    }

    private static final class PatternHolder
    {
        private static final String PATH_SEPARATOR =
                java.util.regex.Pattern.quote(
                        System.getProperty("path.separator"));

        private PatternHolder()
        {
        }
    }
}

package com.erp.oa.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.config.OaSignFileProperties;

@Service
public class OaOfficePdfConverter
{
    private static final byte[] PDF_HEADER = "%PDF-".getBytes(StandardCharsets.ISO_8859_1);

    private final String converterCommand;
    private final int timeoutSeconds;

    public OaOfficePdfConverter(OaSignFileProperties properties)
    {
        if (properties == null || properties.getPdf() == null)
        {
            throw new IllegalArgumentException("PDF转换配置不能为空");
        }
        this.converterCommand = properties.getPdf().getConverterCommand();
        this.timeoutSeconds = properties.getPdf().getTimeoutSeconds();
        if (converterCommand == null || converterCommand.isBlank())
        {
            throw new IllegalArgumentException("PDF转换命令不能为空");
        }
        if (timeoutSeconds <= 0)
        {
            throw new IllegalArgumentException("PDF转换超时时间必须大于0秒");
        }
    }

    public Path convert(Path renderedSource, Path stagingDirectory)
    {
        if (renderedSource == null || !Files.isRegularFile(renderedSource))
        {
            throw conversionFailure("待转换Office文件不存在", null);
        }
        if (stagingDirectory == null)
        {
            throw conversionFailure("PDF转换临时目录不能为空", null);
        }

        Path outputDirectory = stagingDirectory.toAbsolutePath().normalize();
        String sourceName = renderedSource.getFileName().toString();
        int extensionIndex = sourceName.lastIndexOf('.');
        String baseName = extensionIndex > 0 ? sourceName.substring(0, extensionIndex) : sourceName;
        Path targetPdf = outputDirectory.resolve(baseName + ".pdf").normalize();
        Process process = null;
        try
        {
            Files.createDirectories(outputDirectory);
            Files.deleteIfExists(targetPdf);
            Path userProfile = outputDirectory.resolve("office-profile-"
                    + UUID.randomUUID().toString().replace("-", "")).normalize();
            List<String> command = Arrays.asList(converterCommand,
                    "-env:UserInstallation=" + userProfile.toUri(),
                    "--headless", "--convert-to", "pdf", "--outdir", outputDirectory.toString(),
                    renderedSource.toAbsolutePath().normalize().toString());
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            ByteArrayOutputStream processOutput = new ByteArrayOutputStream();
            Thread outputReader = drainOutput(process.getInputStream(), processOutput);

            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS))
            {
                process.destroyForcibly();
                Files.deleteIfExists(targetPdf);
                throw conversionFailure("转换超时（" + timeoutSeconds + "秒）", null);
            }
            outputReader.join(1000L);
            int exitCode = process.exitValue();
            String outputText = processOutput.toString(StandardCharsets.UTF_8);
            if (exitCode != 0)
            {
                Files.deleteIfExists(targetPdf);
                throw conversionFailure("转换进程退出码" + exitCode, outputText);
            }
            if (!Files.isRegularFile(targetPdf) || Files.size(targetPdf) <= PDF_HEADER.length)
            {
                Files.deleteIfExists(targetPdf);
                throw conversionFailure("转换器未生成有效PDF", outputText);
            }
            try (InputStream input = Files.newInputStream(targetPdf))
            {
                byte[] header = input.readNBytes(PDF_HEADER.length);
                if (!Arrays.equals(header, PDF_HEADER))
                {
                    Files.deleteIfExists(targetPdf);
                    throw conversionFailure("转换器生成的文件不是有效PDF", outputText);
                }
            }
            return targetPdf;
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            if (process != null)
            {
                process.destroyForcibly();
            }
            deleteQuietly(targetPdf);
            throw conversionFailure("转换过程被中断", e.getMessage());
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (IOException e)
        {
            deleteQuietly(targetPdf);
            throw conversionFailure("无法执行Office转换", e.getMessage());
        }
    }

    private Thread drainOutput(InputStream input, ByteArrayOutputStream output)
    {
        Thread reader = new Thread(() -> {
            try (input)
            {
                input.transferTo(output);
            }
            catch (IOException ignored)
            {
                // The process result remains authoritative; output is diagnostic only.
            }
        }, "office-pdf-converter-output");
        reader.setDaemon(true);
        reader.start();
        return reader;
    }

    private ServiceException conversionFailure(String reason, String detail)
    {
        return new ServiceException("PDF_CONVERSION_FAILED: " + reason).setDetailMessage(detail);
    }

    private void deleteQuietly(Path path)
    {
        try
        {
            Files.deleteIfExists(path);
        }
        catch (IOException ignored)
        {
            // A scheduled staging-directory cleanup can retry this best-effort cleanup.
        }
    }
}

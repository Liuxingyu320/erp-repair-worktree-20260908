package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.config.OaSignFileProperties;

@DisplayName("Office阅读PDF转换")
class OaOfficePdfConverterTest
{
    @TempDir
    Path tempDir;

    @Test
    @DisplayName("固定LibreOffice参数并支持带空格文件名")
    void shouldConvertOfficeFileWithSpaces() throws Exception
    {
        OaOfficePdfConverter converter = converter(script("""
                base=$(basename "$source")
                base=${base%.*}
                printf '%%PDF-1.4\n%%fake\n' > "$outdir/$base.pdf"
                """), 5);
        Path source = tempDir.resolve("employee contract with spaces.docx");
        Files.writeString(source, "docx");

        Path pdf = converter.convert(source, tempDir.resolve("output with spaces"));

        assertThat(pdf).hasFileName("employee contract with spaces.pdf").isNotEmptyFile();
        assertThat(Files.readString(pdf, StandardCharsets.ISO_8859_1)).startsWith("%PDF-1.4");
    }

    @Test
    @DisplayName("每次转换使用独立LibreOffice用户目录避免并发串进程")
    void shouldUseIsolatedLibreOfficeUserProfile() throws Exception
    {
        Path arguments = tempDir.resolve("converter-arguments.txt");
        Path command = Files.createTempFile(tempDir, "capture-libreoffice-", ".sh");
        Files.writeString(command, """
                #!/bin/sh
                printf '%s\n' "$@" > "__ARGUMENTS__"
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
                base=$(basename "$source")
                base=${base%%.*}
                printf '%%PDF-1.4\n%%fake\n' > "$outdir/$base.pdf"
                """.replace("__ARGUMENTS__", arguments.toString()));
        assertThat(command.toFile().setExecutable(true)).isTrue();
        OaOfficePdfConverter converter = converter(command, 5);
        Path source = tempDir.resolve("contract.docx");
        Files.writeString(source, "docx");
        Path output = tempDir.resolve("isolated-output");

        converter.convert(source, output);

        assertThat(Files.readAllLines(arguments))
                .anyMatch(value -> value.matches("-env:UserInstallation=file:.*/office-profile-[a-f0-9]{32}/?"));
    }

    @Test
    @DisplayName("非0退出码明确失败且不保留目标PDF")
    void shouldRejectNonZeroExit() throws Exception
    {
        OaOfficePdfConverter converter = converter(script("exit 7"), 5);
        Path source = tempDir.resolve("contract.docx");
        Files.writeString(source, "docx");
        Path output = tempDir.resolve("nonzero");

        assertThatThrownBy(() -> converter.convert(source, output))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("PDF_CONVERSION_FAILED")
                .hasMessageContaining("退出码7");
        assertThat(output.resolve("contract.pdf")).doesNotExist();
    }

    @Test
    @DisplayName("转换器未生成PDF时明确失败")
    void shouldRejectMissingOutput() throws Exception
    {
        OaOfficePdfConverter converter = converter(script("exit 0"), 5);
        Path source = tempDir.resolve("application.xlsx");
        Files.writeString(source, "xlsx");

        assertThatThrownBy(() -> converter.convert(source, tempDir.resolve("missing")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("PDF_CONVERSION_FAILED")
                .hasMessageContaining("未生成");
    }

    @Test
    @DisplayName("转换超时后终止进程并清理目标PDF")
    void shouldTerminateTimedOutConversion() throws Exception
    {
        OaOfficePdfConverter converter = converter(script("sleep 3"), 1);
        Path source = tempDir.resolve("slow.docx");
        Files.writeString(source, "docx");
        Path output = tempDir.resolve("timeout");

        assertThatThrownBy(() -> converter.convert(source, output))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("PDF_CONVERSION_FAILED")
                .hasMessageContaining("超时");
        assertThat(output.resolve("slow.pdf")).doesNotExist();
    }

    private OaOfficePdfConverter converter(Path command, int timeoutSeconds)
    {
        OaSignFileProperties properties = new OaSignFileProperties();
        properties.getPdf().setConverterCommand(command.toString());
        properties.getPdf().setTimeoutSeconds(timeoutSeconds);
        return new OaOfficePdfConverter(properties);
    }

    private Path script(String body) throws Exception
    {
        Path script = Files.createTempFile(tempDir, "fake-libreoffice-", ".sh");
        Files.writeString(script, """
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
                """ + body + "\n");
        assertThat(script.toFile().setExecutable(true)).isTrue();
        return script;
    }
}

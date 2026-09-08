package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("劳动合同PDF动态页码")
class OaPdfPageNumberServiceTest
{
    @TempDir
    Path tempDir;

    @Test
    @DisplayName("按实际PDF页数逐页写入居中中文页脚")
    void shouldStampEveryPageWithActualPageAndTotal() throws Exception
    {
        Path pdf = tempDir.resolve("labor-contract.pdf");
        try (PDDocument document = new PDDocument())
        {
            document.addPage(new PDPage(PDRectangle.A4));
            document.addPage(new PDPage(PDRectangle.A4));
            document.addPage(new PDPage(PDRectangle.A4));
            document.save(pdf.toFile());
        }
        String before = sha256(pdf);

        Path result = new OaPdfPageNumberService().stampDynamicPageNumbers(pdf);

        assertThat(result).isEqualTo(pdf.toAbsolutePath().normalize());
        assertThat(sha256(pdf)).isNotEqualTo(before);
        try (PDDocument stamped = Loader.loadPDF(pdf.toFile()))
        {
            assertThat(stamped.getNumberOfPages()).isEqualTo(3);
            PDFTextStripper stripper = new PDFTextStripper();
            for (int page = 1; page <= 3; page++)
            {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                // Some macOS CJK fonts map the simplified leaf glyph back to
                // its Unicode radical alias in ToUnicode.  Both code points
                // render as the same Chinese character; normalize only that
                // extraction alias before checking the semantic footer.
                assertThat(stripper.getText(stamped).replace("⻚", "页")
                        .replaceAll("\\s+", ""))
                        .contains("第" + page + "页共3页");
            }
        }
        try (var files = Files.list(tempDir))
        {
            assertThat(files.map(path -> path.getFileName().toString()))
                    .containsExactly("labor-contract.pdf");
        }
    }

    private String sha256(Path path) throws Exception
    {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(path)));
    }
}

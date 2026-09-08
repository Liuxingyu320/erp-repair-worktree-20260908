package com.erp.oa.service.invoice;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.erp.oa.config.OaReimbursementProperties;

@DisplayName("本地发票OCR引擎")
class OaLocalInvoiceRecognitionEngineTest
{
    @TempDir
    Path tempDir;

    @Test
    @DisplayName("无需联网即可提取OFD电子发票结构化文字")
    void shouldRecognizeTextBasedOfdLocally() throws Exception
    {
        Path ofd = tempDir.resolve("invoice.ofd");
        try (ZipOutputStream zip = new ZipOutputStream(
                Files.newOutputStream(ofd)))
        {
            zip.putNextEntry(new ZipEntry("OFD.xml"));
            zip.write("""
                    <?xml version="1.0" encoding="UTF-8"?>
                    <ofd:OFD xmlns:ofd="http://www.ofdspec.org/2016"/>
                    """.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("Doc_0/Pages/Page_0/Content.xml"));
            zip.write("""
                    <?xml version="1.0" encoding="UTF-8"?>
                    <ofd:Page xmlns:ofd="http://www.ofdspec.org/2016">
                      <ofd:TextCode>增值税电子普通发票</ofd:TextCode>
                      <ofd:TextCode>发票号码：11223344</ofd:TextCode>
                      <ofd:TextCode>开票日期：2026年07月30日</ofd:TextCode>
                      <ofd:TextCode>销售方 名称：本地测试有限公司 纳税人识别号：91310000999999999X</ofd:TextCode>
                      <ofd:TextCode>价税合计（小写）￥66.80</ofd:TextCode>
                    </ofd:Page>
                    """.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        OaReimbursementProperties properties =
                new OaReimbursementProperties();
        properties.setTempRoot(tempDir.resolve("temp").toString());
        OaLocalInvoiceRecognitionEngine engine =
                new OaLocalInvoiceRecognitionEngine(properties,
                        new OaInvoiceTextParser());

        OaInvoiceRecognitionResult result =
                engine.recognize(ofd, "ofd");

        assertThat(result.getStatus()).isEqualTo("succeeded");
        assertThat(result.getEngine()).isEqualTo("local");
        assertThat(result.getProvider()).isEqualTo("ofd-text");
        assertThat(result.getInvoiceNumber()).isEqualTo("11223344");
        assertThat(result.getSellerName())
                .isEqualTo("本地测试有限公司");
        assertThat(result.getTotalAmount())
                .isEqualByComparingTo("66.80");
    }
}

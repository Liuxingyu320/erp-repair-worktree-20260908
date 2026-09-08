package com.erp.oa.service.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.oa.config.OaReimbursementProperties;

@DisplayName("发票双引擎调度")
class OaInvoiceRecognitionCoordinatorTest
{
    @Test
    @DisplayName("自动模式在云端失败后切换本地且保留说明")
    void shouldFallbackFromCloudToLocal()
    {
        OaBaiduInvoiceRecognitionEngine cloud =
                mock(OaBaiduInvoiceRecognitionEngine.class);
        OaLocalInvoiceRecognitionEngine local =
                mock(OaLocalInvoiceRecognitionEngine.class);
        Path path = Path.of("/tmp/invoice.png");
        OaInvoiceRecognitionResult cloudFailure =
                OaInvoiceRecognitionResult.failure("cloud", "cloud",
                        "baidu", "failed", "云端超时");
        OaInvoiceRecognitionResult localSuccess =
                new OaInvoiceRecognitionResult();
        localSuccess.setStatus("succeeded");
        localSuccess.setEngine("local");
        localSuccess.setProvider("tesseract");
        localSuccess.setInvoiceNumber("12345678");
        localSuccess.setInvoiceDate(java.sql.Date.valueOf("2026-07-30"));
        localSuccess.setSellerName("本地商户");
        localSuccess.setTotalAmount(new BigDecimal("20.00"));
        localSuccess.setMessage("本地识别完成");
        when(cloud.available()).thenReturn(true);
        when(cloud.recognize(path, "png")).thenReturn(cloudFailure);
        when(local.recognize(path, "png")).thenReturn(localSuccess);

        OaInvoiceRecognitionCoordinator coordinator =
                new OaInvoiceRecognitionCoordinator(
                        new OaReimbursementProperties(), cloud, local);
        OaInvoiceRecognitionResult result =
                coordinator.recognize(path, "png", "auto");

        assertThat(result.getRequestedEngine()).isEqualTo("auto");
        assertThat(result.getEngine()).isEqualTo("local");
        assertThat(result.getStatus()).isEqualTo("succeeded");
        assertThat(result.getMessage())
                .contains("自动切换本地", "云端超时");
    }
}

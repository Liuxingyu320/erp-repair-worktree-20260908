package com.erp.oa.service.invoice;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("本地发票文本结构化")
class OaInvoiceTextParserTest
{
    private final OaInvoiceTextParser parser = new OaInvoiceTextParser();

    @Test
    @DisplayName("从中文增值税发票文本提取会计关键字段")
    void shouldExtractStructuredInvoiceFields()
    {
        String text = """
                增值税电子普通发票
                发票代码：031002600111
                发票号码：12345678
                开票日期：2026年07月30日
                购买方信息
                名称：测试购买有限公司 纳税人识别号：91310000123456789X
                销售方信息
                名称：上海出行有限公司 纳税人识别号：91310000987654321A
                项目名称
                *运输服务*客运服务
                合计 ￥94.34 ￥5.66
                价税合计（小写）￥100.00
                校验码：12345678901234567890
                """;

        OaInvoiceRecognitionResult result =
                parser.parse(text, "tesseract");

        assertThat(result.getStatus()).isEqualTo("succeeded");
        assertThat(result.getInvoiceType())
                .isEqualTo("增值税电子普通发票");
        assertThat(result.getInvoiceCode()).isEqualTo("031002600111");
        assertThat(result.getInvoiceNumber()).isEqualTo("12345678");
        assertThat(result.getInvoiceDate())
                .isEqualTo(Date.valueOf("2026-07-30"));
        assertThat(result.getPurchaserName())
                .isEqualTo("测试购买有限公司");
        assertThat(result.getSellerName())
                .isEqualTo("上海出行有限公司");
        assertThat(result.getAmountWithoutTax())
                .isEqualByComparingTo(new BigDecimal("94.34"));
        assertThat(result.getTaxAmount())
                .isEqualByComparingTo(new BigDecimal("5.66"));
        assertThat(result.getTotalAmount())
                .isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(result.getCommoditySummary())
                .contains("运输服务");
    }

    @Test
    @DisplayName("无关键字段时返回可人工修正的失败结果")
    void shouldReturnFailedResultForUnstructuredText()
    {
        OaInvoiceRecognitionResult result =
                parser.parse("这是一张模糊图片", "tesseract");

        assertThat(result.getStatus()).isEqualTo("failed");
        assertThat(result.hasRecognizedFields()).isFalse();
        assertThat(result.getMessage()).contains("人工修正");
    }
}

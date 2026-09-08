package com.erp.inventory.domain.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("V2调拨收货差异事实指纹")
class InvTransferReceiptDiscrepancyFactsTest
{
    private static final String PLAN = "a".repeat(64);

    @Test
    @DisplayName("等值十进制输入生成同一稳定指纹")
    void shouldNormalizeEquivalentDecimals()
    {
        String first = fingerprint("1", "10", "10", "外箱破损", "file-1");
        String second = fingerprint("1.0000", "10.000000", "10.000000",
                "外箱破损", "file-1");

        assertThat(first).matches("[a-f0-9]{64}").isEqualTo(second);
    }

    @Test
    @DisplayName("长度前缀避免说明和证据分隔符形成同串碰撞")
    void shouldKeepEveryFactBoundaryInTheFingerprint()
    {
        String first = fingerprint("1", "10", "10", "ab|c", "d");
        String second = fingerprint("1", "10", "10", "ab", "c|d");

        assertThat(first).isNotEqualTo(second);
    }

    private static String fingerprint(String quantity, String price,
            String amount, String note, String attachmentRefs)
    {
        return InvTransferReceiptDiscrepancyFacts.fingerprint(PLAN, 81L,
                "damaged", new BigDecimal(quantity), new BigDecimal(price),
                new BigDecimal(amount), note, attachmentRefs);
    }
}

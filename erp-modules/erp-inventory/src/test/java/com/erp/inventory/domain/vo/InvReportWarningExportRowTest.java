package com.erp.inventory.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.inventory.domain.InvStock;

@DisplayName("库存预警安全导出行")
class InvReportWarningExportRowTest
{
    @Test
    @DisplayName("导出行计算缺口和状态且结构不包含任何成本字段")
    void shouldMapOnlySafeWarningFields()
    {
        InvStock stock = new InvStock();
        stock.setProductCode("TEA-LJ-001");
        stock.setProductName("明前龙井");
        stock.setCategoryName("绿茶");
        stock.setSpec("250g");
        stock.setUnit("罐");
        stock.setWarehouseName("华东示范仓");
        stock.setCurrentQuantity(new BigDecimal("8"));
        stock.setAvailableQuantity(new BigDecimal("6.5"));
        stock.setSafetyStockMin(new BigDecimal("10"));
        stock.setCostPrice(new BigDecimal("299.99"));
        stock.setTotalCost(new BigDecimal("1949.94"));
        stock.setLastOutTime(new Date(1_786_262_400_000L));

        InvReportWarningExportRow row = InvReportWarningExportRow.from(stock);

        assertThat(row.getProductCode()).isEqualTo("TEA-LJ-001");
        assertThat(row.getWarningGap()).isEqualByComparingTo("3.5");
        assertThat(row.getWarningStatus()).isEqualTo("低库存");
        assertThat(row.getLastMovementTime()).isEqualTo(stock.getLastOutTime());
        assertThat(Arrays.stream(InvReportWarningExportRow.class.getDeclaredFields())
                .map(field -> field.getName().toLowerCase()))
                .noneMatch(name -> name.contains("cost") || name.contains("price"));
    }

    @Test
    @DisplayName("缺货和阈值未配置使用不同业务状态")
    void shouldDistinguishEmptyAndUnconfiguredWarnings()
    {
        InvStock empty = new InvStock();
        empty.setAvailableQuantity(BigDecimal.ZERO);
        empty.setSafetyStockMin(BigDecimal.TEN);
        InvStock unconfigured = new InvStock();
        unconfigured.setAvailableQuantity(BigDecimal.TEN);
        unconfigured.setSafetyStockMin(BigDecimal.ZERO);

        assertThat(InvReportWarningExportRow.from(empty).getWarningStatus()).isEqualTo("缺货");
        assertThat(InvReportWarningExportRow.from(unconfigured).getWarningStatus())
                .isEqualTo("阈值未配置");
        assertThat(InvReportWarningExportRow.from(unconfigured).getWarningGap()).isNull();
    }
}

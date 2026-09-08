package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("仓库维度库存查询回归")
class InvWarehouseAwareStockLookupTest
{
    @Test
    @DisplayName("调拨和发货通知服务不再使用店铺级库存查询")
    void shouldAvoidLegacyShopOnlyStockLookup() throws Exception
    {
        assertServiceUsesWarehouseAwareLookup("InvTransferServiceImpl.java");
        assertServiceUsesWarehouseAwareLookup("InvDeliveryNoticeServiceImpl.java");
    }

    private void assertServiceUsesWarehouseAwareLookup(String fileName) throws Exception
    {
        String source = Files.readString(Path.of(
                "src/main/java/com/erp/inventory/service/impl", fileName));

        assertThat(source)
                .doesNotContain("selectInvStockByProductAndShop(")
                .contains("selectInvStockByItemShopWarehouse");
    }
}

package com.erp.inventory.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("采购订单 Mapper 契约")
class InvPurchaseMapperContractTest
{
    private static final Path PURCHASE_ORDER_XML = Path.of(
            "src/main/resources/mapper/inventory/InvPurchaseOrderMapper.xml");
    private static final Path RECEIPT_BATCH_XML = Path.of(
            "src/main/resources/mapper/inventory/InvReceiptBatchMapper.xml");
    private static final Path ATTACHMENT_XML = Path.of(
            "src/main/resources/mapper/inventory/InvQualityInspectionAttachmentMapper.xml");

    @Test
    @DisplayName("采购列表与我的列表都支持精确仓库条件")
    void listQueriesShouldSupportExactWarehouse() throws Exception
    {
        String source = Files.readString(PURCHASE_ORDER_XML, StandardCharsets.UTF_8);

        assertThat(countOccurrences(source,
                "<if test=\"shopDeptId != null\">and o.shop_dept_id = #{shopDeptId}</if>"))
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("采购内容和状态更新使用分离且带预期状态的语句")
    void writesShouldSeparateContentAndStateTransitions() throws Exception
    {
        String source = Files.readString(PURCHASE_ORDER_XML, StandardCharsets.UTF_8);

        assertThat(source).contains(
                "<update id=\"updatePurchaseContent\"",
                "<update id=\"transitionPurchaseStatus\"",
                "and status = #{expectedStatus}");
    }

    @Test
    @DisplayName("收货批次和质检附件支持完整追溯读取")
    void receiptTraceShouldHaveReadMappings() throws Exception
    {
        assertThat(Files.readString(RECEIPT_BATCH_XML, StandardCharsets.UTF_8))
                .contains("<select id=\"selectByOrderId\"");
        assertThat(Files.readString(ATTACHMENT_XML, StandardCharsets.UTF_8))
                .contains("<select id=\"selectByInspectionId\"");
    }

    private static int countOccurrences(String source, String expected)
    {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(expected, offset)) >= 0)
        {
            count++;
            offset += expected.length();
        }
        return count;
    }
}

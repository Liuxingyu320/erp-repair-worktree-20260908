package com.erp.inventory.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("采购退货 Controller 安全边界")
class InvPurchaseReturnControllerTest
{
    private static final Path SOURCE = Path.of(
            "src/main/java/com/erp/inventory/controller/InvPurchaseReturnController.java");

    @Test
    @DisplayName("所有采购退货响应必须禁止缓存")
    void allResponsesShouldDisableCaching() throws Exception
    {
        String source = Files.readString(SOURCE, StandardCharsets.UTF_8);

        assertThat(source).contains("private void disableCaching(HttpServletResponse response)");
        assertThat(source).contains("response.setHeader(\"Cache-Control\", \"no-store, max-age=0\")");
        assertThat(source).contains("response.setHeader(\"Pragma\", \"no-cache\")");
        assertThat(countOccurrences(source, "disableCaching(response);")).isGreaterThanOrEqualTo(11);
    }

    @Test
    @DisplayName("来源采购单只复用采购退货新增权限")
    void sourceOrdersShouldUsePurchaseReturnAddPermission() throws Exception
    {
        String source = Files.readString(SOURCE, StandardCharsets.UTF_8);

        assertThat(source).contains(
                "@GetMapping(\"/source-orders\")",
                "@GetMapping(\"/source-orders/{orderId}\")");
        assertThat(source).containsSubsequence(
                "@RequiresPermissions(\"inv:purchaseReturn:add\")",
                "@GetMapping(\"/source-orders\")");
        assertThat(source).doesNotContain("inv:purchase:list", "inv:purchase:query");
    }

    @Test
    @DisplayName("保存并提交要求新增和提交权限且已存草稿单独提交")
    void submitBoundariesShouldSeparateEditingFromExistingDraftSubmission() throws Exception
    {
        String source = Files.readString(SOURCE, StandardCharsets.UTF_8);

        assertThat(source).contains(
                "@RequiresPermissions(value = { \"inv:purchaseReturn:add\", \"inv:purchaseReturn:submit\" })",
                "@PostMapping(\"/submit/{returnId}\")",
                "purchaseReturnService.submitSavedReturn(returnId, requireDraftVersion(request), ");
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

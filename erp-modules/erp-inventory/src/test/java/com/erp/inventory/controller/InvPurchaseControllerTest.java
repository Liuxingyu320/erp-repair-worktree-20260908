package com.erp.inventory.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("采购订单 Controller 安全边界")
class InvPurchaseControllerTest
{
    private static final Path SOURCE = Path.of(
            "src/main/java/com/erp/inventory/controller/InvPurchaseController.java");

    @Test
    @DisplayName("所有采购订单响应必须禁止缓存")
    void allResponsesShouldDisableCaching() throws Exception
    {
        String source = Files.readString(SOURCE, StandardCharsets.UTF_8);

        assertThat(source).contains("private void disableCaching(HttpServletResponse response)");
        assertThat(source).contains("response.setHeader(\"Cache-Control\", \"no-store, max-age=0\")");
        assertThat(source).contains("response.setHeader(\"Pragma\", \"no-cache\")");
        assertThat(countOccurrences(source, "disableCaching(response);")).isGreaterThanOrEqualTo(18);
    }

    @Test
    @DisplayName("保存并提交要求新增和提交权限且已存草稿单独提交")
    void submitBoundariesShouldSeparateEditingFromExistingDraftSubmission() throws Exception
    {
        String source = Files.readString(SOURCE, StandardCharsets.UTF_8);

        assertThat(source).contains(
                "@RequiresPermissions(value = { \"inv:purchase:add\", \"inv:purchase:submit\" })",
                "@PostMapping(\"/submit/{orderId}\")",
                "purchaseService.submitSavedPurchase(orderId, requireDraftVersion(request), ");
    }

    @Test
    @DisplayName("采购专用目录不要求额外主数据权限")
    void catalogsShouldUsePurchaseAddPermission() throws Exception
    {
        String source = Files.readString(SOURCE, StandardCharsets.UTF_8);

        assertThat(source).contains(
                "@GetMapping(\"/catalog/suppliers\")",
                "@GetMapping(\"/catalog/products\")",
                "@GetMapping(\"/catalog/oe\")",
                "@GetMapping(\"/catalog/gifts\")");
        assertThat(countOccurrences(source, "@RequiresPermissions(\"inv:purchase:add\")"))
                .isGreaterThanOrEqualTo(5);
    }

    @Test
    @DisplayName("详情权限读取完整轨迹而质检权限读取待检批次")
    void receiptBatchReadsShouldSeparateTraceAndQualityPermissions() throws Exception
    {
        String source = Files.readString(SOURCE, StandardCharsets.UTF_8);

        assertThat(source).containsSubsequence(
                "@RequiresPermissions(\"inv:purchase:query\")",
                "@GetMapping(\"/{orderId}/receipt-batches\")");
        assertThat(source).containsSubsequence(
                "@RequiresPermissions(\"inv:purchase:qc\")",
                "@GetMapping(\"/{orderId}/receipt-batches/pending\")");
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

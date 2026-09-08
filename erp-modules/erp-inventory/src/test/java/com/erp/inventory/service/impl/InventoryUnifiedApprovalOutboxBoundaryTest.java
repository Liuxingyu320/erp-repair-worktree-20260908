package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("库存统一审批发起 outbox 边界")
class InventoryUnifiedApprovalOutboxBoundaryTest
{
    @Test
    @DisplayName("统一审批适配器不保留可被误用的远端直连入口")
    void shouldExposeOnlyRequestBuildersForApprovalStart() throws Exception
    {
        String source = source("InventoryUnifiedApprovalService.java");

        assertThat(source).contains(
                "buildStockCheckStartRequest(",
                "buildTransferStartRequest(");
        assertThat(source).doesNotContain(
                "startStockCheck(",
                "startTransfer(",
                "approvalService.start(");
    }

    @Test
    @DisplayName("盘点和调拨提交都只构建快照并写入发件箱")
    void shouldRouteBusinessSubmissionThroughOutbox() throws Exception
    {
        String stockCheck = source("InvStockCheckServiceImpl.java");
        String transfer = source("InvTransferServiceImpl.java");

        assertThat(stockCheck).contains(
                ".buildStockCheckStartRequest(",
                "approvalStartOutboxService.enqueue(",
                "approvalStartAfterCommitTrigger.trigger(");
        assertThat(transfer).contains(
                ".buildTransferStartRequest(",
                "approvalStartOutboxService.enqueue(",
                "approvalStartAfterCommitTrigger.trigger(");
        assertThat(stockCheck).doesNotContain("remoteApprovalService.start(");
        assertThat(transfer).doesNotContain("remoteApprovalService.start(");
    }

    private static String source(String fileName) throws Exception
    {
        Path path = Path.of(System.getProperty("user.dir"), "src", "main",
                "java", "com", "erp", "inventory", "service", "impl",
                fileName);
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}

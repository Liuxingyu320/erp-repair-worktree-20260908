package com.erp.inventory.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import com.erp.common.security.annotation.Logical;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.inventory.annotation.PersistentCommand;
import com.erp.inventory.domain.dto.InvTransferDiscrepancyResolveRequest;
import com.erp.inventory.domain.dto.InvTransferSourceConfirmRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@DisplayName("调拨审批轨迹接口声明")
class InvTransferControllerSourceTest
{
    @Test
    @DisplayName("调出店确认接口使用发货权限并返回逐行确认结果")
    void sourceConfirmationShouldUseSourceStoreDeliveryPermission()
            throws Exception
    {
        Method method = InvTransferController.class.getMethod(
                "confirmSource", Long.class,
                InvTransferSourceConfirmRequest.class,
                HttpServletRequest.class);

        PostMapping mapping = method.getAnnotation(PostMapping.class);
        RequiresPermissions permissions =
                method.getAnnotation(RequiresPermissions.class);

        assertThat(mapping).isNotNull();
        assertThat(mapping.value())
                .containsExactly("/source-confirm/{transferId}");
        assertThat(permissions).isNotNull();
        assertThat(permissions.value())
                .containsExactly("inv:transfer:deliver");
    }

    @Test
    @DisplayName("草稿删除使用独立接口并调用物理删除服务")
    void draftDeleteShouldUseDedicatedEndpoint() throws Exception
    {
        Method method = InvTransferController.class.getMethod(
                "deleteDraft", Long.class, String.class,
                HttpServletRequest.class);

        DeleteMapping mapping = method.getAnnotation(DeleteMapping.class);
        RequiresPermissions permissions = method.getAnnotation(RequiresPermissions.class);

        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/delete/{transferId}");
        assertThat(permissions).isNotNull();
        assertThat(permissions.value()).containsExactly("inv:transfer:remove");
        String source = Files.readString(Path.of(
                "src/main/java/com/erp/inventory/controller/InvTransferController.java"),
                StandardCharsets.UTF_8);
        assertThat(source).contains(
                "transferCommandService.deleteDraft(requestId, transferId,");
    }

    @Test
    @DisplayName("审批轨迹复用调拨详情访问权限")
    void approvalTrackShouldReuseTransferReadPermissions() throws Exception
    {
        Method method = InvTransferController.class.getMethod(
                "approvalTrack", Long.class, HttpServletRequest.class, HttpServletResponse.class);

        GetMapping mapping = method.getAnnotation(GetMapping.class);
        RequiresPermissions permissions = method.getAnnotation(RequiresPermissions.class);
        RequiresPermissions detailPermissions = InvTransferController.class.getMethod(
                "detail", Long.class, HttpServletRequest.class,
                HttpServletResponse.class)
                .getAnnotation(RequiresPermissions.class);

        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/{transferId}/approval-track");
        assertThat(permissions).isNotNull();
        assertThat(detailPermissions.value()).containsExactly(
                "inv:transfer:query", "inv:transfer:records:query",
                "inv:transfer:approve", "inv:transfer:add",
                "inv:transfer:edit", "inv:transfer:submit",
                "inv:transfer:deliver", "inv:transfer:receive");
        assertThat(permissions.value()).containsExactly(detailPermissions.value());
        assertThat(permissions.logical()).isEqualTo(Logical.OR);
        String source = Files.readString(Path.of(
                "src/main/java/com/erp/inventory/controller/InvTransferController.java"),
                StandardCharsets.UTF_8);
        assertThat(source).contains("response.setHeader(\"Cache-Control\", \"no-store\")");
    }

    @Test
    @DisplayName("差异处置由持久化命令根据 requestId 处理重放")
    void discrepancyResolutionShouldUsePersistentCommandBoundary()
            throws Exception
    {
        Method method = InvTransferController.class.getMethod(
                "resolveDiscrepancy", Long.class,
                InvTransferDiscrepancyResolveRequest.class,
                String.class,
                HttpServletRequest.class);

        assertThat(method.getAnnotation(PersistentCommand.class)).isNotNull();
        RequestHeader requestHeader = method.getParameters()[2]
                .getAnnotation(RequestHeader.class);
        assertThat(requestHeader).isNotNull();
        assertThat(requestHeader.name()).isEqualTo("X-Request-Id");
    }
}

package com.erp.inventory.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import com.erp.common.security.annotation.IdempotentSubmit;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.inventory.annotation.PersistentCommand;
import com.erp.inventory.domain.dto.InvTransferShipmentCreateRequest;
import com.erp.inventory.domain.dto.InvTransferShipmentReceiptCreateRequest;

@DisplayName("调拨持久化命令边界")
class InvTransferPersistentCommandBoundaryTest
{
    private static final Set<String> COMMAND_METHODS = Set.of(
            "save", "submit", "approve", "deliver", "receive",
            "createShipmentV2", "createReceiptV2", "receiveShipment",
            "resolveDiscrepancy", "deleteDraft",
            "cancel", "withdraw");

    @Test
    @DisplayName("所有调拨写入口强制携带请求标识并使用持久化命令")
    void shouldRequirePersistentCommandForEveryMutation()
    {
        Set<Method> methods = Arrays.stream(
                InvTransferController.class.getDeclaredMethods())
                .filter(method -> COMMAND_METHODS.contains(method.getName()))
                .collect(Collectors.toSet());

        assertThat(methods).hasSize(COMMAND_METHODS.size());
        for (Method method : methods)
        {
            assertThat(method.getAnnotation(PersistentCommand.class))
                    .as(method.getName()).isNotNull();
            assertThat(method.getAnnotation(IdempotentSubmit.class))
                    .as(method.getName() + " must not be rejected by Redis before replay")
                    .isNull();
            assertThat(requestIdHeader(method)).as(method.getName())
                    .isNotNull()
                    .extracting(RequestHeader::name)
                    .isEqualTo("X-Request-Id");
        }
    }

    @Test
    @DisplayName("调拨控制器写入口只委托事务命令门面")
    void shouldDelegateMutationsToCommandFacade() throws Exception
    {
        String source = Files.readString(Path.of(
                "src/main/java/com/erp/inventory/controller/InvTransferController.java"),
                StandardCharsets.UTF_8);

        assertThat(source).contains(
                "transferCommandService.saveDraft(",
                "transferCommandService.submit(",
                "transferCommandService.approve(",
                "transferCommandService.deliver(",
                "transferCommandService.createShipmentV2(",
                "transferCommandService.createReceiptV2(",
                "transferCommandService.receive(",
                "transferCommandService.receiveShipment(",
                "transferCommandService.resolveDiscrepancy(",
                "transferCommandService.deleteDraft(",
                "transferCommandService.cancel(",
                "transferCommandService.withdraw(");
        assertThat(source).doesNotContain(
                "transferService.saveDraft(",
                "transferService.submitTransfer(",
                "transferService.deliverTransfer(",
                "transferService.receiveTransfer(",
                "transferService.receiveTransferShipment(",
                "transferService.resolveTransferDiscrepancy(",
                "transferService.deleteTransfer(",
                "transferService.cancelTransfer(",
                "transferService.withdrawApproval(");
    }

    @Test
    @DisplayName("V2发货写入口使用独立资源路径与发货权限")
    void shouldExposeGuardedV2ShipmentResource() throws Exception
    {
        Method method = InvTransferController.class.getDeclaredMethod(
                "createShipmentV2", Long.class,
                InvTransferShipmentCreateRequest.class, String.class,
                HttpServletRequest.class);

        assertThat(method.getAnnotation(PostMapping.class).value())
                .containsExactly("/{transferId}/shipments");
        assertThat(method.getAnnotation(RequiresPermissions.class).value())
                .containsExactly("inv:transfer:deliver");
        assertThat(method.getAnnotation(PersistentCommand.class)).isNotNull();
        assertThat(requestIdHeader(method)).isNotNull();
    }

    @Test
    @DisplayName("V2收货写入口使用独立资源路径与收货权限")
    void shouldExposeGuardedV2ReceiptResource() throws Exception
    {
        Method method = InvTransferController.class.getDeclaredMethod(
                "createReceiptV2", Long.class,
                InvTransferShipmentReceiptCreateRequest.class,
                String.class, HttpServletRequest.class);

        assertThat(method.getAnnotation(PostMapping.class).value())
                .containsExactly("/shipments/{shipmentId}/receipts");
        assertThat(method.getAnnotation(RequiresPermissions.class).value())
                .containsExactly("inv:transfer:receive");
        assertThat(method.getAnnotation(PersistentCommand.class)).isNotNull();
        assertThat(requestIdHeader(method)).isNotNull();
    }

    private static RequestHeader requestIdHeader(Method method)
    {
        return Arrays.stream(method.getParameters())
                .map(Parameter::getAnnotations)
                .flatMap(Arrays::stream)
                .filter(RequestHeader.class::isInstance)
                .map(RequestHeader.class::cast)
                .filter(header -> "X-Request-Id".equals(header.name()))
                .findFirst()
                .orElse(null);
    }
}

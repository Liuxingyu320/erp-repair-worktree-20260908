package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

@DisplayName("调拨事务命令门面")
class InvTransferCommandServiceContractTest
{
    private static final Set<String> COMMAND_METHODS = Set.of(
            "saveDraft", "submit", "approve", "deliver", "receive",
            "createShipmentV2", "createReceiptV2", "receiveShipment",
            "resolveDiscrepancy", "deleteDraft",
            "cancel", "withdraw");

    @Test
    @DisplayName("每个命令均建立覆盖台账与业务变更的回滚事务")
    void shouldWrapEveryCommandInRollbackTransaction()
    {
        Method[] methods = Arrays.stream(
                InvTransferCommandService.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> COMMAND_METHODS.contains(method.getName()))
                .toArray(Method[]::new);

        assertThat(methods).hasSize(COMMAND_METHODS.size());
        for (Method method : methods)
        {
            Transactional transactional = method.getAnnotation(
                    Transactional.class);
            assertThat(transactional).as(method.getName()).isNotNull();
            assertThat(transactional.rollbackFor()).as(method.getName())
                    .contains(Exception.class);
        }
    }

    @Test
    @DisplayName("所有命令统一通过持久化执行器而非直接旁路")
    void shouldRouteEveryCommandThroughExecutor() throws Exception
    {
        String source = Files.readString(Path.of(
                "src/main/java/com/erp/inventory/service/impl/InvTransferCommandService.java"),
                StandardCharsets.UTF_8);

        assertThat(occurrences(source, "commandExecutor.execute("))
                .isEqualTo(COMMAND_METHODS.size());
    }

    private static int occurrences(String source, String needle)
    {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(needle, offset)) >= 0)
        {
            count++;
            offset += needle.length();
        }
        return count;
    }
}

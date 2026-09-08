package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionCommand;

@DisplayName("V2调拨差异裁决动作执行硬关闭入口骨架")
class InvTransferReceiptDiscrepancyAdjudicationExecutionSafetySkeletonTest
{
    private static final String SERVICE_NAME =
            "InvTransferReceiptDiscrepancyAdjudicationExecutionService";

    @Test
    @DisplayName("内部命令只接收并发锚点且不信任效果和操作者事实")
    void shouldKeepCommandEnvelopeNarrow()
    {
        assertThat(Arrays.stream(
                InvTransferReceiptDiscrepancyAdjudicationExecutionCommand
                        .class.getRecordComponents())
                .map(component -> component.getName()))
                .containsExactly("requestId", "caseId", "caseVersion",
                        "adjudicationId", "actionId", "executionVersion",
                        "command", "effectReference")
                .doesNotContain("effectKind", "quantity", "amount",
                        "responsibleParty", "executorUserId",
                        "permissions");
    }

    @Test
    @DisplayName("写开关默认关闭并先于效果边界拒绝")
    void shouldRejectWhenWriteIsDisabled()
    {
        var gate =
                new InvTransferReceiptDiscrepancyAdjudicationExecutionWriteGate(
                        false, false);

        assertThatThrownBy(gate::requireEnabled)
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("写事务尚未启用");
    }

    @Test
    @DisplayName("只有写开关不能越过物理效果就绪条件")
    void shouldRejectWhenEffectBoundaryIsNotReady()
    {
        var gate =
                new InvTransferReceiptDiscrepancyAdjudicationExecutionWriteGate(
                        true, false);

        assertThatThrownBy(gate::requireEnabled)
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("物理效果边界尚未就绪");
    }

    @Test
    @DisplayName("两个外部条件即使误开启仍由服务最终硬停止")
    void shouldRemainInertWhenBothExternalConditionsAreTrue()
    {
        var gate =
                new InvTransferReceiptDiscrepancyAdjudicationExecutionWriteGate(
                        true, true);
        var service =
                new InvTransferReceiptDiscrepancyAdjudicationExecutionService(
                        gate);

        assertThatThrownBy(() -> service.execute(command()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("持久化与物理效果边界尚未实现");
    }

    @Test
    @DisplayName("入口无Mapper和事务且开关默认关闭且没有生产调用点")
    void shouldHaveNoPersistenceOrRuntimeWiring() throws Exception
    {
        var fields =
                InvTransferReceiptDiscrepancyAdjudicationExecutionService
                        .class.getDeclaredFields();
        assertThat(fields).hasSize(1);
        assertThat(fields[0].getType()).isEqualTo(
                InvTransferReceiptDiscrepancyAdjudicationExecutionWriteGate
                        .class);
        Method execute =
                InvTransferReceiptDiscrepancyAdjudicationExecutionService
                        .class.getDeclaredMethod("execute",
                                InvTransferReceiptDiscrepancyAdjudicationExecutionCommand
                                        .class);
        assertThat(execute.getAnnotation(Transactional.class)).isNull();
        assertThat(
                InvTransferReceiptDiscrepancyAdjudicationExecutionService
                        .class.getAnnotation(Transactional.class)).isNull();
        var gateConstructor =
                InvTransferReceiptDiscrepancyAdjudicationExecutionWriteGate
                        .class.getDeclaredConstructor(boolean.class,
                                boolean.class);
        assertThat(Arrays.stream(gateConstructor.getParameters())
                .map(parameter -> parameter.getAnnotation(Value.class))
                .map(Value::value))
                .containsExactly(
                        "${erp.inventory.transfer-discrepancy-execution-v2.write-enabled:false}",
                        "${erp.inventory.transfer-discrepancy-execution-v2."
                                + "effect-boundary-ready:false}");
        assertThat(sourceReferences()).containsExactly(
                Path.of("service", "impl", SERVICE_NAME + ".java"));
        assertThat(configurationReferences()).isEmpty();
    }

    private static List<Path> sourceReferences() throws IOException
    {
        Path root = Path.of(System.getProperty("user.dir"), "src", "main",
                "java", "com", "erp", "inventory").normalize();
        try (var paths = Files.walk(root))
        {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> contains(path, SERVICE_NAME))
                    .map(path -> root.relativize(path))
                    .sorted()
                    .toList();
        }
    }

    private static List<Path> configurationReferences() throws IOException
    {
        Path root = Path.of(System.getProperty("user.dir"), "src", "main",
                "resources").normalize();
        try (var paths = Files.walk(root))
        {
            return paths.filter(Files::isRegularFile)
                    .filter(
                            InvTransferReceiptDiscrepancyAdjudicationExecutionSafetySkeletonTest
                                    ::isConfigurationFile)
                    .filter(path -> contains(path,
                            "transfer-discrepancy-execution-v2"))
                    .map(path -> root.relativize(path))
                    .sorted()
                    .toList();
        }
    }

    private static boolean isConfigurationFile(Path path)
    {
        String name = path.getFileName().toString();
        return name.endsWith(".yml") || name.endsWith(".yaml")
                || name.endsWith(".properties")
                || name.endsWith(".xml");
    }

    private static boolean contains(Path path, String value)
    {
        try
        {
            return Files.readString(path, StandardCharsets.UTF_8)
                    .contains(value);
        }
        catch (IOException error)
        {
            throw new IllegalStateException(error);
        }
    }

    private static InvTransferReceiptDiscrepancyAdjudicationExecutionCommand
            command()
    {
        return new InvTransferReceiptDiscrepancyAdjudicationExecutionCommand(
                "execute-action-0001", 700L, 4L, 800L, 900L, 0L,
                "dispatch", null);
    }
}

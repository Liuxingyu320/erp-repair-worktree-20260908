package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationAtomicLedgerPolicy.Prepared;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy.ActionSnapshot;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy.Actor;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy.Boundary;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy.PreparedExecution;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy.Request;
import com.erp.inventory.mapper.InvTransferReceiptDiscrepancyAdjudicationAtomicLedgerMapper;

@DisplayName("V2调拨差异责任与短缺损失原子效果所有者")
class
        InvTransferReceiptDiscrepancyAdjudicationAtomicLedgerEffectOwnerTest
{
    private static final String OWNER =
            "InvTransferReceiptDiscrepancyAdjudicationAtomicLedgerEffectOwner";

    @Test
    @DisplayName("责任调整只追加一条责任台账")
    void shouldAppendExactlyOneResponsibilityLedger()
    {
        Fixture fixture = fixture();
        when(fixture.mapper().insertResponsibilityLedger(any()))
                .thenReturn(1);

        fixture.owner().apply(execution("shortage",
                "responsibility_adjustment"));

        verify(fixture.mapper()).insertResponsibilityLedger(any(Prepared.class));
        verify(fixture.mapper(), never()).insertShortageLossLedger(any());
    }

    @Test
    @DisplayName("短缺运输损耗只追加一条损失台账")
    void shouldAppendExactlyOneShortageLossLedger()
    {
        Fixture fixture = fixture();
        when(fixture.mapper().insertShortageLossLedger(any()))
                .thenReturn(1);

        fixture.owner().apply(execution("shortage",
                "transport_loss_write_off"));

        verify(fixture.mapper()).insertShortageLossLedger(any(Prepared.class));
        verify(fixture.mapper(), never()).insertResponsibilityLedger(any());
    }

    @Test
    @DisplayName("条件插入零行或多行全部失败关闭")
    void shouldRejectAnyNonSingletonWrite()
    {
        Fixture missing = fixture();
        assertThatThrownBy(() -> missing.owner().apply(execution(
                "shortage", "responsibility_adjustment")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("写入冲突");

        Fixture duplicate = fixture();
        when(duplicate.mapper().insertResponsibilityLedger(any()))
                .thenReturn(2);
        assertThatThrownBy(() -> duplicate.owner().apply(execution(
                "damaged", "responsibility_adjustment")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("写入冲突");
    }

    @Test
    @DisplayName("受损库存效果在首个Mapper调用前拒绝")
    void shouldRejectEffectsOwnedByInventoryBoundary()
    {
        Fixture fixture = fixture();

        assertThatThrownBy(() -> fixture.owner().apply(execution(
                "damaged", "damage_write_off")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不属于当前原子台账边界");
        verifyNoInteractions(fixture.mapper());
    }

    @Test
    @DisplayName("效果所有者必须加入既有回滚事务且只被未接线事务调用")
    void shouldRequireExistingTransactionAndOnlyServeTransaction()
            throws Exception
    {
        Method apply =
                InvTransferReceiptDiscrepancyAdjudicationAtomicLedgerEffectOwner
                        .class.getMethod("apply", PreparedExecution.class);
        Transactional transaction = apply.getAnnotation(Transactional.class);

        assertThat(transaction).isNotNull();
        assertThat(transaction.propagation()).isEqualTo(
                Propagation.MANDATORY);
        assertThat(Arrays.asList(transaction.rollbackFor()))
                .contains(Exception.class);
        assertThat(productionReferences()).containsExactly(
                Path.of("service", "impl", OWNER + ".java"),
                Path.of("service", "impl",
                        "InvTransferReceiptDiscrepancyAdjudicationExecutionTransaction.java"));
    }

    private static Fixture fixture()
    {
        var mapper = mock(
                InvTransferReceiptDiscrepancyAdjudicationAtomicLedgerMapper
                        .class);
        return new Fixture(
                new InvTransferReceiptDiscrepancyAdjudicationAtomicLedgerEffectOwner(
                        mapper),
                mapper);
    }

    private static PreparedExecution execution(String discrepancyType,
            String actionType)
    {
        boolean damagedResponsibility = "damaged".equals(discrepancyType)
                && "responsibility_adjustment".equals(actionType);
        ActionSnapshot action = new ActionSnapshot(900L,
                damagedResponsibility ? 2 : 1, actionType,
                damagedResponsibility ? "responsibility" : "resolution",
                new BigDecimal("1.0000"),
                new BigDecimal("10.000000"), "company", "pending", 0L,
                null);
        List<ActionSnapshot> actions = damagedResponsibility
                ? List.of(new ActionSnapshot(901L, 1,
                        "damage_write_off", "resolution",
                        new BigDecimal("1.0000"),
                        new BigDecimal("10.000000"), "company",
                        "pending", 0L, null), action)
                : List.of(action);
        Boundary boundary = new Boundary(700L, 4L, 4L,
                "adjudication_planned", 800L, "a".repeat(64),
                discrepancyType, new BigDecimal("1.0000"),
                new BigDecimal("10.000000"),
                new BigDecimal("10.000000"),
                "adjudication_planned", actions);
        Request request = new Request("execute-action-0001", 700L, 4L,
                800L, 900L, 0L, "dispatch", null);
        Actor actor = new Actor(199L, "execution-user", Set.of(
                InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                        .REQUIRED_PERMISSION));
        return InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                .prepare(request, boundary, actor,
                        Instant.parse("2026-08-03T03:00:00Z"));
    }

    private static List<Path> productionReferences() throws Exception
    {
        Path root = Path.of(System.getProperty("user.dir"), "src", "main",
                "java", "com", "erp", "inventory").normalize();
        try (var paths = Files.walk(root))
        {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> contains(path, OWNER))
                    .map(root::relativize)
                    .sorted()
                    .toList();
        }
    }

    private static boolean contains(Path path, String value)
    {
        try
        {
            return Files.readString(path, StandardCharsets.UTF_8)
                    .contains(value);
        }
        catch (java.io.IOException error)
        {
            throw new IllegalStateException(error);
        }
    }

    private record Fixture(
            InvTransferReceiptDiscrepancyAdjudicationAtomicLedgerEffectOwner
                    owner,
            InvTransferReceiptDiscrepancyAdjudicationAtomicLedgerMapper
                    mapper)
    {
    }
}

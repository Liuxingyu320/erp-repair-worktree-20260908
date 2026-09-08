package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationBasisPolicy;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationBasisPolicy.IssuedBasis;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationCommand;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationPolicy;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationPolicy.ActionInput;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationPolicy.Actor;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationPolicy.PreparedAdjudication;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationPolicy.Request;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyBoundaryPolicy;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyFacts;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReadFact;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyStoredAdjudication;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyStoredAdjudicationAction;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyStoredAdjudicationBasis;
import com.erp.inventory.domain.transfer.InvTransferReceiptGeneratedId;
import com.erp.inventory.domain.vo.InvTransferReceiptDiscrepancyAdjudicationCreationVo;
import com.erp.inventory.mapper.InvTransferReceiptDiscrepancyAdjudicationBasisMapper;
import com.erp.inventory.mapper.InvTransferReceiptDiscrepancyAdjudicationMapper;
import com.erp.system.api.model.LoginUser;

@DisplayName("V2调拨差异裁决依据原子消费唯一写事务")
class InvTransferReceiptDiscrepancyAdjudicationCreationServiceTest
{
    private static final String PLAN = "a".repeat(64);
    private static final Instant NOW = Instant.parse(
            "2026-08-03T12:00:00Z");
    private static final String PERMISSION =
            InvTransferReceiptDiscrepancyAdjudicationPolicy
                    .REQUIRED_PERMISSION;
    private static final List<Long> SCOPE = List.of(301L, 302L);

    @Test
    @DisplayName("按组织、事项锁、请求锁、依据锁、写计划、推进和消费固定顺序")
    void shouldPersistAndConsumeBasisInFixedOrder()
    {
        Fixture fixture = fixture();
        when(fixture.mapper().selectCaseForUpdate(700L, SCOPE))
                .thenReturn(fact());
        generated(fixture.mapper(), 901L);
        when(fixture.mapper().insertActions(anyLong(), any()))
                .thenReturn(1);
        when(fixture.mapper().transitionCaseToPlanned(any()))
                .thenReturn(1);

        InvTransferReceiptDiscrepancyAdjudicationCreationVo result =
                call(fixture, command(), Set.of(PERMISSION), 301L);

        assertThat(result.adjudicationId()).isEqualTo("901");
        assertThat(result.discrepancyCaseId()).isEqualTo("700");
        assertThat(result.planStatus()).isEqualTo("adjudication_planned");
        assertThat(result.actionCount()).isEqualTo(1);
        assertThat(result.replayed()).isFalse();
        assertThat(result.dataSource()).isEqualTo("server");
        String tokenHash = tokenHash();
        assertThat(tokenHash).isNotEqualTo(token());
        InOrder order = inOrder(fixture.scope(), fixture.mapper(),
                fixture.basisMapper());
        order.verify(fixture.scope()).resolveScopeDeptIds(301L);
        order.verify(fixture.mapper()).selectCaseForUpdate(700L, SCOPE);
        order.verify(fixture.mapper())
                .selectByRequestIdForUpdate("adjudicate-0001");
        order.verify(fixture.basisMapper())
                .selectByTokenHashForUpdate(tokenHash);
        order.verify(fixture.mapper()).insertAdjudication(any(), any());
        order.verify(fixture.mapper()).insertActions(anyLong(), any());
        order.verify(fixture.mapper()).transitionCaseToPlanned(any());
        order.verify(fixture.basisMapper()).consumeIssuedBasis(any());
        verify(fixture.mapper(), never())
                .selectActionsByAdjudicationId(anyLong());
    }

    @Test
    @DisplayName("完全一致的已消费令牌在过期后仍只读重放原计划")
    void shouldReplayExactConsumedBasisAfterExpiry()
    {
        Fixture fixture = fixture(NOW.plusSeconds(600));
        ReplayFixture replay = replayFixture();
        stubReplay(fixture, replay);

        InvTransferReceiptDiscrepancyAdjudicationCreationVo result =
                call(fixture, command(), Set.of(PERMISSION), 301L);

        assertThat(result.adjudicationId()).isEqualTo("901");
        assertThat(result.replayed()).isTrue();
        assertThat(result.planCreatedAt()).isEqualTo(
                "2026-08-03T11:59:00Z");
        InOrder order = inOrder(fixture.mapper(), fixture.basisMapper());
        order.verify(fixture.mapper()).selectCaseForUpdate(700L, SCOPE);
        order.verify(fixture.mapper())
                .selectByRequestIdForUpdate("adjudicate-0001");
        order.verify(fixture.basisMapper())
                .selectByTokenHashForUpdate(tokenHash());
        order.verify(fixture.mapper()).selectActionsByAdjudicationId(901L);
        verify(fixture.mapper(), never()).insertAdjudication(any(), any());
        verify(fixture.mapper(), never()).insertActions(anyLong(), any());
        verify(fixture.mapper(), never()).transitionCaseToPlanned(any());
        verify(fixture.basisMapper(), never()).consumeIssuedBasis(any());
    }

    @Test
    @DisplayName("重放动作或决定指纹漂移时失败关闭")
    void shouldRejectTamperedReplaySnapshot()
    {
        Fixture amountFixture = fixture();
        ReplayFixture amountReplay = replayFixture();
        amountReplay.action().setAmount(new BigDecimal("9.000000"));
        stubReplay(amountFixture, amountReplay);
        assertThatThrownBy(() -> call(amountFixture, command(),
                Set.of(PERMISSION), 301L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("动作不可核验");

        Fixture fingerprintFixture = fixture();
        ReplayFixture fingerprintReplay = replayFixture();
        fingerprintReplay.stored().setDecisionFingerprint("b".repeat(64));
        stubReplay(fingerprintFixture, fingerprintReplay);
        assertThatThrownBy(() -> call(fingerprintFixture, command(),
                Set.of(PERMISSION), 301L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("决定指纹不可核验");
    }

    @Test
    @DisplayName("没有专属权限时在解析组织和获取数据库锁前拒绝")
    void shouldRejectMissingPermissionBeforeScopeOrDatabaseAccess()
    {
        Fixture fixture = fixture();

        assertThatThrownBy(() -> call(fixture, command(), Set.of(), 301L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("请求或权限无效");

        verifyNoInteractions(fixture.scope(), fixture.mapper(),
                fixture.basisMapper());
    }

    @Test
    @DisplayName("未消费令牌过期时在任何计划DML前拒绝")
    void shouldRejectExpiredIssuedBasisBeforeDml()
    {
        Fixture fixture = fixture(NOW.plusSeconds(300));
        when(fixture.mapper().selectCaseForUpdate(700L, SCOPE))
                .thenReturn(fact());

        assertThatThrownBy(() -> call(fixture, command(),
                Set.of(PERMISSION), 301L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("令牌无效或已过期");

        verify(fixture.mapper(), never()).insertAdjudication(any(), any());
        verify(fixture.basisMapper(), never()).consumeIssuedBasis(any());
    }

    @Test
    @DisplayName("已消费令牌不能用于新请求")
    void shouldRejectConsumedBasisForNewRequest()
    {
        Fixture fixture = fixture();
        when(fixture.mapper().selectCaseForUpdate(700L, SCOPE))
                .thenReturn(fact());
        when(fixture.basisMapper().selectByTokenHashForUpdate(tokenHash()))
                .thenReturn(consumedBasis());
        InvTransferReceiptDiscrepancyAdjudicationCommand different =
                new InvTransferReceiptDiscrepancyAdjudicationCommand(
                        "adjudicate-0002", token(), 700L, 3L,
                        "独立裁决", "evidence-1", actions());

        assertThatThrownBy(() -> call(fixture, different,
                Set.of(PERMISSION), 301L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("令牌无效或已过期");

        verify(fixture.mapper(), never()).insertAdjudication(any(), any());
        verify(fixture.basisMapper(), never()).consumeIssuedBasis(any());
    }

    @Test
    @DisplayName("重放时组织范围变化使依据绑定失败关闭")
    void shouldRejectReplayWhenScopeChanges()
    {
        Fixture fixture = fixture();
        ReplayFixture replay = replayFixture();
        stubReplay(fixture, replay);
        when(fixture.scope().resolveScopeDeptIds(301L))
                .thenReturn(List.of(301L, 303L));
        when(fixture.mapper().selectCaseForUpdate(700L,
                List.of(301L, 303L))).thenReturn(replay.fact());

        assertThatThrownBy(() -> call(fixture, command(),
                Set.of(PERMISSION), 301L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("令牌无效或已过期");

        verify(fixture.mapper(), never())
                .selectActionsByAdjudicationId(anyLong());
    }

    @Test
    @DisplayName("计划头零行时停止且不写动作、状态或消费依据")
    void shouldStopWhenConditionalHeaderInsertMisses()
    {
        Fixture fixture = fixture();
        when(fixture.mapper().selectCaseForUpdate(700L, SCOPE))
                .thenReturn(fact());

        assertThatThrownBy(() -> call(fixture, command(),
                Set.of(PERMISSION), 301L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("计划写入冲突");

        verify(fixture.mapper(), never()).insertActions(anyLong(), any());
        verify(fixture.mapper(), never()).transitionCaseToPlanned(any());
        verify(fixture.basisMapper(), never()).consumeIssuedBasis(any());
    }

    @Test
    @DisplayName("计划头生成键缺失时停止后续DML")
    void shouldStopWhenGeneratedKeyIsMissing()
    {
        Fixture fixture = fixture();
        when(fixture.mapper().selectCaseForUpdate(700L, SCOPE))
                .thenReturn(fact());
        when(fixture.mapper().insertAdjudication(any(), any()))
                .thenReturn(1);

        assertThatThrownBy(() -> call(fixture, command(),
                Set.of(PERMISSION), 301L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("主键生成失败");

        verify(fixture.mapper(), never()).insertActions(anyLong(), any());
        verify(fixture.mapper(), never()).transitionCaseToPlanned(any());
        verify(fixture.basisMapper(), never()).consumeIssuedBasis(any());
    }

    @Test
    @DisplayName("动作写入不完整时不推进事项或消费依据")
    void shouldStopWhenActionRowsAreIncomplete()
    {
        Fixture fixture = fixture();
        when(fixture.mapper().selectCaseForUpdate(700L, SCOPE))
                .thenReturn(fact());
        generated(fixture.mapper(), 901L);

        assertThatThrownBy(() -> call(fixture, command(),
                Set.of(PERMISSION), 301L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("动作写入不完整");

        verify(fixture.mapper(), never()).transitionCaseToPlanned(any());
        verify(fixture.basisMapper(), never()).consumeIssuedBasis(any());
    }

    @Test
    @DisplayName("事项推进零行时不消费依据")
    void shouldFailWhenConditionalTransitionMisses()
    {
        Fixture fixture = fixture();
        when(fixture.mapper().selectCaseForUpdate(700L, SCOPE))
                .thenReturn(fact());
        generated(fixture.mapper(), 901L);
        when(fixture.mapper().insertActions(anyLong(), any()))
                .thenReturn(1);

        assertThatThrownBy(() -> call(fixture, command(),
                Set.of(PERMISSION), 301L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("状态推进冲突");

        verify(fixture.basisMapper(), never()).consumeIssuedBasis(any());
    }

    @Test
    @DisplayName("事项推进后依据条件消费零行使整个本地事务失败")
    void shouldRollbackWhenBasisConsumptionMisses()
    {
        Fixture fixture = fixture();
        when(fixture.mapper().selectCaseForUpdate(700L, SCOPE))
                .thenReturn(fact());
        generated(fixture.mapper(), 901L);
        when(fixture.mapper().insertActions(anyLong(), any()))
                .thenReturn(1);
        when(fixture.mapper().transitionCaseToPlanned(any()))
                .thenReturn(1);
        when(fixture.basisMapper().consumeIssuedBasis(any()))
                .thenReturn(0);

        assertThatThrownBy(() -> call(fixture, command(),
                Set.of(PERMISSION), 301L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("依据消费冲突");

        InOrder order = inOrder(fixture.mapper(), fixture.basisMapper());
        order.verify(fixture.mapper()).transitionCaseToPlanned(any());
        order.verify(fixture.basisMapper()).consumeIssuedBasis(any());
    }

    @Test
    @DisplayName("重放时当前事项或确认组织漂移被拒绝")
    void shouldRejectReplayWhenCurrentFactDrifts()
    {
        Fixture statusFixture = fixture();
        ReplayFixture statusReplay = replayFixture();
        statusReplay.fact().setCaseStatus("resolved");
        stubReplay(statusFixture, statusReplay);
        assertThatThrownBy(() -> call(statusFixture, command(),
                Set.of(PERMISSION), 301L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("当前事项不可核验");

        Fixture organizationFixture = fixture();
        ReplayFixture organizationReplay = replayFixture();
        organizationReplay.fact().getSourceConfirmation()
                .setPartyDeptId(999L);
        stubReplay(organizationFixture, organizationReplay);
        assertThatThrownBy(() -> call(organizationFixture, command(),
                Set.of(PERMISSION), 301L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("当前事项不可核验");
    }

    @Test
    @DisplayName("浏览器命令无事实指纹且服务方法拥有本地回滚事务")
    void shouldKeepBrowserSafeTransactionalSignature() throws Exception
    {
        assertThat(Arrays.stream(
                InvTransferReceiptDiscrepancyAdjudicationCommand.class
                        .getRecordComponents())
                .map(component -> component.getName()))
                .doesNotContain("factFingerprint");
        assertThat(command().toString())
                .contains("basisToken=[REDACTED]")
                .doesNotContain(token());
        Method method =
                InvTransferReceiptDiscrepancyAdjudicationCreationService.class
                        .getDeclaredMethod("create",
                                InvTransferReceiptDiscrepancyAdjudicationCommand.class,
                                Long.class);
        Transactional transaction = method.getAnnotation(
                Transactional.class);

        assertThat(transaction).isNotNull();
        assertThat(transaction.propagation()).isEqualTo(Propagation.REQUIRED);
        assertThat(transaction.rollbackFor()).contains(Exception.class);
    }

    private static Fixture fixture()
    {
        return fixture(NOW);
    }

    private static Fixture fixture(Instant clockNow)
    {
        InvTransferReceiptDiscrepancyAdjudicationMapper mapper = mock(
                InvTransferReceiptDiscrepancyAdjudicationMapper.class);
        InvTransferReceiptDiscrepancyAdjudicationBasisMapper basisMapper =
                mock(InvTransferReceiptDiscrepancyAdjudicationBasisMapper.class);
        ShopScopeService scope = mock(ShopScopeService.class);
        when(scope.resolveScopeDeptIds(301L)).thenReturn(SCOPE);
        when(basisMapper.selectByTokenHashForUpdate(tokenHash()))
                .thenReturn(issuedBasis());
        when(basisMapper.consumeIssuedBasis(any())).thenReturn(1);
        return new Fixture(
                new InvTransferReceiptDiscrepancyAdjudicationCreationService(
                        mapper, basisMapper, scope,
                        Clock.fixed(clockNow, ZoneOffset.UTC)),
                mapper, basisMapper, scope);
    }

    private static InvTransferReceiptDiscrepancyAdjudicationCreationVo call(
            Fixture fixture,
            InvTransferReceiptDiscrepancyAdjudicationCommand command,
            Set<String> permissions, Long selectedShopDeptId)
    {
        LoginUser login = new LoginUser();
        login.setUserid(99L);
        login.setUsername("independent-user");
        login.setPermissions(permissions);
        try (var security = mockStatic(SecurityUtils.class))
        {
            security.when(SecurityUtils::getLoginUser).thenReturn(login);
            security.when(SecurityUtils::getUserId).thenReturn(99L);
            security.when(SecurityUtils::getUsername)
                    .thenReturn("independent-user");
            return fixture.service().create(command, selectedShopDeptId);
        }
    }

    private static void generated(
            InvTransferReceiptDiscrepancyAdjudicationMapper mapper, Long id)
    {
        doAnswer(invocation -> {
            ((InvTransferReceiptGeneratedId) invocation.getArgument(1))
                    .setValue(id);
            return 1;
        }).when(mapper).insertAdjudication(any(), any());
    }

    private static ReplayFixture replayFixture()
    {
        InvTransferReceiptDiscrepancyReadFact fact = fact();
        PreparedAdjudication prepared = prepared(fact);
        InvTransferReceiptDiscrepancyStoredAdjudication stored =
                stored(prepared);
        InvTransferReceiptDiscrepancyStoredAdjudicationAction action =
                storedAction(prepared);
        fact.setCaseStatus("adjudication_planned");
        fact.setCaseVersion(4L);
        return new ReplayFixture(fact, stored, action);
    }

    private static void stubReplay(Fixture fixture, ReplayFixture replay)
    {
        when(fixture.mapper().selectCaseForUpdate(700L, SCOPE))
                .thenReturn(replay.fact());
        when(fixture.mapper().selectByRequestIdForUpdate(
                "adjudicate-0001")).thenReturn(replay.stored());
        when(fixture.basisMapper().selectByTokenHashForUpdate(tokenHash()))
                .thenReturn(consumedBasis());
        when(fixture.mapper().selectActionsByAdjudicationId(901L))
                .thenReturn(List.of(replay.action()));
    }

    private static PreparedAdjudication prepared(
            InvTransferReceiptDiscrepancyReadFact fact)
    {
        return InvTransferReceiptDiscrepancyAdjudicationPolicy.prepare(
                internalRequest(), InvTransferReceiptDiscrepancyBoundaryPolicy
                        .resolveForAdjudication(fact, 700L), actor(), NOW);
    }

    private static InvTransferReceiptDiscrepancyStoredAdjudication stored(
            PreparedAdjudication prepared)
    {
        var value = new InvTransferReceiptDiscrepancyStoredAdjudication();
        value.setAdjudicationId(901L);
        value.setRequestId(prepared.requestId());
        value.setCaseId(prepared.caseId());
        value.setCaseVersionBefore(prepared.caseVersionBefore());
        value.setCaseVersionAfter(prepared.caseVersionAfter());
        value.setFactFingerprint(prepared.factFingerprint());
        value.setSourceConfirmationEventId(
                prepared.sourceConfirmationEventId());
        value.setTargetConfirmationEventId(
                prepared.targetConfirmationEventId());
        value.setDiscrepancyType(prepared.discrepancyType());
        value.setDiscrepancyQuantity(prepared.discrepancyQuantity());
        value.setSourceCostPrice(prepared.sourceCostPrice());
        value.setDiscrepancyAmount(prepared.discrepancyAmount());
        value.setDecisionFingerprint(prepared.decisionFingerprint());
        value.setAdjudicationNote(prepared.adjudicationNote());
        value.setEvidenceRefs(prepared.evidenceRefs());
        value.setRequiredPermission(prepared.requiredPermission());
        value.setAdjudicatorUserId(prepared.adjudicatorUserId());
        value.setAdjudicatorName(prepared.adjudicatorName());
        value.setPlanStatus(prepared.planStatus());
        value.setCreateTime(Date.from(NOW.minusSeconds(60)));
        return value;
    }

    private static InvTransferReceiptDiscrepancyStoredAdjudicationAction
            storedAction(PreparedAdjudication prepared)
    {
        var source = prepared.actions().get(0);
        var value =
                new InvTransferReceiptDiscrepancyStoredAdjudicationAction();
        value.setActionId(902L);
        value.setAdjudicationId(901L);
        value.setCaseId(prepared.caseId());
        value.setSequence(source.sequence());
        value.setActionType(source.actionType());
        value.setCoverageKind(source.coverageKind());
        value.setQuantity(source.quantity());
        value.setAmount(source.amount());
        value.setResponsibleParty(source.responsibleParty());
        value.setNote(source.note());
        value.setExecutionStatus(source.executionStatus());
        return value;
    }

    private static InvTransferReceiptDiscrepancyAdjudicationCommand command()
    {
        return new InvTransferReceiptDiscrepancyAdjudicationCommand(
                "adjudicate-0001", token(), 700L, 3L,
                "独立裁决", "evidence-1", actions());
    }

    private static Request internalRequest()
    {
        return new Request("adjudicate-0001", 700L, 3L,
                fingerprint(), "独立裁决", "evidence-1", actions());
    }

    private static List<ActionInput> actions()
    {
        return List.of(new ActionInput(1, "reship",
                new BigDecimal("1.0000"), "source", "来源补发"));
    }

    private static IssuedBasis issued()
    {
        return InvTransferReceiptDiscrepancyAdjudicationBasisPolicy.issue(
                InvTransferReceiptDiscrepancyBoundaryPolicy
                        .resolveForAdjudication(fact(), 700L),
                actor(), 301L, SCOPE, NOW.minusSeconds(60), entropy());
    }

    private static String token()
    {
        return issued().token();
    }

    private static String tokenHash()
    {
        return issued().prepared().tokenHash();
    }

    private static byte[] entropy()
    {
        byte[] value = new byte[32];
        for (int index = 0; index < value.length; index++)
        {
            value[index] = (byte) index;
        }
        return value;
    }

    private static Actor actor()
    {
        return new Actor(99L, "independent-user", Set.of(PERMISSION));
    }

    private static InvTransferReceiptDiscrepancyStoredAdjudicationBasis
            issuedBasis()
    {
        IssuedBasis issued = issued();
        var source = issued.prepared();
        var value =
                new InvTransferReceiptDiscrepancyStoredAdjudicationBasis();
        value.setBasisId(801L);
        value.setTokenHash(source.tokenHash());
        value.setCaseId(source.caseId());
        value.setCaseVersion(source.caseVersion());
        value.setFactFingerprint(source.factFingerprint());
        value.setSourceConfirmationEventId(
                source.sourceConfirmationEventId());
        value.setTargetConfirmationEventId(
                source.targetConfirmationEventId());
        value.setSelectedShopDeptId(source.selectedShopDeptId());
        value.setScopeDigest(source.scopeDigest());
        value.setRequiredPermission(source.requiredPermission());
        value.setAdjudicatorUserId(source.adjudicatorUserId());
        value.setAdjudicatorName(source.adjudicatorName());
        value.setBasisStatus(source.basisStatus());
        value.setIssuedAt(Date.from(source.issuedAt()));
        value.setExpiresAt(Date.from(source.expiresAt()));
        return value;
    }

    private static InvTransferReceiptDiscrepancyStoredAdjudicationBasis
            consumedBasis()
    {
        var value = issuedBasis();
        value.setBasisStatus(
                InvTransferReceiptDiscrepancyAdjudicationBasisPolicy
                        .CONSUMED_STATUS);
        value.setConsumedAt(Date.from(NOW.minusSeconds(30)));
        value.setConsumedRequestId("adjudicate-0001");
        value.setConsumedAdjudicationId(901L);
        return value;
    }

    private static InvTransferReceiptDiscrepancyReadFact fact()
    {
        var value = new InvTransferReceiptDiscrepancyReadFact();
        value.setDiscrepancyCaseId(700L);
        value.setReceiptId(600L);
        value.setReceiptAllocationId(601L);
        value.setShipmentId(91L);
        value.setTransferId(900L);
        value.setShipmentAllocationId(81L);
        value.setShipmentDetailId(71L);
        value.setTransferDetailId(11L);
        value.setSourceDeptId(301L);
        value.setTargetDeptId(302L);
        value.setSourceName("苏州门店");
        value.setTargetName("南京门店");
        value.setOrderNo("TF202608030001");
        value.setShipmentNo("TS20260803ABCD");
        value.setReceiptNo("TR20260803ABCD");
        value.setReceiptPlanVersion(PLAN);
        value.setItemType("product");
        value.setItemId(1001L);
        value.setItemCode("SKU-1001");
        value.setItemName("演示商品");
        value.setUnit("件");
        value.setDiscrepancyType("shortage");
        value.setDiscrepancyQuantity(new BigDecimal("1.0000"));
        value.setSourceCostPrice(new BigDecimal("10.000000"));
        value.setDiscrepancyAmount(new BigDecimal("10.000000"));
        value.setDiscrepancyNote("封签完整但箱内短少");
        value.setAttachmentRefs("attachment-1");
        value.setFactFingerprint(fingerprint());
        value.setCaseStatus("awaiting_confirmation");
        value.setCaseVersion(3L);
        value.setCaseCreateBy("receiving-user");
        value.setCaseCreateTime(Date.from(NOW.minusSeconds(600)));
        value.setSourceConfirmation(confirmation(1L, "source", 301L, 91L));
        value.setTargetConfirmation(confirmation(2L, "target", 302L, 92L));
        return value;
    }

    private static String fingerprint()
    {
        return InvTransferReceiptDiscrepancyFacts.fingerprint(PLAN, 81L,
                "shortage", new BigDecimal("1.0000"),
                new BigDecimal("10.000000"),
                new BigDecimal("10.000000"), "封签完整但箱内短少",
                "attachment-1");
    }

    private static InvTransferReceiptDiscrepancyReadFact.Confirmation
            confirmation(Long id, String role, Long deptId, Long userId)
    {
        var value =
                new InvTransferReceiptDiscrepancyReadFact.Confirmation();
        value.setEventId(id);
        value.setRequestId("confirm-" + role);
        value.setCaseVersion(3L);
        value.setFactFingerprint(fingerprint());
        value.setPartyRole(role);
        value.setPartyDeptId(deptId);
        value.setDecision("confirmed");
        value.setOperatorUserId(userId);
        value.setOperatorName(role + "-user");
        value.setCreateTime(Date.from(NOW.minusSeconds(id)));
        return value;
    }

    private record Fixture(
            InvTransferReceiptDiscrepancyAdjudicationCreationService service,
            InvTransferReceiptDiscrepancyAdjudicationMapper mapper,
            InvTransferReceiptDiscrepancyAdjudicationBasisMapper basisMapper,
            ShopScopeService scope)
    {
    }

    private record ReplayFixture(
            InvTransferReceiptDiscrepancyReadFact fact,
            InvTransferReceiptDiscrepancyStoredAdjudication stored,
            InvTransferReceiptDiscrepancyStoredAdjudicationAction action)
    {
    }
}

package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationBasisPolicy.PreparedBasis;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationPolicy;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyFacts;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReadFact;
import com.erp.inventory.domain.transfer.InvTransferReceiptGeneratedId;
import com.erp.inventory.domain.vo.InvTransferReceiptDiscrepancyAdjudicationBasisIssueVo;
import com.erp.inventory.mapper.InvTransferReceiptDiscrepancyAdjudicationBasisMapper;
import com.erp.inventory.mapper.InvTransferReceiptDiscrepancyAdjudicationMapper;
import com.erp.system.api.model.LoginUser;

@DisplayName("V2调拨差异裁决不透明依据签发事务")
class InvTransferReceiptDiscrepancyAdjudicationBasisIssueServiceTest
{
    private static final String PLAN = "a".repeat(64);
    private static final Instant NOW = Instant.parse(
            "2026-08-03T00:00:00Z");
    private static final String PERMISSION =
            InvTransferReceiptDiscrepancyAdjudicationPolicy
                    .REQUIRED_PERMISSION;

    @Test
    @DisplayName("按组织范围、事项锁和条件摘要插入固定顺序签发")
    void shouldIssueInFixedOrderWithoutPersistingRawToken()
    {
        Fixture fixture = fixture();
        when(fixture.scope().resolveScopeDeptIds(301L))
                .thenReturn(List.of(302L, 301L, 302L));
        when(fixture.caseMapper().selectCaseForUpdate(700L,
                List.of(302L, 301L)))
                .thenReturn(fact());
        doAnswer(invocation -> {
            ((InvTransferReceiptGeneratedId) invocation.getArgument(1))
                    .setValue(801L);
            return 1;
        }).when(fixture.basisMapper()).insertIssuedBasis(any(), any());

        InvTransferReceiptDiscrepancyAdjudicationBasisIssueVo result =
                call(fixture, Set.of(PERMISSION));

        assertThat(result.basisToken())
                .matches("adjb_v1_[A-Za-z0-9_-]{43}");
        assertThat(result.discrepancyCaseId()).isEqualTo("700");
        assertThat(result.caseVersion()).isEqualTo("3");
        assertThat(result.selectedOrganizationId()).isEqualTo("301");
        assertThat(result.expiresAt())
                .isEqualTo("2026-08-03T00:05:00Z");
        assertThat(result.dataSource()).isEqualTo("server");
        assertThat(result.toString())
                .contains("[REDACTED]")
                .doesNotContain(result.basisToken());

        ArgumentCaptor<PreparedBasis> basis =
                ArgumentCaptor.forClass(PreparedBasis.class);
        verify(fixture.basisMapper())
                .insertIssuedBasis(basis.capture(), any());
        assertThat(basis.getValue().tokenHash())
                .matches("[a-f0-9]{64}")
                .isNotEqualTo(result.basisToken());
        assertThat(basis.getValue().toString())
                .doesNotContain(result.basisToken());
        assertThat(basis.getValue().scopeDeptIds())
                .containsExactly(301L, 302L);

        InOrder order = inOrder(fixture.scope(), fixture.caseMapper(),
                fixture.basisMapper());
        order.verify(fixture.scope()).resolveScopeDeptIds(301L);
        order.verify(fixture.caseMapper()).selectCaseForUpdate(700L,
                List.of(302L, 301L));
        order.verify(fixture.basisMapper())
                .insertIssuedBasis(any(), any());
    }

    @Test
    @DisplayName("没有精确权限时在组织解析和数据库访问前拒绝")
    void shouldRejectPermissionBeforeExternalAccess()
    {
        Fixture fixture = fixture();

        assertThatThrownBy(() -> call(fixture, Set.of()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("签发权限无效");

        verifyNoInteractions(fixture.scope(), fixture.caseMapper(),
                fixture.basisMapper());
    }

    @Test
    @DisplayName("事项不在当前组织范围时不插入令牌摘要")
    void shouldRejectCaseOutsideResolvedScope()
    {
        Fixture fixture = fixture();
        when(fixture.scope().resolveScopeDeptIds(301L))
                .thenReturn(List.of(301L));
        InvTransferReceiptDiscrepancyReadFact outOfScope = fact();
        outOfScope.setSourceDeptId(401L);
        outOfScope.setTargetDeptId(402L);
        outOfScope.getSourceConfirmation().setPartyDeptId(401L);
        outOfScope.getTargetConfirmation().setPartyDeptId(402L);
        when(fixture.caseMapper().selectCaseForUpdate(700L,
                List.of(301L)))
                .thenReturn(outOfScope);

        assertThatThrownBy(() -> call(fixture, Set.of(PERMISSION)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不存在或当前范围无权签发依据");
        verify(fixture.basisMapper(), never())
                .insertIssuedBasis(any(), any());
    }

    @Test
    @DisplayName("条件插入或生成键异常时签发事务失败关闭")
    void shouldFailClosedOnConditionalInsertOrGeneratedKey()
    {
        Fixture conflict = fixture();
        stubBoundary(conflict);
        assertThatThrownBy(() -> call(conflict, Set.of(PERMISSION)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("签发冲突");

        Fixture missingId = fixture();
        stubBoundary(missingId);
        when(missingId.basisMapper().insertIssuedBasis(any(), any()))
                .thenReturn(1);
        assertThatThrownBy(() -> call(missingId, Set.of(PERMISSION)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("主键生成失败");
    }

    @Test
    @DisplayName("签发方法是唯一回滚事务且当前没有控制器入口")
    void shouldOwnRollbackTransactionWithoutController()
            throws Exception
    {
        Method method =
                InvTransferReceiptDiscrepancyAdjudicationBasisIssueService
                        .class.getMethod("issue", Long.class, Long.class);
        Transactional transactional =
                method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(Arrays.asList(transactional.rollbackFor()))
                .contains(Exception.class);
    }

    private static void stubBoundary(Fixture fixture)
    {
        when(fixture.scope().resolveScopeDeptIds(301L))
                .thenReturn(List.of(301L, 302L));
        when(fixture.caseMapper().selectCaseForUpdate(700L,
                List.of(301L, 302L)))
                .thenReturn(fact());
    }

    private static Fixture fixture()
    {
        var caseMapper =
                mock(InvTransferReceiptDiscrepancyAdjudicationMapper.class);
        var basisMapper = mock(
                InvTransferReceiptDiscrepancyAdjudicationBasisMapper.class);
        var scope = mock(ShopScopeService.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        var service =
                new InvTransferReceiptDiscrepancyAdjudicationBasisIssueService(
                        caseMapper, basisMapper, scope, clock,
                        InvTransferReceiptDiscrepancyAdjudicationBasisIssueServiceTest
                                ::entropy);
        return new Fixture(service, caseMapper, basisMapper, scope);
    }

    private static byte[] entropy()
    {
        byte[] result = new byte[32];
        for (int index = 0; index < result.length; index++)
        {
            result[index] = (byte) index;
        }
        return result;
    }

    private static InvTransferReceiptDiscrepancyAdjudicationBasisIssueVo
            call(Fixture fixture, Set<String> permissions)
    {
        LoginUser login = new LoginUser();
        login.setUserid(99L);
        login.setUsername("independent-user");
        login.setPermissions(permissions);
        try (MockedStatic<SecurityUtils> security =
                mockStatic(SecurityUtils.class))
        {
            security.when(SecurityUtils::getLoginUser).thenReturn(login);
            security.when(SecurityUtils::getUserId).thenReturn(99L);
            security.when(SecurityUtils::getUsername)
                    .thenReturn("independent-user");
            return fixture.service().issue(700L, 301L);
        }
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
        value.setSourceConfirmation(confirmation(1L, "source", 301L,
                91L));
        value.setTargetConfirmation(confirmation(2L, "target", 302L,
                92L));
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
            InvTransferReceiptDiscrepancyAdjudicationBasisIssueService
                    service,
            InvTransferReceiptDiscrepancyAdjudicationMapper caseMapper,
            InvTransferReceiptDiscrepancyAdjudicationBasisMapper basisMapper,
            ShopScopeService scope)
    {
    }

}

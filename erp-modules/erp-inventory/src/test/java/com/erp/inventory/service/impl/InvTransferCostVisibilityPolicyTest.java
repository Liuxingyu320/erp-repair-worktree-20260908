package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.inventory.domain.InvTransferDetail;
import com.erp.inventory.domain.InvTransferDiscrepancy;
import com.erp.inventory.domain.InvTransferDiscrepancyDisposition;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.InvTransferShipment;
import com.erp.inventory.domain.InvTransferShipmentDetail;
import com.erp.inventory.domain.vo.InvTransferRevisionDetailVo;
import com.erp.inventory.domain.vo.InvTransferRevisionHeaderVo;
import com.erp.inventory.domain.vo.InvTransferRevisionHistoryVo;
import com.erp.inventory.domain.vo.InvTransferRevisionVo;
import com.erp.system.api.model.LoginUser;

@DisplayName("调拨成本字段可见性策略")
class InvTransferCostVisibilityPolicyTest
{
    private final InvTransferCostVisibilityPolicy policy =
            new InvTransferCostVisibilityPolicy();

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("无成本权限时脱敏单头、申请明细、发货明细和修订快照")
    void shouldRedactEveryTransferCostSurface()
    {
        InvTransferOrder order = orderWithCosts();
        InvTransferRevisionHistoryVo history = historyWithCosts();
        InvTransferDiscrepancy discrepancy = discrepancyWithCosts();

        policy.redactOrdersIfNeeded(List.of(order));
        policy.redactRevisionHistoryIfNeeded(history);
        policy.redactDiscrepanciesIfNeeded(List.of(discrepancy));

        assertThat(order.getTotalAmount()).isNull();
        assertThat(order.getDetails().get(0).getCostPrice()).isNull();
        assertThat(order.getDetails().get(0).getAmount()).isNull();
        assertThat(order.getShipments().get(0).getDetails().get(0)
                .getCostPrice()).isNull();
        assertThat(history.getRevisions().get(0).getHeader()
                .getTotalAmount()).isNull();
        assertThat(history.getRevisions().get(0).getDetails().get(0)
                .getCostPrice()).isNull();
        assertThat(history.getRevisions().get(0).getDetails().get(0)
                .getAmount()).isNull();
        assertThat(discrepancy.getDispositions().get(0).getCostPrice())
                .isNull();
        assertThat(discrepancy.getDispositions().get(0).getAmount()).isNull();
    }

    @Test
    @DisplayName("精确成本权限保留全部调拨成本字段")
    void shouldRetainCostsForAuthorizedViewer()
    {
        setCostViewer();
        InvTransferOrder order = orderWithCosts();
        InvTransferRevisionHistoryVo history = historyWithCosts();
        InvTransferDiscrepancy discrepancy = discrepancyWithCosts();

        policy.redactOrderIfNeeded(order);
        policy.redactRevisionHistoryIfNeeded(history);
        policy.redactDiscrepancyIfNeeded(discrepancy);

        assertThat(order.getTotalAmount()).isEqualByComparingTo("88.00");
        assertThat(order.getDetails().get(0).getCostPrice())
                .isEqualByComparingTo("8.00");
        assertThat(order.getDetails().get(0).getAmount())
                .isEqualByComparingTo("88.00");
        assertThat(order.getShipments().get(0).getDetails().get(0)
                .getCostPrice()).isEqualByComparingTo("8.00");
        assertThat(history.getRevisions().get(0).getHeader()
                .getTotalAmount()).isEqualTo("88.00");
        assertThat(history.getRevisions().get(0).getDetails().get(0)
                .getCostPrice()).isEqualTo("8.00");
        assertThat(history.getRevisions().get(0).getDetails().get(0)
                .getAmount()).isEqualTo("88.00");
        assertThat(discrepancy.getDispositions().get(0).getCostPrice())
                .isEqualByComparingTo("8.00");
        assertThat(discrepancy.getDispositions().get(0).getAmount())
                .isEqualByComparingTo("88.00");
    }

    private InvTransferOrder orderWithCosts()
    {
        InvTransferDetail detail = new InvTransferDetail();
        detail.setCostPrice(new BigDecimal("8.00"));
        detail.setAmount(new BigDecimal("88.00"));
        InvTransferShipmentDetail shipmentDetail =
                new InvTransferShipmentDetail();
        shipmentDetail.setCostPrice(new BigDecimal("8.00"));
        InvTransferShipment shipment = new InvTransferShipment();
        shipment.setDetails(List.of(shipmentDetail));
        InvTransferOrder order = new InvTransferOrder();
        order.setTotalAmount(new BigDecimal("88.00"));
        order.setDetails(List.of(detail));
        order.setShipments(List.of(shipment));
        return order;
    }

    private InvTransferRevisionHistoryVo historyWithCosts()
    {
        InvTransferRevisionHeaderVo header =
                new InvTransferRevisionHeaderVo();
        header.setTotalAmount("88.00");
        InvTransferRevisionDetailVo detail =
                new InvTransferRevisionDetailVo();
        detail.setCostPrice("8.00");
        detail.setAmount("88.00");
        InvTransferRevisionVo revision = new InvTransferRevisionVo();
        revision.setHeader(header);
        revision.setDetails(List.of(detail));
        InvTransferRevisionHistoryVo history =
                new InvTransferRevisionHistoryVo();
        history.setRevisions(List.of(revision));
        return history;
    }

    private InvTransferDiscrepancy discrepancyWithCosts()
    {
        InvTransferDiscrepancyDisposition disposition =
                new InvTransferDiscrepancyDisposition();
        disposition.setCostPrice(new BigDecimal("8.00"));
        disposition.setAmount(new BigDecimal("88.00"));
        InvTransferDiscrepancy discrepancy = new InvTransferDiscrepancy();
        discrepancy.setDispositions(List.of(disposition));
        return discrepancy;
    }

    private void setCostViewer()
    {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserid(42L);
        loginUser.setUsername("transfer-cost-viewer");
        loginUser.setPermissions(Set.of(
                InvTransferCostVisibilityPolicy.COST_VIEW_PERMISSION));
        SecurityContextHolder.setUserId("42");
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);
    }
}

package com.erp.inventory.service.impl;

import java.util.List;
import org.springframework.stereotype.Component;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.inventory.domain.InvTransferDetail;
import com.erp.inventory.domain.InvTransferDiscrepancy;
import com.erp.inventory.domain.InvTransferDiscrepancyDisposition;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.InvTransferShipment;
import com.erp.inventory.domain.InvTransferShipmentDetail;
import com.erp.inventory.domain.vo.InvTransferRevisionDetailVo;
import com.erp.inventory.domain.vo.InvTransferRevisionHistoryVo;
import com.erp.inventory.domain.vo.InvTransferRevisionVo;
import com.erp.system.api.model.LoginUser;

/**
 * Server-side field policy for transfer cost responses.
 *
 * UI visibility is deliberately not treated as a security boundary. The same
 * policy is reused by JSON reads and exports so a transfer permission alone
 * never implies access to internal cost values.
 */
@Component
public class InvTransferCostVisibilityPolicy
{
    public static final String COST_VIEW_PERMISSION = "inv:cost:view";

    public boolean canViewCost()
    {
        if (SecurityUtils.isAdmin())
        {
            return true;
        }
        LoginUser loginUser = SecurityUtils.getLoginUser();
        return loginUser != null
                && loginUser.getPermissions() != null
                && loginUser.getPermissions().contains(COST_VIEW_PERMISSION);
    }

    public void redactOrdersIfNeeded(List<InvTransferOrder> orders)
    {
        if (canViewCost() || orders == null)
        {
            return;
        }
        orders.forEach(this::redactOrder);
    }

    public InvTransferOrder redactOrderIfNeeded(InvTransferOrder order)
    {
        if (!canViewCost())
        {
            redactOrder(order);
        }
        return order;
    }

    public InvTransferRevisionHistoryVo redactRevisionHistoryIfNeeded(
            InvTransferRevisionHistoryVo history)
    {
        if (canViewCost() || history == null || history.getRevisions() == null)
        {
            return history;
        }
        for (InvTransferRevisionVo revision : history.getRevisions())
        {
            if (revision == null)
            {
                continue;
            }
            if (revision.getHeader() != null)
            {
                revision.getHeader().setTotalAmount(null);
            }
            if (revision.getDetails() == null)
            {
                continue;
            }
            for (InvTransferRevisionDetailVo detail : revision.getDetails())
            {
                if (detail != null)
                {
                    detail.setCostPrice(null);
                    detail.setAmount(null);
                }
            }
        }
        return history;
    }

    public List<InvTransferDiscrepancy> redactDiscrepanciesIfNeeded(
            List<InvTransferDiscrepancy> discrepancies)
    {
        if (canViewCost() || discrepancies == null)
        {
            return discrepancies;
        }
        discrepancies.forEach(this::redactDiscrepancy);
        return discrepancies;
    }

    public InvTransferDiscrepancy redactDiscrepancyIfNeeded(
            InvTransferDiscrepancy discrepancy)
    {
        if (!canViewCost())
        {
            redactDiscrepancy(discrepancy);
        }
        return discrepancy;
    }

    private void redactDiscrepancy(InvTransferDiscrepancy discrepancy)
    {
        if (discrepancy == null || discrepancy.getDispositions() == null)
        {
            return;
        }
        for (InvTransferDiscrepancyDisposition disposition
                : discrepancy.getDispositions())
        {
            if (disposition != null)
            {
                disposition.setCostPrice(null);
                disposition.setAmount(null);
            }
        }
    }

    private void redactOrder(InvTransferOrder order)
    {
        if (order == null)
        {
            return;
        }
        order.setTotalAmount(null);
        if (order.getDetails() != null)
        {
            for (InvTransferDetail detail : order.getDetails())
            {
                if (detail != null)
                {
                    detail.setCostPrice(null);
                    detail.setAmount(null);
                }
            }
        }
        if (order.getShipments() == null)
        {
            return;
        }
        for (InvTransferShipment shipment : order.getShipments())
        {
            if (shipment == null || shipment.getDetails() == null)
            {
                continue;
            }
            for (InvTransferShipmentDetail detail : shipment.getDetails())
            {
                if (detail != null)
                {
                    detail.setCostPrice(null);
                }
            }
        }
    }
}

package com.erp.inventory.service.impl;

import java.util.Objects;
import org.springframework.stereotype.Service;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.inventory.domain.InvTransferCommand;
import com.erp.inventory.mapper.InvTransferCommandStatusMapper;

@Service
public class InvTransferCommandStatusService
{
    private final InvTransferCommandStatusMapper mapper;
    private final InventoryShopScopeService scope;
    public InvTransferCommandStatusService(InvTransferCommandStatusMapper mapper,
            InventoryShopScopeService scope)
    { this.mapper = mapper; this.scope = scope; }

    public Status status(String requestId, Long selectedDeptId)
    {
        String normalized = InvTransferCommandExecutor.requireRequestId(requestId);
        Long actor = SecurityUtils.getUserId();
        if (actor == null || actor <= 0) throw new ServiceException("请先登录");
        Long deptId = scope.resolveRequiredShopDept(selectedDeptId);
        InvTransferCommand command = mapper.selectStatus(normalized);
        // Do not reveal whether another actor's or organization's command exists.
        if (command == null || !Objects.equals(actor, command.getActorUserId())
                || !Objects.equals(deptId, command.getSelectedDeptId()))
            return new Status("NOT_FOUND");
        return new Status("SUCCEEDED".equals(command.getStatus()) ? "SUCCEEDED" : "PENDING");
    }
    public record Status(String status) { }
}

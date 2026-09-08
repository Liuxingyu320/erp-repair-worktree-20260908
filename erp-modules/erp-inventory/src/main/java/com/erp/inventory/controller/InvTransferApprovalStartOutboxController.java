package com.erp.inventory.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.core.web.page.TableDataInfo;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessType;
import com.erp.common.security.annotation.IdempotentSubmit;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.inventory.domain.dto.InvTransferApprovalStartReplayRequest;
import com.erp.inventory.domain.vo.InvTransferApprovalStartOutboxQuery;
import com.erp.inventory.service.impl.InvTransferApprovalStartDispatcher;
import com.erp.inventory.service.impl.InvTransferApprovalStartOutboxService;

/** 调拨统一审批发起可观测与人工恢复入口。 */
@RestController
@RequestMapping("/transfer/approval-start-outbox")
public class InvTransferApprovalStartOutboxController
        extends InvBaseController
{
    private final InvTransferApprovalStartOutboxService service;
    private final InvTransferApprovalStartDispatcher dispatcher;

    public InvTransferApprovalStartOutboxController(
            InvTransferApprovalStartOutboxService service,
            InvTransferApprovalStartDispatcher dispatcher)
    {
        this.service = service;
        this.dispatcher = dispatcher;
    }

    @RequiresPermissions("inv:transfer:approvalStartOutbox:list")
    @GetMapping("/list")
    public TableDataInfo list(InvTransferApprovalStartOutboxQuery query)
    {
        startPage();
        return getDataTable(service.selectOps(query));
    }

    @RequiresPermissions("inv:transfer:approvalStartOutbox:list")
    @GetMapping("/summary")
    public AjaxResult summary(InvTransferApprovalStartOutboxQuery query)
    {
        return success(service.selectSummary(query));
    }

    @RequiresPermissions("inv:transfer:approvalStartOutbox:replay")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "调拨审批发起失败重放", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/{outboxId}/replay")
    public AjaxResult replay(@PathVariable Long outboxId,
            @Validated @RequestBody InvTransferApprovalStartReplayRequest request)
    {
        InvTransferApprovalStartOutboxQuery query =
                new InvTransferApprovalStartOutboxQuery();
        query.setOutboxId(outboxId);
        service.replayFailed(query, request.getVersion(),
                SecurityUtils.getUsername());
        dispatcher.dispatchOneNow(outboxId);
        return success();
    }
}

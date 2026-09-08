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
import com.erp.inventory.domain.dto.InvStockCheckApprovalStartReplayRequest;
import com.erp.inventory.domain.vo.InvStockCheckApprovalStartOutboxQuery;
import com.erp.inventory.service.impl.InvStockCheckApprovalStartDispatcher;
import com.erp.inventory.service.impl.InvStockCheckApprovalStartOutboxService;

/** 盘点统一审批发起可观测与人工恢复入口。 */
@RestController
@RequestMapping("/stockCheck/approval-start-outbox")
public class InvStockCheckApprovalStartOutboxController
        extends InvBaseController
{
    private final InvStockCheckApprovalStartOutboxService service;
    private final InvStockCheckApprovalStartDispatcher dispatcher;

    public InvStockCheckApprovalStartOutboxController(
            InvStockCheckApprovalStartOutboxService service,
            InvStockCheckApprovalStartDispatcher dispatcher)
    {
        this.service = service;
        this.dispatcher = dispatcher;
    }

    @RequiresPermissions("inv:stockCheck:approvalStartOutbox:list")
    @GetMapping("/list")
    public TableDataInfo list(InvStockCheckApprovalStartOutboxQuery query)
    {
        startPage();
        return getDataTable(service.selectOps(query));
    }

    @RequiresPermissions("inv:stockCheck:approvalStartOutbox:list")
    @GetMapping("/summary")
    public AjaxResult summary(InvStockCheckApprovalStartOutboxQuery query)
    {
        return success(service.selectSummary(query));
    }

    @RequiresPermissions("inv:stockCheck:approvalStartOutbox:replay")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "盘点审批发起失败重放", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/{outboxId}/replay")
    public AjaxResult replay(@PathVariable Long outboxId,
            @Validated @RequestBody InvStockCheckApprovalStartReplayRequest request)
    {
        InvStockCheckApprovalStartOutboxQuery query =
                new InvStockCheckApprovalStartOutboxQuery();
        query.setOutboxId(outboxId);
        service.replayFailed(query, request.getVersion(),
                SecurityUtils.getUsername());
        dispatcher.dispatchOneNow(outboxId);
        return success();
    }
}

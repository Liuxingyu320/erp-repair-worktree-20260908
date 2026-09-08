package com.erp.oa.controller;

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
import com.erp.oa.domain.dto.OaPurchaseApprovalStartReplayRequest;
import com.erp.oa.domain.vo.OaPurchaseApprovalStartOutboxQuery;
import com.erp.oa.service.impl.OaPurchaseApprovalStartDispatcher;
import com.erp.oa.service.impl.OaPurchaseApprovalStartOutboxService;

/** OA 采购审批发起的可观测与人工恢复入口。 */
@RestController
@RequestMapping("/purchase/approval-start-outbox")
public class OaPurchaseApprovalStartOutboxController
        extends OaBaseController
{
    private final OaPurchaseApprovalStartOutboxService service;
    private final OaPurchaseApprovalStartDispatcher dispatcher;

    public OaPurchaseApprovalStartOutboxController(
            OaPurchaseApprovalStartOutboxService service,
            OaPurchaseApprovalStartDispatcher dispatcher)
    {
        this.service = service;
        this.dispatcher = dispatcher;
    }

    @RequiresPermissions("oa:purchase:approvalStartOutbox:list")
    @GetMapping("/list")
    public TableDataInfo list(OaPurchaseApprovalStartOutboxQuery query)
    {
        startPage();
        return getDataTable(service.selectOps(query));
    }

    @RequiresPermissions("oa:purchase:approvalStartOutbox:list")
    @GetMapping("/summary")
    public AjaxResult summary(OaPurchaseApprovalStartOutboxQuery query)
    {
        return success(service.selectSummary(query));
    }

    @RequiresPermissions("oa:purchase:approvalStartOutbox:replay")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "OA采购审批发起失败重放",
            businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/{outboxId}/replay")
    public AjaxResult replay(@PathVariable Long outboxId,
            @Validated @RequestBody OaPurchaseApprovalStartReplayRequest request)
    {
        OaPurchaseApprovalStartOutboxQuery query =
                new OaPurchaseApprovalStartOutboxQuery();
        query.setOutboxId(outboxId);
        service.replayFailed(query, request.getVersion(),
                SecurityUtils.getUsername());
        dispatcher.dispatchOneNow(outboxId);
        return success();
    }
}

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
import com.erp.oa.domain.dto.OaReimbursementApprovalStartReplayRequest;
import com.erp.oa.domain.vo.OaReimbursementApprovalStartOutboxQuery;
import com.erp.oa.service.impl.OaReimbursementApprovalStartDispatcher;
import com.erp.oa.service.impl.OaReimbursementApprovalStartOutboxService;

/** OA 报销审批发起的可观测与人工恢复入口。 */
@RestController
@RequestMapping("/reimbursement/approval-start-outbox")
public class OaReimbursementApprovalStartOutboxController
        extends OaBaseController
{
    private final OaReimbursementApprovalStartOutboxService service;
    private final OaReimbursementApprovalStartDispatcher dispatcher;

    public OaReimbursementApprovalStartOutboxController(
            OaReimbursementApprovalStartOutboxService service,
            OaReimbursementApprovalStartDispatcher dispatcher)
    {
        this.service = service;
        this.dispatcher = dispatcher;
    }

    @RequiresPermissions("oa:reimbursement:approvalStartOutbox:list")
    @GetMapping("/list")
    public TableDataInfo list(OaReimbursementApprovalStartOutboxQuery query)
    {
        startPage();
        return getDataTable(service.selectOps(query));
    }

    @RequiresPermissions("oa:reimbursement:approvalStartOutbox:list")
    @GetMapping("/summary")
    public AjaxResult summary(OaReimbursementApprovalStartOutboxQuery query)
    {
        return success(service.selectSummary(query));
    }

    @RequiresPermissions("oa:reimbursement:approvalStartOutbox:replay")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "OA报销审批发起失败重放",
            businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/{outboxId}/replay")
    public AjaxResult replay(@PathVariable Long outboxId,
            @Validated @RequestBody OaReimbursementApprovalStartReplayRequest request)
    {
        OaReimbursementApprovalStartOutboxQuery query =
                new OaReimbursementApprovalStartOutboxQuery();
        query.setOutboxId(outboxId);
        service.replayFailed(query, request.getVersion(),
                SecurityUtils.getUsername());
        dispatcher.dispatchOneNow(outboxId);
        return success();
    }
}

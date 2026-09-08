package com.erp.system.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.erp.common.core.web.controller.BaseController;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.core.web.page.TableDataInfo;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessType;
import com.erp.common.security.annotation.IdempotentSubmit;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.system.domain.dto.HrHealthCertificateApprovalStartReplayRequest;
import com.erp.system.domain.vo.HrHealthCertificateApprovalStartOutboxQuery;
import com.erp.system.service.impl.HrHealthCertificateApprovalStartDispatcher;
import com.erp.system.service.impl.HrHealthCertificateApprovalStartOutboxService;

/** 健康证审批发起的脱敏运维与人工恢复入口。 */
@RestController
@RequestMapping("/hr/health-certificate/approval-start-outbox")
public class HrHealthCertificateApprovalStartOutboxController
        extends BaseController
{
    private final HrHealthCertificateApprovalStartOutboxService service;
    private final HrHealthCertificateApprovalStartDispatcher dispatcher;

    public HrHealthCertificateApprovalStartOutboxController(
            HrHealthCertificateApprovalStartOutboxService service,
            HrHealthCertificateApprovalStartDispatcher dispatcher)
    {
        this.service = service;
        this.dispatcher = dispatcher;
    }

    @RequiresPermissions("hr:healthCertificate:approvalStartOutbox:list")
    @GetMapping("/list")
    public TableDataInfo list(
            HrHealthCertificateApprovalStartOutboxQuery query)
    {
        startPage();
        return getDataTable(service.selectOps(query));
    }

    @RequiresPermissions("hr:healthCertificate:approvalStartOutbox:list")
    @GetMapping("/summary")
    public AjaxResult summary(
            HrHealthCertificateApprovalStartOutboxQuery query)
    {
        return success(service.selectSummary(query));
    }

    @RequiresPermissions("hr:healthCertificate:approvalStartOutbox:replay")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "健康证审批发起失败重放", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/{outboxId}/replay")
    public AjaxResult replay(@PathVariable Long outboxId,
            @Validated @RequestBody
            HrHealthCertificateApprovalStartReplayRequest request)
    {
        HrHealthCertificateApprovalStartOutboxQuery query =
                new HrHealthCertificateApprovalStartOutboxQuery();
        query.setOutboxId(outboxId);
        service.replayFailed(query, request.getVersion(),
                SecurityUtils.getUsername());
        dispatcher.dispatchOneNow(outboxId);
        return success();
    }
}

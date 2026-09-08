package com.erp.oa.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.erp.approval.api.domain.ApprovalBusinessCallbackRequest;
import com.erp.approval.api.domain.ApprovalBusinessCallbackResponse;
import com.erp.approval.api.domain.ApprovalBusinessActionValidationRequest;
import com.erp.approval.api.domain.ApprovalBusinessActionValidationResponse;
import com.erp.approval.api.constant.ApprovalApiPaths;
import com.erp.common.core.domain.R;
import com.erp.common.security.annotation.InnerAuth;
import com.erp.oa.service.IOaPurchaseService;
import com.erp.oa.service.IOaReimbursementService;
import com.erp.oa.service.impl.OaReimbursementServiceImpl;
import com.erp.oa.attendance.approval.OaAttendanceApprovalCallbackService;
import com.erp.oa.service.impl.OaPurchaseApprovalStartOutboxService;

/** 统一审批中心回调 OA 业务的唯一内部入口。 */
@RestController
@RequestMapping(ApprovalApiPaths.INNER_BASE)
public class OaApprovalCallbackController
{
    private final IOaPurchaseService purchaseService;
    private final IOaReimbursementService reimbursementService;
    private final OaAttendanceApprovalCallbackService attendanceService;

    public OaApprovalCallbackController(IOaPurchaseService purchaseService,
            IOaReimbursementService reimbursementService,
            OaAttendanceApprovalCallbackService attendanceService)
    {
        this.purchaseService = purchaseService;
        this.reimbursementService = reimbursementService;
        this.attendanceService = attendanceService;
    }

    @InnerAuth
    @PostMapping("/callback")
    public R<ApprovalBusinessCallbackResponse> callback(
            @RequestBody ApprovalBusinessCallbackRequest request)
    {
        if (request != null && OaReimbursementServiceImpl.BUSINESS_CODE
                .equals(request.getBusinessCode()))
        {
            return R.ok(reimbursementService.applyApprovalCallback(request));
        }
        if (request != null && attendanceService.supports(
                request.getBusinessCode()))
        {
            return R.ok(attendanceService.apply(request));
        }
        if (request != null && OaPurchaseApprovalStartOutboxService
                .BUSINESS_CODE.equals(request.getBusinessCode()))
        {
            return R.ok(purchaseService.applyApprovalCallback(request));
        }
        ApprovalBusinessCallbackResponse unsupported =
                new ApprovalBusinessCallbackResponse();
        unsupported.setAccepted(false);
        unsupported.setInvalidated(false);
        unsupported.setCode("UNSUPPORTED_BUSINESS_CODE");
        unsupported.setMessage("OA 不支持该审批业务类型");
        return R.ok(unsupported);
    }

    @InnerAuth
    @PostMapping(ApprovalApiPaths.VALIDATE_ACTION)
    public R<ApprovalBusinessActionValidationResponse> validateAction(
            @RequestBody ApprovalBusinessActionValidationRequest request)
    {
        return R.ok(attendanceService.validateAction(request));
    }
}

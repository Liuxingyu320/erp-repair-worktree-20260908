package com.erp.system.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.erp.approval.api.domain.ApprovalBusinessCallbackRequest;
import com.erp.approval.api.domain.ApprovalBusinessCallbackResponse;
import com.erp.common.core.domain.R;
import com.erp.common.security.annotation.InnerAuth;
import com.erp.system.service.IHrHealthCertificateService;

/** 统一审批中心回调 system 业务的唯一内部入口。 */
@RestController
@RequestMapping("/inner/approval")
public class SystemApprovalCallbackController
{
    private final IHrHealthCertificateService healthCertificateService;

    public SystemApprovalCallbackController(
            IHrHealthCertificateService healthCertificateService)
    {
        this.healthCertificateService = healthCertificateService;
    }

    @InnerAuth
    @PostMapping("/callback")
    public R<ApprovalBusinessCallbackResponse> callback(
            @RequestBody ApprovalBusinessCallbackRequest request)
    {
        return R.ok(healthCertificateService.applyApprovalCallback(request));
    }
}

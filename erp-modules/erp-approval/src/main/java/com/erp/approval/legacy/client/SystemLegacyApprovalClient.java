package com.erp.approval.legacy.client;

import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import com.erp.approval.api.domain.LegacyApprovalDetail;
import com.erp.approval.api.domain.LegacyApprovalPage;
import com.erp.approval.api.domain.LegacyApprovalTemplateSummary;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.constant.ServiceNameConstants;
import com.erp.common.core.domain.R;

@FeignClient(contextId = "systemLegacyApprovalClient",
        value = ServiceNameConstants.SYSTEM_SERVICE)
public interface SystemLegacyApprovalClient
{
    @GetMapping("/inner/approval/legacy/templates")
    R<List<LegacyApprovalTemplateSummary>> templates(
            @RequestHeader(SecurityConstants.FROM_SOURCE) String source);

    @GetMapping("/inner/approval/legacy/instances")
    R<LegacyApprovalPage> instances(
            @RequestParam(value = "businessCode", required = false) String businessCode,
            @RequestParam(value = "businessId", required = false) String businessId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam("pageNum") Integer pageNum,
            @RequestParam("pageSize") Integer pageSize,
            @RequestHeader(SecurityConstants.FROM_SOURCE) String source);

    @GetMapping("/inner/approval/legacy/instances/{businessCode}/{legacyInstanceId}")
    R<LegacyApprovalDetail> detail(
            @PathVariable("businessCode") String businessCode,
            @PathVariable("legacyInstanceId") Long legacyInstanceId,
            @RequestHeader(SecurityConstants.FROM_SOURCE) String source);
}

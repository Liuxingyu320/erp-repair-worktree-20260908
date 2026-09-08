package com.erp.approval.legacy;

import java.util.List;
import java.util.Set;
import com.erp.approval.api.domain.LegacyApprovalDetail;
import com.erp.approval.api.domain.LegacyApprovalPage;
import com.erp.approval.api.domain.LegacyApprovalQuery;
import com.erp.approval.api.domain.LegacyApprovalTemplateSummary;

/** Read-only adapter contract for approval engines being phased out. */
public interface LegacyApprovalBridge
{
    Set<String> businessCodes();
    List<LegacyApprovalTemplateSummary> templates();
    LegacyApprovalPage instances(LegacyApprovalQuery query);
    LegacyApprovalDetail detail(String businessCode, Long legacyInstanceId);
}

package com.erp.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.approval.api.domain.LegacyApprovalInstanceSummary;
import com.erp.approval.api.domain.LegacyApprovalQuery;
import com.erp.approval.api.domain.LegacyApprovalTaskSummary;

/** Read-only projection of pre-unified health-certificate reviews. */
public interface HrHealthCertificateLegacyApprovalMapper
{
    long countInstances(@Param("query") LegacyApprovalQuery query);

    List<LegacyApprovalInstanceSummary> selectInstances(
            @Param("query") LegacyApprovalQuery query,
            @Param("offset") int offset, @Param("limit") int limit);

    LegacyApprovalInstanceSummary selectInstance(Long certificateId);

    List<LegacyApprovalTaskSummary> selectTasks(Long certificateId);
}

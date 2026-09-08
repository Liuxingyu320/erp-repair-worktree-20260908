package com.erp.inventory.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.approval.api.domain.LegacyApprovalInstanceSummary;
import com.erp.approval.api.domain.LegacyApprovalQuery;
import com.erp.approval.api.domain.LegacyApprovalTaskSummary;

public interface InvLegacyApprovalMapper
{
    long countInstances(@Param("query") LegacyApprovalQuery query);

    List<LegacyApprovalInstanceSummary> selectInstances(
            @Param("query") LegacyApprovalQuery query,
            @Param("offset") int offset, @Param("limit") int limit);

    LegacyApprovalInstanceSummary selectInstance(
            @Param("businessCode") String businessCode,
            @Param("legacyInstanceId") Long legacyInstanceId);

    List<LegacyApprovalTaskSummary> selectTasks(
            @Param("businessCode") String businessCode,
            @Param("legacyInstanceId") Long legacyInstanceId);
}

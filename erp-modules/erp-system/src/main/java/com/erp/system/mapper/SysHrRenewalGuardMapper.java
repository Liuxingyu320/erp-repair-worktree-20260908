package com.erp.system.mapper;

import org.apache.ibatis.annotations.Param;
import com.erp.oa.api.domain.HrRenewalGuard;

/** System侧续签任务权威门闩持久化。 */
public interface SysHrRenewalGuardMapper
{
    HrRenewalGuard selectCurrent(@Param("employeeId") Long employeeId,
            @Param("scenario") String scenario);

    int insertIdle(@Param("employeeId") Long employeeId,
            @Param("scenario") String scenario);

    HrRenewalGuard selectForUpdate(@Param("employeeId") Long employeeId,
            @Param("scenario") String scenario);

    int reserve(@Param("employeeId") Long employeeId,
            @Param("scenario") String scenario,
            @Param("actionId") Long actionId,
            @Param("expectedVersion") Long expectedVersion);
}

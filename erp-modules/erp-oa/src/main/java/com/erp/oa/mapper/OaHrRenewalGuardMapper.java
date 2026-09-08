package com.erp.oa.mapper;

import org.apache.ibatis.annotations.Param;
import com.erp.oa.api.domain.HrRenewalGuard;

/** OA侧续签门闩接管与终态释放。 */
public interface OaHrRenewalGuardMapper
{
    HrRenewalGuard selectForUpdate(@Param("employeeId") Long employeeId,
            @Param("scenario") String scenario);

    int activate(@Param("employeeId") Long employeeId,
            @Param("scenario") String scenario,
            @Param("actionId") Long actionId,
            @Param("taskId") Long taskId,
            @Param("expectedVersion") Long expectedVersion);

    int activateReplacement(@Param("employeeId") Long employeeId,
            @Param("scenario") String scenario,
            @Param("actionId") Long actionId,
            @Param("sourceTerminalTaskId") Long sourceTerminalTaskId,
            @Param("replacementTaskId") Long replacementTaskId,
            @Param("expectedVersion") Long expectedVersion);

    int reserveIdleForEarliestRecoverableAction(@Param("employeeId") Long employeeId,
            @Param("scenario") String scenario,
            @Param("actionId") Long actionId,
            @Param("expectedVersion") Long expectedVersion);

    int rebindActive(@Param("employeeId") Long employeeId,
            @Param("scenario") String scenario,
            @Param("expectedActionId") Long expectedActionId,
            @Param("expectedTaskId") Long expectedTaskId,
            @Param("newActionId") Long newActionId,
            @Param("newTaskId") Long newTaskId);

    int release(@Param("employeeId") Long employeeId,
            @Param("scenario") String scenario,
            @Param("actionId") Long actionId,
            @Param("taskId") Long taskId);

    int releaseReserved(@Param("employeeId") Long employeeId,
            @Param("scenario") String scenario,
            @Param("actionId") Long actionId,
            @Param("expectedVersion") Long expectedVersion);

    int releaseByTaskForHardDelete(@Param("employeeId") Long employeeId,
            @Param("scenario") String scenario,
            @Param("taskId") Long taskId);
}

package com.erp.system.mapper;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.system.domain.SysHrLifecycleAction;

/**
 * 人事生命周期动作数据层。
 */
public interface SysHrLifecycleActionMapper
{
    com.erp.system.domain.vo.HrEmployeeLifecycleContextVo selectLifecycleContext(@Param("employeeId") Long employeeId);

    List<com.erp.system.domain.vo.HrEmployeeLifecycleContextVo.History> selectLifecycleHistory(
            @Param("employeeId") Long employeeId, @Param("scenario") String scenario);

    List<String> selectRenewalCycleTypes(@Param("employeeId") Long employeeId,
            @Param("cycleKey") String cycleKey);

    int countUnfinishedRenewal(@Param("employeeId") Long employeeId);

    int insertAction(SysHrLifecycleAction action);

    SysHrLifecycleAction selectById(@Param("actionId") Long actionId);

    SysHrLifecycleAction selectByRequestId(@Param("requestId") String requestId);

    SysHrLifecycleAction selectByRequestIdForUpdate(@Param("requestId") String requestId);

    /** 按不可变追加历史读取一个合同续签周期的所有动作。 */
    List<SysHrLifecycleAction> selectRenewalCycleActionsForUpdate(
            @Param("employeeId") Long employeeId,
            @Param("sourceBusinessId") String sourceBusinessId);

    List<SysHrLifecycleAction> selectTransferActionsForUpdate(
            @Param("employeeId") Long employeeId,
            @Param("effectiveDate") LocalDate effectiveDate);

    List<SysHrLifecycleAction> selectOffboardingActionsForUpdate(
            @Param("employeeId") Long employeeId,
            @Param("effectiveDate") LocalDate effectiveDate);

    List<SysHrLifecycleAction> selectConfirmedActionsWithoutOutbox(
            @Param("limit") int limit);
}

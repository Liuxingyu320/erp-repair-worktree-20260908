package com.erp.system.service;

import java.time.LocalDate;
import com.erp.system.domain.dto.HrLifecycleOnboardingConfirmRequest;
import com.erp.system.domain.dto.HrRenewalDecisionRequest;
import com.erp.system.domain.dto.HrRegularizationRequest;
import com.erp.system.domain.dto.HrEmployeeTransferRequest;
import com.erp.system.domain.dto.HrOffboardingConfirmRequest;

/** HR生命周期业务动作入口。 */
public interface IHrLifecycleService
{
    com.erp.system.domain.vo.HrEmployeeLifecycleContextVo lifecycleContext(Long employeeId,
            String scenario, Long operatorUserId);

    Long confirmOnboarding(Long employeeId, HrLifecycleOnboardingConfirmRequest request,
            Long operatorUserId, String operatorName, boolean operatorAdmin,
            String operatorIp, String operatorUserAgent);

    /** 为一个临近到期的现合同追加幂等系统续签决策动作。 */
    Long createRenewalDecision(Long employeeId, LocalDate windowStart, LocalDate windowEnd);

    Long confirmRenewal(Long employeeId, HrRenewalDecisionRequest request,
            Long operatorUserId, String operatorName, boolean operatorAdmin,
            String operatorIp, String operatorUserAgent);

    /** 具备相应角色权限的人员确认一名试用员工转正。 */
    Long confirmRegularization(Long employeeId, HrRegularizationRequest request,
            Long operatorUserId, String operatorName, boolean operatorAdmin,
            String operatorIp, String operatorUserAgent);

    /** 具备相应角色权限的人员确认员工调岗；日期权威判断使用上海业务自然日。 */
    Long confirmTransfer(Long employeeId, HrEmployeeTransferRequest request,
            Long operatorUserId, String operatorName, boolean operatorAdmin,
            String operatorIp, String operatorUserAgent);

    LocalDate transferBusinessDate();

    /** 具备相应角色权限的人员确认员工离职；由实现保证原子停用账号和写出业务事件。 */
    Long confirmOffboarding(Long employeeId, HrOffboardingConfirmRequest request,
            Long operatorUserId, String operatorName, boolean operatorAdmin,
            String operatorIp, String operatorUserAgent);

    /** 离职入口使用与调岗相同的服务端上海业务自然日。 */
    LocalDate offboardingBusinessDate();
}

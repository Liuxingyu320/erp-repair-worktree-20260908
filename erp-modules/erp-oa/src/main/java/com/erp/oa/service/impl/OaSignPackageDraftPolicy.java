package com.erp.oa.service.impl;

import java.util.Objects;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;
import com.erp.oa.constant.OaSignPackageStatus;
import com.erp.oa.constant.OaSignScenarioCodes;
import com.erp.oa.constant.OaSignTaskStatus;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignTask;

/**
 * Builds and validates draft-package updates without owning access, persistence or file cleanup.
 */
final class OaSignPackageDraftPolicy
{
    private static final String EMERGENCY_REASON_PREFIX = "非生命周期任务来源：";

    OaSignPackage buildDraftPackageUpdate(Long packageId, OaSignPackage request,
            OaSignPackage existing, OaSignTask task, Long currentHrUserId)
    {
        Long taskPlanReference = taskPlanReference(task);
        OaSignPackage update = new OaSignPackage();
        update.setPackageId(packageId);
        update.setEmployeeId(task == null ? existing.getEmployeeId() : task.getEmployeeId());
        update.setShopDeptId(task == null || task.getShopDeptId() == null
                ? existing.getShopDeptId() : task.getShopDeptId());
        update.setShopDeptName(existing.getShopDeptName());
        update.setLegalEntityIdSnapshot(task == null
                ? request.getLegalEntityIdSnapshot() : existing.getLegalEntityIdSnapshot());
        update.setLegalEntityCodeSnapshot(task == null
                ? request.getLegalEntityCodeSnapshot() : existing.getLegalEntityCodeSnapshot());
        update.setLegalEntityNameSnapshot(task == null
                ? request.getLegalEntityNameSnapshot() : existing.getLegalEntityNameSnapshot());
        update.setSourcePlanId(task == null ? existing.getSourcePlanId() : taskPlanReference);
        update.setSourcePlanName(task == null
                || Objects.equals(existing.getSourcePlanId(), taskPlanReference)
                        ? existing.getSourcePlanName() : null);
        update.setTaskId(task == null ? existing.getTaskId() : task.getTaskId());
        update.setPlanVersionId(task == null ? existing.getPlanVersionId() : taskPlanReference);
        update.setConfirmStatus(task == null
                ? existing.getConfirmStatus() : OaSignTaskStatus.NEEDS_DATA.name());
        update.setStatus(OaSignPackageStatus.DRAFT);
        update.setEmployeeNameSnapshot(request.getEmployeeNameSnapshot());
        update.setEmployeePhoneSnapshot(request.getEmployeePhoneSnapshot());
        update.setEmployeeIdCardSnapshot(request.getEmployeeIdCardSnapshot());
        update.setEmployeeAddressSnapshot(request.getEmployeeAddressSnapshot());
        update.setDeptIdSnapshot(request.getDeptIdSnapshot());
        update.setDeptNameSnapshot(request.getDeptNameSnapshot());
        update.setScenario(task == null
                ? OaSignScenarioCodes.normalizePackageScenario(request.getScenario())
                : OaSignScenarioCodes.normalizePackageScenario(task.getScenario()));
        update.setEmploymentType(request.getEmploymentType());
        update.setContractTermCodeSnapshot(task == null
                ? request.getContractTermCodeSnapshot()
                : existing.getContractTermCodeSnapshot());
        update.setSocialType(request.getSocialType());
        update.setServicePersonType(request.getServicePersonType());
        update.setInsuranceType(request.getInsuranceType());
        update.setPostNameSnapshot(request.getPostNameSnapshot());
        update.setPostLevelSnapshot(request.getPostLevelSnapshot());
        update.setSalaryVersion(request.getSalaryVersion());
        update.setEntryDate(request.getEntryDate());
        update.setContractStartDate(request.getContractStartDate());
        update.setContractEndDate(request.getContractEndDate());
        update.setPreviousContractEndDate(task == null
                ? request.getPreviousContractEndDate()
                : existing.getPreviousContractEndDate());
        update.setPreviousEmploymentType(task == null
                ? request.getPreviousEmploymentType()
                : existing.getPreviousEmploymentType());
        update.setPreviousLegalEntityIdSnapshot(task == null
                ? request.getPreviousLegalEntityIdSnapshot()
                : existing.getPreviousLegalEntityIdSnapshot());
        update.setPreviousRenewalCount(task == null
                ? request.getPreviousRenewalCount() : existing.getPreviousRenewalCount());
        update.setRenewalCount(task == null
                ? request.getRenewalCount() : existing.getRenewalCount());
        update.setProbationStartDate(request.getProbationStartDate());
        update.setProbationEndDate(request.getProbationEndDate());
        update.setActualRegularizationDate(task != null
                && OaSignScenarioCodes.REGULARIZE.equalsIgnoreCase(task.getScenario())
                        ? existing.getActualRegularizationDate()
                        : request.getActualRegularizationDate());
        update.setWorkStartDate(request.getWorkStartDate());
        update.setWorkEndDate(request.getWorkEndDate());
        update.setLeaveDate(request.getLeaveDate());
        update.setLeaveReason(request.getLeaveReason());
        update.setBaseSalary(request.getBaseSalary());
        update.setPostSalary(request.getPostSalary());
        update.setFieldAllowance(request.getFieldAllowance());
        update.setPerformanceSalary(request.getPerformanceSalary());
        update.setSalaryTotal(request.getSalaryTotal());
        update.setRemark(resolveDraftRemark(existing, request.getRemark()));
        update.getParams().put("assignedHrUserId",
                task == null ? currentHrUserId : task.getAssignedHrUserId());
        return update;
    }

    /**
     * The emergency source reason is audit evidence, rather than an editable business remark.
     */
    String resolveDraftRemark(OaSignPackage existing, String requestedRemark)
    {
        String existingRemark = StringUtils.trim(existing.getRemark());
        if (existing.getTaskId() != null || StringUtils.isBlank(existingRemark)
                || !existingRemark.startsWith(EMERGENCY_REASON_PREFIX))
        {
            return requestedRemark;
        }
        String existingReason = StringUtils.trim(
                existingRemark.substring(EMERGENCY_REASON_PREFIX.length()));
        if (StringUtils.isBlank(existingReason))
        {
            throw new ServiceException("应急建包来源原因不完整，请联系管理员处理");
        }
        String canonicalRemark = EMERGENCY_REASON_PREFIX + existingReason;
        String normalizedRequest = StringUtils.trim(requestedRemark);
        if (StringUtils.isBlank(normalizedRequest))
        {
            return canonicalRemark;
        }
        if (!normalizedRequest.startsWith(EMERGENCY_REASON_PREFIX))
        {
            throw new ServiceException("应急建包来源标识和原因不可删除");
        }
        String requestedReason = StringUtils.trim(
                normalizedRequest.substring(EMERGENCY_REASON_PREFIX.length()));
        if (!existingReason.equals(requestedReason))
        {
            throw new ServiceException("应急建包来源原因不可修改");
        }
        return canonicalRemark;
    }

    void validateDraftPackage(OaSignPackage signPackage)
    {
        if (StringUtils.isBlank(signPackage.getEmployeeNameSnapshot()))
        {
            throw new ServiceException("员工姓名不能为空");
        }
        if (StringUtils.isBlank(signPackage.getEmployeePhoneSnapshot()))
        {
            throw new ServiceException("手机号不能为空");
        }
        if (StringUtils.isBlank(signPackage.getEmployeeIdCardSnapshot()))
        {
            throw new ServiceException("身份证号不能为空");
        }
        if (StringUtils.isBlank(signPackage.getScenario()))
        {
            throw new ServiceException("签约场景不能为空");
        }
    }

    private Long taskPlanReference(OaSignTask task)
    {
        return task == null ? null : task.getPlanVersionId();
    }
}

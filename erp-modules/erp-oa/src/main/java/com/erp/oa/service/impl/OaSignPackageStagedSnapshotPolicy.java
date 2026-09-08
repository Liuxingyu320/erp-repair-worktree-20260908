package com.erp.oa.service.impl;

import java.util.Objects;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;
import com.erp.oa.constant.OaSignSigningSequence;
import com.erp.oa.domain.OaSignPackage;

/**
 * Owns the immutable-fact boundary when a signature-first package moves from
 * the employee signature stage to the HR company/seal stage.
 *
 * <p>The policy only compares and copies in-memory snapshots. Database writes,
 * file generation, evidence persistence and transaction ordering remain in the
 * package service.</p>
 */
final class OaSignPackageStagedSnapshotPolicy
{
    void requireSameStagedPackageIdentity(OaSignPackage existing,
            OaSignPackage finalized)
    {
        if (finalized == null
                || !Objects.equals(existing.getEmployeeId(), finalized.getEmployeeId())
                || !Objects.equals(existing.getShopDeptId(), finalized.getShopDeptId())
                || !Objects.equals(existing.getPlanVersionId(), finalized.getPlanVersionId())
                || !OaSignSigningSequence.SIGNATURE_FIRST.equals(
                        OaSignSigningSequence.normalize(finalized.getSigningSequence()))
                || finalized.getLegalEntityIdSnapshot() == null
                || finalized.getSealIdSnapshot() == null)
        {
            throw new ServiceException("最终合同事实与原签约包身份不一致");
        }
    }

    void requireSameStagedCompanyDecision(OaSignPackage existing,
            OaSignPackage finalized)
    {
        requireSameStagedPackageIdentity(existing, finalized);
        if (!Objects.equals(existing.getLegalEntityIdSnapshot(),
                    finalized.getLegalEntityIdSnapshot())
                || !Objects.equals(existing.getSealIdSnapshot(), finalized.getSealIdSnapshot())
                || !Objects.equals(StringUtils.trim(existing.getLegalEntityNameSnapshot()),
                        StringUtils.trim(finalized.getLegalEntityNameSnapshot()))
                || !sameHash(existing.getSealImageHashSnapshot(),
                        finalized.getSealImageHashSnapshot()))
        {
            throw new ServiceException("最终合同已按另一公司或印章生成，不能覆盖");
        }
    }

    void applyStagedFinalizedSnapshot(OaSignPackage target, OaSignPackage source)
    {
        target.setEmployeeNameSnapshot(source.getEmployeeNameSnapshot());
        target.setEmployeePhoneSnapshot(source.getEmployeePhoneSnapshot());
        target.setEmployeeIdCardSnapshot(source.getEmployeeIdCardSnapshot());
        target.setEmployeeAddressSnapshot(source.getEmployeeAddressSnapshot());
        target.setDeptIdSnapshot(source.getDeptIdSnapshot());
        target.setDeptNameSnapshot(source.getDeptNameSnapshot());
        target.setShopDeptName(source.getShopDeptName());
        target.setLegalEntityIdSnapshot(source.getLegalEntityIdSnapshot());
        target.setLegalEntityCodeSnapshot(source.getLegalEntityCodeSnapshot());
        target.setLegalEntityNameSnapshot(source.getLegalEntityNameSnapshot());
        target.setLegalEntitySourceDeptId(source.getLegalEntitySourceDeptId());
        target.setLegalEntityResolveMode(source.getLegalEntityResolveMode());
        target.setLegalEntityCreditCodeSnapshot(source.getLegalEntityCreditCodeSnapshot());
        target.setLegalEntityAddressSnapshot(source.getLegalEntityAddressSnapshot());
        target.setLegalRepresentativeSnapshot(source.getLegalRepresentativeSnapshot());
        target.setLegalEntityPhoneSnapshot(source.getLegalEntityPhoneSnapshot());
        target.setLegalEntityOverrideReason(source.getLegalEntityOverrideReason());
        target.setSealIdSnapshot(source.getSealIdSnapshot());
        target.setSealNameSnapshot(source.getSealNameSnapshot());
        target.setSealImageUrlSnapshot(source.getSealImageUrlSnapshot());
        target.setSealImageHashSnapshot(source.getSealImageHashSnapshot());
        target.setRecommendedCompanySnapshot(source.getRecommendedCompanySnapshot());
        target.setRecommendedLegalRepresentativeSnapshot(
                source.getRecommendedLegalRepresentativeSnapshot());
        target.setRecommendedRegisteredAddressSnapshot(
                source.getRecommendedRegisteredAddressSnapshot());
        target.setScenario(source.getScenario());
        target.setEmploymentType(source.getEmploymentType());
        target.setContractTermCodeSnapshot(source.getContractTermCodeSnapshot());
        target.setSocialType(source.getSocialType());
        target.setServicePersonType(source.getServicePersonType());
        target.setInsuranceType(source.getInsuranceType());
        target.setPostNameSnapshot(source.getPostNameSnapshot());
        target.setPostLevelSnapshot(source.getPostLevelSnapshot());
        target.setSalaryVersion(source.getSalaryVersion());
        target.setEntryDate(source.getEntryDate());
        target.setContractStartDate(source.getContractStartDate());
        target.setContractEndDate(source.getContractEndDate());
        target.setPreviousContractEndDate(source.getPreviousContractEndDate());
        target.setPreviousEmploymentType(source.getPreviousEmploymentType());
        target.setPreviousLegalEntityIdSnapshot(source.getPreviousLegalEntityIdSnapshot());
        target.setPreviousRenewalCount(source.getPreviousRenewalCount());
        target.setRenewalCount(source.getRenewalCount());
        target.setProbationStartDate(source.getProbationStartDate());
        target.setProbationEndDate(source.getProbationEndDate());
        target.setActualRegularizationDate(source.getActualRegularizationDate());
        target.setTransferEffectiveDate(source.getTransferEffectiveDate());
        target.setHistoricalSupplement(source.getHistoricalSupplement());
        target.setBeforeDeptNameSnapshot(source.getBeforeDeptNameSnapshot());
        target.setBeforePostNameSnapshot(source.getBeforePostNameSnapshot());
        target.setWorkStartDate(source.getWorkStartDate());
        target.setStudentStatusSnapshot(source.getStudentStatusSnapshot());
        target.setRetirementStatusSnapshot(source.getRetirementStatusSnapshot());
        target.setIncomeStartYearMonth(source.getIncomeStartYearMonth());
        target.setWorkEndDate(source.getWorkEndDate());
        target.setLeaveDate(source.getLeaveDate());
        target.setLeaveReason(source.getLeaveReason());
        target.setOffboardingType(source.getOffboardingType());
        target.setSalarySettlementStatus(source.getSalarySettlementStatus());
        target.setAssetHandoverStatus(source.getAssetHandoverStatus());
        target.setNonCompeteDecision(source.getNonCompeteDecision());
        target.setCompensationAmount(source.getCompensationAmount());
        target.setCompensationNote(source.getCompensationNote());
        target.setBaseSalary(source.getBaseSalary());
        target.setPostSalary(source.getPostSalary());
        target.setFieldAllowance(source.getFieldAllowance());
        target.setPerformanceSalary(source.getPerformanceSalary());
        target.setSalaryTotal(source.getSalaryTotal());
    }

    private boolean sameHash(String left, String right)
    {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }
}

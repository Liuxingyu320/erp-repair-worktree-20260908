package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.constant.OaSignPackageStatus;
import com.erp.oa.domain.OaSignPackage;

class OaSignPackageStagedSnapshotPolicyTest
{
    private final OaSignPackageStagedSnapshotPolicy policy =
            new OaSignPackageStagedSnapshotPolicy();

    @Test
    void compatibleIdentityRequiresTheOriginalEmployeeShopPlanAndExplicitCompanyDecision()
    {
        OaSignPackage existing = existingPackage();
        OaSignPackage finalized = compatibleFinalizedPackage();
        finalized.setSigningSequence(" signature_first ");

        assertThatCode(() -> policy.requireSameStagedPackageIdentity(
                existing, finalized)).doesNotThrowAnyException();

        OaSignPackage changedEmployee = compatibleFinalizedPackage();
        changedEmployee.setEmployeeId(999L);
        assertThatThrownBy(() -> policy.requireSameStagedPackageIdentity(
                existing, changedEmployee))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("身份不一致");

        OaSignPackage missingSeal = compatibleFinalizedPackage();
        missingSeal.setSealIdSnapshot(null);
        assertThatThrownBy(() -> policy.requireSameStagedPackageIdentity(
                existing, missingSeal))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("身份不一致");
    }

    @Test
    void malformedSigningSequenceFailsClosed()
    {
        OaSignPackage finalized = compatibleFinalizedPackage();
        finalized.setSigningSequence("employee_first");

        assertThatThrownBy(() -> policy.requireSameStagedPackageIdentity(
                existingPackage(), finalized))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("签署顺序无效");
    }

    @Test
    void preparedCandidateOnlyAcceptsTheSameCompanySealNameAndSealImage()
    {
        OaSignPackage existing = existingPackage();
        existing.setLegalEntityIdSnapshot(31L);
        existing.setSealIdSnapshot(42L);
        existing.setLegalEntityNameSnapshot(" 冻结公司 ");
        existing.setSealImageHashSnapshot("ABCDEF");
        OaSignPackage finalized = compatibleFinalizedPackage();
        finalized.setLegalEntityIdSnapshot(31L);
        finalized.setSealIdSnapshot(42L);
        finalized.setLegalEntityNameSnapshot("冻结公司");
        finalized.setSealImageHashSnapshot("abcdef");

        assertThatCode(() -> policy.requireSameStagedCompanyDecision(
                existing, finalized)).doesNotThrowAnyException();

        finalized.setSealIdSnapshot(43L);
        assertThatThrownBy(() -> policy.requireSameStagedCompanyDecision(
                existing, finalized))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("另一公司或印章");
    }

    @Test
    void finalizedFactsAreCopiedWithoutRewritingPackageLifecycleOrSignatureEvidence()
    {
        OaSignPackage target = existingPackage();
        target.setPackageId(90L);
        target.setTaskId(9L);
        target.setStatus(OaSignPackageStatus.PENDING_COMPANY);
        target.setVersion(7L);
        target.setDocumentVersion("SP-90-V1");
        target.setSignatureSampleHash("sample-hash");

        OaSignPackage source = compatibleFinalizedPackage();
        source.setEmployeeNameSnapshot("张三");
        source.setEmployeePhoneSnapshot("13800000000");
        source.setDeptNameSnapshot("营运部");
        source.setLegalEntityCodeSnapshot("LEGAL-31");
        source.setLegalEntityNameSnapshot("冻结公司");
        source.setSealNameSnapshot("合同章");
        source.setRecommendedCompanySnapshot("推荐公司");
        source.setScenario("renew");
        source.setEmploymentType("劳动合同");
        source.setContractTermCodeSnapshot("FIXED_TERM");
        source.setPostNameSnapshot("店长");
        source.setEntryDate("2026-07-01");
        source.setContractEndDate("2029-06-30");
        source.setPreviousRenewalCount(2);
        source.setRenewalCount(3);
        source.setActualRegularizationDate("2026-07-15");
        source.setTransferEffectiveDate("2026-08-01");
        source.setHistoricalSupplement(true);
        source.setLeaveDate("2029-06-30");
        source.setOffboardingType("NORMAL");
        source.setCompensationAmount(new BigDecimal("1234.50"));
        source.setBaseSalary(new BigDecimal("5000.00"));
        source.setSalaryTotal(new BigDecimal("6800.00"));

        policy.applyStagedFinalizedSnapshot(target, source);

        assertThat(target.getEmployeeNameSnapshot()).isEqualTo("张三");
        assertThat(target.getEmployeePhoneSnapshot()).isEqualTo("13800000000");
        assertThat(target.getDeptNameSnapshot()).isEqualTo("营运部");
        assertThat(target.getLegalEntityCodeSnapshot()).isEqualTo("LEGAL-31");
        assertThat(target.getLegalEntityNameSnapshot()).isEqualTo("冻结公司");
        assertThat(target.getSealNameSnapshot()).isEqualTo("合同章");
        assertThat(target.getRecommendedCompanySnapshot()).isEqualTo("推荐公司");
        assertThat(target.getScenario()).isEqualTo("renew");
        assertThat(target.getEmploymentType()).isEqualTo("劳动合同");
        assertThat(target.getContractTermCodeSnapshot()).isEqualTo("FIXED_TERM");
        assertThat(target.getPostNameSnapshot()).isEqualTo("店长");
        assertThat(target.getEntryDate()).isEqualTo("2026-07-01");
        assertThat(target.getContractEndDate()).isEqualTo("2029-06-30");
        assertThat(target.getPreviousRenewalCount()).isEqualTo(2);
        assertThat(target.getRenewalCount()).isEqualTo(3);
        assertThat(target.getActualRegularizationDate()).isEqualTo("2026-07-15");
        assertThat(target.getTransferEffectiveDate()).isEqualTo("2026-08-01");
        assertThat(target.getHistoricalSupplement()).isTrue();
        assertThat(target.getLeaveDate()).isEqualTo("2029-06-30");
        assertThat(target.getOffboardingType()).isEqualTo("NORMAL");
        assertThat(target.getCompensationAmount()).isEqualByComparingTo("1234.50");
        assertThat(target.getBaseSalary()).isEqualByComparingTo("5000.00");
        assertThat(target.getSalaryTotal()).isEqualByComparingTo("6800.00");

        assertThat(target.getPackageId()).isEqualTo(90L);
        assertThat(target.getTaskId()).isEqualTo(9L);
        assertThat(target.getStatus()).isEqualTo(OaSignPackageStatus.PENDING_COMPANY);
        assertThat(target.getVersion()).isEqualTo(7L);
        assertThat(target.getDocumentVersion()).isEqualTo("SP-90-V1");
        assertThat(target.getSignatureSampleHash()).isEqualTo("sample-hash");
    }

    private OaSignPackage existingPackage()
    {
        OaSignPackage value = new OaSignPackage();
        value.setEmployeeId(201L);
        value.setShopDeptId(1171L);
        value.setPlanVersionId(66L);
        return value;
    }

    private OaSignPackage compatibleFinalizedPackage()
    {
        OaSignPackage value = existingPackage();
        value.setSigningSequence("SIGNATURE_FIRST");
        value.setLegalEntityIdSnapshot(31L);
        value.setSealIdSnapshot(42L);
        return value;
    }
}

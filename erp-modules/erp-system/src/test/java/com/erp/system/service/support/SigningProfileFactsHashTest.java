package com.erp.system.service.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import com.erp.system.api.domain.SignCandidateUser;
import com.erp.system.api.domain.SysUserProfile;

class SigningProfileFactsHashTest
{
    @Test
    void candidateAndProfileUseOneStableCanonicalHash()
    {
        SignCandidateUser candidate = new SignCandidateUser();
        candidate.setCurrentAddress("杭州市西湖区测试路1号");
        candidate.setStudentStatus("NON_STUDENT");
        candidate.setRetirementStatus("NOT_RETIRED");
        candidate.setIncomeStartYearMonth("2024-06");

        SysUserProfile profile = new SysUserProfile();
        profile.setCurrentAddress(candidate.getCurrentAddress());
        profile.setStudentStatus(candidate.getStudentStatus());
        profile.setRetirementStatus(candidate.getRetirementStatus());
        profile.setIncomeStartYearMonth(candidate.getIncomeStartYearMonth());

        assertThat(SigningProfileFactsHash.of(candidate))
                .isEqualTo(SigningProfileFactsHash.of(profile))
                .isEqualTo("0bf8e04b07db8db95e0e7ff914efd86b3b073be3d20edcf7bdb651a8cf1906e2");
    }
}

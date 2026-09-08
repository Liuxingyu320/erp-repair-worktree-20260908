package com.erp.system.api.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.system.api.constant.SigningProfileCodes;

@DisplayName("签约档案机器字典")
class SigningProfileCodesTest
{
    @Test
    @DisplayName("合同期限和社保仅接受稳定机器代码")
    void shouldExposeStableSigningProfileCodes()
    {
        assertThat(SigningProfileCodes.CONTRACT_TYPES)
                .containsExactlyInAnyOrder("LABOR_CONTRACT", "SERVICE_CONTRACT",
                        "INTERNSHIP_AGREEMENT", "OUTSOURCING_CONTRACT");
        assertThat(SigningProfileCodes.CONTRACT_TERMS)
                .containsExactlyInAnyOrder("FIXED_TERM", "OPEN_ENDED");
        assertThat(SigningProfileCodes.SOCIAL_TYPES)
                .containsExactlyInAnyOrder("SOCIAL_INSURED", "SOCIAL_UNINSURED",
                        "DISPATCHED", "PENDING_CONFIRMATION");

        assertThat(SigningProfileCodes.isKnownContractType("LABOR_CONTRACT")).isTrue();
        assertThat(SigningProfileCodes.isKnownContractType("劳动合同")).isFalse();
        assertThat(SigningProfileCodes.isKnownContractTerm("FIXED_TERM")).isTrue();
        assertThat(SigningProfileCodes.isKnownContractTerm("3年")).isFalse();
        assertThat(SigningProfileCodes.isKnownSocialType("SOCIAL_INSURED")).isTrue();
        assertThat(SigningProfileCodes.isKnownSocialType("有社保")).isFalse();
    }
}

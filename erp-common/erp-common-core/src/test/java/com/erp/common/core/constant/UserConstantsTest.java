package com.erp.common.core.constant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserConstantsTest
{
    @Test
    void userIdOneShouldBeAdminByDefault()
    {
        assertThat(UserConstants.isAdmin(1L)).isTrue();
        assertThat(UserConstants.isAdmin(2L)).isFalse();
        assertThat(UserConstants.isAdmin(null)).isFalse();
    }

    @Test
    void passwordMinimumLengthShouldBeAtLeastEightCharacters()
    {
        assertThat(UserConstants.PASSWORD_MIN_LENGTH).isGreaterThanOrEqualTo(8);
    }

    @Test
    void passwordPolicyShouldRejectPasswordsThatDoNotMatchConfiguredCharacterType()
    {
        assertThat(UserConstants.getPasswordPolicyError("abcdefgh", "3"))
                .isEqualTo("密码必须同时包含字母和数字");
        assertThat(UserConstants.getPasswordPolicyError("abc12345", "3")).isNull();
        assertThat(UserConstants.getPasswordPolicyError("abc12345", "4"))
                .isEqualTo("密码必须同时包含字母、数字和特殊字符（~!@#$%^&*()-=_+）");
        assertThat(UserConstants.getPasswordPolicyError("abc12345!", "4")).isNull();
        assertThat(UserConstants.getPasswordPolicyError("abc<1234", "0"))
                .isEqualTo("密码不能包含非法字符：< > \" ' \\ |");
    }
}

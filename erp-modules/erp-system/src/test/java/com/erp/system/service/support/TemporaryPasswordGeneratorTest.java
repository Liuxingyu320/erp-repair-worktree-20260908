package com.erp.system.service.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.erp.common.core.constant.UserConstants;
import com.erp.common.core.exception.ServiceException;

class TemporaryPasswordGeneratorTest
{
    private final TemporaryPasswordGenerator generator = new TemporaryPasswordGenerator();

    @Test
    void newUsersReceiveTheFixedDefaultPassword()
    {
        assertThat(generator.defaultNewUserPassword()).isEqualTo("123456");
    }

    @Test
    void generatedPasswordsAreUniquePolicyCompliantAndAtLeastEightyBits()
    {
        for (String policy : new String[] { "0", "2", "3", "4" })
        {
            Set<String> generated = new HashSet<>();
            for (int index = 0; index < 200; index++)
            {
                String password = generator.generate(policy);
                assertThat(password).hasSize(UserConstants.PASSWORD_MAX_LENGTH);
                assertThat(password.substring(0, 1)).matches("[A-Za-z]");
                assertThat(UserConstants.isPasswordPolicyValid(password, policy)).isTrue();
                generated.add(password);
            }
            assertThat(generated).hasSize(200);
            assertThat(generator.minimumEntropyBits(policy)).isGreaterThanOrEqualTo(80.0d);
        }
    }

    @Test
    void numericOnlyPolicyFailsClosedBecauseConfiguredLengthCannotReachEightyBits()
    {
        assertThat(generator.minimumEntropyBits("1")).isLessThan(80.0d);
        assertThatThrownBy(() -> generator.generate("1"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("80 bit");
    }
}

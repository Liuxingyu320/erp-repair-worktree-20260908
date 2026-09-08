package com.erp.system.service.credential;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.erp.common.core.constant.UserConstants;

class CredentialTemporaryPasswordGeneratorTest
{
    @Test
    void tenThousandGeneratedPasswordsMatchEverySupportedPolicy()
    {
        CredentialTemporaryPasswordGenerator generator = new CredentialTemporaryPasswordGenerator();
        Set<String> values = new HashSet<>();

        for (String policy : new String[] { "0", "1", "2", "3", "4" })
        {
            for (int index = 0; index < 2_000; index++)
            {
                String password = generator.generate(policy);
                assertThat(password).hasSize(CredentialTemporaryPasswordGenerator.PASSWORD_LENGTH);
                assertThat(Character.isLetterOrDigit(password.charAt(0))).isTrue();
                assertThat(UserConstants.getPasswordPolicyError(password, policy)).isNull();
                values.add(password);
            }
        }

        assertThat(values).hasSizeGreaterThan(9_900);
    }
}

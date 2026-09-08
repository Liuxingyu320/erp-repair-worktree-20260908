package com.erp.system.service.credential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.system.service.ISysConfigService;
import com.erp.system.service.credential.UserCredentialProvisioningService.ProvisionedCredential;

class UserCredentialProvisioningServiceTest
{
    @Test
    void generatedCredentialUsesCurrentPolicyAndStoresOnlyHash()
    {
        ISysConfigService configService = mock(ISysConfigService.class);
        when(configService.selectConfigByKey("sys.account.chrtype")).thenReturn("4");
        UserCredentialProvisioningService service = new UserCredentialProvisioningService(
                configService, new CredentialTemporaryPasswordGenerator());

        ProvisionedCredential credential = service.provision(null);

        assertThat(credential.isGenerated()).isTrue();
        assertThat(credential.getRawPassword()).hasSize(16);
        assertThat(credential.getEncodedPassword()).doesNotContain(credential.getRawPassword());
        assertThat(SecurityUtils.matchesPassword(credential.getRawPassword(), credential.getEncodedPassword())).isTrue();
    }

    @Test
    void manuallySuppliedCredentialIsValidatedAgainstSamePolicy()
    {
        ISysConfigService configService = mock(ISysConfigService.class);
        when(configService.selectConfigByKey("sys.account.chrtype")).thenReturn("4");
        UserCredentialProvisioningService service = new UserCredentialProvisioningService(
                configService, new CredentialTemporaryPasswordGenerator());

        assertThatThrownBy(() -> service.provision("letters123"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("特殊字符");

        ProvisionedCredential credential = service.provision("Letters123!");
        assertThat(credential.isGenerated()).isFalse();
        assertThat(SecurityUtils.matchesPassword("Letters123!", credential.getEncodedPassword())).isTrue();
    }
}

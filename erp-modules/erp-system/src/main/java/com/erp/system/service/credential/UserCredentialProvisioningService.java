package com.erp.system.service.credential;

import org.springframework.stereotype.Service;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.text.Convert;
import com.erp.common.core.utils.StringUtils;
import com.erp.common.core.constant.UserConstants;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.system.service.ISysConfigService;

/**
 * 统一创建、校验并加密用户一次性凭据。
 */
@Service
public class UserCredentialProvisioningService
{
    private final ISysConfigService configService;
    private final CredentialTemporaryPasswordGenerator passwordGenerator;

    public UserCredentialProvisioningService(ISysConfigService configService,
            CredentialTemporaryPasswordGenerator passwordGenerator)
    {
        this.configService = configService;
        this.passwordGenerator = passwordGenerator;
    }

    public ProvisionedCredential provision(String requestedPassword)
    {
        String policyType = currentPolicyType();
        boolean generated = StringUtils.isEmpty(requestedPassword);
        String rawPassword = generated ? passwordGenerator.generate(policyType) : requestedPassword;
        String validationError = UserConstants.getPasswordPolicyError(rawPassword, policyType);
        if (StringUtils.isNotEmpty(validationError))
        {
            throw new ServiceException(validationError);
        }
        return new ProvisionedCredential(rawPassword, SecurityUtils.encryptPassword(rawPassword), generated);
    }

    public String generateTemporaryPassword()
    {
        return passwordGenerator.generate(currentPolicyType());
    }

    private String currentPolicyType()
    {
        return Convert.toStr(configService.selectConfigByKey("sys.account.chrtype"), "0");
    }

    public static final class ProvisionedCredential
    {
        private final String rawPassword;
        private final String encodedPassword;
        private final boolean generated;

        private ProvisionedCredential(String rawPassword, String encodedPassword, boolean generated)
        {
            this.rawPassword = rawPassword;
            this.encodedPassword = encodedPassword;
            this.generated = generated;
        }

        public String getRawPassword()
        {
            return rawPassword;
        }

        public String getEncodedPassword()
        {
            return encodedPassword;
        }

        public boolean isGenerated()
        {
            return generated;
        }
    }
}

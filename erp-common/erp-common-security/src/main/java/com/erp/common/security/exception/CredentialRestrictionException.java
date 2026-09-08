package com.erp.common.security.exception;

/** Business-safe exception used for the forced credential change boundary. */
public class CredentialRestrictionException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    public static final String CHANGE_REQUIRED = "CREDENTIAL_CHANGE_REQUIRED";

    public static final String TEMPORARY_EXPIRED = "TEMPORARY_CREDENTIAL_EXPIRED";

    public static final String INVALID_STATE = "CREDENTIAL_STATE_INVALID";

    private final String businessCode;

    private final String credentialState;

    private final Long userId;

    public CredentialRestrictionException(String businessCode, String credentialState,
            Long userId, String message)
    {
        super(message);
        this.businessCode = businessCode;
        this.credentialState = credentialState;
        this.userId = userId;
    }

    public String getBusinessCode()
    {
        return businessCode;
    }

    public String getCredentialState()
    {
        return credentialState;
    }

    public Long getUserId()
    {
        return userId;
    }
}

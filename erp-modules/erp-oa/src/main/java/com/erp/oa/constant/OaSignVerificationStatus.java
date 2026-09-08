package com.erp.oa.constant;

/**
 * Business-facing result of verifying a signing package.
 */
public enum OaSignVerificationStatus
{
    VERIFIED,
    MISMATCH,
    FILE_MISSING,
    LEGACY_LIMITED
}

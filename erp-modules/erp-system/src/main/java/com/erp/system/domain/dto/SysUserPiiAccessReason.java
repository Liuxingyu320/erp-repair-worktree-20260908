package com.erp.system.domain.dto;

/** Controlled reasons for accessing personal data. Free-form reasons are forbidden. */
public enum SysUserPiiAccessReason
{
    BUSINESS_PROCESSING,
    LEGAL_AUDIT,
    DATA_SUBJECT_REQUEST,
    DATA_CORRECTION
}

package com.erp.system.api.constant;

import java.util.Set;

/**
 * Machine-readable dictionary codes used by the employee signing profile.
 */
public final class SigningProfileCodes {

    public static final String LABOR_CONTRACT = "LABOR_CONTRACT";
    public static final String SERVICE_CONTRACT = "SERVICE_CONTRACT";
    public static final String INTERNSHIP_AGREEMENT = "INTERNSHIP_AGREEMENT";
    public static final String OUTSOURCING_CONTRACT = "OUTSOURCING_CONTRACT";

    public static final String FIXED_TERM = "FIXED_TERM";
    public static final String OPEN_ENDED = "OPEN_ENDED";

    public static final String SOCIAL_INSURED = "SOCIAL_INSURED";
    public static final String SOCIAL_UNINSURED = "SOCIAL_UNINSURED";
    public static final String DISPATCHED = "DISPATCHED";
    public static final String PENDING_CONFIRMATION = "PENDING_CONFIRMATION";

    public static final Set<String> CONTRACT_TYPES = Set.of(
            LABOR_CONTRACT,
            SERVICE_CONTRACT,
            INTERNSHIP_AGREEMENT,
            OUTSOURCING_CONTRACT
    );

    public static final Set<String> CONTRACT_TERMS = Set.of(
            FIXED_TERM,
            OPEN_ENDED
    );

    public static final Set<String> SOCIAL_TYPES = Set.of(
            SOCIAL_INSURED,
            SOCIAL_UNINSURED,
            DISPATCHED,
            PENDING_CONFIRMATION
    );

    private SigningProfileCodes() {
    }

    public static boolean isKnownContractType(String value) {
        return value != null && CONTRACT_TYPES.contains(value);
    }

    public static boolean isKnownContractTerm(String value) {
        return value != null && CONTRACT_TERMS.contains(value);
    }

    public static boolean isKnownSocialType(String value) {
        return value != null && SOCIAL_TYPES.contains(value);
    }
}

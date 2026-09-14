package com.erp.oa.attendance.support;

import java.util.Set;
import com.erp.common.core.exception.ServiceException;

/** Only validation failures before any punch event is persisted are terminal. */
public final class AttendancePunchRejectionPolicy
{
    public static final String BUSINESS_CODE = "ATTENDANCE_PUNCH_REJECTED";
    private static final Set<String> REJECTED = Set.of(
            "OUTSIDE_ATTENDANCE_GEOFENCE", "LOCATION_ACCURACY_INSUFFICIENT",
            "LOCATION_INVALID", "CLIENT_CAPTURE_TIME_REQUIRED",
            "CLIENT_CAPTURE_TIME_STALE", "CLIENT_CAPTURE_TIME_INVALID", "OUTSIDE_PUNCH_WINDOW",
            "ATTENDANCE_ADDRESS_RESOLVER_UNAVAILABLE",
            "ATTENDANCE_ADDRESS_RESOLUTION_FAILED", "ATTENDANCE_ADDRESS_INVALID",
            "PHOTO_REQUIRED", "PHOTO_SIZE_INVALID", "PHOTO_DIMENSION_INVALID",
            "ATTENDANCE_SHOP_SCOPE_MISMATCH", "PUNCH_DAY_RESULT_ALREADY_SETTLED",
            "PUNCH_SLOT_TYPE_MISMATCH", "PUNCH_SLOT_CHALLENGE_MISMATCH",
            "PUNCH_SEGMENT_CHALLENGE_MISMATCH", "PUNCH_BLOCKED_BY_APPROVED_LEAVE",
            "PUNCH_REMAINING_WORK_CONFIRMATION_REQUIRED");

    private AttendancePunchRejectionPolicy() { }

    public static boolean isDefiniteRejection(ServiceException error)
    {
        // Never classify consumed/expired challenges, duplicate keys, commit
        // failures or arbitrary 500 responses: a prior success may exist.
        return error != null && REJECTED.contains(error.getMessage());
    }
}

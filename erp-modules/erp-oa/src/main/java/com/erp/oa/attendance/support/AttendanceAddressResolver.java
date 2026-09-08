package com.erp.oa.attendance.support;

import java.math.BigDecimal;

/**
 * Controlled reverse-geocoding boundary for attendance evidence.
 * Implementations must not silently fall back to a client-declared address.
 */
public interface AttendanceAddressResolver
{
    ResolvedAddress resolve(BigDecimal latitude, BigDecimal longitude,
            String coordinateSystem);

    record ResolvedAddress(String formattedAddress, String provider) { }
}

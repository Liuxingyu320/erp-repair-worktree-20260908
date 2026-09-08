package com.erp.oa.attendance.support;

import java.math.BigDecimal;
import com.erp.common.core.exception.ServiceException;

/** Fail closed until an explicitly configured reverse-geocoder is supplied. */
public class UnavailableAttendanceAddressResolver
        implements AttendanceAddressResolver
{
    @Override
    public ResolvedAddress resolve(BigDecimal latitude, BigDecimal longitude,
            String coordinateSystem)
    {
        throw new ServiceException(
                "ATTENDANCE_ADDRESS_RESOLVER_UNAVAILABLE");
    }
}

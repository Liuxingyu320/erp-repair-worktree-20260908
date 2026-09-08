package com.erp.oa.attendance.support;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;

class UnavailableAttendanceAddressResolverTest
{
    @Test
    void failsClosedWithoutAConfiguredProvider()
    {
        AttendanceAddressResolver resolver =
                new UnavailableAttendanceAddressResolver();

        assertThatThrownBy(() -> resolver.resolve(
                new BigDecimal("30.2741000"),
                new BigDecimal("120.1551000"), "WGS84"))
                .isInstanceOf(ServiceException.class)
                .hasMessage("ATTENDANCE_ADDRESS_RESOLVER_UNAVAILABLE");
    }
}

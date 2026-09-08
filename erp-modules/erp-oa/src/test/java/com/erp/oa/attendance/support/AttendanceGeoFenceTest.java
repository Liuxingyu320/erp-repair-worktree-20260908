package com.erp.oa.attendance.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;

class AttendanceGeoFenceTest
{
    private final AttendanceGeoFence fence = new AttendanceGeoFence();

    @Test
    void acceptsAccuratePointInsideAndRejectsOutsideOrInaccurate()
    {
        BigDecimal distance = fence.requireInside(
                new BigDecimal("28.2282000"), new BigDecimal("112.9388000"),
                new BigDecimal("18.5"), new BigDecimal("28.2281000"),
                new BigDecimal("112.9387000"), 200, 100);
        assertThat(distance).isBetween(new BigDecimal("10"),
                new BigDecimal("30"));

        assertThatThrownBy(() -> fence.requireInside(
                new BigDecimal("28.2382000"), new BigDecimal("112.9488000"),
                BigDecimal.TEN, new BigDecimal("28.2281000"),
                new BigDecimal("112.9387000"), 200, 100))
                .isInstanceOf(ServiceException.class)
                .hasMessage("OUTSIDE_ATTENDANCE_GEOFENCE");
        assertThatThrownBy(() -> fence.requireInside(
                new BigDecimal("28.2282000"), new BigDecimal("112.9388000"),
                new BigDecimal("101"), new BigDecimal("28.2281000"),
                new BigDecimal("112.9387000"), 200, 100))
                .hasMessage("LOCATION_ACCURACY_INSUFFICIENT");
    }
}

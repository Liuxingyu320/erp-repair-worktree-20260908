package com.erp.oa.attendance.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import com.erp.oa.attendance.config.AttendanceV2Properties;

class AttendanceLocationAuditPolicyTest
{
    private final AttendanceV2Properties properties =
            new AttendanceV2Properties();
    private final AttendanceLocationAuditPolicy policy =
            new AttendanceLocationAuditPolicy(
                    new AttendanceCoordinateTransformer(), properties);

    @Test
    void validatesRawLocationAndAccuracyWithoutAFixedSite()
    {
        assertThat(policy.requireValid(new BigDecimal("30.2741000"),
                new BigDecimal("120.1551000"), new BigDecimal("35.50"),
                "WGS84")).isEqualTo("WGS84");

        assertThatThrownBy(() -> policy.requireValid(
                new BigDecimal("30.2741000"),
                new BigDecimal("120.1551000"), new BigDecimal("100.01"),
                "WGS84"))
                .hasMessage("LOCATION_ACCURACY_INSUFFICIENT");
        assertThatThrownBy(() -> policy.requireValid(
                new BigDecimal("91"), new BigDecimal("120"),
                BigDecimal.TEN, "WGS84"))
                .hasMessage("LOCATION_INVALID");
    }
}

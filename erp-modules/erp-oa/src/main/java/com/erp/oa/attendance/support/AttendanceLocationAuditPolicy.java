package com.erp.oa.attendance.support;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.attendance.config.AttendanceV2Properties;

/** Validates raw device location without comparing it to a fixed site. */
@Component
public class AttendanceLocationAuditPolicy
{
    private final AttendanceCoordinateTransformer coordinates;
    private final AttendanceV2Properties properties;

    public AttendanceLocationAuditPolicy(
            AttendanceCoordinateTransformer coordinates,
            AttendanceV2Properties properties)
    {
        this.coordinates = coordinates;
        this.properties = properties;
    }

    public String requireValid(BigDecimal latitude, BigDecimal longitude,
            BigDecimal accuracyMeters, String coordinateSystem)
    {
        if (latitude == null || longitude == null || accuracyMeters == null)
            throw new ServiceException("LOCATION_REQUIRED");
        String system = coordinates.normalize(coordinateSystem);
        // Same-system conversion performs finite and range validation while
        // preserving the submitted coordinates as the audit source of truth.
        coordinates.transform(latitude, longitude, system, system);
        int maximum = properties.getMaxLocationAccuracyMeters();
        if (maximum < 5 || maximum > 10_000)
            throw new ServiceException(
                    "ATTENDANCE_LOCATION_POLICY_INVALID");
        if (accuracyMeters.signum() < 0
                || accuracyMeters.compareTo(
                        BigDecimal.valueOf(maximum)) > 0)
            throw new ServiceException("LOCATION_ACCURACY_INSUFFICIENT");
        return system;
    }
}

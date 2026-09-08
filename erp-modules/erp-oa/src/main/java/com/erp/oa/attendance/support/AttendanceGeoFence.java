package com.erp.oa.attendance.support;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;
import com.erp.common.core.exception.ServiceException;

@Component
public class AttendanceGeoFence
{
    private static final double EARTH_RADIUS_METERS = 6_371_008.8d;

    public BigDecimal requireInside(BigDecimal latitude, BigDecimal longitude,
            BigDecimal accuracyMeters, BigDecimal siteLatitude,
            BigDecimal siteLongitude, int radiusMeters,
            int maxAccuracyMeters)
    {
        requireCoordinate(latitude, longitude);
        requireCoordinate(siteLatitude, siteLongitude);
        if (accuracyMeters == null || accuracyMeters.signum() < 0
                || accuracyMeters.compareTo(BigDecimal.valueOf(
                        maxAccuracyMeters)) > 0)
        {
            throw new ServiceException("LOCATION_ACCURACY_INSUFFICIENT");
        }
        BigDecimal distance = BigDecimal.valueOf(distanceMeters(
                latitude.doubleValue(), longitude.doubleValue(),
                siteLatitude.doubleValue(), siteLongitude.doubleValue()))
                .setScale(2, RoundingMode.HALF_UP);
        if (distance.compareTo(BigDecimal.valueOf(radiusMeters)) > 0)
        {
            throw new ServiceException("OUTSIDE_ATTENDANCE_GEOFENCE");
        }
        return distance;
    }

    private void requireCoordinate(BigDecimal latitude, BigDecimal longitude)
    {
        if (latitude == null || longitude == null
                || latitude.compareTo(BigDecimal.valueOf(-90)) < 0
                || latitude.compareTo(BigDecimal.valueOf(90)) > 0
                || longitude.compareTo(BigDecimal.valueOf(-180)) < 0
                || longitude.compareTo(BigDecimal.valueOf(180)) > 0)
        {
            throw new ServiceException("LOCATION_INVALID");
        }
    }

    static double distanceMeters(double lat1, double lon1, double lat2,
            double lon2)
    {
        double lat = Math.toRadians(lat2 - lat1);
        double lon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(lat / 2) * Math.sin(lat / 2)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(lon / 2) * Math.sin(lon / 2);
        return 2 * EARTH_RADIUS_METERS
                * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}

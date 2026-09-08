package com.erp.oa.attendance.support;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;
import com.erp.common.core.exception.ServiceException;

/** Coordinate conversion used before geofence distance calculation. */
@Component
public class AttendanceCoordinateTransformer
{
    private static final double PI = Math.PI;
    private static final double X_PI = PI * 3000.0 / 180.0;
    private static final double A = 6378245.0;
    private static final double EE = 0.00669342162296594323;
    private static final Set<String> SYSTEMS = Set.of("WGS84", "GCJ02", "BD09");

    public Coordinate transform(BigDecimal latitude, BigDecimal longitude,
            String sourceSystem, String targetSystem)
    {
        String source = normalize(sourceSystem);
        String target = normalize(targetSystem);
        double lat = latitude == null ? Double.NaN : latitude.doubleValue();
        double lon = longitude == null ? Double.NaN : longitude.doubleValue();
        if (!Double.isFinite(lat) || !Double.isFinite(lon)
                || lat < -90 || lat > 90 || lon < -180 || lon > 180)
            throw new ServiceException("LOCATION_INVALID");
        double[] gcj;
        if (source.equals(target)) return coordinate(lat, lon, target);
        if ("WGS84".equals(source)) gcj = wgsToGcj(lat, lon);
        else if ("BD09".equals(source)) gcj = bdToGcj(lat, lon);
        else gcj = new double[] { lat, lon };
        if ("GCJ02".equals(target)) return coordinate(gcj[0], gcj[1], target);
        if ("BD09".equals(target))
        {
            double[] bd = gcjToBd(gcj[0], gcj[1]);
            return coordinate(bd[0], bd[1], target);
        }
        double[] wgs = gcjToWgs(gcj[0], gcj[1]);
        return coordinate(wgs[0], wgs[1], target);
    }

    public String normalize(String value)
    {
        String system = value == null ? ""
                : value.replace("-", "").trim().toUpperCase(Locale.ROOT);
        if (!SYSTEMS.contains(system))
            throw new ServiceException("COORDINATE_SYSTEM_INVALID");
        return system;
    }

    private double[] wgsToGcj(double lat, double lon)
    {
        if (outsideChina(lat, lon)) return new double[] { lat, lon };
        double[] delta = delta(lat, lon);
        return new double[] { lat + delta[0], lon + delta[1] };
    }

    private double[] gcjToWgs(double lat, double lon)
    {
        if (outsideChina(lat, lon)) return new double[] { lat, lon };
        double[] converted = wgsToGcj(lat, lon);
        return new double[] { lat * 2 - converted[0],
                lon * 2 - converted[1] };
    }

    private double[] gcjToBd(double lat, double lon)
    {
        double z = Math.sqrt(lon * lon + lat * lat)
                + 0.00002 * Math.sin(lat * X_PI);
        double theta = Math.atan2(lat, lon)
                + 0.000003 * Math.cos(lon * X_PI);
        return new double[] { z * Math.sin(theta) + 0.006,
                z * Math.cos(theta) + 0.0065 };
    }

    private double[] bdToGcj(double lat, double lon)
    {
        double x = lon - 0.0065;
        double y = lat - 0.006;
        double z = Math.sqrt(x * x + y * y)
                - 0.00002 * Math.sin(y * X_PI);
        double theta = Math.atan2(y, x)
                - 0.000003 * Math.cos(x * X_PI);
        return new double[] { z * Math.sin(theta), z * Math.cos(theta) };
    }

    private double[] delta(double lat, double lon)
    {
        double dLat = transformLat(lon - 105.0, lat - 35.0);
        double dLon = transformLon(lon - 105.0, lat - 35.0);
        double radLat = lat / 180.0 * PI;
        double magic = Math.sin(radLat);
        magic = 1 - EE * magic * magic;
        double sqrtMagic = Math.sqrt(magic);
        dLat = dLat * 180.0 / ((A * (1 - EE))
                / (magic * sqrtMagic) * PI);
        dLon = dLon * 180.0 / (A / sqrtMagic * Math.cos(radLat) * PI);
        return new double[] { dLat, dLon };
    }

    private double transformLat(double x, double y)
    {
        double ret = -100 + 2 * x + 3 * y + 0.2 * y * y
                + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x));
        ret += (20 * Math.sin(6 * x * PI) + 20 * Math.sin(2 * x * PI)) * 2 / 3;
        ret += (20 * Math.sin(y * PI) + 40 * Math.sin(y / 3 * PI)) * 2 / 3;
        ret += (160 * Math.sin(y / 12 * PI) + 320 * Math.sin(y * PI / 30)) * 2 / 3;
        return ret;
    }

    private double transformLon(double x, double y)
    {
        double ret = 300 + x + 2 * y + 0.1 * x * x
                + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x));
        ret += (20 * Math.sin(6 * x * PI) + 20 * Math.sin(2 * x * PI)) * 2 / 3;
        ret += (20 * Math.sin(x * PI) + 40 * Math.sin(x / 3 * PI)) * 2 / 3;
        ret += (150 * Math.sin(x / 12 * PI) + 300 * Math.sin(x / 30 * PI)) * 2 / 3;
        return ret;
    }

    private boolean outsideChina(double lat, double lon)
    { return lon < 72.004 || lon > 137.8347 || lat < 0.8293 || lat > 55.8271; }

    private Coordinate coordinate(double lat, double lon, String system)
    {
        return new Coordinate(BigDecimal.valueOf(lat).setScale(7,
                RoundingMode.HALF_UP), BigDecimal.valueOf(lon).setScale(7,
                RoundingMode.HALF_UP), system);
    }

    public record Coordinate(BigDecimal latitude, BigDecimal longitude,
            String coordinateSystem) { }
}

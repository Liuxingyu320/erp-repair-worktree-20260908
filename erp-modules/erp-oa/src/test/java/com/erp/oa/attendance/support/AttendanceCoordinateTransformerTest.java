package com.erp.oa.attendance.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import com.erp.oa.attendance.support.AttendanceCoordinateTransformer.Coordinate;

class AttendanceCoordinateTransformerTest
{
    private final AttendanceCoordinateTransformer transformer =
            new AttendanceCoordinateTransformer();

    @Test
    void convertsWgs84BeforeComparingWithGcj02OrBd09Site()
    {
        BigDecimal lat = new BigDecimal("28.2282000");
        BigDecimal lon = new BigDecimal("112.9388000");
        Coordinate gcj = transformer.transform(lat, lon, "WGS84", "GCJ02");
        assertThat(gcj.coordinateSystem()).isEqualTo("GCJ02");
        assertThat(gcj.latitude()).isNotEqualByComparingTo(lat);
        assertThat(gcj.longitude()).isNotEqualByComparingTo(lon);

        Coordinate restored = transformer.transform(gcj.latitude(),
                gcj.longitude(), "GCJ02", "WGS84");
        assertThat(restored.latitude().subtract(lat).abs())
                .isLessThan(new BigDecimal("0.00002"));
        assertThat(restored.longitude().subtract(lon).abs())
                .isLessThan(new BigDecimal("0.00002"));

        Coordinate bd = transformer.transform(lat, lon, "WGS84", "BD09");
        assertThat(bd.coordinateSystem()).isEqualTo("BD09");
        assertThatThrownBy(() -> transformer.transform(lat, lon,
                "UNKNOWN", "GCJ02")).hasMessage("COORDINATE_SYSTEM_INVALID");
    }
}

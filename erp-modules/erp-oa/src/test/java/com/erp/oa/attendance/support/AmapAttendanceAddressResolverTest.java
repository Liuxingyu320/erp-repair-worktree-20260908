package com.erp.oa.attendance.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.attendance.config.AttendanceV2Properties.Address;
import com.erp.oa.attendance.support.AmapAttendanceAddressResolver.HttpResult;
import com.erp.oa.attendance.support.AttendanceAddressResolver.ResolvedAddress;
import com.erp.oa.attendance.support.AttendanceCoordinateTransformer.Coordinate;
import com.fasterxml.jackson.databind.ObjectMapper;

class AmapAttendanceAddressResolverTest
{
    private static final BigDecimal LATITUDE =
            new BigDecimal("30.2741000");
    private static final BigDecimal LONGITUDE =
            new BigDecimal("120.1551000");

    @Test
    void convertsWgs84AndAppendsTheNearestAoiWithoutLeakingConfiguration()
    {
        AtomicReference<URI> requested = new AtomicReference<>();
        AmapAttendanceAddressResolver resolver = resolver(uri -> {
            requested.set(uri);
            return json("""
                    {
                      "status":"1","info":"OK",
                      "regeocode":{
                        "formatted_address":"浙江省杭州市拱墅区某某路88号",
                        "addressComponent":{"building":{"name":[]}},
                        "aois":[
                          {"name":"远处园区","distance":"50"},
                          {"name":"华润大厦","distance":"0"}
                        ],
                        "pois":[{"name":"便利店","distance":"3.5"}]
                      }
                    }
                    """);
        });

        ResolvedAddress result = resolver.resolve(LATITUDE, LONGITUDE,
                "WGS84");

        assertThat(result.provider()).isEqualTo("AMAP");
        assertThat(result.formattedAddress()).isEqualTo(
                "浙江省杭州市拱墅区某某路88号（华润大厦）");
        URI uri = requested.get();
        assertThat(uri.getScheme()).isEqualTo("https");
        assertThat(uri.getHost()).isEqualTo("restapi.amap.com");
        assertThat(uri.getPath()).isEqualTo("/v3/geocode/regeo");
        Map<String, String> query = query(uri);
        assertThat(query).containsEntry("output", "JSON")
                .containsEntry("extensions", "all")
                .containsEntry("homeorcorp", "2")
                .containsEntry("radius", "100");
        Coordinate expected = new AttendanceCoordinateTransformer()
                .transform(LATITUDE, LONGITUDE, "WGS84", "GCJ02");
        assertThat(query.get("location")).isEqualTo(
                expected.longitude().setScale(6, RoundingMode.HALF_UP)
                        .toPlainString()
                + "," + expected.latitude().setScale(6,
                        RoundingMode.HALF_UP).toPlainString());
        assertThat(query.get("location"))
                .isNotEqualTo("120.155100,30.274100");
    }

    @Test
    void doesNotAppendNoisyPoiWhenTheAoiIsAlreadyInTheAddress()
    {
        AmapAttendanceAddressResolver resolver = resolver(uri -> json("""
                {
                  "status":"1","info":"OK",
                  "regeocode":{
                    "formatted_address":"浙江省杭州市拱墅区华润大厦",
                    "addressComponent":{"building":{"name":[]}},
                    "aois":[{"name":"华润大厦","distance":"0"}],
                    "pois":[{"name":"某咖啡店","distance":"1"}]
                  }
                }
                """));

        ResolvedAddress result = resolver.resolve(LATITUDE, LONGITUDE,
                "WGS84");

        assertThat(result.formattedAddress())
                .isEqualTo("浙江省杭州市拱墅区华润大厦");
    }

    @Test
    void mapsProviderTransportAndPayloadFailuresToStableBusinessCodes()
    {
        assertThatThrownBy(() -> resolver(uri -> new HttpResult(
                503, "application/json", "service unavailable"))
                        .resolve(LATITUDE, LONGITUDE, "WGS84"))
                .isInstanceOf(ServiceException.class)
                .hasMessage("ATTENDANCE_ADDRESS_RESOLUTION_FAILED")
                .message().doesNotContain("unit-test-only");

        assertThatThrownBy(() -> resolver(uri -> json("""
                {"status":"0","info":"INVALID_USER_KEY"}
                """)).resolve(LATITUDE, LONGITUDE, "WGS84"))
                .isInstanceOf(ServiceException.class)
                .hasMessage("ATTENDANCE_ADDRESS_RESOLUTION_FAILED")
                .message().doesNotContain("INVALID_USER_KEY");

        assertThatThrownBy(() -> resolver(uri -> json("""
                {
                  "status":"1","info":"OK",
                  "regeocode":{"formatted_address":"浙江省\\n杭州市"}
                }
                """)).resolve(LATITUDE, LONGITUDE, "WGS84"))
                .isInstanceOf(ServiceException.class)
                .hasMessage("ATTENDANCE_ADDRESS_INVALID");
    }

    @Test
    void rejectsAmapModeWithoutAValidPrivateConfiguration()
    {
        Address missing = new Address();

        assertThatThrownBy(() -> new AmapAttendanceAddressResolver(
                missing, new AttendanceCoordinateTransformer(),
                new ObjectMapper()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("AMAP_ATTENDANCE_KEY_REQUIRED");
    }

    private AmapAttendanceAddressResolver resolver(
            AmapAttendanceAddressResolver.Transport transport)
    {
        Address properties = new Address();
        properties.setProvider("AMAP");
        properties.setAmapWebServiceKey("unit-test-only");
        return new AmapAttendanceAddressResolver(properties,
                new AttendanceCoordinateTransformer(), new ObjectMapper(),
                transport);
    }

    private HttpResult json(String body)
    {
        return new HttpResult(200, "application/json;charset=UTF-8", body);
    }

    private Map<String, String> query(URI uri)
    {
        return Arrays.stream(uri.getRawQuery().split("&"))
                .map(value -> value.split("=", 2))
                .collect(Collectors.toMap(value -> decode(value[0]),
                        value -> value.length == 1 ? "" : decode(value[1])));
    }

    private String decode(String value)
    {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}

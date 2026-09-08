package com.erp.oa.attendance.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.erp.oa.attendance.support.AttendanceAddressResolver;
import com.erp.oa.attendance.support.AmapAttendanceAddressResolver;
import com.erp.oa.attendance.support.AttendanceCoordinateTransformer;
import com.erp.oa.attendance.support.UnavailableAttendanceAddressResolver;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Installs a fail-closed resolver until a controlled provider bean exists. */
@Configuration(proxyBeanMethods = false)
public class AttendanceAddressResolverConfiguration
{
    @Bean
    @ConditionalOnProperty(prefix = "oa.attendance-v2.address",
            name = "provider", havingValue = "NONE", matchIfMissing = true)
    public AttendanceAddressResolver attendanceAddressResolver()
    {
        return new UnavailableAttendanceAddressResolver();
    }

    @Bean
    @ConditionalOnProperty(prefix = "oa.attendance-v2.address",
            name = "provider", havingValue = "AMAP")
    public AttendanceAddressResolver amapAttendanceAddressResolver(
            AttendanceV2Properties properties,
            AttendanceCoordinateTransformer coordinates,
            ObjectMapper objectMapper)
    {
        return new AmapAttendanceAddressResolver(properties.getAddress(),
                coordinates, objectMapper);
    }
}

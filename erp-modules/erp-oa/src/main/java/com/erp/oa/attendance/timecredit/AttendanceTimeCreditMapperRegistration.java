package com.erp.oa.attendance.timecredit;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@MapperScan(basePackageClasses = AttendanceTimeCreditMapper.class)
class AttendanceTimeCreditMapperRegistration
{
}

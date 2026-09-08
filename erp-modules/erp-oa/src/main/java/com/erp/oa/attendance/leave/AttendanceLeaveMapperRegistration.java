package com.erp.oa.attendance.leave;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/** Registers the package-local mapper outside the repository's *.mapper scan. */
@Configuration(proxyBeanMethods = false)
@MapperScan(basePackageClasses = AttendanceLeaveMapper.class)
class AttendanceLeaveMapperRegistration
{
}

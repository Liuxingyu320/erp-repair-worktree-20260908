package com.erp.oa.attendance.payroll;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/** Registers the attendance payroll mapper outside the repository's *.mapper scan. */
@Configuration(proxyBeanMethods = false)
@MapperScan(basePackageClasses = AttendancePayrollMapper.class)
class AttendancePayrollMapperRegistration
{
}

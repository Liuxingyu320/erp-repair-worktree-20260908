package com.erp.oa.attendance.approval;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/** Registers the attendance approval mapper outside the repository's *.mapper scan. */
@Configuration(proxyBeanMethods = false)
@MapperScan(basePackageClasses = OaAttendanceApprovalCallbackMapper.class)
class AttendanceApprovalMapperRegistration
{
}

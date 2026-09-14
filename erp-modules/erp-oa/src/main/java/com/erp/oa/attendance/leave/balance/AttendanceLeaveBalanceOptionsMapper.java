package com.erp.oa.attendance.leave.balance;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;

/** Read-only projections: no payroll, identity document or contact fields. */
@Mapper
public interface AttendanceLeaveBalanceOptionsMapper
{
    List<TypeOption> selectTypes();
    List<CompanyOption> selectCompanies(@Param("ownerDeptId") Long ownerDeptId);
    int countEmployees(@Param("ownerDeptId") Long ownerDeptId, @Param("keyword") String keyword);
    List<EmployeeOption> selectEmployees(@Param("ownerDeptId") Long ownerDeptId, @Param("keyword") String keyword,
            @Param("offset") long offset, @Param("limit") int limit);
    int countOvertimeSources(@Param("ownerDeptId") Long ownerDeptId, @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo, @Param("keyword") String keyword);
    List<SourceOption> selectOvertimeSources(@Param("ownerDeptId") Long ownerDeptId, @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo, @Param("keyword") String keyword, @Param("offset") long offset, @Param("limit") int limit);

    class TypeOption { public String leaveTypeId, typeName, typeCode, unitMode; public Boolean balanceRequired; }
    class CompanyOption { public String legalEntityId, legalEntityName, legalEntityCode; }
    class EmployeeOption { public String userId, userName, account, deptName; }
    class SourceOption {
        public String dayResultId, rowVersion, userId, shopId, scheduleId, userName, resultStatus;
        public LocalDate businessDate; public LocalDateTime settledAt;
        public Integer workedMinutes, scheduledMinutes;
    }
}

package com.erp.system.mapper;

import org.apache.ibatis.annotations.Param;
import com.erp.system.api.domain.EmployeeSalarySource;

public interface HrSalarySourceMapper
{
    EmployeeSalarySource currentLocked(@Param("employeeId") Long employeeId);
    EmployeeSalarySource commandLocked(@Param("commandId") String commandId);
    EmployeeSalarySource current(@Param("employeeId") Long employeeId);
    EmployeeSalarySource command(@Param("commandId") String commandId);
    EmployeeSalarySource historicalForRow(@Param("batchId") Long batchId, @Param("rowId") Long rowId);
    int insert(EmployeeSalarySource source);
    int pointTo(EmployeeSalarySource source);
    java.util.List<java.util.Map<String, Object>> contracts(@Param("employeeId") Long employeeId,
            @Param("sourceId") String sourceId);
}

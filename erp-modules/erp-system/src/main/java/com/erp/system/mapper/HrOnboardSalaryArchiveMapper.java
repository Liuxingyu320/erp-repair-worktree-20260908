package com.erp.system.mapper;

import org.apache.ibatis.annotations.Param;
import java.util.List;
import com.erp.system.domain.HrOnboardSalaryTaskOwner;
import com.erp.system.domain.HrOnboardSalarySource;
import com.erp.system.domain.HrEmployeeSalaryImportAudit;
import com.erp.system.api.domain.EmployeeSalaryValues;

public interface HrOnboardSalaryArchiveMapper
{
    HrOnboardSalarySource readSource(@Param("batchId") Long batchId, @Param("rowId") Long rowId);
    List<HrOnboardSalaryTaskOwner> readTasks(HrOnboardSalarySource source);
    HrOnboardSalarySource lockSource(@Param("batchId") Long batchId,
            @Param("rowId") Long rowId);
    List<HrOnboardSalaryTaskOwner> lockTasks(HrOnboardSalarySource source);
    HrEmployeeSalaryImportAudit auditForRow(@Param("rowId") Long rowId, @Param("version") Long version);
    HrEmployeeSalaryImportAudit latest(@Param("employeeId") Long employeeId);
    int updateSalary(@Param("employeeId") Long employeeId,
            @Param("salary") EmployeeSalaryValues salary,
            @Param("operator") String operator);
    int insertAudit(@Param("source") HrOnboardSalarySource source,
            @Param("beforeJson") String beforeJson,
            @Param("salaryJson") String salaryJson,
            @Param("operatorId") Long operatorId,
            @Param("operator") String operator);
}

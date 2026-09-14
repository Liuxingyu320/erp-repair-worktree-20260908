package com.erp.oa.mapper;
import org.apache.ibatis.annotations.Param;
import com.erp.system.api.domain.EmployeeSalarySource;
public interface OaSignSalarySourceMapper
{
    String currentSourceIdLocked(@Param("employeeId") Long employeeId);
    Long lockPackageTask(@Param("packageId") Long packageId);
    Long lockProfile(@Param("employeeId") Long employeeId);
    EmployeeSalarySource bindingLocked(@Param("packageId") Long packageId);
    EmployeeSalarySource currentLocked(@Param("employeeId") Long employeeId);
    EmployeeSalarySource current(@Param("employeeId") Long employeeId);
    EmployeeSalarySource binding(@Param("packageId") Long packageId);
    EmployeeSalarySource historicalForRow(@Param("batchId") Long batchId, @Param("rowId") Long rowId);
    EmployeeSalarySource historicalForPackageLocked(@Param("packageId") Long packageId,
            @Param("employeeId") Long employeeId, @Param("sourceId") String sourceId);
    int bindHistorical(@Param("packageId") Long packageId, @Param("employeeId") Long employeeId,
            @Param("sourceId") String sourceId);
    int bind(@Param("packageId") Long packageId, @Param("employeeId") Long employeeId, @Param("sourceId") String sourceId);
    int isLegacyPreparedSignature(@Param("packageId") Long packageId);
    int isExcelPackage(@Param("packageId") Long packageId);
}

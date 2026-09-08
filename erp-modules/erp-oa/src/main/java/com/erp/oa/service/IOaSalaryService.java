package com.erp.oa.service;

import java.util.List;
import com.erp.oa.domain.OaSalaryConfig;
import com.erp.oa.domain.OaSalaryRecord;
import com.erp.oa.domain.vo.OaSalaryAttendancePreflightVo;

public interface IOaSalaryService
{
    OaSalaryConfig getConfig(Long shopDeptId, Long selectedShopDeptId);
    OaSalaryConfig saveConfig(OaSalaryConfig config, Long selectedShopDeptId);
    List<OaSalaryRecord> selectMyRecords(OaSalaryRecord record, Long selectedShopDeptId);
    List<OaSalaryRecord> selectAllRecords(OaSalaryRecord record, Long selectedShopDeptId);
    OaSalaryRecord getRecordById(Long salaryId, Long selectedShopDeptId);
    OaSalaryAttendancePreflightVo preflightAttendance(Long shopDeptId, String salaryMonth, Long selectedShopDeptId);
    List<OaSalaryRecord> calculateSalary(Long shopDeptId, String salaryMonth, Long selectedShopDeptId);
}

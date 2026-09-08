package com.erp.oa.mapper;

import java.util.List;
import com.erp.oa.domain.OaSalaryRecord;

public interface OaSalaryRecordMapper
{
    int insertOaSalaryRecord(OaSalaryRecord record);
    int updateOaSalaryRecord(OaSalaryRecord record);
    OaSalaryRecord selectOaSalaryRecordById(Long salaryId);
    List<OaSalaryRecord> selectOaSalaryRecordList(OaSalaryRecord record);
    OaSalaryRecord selectByUserIdAndMonth(Long userId, String salaryMonth);
    int deleteByShopDeptIdAndMonth(Long shopDeptId, String salaryMonth);
}

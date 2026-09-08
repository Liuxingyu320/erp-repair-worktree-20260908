package com.erp.oa.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.oa.domain.OaSalaryEmployee;

public interface OaSalaryEmployeeMapper
{
    List<OaSalaryEmployee> selectSalaryEmployeesByShopDeptId(@Param("shopDeptId") Long shopDeptId,
                                                             @Param("salaryMonth") String salaryMonth);
}

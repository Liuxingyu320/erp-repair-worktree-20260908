package com.erp.oa.mapper;

import com.erp.oa.domain.OaSalaryConfig;

public interface OaSalaryConfigMapper
{
    int insertOaSalaryConfig(OaSalaryConfig config);
    int updateOaSalaryConfig(OaSalaryConfig config);
    OaSalaryConfig selectOaSalaryConfigById(Long configId);
    OaSalaryConfig selectOaSalaryConfigByShopDeptId(Long shopDeptId);
}

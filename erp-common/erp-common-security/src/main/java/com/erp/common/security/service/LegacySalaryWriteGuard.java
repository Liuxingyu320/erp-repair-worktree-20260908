package com.erp.common.security.service;

import org.springframework.stereotype.Component;
import com.erp.common.core.exception.ServiceException;

/** Legacy salary/contract records remain readable. New amounts are confirmed in the employee archive. */
@Component
public class LegacySalaryWriteGuard
{
    public void reject()
    {
        throw new ServiceException("此功能已停用，仅保留历史记录；请到人事管理 → 员工档案 → 批量处理入职合同确认薪资和签约");
    }
}

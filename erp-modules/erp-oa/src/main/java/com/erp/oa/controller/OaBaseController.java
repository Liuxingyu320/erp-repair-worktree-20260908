package com.erp.oa.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import com.erp.common.core.constant.HttpStatus;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.SignScopeHeaderUtils;
import com.erp.common.core.utils.ShopHeaderUtils;
import com.erp.common.core.web.controller.BaseController;
import com.erp.oa.mapper.OaDeptScopeMapper;

public abstract class OaBaseController extends BaseController
{
    @Autowired
    private OaDeptScopeMapper deptScopeMapper;

    protected Long resolveShopDeptId(HttpServletRequest request)
    {
        return ShopHeaderUtils.resolveShopDeptId(request);
    }

    protected Long resolveSignScopeDeptId(HttpServletRequest request)
    {
        return SignScopeHeaderUtils.resolveSignScopeDeptId(request);
    }

    /** Attendance is a store-only domain even for administrators. */
    protected Long resolveAttendanceShopDeptId(HttpServletRequest request)
    {
        Long deptId = resolveShopDeptId(request);
        if (deptId != null && deptId > 0
                && deptScopeMapper.countActiveStoreDept(deptId) != 1)
        {
            throw new ServiceException("考勤只能选择有效门店",
                    HttpStatus.FORBIDDEN);
        }
        return deptId;
    }
}

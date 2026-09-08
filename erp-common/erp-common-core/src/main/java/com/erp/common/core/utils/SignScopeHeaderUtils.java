package com.erp.common.core.utils;

import jakarta.servlet.http.HttpServletRequest;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.text.Convert;

/**
 * Resolves the organization context used only by the employee-signing domain.
 *
 * <p>The dedicated header takes precedence. The legacy shop header remains a
 * compatibility fallback for existing STORE clients.</p>
 */
public final class SignScopeHeaderUtils
{
    public static final String SIGN_SCOPE_HEADER = "Sign-Scope-Dept-Id";

    private SignScopeHeaderUtils()
    {
    }

    public static Long resolveSignScopeDeptId(HttpServletRequest request)
    {
        if (request == null)
        {
            return null;
        }
        String explicitValue = StringUtils.trim(request.getHeader(SIGN_SCOPE_HEADER));
        if (StringUtils.isNotEmpty(explicitValue))
        {
            Long deptId = Convert.toLong(explicitValue);
            if (deptId == null || deptId <= 0)
            {
                throw new ServiceException("签约组织编号不正确");
            }
            return deptId;
        }
        return ShopHeaderUtils.resolveShopDeptId(request);
    }
}

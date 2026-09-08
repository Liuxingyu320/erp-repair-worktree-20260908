package com.erp.oa.service.impl;

import java.util.Objects;
import org.springframework.stereotype.Service;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.oa.domain.OaSignTask;
import com.erp.system.api.model.LoginUser;

/**
 * Server-side boundary for the signing workspace.
 *
 * Signing access requires both the ordinary role permission and, for task-bound business
 * operations, the current task assignment. System administrators are the explicit exception.
 */
@Service
public class OaSignHrAccessService
{
    private static final String TECHNICAL_EVIDENCE_PERMISSION = "oa:signTask:technicalEvidence";

    /**
     * Keep the existing constructor signature so rolling upgrades and focused service tests do
     * not need a second dependency graph. The setting is no longer consulted for authorization.
     */
    public OaSignHrAccessService(OaSignAutomationSettingsService settings)
    {
    }

    public void requireCurrentHr()
    {
        if (!isCurrentHr())
        {
            throw new ServiceException("当前角色未配置合同签约业务权限");
        }
    }

    public void requireCurrentHrOrTechnicalEvidenceReader()
    {
        if (!isCurrentHr() && !isTechnicalEvidenceReader())
        {
            throw new ServiceException("当前角色未配置合同签约或技术证据验真权限");
        }
    }

    public boolean isCurrentHr()
    {
        return SecurityUtils.isAdmin() || hasSigningBusinessPermission(SecurityUtils.getLoginUser());
    }

    /**
     * Returns the mandatory task-list owner for an ordinary HR. Administrators intentionally
     * receive no owner filter.
     */
    public Long currentTaskOwnerFilter()
    {
        requireCurrentHr();
        if (SecurityUtils.isAdmin())
        {
            return null;
        }
        Long userId = SecurityUtils.getUserId();
        if (userId == null || userId <= 0)
        {
            throw new ServiceException("无法识别当前合同经办人");
        }
        return userId;
    }

    public boolean canHandleTask(OaSignTask task)
    {
        return isCurrentHr() && isCurrentUserTaskOwner(task);
    }

    public void requireTaskOwner(OaSignTask task)
    {
        requireCurrentHr();
        if (!isCurrentUserTaskOwner(task))
        {
            throw new ServiceException("签约任务不存在或未分配给当前合同经办人");
        }
    }

    static boolean isCurrentUserTaskOwner(OaSignTask task)
    {
        if (task == null)
        {
            return false;
        }
        if (SecurityUtils.isAdmin())
        {
            return true;
        }
        Long userId = SecurityUtils.getUserId();
        return userId != null && userId > 0
                && Objects.equals(userId, task.getAssignedHrUserId());
    }

    static boolean hasSigningBusinessPermission(LoginUser loginUser)
    {
        if (loginUser == null || loginUser.getPermissions() == null)
        {
            return false;
        }
        return loginUser.getPermissions().stream()
                .filter(permission -> permission != null)
                .anyMatch(permission -> permission.startsWith("oa:sign")
                        && !TECHNICAL_EVIDENCE_PERMISSION.equals(permission));
    }

    public boolean isTechnicalEvidenceReader()
    {
        if (SecurityUtils.isAdmin())
        {
            return true;
        }
        LoginUser loginUser = SecurityUtils.getLoginUser();
        return loginUser != null && loginUser.getPermissions() != null
                && loginUser.getPermissions().contains(TECHNICAL_EVIDENCE_PERMISSION);
    }
}

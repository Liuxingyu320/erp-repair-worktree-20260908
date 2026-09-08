package com.erp.file.drive.service;

import java.util.Objects;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.domain.DriveActor;
import com.erp.file.drive.domain.DriveSpace;
import com.erp.file.drive.exception.DriveException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 三类云盘空间的集中授权规则。
 */
@Service
public class DriveAuthorizationService
{
    private final DriveOrganizationAccessService organizationAccess;

    public DriveAuthorizationService()
    {
        this.organizationAccess = null;
    }

    @Autowired
    public DriveAuthorizationService(DriveOrganizationAccessService organizationAccess)
    {
        this.organizationAccess = organizationAccess;
    }

    public boolean canRead(DriveActor actor, DriveSpace space)
    {
        if (!isReadable(actor, space))
        {
            return false;
        }
        if (actor.admin())
        {
            return true;
        }
        if (!actor.hasPermission(DriveConstants.PERMISSION_ACCESS))
        {
            return false;
        }
        return switch (space.getSpaceType())
        {
            case DriveConstants.SPACE_PERSONAL -> Objects.equals(actor.userId(), space.getOwnerUserId());
            case DriveConstants.SPACE_COMPANY -> true;
            case DriveConstants.SPACE_DEPARTMENT -> organizationAccess == null
                    ? actor.deptId() != null && Objects.equals(actor.deptId(), space.getDeptId())
                    : organizationAccess.canRead(actor, space);
            default -> false;
        };
    }

    public boolean canWrite(DriveActor actor, DriveSpace space)
    {
        if (!isActive(actor, space))
        {
            return false;
        }
        if (actor.admin())
        {
            return true;
        }
        if (!actor.hasPermission(DriveConstants.PERMISSION_ACCESS))
        {
            return false;
        }
        return switch (space.getSpaceType())
        {
            case DriveConstants.SPACE_PERSONAL -> Objects.equals(actor.userId(), space.getOwnerUserId());
            case DriveConstants.SPACE_COMPANY -> actor.hasPermission(
                    DriveConstants.PERMISSION_COMPANY_MANAGE);
            case DriveConstants.SPACE_DEPARTMENT -> organizationAccess == null
                    ? actor.deptId() != null && Objects.equals(actor.deptId(), space.getDeptId())
                        && actor.hasPermission(DriveConstants.PERMISSION_DEPARTMENT_MANAGE)
                    : organizationAccess.canWrite(actor, space);
            default -> false;
        };
    }

    public void requireRead(DriveActor actor, DriveSpace space)
    {
        if (!canRead(actor, space))
        {
            throw accessDenied();
        }
    }

    public void requireWrite(DriveActor actor, DriveSpace space)
    {
        if (!canWrite(actor, space))
        {
            throw accessDenied();
        }
    }

    public boolean canCleanup(DriveActor actor, DriveSpace space)
    {
        if (!isReadable(actor, space)) return false;
        if (DriveConstants.STATUS_ACTIVE.equals(space.getStatus())) return canWrite(actor, space);
        if (actor.admin()) return true;
        if (!actor.hasPermission(DriveConstants.PERMISSION_ACCESS)) return false;
        return switch (space.getSpaceType())
        {
            case DriveConstants.SPACE_PERSONAL -> Objects.equals(
                    actor.userId(), space.getOwnerUserId());
            case DriveConstants.SPACE_COMPANY -> actor.hasPermission(
                    DriveConstants.PERMISSION_COMPANY_MANAGE);
            case DriveConstants.SPACE_DEPARTMENT -> organizationAccess == null
                    ? actor.deptId() != null && Objects.equals(actor.deptId(), space.getDeptId())
                        && actor.hasPermission(DriveConstants.PERMISSION_DEPARTMENT_MANAGE)
                    : organizationAccess.canCleanup(actor, space);
            default -> false;
        };
    }

    public void requireCleanup(DriveActor actor, DriveSpace space)
    {
        if (!canCleanup(actor, space)) throw accessDenied();
    }

    public void requireQuotaManage(DriveActor actor)
    {
        if (actor == null || (!actor.admin()
                && (!actor.hasPermission(DriveConstants.PERMISSION_ACCESS)
                || !actor.hasPermission(DriveConstants.PERMISSION_QUOTA_MANAGE))))
        {
            throw accessDenied();
        }
    }

    private static boolean isActive(DriveActor actor, DriveSpace space)
    {
        return actor != null && actor.userId() != null && space != null
                && DriveConstants.STATUS_ACTIVE.equals(space.getStatus());
    }

    private static boolean isReadable(DriveActor actor, DriveSpace space)
    {
        return actor != null && actor.userId() != null && space != null
                && (DriveConstants.STATUS_ACTIVE.equals(space.getStatus())
                    || DriveConstants.STATUS_READ_ONLY.equals(space.getStatus()));
    }

    private static DriveException accessDenied()
    {
        return new DriveException(DriveErrorCodes.DRIVE_ACCESS_DENIED, "无权访问该云盘空间");
    }
}

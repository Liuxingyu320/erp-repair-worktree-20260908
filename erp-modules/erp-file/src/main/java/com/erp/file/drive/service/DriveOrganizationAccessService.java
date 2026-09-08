package com.erp.file.drive.service;

import java.util.Objects;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.domain.DriveActor;
import com.erp.file.drive.domain.DriveOrganizationSpaceConfig;
import com.erp.file.drive.domain.DriveSpace;
import com.erp.file.drive.mapper.DriveOrganizationMapper;
import org.springframework.stereotype.Service;

/**
 * 组合组织数据范围、直属成员身份和成员写入模式。
 */
@Service
public class DriveOrganizationAccessService
{
    private final DriveOrganizationMapper mapper;
    private final DriveOrganizationScopeService scopeService;

    public DriveOrganizationAccessService(DriveOrganizationMapper mapper,
            DriveOrganizationScopeService scopeService)
    {
        this.mapper = mapper;
        this.scopeService = scopeService;
    }

    public boolean canRead(DriveActor actor, DriveSpace space)
    {
        if (space == null || space.getDeptId() == null) return false;
        DriveOrganizationSpaceConfig config = mapper.selectConfig(space.getDeptId());
        if (!isReadable(config)) return false;
        return scopeService.canRead(actor, space.getDeptId());
    }

    public boolean canWrite(DriveActor actor, DriveSpace space)
    {
        if (actor == null || space == null || space.getDeptId() == null) return false;
        DriveOrganizationSpaceConfig config = mapper.selectConfig(space.getDeptId());
        if (!isWritable(config)) return false;
        if (actor.admin()) return true;
        String mode = config == null ? DriveConstants.ORG_WRITE_PERMISSION_ONLY
                : config.getMemberWriteMode();
        if (DriveConstants.ORG_WRITE_ALL_DIRECT_MEMBERS.equals(mode)
                && Objects.equals(actor.deptId(), space.getDeptId()))
        {
            return actor.hasPermission(DriveConstants.PERMISSION_ACCESS);
        }
        return DriveConstants.ORG_WRITE_PERMISSION_ONLY.equals(mode)
                && scopeService.canManage(actor, space.getDeptId());
    }

    public boolean canCleanup(DriveActor actor, DriveSpace space)
    {
        if (actor == null || space == null || space.getDeptId() == null) return false;
        DriveOrganizationSpaceConfig config = mapper.selectConfig(space.getDeptId());
        if (!isReadable(config)) return false;
        if (actor.admin()) return true;
        String mode = config == null ? DriveConstants.ORG_WRITE_PERMISSION_ONLY
                : config.getMemberWriteMode();
        if (DriveConstants.ORG_WRITE_ALL_DIRECT_MEMBERS.equals(mode)
                && Objects.equals(actor.deptId(), space.getDeptId()))
        {
            return actor.hasPermission(DriveConstants.PERMISSION_ACCESS);
        }
        return scopeService.canManage(actor, space.getDeptId());
    }

    private static boolean isReadable(DriveOrganizationSpaceConfig config)
    {
        if (config == null) return true;
        return Boolean.TRUE.equals(config.getEnabled())
                && (DriveConstants.STATUS_ACTIVE.equals(config.getLifecycleStatus())
                    || DriveConstants.STATUS_READ_ONLY.equals(config.getLifecycleStatus()));
    }

    private static boolean isWritable(DriveOrganizationSpaceConfig config)
    {
        if (config == null) return true;
        return Boolean.TRUE.equals(config.getEnabled())
                && DriveConstants.STATUS_ACTIVE.equals(config.getLifecycleStatus())
                && !DriveConstants.ORG_WRITE_READ_ONLY.equals(config.getMemberWriteMode());
    }
}

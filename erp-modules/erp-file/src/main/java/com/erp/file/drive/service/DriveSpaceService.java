package com.erp.file.drive.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import com.erp.file.drive.config.DriveProperties;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.domain.DriveActor;
import com.erp.file.drive.domain.DriveEffectiveQuota;
import com.erp.file.drive.domain.DriveSpace;
import com.erp.file.drive.domain.vo.DriveSpaceVo;
import com.erp.file.drive.domain.vo.DriveOrganizationBudgetViolationVo;
import com.erp.file.drive.exception.DriveException;
import com.erp.file.drive.mapper.DriveSpaceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 创建并返回当前登录用户可见的三类云盘空间。
 */
@Service
public class DriveSpaceService
{
    private final DriveSpaceMapper spaceMapper;
    private final DriveProperties properties;
    private final DriveAuthorizationService authorization;
    private final DriveQuotaService quotaService;
    private final DriveEffectiveQuotaService effectiveQuotaService;
    private final DriveOrganizationProvisioningService organizationProvisioningService;
    private final DriveOrganizationScopeService organizationScopeService;
    private final DrivePersonalQuotaPolicyService personalQuotaPolicyService;
    private final DriveOrganizationConfigService organizationConfigService;
    private final DriveCapacityService capacityService;
    private final DriveOrganizationBudgetService organizationBudgetService;

    public DriveSpaceService(DriveSpaceMapper spaceMapper, DriveProperties properties,
            DriveAuthorizationService authorization, DriveQuotaService quotaService,
            DriveEffectiveQuotaService effectiveQuotaService,
            DriveOrganizationProvisioningService organizationProvisioningService,
            DriveOrganizationScopeService organizationScopeService,
            DrivePersonalQuotaPolicyService personalQuotaPolicyService,
            DriveOrganizationConfigService organizationConfigService,
            DriveCapacityService capacityService,
            DriveOrganizationBudgetService organizationBudgetService)
    {
        this.spaceMapper = spaceMapper;
        this.properties = properties;
        this.authorization = authorization;
        this.quotaService = quotaService;
        this.effectiveQuotaService = effectiveQuotaService;
        this.organizationProvisioningService = organizationProvisioningService;
        this.organizationScopeService = organizationScopeService;
        this.personalQuotaPolicyService = personalQuotaPolicyService;
        this.organizationConfigService = organizationConfigService;
        this.capacityService = capacityService;
        this.organizationBudgetService = organizationBudgetService;
    }

    public List<DriveSpaceVo> listVisibleSpaces(DriveActor actor)
    {
        Long userId = requireUserId(actor);
        DriveEffectiveQuota personalQuota = properties.isQuotaPolicyEnabled()
                ? effectiveQuotaService.resolve(userId)
                : new DriveEffectiveQuota(properties.getPersonalQuota(),
                        DriveConstants.QUOTA_SOURCE_LEGACY, null, "全员默认");
        DriveSpace personalTemplate = newSpace("PERSONAL:" + userId,
                DriveConstants.SPACE_PERSONAL, "我的文件", actor.userId(), null,
                personalQuota.quotaBytes(), personalQuota.sourceType(),
                personalQuota.sourceId(), actor.username());
        authorization.requireRead(actor, personalTemplate);
        DriveSpace personal = ensureSpace(personalTemplate);
        authorization.requireRead(actor, personal);
        if (properties.isQuotaPolicyEnabled())
        {
            personal = effectiveQuotaService.reconcilePersonalSpace(
                    personal, actor.userId(), actor.username());
        }

        DriveSpace company = spaceMapper.selectByKey(DriveConstants.COMPANY_SPACE_KEY);
        requireActive(company);
        authorization.requireRead(actor, company);

        List<DriveSpaceVo> result = new ArrayList<>(3);
        result.add(toVo(personal, actor));
        result.add(toVo(company, actor));

        if (properties.isOrganizationSyncEnabled())
        {
            appendDynamicOrganizationSpaces(result, actor);
        }
        else if (actor.deptId() != null)
        {
            String departmentName = departmentName(actor);
            DriveSpace departmentTemplate = newSpace("DEPARTMENT:" + actor.deptId(),
                    DriveConstants.SPACE_DEPARTMENT, departmentName, null, actor.deptId(),
                    properties.getDepartmentQuota(), DriveConstants.QUOTA_SOURCE_LEGACY,
                    actor.deptId(), actor.username());
            authorization.requireRead(actor, departmentTemplate);
            DriveSpace department = ensureSpace(departmentTemplate);
            authorization.requireRead(actor, department);
            result.add(toVo(department, actor));
        }
        return result;
    }

    public DriveSpace requireSpace(Long spaceId)
    {
        DriveSpace space = spaceId == null ? null : spaceMapper.selectById(spaceId);
        requireReadableState(space);
        return space;
    }

    public DriveSpace requireReadableSpace(Long spaceId, DriveActor actor)
    {
        DriveSpace space = requireSpace(spaceId);
        authorization.requireRead(actor, space);
        return space;
    }

    public DriveSpace requireWritableSpace(Long spaceId, DriveActor actor)
    {
        DriveSpace space = requireSpace(spaceId);
        authorization.requireWrite(actor, space);
        if (properties.isQuotaPolicyEnabled()
                && DriveConstants.SPACE_PERSONAL.equals(space.getSpaceType()))
        {
            space = effectiveQuotaService.reconcilePersonalSpace(
                    space, actor.userId(), actor.username());
        }
        if (properties.isOrganizationSyncEnabled()
                && DriveConstants.SPACE_DEPARTMENT.equals(space.getSpaceType()))
        {
            organizationBudgetService.requireWriteAllowed(space.getDeptId());
        }
        return space;
    }

    /**
     * 返回空间当前是否可以执行写操作。该能力判断与 {@link #requireWritableSpace}
     * 使用同一套组织预算规则，供节点列表等只读接口声明按钮能力时使用。
     */
    public boolean canWriteSpace(DriveSpace space, DriveActor actor)
    {
        if (!authorization.canWrite(actor, space))
        {
            return false;
        }
        return !properties.isOrganizationSyncEnabled()
                || !DriveConstants.SPACE_DEPARTMENT.equals(space.getSpaceType())
                || organizationBudgetService.violationForDept(space.getDeptId()) == null;
    }

    public DriveSpace requireCleanupSpace(Long spaceId, DriveActor actor)
    {
        DriveSpace space = requireSpace(spaceId);
        authorization.requireCleanup(actor, space);
        return space;
    }

    @Transactional
    public DriveSpaceVo updateQuota(Long spaceId, long quotaBytes, int version, DriveActor actor)
    {
        authorization.requireQuotaManage(actor);
        DriveSpace current = requireSpace(spaceId);
        DriveSpace updated;
        if (DriveConstants.SPACE_PERSONAL.equals(current.getSpaceType())
                && properties.isQuotaPolicyEnabled())
        {
            requireVersion(current, version);
            personalQuotaPolicyService.saveLegacyUserQuota(current.getOwnerUserId(), quotaBytes,
                    "兼容空间额度接口调整", actor);
            updated = effectiveQuotaService.reconcilePersonalSpace(current,
                    current.getOwnerUserId(), actor.username());
        }
        else if (DriveConstants.SPACE_DEPARTMENT.equals(current.getSpaceType())
                && properties.isOrganizationSyncEnabled())
        {
            requireVersion(current, version);
            updated = organizationConfigService.saveLegacyQuota(current, quotaBytes,
                    "兼容空间额度接口调整", actor);
        }
        else
        {
            if (DriveConstants.SPACE_COMPANY.equals(current.getSpaceType())
                    && (properties.isQuotaPolicyEnabled()
                        || properties.isOrganizationSyncEnabled()))
            {
                capacityService.lockForAllocationChange();
            }
            updated = quotaService.updateQuota(spaceId, quotaBytes, version, actor.username());
            if (DriveConstants.SPACE_COMPANY.equals(current.getSpaceType())
                    && (properties.isQuotaPolicyEnabled()
                        || properties.isOrganizationSyncEnabled()))
            {
                capacityService.requireCurrentAllocationsWithinPools();
            }
        }
        return toVo(updated, actor);
    }

    private DriveSpace ensureSpace(DriveSpace candidate)
    {
        DriveSpace existing = spaceMapper.selectByKey(candidate.getSpaceKey());
        if (existing == null)
        {
            spaceMapper.insertIgnore(candidate);
        }
        DriveSpace ensured = spaceMapper.selectByKey(candidate.getSpaceKey());
        requireActive(ensured);
        return ensured;
    }

    private void appendDynamicOrganizationSpaces(List<DriveSpaceVo> result, DriveActor actor)
    {
        Set<Long> readable = organizationScopeService.readableDeptIds(actor);
        if (readable.isEmpty()) return;
        for (Long deptId : readable)
        {
            organizationProvisioningService.syncOrganization(deptId, actor.username());
        }
        List<DriveSpace> selected = spaceMapper.selectByDeptIds(List.copyOf(readable));
        List<DriveSpace> organizationSpaces = new ArrayList<>(
                selected == null ? List.of() : selected);
        Map<Long, DriveOrganizationBudgetViolationVo> budgetBlocks =
                organizationBudgetService.violationsByDept();
        organizationSpaces.sort((left, right) ->
        {
            boolean leftDirect = Objects.equals(actor.deptId(), left.getDeptId());
            boolean rightDirect = Objects.equals(actor.deptId(), right.getDeptId());
            if (leftDirect != rightDirect) return leftDirect ? -1 : 1;
            String leftName = left.getSpaceName() == null ? "" : left.getSpaceName();
            String rightName = right.getSpaceName() == null ? "" : right.getSpaceName();
            return leftName.compareTo(rightName);
        });
        for (DriveSpace space : organizationSpaces)
        {
            if (authorization.canRead(actor, space))
            {
                result.add(toVo(space, actor, budgetBlocks.get(space.getDeptId())));
            }
        }
    }

    private DriveSpaceVo toVo(DriveSpace space, DriveActor actor)
    {
        return toVo(space, actor, null);
    }

    private DriveSpaceVo toVo(DriveSpace space, DriveActor actor,
            DriveOrganizationBudgetViolationVo budgetViolation)
    {
        String name = switch (space.getSpaceType())
        {
            case DriveConstants.SPACE_PERSONAL -> "我的文件";
            case DriveConstants.SPACE_COMPANY -> "公司公共盘";
            case DriveConstants.SPACE_DEPARTMENT -> properties.isOrganizationSyncEnabled()
                    ? fallbackDepartmentName(space.getSpaceName())
                    : Objects.equals(actor.deptId(), space.getDeptId())
                        ? departmentName(actor) : fallbackDepartmentName(space.getSpaceName());
            default -> space.getSpaceName();
        };
        boolean canManageQuota = actor.admin()
                || (actor.hasPermission(DriveConstants.PERMISSION_ACCESS)
                && actor.hasPermission(DriveConstants.PERMISSION_QUOTA_MANAGE));
        long quota = valueOrZero(space.getQuotaBytes());
        long used = valueOrZero(space.getUsedBytes());
        long overQuotaBytes = Math.max(0L, used - quota);
        double usagePercent = quota <= 0L ? 0D
                : Math.round((used * 1000D / quota)) / 10D;
        boolean canWrite = authorization.canWrite(actor, space)
                && budgetViolation == null;
        boolean canCleanup = authorization.canCleanup(actor, space);
        String writeBlockedMessage = budgetViolation == null ? null
                : budgetViolation.hierarchyInvalid()
                    ? budgetViolation.violationMessage()
                    : budgetViolation.budgetDeptName() + "组织树额度预算不足，"
                        + "当前超出 " + budgetViolation.exceededBytes()
                        + " 字节，该组织盘暂时只读";
        return new DriveSpaceVo(space.getSpaceId(), space.getSpaceType(), name,
                quota, used,
                space.getVersion() == null ? 0 : space.getVersion(),
                canWrite, canManageQuota,
                quotaSourceLabel(space), overQuotaBytes > 0L, overQuotaBytes,
                usagePercent, canManageQuota, space.getStatus(),
                canCleanup,
                budgetViolation == null ? null
                        : DriveErrorCodes.DRIVE_ORG_BUDGET_EXCEEDED,
                writeBlockedMessage);
    }

    private static DriveSpace newSpace(String key, String type, String name,
            Long ownerUserId, Long deptId, long quotaBytes, String quotaSourceType,
            Long quotaSourceId, String createBy)
    {
        DriveSpace space = new DriveSpace();
        space.setSpaceKey(key);
        space.setSpaceType(type);
        space.setOwnerUserId(ownerUserId);
        space.setDeptId(deptId);
        space.setSpaceName(name);
        space.setQuotaBytes(quotaBytes);
        space.setQuotaSourceType(quotaSourceType);
        space.setQuotaSourceId(quotaSourceId);
        space.setQuotaSyncedTime(new Date());
        space.setUsedBytes(0L);
        space.setStatus(DriveConstants.STATUS_ACTIVE);
        space.setVersion(0);
        space.setCreateBy(createBy == null ? "" : createBy);
        space.setCreateTime(new Date());
        return space;
    }

    private static Long requireUserId(DriveActor actor)
    {
        if (actor == null || actor.userId() == null)
        {
            throw new DriveException(DriveErrorCodes.DRIVE_ACCESS_DENIED, "无权访问该云盘空间");
        }
        return actor.userId();
    }

    private static void requireActive(DriveSpace space)
    {
        if (space == null || !DriveConstants.STATUS_ACTIVE.equals(space.getStatus()))
        {
            throw new DriveException(DriveErrorCodes.DRIVE_SPACE_NOT_FOUND, "云盘空间不存在");
        }
    }

    private static void requireReadableState(DriveSpace space)
    {
        if (space == null || (!DriveConstants.STATUS_ACTIVE.equals(space.getStatus())
                && !DriveConstants.STATUS_READ_ONLY.equals(space.getStatus())))
        {
            throw new DriveException(DriveErrorCodes.DRIVE_SPACE_NOT_FOUND, "云盘空间不存在");
        }
    }

    private static String departmentName(DriveActor actor)
    {
        String name = actor.deptName();
        return name == null || name.isBlank() ? "部门盘" : name + "部门盘";
    }

    private static String fallbackDepartmentName(String storedName)
    {
        return storedName == null || storedName.isBlank() ? "部门盘" : storedName;
    }

    private static Long valueOrZero(Long value)
    {
        return value == null ? 0L : value;
    }

    private static String quotaSourceLabel(DriveSpace space)
    {
        return switch (space.getQuotaSourceType() == null
                ? DriveConstants.QUOTA_SOURCE_LEGACY : space.getQuotaSourceType())
        {
            case DriveConstants.QUOTA_SOURCE_GLOBAL -> "全员默认";
            case DriveConstants.QUOTA_SOURCE_POST -> "岗位额度";
            case DriveConstants.QUOTA_SOURCE_USER -> "个人例外";
            case DriveConstants.QUOTA_SOURCE_ORG_TYPE -> "组织类型默认";
            case DriveConstants.QUOTA_SOURCE_ORG_OVERRIDE -> "组织单独配置";
            case DriveConstants.QUOTA_SOURCE_COMPANY -> "公司公共额度";
            default -> "原有空间额度";
        };
    }

    private static void requireVersion(DriveSpace space, int version)
    {
        if (space.getVersion() == null || space.getVersion() != version)
        {
            throw new DriveException(DriveErrorCodes.DRIVE_CONCURRENT_MODIFICATION,
                    "空间额度已被其他操作更新，请刷新后重试");
        }
    }
}

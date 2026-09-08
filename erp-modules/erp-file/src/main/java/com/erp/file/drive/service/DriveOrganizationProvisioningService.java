package com.erp.file.drive.service;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import com.erp.file.drive.config.DriveProperties;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.domain.DriveOrganization;
import com.erp.file.drive.domain.DriveOrganizationSpaceConfig;
import com.erp.file.drive.domain.DriveOrganizationTypeRule;
import com.erp.file.drive.domain.DriveSpace;
import com.erp.file.drive.exception.DriveException;
import com.erp.file.drive.mapper.DriveOrganizationMapper;
import com.erp.file.drive.mapper.DriveSpaceMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 从实时组织目录和组织盘配置幂等创建、更名或收敛空间生命周期。
 */
@Service
public class DriveOrganizationProvisioningService
{
    private static final int UPDATE_ATTEMPTS = 3;

    private final DriveOrganizationMapper organizationMapper;
    private final DriveSpaceMapper spaceMapper;
    private final DriveProperties properties;
    private final DriveOrganizationBudgetService budgetService;
    private final DriveCapacityService capacityService;

    public DriveOrganizationProvisioningService(DriveOrganizationMapper organizationMapper,
            DriveSpaceMapper spaceMapper, DriveProperties properties,
            DriveOrganizationBudgetService budgetService,
            DriveCapacityService capacityService)
    {
        this.organizationMapper = organizationMapper;
        this.spaceMapper = spaceMapper;
        this.properties = properties;
        this.budgetService = budgetService;
        this.capacityService = capacityService;
    }

    @Transactional
    public DriveSpace syncOrganization(Long deptId, String updateBy)
    {
        if (deptId == null) return null;
        DriveSpace existing = spaceMapper.selectByKey(key(deptId));
        if (!properties.isOrganizationSyncEnabled()) return existing;

        DriveOrganization organization = organizationMapper.selectOrganization(deptId);
        if (organization == null || !"0".equals(organization.getDelFlag()))
        {
            return updateExisting(existing, fallbackName(organization, existing),
                    DriveConstants.STATUS_ARCHIVED, updateBy);
        }
        if (!"0".equals(organization.getStatus()))
        {
            return updateExisting(existing, organizationName(organization),
                    DriveConstants.STATUS_READ_ONLY, updateBy);
        }

        DriveOrganizationSpaceConfig config = organizationMapper.selectConfig(deptId);
        if (config == null)
        {
            config = createAutomaticConfig(organization, updateBy);
        }
        if (config == null) return existing;

        String desiredStatus = desiredStatus(config);
        if (!Boolean.TRUE.equals(config.getEnabled()) && existing == null) return null;
        if (existing == null)
        {
            DriveSpace candidate = newSpace(organization, config, desiredStatus, updateBy);
            spaceMapper.insertIgnore(candidate);
            existing = spaceMapper.selectByKey(key(deptId));
            if (existing == null)
            {
                throw unavailable("组织盘创建失败");
            }
        }

        existing = updateExisting(existing, organizationName(organization),
                desiredStatus, updateBy);
        if (existing == null || DriveConstants.STATUS_ARCHIVED.equals(desiredStatus))
        {
            return existing;
        }
        return reconcileQuota(existing, config, updateBy);
    }

    private DriveOrganizationSpaceConfig createAutomaticConfig(
            DriveOrganization organization, String updateBy)
    {
        DriveOrganizationTypeRule rule = organization.getDeptType() == null ? null
                : organizationMapper.selectTypeRule(organization.getDeptType());
        if (rule == null || !DriveConstants.STATUS_ACTIVE.equals(rule.getStatus())
                || !Boolean.TRUE.equals(rule.getAutoEnable())
                || (Boolean.TRUE.equals(rule.getRequireActiveMember())
                    && value(organization.getActiveMemberCount()) == 0L)
                || rule.getDefaultQuotaBytes() == null || rule.getDefaultQuotaBytes() <= 0)
        {
            return null;
        }

        capacityService.lockForAllocationChange();
        DriveOrganizationSpaceConfig current = organizationMapper.selectConfig(
                organization.getDeptId());
        if (current != null) return current;

        Date now = new Date();
        DriveOrganizationSpaceConfig value = new DriveOrganizationSpaceConfig();
        value.setDeptId(organization.getDeptId());
        value.setEnabled(true);
        value.setQuotaBytes(rule.getDefaultQuotaBytes());
        value.setTreeBudgetBytes(null);
        value.setMemberWriteMode(DriveConstants.ORG_WRITE_PERMISSION_ONLY);
        value.setLifecycleStatus(DriveConstants.STATUS_ACTIVE);
        value.setConfigSource(DriveConstants.CONFIG_SOURCE_TYPE_DEFAULT);
        value.setVersion(0);
        value.setCreateBy(safeUser(updateBy));
        value.setCreateTime(now);
        value.setUpdateBy(safeUser(updateBy));
        value.setUpdateTime(now);
        value.setRemark("按组织类型规则自动创建");
        budgetService.validateTreeBudgets(value);
        try
        {
            organizationMapper.insertConfig(value);
        }
        catch (DuplicateKeyException ignored)
        {
            // 并发同步由唯一索引收敛，下面重读最终配置。
        }
        capacityService.requireCurrentAllocationsWithinPools();
        return organizationMapper.selectConfig(organization.getDeptId());
    }

    private DriveSpace updateExisting(DriveSpace existing, String name,
            String status, String updateBy)
    {
        if (existing == null) return null;
        if (!Objects.equals(existing.getSpaceName(), name)
                || !Objects.equals(existing.getStatus(), status))
        {
            spaceMapper.updateOrganizationMetadata(existing.getDeptId(), name,
                    status, safeUser(updateBy));
            DriveSpace updated = spaceMapper.selectByKey(existing.getSpaceKey());
            if (updated != null) existing = updated;
        }
        return existing;
    }

    private DriveSpace reconcileQuota(DriveSpace original,
            DriveOrganizationSpaceConfig config, String updateBy)
    {
        String sourceType = DriveConstants.CONFIG_SOURCE_TYPE_DEFAULT.equals(
                config.getConfigSource()) ? DriveConstants.QUOTA_SOURCE_ORG_TYPE
                        : DriveConstants.QUOTA_SOURCE_ORG_OVERRIDE;
        DriveSpace current = original;
        for (int attempt = 0; attempt < UPDATE_ATTEMPTS; attempt++)
        {
            if (Objects.equals(current.getQuotaBytes(), config.getQuotaBytes())
                    && Objects.equals(current.getQuotaSourceType(), sourceType)
                    && Objects.equals(current.getQuotaSourceId(), config.getDeptId()))
            {
                return current;
            }
            int version = current.getVersion() == null ? 0 : current.getVersion();
            if (spaceMapper.updateEffectiveQuota(current.getSpaceId(), config.getQuotaBytes(),
                    sourceType, config.getDeptId(), version, safeUser(updateBy)) == 1)
            {
                DriveSpace updated = spaceMapper.selectById(current.getSpaceId());
                return updated == null ? current : updated;
            }
            current = spaceMapper.selectById(current.getSpaceId());
            if (current == null) throw unavailable("组织盘在同步期间不存在");
        }
        throw new DriveException(DriveErrorCodes.DRIVE_CONCURRENT_MODIFICATION,
                "组织盘配置正在变化，请稍后重试");
    }

    private static DriveSpace newSpace(DriveOrganization organization,
            DriveOrganizationSpaceConfig config, String status, String updateBy)
    {
        Date now = new Date();
        DriveSpace value = new DriveSpace();
        value.setSpaceKey(key(organization.getDeptId()));
        value.setSpaceType(DriveConstants.SPACE_DEPARTMENT);
        value.setDeptId(organization.getDeptId());
        value.setSpaceName(organizationName(organization));
        value.setQuotaBytes(config.getQuotaBytes());
        value.setUsedBytes(0L);
        value.setQuotaSourceType(DriveConstants.CONFIG_SOURCE_TYPE_DEFAULT.equals(
                config.getConfigSource()) ? DriveConstants.QUOTA_SOURCE_ORG_TYPE
                        : DriveConstants.QUOTA_SOURCE_ORG_OVERRIDE);
        value.setQuotaSourceId(organization.getDeptId());
        value.setQuotaSyncedTime(now);
        value.setStatus(status);
        value.setVersion(0);
        value.setCreateBy(safeUser(updateBy));
        value.setCreateTime(now);
        return value;
    }

    private static String desiredStatus(DriveOrganizationSpaceConfig config)
    {
        if (DriveConstants.STATUS_ARCHIVED.equals(config.getLifecycleStatus()))
        {
            return DriveConstants.STATUS_ARCHIVED;
        }
        if (!Boolean.TRUE.equals(config.getEnabled())
                || DriveConstants.STATUS_READ_ONLY.equals(config.getLifecycleStatus()))
        {
            return DriveConstants.STATUS_READ_ONLY;
        }
        return DriveConstants.STATUS_ACTIVE;
    }

    private static String organizationName(DriveOrganization organization)
    {
        String name = organization == null ? null : organization.getDeptName();
        return name == null || name.isBlank() ? "组织盘" : name + "组织盘";
    }

    private static String fallbackName(DriveOrganization organization, DriveSpace space)
    {
        if (organization != null) return organizationName(organization);
        return space == null || space.getSpaceName() == null || space.getSpaceName().isBlank()
                ? "已删除组织盘" : space.getSpaceName();
    }

    private static String key(Long deptId)
    {
        return "DEPARTMENT:" + deptId;
    }

    private static long value(Long value)
    {
        return value == null ? 0L : value;
    }

    private static String safeUser(String value)
    {
        return value == null ? "" : value;
    }

    private static DriveException unavailable(String message)
    {
        return new DriveException(DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE, message);
    }
}

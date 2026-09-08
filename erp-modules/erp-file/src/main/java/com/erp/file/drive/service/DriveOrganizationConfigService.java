package com.erp.file.drive.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.domain.DriveActor;
import com.erp.file.drive.domain.DriveOrganization;
import com.erp.file.drive.domain.DriveOrganizationSpaceConfig;
import com.erp.file.drive.domain.DriveOrganizationTypeRule;
import com.erp.file.drive.domain.DriveSpace;
import com.erp.file.drive.domain.dto.DriveOrganizationBatchTarget;
import com.erp.file.drive.domain.vo.DriveOrganizationQuotaVo;
import com.erp.file.drive.domain.vo.DriveOrganizationBudgetViolationVo;
import com.erp.file.drive.exception.DriveException;
import com.erp.file.drive.mapper.DriveOrganizationMapper;
import com.erp.file.drive.mapper.DriveSpaceMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 保存单组织盘额度、组织树预算和成员写入模式，并同时校验所有上级预算与全局组织池。
 */
@Service
public class DriveOrganizationConfigService
{
    private final DriveOrganizationMapper organizationMapper;
    private final DriveSpaceMapper spaceMapper;
    private final DriveOrganizationScopeService scopeService;
    private final DriveOrganizationProvisioningService provisioningService;
    private final DriveOrganizationBudgetService budgetService;
    private final DriveCapacityService capacityService;
    private final DriveAuthorizationService authorization;

    public DriveOrganizationConfigService(DriveOrganizationMapper organizationMapper,
            DriveSpaceMapper spaceMapper, DriveOrganizationScopeService scopeService,
            DriveOrganizationProvisioningService provisioningService,
            DriveOrganizationBudgetService budgetService,
            DriveCapacityService capacityService, DriveAuthorizationService authorization)
    {
        this.organizationMapper = organizationMapper;
        this.spaceMapper = spaceMapper;
        this.scopeService = scopeService;
        this.provisioningService = provisioningService;
        this.budgetService = budgetService;
        this.capacityService = capacityService;
        this.authorization = authorization;
    }

    public List<DriveOrganizationQuotaVo> list(DriveActor actor)
    {
        authorization.requireQuotaManage(actor);
        List<DriveOrganization> organizations = safeList(
                organizationMapper.selectAllOrganizations());
        Set<Long> allowed = actor.admin()
                ? organizations.stream().map(DriveOrganization::getDeptId)
                    .filter(Objects::nonNull).collect(Collectors.toSet())
                : scopeService.manageableDeptIds(actor);
        Map<Long, DriveOrganizationSpaceConfig> configs = safeList(
                organizationMapper.selectConfigs()).stream()
                .filter(value -> value.getDeptId() != null)
                .collect(Collectors.toMap(DriveOrganizationSpaceConfig::getDeptId,
                        Function.identity(), (left, right) -> left, LinkedHashMap::new));
        Map<Long, DriveSpace> spaces = safeList(spaceMapper.selectByType(
                DriveConstants.SPACE_DEPARTMENT)).stream()
                .filter(value -> value.getDeptId() != null)
                .collect(Collectors.toMap(DriveSpace::getDeptId, Function.identity(),
                        (left, right) -> left, LinkedHashMap::new));
        Map<String, DriveOrganizationTypeRule> typeRules = safeList(
                organizationMapper.selectTypeRules()).stream()
                .filter(value -> value.getDeptType() != null)
                .collect(Collectors.toMap(value -> value.getDeptType().toUpperCase(Locale.ROOT),
                        Function.identity(), (left, right) -> left, LinkedHashMap::new));
        Map<Long, DriveOrganizationBudgetViolationVo> budgetBlocks =
                budgetService.indexByAffectedDept(
                        budgetService.detectViolations(configs.values(), organizations));

        List<DriveOrganizationQuotaVo> result = new ArrayList<>();
        for (DriveOrganization organization : organizations)
        {
            if (organization.getDeptId() == null || !allowed.contains(organization.getDeptId()))
            {
                continue;
            }
            DriveOrganizationSpaceConfig config = configs.get(organization.getDeptId());
            DriveSpace space = spaces.get(organization.getDeptId());
            long quota = config == null ? defaultQuota(organization, typeRules)
                    : nonNegative(config.getQuotaBytes());
            long used = space == null ? 0L : nonNegative(space.getUsedBytes());
            DriveOrganizationBudgetViolationVo budgetViolation =
                    budgetBlocks.get(organization.getDeptId());
            result.add(new DriveOrganizationQuotaVo(organization.getDeptId(),
                    organization.getParentId(), organization.getAncestors(),
                    organization.getDeptName(), organization.getDeptType(),
                    organization.getStatus(), organization.getDelFlag(),
                    nonNegative(organization.getActiveMemberCount()),
                    config != null && Boolean.TRUE.equals(config.getEnabled()), quota,
                    config == null ? null : config.getTreeBudgetBytes(),
                    config == null ? DriveConstants.ORG_WRITE_PERMISSION_ONLY
                            : config.getMemberWriteMode(),
                    config == null ? DriveConstants.STATUS_DISABLED
                            : config.getLifecycleStatus(),
                    config == null ? DriveConstants.CONFIG_SOURCE_TYPE_DEFAULT
                            : config.getConfigSource(),
                    config == null || config.getVersion() == null ? 0 : config.getVersion(),
                    space == null ? null : space.getSpaceId(), used, used > quota,
                    Math.max(0L, used - quota),
                    DriveOrganizationScopeService.isActive(organization),
                    budgetViolation != null, budgetViolation));
        }
        return result;
    }

    @Transactional
    public DriveOrganizationSpaceConfig save(Long deptId, boolean enabled,
            long quotaBytes, Long treeBudgetBytes, String memberWriteMode,
            String lifecycleStatus, Integer version, String reason, DriveActor actor)
    {
        authorization.requireQuotaManage(actor);
        capacityService.lockForAllocationChange();
        requireManageable(actor, deptId);
        DriveOrganization organization = requireActiveOrganization(deptId);
        String writeMode = validateWriteMode(memberWriteMode);
        String lifecycle = validateLifecycle(lifecycleStatus);
        validateValues(quotaBytes, treeBudgetBytes, version, reason);

        DriveOrganizationSpaceConfig existing = organizationMapper.selectConfigForUpdate(deptId);
        DriveOrganizationSpaceConfig proposed = config(deptId, enabled, quotaBytes,
                treeBudgetBytes, writeMode, lifecycle, existing, version,
                reason, actor.username());
        validateTreeBudgets(proposed);

        if (existing == null)
        {
            try
            {
                if (organizationMapper.insertConfig(proposed) != 1) throw concurrentModification();
            }
            catch (DuplicateKeyException ex)
            {
                throw concurrentModification();
            }
        }
        else if (organizationMapper.updateConfig(proposed) != 1)
        {
            throw concurrentModification();
        }

        capacityService.requireCurrentAllocationsWithinPools();
        provisioningService.syncOrganization(organization.getDeptId(), actor.username());
        DriveOrganizationSpaceConfig saved = organizationMapper.selectConfig(deptId);
        if (saved == null)
        {
            throw new DriveException(DriveErrorCodes.DRIVE_ORG_CONFIG_NOT_FOUND,
                    "组织盘配置不存在");
        }
        return saved;
    }

    @Transactional
    public DriveSpace saveLegacyQuota(DriveSpace space, long quotaBytes,
            String reason, DriveActor actor)
    {
        if (space == null || space.getDeptId() == null)
        {
            throw invalid("组织盘参数无效");
        }
        DriveOrganizationSpaceConfig current = organizationMapper.selectConfig(space.getDeptId());
        boolean enabled = current == null || Boolean.TRUE.equals(current.getEnabled());
        save(space.getDeptId(), enabled, quotaBytes,
                current == null ? null : current.getTreeBudgetBytes(),
                current == null ? DriveConstants.ORG_WRITE_PERMISSION_ONLY
                        : current.getMemberWriteMode(),
                current == null ? DriveConstants.STATUS_ACTIVE
                        : current.getLifecycleStatus(),
                current == null ? 0 : current.getVersion(), reason, actor);
        DriveSpace updated = spaceMapper.selectById(space.getSpaceId());
        return updated == null ? space : updated;
    }

    @Transactional
    public List<DriveOrganizationSpaceConfig> saveBatch(
            List<DriveOrganizationBatchTarget> targets, boolean enabled,
            long quotaBytes, String memberWriteMode, String lifecycleStatus,
            String reason, DriveActor actor)
    {
        authorization.requireQuotaManage(actor);
        capacityService.lockForAllocationChange();
        String writeMode = validateWriteMode(memberWriteMode);
        String lifecycle = validateLifecycle(lifecycleStatus);
        validateValues(quotaBytes, null, 0, reason);
        if (targets == null || targets.isEmpty() || targets.size() > 200)
        {
            throw invalid("请选择 1 至 200 个组织");
        }

        Map<Long, Integer> versions = new TreeMap<>();
        for (DriveOrganizationBatchTarget target : targets)
        {
            if (target == null || target.getDeptId() == null || target.getDeptId() <= 0
                    || target.getVersion() == null || target.getVersion() < 0
                    || versions.put(target.getDeptId(), target.getVersion()) != null)
            {
                throw invalid("批量组织或版本参数无效");
            }
        }

        List<DriveOrganization> organizations = safeList(
                organizationMapper.selectAllOrganizations());
        Map<Long, DriveOrganization> orgIndex = organizations.stream()
                .filter(value -> value.getDeptId() != null)
                .collect(Collectors.toMap(DriveOrganization::getDeptId,
                        Function.identity(), (left, right) -> left, LinkedHashMap::new));
        Map<Long, DriveOrganizationSpaceConfig> projected = safeList(
                organizationMapper.selectConfigs()).stream()
                .filter(value -> value.getDeptId() != null)
                .collect(Collectors.toMap(DriveOrganizationSpaceConfig::getDeptId,
                        Function.identity(), (left, right) -> left, LinkedHashMap::new));
        Map<Long, DriveOrganizationSpaceConfig> changes = new LinkedHashMap<>();
        Set<Long> existingDeptIds = new HashSet<>();

        for (Map.Entry<Long, Integer> target : versions.entrySet())
        {
            Long deptId = target.getKey();
            requireManageable(actor, deptId);
            DriveOrganization organization = orgIndex.get(deptId);
            if (!DriveOrganizationScopeService.isActive(organization))
            {
                throw invalid("批量目标中存在已停用或已删除的组织");
            }
            DriveOrganizationSpaceConfig existing =
                    organizationMapper.selectConfigForUpdate(deptId);
            if (existing != null) existingDeptIds.add(deptId);
            DriveOrganizationSpaceConfig proposed = config(deptId, enabled,
                    quotaBytes, existing == null ? null : existing.getTreeBudgetBytes(),
                    writeMode, lifecycle, existing, target.getValue(),
                    reason, actor.username());
            projected.put(deptId, proposed);
            changes.put(deptId, proposed);
        }

        budgetService.validate(projected.values(), organizations);
        capacityService.requireOrganizationAllocationWithinPool(
                allocated(projected.values()));
        for (DriveOrganizationSpaceConfig proposed : changes.values())
        {
            if (!existingDeptIds.contains(proposed.getDeptId()))
            {
                try
                {
                    if (organizationMapper.insertConfig(proposed) != 1)
                    {
                        throw concurrentModification();
                    }
                }
                catch (DuplicateKeyException ex)
                {
                    throw concurrentModification();
                }
            }
            else if (organizationMapper.updateConfig(proposed) != 1)
            {
                throw concurrentModification();
            }
        }
        capacityService.requireCurrentAllocationsWithinPools();

        List<DriveOrganizationSpaceConfig> result = new ArrayList<>();
        for (Long deptId : versions.keySet())
        {
            provisioningService.syncOrganization(deptId, actor.username());
            DriveOrganizationSpaceConfig saved = organizationMapper.selectConfig(deptId);
            if (saved != null) result.add(saved);
        }
        return result;
    }

    public void validateTreeBudgets(DriveOrganizationSpaceConfig proposed)
    {
        budgetService.validateTreeBudgets(proposed);
    }

    private DriveOrganizationSpaceConfig config(Long deptId, boolean enabled,
            long quotaBytes, Long treeBudgetBytes, String writeMode,
            String lifecycle, DriveOrganizationSpaceConfig existing,
            Integer version, String reason, String username)
    {
        if (existing == null && version != null && version != 0) throw concurrentModification();
        if (existing != null && (version == null || !version.equals(existing.getVersion())))
        {
            throw concurrentModification();
        }
        Date now = new Date();
        DriveOrganizationSpaceConfig value = new DriveOrganizationSpaceConfig();
        value.setDeptId(deptId);
        value.setEnabled(enabled);
        value.setQuotaBytes(quotaBytes);
        value.setTreeBudgetBytes(treeBudgetBytes);
        value.setMemberWriteMode(writeMode);
        value.setLifecycleStatus(lifecycle);
        value.setConfigSource(DriveConstants.CONFIG_SOURCE_MANUAL);
        value.setVersion(existing == null ? 0 : version);
        value.setCreateBy(existing == null ? safeUser(username) : existing.getCreateBy());
        value.setCreateTime(existing == null ? now : existing.getCreateTime());
        value.setUpdateBy(safeUser(username));
        value.setUpdateTime(now);
        value.setRemark(reason.trim());
        return value;
    }

    private void requireManageable(DriveActor actor, Long deptId)
    {
        if (deptId == null || (!actor.admin() && !scopeService.canManage(actor, deptId)))
        {
            throw new DriveException(DriveErrorCodes.DRIVE_ACCESS_DENIED,
                    "无权管理该组织盘");
        }
    }

    private DriveOrganization requireActiveOrganization(Long deptId)
    {
        DriveOrganization organization = organizationMapper.selectOrganization(deptId);
        if (!DriveOrganizationScopeService.isActive(organization))
        {
            throw invalid("组织不存在、已停用或已删除");
        }
        return organization;
    }

    private static void validateValues(long quotaBytes, Long treeBudgetBytes,
            Integer version, String reason)
    {
        if (quotaBytes <= 0 || (treeBudgetBytes != null && treeBudgetBytes < 0)
                || version == null || version < 0)
        {
            throw invalid("组织盘额度、预算或版本无效");
        }
        if (reason == null || reason.isBlank() || reason.length() > 500)
        {
            throw invalid("请填写 500 字以内的调整原因");
        }
    }

    private static String validateWriteMode(String value)
    {
        String mode = normalize(value);
        if (!Set.of(DriveConstants.ORG_WRITE_PERMISSION_ONLY,
                DriveConstants.ORG_WRITE_ALL_DIRECT_MEMBERS,
                DriveConstants.ORG_WRITE_READ_ONLY).contains(mode))
        {
            throw invalid("成员写入模式无效");
        }
        return mode;
    }

    private static String validateLifecycle(String value)
    {
        String lifecycle = normalize(value);
        if (!Set.of(DriveConstants.STATUS_ACTIVE,
                DriveConstants.STATUS_READ_ONLY,
                DriveConstants.STATUS_ARCHIVED).contains(lifecycle))
        {
            throw invalid("组织盘生命周期无效");
        }
        return lifecycle;
    }

    private static long defaultQuota(DriveOrganization organization,
            Map<String, DriveOrganizationTypeRule> typeRules)
    {
        DriveOrganizationTypeRule rule = organization.getDeptType() == null ? null
                : typeRules.get(organization.getDeptType().toUpperCase(Locale.ROOT));
        return rule == null ? 0L : nonNegative(rule.getDefaultQuotaBytes());
    }

    private static String normalize(String value)
    {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static long nonNegative(Long value)
    {
        return value == null ? 0L : Math.max(0L, value);
    }

    private static long allocated(Iterable<DriveOrganizationSpaceConfig> configs)
    {
        long total = 0L;
        for (DriveOrganizationSpaceConfig value : configs)
        {
            if (Boolean.TRUE.equals(value.getEnabled())
                    && !DriveConstants.STATUS_ARCHIVED.equals(value.getLifecycleStatus()))
            {
                try
                {
                    total = Math.addExact(total, nonNegative(value.getQuotaBytes()));
                }
                catch (ArithmeticException ex)
                {
                    throw invalid("组织额度合计超出系统可支持范围");
                }
            }
        }
        return total;
    }

    private static String safeUser(String value)
    {
        return value == null ? "" : value;
    }

    private static <T> List<T> safeList(List<T> values)
    {
        return values == null ? List.of() : values;
    }

    private static DriveException invalid(String message)
    {
        return new DriveException(DriveErrorCodes.DRIVE_POLICY_INVALID, message);
    }

    private static DriveException concurrentModification()
    {
        return new DriveException(DriveErrorCodes.DRIVE_CONCURRENT_MODIFICATION,
                "组织盘配置已被其他操作更新，请刷新后重试");
    }
}

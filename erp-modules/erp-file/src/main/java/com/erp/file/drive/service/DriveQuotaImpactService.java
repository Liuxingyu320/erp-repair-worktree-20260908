package com.erp.file.drive.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.erp.file.drive.config.DriveProperties;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.domain.DriveActor;
import com.erp.file.drive.domain.DriveCapacityConfig;
import com.erp.file.drive.domain.DriveEffectiveQuota;
import com.erp.file.drive.domain.DriveOrganization;
import com.erp.file.drive.domain.DriveOrganizationSpaceConfig;
import com.erp.file.drive.domain.DriveOrganizationTypeRule;
import com.erp.file.drive.domain.DrivePersonalQuotaPolicy;
import com.erp.file.drive.domain.DriveSpace;
import com.erp.file.drive.domain.DriveUserQuotaContext;
import com.erp.file.drive.domain.dto.DriveQuotaImpactRequest;
import com.erp.file.drive.domain.dto.DriveOrganizationBatchTarget;
import com.erp.file.drive.domain.vo.DriveCapacityOverviewVo;
import com.erp.file.drive.domain.vo.DriveQuotaImpactVo;
import com.erp.file.drive.exception.DriveException;
import com.erp.file.drive.mapper.DriveCapacityMapper;
import com.erp.file.drive.mapper.DriveOrganizationMapper;
import com.erp.file.drive.mapper.DrivePersonalQuotaPolicyMapper;
import com.erp.file.drive.mapper.DriveSpaceMapper;
import org.springframework.stereotype.Service;

/**
 * 对策略、组织和容量变更做无写入模拟，哈希同时覆盖配置版本、成员、岗位和已用量快照。
 */
@Service
public class DriveQuotaImpactService
{
    private static final long CONFIG_ID = 1L;

    private final DrivePersonalQuotaPolicyMapper policyMapper;
    private final DriveOrganizationMapper organizationMapper;
    private final DriveCapacityMapper capacityMapper;
    private final DriveSpaceMapper spaceMapper;
    private final DriveOrganizationConfigService organizationConfigService;
    private final DriveOrganizationBudgetService organizationBudgetService;
    private final DriveOrganizationTypeRuleService organizationTypeRuleService;
    private final DriveOrganizationScopeService scopeService;
    private final DriveCapacityService capacityService;
    private final DriveAuthorizationService authorization;
    private final DriveProperties properties;

    public DriveQuotaImpactService(DrivePersonalQuotaPolicyMapper policyMapper,
            DriveOrganizationMapper organizationMapper, DriveCapacityMapper capacityMapper,
            DriveSpaceMapper spaceMapper,
            DriveOrganizationConfigService organizationConfigService,
            DriveOrganizationBudgetService organizationBudgetService,
            DriveOrganizationTypeRuleService organizationTypeRuleService,
            DriveOrganizationScopeService scopeService, DriveCapacityService capacityService,
            DriveAuthorizationService authorization, DriveProperties properties)
    {
        this.policyMapper = policyMapper;
        this.organizationMapper = organizationMapper;
        this.capacityMapper = capacityMapper;
        this.spaceMapper = spaceMapper;
        this.organizationConfigService = organizationConfigService;
        this.organizationBudgetService = organizationBudgetService;
        this.organizationTypeRuleService = organizationTypeRuleService;
        this.scopeService = scopeService;
        this.capacityService = capacityService;
        this.authorization = authorization;
        this.properties = properties;
    }

    public DriveQuotaImpactVo preview(DriveQuotaImpactRequest request, DriveActor actor)
    {
        authorization.requireQuotaManage(actor);
        if (request == null || request.getChangeType() == null) throw invalid("变更类型不能为空");
        String type = request.getChangeType().trim().toUpperCase(Locale.ROOT);
        return switch (type)
        {
            case DriveConstants.IMPACT_PERSONAL_POLICY -> previewPersonal(request, actor);
            case DriveConstants.IMPACT_ORGANIZATION -> previewOrganization(request, actor);
            case DriveConstants.IMPACT_ORGANIZATION_BATCH -> previewOrganizationBatch(request, actor);
            case DriveConstants.IMPACT_ORGANIZATION_TYPE_RULE -> previewOrganizationTypeRule(request);
            case DriveConstants.IMPACT_CAPACITY -> previewCapacity(request, actor);
            default -> throw invalid("不支持的影响预览类型");
        };
    }

    public void verify(DriveQuotaImpactRequest request, String impactHash, DriveActor actor)
    {
        DriveQuotaImpactVo current = preview(request, actor);
        if (impactHash == null || !MessageDigest.isEqual(
                impactHash.getBytes(StandardCharsets.UTF_8),
                current.impactHash().getBytes(StandardCharsets.UTF_8)))
        {
            throw new DriveException(DriveErrorCodes.DRIVE_IMPACT_STALE,
                    "组织、人员、用量或配置已变化，请重新预览影响");
        }
    }

    private DriveQuotaImpactVo previewPersonal(DriveQuotaImpactRequest request,
            DriveActor actor)
    {
        String subjectType = normalizeSubject(request.getSubjectType());
        Long subjectId = request.getSubjectId();
        Date now = new Date();
        boolean deleting = Boolean.TRUE.equals(request.getDeletePolicy());
        if (DriveConstants.QUOTA_SUBJECT_GLOBAL.equals(subjectType)) subjectId = 0L;
        if (subjectId == null || subjectId < 0
                || (!DriveConstants.QUOTA_SUBJECT_GLOBAL.equals(subjectType) && subjectId == 0)
                || (!deleting && (request.getQuotaBytes() == null
                    || request.getQuotaBytes() <= 0 || request.getPriority() == null
                    || request.getPriority() < 0 || request.getPriority() > 1000))
                || (!deleting && request.getExpireTime() != null
                    && (!DriveConstants.QUOTA_SUBJECT_USER.equals(subjectType)
                        || !request.getExpireTime().after(now)))
                || (!deleting && DriveConstants.QUOTA_SUBJECT_GLOBAL.equals(subjectType)
                    && request.getPriority() != 0))
        {
            throw invalid("个人额度预览参数无效");
        }

        List<DrivePersonalQuotaPolicy> policies = safeList(policyMapper.selectAllPolicies());
        List<DriveUserQuotaContext> contexts = safeList(policyMapper.selectActiveUserContexts());
        requirePersonalSubjectScope(subjectType, subjectId, contexts, actor);
        Map<Long, DriveSpace> spaces = personalSpaces();
        Map<String, DrivePersonalQuotaPolicy> beforePolicies = policyIndex(policies);
        Map<String, DrivePersonalQuotaPolicy> afterPolicies = new HashMap<>(beforePolicies);
        String targetKey = key(subjectType, subjectId);
        if (deleting)
        {
            if (DriveConstants.QUOTA_SUBJECT_GLOBAL.equals(subjectType))
            {
                throw invalid("全员默认额度不能删除");
            }
            afterPolicies.remove(targetKey);
        }
        else
        {
            DrivePersonalQuotaPolicy proposed = new DrivePersonalQuotaPolicy();
            proposed.setSubjectType(subjectType);
            proposed.setSubjectId(subjectId);
            proposed.setQuotaBytes(request.getQuotaBytes());
            proposed.setPriority(request.getPriority() == null ? 0 : request.getPriority());
            proposed.setExpireTime(request.getExpireTime());
            proposed.setStatus(DriveConstants.STATUS_ACTIVE);
            proposed.setVersion(request.getVersion() == null ? 0 : request.getVersion());
            afterPolicies.put(targetKey, proposed);
        }

        Map<Long, List<DriveUserQuotaContext>> users = contexts.stream()
                .filter(value -> value.getUserId() != null)
                .collect(Collectors.groupingBy(DriveUserQuotaContext::getUserId,
                        LinkedHashMap::new, Collectors.toList()));
        long beforeTotal = 0L;
        long afterTotal = 0L;
        int affected = 0;
        int overCount = 0;
        long overBytes = 0L;
        for (Map.Entry<Long, List<DriveUserQuotaContext>> entry : users.entrySet())
        {
            DriveEffectiveQuota before = resolve(entry.getKey(), entry.getValue(), beforePolicies, now);
            DriveEffectiveQuota after = resolve(entry.getKey(), entry.getValue(), afterPolicies, now);
            beforeTotal = safeAdd(beforeTotal, before.quotaBytes());
            afterTotal = safeAdd(afterTotal, after.quotaBytes());
            if (before.quotaBytes() != after.quotaBytes()
                    || !Objects.equals(before.sourceType(), after.sourceType())
                    || !Objects.equals(before.sourceId(), after.sourceId())) affected++;
            DriveSpace space = spaces.get(entry.getKey());
            long used = space == null ? 0L : nonNegative(space.getUsedBytes());
            if (used > after.quotaBytes())
            {
                overCount++;
                overBytes = safeAdd(overBytes, used - after.quotaBytes());
            }
        }
        List<String> warnings = new ArrayList<>();
        DriveCapacityConfig capacity = capacityMapper.selectConfig(CONFIG_ID);
        if (capacity != null && afterTotal > nonNegative(capacity.getPersonalPoolBytes()))
        {
            warnings.add("修改后个人盘逻辑分配将超出个人盘池");
        }
        String state = personalState(policies, contexts, spaces);
        return impact(request, DriveConstants.IMPACT_PERSONAL_POLICY, affected,
                beforeTotal, afterTotal, overCount, overBytes, warnings, state);
    }

    private DriveQuotaImpactVo previewOrganizationTypeRule(DriveQuotaImpactRequest request)
    {
        String deptType = request.getSubjectType() == null ? ""
                : request.getSubjectType().trim().toUpperCase(Locale.ROOT);
        String status = request.getRuleStatus() == null ? ""
                : request.getRuleStatus().trim().toUpperCase(Locale.ROOT);
        if (!List.of("GROUP", "COMPANY", "STORE", "WAREHOUSE").contains(deptType)
                || request.getEnabled() == null || request.getRequireActiveMember() == null
                || request.getQuotaBytes() == null || request.getQuotaBytes() <= 0
                || !List.of(DriveConstants.STATUS_ACTIVE,
                        DriveConstants.STATUS_DISABLED).contains(status))
        {
            throw invalid("组织类型规则预览参数无效");
        }

        List<DriveOrganizationSpaceConfig> current = safeList(
                organizationMapper.selectConfigs());
        long beforeTotal = allocatedOrganizations(current);
        DriveOrganizationTypeRule proposed = new DriveOrganizationTypeRule();
        proposed.setDeptType(deptType);
        proposed.setAutoEnable(request.getEnabled());
        proposed.setRequireActiveMember(request.getRequireActiveMember());
        proposed.setDefaultQuotaBytes(request.getQuotaBytes());
        proposed.setStatus(status);
        proposed.setVersion(request.getVersion() == null ? 0 : request.getVersion());

        DriveOrganizationTypeRuleService.Projection projection =
                organizationTypeRuleService.project(proposed);
        long afterTotal = DriveOrganizationTypeRuleService.allocated(projection.configs());
        List<String> warnings = new ArrayList<>();
        try
        {
            organizationBudgetService.validate(projection.configs(),
                    projection.organizations());
        }
        catch (DriveException ex)
        {
            if (DriveErrorCodes.DRIVE_ORG_BUDGET_EXCEEDED.equals(ex.getBusinessCode()))
            {
                warnings.add(ex.getMessage());
            }
            else throw ex;
        }
        DriveCapacityConfig capacity = capacityMapper.selectConfig(CONFIG_ID);
        if (capacity != null && afterTotal > nonNegative(capacity.getOrganizationPoolBytes()))
        {
            warnings.add("启用该规则后组织盘逻辑分配将超出组织盘池");
        }
        String state = organizationState(current, projection.organizations(),
                safeList(spaceMapper.selectByType(DriveConstants.SPACE_DEPARTMENT)))
                + typeRuleState(safeList(organizationMapper.selectTypeRules()));
        return impact(request, DriveConstants.IMPACT_ORGANIZATION_TYPE_RULE,
                Math.max(1, projection.additions()), beforeTotal, afterTotal,
                0, 0L, warnings, state);
    }

    private DriveQuotaImpactVo previewOrganization(DriveQuotaImpactRequest request,
            DriveActor actor)
    {
        Long deptId = request.getSubjectId();
        if (deptId == null || request.getQuotaBytes() == null || request.getQuotaBytes() <= 0
                || request.getEnabled() == null)
        {
            throw invalid("组织额度预览参数无效");
        }
        if (!actor.admin() && !scopeService.canManage(actor, deptId))
        {
            throw new DriveException(DriveErrorCodes.DRIVE_ACCESS_DENIED, "无权管理该组织盘");
        }

        List<DriveOrganizationSpaceConfig> configs = safeList(organizationMapper.selectConfigs());
        Map<Long, DriveOrganizationSpaceConfig> beforeMap = configs.stream()
                .filter(value -> value.getDeptId() != null)
                .collect(Collectors.toMap(DriveOrganizationSpaceConfig::getDeptId,
                        Function.identity(), (left, right) -> left, HashMap::new));
        long beforeTotal = allocatedOrganizations(beforeMap.values());
        DriveOrganizationSpaceConfig proposed = new DriveOrganizationSpaceConfig();
        proposed.setDeptId(deptId);
        proposed.setEnabled(request.getEnabled());
        proposed.setQuotaBytes(request.getQuotaBytes());
        proposed.setTreeBudgetBytes(request.getTreeBudgetBytes());
        proposed.setMemberWriteMode(request.getMemberWriteMode());
        proposed.setLifecycleStatus(request.getLifecycleStatus());
        proposed.setConfigSource(DriveConstants.CONFIG_SOURCE_MANUAL);
        proposed.setVersion(request.getVersion() == null ? 0 : request.getVersion());
        Map<Long, DriveOrganizationSpaceConfig> afterMap = new HashMap<>(beforeMap);
        afterMap.put(deptId, proposed);
        long afterTotal = allocatedOrganizations(afterMap.values());

        List<String> warnings = new ArrayList<>();
        try
        {
            organizationConfigService.validateTreeBudgets(proposed);
        }
        catch (DriveException ex)
        {
            if (DriveErrorCodes.DRIVE_ORG_BUDGET_EXCEEDED.equals(ex.getBusinessCode()))
            {
                warnings.add(ex.getMessage());
            }
            else throw ex;
        }
        DriveCapacityConfig capacity = capacityMapper.selectConfig(CONFIG_ID);
        if (capacity != null && afterTotal > nonNegative(capacity.getOrganizationPoolBytes()))
        {
            warnings.add("修改后组织盘逻辑分配将超出组织盘池");
        }
        DriveSpace target = spaceMapper.selectByKey("DEPARTMENT:" + deptId);
        long used = target == null ? 0L : nonNegative(target.getUsedBytes());
        int overCount = used > request.getQuotaBytes() ? 1 : 0;
        long overBytes = Math.max(0L, used - request.getQuotaBytes());
        String state = organizationState(configs,
                safeList(organizationMapper.selectAllOrganizations()),
                safeList(spaceMapper.selectByType(DriveConstants.SPACE_DEPARTMENT)));
        return impact(request, DriveConstants.IMPACT_ORGANIZATION,
                beforeTotal == afterTotal && beforeMap.containsKey(deptId) ? 0 : 1,
                beforeTotal, afterTotal, overCount, overBytes, warnings, state);
    }

    private DriveQuotaImpactVo previewOrganizationBatch(DriveQuotaImpactRequest request,
            DriveActor actor)
    {
        if (request.getTargets() == null || request.getTargets().isEmpty()
                || request.getTargets().size() > 200 || request.getEnabled() == null
                || request.getQuotaBytes() == null || request.getQuotaBytes() <= 0
                || request.getMemberWriteMode() == null
                || !Set.of(DriveConstants.ORG_WRITE_PERMISSION_ONLY,
                        DriveConstants.ORG_WRITE_ALL_DIRECT_MEMBERS,
                        DriveConstants.ORG_WRITE_READ_ONLY).contains(request.getMemberWriteMode())
                || request.getLifecycleStatus() == null
                || !Set.of(DriveConstants.STATUS_ACTIVE, DriveConstants.STATUS_READ_ONLY,
                        DriveConstants.STATUS_ARCHIVED).contains(request.getLifecycleStatus()))
        {
            throw invalid("组织批量额度预览参数无效");
        }

        Map<Long, Integer> targets = new TreeMap<>();
        for (DriveOrganizationBatchTarget target : request.getTargets())
        {
            if (target == null || target.getDeptId() == null || target.getDeptId() <= 0
                    || target.getVersion() == null || target.getVersion() < 0
                    || targets.put(target.getDeptId(), target.getVersion()) != null)
            {
                throw invalid("批量组织或版本参数无效");
            }
        }

        List<DriveOrganization> organizations = safeList(
                organizationMapper.selectAllOrganizations());
        Map<Long, DriveOrganization> orgIndex = organizations.stream()
                .filter(value -> value.getDeptId() != null)
                .collect(Collectors.toMap(DriveOrganization::getDeptId,
                        Function.identity(), (left, right) -> left, HashMap::new));
        List<DriveOrganizationSpaceConfig> configs = safeList(
                organizationMapper.selectConfigs());
        Map<Long, DriveOrganizationSpaceConfig> beforeMap = configs.stream()
                .filter(value -> value.getDeptId() != null)
                .collect(Collectors.toMap(DriveOrganizationSpaceConfig::getDeptId,
                        Function.identity(), (left, right) -> left, HashMap::new));
        long beforeTotal = allocatedOrganizations(beforeMap.values());
        Map<Long, DriveOrganizationSpaceConfig> afterMap = new HashMap<>(beforeMap);
        for (Map.Entry<Long, Integer> target : targets.entrySet())
        {
            if (!actor.admin() && !scopeService.canManage(actor, target.getKey()))
            {
                throw new DriveException(DriveErrorCodes.DRIVE_ACCESS_DENIED,
                        "无权管理批量目标中的组织盘");
            }
            DriveOrganization organization = orgIndex.get(target.getKey());
            if (!DriveOrganizationScopeService.isActive(organization))
            {
                throw invalid("批量目标中存在已停用或已删除的组织");
            }
            DriveOrganizationSpaceConfig current = beforeMap.get(target.getKey());
            DriveOrganizationSpaceConfig proposed = new DriveOrganizationSpaceConfig();
            proposed.setDeptId(target.getKey());
            proposed.setEnabled(request.getEnabled());
            proposed.setQuotaBytes(request.getQuotaBytes());
            proposed.setTreeBudgetBytes(current == null ? null : current.getTreeBudgetBytes());
            proposed.setMemberWriteMode(request.getMemberWriteMode());
            proposed.setLifecycleStatus(request.getLifecycleStatus());
            proposed.setConfigSource(DriveConstants.CONFIG_SOURCE_MANUAL);
            proposed.setVersion(target.getValue());
            afterMap.put(target.getKey(), proposed);
        }
        long afterTotal = allocatedOrganizations(afterMap.values());
        List<String> warnings = new ArrayList<>();
        try
        {
            organizationBudgetService.validate(afterMap.values(), organizations);
        }
        catch (DriveException ex)
        {
            if (DriveErrorCodes.DRIVE_ORG_BUDGET_EXCEEDED.equals(ex.getBusinessCode()))
            {
                warnings.add(ex.getMessage());
            }
            else throw ex;
        }
        DriveCapacityConfig capacity = capacityMapper.selectConfig(CONFIG_ID);
        if (capacity != null && afterTotal > nonNegative(capacity.getOrganizationPoolBytes()))
        {
            warnings.add("批量修改后组织盘逻辑分配将超出组织盘池");
        }

        Map<Long, DriveSpace> spaces = safeList(spaceMapper.selectByType(
                DriveConstants.SPACE_DEPARTMENT)).stream()
                .filter(value -> value.getDeptId() != null)
                .collect(Collectors.toMap(DriveSpace::getDeptId,
                        Function.identity(), (left, right) -> left, HashMap::new));
        int overCount = 0;
        long overBytes = 0L;
        for (Long deptId : targets.keySet())
        {
            DriveSpace space = spaces.get(deptId);
            long used = space == null ? 0L : nonNegative(space.getUsedBytes());
            if (used > request.getQuotaBytes())
            {
                overCount++;
                overBytes = safeAdd(overBytes, used - request.getQuotaBytes());
            }
        }
        String state = organizationState(configs, organizations,
                new ArrayList<>(spaces.values()));
        return impact(request, DriveConstants.IMPACT_ORGANIZATION_BATCH,
                targets.size(), beforeTotal, afterTotal, overCount, overBytes,
                warnings, state);
    }

    private DriveQuotaImpactVo previewCapacity(DriveQuotaImpactRequest request, DriveActor actor)
    {
        if (request.getReservePercent() == null || request.getPublicPoolBytes() == null
                || request.getPersonalPoolBytes() == null
                || request.getOrganizationPoolBytes() == null)
        {
            throw invalid("容量预览参数无效");
        }
        DriveCapacityOverviewVo before = capacityService.overview(actor);
        DriveCapacityOverviewVo after = capacityService.preview(
                request.getPhysicalCapacityBytes(), request.getReservePercent(),
                request.getPublicPoolBytes(), request.getPersonalPoolBytes(),
                request.getOrganizationPoolBytes(), request.getEnforcementMode(), actor);
        long beforeTotal = safeAdd(safeAdd(before.publicPoolBytes(),
                before.personalPoolBytes()), before.organizationPoolBytes());
        long afterTotal = safeAdd(safeAdd(after.publicPoolBytes(),
                after.personalPoolBytes()), after.organizationPoolBytes());
        String state = capacityState(before);
        return impact(request, DriveConstants.IMPACT_CAPACITY,
                beforeTotal == afterTotal ? 0 : 1, beforeTotal, afterTotal,
                0, 0L, after.warnings(), state);
    }

    private DriveQuotaImpactVo impact(DriveQuotaImpactRequest request, String type,
            int affected, long before, long after, int overCount, long overBytes,
            List<String> warnings, String state)
    {
        String hashSource = requestState(request) + '|' + state + '|' + affected + '|'
                + before + '|' + after + '|' + overCount + '|' + overBytes + '|' + warnings;
        return new DriveQuotaImpactVo(sha256(hashSource), type, affected,
                before, after, subtract(after, before), overCount, overBytes, warnings);
    }

    private DriveEffectiveQuota resolve(Long userId, List<DriveUserQuotaContext> contexts,
            Map<String, DrivePersonalQuotaPolicy> policies, Date now)
    {
        DrivePersonalQuotaPolicy user = policies.get(key(DriveConstants.QUOTA_SUBJECT_USER, userId));
        if (valid(user, now)) return effective(user);
        DrivePersonalQuotaPolicy post = contexts.stream()
                .map(DriveUserQuotaContext::getPostId).filter(Objects::nonNull)
                .map(id -> policies.get(key(DriveConstants.QUOTA_SUBJECT_POST, id)))
                .filter(value -> valid(value, now)).min(postComparator()).orElse(null);
        if (post != null) return effective(post);
        DrivePersonalQuotaPolicy global = policies.get(key(DriveConstants.QUOTA_SUBJECT_GLOBAL, 0L));
        return valid(global, now) ? effective(global)
                : new DriveEffectiveQuota(properties.getPersonalQuota(),
                        DriveConstants.QUOTA_SOURCE_GLOBAL, null, "全员默认");
    }

    private static DriveEffectiveQuota effective(DrivePersonalQuotaPolicy policy)
    {
        return new DriveEffectiveQuota(policy.getQuotaBytes(), policy.getSubjectType(),
                DriveConstants.QUOTA_SUBJECT_GLOBAL.equals(policy.getSubjectType())
                        ? null : policy.getSubjectId(), policy.getSubjectType());
    }

    private static boolean valid(DrivePersonalQuotaPolicy value, Date now)
    {
        return value != null && value.getQuotaBytes() != null && value.getQuotaBytes() > 0
                && DriveConstants.STATUS_ACTIVE.equals(value.getStatus())
                && (value.getExpireTime() == null || value.getExpireTime().after(now));
    }

    private static Comparator<DrivePersonalQuotaPolicy> postComparator()
    {
        return Comparator.comparing((DrivePersonalQuotaPolicy value) -> number(value.getPriority()))
                .reversed().thenComparing(value -> number(value.getQuotaBytes()),
                        Comparator.reverseOrder())
                .thenComparing(value -> number(value.getPolicyId()));
    }

    private static Map<String, DrivePersonalQuotaPolicy> policyIndex(
            List<DrivePersonalQuotaPolicy> policies)
    {
        Map<String, DrivePersonalQuotaPolicy> result = new HashMap<>();
        for (DrivePersonalQuotaPolicy policy : policies)
        {
            if (policy.getSubjectType() != null && policy.getSubjectId() != null)
            {
                result.put(key(policy.getSubjectType(), policy.getSubjectId()), policy);
            }
        }
        return result;
    }

    private Map<Long, DriveSpace> personalSpaces()
    {
        return safeList(spaceMapper.selectByType(DriveConstants.SPACE_PERSONAL)).stream()
                .filter(value -> value.getOwnerUserId() != null)
                .collect(Collectors.toMap(DriveSpace::getOwnerUserId, Function.identity(),
                        (left, right) -> left, HashMap::new));
    }

    private static long allocatedOrganizations(
            java.util.Collection<DriveOrganizationSpaceConfig> configs)
    {
        long result = 0L;
        for (DriveOrganizationSpaceConfig config : configs)
        {
            if (Boolean.TRUE.equals(config.getEnabled())
                    && !DriveConstants.STATUS_ARCHIVED.equals(config.getLifecycleStatus()))
            {
                result = safeAdd(result, nonNegative(config.getQuotaBytes()));
            }
        }
        return result;
    }

    private static String personalState(List<DrivePersonalQuotaPolicy> policies,
            List<DriveUserQuotaContext> contexts, Map<Long, DriveSpace> spaces)
    {
        StringBuilder result = new StringBuilder();
        policies.stream().sorted(Comparator.comparing(value -> key(value.getSubjectType(),
                value.getSubjectId()))).forEach(value -> result.append(key(value.getSubjectType(),
                        value.getSubjectId())).append(':').append(value.getQuotaBytes()).append(':')
                .append(value.getPriority()).append(':').append(time(value.getExpireTime())).append(':')
                .append(value.getStatus()).append(':').append(value.getVersion()).append(';'));
        contexts.forEach(value -> result.append('u').append(value.getUserId()).append('p')
                .append(value.getPostId()).append(';'));
        spaces.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> result
                .append('s').append(entry.getKey()).append(':')
                .append(entry.getValue().getUsedBytes()).append(':')
                .append(entry.getValue().getVersion()).append(';'));
        return result.toString();
    }

    private static String organizationState(List<DriveOrganizationSpaceConfig> configs,
            List<DriveOrganization> organizations, List<DriveSpace> spaces)
    {
        StringBuilder result = new StringBuilder();
        configs.stream().sorted(Comparator.comparing(DriveOrganizationSpaceConfig::getDeptId))
                .forEach(value -> result.append('c').append(value.getDeptId()).append(':')
                        .append(value.getEnabled()).append(':').append(value.getQuotaBytes()).append(':')
                        .append(value.getTreeBudgetBytes()).append(':').append(value.getMemberWriteMode())
                        .append(':').append(value.getLifecycleStatus()).append(':')
                        .append(value.getVersion()).append(';'));
        organizations.stream().filter(value -> value.getDeptId() != null)
                .sorted(Comparator.comparing(DriveOrganization::getDeptId))
                .forEach(value -> result.append('o').append(value.getDeptId()).append(':')
                        .append(value.getAncestors()).append(':').append(value.getStatus()).append(':')
                        .append(value.getDelFlag()).append(':').append(value.getActiveMemberCount()).append(';'));
        spaces.stream().filter(value -> value.getDeptId() != null)
                .sorted(Comparator.comparing(DriveSpace::getDeptId))
                .forEach(value -> result.append('s').append(value.getDeptId()).append(':')
                        .append(value.getUsedBytes()).append(':').append(value.getVersion()).append(';'));
        return result.toString();
    }

    private static String capacityState(DriveCapacityOverviewVo value)
    {
        return value.physicalCapacityBytes() + ":" + value.reservePercent() + ":"
                + value.publicPoolBytes() + ":" + value.personalPoolBytes() + ":"
                + value.organizationPoolBytes() + ":" + value.publicAllocatedBytes() + ":"
                + value.personalAllocatedBytes() + ":" + value.organizationAllocatedBytes() + ":"
                + value.actualUsedBytes() + ":" + value.version();
    }

    private static String typeRuleState(List<DriveOrganizationTypeRule> rules)
    {
        StringBuilder result = new StringBuilder();
        rules.stream().filter(value -> value.getDeptType() != null)
                .sorted(Comparator.comparing(DriveOrganizationTypeRule::getDeptType))
                .forEach(value -> result.append('r').append(value.getDeptType()).append(':')
                        .append(value.getAutoEnable()).append(':')
                        .append(value.getRequireActiveMember()).append(':')
                        .append(value.getDefaultQuotaBytes()).append(':')
                        .append(value.getStatus()).append(':')
                        .append(value.getVersion()).append(';'));
        return result.toString();
    }

    private static String requestState(DriveQuotaImpactRequest value)
    {
        return String.join("|", text(value.getChangeType()), text(value.getSubjectType()),
                text(value.getSubjectId()), text(value.getDeletePolicy()), text(value.getQuotaBytes()),
                text(value.getPriority()), text(time(value.getExpireTime())), text(value.getEnabled()),
                text(value.getRequireActiveMember()), text(value.getRuleStatus()),
                text(value.getTreeBudgetBytes()), text(value.getMemberWriteMode()),
                text(value.getLifecycleStatus()), text(value.getPhysicalCapacityBytes()),
                text(value.getReservePercent()), text(value.getPublicPoolBytes()),
                text(value.getPersonalPoolBytes()), text(value.getOrganizationPoolBytes()),
                text(value.getEnforcementMode()), text(value.getVersion()),
                batchTargets(value.getTargets()));
    }

    private static String batchTargets(List<DriveOrganizationBatchTarget> targets)
    {
        if (targets == null) return "";
        return targets.stream().filter(Objects::nonNull)
                .sorted(Comparator.comparing(DriveOrganizationBatchTarget::getDeptId,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(value -> text(value.getDeptId()) + ':' + text(value.getVersion()))
                .collect(Collectors.joining(","));
    }

    private static String normalizeSubject(String value)
    {
        String type = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!List.of(DriveConstants.QUOTA_SUBJECT_GLOBAL,
                DriveConstants.QUOTA_SUBJECT_POST,
                DriveConstants.QUOTA_SUBJECT_USER).contains(type))
        {
            throw invalid("个人额度策略类型无效");
        }
        return type;
    }

    private static String key(String type, Long id)
    {
        return type + ':' + id;
    }

    private void requirePersonalSubjectScope(String subjectType, Long subjectId,
            List<DriveUserQuotaContext> contexts, DriveActor actor)
    {
        if (!DriveConstants.QUOTA_SUBJECT_USER.equals(subjectType)
                || actor == null || actor.admin()) return;
        Long deptId = contexts.stream()
                .filter(value -> value != null && Objects.equals(subjectId, value.getUserId()))
                .map(DriveUserQuotaContext::getDeptId)
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
        if (deptId == null || !scopeService.canManage(actor, deptId))
        {
            throw new DriveException(DriveErrorCodes.DRIVE_ACCESS_DENIED,
                    "无权预览该用户的个人额度变更");
        }
    }

    private static long number(Number value)
    {
        return value == null ? 0L : value.longValue();
    }

    private static long nonNegative(Long value)
    {
        return value == null ? 0L : Math.max(0L, value);
    }

    private static long safeAdd(long left, long right)
    {
        try { return Math.addExact(left, right); }
        catch (ArithmeticException ex) { throw invalid("额度合计超出系统可支持范围"); }
    }

    private static long subtract(long left, long right)
    {
        try { return Math.subtractExact(left, right); }
        catch (ArithmeticException ex) { throw invalid("额度变化超出系统可支持范围"); }
    }

    private static long time(Date value)
    {
        return value == null ? 0L : value.getTime();
    }

    private static String text(Object value)
    {
        return value == null ? "" : String.valueOf(value);
    }

    private static String sha256(String value)
    {
        try
        {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        }
        catch (NoSuchAlgorithmException ex)
        {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static <T> List<T> safeList(List<T> values)
    {
        return values == null ? List.of() : values;
    }

    private static DriveException invalid(String message)
    {
        return new DriveException(DriveErrorCodes.DRIVE_POLICY_INVALID, message);
    }
}

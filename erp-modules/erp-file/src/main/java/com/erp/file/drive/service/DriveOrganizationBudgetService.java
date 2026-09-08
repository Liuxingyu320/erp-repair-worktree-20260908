package com.erp.file.drive.service;

import java.util.Collection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.domain.DriveOrganization;
import com.erp.file.drive.domain.DriveOrganizationSpaceConfig;
import com.erp.file.drive.domain.vo.DriveOrganizationBudgetViolationVo;
import com.erp.file.drive.exception.DriveException;
import com.erp.file.drive.mapper.DriveOrganizationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 统一校验组织树预算，供单组织修改、类型规则和自动建盘共用。
 */
@Service
public class DriveOrganizationBudgetService
{
    private static final Logger log = LoggerFactory.getLogger(
            DriveOrganizationBudgetService.class);

    private final DriveOrganizationMapper organizationMapper;
    private volatile List<Long> lastInvalidHierarchyIds = List.of();

    public DriveOrganizationBudgetService(DriveOrganizationMapper organizationMapper)
    {
        this.organizationMapper = organizationMapper;
    }

    public void validateTreeBudgets(DriveOrganizationSpaceConfig proposed)
    {
        Map<Long, DriveOrganizationSpaceConfig> configs = indexConfigs(
                safeList(organizationMapper.selectConfigs()));
        if (proposed != null && proposed.getDeptId() != null)
        {
            configs.put(proposed.getDeptId(), proposed);
        }
        validate(configs.values(), safeList(organizationMapper.selectAllOrganizations()));
    }

    public void validate(Collection<DriveOrganizationSpaceConfig> configs,
            Collection<DriveOrganization> organizations)
    {
        List<DriveOrganizationBudgetViolationVo> violations = detectViolations(
                configs, organizations);
        if (!violations.isEmpty())
        {
            throw exceeded(violations.get(0));
        }
    }

    /** 一次读取当前目录与配置，识别所有因外部组织调整产生的预算漂移。 */
    public List<DriveOrganizationBudgetViolationVo> detectViolations()
    {
        return detectViolations(safeList(organizationMapper.selectConfigs()),
                safeList(organizationMapper.selectAllOrganizations()));
    }

    public List<DriveOrganizationBudgetViolationVo> detectViolations(
            Collection<DriveOrganizationSpaceConfig> configs,
            Collection<DriveOrganization> organizations)
    {
        Map<Long, DriveOrganization> orgIndex = safeCollection(organizations).stream()
                .filter(value -> value != null && value.getDeptId() != null)
                .collect(Collectors.toMap(DriveOrganization::getDeptId,
                        Function.identity(), (left, right) -> left, HashMap::new));
        Map<Long, DriveOrganizationSpaceConfig> configIndex = indexConfigs(configs);
        List<DriveOrganizationBudgetViolationVo> violations = new ArrayList<>();

        DriveOrganizationBudgetViolationVo hierarchyViolation = hierarchyViolation(
                orgIndex, configIndex);

        for (DriveOrganizationSpaceConfig budgetConfig : configIndex.values())
        {
            if (budgetConfig.getTreeBudgetBytes() == null) continue;
            long allocated = 0L;
            Set<Long> affected = new LinkedHashSet<>();
            for (DriveOrganizationSpaceConfig allocation : configIndex.values())
            {
                if (!isCountedAllocation(allocation)) continue;
                DriveOrganization allocatedOrg = orgIndex.get(allocation.getDeptId());
                if (!DriveOrganizationScopeService.isActive(allocatedOrg)
                        || !isInTree(allocatedOrg, budgetConfig.getDeptId()))
                {
                    continue;
                }
                allocated = safeAdd(allocated, nonNegative(allocation.getQuotaBytes()));
                affected.add(allocation.getDeptId());
            }
            long budget = nonNegative(budgetConfig.getTreeBudgetBytes());
            if (allocated <= budget) continue;
            DriveOrganization budgetOrg = orgIndex.get(budgetConfig.getDeptId());
            String name = budgetOrg == null || budgetOrg.getDeptName() == null
                    || budgetOrg.getDeptName().isBlank()
                    ? String.valueOf(budgetConfig.getDeptId()) : budgetOrg.getDeptName();
            List<Long> affectedIds = affected.stream().sorted().toList();
            violations.add(new DriveOrganizationBudgetViolationVo(
                    budgetConfig.getDeptId(), name, budget, allocated,
                    allocated - budget, affectedIds));
        }
        violations.sort(Comparator
                .comparingInt((DriveOrganizationBudgetViolationVo value) ->
                        depth(orgIndex.get(value.budgetDeptId())))
                .reversed()
                .thenComparing(DriveOrganizationBudgetViolationVo::budgetDeptId,
                        Comparator.nullsLast(Long::compareTo)));
        if (hierarchyViolation != null)
        {
            violations.add(0, hierarchyViolation);
        }
        return List.copyOf(violations);
    }

    /** 对同时落入多层违规预算的组织，返回距离它最近的预算根。 */
    public DriveOrganizationBudgetViolationVo violationForDept(Long deptId)
    {
        return deptId == null ? null : violationsByDept().get(deptId);
    }

    /** 给列表类接口一次计算阻断集合，避免每行重复查询。 */
    public Map<Long, DriveOrganizationBudgetViolationVo> violationsByDept()
    {
        return indexByAffectedDept(detectViolations());
    }

    public Map<Long, DriveOrganizationBudgetViolationVo> indexByAffectedDept(
            Collection<DriveOrganizationBudgetViolationVo> violations)
    {
        Map<Long, DriveOrganizationBudgetViolationVo> result = new LinkedHashMap<>();
        for (DriveOrganizationBudgetViolationVo violation : safeCollection(violations))
        {
            for (Long deptId : violation.affectedDeptIds())
            {
                if (deptId != null) result.putIfAbsent(deptId, violation);
            }
        }
        return Map.copyOf(result);
    }

    public void requireWriteAllowed(Long deptId)
    {
        DriveOrganizationBudgetViolationVo violation = violationForDept(deptId);
        if (violation != null) throw exceeded(violation);
    }

    private static Map<Long, DriveOrganizationSpaceConfig> indexConfigs(
            Collection<DriveOrganizationSpaceConfig> configs)
    {
        return safeCollection(configs).stream()
                .filter(value -> value != null && value.getDeptId() != null)
                .collect(Collectors.toMap(DriveOrganizationSpaceConfig::getDeptId,
                        Function.identity(), (left, right) -> right, HashMap::new));
    }

    private static boolean isCountedAllocation(DriveOrganizationSpaceConfig value)
    {
        return value != null && Boolean.TRUE.equals(value.getEnabled())
                && !DriveConstants.STATUS_ARCHIVED.equals(value.getLifecycleStatus());
    }

    private static boolean isInTree(DriveOrganization organization, Long budgetDeptId)
    {
        return organization != null && budgetDeptId != null
                && (Objects.equals(organization.getDeptId(), budgetDeptId)
                    || DriveOrganizationScopeService.containsAncestor(
                            organization.getAncestors(), budgetDeptId));
    }

    private static int depth(DriveOrganization organization)
    {
        if (organization == null || organization.getAncestors() == null
                || organization.getAncestors().isBlank()) return 0;
        int result = 0;
        for (String token : organization.getAncestors().split(","))
        {
            if (!token.isBlank() && !"0".equals(token.trim())) result++;
        }
        return result;
    }

    /**
     * sys_dept 是外部权威目录。遇到缺失祖先、重复节点、自环/互环或 parent_id
     * 与 ancestors 不一致时，无法可靠计算任何树预算，因此保守阻断当前全部有效
     * 组织盘写入，但不改变生命周期，目录修复后会自动恢复。
     */
    private DriveOrganizationBudgetViolationVo hierarchyViolation(
            Map<Long, DriveOrganization> orgIndex,
            Map<Long, DriveOrganizationSpaceConfig> configIndex)
    {
        Map<Long, List<Long>> chains = new HashMap<>();
        Set<Long> invalid = new LinkedHashSet<>();
        for (DriveOrganization organization : orgIndex.values())
        {
            if (!DriveOrganizationScopeService.isActive(organization)) continue;
            List<Long> chain = parseAncestors(organization);
            if (chain == null) invalid.add(organization.getDeptId());
            else chains.put(organization.getDeptId(), chain);
        }
        for (Map.Entry<Long, List<Long>> entry : chains.entrySet())
        {
            DriveOrganization organization = orgIndex.get(entry.getKey());
            List<Long> chain = entry.getValue();
            for (int index = 0; index < chain.size(); index++)
            {
                Long ancestorId = chain.get(index);
                DriveOrganization ancestor = orgIndex.get(ancestorId);
                List<Long> ancestorChain = chains.get(ancestorId);
                if (!DriveOrganizationScopeService.isActive(ancestor)
                        || ancestorChain == null
                        || !ancestorChain.equals(chain.subList(0, index)))
                {
                    invalid.add(organization.getDeptId());
                    break;
                }
            }
            Long parentId = organization.getParentId();
            Long expectedParent = chain.isEmpty() ? 0L : chain.get(chain.size() - 1);
            if (parentId != null && !Objects.equals(parentId, expectedParent))
            {
                invalid.add(organization.getDeptId());
            }
        }
        for (DriveOrganizationSpaceConfig config : configIndex.values())
        {
            if (isCountedAllocation(config)
                    && !orgIndex.containsKey(config.getDeptId()))
            {
                invalid.add(config.getDeptId());
            }
        }
        if (invalid.isEmpty())
        {
            if (!lastInvalidHierarchyIds.isEmpty())
            {
                log.info("drive_organization_hierarchy_recovered previousInvalidDeptCount={}",
                        lastInvalidHierarchyIds.size());
                lastInvalidHierarchyIds = List.of();
            }
            return null;
        }

        List<Long> invalidIds = invalid.stream().sorted().toList();
        List<Long> affected = configIndex.values().stream()
                .filter(DriveOrganizationBudgetService::isCountedAllocation)
                .filter(config -> DriveOrganizationScopeService.isActive(
                        orgIndex.get(config.getDeptId())))
                .map(DriveOrganizationSpaceConfig::getDeptId)
                .filter(Objects::nonNull).distinct().sorted().toList();
        Long firstId = invalidIds.get(0);
        DriveOrganization first = orgIndex.get(firstId);
        String firstName = first == null || first.getDeptName() == null
                || first.getDeptName().isBlank()
                ? String.valueOf(firstId) : first.getDeptName();
        String message = "组织目录层级数据异常（组织 " + firstName
                + " 等 " + invalidIds.size()
                + " 项），为防止额度越界，组织盘暂时只读；请管理员修复 parent_id 与 ancestors";
        if (!invalidIds.equals(lastInvalidHierarchyIds))
        {
            log.error("drive_organization_hierarchy_invalid invalidDeptIds={} blockedDeptCount={}",
                    invalidIds, affected.size());
            lastInvalidHierarchyIds = invalidIds;
        }
        return new DriveOrganizationBudgetViolationVo(firstId, firstName,
                0L, 0L, 0L, affected, true, message);
    }

    /** 返回不含根标记 0 的祖先链；异常返回 null，全程无递归。 */
    private static List<Long> parseAncestors(DriveOrganization organization)
    {
        String raw = organization == null ? null : organization.getAncestors();
        if (raw == null || raw.isBlank()) return null;
        String[] tokens = raw.split(",", -1);
        if (tokens.length == 0 || !"0".equals(tokens[0].trim())) return null;
        List<Long> result = new ArrayList<>(Math.max(0, tokens.length - 1));
        Set<Long> seen = new LinkedHashSet<>();
        for (int index = 1; index < tokens.length; index++)
        {
            String token = tokens[index].trim();
            if (token.isEmpty()) return null;
            final long ancestorId;
            try
            {
                ancestorId = Long.parseLong(token);
            }
            catch (NumberFormatException ex)
            {
                return null;
            }
            if (ancestorId <= 0L
                    || Objects.equals(organization.getDeptId(), ancestorId)
                    || !seen.add(ancestorId))
            {
                return null;
            }
            result.add(ancestorId);
        }
        return List.copyOf(result);
    }

    private static DriveException exceeded(DriveOrganizationBudgetViolationVo violation)
    {
        if (violation.hierarchyInvalid())
        {
            return new DriveException(DriveErrorCodes.DRIVE_ORG_BUDGET_EXCEEDED,
                    violation.violationMessage());
        }
        return new DriveException(DriveErrorCodes.DRIVE_ORG_BUDGET_EXCEEDED,
                violation.budgetDeptName() + "的组织树已分配 "
                        + violation.allocatedBytes() + " 字节，超出预算 "
                        + violation.exceededBytes()
                        + " 字节，请上调上级预算或降低下级额度");
    }

    private static long safeAdd(long left, long right)
    {
        try
        {
            return Math.addExact(left, right);
        }
        catch (ArithmeticException ex)
        {
            throw new DriveException(DriveErrorCodes.DRIVE_POLICY_INVALID,
                    "组织额度合计超出系统可支持范围");
        }
    }

    private static long nonNegative(Long value)
    {
        return value == null ? 0L : Math.max(0L, value);
    }

    private static <T> List<T> safeList(List<T> values)
    {
        return values == null ? List.of() : values;
    }

    private static <T> Collection<T> safeCollection(Collection<T> values)
    {
        return values == null ? List.of() : values;
    }
}

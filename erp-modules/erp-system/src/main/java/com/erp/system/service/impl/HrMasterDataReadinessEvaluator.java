package com.erp.system.service.impl;

import static com.erp.system.constant.HrMasterDataIssueCodes.COMPANY_NODE_MISSING;
import static com.erp.system.constant.HrMasterDataIssueCodes.DEPT_LEADER_MISSING;
import static com.erp.system.constant.HrMasterDataIssueCodes.DEPT_LEADER_UNRESOLVED;
import static com.erp.system.constant.HrMasterDataIssueCodes.DICTIONARY_ROUTE_MISSING;
import static com.erp.system.constant.HrMasterDataIssueCodes.EMPLOYEE_PROFILE_MISSING;
import static com.erp.system.constant.HrMasterDataIssueCodes.ORG_PATH_INVALID;
import static com.erp.system.constant.HrMasterDataIssueCodes.POSITION_CONFIG_MISSING;
import static com.erp.system.constant.HrMasterDataIssueCodes.POSITION_CONFIG_ROLE_MISSING;
import static com.erp.system.constant.HrMasterDataIssueCodes.POST_MISSING_OR_DISABLED;
import static com.erp.system.constant.HrMasterDataIssueCodes.STORE_MAPPING_MISSING;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.erp.system.api.domain.SysDept;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.domain.SysUserProfile;
import com.erp.system.constant.HrMasterDataIssueCodes;
import com.erp.system.domain.HrOnboardingPositionConfig;
import com.erp.system.domain.SysPost;
import com.erp.system.domain.SysUserPost;
import com.erp.system.domain.vo.HrMasterDataIssueVo;

/**
 * Pure HR master-data evaluator. All database and data-scope access happens
 * before this class receives an immutable-by-contract snapshot.
 */
public class HrMasterDataReadinessEvaluator
{
    private static final Set<String> NON_DEPARTMENT_TYPES = Set.of(
            "GROUP", "STORE", "WAREHOUSE");

    public HrMasterDataScanResult evaluate(Snapshot snapshot)
    {
        Map<Long, SysDept> departmentById = safe(snapshot.allDepartments())
                .stream()
                .filter(value -> value != null && value.getDeptId() != null)
                .collect(Collectors.toMap(SysDept::getDeptId,
                        Function.identity(), (left, right) -> left,
                        LinkedHashMap::new));
        List<SysUser> employees = safe(snapshot.employees());
        Map<Long, Set<Long>> employeeIdsByDept = employees.stream()
                .filter(value -> value != null && value.getUserId() != null
                        && value.getDeptId() != null)
                .collect(Collectors.groupingBy(SysUser::getDeptId,
                        LinkedHashMap::new,
                        Collectors.mapping(SysUser::getUserId,
                                Collectors.toCollection(LinkedHashSet::new))));
        Map<Long, SysUser> validLeaderById = employees.stream()
                .filter(value -> value != null && value.getUserId() != null
                        && "0".equals(value.getStatus())
                        && !blank(value.getNickName()))
                .collect(Collectors.toMap(SysUser::getUserId,
                        Function.identity(), (left, right) -> left,
                        LinkedHashMap::new));

        HrMasterDataScanResult result = new HrMasterDataScanResult();
        evaluateOrganization(safe(snapshot.scopedDepartments()),
                departmentById, employeeIdsByDept, validLeaderById, result);
        evaluateMissingProfiles(employees, departmentById, result);
        evaluatePostsAndConfiguration(snapshot, departmentById, result);
        evaluateDictionaryRoutes(snapshot.dictionaryRoutes(), result);
        return result;
    }

    private void evaluateMissingProfiles(List<SysUser> employees,
            Map<Long, SysDept> departments, HrMasterDataScanResult result)
    {
        for (SysUser employee : employees)
        {
            if (employee == null || employee.getUserId() == null
                    || employee.hasProfile())
            {
                continue;
            }
            SysDept department = departments.get(employee.getDeptId());
            result.add(issue(EMPLOYEE_PROFILE_MISSING, "EMPLOYEE",
                    employee.getUserId(), employee.getNickName(),
                    employee.getDeptId(),
                    department == null ? null : department.getDeptName(), null,
                    1, "账号存在，但尚未建立 sys_user_profile 员工档案",
                    "/hr/employee?userId=" + employee.getUserId()
                            + "&action=initializeProfile"),
                    Set.of(employee.getUserId()));
        }
    }

    private void evaluateOrganization(List<SysDept> scoped,
            Map<Long, SysDept> all, Map<Long, Set<Long>> employeeIdsByDept,
            Map<Long, SysUser> validLeaderById,
            HrMasterDataScanResult result)
    {
        Map<Long, Set<Long>> missingLeaderAffected = new LinkedHashMap<>();
        Map<Long, SysDept> missingLeaderResource = new LinkedHashMap<>();
        Map<Long, Set<Long>> unresolvedLeaderAffected = new LinkedHashMap<>();
        Map<Long, SysDept> unresolvedLeaderResource = new LinkedHashMap<>();
        Map<Long, String> unresolvedLeaderDetail = new LinkedHashMap<>();
        for (SysDept department : scoped)
        {
            if (department == null || department.getDeptId() == null)
            {
                continue;
            }
            Set<Long> affected = employeeIdsByDept.getOrDefault(
                    department.getDeptId(), Collections.emptySet());
            OrgPath path = resolvePath(department.getDeptId(), all);
            if (!path.valid())
            {
                result.add(issue(ORG_PATH_INVALID, "DEPARTMENT",
                        department.getDeptId(), department.getDeptName(),
                        department.getDeptId(), department.getDeptName(), null,
                        affected.size(), path.reason(),
                        "/system/dept?deptId=" + department.getDeptId()),
                        affected);
                continue;
            }
            if ("GROUP".equals(type(department)))
            {
                Long parentId = department.getParentId() == null ? 0L
                        : department.getParentId();
                if (path.nodes().size() == 1 && Long.valueOf(0L)
                        .equals(parentId))
                {
                    continue;
                }
                result.add(issue(ORG_PATH_INVALID, "DEPARTMENT",
                        department.getDeptId(), department.getDeptName(),
                        department.getDeptId(), department.getDeptName(), null,
                        affected.size(), "集团节点必须是父级为0的组织根节点",
                        "/system/dept?deptId=" + department.getDeptId()),
                        affected);
                continue;
            }
            int groupIndex = nearestTypeIndex(path.nodes(), "GROUP");
            int companyIndex = groupIndex + 1;
            if (groupIndex < 0 || companyIndex >= path.nodes().size()
                    || !isDepartmentLayer(path.nodes().get(companyIndex)))
            {
                result.add(issue(COMPANY_NODE_MISSING, "DEPARTMENT",
                        department.getDeptId(), department.getDeptName(),
                        department.getDeptId(), department.getDeptName(), null,
                        affected.size(),
                        "该组织路径无法按“集团 → 公司”规则识别公司节点",
                        "/system/dept?deptId=" + department.getDeptId()),
                        affected);
                continue;
            }
            int storeIndex = nearestTypeIndex(path.nodes(), "STORE");
            long storeCount = path.nodes().stream()
                    .filter(value -> "STORE".equals(type(value))).count();
            if (storeCount > 1
                    || (storeIndex >= 0
                            && storeIndex < path.nodes().size() - 1))
            {
                result.add(issue(STORE_MAPPING_MISSING, "DEPARTMENT",
                        department.getDeptId(), department.getDeptName(),
                        department.getDeptId(), department.getDeptName(), null,
                        affected.size(),
                        storeCount > 1 ? "组织路径中存在多个门店节点"
                                : "门店节点下仍挂有员工所属部门，门店层级无法稳定派生",
                        "/system/dept?deptId=" + department.getDeptId()),
                        affected);
            }

            int levelEnd = storeIndex > companyIndex ? storeIndex
                    : path.nodes().size();
            List<SysDept> layers = departmentLayers(path.nodes(),
                    companyIndex + 1, levelEnd);
            if (layers.size() > 3)
            {
                result.add(issue(ORG_PATH_INVALID, "DEPARTMENT",
                        department.getDeptId(), department.getDeptName(),
                        department.getDeptId(), department.getDeptName(), null,
                        affected.size(), "公司与门店之间的部门层级超过系统支持的3级",
                        "/system/dept?deptId=" + department.getDeptId()),
                        affected);
            }
            SysDept leaderOwner = layers.isEmpty()
                    ? path.nodes().get(companyIndex) : layers.get(0);
            String leaderProblem = leaderProblem(leaderOwner,
                    validLeaderById);
            if ("MISSING".equals(leaderProblem))
            {
                missingLeaderResource.putIfAbsent(leaderOwner.getDeptId(),
                        leaderOwner);
                missingLeaderAffected.computeIfAbsent(
                        leaderOwner.getDeptId(), ignored -> new LinkedHashSet<>())
                        .addAll(affected);
            }
            else if (leaderProblem != null)
            {
                unresolvedLeaderResource.putIfAbsent(leaderOwner.getDeptId(),
                        leaderOwner);
                unresolvedLeaderDetail.putIfAbsent(leaderOwner.getDeptId(),
                        leaderProblem);
                unresolvedLeaderAffected.computeIfAbsent(
                        leaderOwner.getDeptId(), ignored -> new LinkedHashSet<>())
                        .addAll(affected);
            }
        }
        for (Map.Entry<Long, SysDept> entry : missingLeaderResource.entrySet())
        {
            SysDept department = entry.getValue();
            Set<Long> affected = missingLeaderAffected.getOrDefault(
                    entry.getKey(), Collections.emptySet());
            result.add(issue(DEPT_LEADER_MISSING, "DEPARTMENT",
                    department.getDeptId(), department.getDeptName(),
                    department.getDeptId(), department.getDeptName(), null,
                    affected.size(), "负责该组织范围员工的部门节点未配置负责人",
                    "/system/dept?deptId=" + department.getDeptId()), affected);
        }
        for (Map.Entry<Long, SysDept> entry : unresolvedLeaderResource
                .entrySet())
        {
            SysDept department = entry.getValue();
            Set<Long> affected = unresolvedLeaderAffected.getOrDefault(
                    entry.getKey(), Collections.emptySet());
            result.add(issue(DEPT_LEADER_UNRESOLVED, "DEPARTMENT",
                    department.getDeptId(), department.getDeptName(),
                    department.getDeptId(), department.getDeptName(), null,
                    affected.size(), unresolvedLeaderDetail.get(entry.getKey()),
                    "/system/dept?deptId=" + department.getDeptId()), affected);
        }
    }

    private String leaderProblem(SysDept department,
            Map<Long, SysUser> validLeaderById)
    {
        Long leaderUserId = department.getLeaderUserId();
        String leaderName = trim(department.getLeader());
        if (leaderUserId == null)
        {
            return blank(leaderName) ? "MISSING"
                    : "历史负责人“" + leaderName + "”待人工确认员工身份";
        }
        if (leaderUserId <= 0)
        {
            return "负责人用户ID无效，请重新选择有效在职员工";
        }
        SysUser leader = validLeaderById.get(leaderUserId);
        if (leader == null)
        {
            return "负责人账号不存在、已离职、已停用或当前数据范围不可见";
        }
        if (blank(leaderName))
        {
            return "负责人姓名快照缺失，请重新选择负责人";
        }
        if (!leaderName.equals(trim(leader.getNickName())))
        {
            return "负责人姓名快照与员工主数据不一致，请重新确认负责人";
        }
        return null;
    }

    private void evaluatePostsAndConfiguration(Snapshot snapshot,
            Map<Long, SysDept> departments, HrMasterDataScanResult result)
    {
        List<SysUser> employees = safe(snapshot.employees());
        List<SysPost> posts = safe(snapshot.posts());
        Map<Long, SysPost> postById = posts.stream()
                .filter(value -> value != null && value.getPostId() != null)
                .collect(Collectors.toMap(SysPost::getPostId,
                        Function.identity(), (left, right) -> left,
                        LinkedHashMap::new));
        Map<Long, List<SysUserPost>> byUser = safe(snapshot.userPosts())
                .stream().filter(Objects::nonNull)
                .collect(Collectors.groupingBy(SysUserPost::getUserId,
                        LinkedHashMap::new, Collectors.toList()));
        Map<Long, Set<Long>> invalidPostUsers = new LinkedHashMap<>();
        Map<PositionCategoryKey, Set<Long>> usage = new LinkedHashMap<>();

        for (SysUser employee : employees)
        {
            if (employee == null || employee.getUserId() == null
                    || isDeparted(employee))
            {
                continue;
            }
            List<SysUserPost> assigned = byUser.getOrDefault(
                    employee.getUserId(), Collections.emptyList());
            if (assigned.isEmpty())
            {
                SysDept dept = departments.get(employee.getDeptId());
                result.add(issue(POST_MISSING_OR_DISABLED, "EMPLOYEE",
                        employee.getUserId(), employee.getNickName(),
                        employee.getDeptId(),
                        dept == null ? null : dept.getDeptName(),
                        category(employee), 1, "员工未关联任何岗位",
                        "/hr/employee?userId=" + employee.getUserId()),
                        Set.of(employee.getUserId()));
                continue;
            }
            for (SysUserPost relation : assigned)
            {
                SysPost post = postById.get(relation.getPostId());
                if (post == null || !"0".equals(post.getStatus()))
                {
                    invalidPostUsers.computeIfAbsent(relation.getPostId(),
                            ignored -> new LinkedHashSet<>())
                            .add(employee.getUserId());
                    continue;
                }
                String category = category(employee);
                if (!blank(category))
                {
                    usage.computeIfAbsent(new PositionCategoryKey(
                            post.getPostId(), category),
                            ignored -> new LinkedHashSet<>())
                            .add(employee.getUserId());
                }
            }
        }
        invalidPostUsers.forEach((postId, affectedUsers) -> {
            SysPost post = postById.get(postId);
            result.add(issue(POST_MISSING_OR_DISABLED, "POST", postId,
                    post == null ? "不存在的岗位 " + postId
                            : post.getPostName(),
                    null, null, null, affectedUsers.size(),
                    post == null ? "员工岗位关系引用了不存在的岗位"
                            : "员工岗位关系引用了已停用岗位",
                    "/system/post?postId=" + postId), affectedUsers);
        });

        List<HrOnboardingPositionConfig> configs = safe(
                snapshot.positionConfigs());
        Map<PositionCategoryKey, HrOnboardingPositionConfig> configByPair = configs
                .stream()
                .filter(value -> value != null && value.getPostId() != null
                        && !blank(value.getEmployeeCategory()))
                .collect(Collectors.toMap(
                        value -> new PositionCategoryKey(value.getPostId(),
                                value.getEmployeeCategory().trim()),
                        Function.identity(), (left, right) -> left,
                        LinkedHashMap::new));

        for (HrOnboardingPositionConfig config : configs)
        {
            if (config == null || config.getPostId() == null)
            {
                continue;
            }
            SysPost post = postById.get(config.getPostId());
            if (post == null || !"0".equals(post.getStatus()))
            {
                PositionCategoryKey pair = new PositionCategoryKey(
                        config.getPostId(), trim(config.getEmployeeCategory()));
                Set<Long> affectedUsers = usage.getOrDefault(pair,
                        Collections.emptySet());
                result.add(issue(POST_MISSING_OR_DISABLED,
                        "POSITION_CONFIG", config.getConfigId(),
                        "配置 #" + config.getConfigId(), null, null,
                        config.getEmployeeCategory(), affectedUsers.size(),
                        "岗位入职配置引用了不存在或已停用的岗位",
                        "/hr/position-config?configId=" + config.getConfigId()),
                        affectedUsers);
            }
        }

        List<String> categories = activeEmployeeCategories(
                snapshot.dictionaryRoutes());
        Set<Long> activeRoleIds = snapshot.activeRoleIds() == null
                ? Collections.emptySet() : snapshot.activeRoleIds();
        Map<Long, List<Long>> roleIdsByConfig = snapshot.roleIdsByConfig() == null
                ? Collections.emptyMap() : snapshot.roleIdsByConfig();
        for (SysPost post : posts)
        {
            if (post == null || post.getPostId() == null
                    || !"0".equals(post.getStatus()))
            {
                continue;
            }
            for (String employeeCategory : categories)
            {
                PositionCategoryKey pair = new PositionCategoryKey(
                        post.getPostId(), employeeCategory);
                HrOnboardingPositionConfig config = configByPair.get(pair);
                Set<Long> affectedUsers = usage.getOrDefault(pair,
                        Collections.emptySet());
                long affected = affectedUsers.size();
                String action = "/hr/position-config?postId="
                        + post.getPostId() + "&employeeCategory="
                        + URLEncoder.encode(employeeCategory,
                                StandardCharsets.UTF_8);
                if (config == null || !"0".equals(config.getStatus()))
                {
                    result.add(issue(POSITION_CONFIG_MISSING,
                            "POSITION_CONFIG_PAIR",
                            post.getPostId() + ":" + employeeCategory,
                            post.getPostName(), null, null, employeeCategory,
                            affected,
                            config == null ? "岗位与人员类别尚未建立入职配置"
                                    : "岗位入职配置已停用",
                            action), affectedUsers);
                    continue;
                }
                if (Boolean.TRUE.equals(config.getAccountEnabled()))
                {
                    List<Long> roleIds = safe(
                            roleIdsByConfig.get(config.getConfigId()));
                    boolean hasActiveRole = roleIds.stream()
                            .anyMatch(activeRoleIds::contains);
                    if (!hasActiveRole)
                    {
                        result.add(issue(POSITION_CONFIG_ROLE_MISSING,
                                "POSITION_CONFIG", config.getConfigId(),
                                post.getPostName(), null, null,
                                employeeCategory, affected,
                                "该配置会启用账号，但没有任何有效默认角色",
                                action), affectedUsers);
                    }
                }
            }
        }
    }

    private void evaluateDictionaryRoutes(
            Map<String, DictionaryRouteSnapshot> dictionaryRoutes,
            HrMasterDataScanResult result)
    {
        if (dictionaryRoutes == null)
        {
            return;
        }
        for (DictionaryRouteSnapshot route : dictionaryRoutes.values())
        {
            String detail = null;
            if (blank(route.dictType()))
            {
                detail = "未配置字典路由参数 " + route.configKey();
            }
            else if (!route.typeActive())
            {
                detail = "路由指向的字典类型不存在或已停用: "
                        + route.dictType();
            }
            else if (safe(route.activeValues()).isEmpty())
            {
                detail = "字典类型没有启用的选项: " + route.dictType();
            }
            if (detail != null)
            {
                result.add(issue(DICTIONARY_ROUTE_MISSING, "SYSTEM_CONFIG",
                        route.configKey(), route.fieldKey(), null, null, null, 0,
                        detail, "/system/config?configKey="
                                + URLEncoder.encode(route.configKey(),
                                        StandardCharsets.UTF_8)),
                        Collections.emptySet());
            }
        }
    }

    private List<String> activeEmployeeCategories(
            Map<String, DictionaryRouteSnapshot> dictionaryRoutes)
    {
        if (dictionaryRoutes == null)
        {
            return Collections.emptyList();
        }
        DictionaryRouteSnapshot route = dictionaryRoutes
                .get("employeeCategory");
        if (route == null || blank(route.dictType()) || !route.typeActive())
        {
            return Collections.emptyList();
        }
        return safe(route.activeValues()).stream().map(this::trim)
                .filter(value -> !blank(value)).distinct().toList();
    }

    private HrMasterDataIssueVo issue(String code, String resourceType,
            Object resourceId, String resourceName, Long deptId,
            String deptName, String employeeCategory, long affected,
            String detail, String actionUrl)
    {
        HrMasterDataIssueCodes.Definition definition = HrMasterDataIssueCodes
                .definition(code);
        HrMasterDataIssueVo value = new HrMasterDataIssueVo();
        value.setIssueCode(code);
        value.setIssueName(definition.label());
        value.setSeverity(definition.severity());
        value.setRiskLevel(definition.riskLevel());
        value.setResourceType(resourceType);
        value.setResourceId(resourceId == null ? null
                : String.valueOf(resourceId));
        value.setResourceName(resourceName);
        value.setDeptId(deptId);
        value.setDeptName(deptName);
        value.setEmployeeCategory(employeeCategory);
        value.setAffectedEmployeeCount(affected);
        value.setDetail(detail);
        value.setSuggestion(definition.defaultSuggestion());
        value.setActionUrl(actionUrl);
        return value;
    }

    private OrgPath resolvePath(Long deptId, Map<Long, SysDept> all)
    {
        SysDept selected = all.get(deptId);
        if (selected == null)
        {
            return new OrgPath(false, "组织节点不存在",
                    Collections.emptyList());
        }
        if (!"0".equals(selected.getStatus()))
        {
            return new OrgPath(false, "组织节点已停用",
                    Collections.emptyList());
        }
        List<SysDept> path = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        String ancestors = selected.getAncestors();
        if (!blank(ancestors))
        {
            for (String token : ancestors.split(","))
            {
                String value = trim(token);
                if (blank(value) || "0".equals(value))
                {
                    continue;
                }
                Long id;
                try
                {
                    id = Long.valueOf(value);
                }
                catch (NumberFormatException invalid)
                {
                    return new OrgPath(false,
                            "祖级路径包含非法ID: " + value,
                            Collections.emptyList());
                }
                if (!seen.add(id))
                {
                    return new OrgPath(false, "祖级路径存在重复或循环节点",
                            Collections.emptyList());
                }
                SysDept ancestor = all.get(id);
                if (ancestor == null)
                {
                    return new OrgPath(false, "祖级路径引用了不存在的组织: " + id,
                            Collections.emptyList());
                }
                if (!"0".equals(ancestor.getStatus()))
                {
                    return new OrgPath(false,
                            "祖级路径包含已停用组织: " + ancestor.getDeptName(),
                            Collections.emptyList());
                }
                path.add(ancestor);
            }
        }
        if (!seen.add(selected.getDeptId()))
        {
            return new OrgPath(false, "组织路径形成循环",
                    Collections.emptyList());
        }
        path.add(selected);
        for (int index = 0; index < path.size(); index++)
        {
            Long expected = index == 0 ? 0L
                    : path.get(index - 1).getDeptId();
            Long actual = path.get(index).getParentId() == null ? 0L
                    : path.get(index).getParentId();
            if (!expected.equals(actual))
            {
                return new OrgPath(false, "祖级路径与父组织关系不一致",
                        Collections.emptyList());
            }
        }
        return new OrgPath(true, null, path);
    }

    private int nearestTypeIndex(List<SysDept> path, String expectedType)
    {
        for (int index = path.size() - 1; index >= 0; index--)
        {
            if (expectedType.equals(type(path.get(index))))
            {
                return index;
            }
        }
        return -1;
    }

    private List<SysDept> departmentLayers(List<SysDept> path, int start,
            int end)
    {
        return path.subList(Math.max(0, start), Math.min(path.size(), end))
                .stream().filter(this::isDepartmentLayer).toList();
    }

    private boolean isDepartmentLayer(SysDept dept)
    {
        return dept != null && !NON_DEPARTMENT_TYPES.contains(type(dept));
    }

    private String type(SysDept dept)
    {
        return dept == null || dept.getDeptType() == null ? ""
                : dept.getDeptType().trim().toUpperCase(Locale.ROOT);
    }

    private boolean isDeparted(SysUser user)
    {
        return user.getProfile() != null
                && "离职".equals(user.getProfile().getEmployeeStatus());
    }

    private String category(SysUser user)
    {
        SysUserProfile profile = user.getProfile();
        return profile == null ? null
                : trim(profile.getEmployeeCategory());
    }

    private String trim(String value)
    {
        return value == null ? null : value.trim();
    }

    private boolean blank(String value)
    {
        return value == null || value.isBlank();
    }

    private <T> List<T> safe(List<T> values)
    {
        return values == null ? Collections.emptyList() : values;
    }

    public record Snapshot(List<SysDept> scopedDepartments,
            List<SysDept> allDepartments, List<SysUser> employees,
            List<SysPost> posts, List<SysUserPost> userPosts,
            List<HrOnboardingPositionConfig> positionConfigs,
            Map<Long, List<Long>> roleIdsByConfig, Set<Long> activeRoleIds,
            Map<String, DictionaryRouteSnapshot> dictionaryRoutes)
    {
    }

    public record DictionaryRouteSnapshot(String fieldKey, String configKey,
            String dictType, boolean typeActive, List<String> activeValues)
    {
    }

    private record OrgPath(boolean valid, String reason, List<SysDept> nodes)
    {
    }

    private record PositionCategoryKey(Long postId, String employeeCategory)
    {
        private PositionCategoryKey
        {
            employeeCategory = employeeCategory == null ? null
                    : employeeCategory.trim();
        }
    }
}

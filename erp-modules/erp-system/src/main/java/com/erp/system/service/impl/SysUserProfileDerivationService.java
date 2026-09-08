package com.erp.system.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.erp.system.api.domain.SysDept;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.domain.SysUserProfile;
import com.erp.system.domain.SysPost;
import com.erp.system.mapper.SysDeptMapper;
import com.erp.system.mapper.SysPostMapper;

/**
 * 根据组织、岗位和日期实时派生用户档案展示字段。
 */
@Service
public class SysUserProfileDerivationService
{
    static final String ORGANIZATION_WARNING = "组织结构待修复";
    static final String COMPANY_WARNING = "无法识别所属公司";
    static final String HIERARCHY_WARNING = "组织层级超过系统定义";
    static final String SUPERVISOR_WARNING = "未配置部门负责人";
    static final String DATE_WARNING = "档案日期异常";

    private static final Set<String> NON_DEPARTMENT_TYPES = Set.of("GROUP", "STORE", "WAREHOUSE");

    private final SysDeptMapper deptMapper;
    private final SysPostMapper postMapper;
    private final Clock clock;

    @Autowired
    public SysUserProfileDerivationService(SysDeptMapper deptMapper, SysPostMapper postMapper)
    {
        this(deptMapper, postMapper, Clock.systemDefaultZone());
    }

    SysUserProfileDerivationService(SysDeptMapper deptMapper, SysPostMapper postMapper, Clock clock)
    {
        this.deptMapper = deptMapper;
        this.postMapper = postMapper;
        this.clock = clock;
    }

    public void applyToUsers(List<SysUser> users)
    {
        if (users == null || users.isEmpty())
        {
            return;
        }
        List<SysDept> departments = deptMapper.selectDeptList(new SysDept());
        LocalDate currentDate = LocalDate.now(clock);
        for (SysUser user : users)
        {
            derive(user, departments, Collections.emptyList(), currentDate);
        }
    }

    public SysUser applyToUser(SysUser user)
    {
        if (user == null)
        {
            return null;
        }
        List<SysDept> departments = deptMapper.selectDeptList(new SysDept());
        List<SysPost> posts = user.getUserName() == null || user.getUserName().isBlank()
                ? Collections.emptyList()
                : postMapper.selectPostsByUserName(user.getUserName());
        derive(user, departments, posts, LocalDate.now(clock));
        return user;
    }

    public SysUserProfile preview(SysUser user)
    {
        List<SysDept> departments = deptMapper.selectDeptList(new SysDept());
        List<SysPost> posts = postMapper.selectPostAll();
        return derive(user, departments, posts, LocalDate.now(clock));
    }

    SysUserProfile derive(SysUser user, List<SysDept> departments, List<SysPost> posts, LocalDate currentDate)
    {
        SysUserProfile profile = user == null ? new SysUserProfile() : user.getProfile();
        profile.setDerivedWarnings(new ArrayList<>());
        clearOrganizationFields(profile);
        profile.setPositionNames(null);
        profile.setWorkYears(null);
        profile.setCompanyYears(null);
        profile.setContractTerm(null);

        derivePositions(user, posts, profile);
        deriveDurations(profile, currentDate == null ? LocalDate.now(clock) : currentDate);

        if (user == null || user.getDeptId() == null)
        {
            return profile;
        }

        Map<Long, SysDept> departmentById = new HashMap<>();
        for (SysDept department : departments == null ? Collections.<SysDept>emptyList() : departments)
        {
            if (department != null && department.getDeptId() != null)
            {
                departmentById.put(department.getDeptId(), department);
            }
        }

        List<SysDept> path = resolvePath(user.getDeptId(), departmentById);
        if (!isValidPath(path))
        {
            addWarning(profile, ORGANIZATION_WARNING);
            return profile;
        }

        int groupIndex = nearestTypeIndex(path, "GROUP");
        if (groupIndex < 0)
        {
            addWarning(profile, COMPANY_WARNING);
            return profile;
        }

        int companyIndex = groupIndex + 1;
        if (companyIndex >= path.size() || !isDepartmentLayer(path.get(companyIndex)))
        {
            addWarning(profile, COMPANY_WARNING);
            return profile;
        }

        int storeIndex = nearestTypeIndex(path, "STORE");
        if (storeIndex >= 0 && storeIndex <= companyIndex)
        {
            addWarning(profile, ORGANIZATION_WARNING);
            return profile;
        }

        int levelEnd = storeIndex > companyIndex ? storeIndex : path.size();
        List<SysDept> departmentLayers = departmentLayers(path, companyIndex + 1, levelEnd);

        profile.setCompanyName(path.get(companyIndex).getDeptName());
        if (storeIndex > companyIndex)
        {
            applyStoreAlignedLevels(profile, departmentLayers);
            profile.setStoreName(path.get(storeIndex).getDeptName());
        }
        else
        {
            applyNonStoreLevels(profile, departmentLayers);
        }
        SysDept leaderOwner = departmentLayers.isEmpty() ? path.get(companyIndex) : departmentLayers.get(0);
        applyDepartmentSupervisor(profile, leaderOwner);
        return profile;
    }

    private void derivePositions(SysUser user, List<SysPost> posts, SysUserProfile profile)
    {
        if (user == null)
        {
            return;
        }
        if (user.getPostNames() != null && !user.getPostNames().isBlank()
                && (posts == null || posts.isEmpty()))
        {
            profile.setPositionNames(user.getPostNames());
            return;
        }

        Long[] postIds = user.getPostIds();
        Set<Long> selectedIds = postIds == null ? Collections.emptySet() : new HashSet<>(Arrays.asList(postIds));
        boolean useAllProvidedPosts = postIds == null;
        String positionNames = (posts == null ? Collections.<SysPost>emptyList() : posts).stream()
                .filter(post -> post != null && "0".equals(post.getStatus()))
                .filter(post -> useAllProvidedPosts || selectedIds.contains(post.getPostId()))
                .sorted(Comparator.comparing(SysPost::getPostSort,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(SysPost::getPostName, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(SysPost::getPostName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .collect(java.util.stream.Collectors.joining("、"));
        profile.setPositionNames(positionNames.isEmpty() ? null : positionNames);
    }

    private void deriveDurations(SysUserProfile profile, LocalDate currentDate)
    {
        profile.setWorkYears(formatDuration(toLocalDate(profile.getWorkStartDate()), currentDate, profile));

        LocalDate companyEnd = currentDate;
        if ("离职".equals(profile.getEmployeeStatus()) && profile.getLeaveDate() != null)
        {
            companyEnd = toLocalDate(profile.getLeaveDate());
        }
        profile.setCompanyYears(formatDuration(toLocalDate(profile.getEntryDate()), companyEnd, profile));
        profile.setContractTerm(formatDuration(toLocalDate(profile.getContractStartDate()),
                toLocalDate(profile.getContractEndDate()), profile));
    }

    private String formatDuration(LocalDate start, LocalDate end, SysUserProfile profile)
    {
        if (start == null)
        {
            return null;
        }
        if (end == null || start.isAfter(end))
        {
            addWarning(profile, DATE_WARNING);
            return null;
        }
        Period period = Period.between(start, end);
        return period.getYears() + "年" + period.getMonths() + "个月";
    }

    private LocalDate toLocalDate(Date date)
    {
        if (date == null)
        {
            return null;
        }
        if (date instanceof java.sql.Date sqlDate)
        {
            return sqlDate.toLocalDate();
        }
        Instant instant = date.toInstant();
        return instant.atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private List<SysDept> resolvePath(Long deptId, Map<Long, SysDept> departmentById)
    {
        SysDept selected = departmentById.get(deptId);
        if (selected == null || !"0".equals(selected.getStatus()))
        {
            return Collections.emptyList();
        }

        List<SysDept> path = new ArrayList<>();
        String ancestors = selected.getAncestors();
        if (ancestors != null && !ancestors.isBlank())
        {
            for (String token : ancestors.split(","))
            {
                String value = token.trim();
                if (value.isEmpty() || "0".equals(value))
                {
                    continue;
                }
                try
                {
                    SysDept ancestor = departmentById.get(Long.valueOf(value));
                    if (ancestor == null || !"0".equals(ancestor.getStatus()))
                    {
                        return Collections.emptyList();
                    }
                    path.add(ancestor);
                }
                catch (NumberFormatException ex)
                {
                    return Collections.emptyList();
                }
            }
        }
        path.add(selected);
        return path;
    }

    private boolean isValidPath(List<SysDept> path)
    {
        if (path.isEmpty())
        {
            return false;
        }
        for (int index = 0; index < path.size(); index++)
        {
            SysDept current = path.get(index);
            Long expectedParentId = index == 0 ? 0L : path.get(index - 1).getDeptId();
            Long actualParentId = current.getParentId() == null ? 0L : current.getParentId();
            if (!expectedParentId.equals(actualParentId))
            {
                return false;
            }
        }
        return true;
    }

    private int nearestTypeIndex(List<SysDept> path, String type)
    {
        for (int index = path.size() - 1; index >= 0; index--)
        {
            if (type.equals(normalizeType(path.get(index).getDeptType())))
            {
                return index;
            }
        }
        return -1;
    }

    private List<SysDept> departmentLayers(List<SysDept> path, int startInclusive, int endExclusive)
    {
        List<SysDept> layers = new ArrayList<>();
        for (int index = Math.max(0, startInclusive); index < Math.min(path.size(), endExclusive); index++)
        {
            SysDept department = path.get(index);
            if (isDepartmentLayer(department))
            {
                layers.add(department);
            }
        }
        return layers;
    }

    private boolean isDepartmentLayer(SysDept department)
    {
        return department != null && !NON_DEPARTMENT_TYPES.contains(normalizeType(department.getDeptType()));
    }

    private String normalizeType(String type)
    {
        return type == null ? "" : type.trim().toUpperCase();
    }

    private String nameAt(List<SysDept> departments, int index)
    {
        return departments.size() > index ? departments.get(index).getDeptName() : null;
    }

    private void applyStoreAlignedLevels(SysUserProfile profile, List<SysDept> layers)
    {
        if (layers.size() > 3)
        {
            addWarning(profile, HIERARCHY_WARNING);
        }
        int visibleStart = Math.max(0, layers.size() - 3);
        List<SysDept> visible = layers.subList(visibleStart, layers.size());
        int targetStart = 3 - visible.size();
        profile.setDeptLevel1Name(targetStart == 0 ? nameAt(visible, 0) : null);
        profile.setDeptLevel2Name(targetStart <= 1 ? nameAt(visible, 1 - targetStart) : null);
        profile.setDeptLevel3Name(targetStart <= 2 ? nameAt(visible, 2 - targetStart) : null);
    }

    private void applyNonStoreLevels(SysUserProfile profile, List<SysDept> layers)
    {
        if (layers.size() > 3)
        {
            addWarning(profile, HIERARCHY_WARNING);
        }
        profile.setDeptLevel1Name(nameAt(layers, 0));
        profile.setDeptLevel2Name(nameAt(layers, 1));
        profile.setDeptLevel3Name(nameAt(layers, 2));
    }

    private void applyDepartmentSupervisor(SysUserProfile profile, SysDept leaderOwner)
    {
        if (leaderOwner == null || leaderOwner.getLeaderUserId() == null)
        {
            addWarning(profile, SUPERVISOR_WARNING);
            return;
        }
        String leader = leaderOwner.getLeader();
        if (leader == null || leader.isBlank())
        {
            addWarning(profile, SUPERVISOR_WARNING);
            return;
        }
        profile.setDepartmentSupervisor(leader);
    }

    private void clearOrganizationFields(SysUserProfile profile)
    {
        profile.setCompanyName(null);
        profile.setDeptLevel1Name(null);
        profile.setDeptLevel2Name(null);
        profile.setDeptLevel3Name(null);
        profile.setStoreName(null);
        profile.setDepartmentSupervisor(null);
    }

    private void addWarning(SysUserProfile profile, String warning)
    {
        if (!profile.getDerivedWarnings().contains(warning))
        {
            profile.getDerivedWarnings().add(warning);
        }
    }
}

package com.erp.file.drive.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.domain.DriveActor;
import com.erp.file.drive.domain.DriveOrganization;
import com.erp.file.drive.domain.DriveRoleScope;
import com.erp.file.drive.mapper.DriveOrganizationMapper;
import org.springframework.stereotype.Service;

/**
 * 只根据当前数据库身份、角色数据范围和组织树解析组织盘访问范围。
 */
@Service
public class DriveOrganizationScopeService
{
    private static final String SCOPE_ALL = "1";
    private static final String SCOPE_CUSTOM = "2";
    private static final String SCOPE_DEPT = "3";
    private static final String SCOPE_DEPT_AND_CHILD = "4";
    private static final String SCOPE_SELF = "5";

    private final DriveOrganizationMapper mapper;

    public DriveOrganizationScopeService(DriveOrganizationMapper mapper)
    {
        this.mapper = mapper;
    }

    public Set<Long> readableDeptIds(DriveActor actor)
    {
        Set<Long> result = new LinkedHashSet<>();
        if (!hasAccess(actor)) return result;
        List<DriveOrganization> organizations = safeList(mapper.selectAllOrganizations());
        if (actor.deptId() != null && isActive(find(organizations, actor.deptId())))
        {
            result.add(actor.deptId());
        }
        if (actor.admin())
        {
            addAllActive(result, organizations);
            return result;
        }
        if (!actor.hasPermission(DriveConstants.PERMISSION_DEPARTMENT_MANAGE)) return result;
        applyRoleScopes(result, actor, organizations, safeList(mapper.selectRoleScopes(actor.userId())));
        return result;
    }

    public Set<Long> manageableDeptIds(DriveActor actor)
    {
        Set<Long> result = new LinkedHashSet<>();
        if (!hasAccess(actor)) return result;
        List<DriveOrganization> organizations = safeList(mapper.selectAllOrganizations());
        if (actor.admin())
        {
            addAllActive(result, organizations);
            return result;
        }
        if (!actor.hasPermission(DriveConstants.PERMISSION_DEPARTMENT_MANAGE)) return result;
        applyRoleScopes(result, actor, organizations, safeList(mapper.selectRoleScopes(actor.userId())));
        return result;
    }

    public boolean canRead(DriveActor actor, Long deptId)
    {
        return deptId != null && readableDeptIds(actor).contains(deptId);
    }

    public boolean canManage(DriveActor actor, Long deptId)
    {
        return deptId != null && manageableDeptIds(actor).contains(deptId);
    }

    private static void applyRoleScopes(Set<Long> result, DriveActor actor,
            List<DriveOrganization> organizations, List<DriveRoleScope> scopes)
    {
        for (DriveRoleScope scope : scopes)
        {
            String dataScope = scope.getDataScope();
            if (SCOPE_ALL.equals(dataScope))
            {
                addAllActive(result, organizations);
            }
            else if (SCOPE_CUSTOM.equals(dataScope))
            {
                DriveOrganization custom = find(organizations, scope.getDeptId());
                if (isActive(custom)) result.add(custom.getDeptId());
            }
            else if (SCOPE_DEPT.equals(dataScope) || SCOPE_SELF.equals(dataScope))
            {
                DriveOrganization own = find(organizations, actor.deptId());
                if (isActive(own)) result.add(own.getDeptId());
            }
            else if (SCOPE_DEPT_AND_CHILD.equals(dataScope) && actor.deptId() != null)
            {
                for (DriveOrganization organization : organizations)
                {
                    if (isActive(organization) && (Objects.equals(actor.deptId(), organization.getDeptId())
                            || containsAncestor(organization.getAncestors(), actor.deptId())))
                    {
                        result.add(organization.getDeptId());
                    }
                }
            }
        }
    }

    static boolean containsAncestor(String ancestors, Long deptId)
    {
        if (ancestors == null || deptId == null) return false;
        String expected = String.valueOf(deptId);
        for (String value : ancestors.split(","))
        {
            if (expected.equals(value.trim())) return true;
        }
        return false;
    }

    static boolean isActive(DriveOrganization organization)
    {
        return organization != null && "0".equals(organization.getStatus())
                && "0".equals(organization.getDelFlag());
    }

    private static DriveOrganization find(List<DriveOrganization> values, Long deptId)
    {
        if (deptId == null) return null;
        for (DriveOrganization value : values)
        {
            if (Objects.equals(deptId, value.getDeptId())) return value;
        }
        return null;
    }

    private static void addAllActive(Set<Long> result, List<DriveOrganization> organizations)
    {
        for (DriveOrganization organization : organizations)
        {
            if (isActive(organization) && organization.getDeptId() != null)
            {
                result.add(organization.getDeptId());
            }
        }
    }

    private static boolean hasAccess(DriveActor actor)
    {
        return actor != null && actor.userId() != null
                && (actor.admin() || actor.hasPermission(DriveConstants.PERMISSION_ACCESS));
    }

    private static <T> List<T> safeList(List<T> values)
    {
        return values == null ? List.of() : values;
    }
}

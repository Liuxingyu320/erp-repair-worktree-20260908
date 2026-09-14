package com.erp.system.service.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.api.domain.SysRole;
import com.erp.system.domain.SysUserRole;
import com.erp.system.mapper.SysRoleMapper;
import com.erp.system.mapper.SysUserRoleMapper;

/** Role association writes used only inside the callers' existing transactions. */
public final class RoleAssignmentGuard
{
    private RoleAssignmentGuard() { }

    /** Lock roles before users/relations, matching onboarding confirmation's lock order. */
    public static List<SysRole> lockRoles(SysRoleMapper mapper, Long[] roleIds)
    {
        List<SysRole> roles = new ArrayList<>();
        for (Long roleId : sortedIds(roleIds, "角色ID"))
        {
            SysRole role = mapper.selectRoleByIdForUpdate(roleId);
            if (role == null || !"0".equals(role.getDelFlag()))
                throw new ServiceException("角色不存在或已删除，不能分配：" + roleId);
            roles.add(role);
        }
        return roles;
    }

    public static List<SysRole> lockNewUserRoles(SysRoleMapper mapper, Long[] roleIds)
    {
        List<SysRole> roles = lockRoles(mapper, roleIds);
        for (SysRole role : roles) requireEnabled(role);
        return roles;
    }

    /** A freshly inserted user has no previous associations; roles were locked before insert. */
    public static void insertNewUserRoles(SysUserRoleMapper mapper, Long userId, List<SysRole> roles)
    {
        List<SysUserRole> added = new ArrayList<>();
        for (SysRole role : roles) added.add(relation(userId, role.getRoleId()));
        if (!added.isEmpty()) mapper.batchUserRole(added);
    }

    public static void replaceRoles(SysRoleMapper roles, SysUserRoleMapper relations,
            Long userId, Long[] roleIds)
    {
        List<SysRole> requested = lockRoles(roles, roleIds);
        lockUser(relations, userId);
        Set<Long> previous = new LinkedHashSet<>(relations.selectRoleIdsByUserId(userId));
        Set<Long> desired = new LinkedHashSet<>();
        List<SysUserRole> added = new ArrayList<>();
        for (SysRole role : requested)
        {
            desired.add(role.getRoleId());
            if (!previous.contains(role.getRoleId()))
            {
                requireEnabled(role);
                added.add(relation(userId, role.getRoleId()));
            }
        }
        // Validate the complete selection before deleting anything. Existing disabled bindings stay put.
        for (Long roleId : previous)
            if (!desired.contains(roleId)) relations.deleteUserRoleInfo(relation(userId, roleId));
        if (!added.isEmpty()) relations.batchUserRole(added);
    }

    public static int addUsers(SysRoleMapper roles, SysUserRoleMapper relations,
            Long roleId, Long[] userIds)
    {
        SysRole role = lockRoles(roles, new Long[] { roleId }).get(0);
        Collection<Long> users = sortedIds(userIds, "用户ID");
        for (Long userId : users) lockUser(relations, userId);
        List<SysUserRole> added = new ArrayList<>();
        for (Long userId : users)
        {
            if (!relations.selectRoleIdsByUserId(userId).contains(roleId))
            {
                requireEnabled(role);
                added.add(relation(userId, roleId));
            }
        }
        return added.isEmpty() ? 0 : relations.batchUserRole(added);
    }

    private static Set<Long> sortedIds(Long[] values, String label)
    {
        Set<Long> ids = new TreeSet<>();
        if (values != null)
            for (Long value : values)
            {
                if (value == null || value <= 0) throw new ServiceException(label + "无效");
                ids.add(value);
            }
        return ids;
    }

    private static void lockUser(SysUserRoleMapper mapper, Long userId)
    {
        if (userId == null || mapper.lockUserForRoleAssignment(userId) == null)
            throw new ServiceException("用户不存在或已删除，不能分配角色");
    }

    private static void requireEnabled(SysRole role)
    {
        if (!"0".equals(role.getStatus()))
            throw new ServiceException("停用角色不能新增成员，请保留已有绑定或取消绑定：" + role.getRoleId());
    }

    private static SysUserRole relation(Long userId, Long roleId)
    {
        SysUserRole relation = new SysUserRole();
        relation.setUserId(userId);
        relation.setRoleId(roleId);
        return relation;
    }
}

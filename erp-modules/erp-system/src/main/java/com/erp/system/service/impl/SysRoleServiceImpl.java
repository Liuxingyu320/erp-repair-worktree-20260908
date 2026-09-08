package com.erp.system.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.constant.Constants;
import com.erp.common.core.constant.HttpStatus;
import com.erp.common.core.constant.UserConstants;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.SpringUtils;
import com.erp.common.core.utils.StringUtils;
import com.erp.common.datascope.annotation.DataScope;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.system.api.domain.SysRole;
import com.erp.system.domain.SysRoleDept;
import com.erp.system.domain.SysRoleMenu;
import com.erp.system.domain.SysUserRole;
import com.erp.system.mapper.SysRoleDeptMapper;
import com.erp.system.mapper.SysConfigMapper;
import com.erp.system.mapper.SysRoleMapper;
import com.erp.system.mapper.SysRoleMenuMapper;
import com.erp.system.mapper.SysUserRoleMapper;
import com.erp.system.service.ISysDeptService;
import com.erp.system.service.ISysRoleService;
import com.erp.system.service.ISysUserService;
import com.erp.system.service.support.UserSessionInvalidationService;

/**
 * 角色 业务层处理
 * 
 * @author erp
 */
@Service
public class SysRoleServiceImpl implements ISysRoleService
{
    private static final String DEFAULT_DATA_SCOPE = "4";
    private static final Set<String> ALLOWED_DATA_SCOPES = Set.of("1", "2", "3", "4", "5");

    @Autowired
    private SysRoleMapper roleMapper;

    @Autowired
    private SysRoleMenuMapper roleMenuMapper;

    @Autowired
    private SysUserRoleMapper userRoleMapper;

    @Autowired
    private SysRoleDeptMapper roleDeptMapper;

    @Autowired
    private ISysUserService userService;

    @Autowired
    private ISysDeptService deptService;

    @Autowired
    private SysConfigMapper configMapper;

    @Autowired
    private UserSessionInvalidationService userSessionInvalidationService;

    /**
     * 根据条件分页查询角色数据
     * 
     * @param role 角色信息
     * @return 角色数据集合信息
     */
    @Override
    @DataScope(deptAlias = "d")
    public List<SysRole> selectRoleList(SysRole role)
    {
        return roleMapper.selectRoleList(role);
    }

    /**
     * 根据用户ID查询角色
     * 
     * @param userId 用户ID
     * @return 角色列表
     */
    @Override
    public List<SysRole> selectRolesByUserId(Long userId)
    {
        List<SysRole> userRoles = roleMapper.selectRolePermissionByUserId(userId);
        List<SysRole> roles = selectRoleAll();
        for (SysRole role : roles)
        {
            for (SysRole userRole : userRoles)
            {
                if (role.getRoleId().longValue() == userRole.getRoleId().longValue())
                {
                    role.setFlag(true);
                    break;
                }
            }
        }
        return roles;
    }

    /**
     * 根据用户ID查询权限
     * 
     * @param userId 用户ID
     * @return 权限列表
     */
    @Override
    public Set<String> selectRolePermissionByUserId(Long userId)
    {
        List<SysRole> perms = roleMapper.selectRolePermissionByUserId(userId);
        Set<String> permsSet = new HashSet<>();
        for (SysRole perm : perms)
        {
            if (StringUtils.isNotNull(perm))
            {
                permsSet.addAll(Arrays.asList(perm.getRoleKey().trim().split(",")));
            }
        }
        return permsSet;
    }

    /**
     * 查询所有角色
     * 
     * @return 角色列表
     */
    @Override
    public List<SysRole> selectRoleAll()
    {
        return SpringUtils.getAopProxy(this).selectRoleList(new SysRole());
    }

    /**
     * 根据用户ID获取角色选择框列表
     * 
     * @param userId 用户ID
     * @return 选中角色ID列表
     */
    @Override
    public List<Long> selectRoleListByUserId(Long userId)
    {
        return roleMapper.selectRoleListByUserId(userId);
    }

    /**
     * 通过角色ID查询角色
     * 
     * @param roleId 角色ID
     * @return 角色对象信息
     */
    @Override
    public SysRole selectRoleById(Long roleId)
    {
        return roleMapper.selectRoleById(roleId);
    }

    /**
     * 校验角色名称是否唯一
     * 
     * @param role 角色信息
     * @return 结果
     */
    @Override
    public boolean checkRoleNameUnique(SysRole role)
    {
        Long roleId = StringUtils.isNull(role.getRoleId()) ? -1L : role.getRoleId();
        SysRole info = roleMapper.checkRoleNameUnique(role.getRoleName());
        if (StringUtils.isNotNull(info) && info.getRoleId().longValue() != roleId.longValue())
        {
            return UserConstants.NOT_UNIQUE;
        }
        return UserConstants.UNIQUE;
    }

    /**
     * 校验角色权限是否唯一
     * 
     * @param role 角色信息
     * @return 结果
     */
    @Override
    public boolean checkRoleKeyUnique(SysRole role)
    {
        Long roleId = StringUtils.isNull(role.getRoleId()) ? -1L : role.getRoleId();
        SysRole info = roleMapper.checkRoleKeyUnique(role.getRoleKey());
        if (StringUtils.isNotNull(info) && info.getRoleId().longValue() != roleId.longValue())
        {
            return UserConstants.NOT_UNIQUE;
        }
        return UserConstants.UNIQUE;
    }

    /**
     * 校验角色是否允许操作
     * 
     * @param role 角色信息
     */
    @Override
    public void checkRoleAllowed(SysRole role)
    {
        if (StringUtils.isNotNull(role.getRoleId()) && isSuperAdminRole(role))
        {
            throw new ServiceException("不允许操作超级管理员角色");
        }
    }

    private boolean isSuperAdminRole(SysRole role)
    {
        if (role.isAdmin())
        {
            return true;
        }
        SysRole dbRole = roleMapper.selectRoleById(role.getRoleId());
        return dbRole != null && dbRole.isAdmin();
    }

    /**
     * 校验角色是否有数据权限
     * 
     * @param roleIds 角色id
     */
    @Override
    public void checkRoleDataScope(Long... roleIds)
    {
        if (!SecurityUtils.isAdmin())
        {
            for (Long roleId : roleIds)
            {
                SysRole role = new SysRole();
                role.setRoleId(roleId);
                List<SysRole> roles = SpringUtils.getAopProxy(this).selectRoleList(role);
                if (StringUtils.isEmpty(roles))
                {
                    throw new ServiceException("没有权限访问角色数据！", HttpStatus.FORBIDDEN);
                }
            }
        }
    }

    private void checkAuthUserScope(Long roleId, Long... userIds)
    {
        if (roleId == null)
        {
            throw new ServiceException("角色ID不能为空");
        }
        if (userIds == null || userIds.length == 0)
        {
            throw new ServiceException("用户ID不能为空");
        }
        checkRoleAllowed(new SysRole(roleId));
        checkRoleDataScope(roleId);
        for (Long userId : userIds)
        {
            if (userId == null)
            {
                throw new ServiceException("用户ID不能为空");
            }
            userService.checkUserDataScope(userId);
        }
    }

    /**
     * 通过角色ID查询角色使用数量
     * 
     * @param roleId 角色ID
     * @return 结果
     */
    @Override
    public int countUserRoleByRoleId(Long roleId)
    {
        return userRoleMapper.countUserRoleByRoleId(roleId);
    }

    /**
     * 新增保存角色信息
     * 
     * @param role 角色信息
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertRole(SysRole role)
    {
        normalizeNewRoleDataScope(role);
        int rows = roleMapper.insertRole(role);
        insertRoleMenu(role);
        insertRoleDept(role);
        return rows;
    }

    private void normalizeNewRoleDataScope(SysRole role)
    {
        if (role == null)
        {
            throw new ServiceException("角色信息不能为空");
        }
        String dataScope = StringUtils.isEmpty(role.getDataScope())
                ? DEFAULT_DATA_SCOPE : role.getDataScope().trim();
        if (!ALLOWED_DATA_SCOPES.contains(dataScope))
        {
            throw new ServiceException("数据权限范围不正确");
        }
        role.setDataScope(dataScope);
        if ("2".equals(dataScope))
        {
            checkRoleDeptDataScope(role.getDeptIds());
        }
        else
        {
            role.setDeptIds(new Long[0]);
        }
    }

    private void validateDataScope(SysRole role)
    {
        if (role == null || !Constants.Dept.isValidDataScope(role.getDataScope()))
        {
            throw new ServiceException("数据权限范围必须为1到5");
        }
        if (Constants.Dept.DATA_SCOPE_CUSTOM.equals(role.getDataScope())
                && (role.getDeptIds() == null || role.getDeptIds().length == 0))
        {
            throw new ServiceException("自定义数据权限至少选择一个部门");
        }
        if (!Constants.Dept.DATA_SCOPE_CUSTOM.equals(role.getDataScope()))
        {
            role.setDeptIds(new Long[0]);
        }
    }

    /**
     * 修改保存角色信息
     * 
     * @param role 角色信息
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateRole(SysRole role)
    {
        validateDataScope(role);
        checkRoleDeptDataScope(role.getDeptIds());
        configMapper.lockSignHrState();
        // 修改角色信息
        int roleRows = roleMapper.updateRole(role);
        if (roleRows <= 0)
        {
            throw new ServiceException("角色已被删除或修改，请刷新后重试");
        }
        List<Long> affectedUserIds = userRoleMapper.selectUserIdsByRoleId(role.getRoleId());
        // 删除角色与菜单关联
        roleMenuMapper.deleteRoleMenuByRoleId(role.getRoleId());
        insertRoleMenu(role);
        roleDeptMapper.deleteRoleDeptByRoleId(role.getRoleId());
        insertRoleDept(role);
        configMapper.syncSignHrPermissions();
        userSessionInvalidationService.recordAll(affectedUserIds,
                UserSessionInvalidationService.ROLE_GRANTS_CHANGED);
        return roleRows;
    }

    /**
     * 修改角色状态
     * 
     * @param role 角色信息
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateRoleStatus(SysRole role)
    {
        configMapper.lockSignHrState();
        List<Long> affectedUserIds = userRoleMapper.selectUserIdsByRoleId(role.getRoleId());
        int rows = roleMapper.updateRole(role);
        configMapper.syncSignHrPermissions();
        if (rows > 0)
        {
            userSessionInvalidationService.recordAll(affectedUserIds,
                    UserSessionInvalidationService.ROLE_GRANTS_CHANGED);
        }
        return rows;
    }

    /**
     * 修改数据权限信息
     * 
     * @param role 角色信息
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int authDataScope(SysRole role)
    {
        validateDataScope(role);
        configMapper.lockSignHrState();
        checkRoleDeptDataScope(role.getDeptIds());
        List<Long> affectedUserIds = userRoleMapper.selectUserIdsByRoleId(role.getRoleId());
        // 修改角色信息
        int roleRows = roleMapper.updateRole(role);
        if (roleRows <= 0)
        {
            throw new ServiceException("角色已被删除或修改，请刷新后重试");
        }
        // 删除角色与部门关联
        roleDeptMapper.deleteRoleDeptByRoleId(role.getRoleId());
        // 新增角色和部门信息（数据权限）
        insertRoleDept(role);
        configMapper.syncSignHrPermissions();
        userSessionInvalidationService.recordAll(affectedUserIds,
                UserSessionInvalidationService.ROLE_GRANTS_CHANGED);
        return roleRows;
    }

    private void checkRoleDeptDataScope(Long[] deptIds)
    {
        if (deptIds == null || deptIds.length == 0)
        {
            return;
        }
        for (Long deptId : deptIds)
        {
            if (deptId == null)
            {
                throw new ServiceException("部门ID不能为空");
            }
            deptService.checkDeptDataScope(deptId);
        }
    }

    /**
     * 新增角色菜单信息
     * 
     * @param role 角色对象
     */
    public int insertRoleMenu(SysRole role)
    {
        int rows = 1;
        if (role.getMenuIds() == null || role.getMenuIds().length == 0)
        {
            return rows;
        }
        // 新增用户与角色管理
        List<SysRoleMenu> list = new ArrayList<SysRoleMenu>();
        Long[] menuIds = role.getMenuIds() == null ? new Long[0] : role.getMenuIds();
        for (Long menuId : menuIds)
        {
            if (menuId == null)
            {
                throw new ServiceException("菜单ID不能为空");
            }
            SysRoleMenu rm = new SysRoleMenu();
            rm.setRoleId(role.getRoleId());
            rm.setMenuId(menuId);
            list.add(rm);
        }
        if (list.size() > 0)
        {
            rows = roleMenuMapper.batchRoleMenu(list);
        }
        return rows;
    }

    /**
     * 新增角色部门信息(数据权限)
     *
     * @param role 角色对象
     */
    public int insertRoleDept(SysRole role)
    {
        int rows = 1;
        if (role.getDeptIds() == null || role.getDeptIds().length == 0)
        {
            return rows;
        }
        // 新增角色与部门（数据权限）管理
        List<SysRoleDept> list = new ArrayList<SysRoleDept>();
        for (Long deptId : role.getDeptIds())
        {
            SysRoleDept rd = new SysRoleDept();
            rd.setRoleId(role.getRoleId());
            rd.setDeptId(deptId);
            list.add(rd);
        }
        if (list.size() > 0)
        {
            rows = roleDeptMapper.batchRoleDept(list);
        }
        return rows;
    }

    /**
     * 通过角色ID删除角色
     * 
     * @param roleId 角色ID
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteRoleById(Long roleId)
    {
        configMapper.lockSignHrState();
        List<Long> affectedUserIds = userRoleMapper.selectUserIdsByRoleId(roleId);
        // 删除角色与菜单关联
        roleMenuMapper.deleteRoleMenuByRoleId(roleId);
        // 删除角色与部门关联
        roleDeptMapper.deleteRoleDeptByRoleId(roleId);
        int rows = roleMapper.deleteRoleById(roleId);
        configMapper.syncSignHrPermissions();
        if (rows > 0)
        {
            userSessionInvalidationService.recordAll(affectedUserIds,
                    UserSessionInvalidationService.ROLE_GRANTS_CHANGED);
        }
        return rows;
    }

    /**
     * 批量删除角色信息
     * 
     * @param roleIds 需要删除的角色ID
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteRoleByIds(Long[] roleIds)
    {
        configMapper.lockSignHrState();
        for (Long roleId : roleIds)
        {
            checkRoleAllowed(new SysRole(roleId));
            checkRoleDataScope(roleId);
            SysRole role = selectRoleById(roleId);
            if (countUserRoleByRoleId(roleId) > 0)
            {
                throw new ServiceException(String.format("%1$s已分配,不能删除", role.getRoleName()));
            }
        }
        // 删除角色与菜单关联
        roleMenuMapper.deleteRoleMenu(roleIds);
        // 删除角色与部门关联
        roleDeptMapper.deleteRoleDept(roleIds);
        int rows = roleMapper.deleteRoleByIds(roleIds);
        configMapper.syncSignHrPermissions();
        return rows;
    }

    /**
     * 取消授权用户角色
     * 
     * @param userRole 用户和角色关联信息
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteAuthUser(SysUserRole userRole)
    {
        if (userRole == null)
        {
            throw new ServiceException("授权关系不能为空");
        }
        validateAuthUserScope(userRole.getRoleId(), userRole.getUserId());
        checkAuthUserScope(userRole.getRoleId(), userRole.getUserId());
        configMapper.lockSignHrState();
        int rows = userRoleMapper.deleteUserRoleInfo(userRole);
        if (rows > 0)
        {
            configMapper.syncSignHrPermissions();
            userSessionInvalidationService.record(userRole.getUserId(),
                    UserSessionInvalidationService.ROLE_GRANTS_CHANGED);
        }
        return rows;
    }

    /**
     * 批量取消授权用户角色
     * 
     * @param roleId 角色ID
     * @param userIds 需要取消授权的用户数据ID
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteAuthUsers(Long roleId, Long[] userIds)
    {
        validateAuthUserScope(roleId, userIds);
        checkAuthUserScope(roleId, userIds);
        configMapper.lockSignHrState();
        int rows = userRoleMapper.deleteUserRoleInfos(roleId, userIds);
        if (rows > 0)
        {
            configMapper.syncSignHrPermissions();
            userSessionInvalidationService.recordAll(Arrays.asList(userIds),
                    UserSessionInvalidationService.ROLE_GRANTS_CHANGED);
        }
        return rows;
    }

    /**
     * 批量选择授权用户角色
     * 
     * @param roleId 角色ID
     * @param userIds 需要授权的用户数据ID
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertAuthUsers(Long roleId, Long[] userIds)
    {
        validateAuthUserScope(roleId, userIds);
        checkAuthUserScope(roleId, userIds);
        configMapper.lockSignHrState();
        // 新增用户与角色管理
        List<SysUserRole> list = new ArrayList<SysUserRole>();
        for (Long userId : userIds)
        {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            list.add(ur);
        }
        int rows = userRoleMapper.batchUserRole(list);
        if (rows > 0)
        {
            configMapper.syncSignHrPermissions();
            userSessionInvalidationService.recordAll(Arrays.asList(userIds),
                    UserSessionInvalidationService.ROLE_GRANTS_CHANGED);
        }
        return rows;
    }

    private void validateAuthUserScope(Long roleId, Long... userIds)
    {
        if (roleId == null)
        {
            throw new ServiceException("角色ID不能为空");
        }
        if (userIds == null || userIds.length == 0)
        {
            throw new ServiceException("用户ID不能为空");
        }
        for (Long userId : userIds)
        {
            if (userId == null)
            {
                throw new ServiceException("用户ID不能为空");
            }
        }
    }
}

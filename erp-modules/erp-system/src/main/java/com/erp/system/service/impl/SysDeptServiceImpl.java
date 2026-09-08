package com.erp.system.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.constant.HttpStatus;
import com.erp.common.core.constant.UserConstants;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.text.Convert;
import com.erp.common.core.utils.SpringUtils;
import com.erp.common.core.utils.StringUtils;
import com.erp.common.datascope.annotation.DataScope;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.system.api.domain.SysDept;
import com.erp.system.api.domain.SysRole;
import com.erp.system.api.domain.SysUser;
import com.erp.system.domain.dto.SysSortBatchRequest;
import com.erp.system.domain.dto.SysSortChangeItem;
import com.erp.system.domain.dto.SysSortChangeRequest;
import com.erp.system.domain.vo.HrEmployeeQuery;
import com.erp.system.domain.vo.SysDeptLeaderOptionVo;
import com.erp.system.domain.vo.TreeSelect;
import com.erp.system.mapper.SysDeptMapper;
import com.erp.system.mapper.SysLegalEntityMapper;
import com.erp.system.mapper.SysRoleMapper;
import com.erp.system.service.ISysDeptService;
import com.erp.system.service.ISysUserShopService;

/**
 * 部门管理 服务实现
 * 
 * @author erp
 */
@Service
public class SysDeptServiceImpl implements ISysDeptService
{
    private static final Logger log = LoggerFactory.getLogger(SysDeptServiceImpl.class);

    private static final String PURPOSE_DELIVERY_SOURCE = "deliverySource";
    private static final String PURPOSE_REPLENISHMENT_SOURCE = "replenishmentSource";
    private static final String PURPOSE_RETURN_TARGET = "returnTarget";
    private static final String PURPOSE_CURRENT_WAREHOUSE = "currentWarehouse";
    private static final Set<String> LEADER_OPTIONAL_DEPT_TYPES = Set.of("GROUP", "STORE", "WAREHOUSE");
    private static final String INVALID_LEADER_MESSAGE = "负责人不存在、已离职、已停用或无权访问";

    @Autowired
    private SysDeptMapper deptMapper;

    @Autowired
    private SysRoleMapper roleMapper;

    @Autowired
    private ISysUserShopService userShopService;

    @Autowired
    private HrEmployeeAccessService employeeAccess;

    @Autowired
    private HrMasterDataGateService masterDataGate;

    @Autowired
    private SysLegalEntityMapper legalEntityMapper;

    /**
     * 查询部门管理数据
     * 
     * @param dept 部门信息
     * @return 部门信息集合
     */
    @Override
    @DataScope(deptAlias = "d")
    public List<SysDept> selectDeptList(SysDept dept)
    {
        return deptMapper.selectDeptList(dept);
    }

    /**
     * 查询部门树结构信息
     * 
     * @param dept 部门信息
     * @return 部门树信息集合
     */
    @Override
    public List<TreeSelect> selectDeptTreeList(SysDept dept)
    {
        List<SysDept> depts = SpringUtils.getAopProxy(this).selectDeptList(dept);
        return buildDeptTreeSelect(depts);
    }

    /**
     * 构建前端所需要树结构
     * 
     * @param depts 部门列表
     * @return 树结构列表
     */
    @Override
    public List<SysDept> buildDeptTree(List<SysDept> depts)
    {
        List<SysDept> returnList = new ArrayList<SysDept>();
        List<Long> tempList = depts.stream().map(SysDept::getDeptId).collect(Collectors.toList());
        for (SysDept dept : depts)
        {
            // 如果是顶级节点, 遍历该父节点的所有子节点
            if (!tempList.contains(dept.getParentId()))
            {
                recursionFn(depts, dept);
                returnList.add(dept);
            }
        }
        if (returnList.isEmpty())
        {
            returnList = depts;
        }
        return returnList;
    }

    /**
     * 构建前端所需要下拉树结构
     * 
     * @param depts 部门列表
     * @return 下拉树结构列表
     */
    @Override
    public List<TreeSelect> buildDeptTreeSelect(List<SysDept> depts)
    {
        List<SysDept> deptTrees = buildDeptTree(depts);
        return deptTrees.stream().map(TreeSelect::new).collect(Collectors.toList());
    }

    /**
     * 查询正常仓库列表（供业务页面选择仓库目标）
     */
    @Override
    public List<SysDept> selectWarehouseList()
    {
        return selectWarehouseList(null, null);
    }

    @Override
    public List<SysDept> selectWarehouseList(String purpose, Long scopeDeptId)
    {
        return selectWarehouseList(purpose, scopeDeptId, SecurityUtils.getUserId(), SecurityUtils.isAdmin());
    }

    List<SysDept> selectWarehouseList(Long userId, boolean admin)
    {
        return deptMapper.selectWarehouseList();
    }

    List<SysDept> selectWarehouseList(String purpose, Long scopeDeptId, Long userId, boolean admin)
    {
        if (purpose == null || purpose.isEmpty())
        {
            return deptMapper.selectWarehouseList();
        }
        if (PURPOSE_REPLENISHMENT_SOURCE.equals(purpose))
        {
            if (scopeDeptId == null || scopeDeptId <= 0)
            {
                return Collections.emptyList();
            }
            assertScopeDeptSelectable(userId, admin, scopeDeptId,
                    "当前用户无权选择该门店");
            return deptMapper.selectReplenishmentSourceWarehouseList(
                    scopeDeptId);
        }
        if (!PURPOSE_DELIVERY_SOURCE.equals(purpose)
                && !PURPOSE_RETURN_TARGET.equals(purpose)
                && !PURPOSE_CURRENT_WAREHOUSE.equals(purpose))
        {
            throw new ServiceException("不支持的仓库选择用途",
                    HttpStatus.BAD_REQUEST);
        }
        List<SysDept> warehouses = deptMapper.selectWarehouseList();
        if (PURPOSE_DELIVERY_SOURCE.equals(purpose))
        {
            return warehouses.stream()
                    .filter(warehouse -> isDeptInScope(scopeDeptId, warehouse))
                    .collect(Collectors.toList());
        }
        if (PURPOSE_RETURN_TARGET.equals(purpose))
        {
            if (scopeDeptId == null || scopeDeptId <= 0)
            {
                return Collections.emptyList();
            }
            assertScopeDeptSelectable(userId, admin, scopeDeptId,
                    "当前用户无权选择该门店");
            return warehouses.stream()
                    .filter(warehouse -> canSelectWarehouse(userId, admin,
                            warehouse.getDeptId()))
                    .collect(Collectors.toList());
        }
        if (PURPOSE_CURRENT_WAREHOUSE.equals(purpose))
        {
            return warehouses.stream()
                    .filter(warehouse -> warehouse.getDeptId() != null && warehouse.getDeptId().equals(scopeDeptId))
                    .filter(warehouse -> canSelectWarehouse(userId, admin, warehouse.getDeptId()))
                    .collect(Collectors.toList());
        }
        throw new IllegalStateException("仓库选择用途分支未覆盖");
    }

    private void assertScopeDeptSelectable(Long userId, boolean admin,
            Long scopeDeptId, String message)
    {
        if (admin)
        {
            return;
        }
        if (userShopService == null)
        {
            throw new ServiceException(message, HttpStatus.FORBIDDEN);
        }
        userShopService.checkUserShopScope(userId, scopeDeptId, false);
    }

    @Override
    public List<SysDept> selectVisibleStoreList(Long scopeDeptId)
    {
        return selectVisibleStoreList(scopeDeptId, SecurityUtils.getUserId(), SecurityUtils.isAdmin());
    }

    @Override
    public List<SysDeptLeaderOptionVo> selectLeaderOptions(String keyword)
    {
        if (employeeAccess == null)
        {
            throw new ServiceException("负责人候选查询不可用");
        }
        HrEmployeeQuery query = new HrEmployeeQuery();
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        query.setKeyword(StringUtils.isEmpty(normalizedKeyword) ? null : normalizedKeyword);
        return employeeAccess.listActiveScoped(query).stream()
                .filter(user -> user != null && user.getUserId() != null && !UserConstants.isAdmin(user.getUserId()))
                .filter(user -> UserConstants.NORMAL.equals(user.getStatus()))
                .filter(user -> StringUtils.isNotEmpty(user.getNickName()))
                .collect(Collectors.toMap(SysUser::getUserId, user -> user, (left, right) -> left))
                .values().stream()
                .sorted(Comparator.comparing(SysUser::getNickName)
                        .thenComparing(SysUser::getUserId))
                .limit(100)
                .map(this::toLeaderOption)
                .collect(Collectors.toList());
    }

    private SysDeptLeaderOptionVo toLeaderOption(SysUser user)
    {
        SysDeptLeaderOptionVo option = new SysDeptLeaderOptionVo();
        option.setUserId(user.getUserId());
        option.setEmployeeName(user.getNickName().trim());
        String employeeNo = user.getEmployeeNo();
        if (StringUtils.isEmpty(employeeNo) && user.hasProfile())
        {
            employeeNo = user.getProfile().getEmployeeNo();
        }
        option.setEmployeeNo(employeeNo);
        option.setDeptId(user.getDeptId());
        option.setDeptName(user.getDept() == null ? null : user.getDept().getDeptName());
        return option;
    }

    List<SysDept> selectVisibleStoreList(Long scopeDeptId, Long userId, boolean admin)
    {
        if (scopeDeptId == null || scopeDeptId == 0)
        {
            throw new ServiceException("请先选择店铺或仓库");
        }
        if (!admin)
        {
            if (userShopService == null)
            {
                throw new ServiceException("当前用户无权选择该店铺或仓库", HttpStatus.FORBIDDEN);
            }
            userShopService.checkUserShopScope(userId, scopeDeptId, false);
        }
        return deptMapper.selectVisibleStoreListByScopeDeptId(scopeDeptId);
    }

    private boolean canSelectWarehouse(Long userId, boolean admin, Long warehouseDeptId)
    {
        if (admin)
        {
            return true;
        }
        if (userShopService == null)
        {
            return false;
        }
        try
        {
            userShopService.checkUserShopScope(userId, warehouseDeptId, false);
            return true;
        }
        catch (ServiceException ex)
        {
            return false;
        }
    }

    private boolean isDeptInScope(Long scopeDeptId, SysDept dept)
    {
        if (scopeDeptId == null || dept == null)
        {
            return false;
        }
        if (scopeDeptId.equals(dept.getDeptId()) || scopeDeptId.equals(dept.getParentId()))
        {
            return true;
        }
        String ancestors = dept.getAncestors();
        if (StringUtils.isEmpty(ancestors))
        {
            return false;
        }
        for (String ancestor : ancestors.split(","))
        {
            if (String.valueOf(scopeDeptId).equals(ancestor))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * 根据角色ID查询部门树信息
     * 
     * @param roleId 角色ID
     * @return 选中部门列表
     */
    @Override
    public List<Long> selectDeptListByRoleId(Long roleId)
    {
        SysRole role = roleMapper.selectRoleById(roleId);
        return deptMapper.selectDeptListByRoleId(roleId, role.isDeptCheckStrictly());
    }

    /**
     * 根据部门ID查询信息
     * 
     * @param deptId 部门ID
     * @return 部门信息
     */
    @Override
    public SysDept selectDeptById(Long deptId)
    {
        return deptMapper.selectDeptById(deptId);
    }

    /**
     * 根据ID查询所有子部门（正常状态）
     * 
     * @param deptId 部门ID
     * @return 子部门数
     */
    @Override
    public int selectNormalChildrenDeptById(Long deptId)
    {
        return deptMapper.selectNormalChildrenDeptById(deptId);
    }

    /**
     * 是否存在子节点
     * 
     * @param deptId 部门ID
     * @return 结果
     */
    @Override
    public boolean hasChildByDeptId(Long deptId)
    {
        int result = deptMapper.hasChildByDeptId(deptId);
        return result > 0;
    }

    /**
     * 查询部门是否存在用户
     * 
     * @param deptId 部门ID
     * @return 结果 true 存在 false 不存在
     */
    @Override
    public boolean checkDeptExistUser(Long deptId)
    {
        int result = deptMapper.checkDeptExistUser(deptId);
        return result > 0;
    }

    /**
     * 校验部门名称是否唯一
     * 
     * @param dept 部门信息
     * @return 结果
     */
    @Override
    public boolean checkDeptNameUnique(SysDept dept)
    {
        Long deptId = StringUtils.isNull(dept.getDeptId()) ? -1L : dept.getDeptId();
        SysDept info = deptMapper.checkDeptNameUnique(dept.getDeptName(), dept.getParentId());
        if (StringUtils.isNotNull(info) && info.getDeptId().longValue() != deptId.longValue())
        {
            return UserConstants.NOT_UNIQUE;
        }
        return UserConstants.UNIQUE;
    }

    /**
     * 校验部门是否有数据权限
     * 
     * @param deptId 部门id
     */
    @Override
    public void checkDeptDataScope(Long deptId)
    {
        if (!SecurityUtils.isAdmin() && StringUtils.isNotNull(deptId))
        {
            SysDept dept = new SysDept();
            dept.setDeptId(deptId);
            List<SysDept> depts = SpringUtils.getAopProxy(this).selectDeptList(dept);
            if (StringUtils.isEmpty(depts))
            {
                throw new ServiceException("没有权限访问部门数据！", HttpStatus.FORBIDDEN);
            }
        }
    }

    /**
     * 新增保存部门信息
     * 
     * @param dept 部门信息
     * @return 结果
     */
    @Override
    public int insertDept(SysDept dept)
    {
        if (dept.getParentId() == null)
        {
            throw new ServiceException("上级部门不能为空");
        }
        if (dept.getParentId() == 0 && !SecurityUtils.isAdmin())
        {
            throw new ServiceException("没有权限访问部门数据！", HttpStatus.FORBIDDEN);
        }
        checkDeptDataScope(dept.getParentId());
        SysDept info = deptMapper.selectDeptById(dept.getParentId());
        if (info == null)
        {
            throw new ServiceException("上级部门不存在");
        }
        // 如果父节点不为正常状态,则不允许新增子节点
        if (!UserConstants.DEPT_NORMAL.equals(info.getStatus()))
        {
            throw new ServiceException("部门停用，不允许新增");
        }
        if (StringUtils.isEmpty(dept.getDeptType()))
        {
            dept.setDeptType("STORE");
        }
        if (StringUtils.isEmpty(dept.getStatus()))
        {
            dept.setStatus(UserConstants.DEPT_NORMAL);
        }
        normalizeLeaderIdentity(dept);
        validateLegalEntityBinding(dept.getLegalEntityId());
        dept.setAncestors(info.getAncestors() + "," + dept.getParentId());
        if (masterDataGate != null) masterDataGate.validateDepartmentChange(dept, null);
        return deptMapper.insertDept(dept);
    }

    /**
     * 修改保存部门信息
     * 
     * @param dept 部门信息
     * @return 结果
     */
    @Override
    public int updateDept(SysDept dept)
    {
        boolean leaderIdentitySpecified = dept.isLeaderUserIdSpecified();
        boolean legalEntitySpecified = dept.isLegalEntityIdSpecified();
        SysDept newParentDept = deptMapper.selectDeptById(dept.getParentId());
        SysDept oldDept = deptMapper.selectDeptById(dept.getDeptId());
        if (oldDept != null)
        {
            if (StringUtils.isEmpty(dept.getDeptType())) dept.setDeptType(oldDept.getDeptType());
            if (StringUtils.isEmpty(dept.getStatus())) dept.setStatus(oldDept.getStatus());
            if (!leaderIdentitySpecified)
            {
                dept.setLeaderUserId(oldDept.getLeaderUserId());
                dept.setLeader(oldDept.getLeader());
            }
            if (!legalEntitySpecified)
            {
                dept.setLegalEntityId(oldDept.getLegalEntityId());
            }
        }
        validateLegalEntityBinding(dept.getLegalEntityId());
        if (leaderIdentitySpecified || oldDept == null || leaderGovernanceChanged(dept, oldDept))
        {
            normalizeLeaderIdentity(dept);
        }
        if (StringUtils.isNotNull(newParentDept) && StringUtils.isNotNull(oldDept))
        {
            String newAncestors = newParentDept.getAncestors() + "," + newParentDept.getDeptId();
            String oldAncestors = oldDept.getAncestors();
            dept.setAncestors(newAncestors);
            if (masterDataGate != null) masterDataGate.validateDepartmentChange(dept, oldDept);
            updateDeptChildren(dept.getDeptId(), newAncestors, oldAncestors);
        }
        else if (masterDataGate != null)
        {
            masterDataGate.validateDepartmentChange(dept, oldDept);
        }
        int result = deptMapper.updateDept(dept);
        if (UserConstants.DEPT_NORMAL.equals(dept.getStatus()) && StringUtils.isNotEmpty(dept.getAncestors())
                && !StringUtils.equals("0", dept.getAncestors()))
        {
            // 如果该部门是启用状态，则启用该部门的所有上级部门
            updateParentDeptStatusNormal(dept);
        }
        return result;
    }

    private void validateLegalEntityBinding(Long legalEntityId)
    {
        if (legalEntityId == null)
        {
            return;
        }
        com.erp.system.api.domain.SysLegalEntity legalEntity =
                legalEntityMapper.selectLegalEntityById(legalEntityId);
        if (legalEntity == null || !UserConstants.NORMAL.equals(legalEntity.getStatus()))
        {
            throw new ServiceException("所选公司主体不存在或已停用");
        }
    }

    private boolean leaderGovernanceChanged(SysDept proposed, SysDept previous)
    {
        return !StringUtils.equals(proposed.getStatus(), previous.getStatus())
                || !StringUtils.equalsIgnoreCase(proposed.getDeptType(), previous.getDeptType());
    }

    /**
     * Treats leaderUserId as the only writable identity and derives the legacy name snapshot.
     * Package visibility keeps the contract directly testable without bypassing production service calls.
     */
    void normalizeLeaderIdentity(SysDept dept)
    {
        if (dept == null)
        {
            throw new ServiceException("部门信息不能为空");
        }
        Long leaderUserId = dept.getLeaderUserId();
        if (leaderUserId == null || leaderUserId <= 0)
        {
            dept.setLeaderUserId(null);
            dept.setLeader(null);
            if (leaderRequired(dept))
            {
                throw new ServiceException("启用的公司或部门必须选择有效负责人");
            }
            return;
        }
        if (UserConstants.isAdmin(leaderUserId))
        {
            throw new ServiceException("超级管理员不能设置为负责人");
        }
        if (employeeAccess == null)
        {
            throw new ServiceException(INVALID_LEADER_MESSAGE);
        }
        HrEmployeeQuery query = new HrEmployeeQuery();
        query.setUserId(leaderUserId);
        SysUser leader;
        try
        {
            leader = employeeAccess.findActiveScoped(query);
        }
        catch (ServiceException ex)
        {
            throw new ServiceException(INVALID_LEADER_MESSAGE);
        }
        if (leader == null || !leaderUserId.equals(leader.getUserId())
                || !UserConstants.NORMAL.equals(leader.getStatus())
                || StringUtils.isEmpty(leader.getNickName())
                || leader.hasProfile() && "离职".equals(leader.getProfile().getEmployeeStatus()))
        {
            throw new ServiceException(INVALID_LEADER_MESSAGE);
        }
        dept.setLeader(leader.getNickName().trim());
    }

    private boolean leaderRequired(SysDept dept)
    {
        String status = dept.getStatus();
        boolean active = StringUtils.isEmpty(status) || UserConstants.DEPT_NORMAL.equals(status);
        String type = dept.getDeptType() == null ? "" : dept.getDeptType().trim().toUpperCase();
        return active && !LEADER_OPTIONAL_DEPT_TYPES.contains(type);
    }

    /**
     * 修改该部门的父级部门状态
     * 
     * @param dept 当前部门
     */
    private void updateParentDeptStatusNormal(SysDept dept)
    {
        String ancestors = dept.getAncestors();
        Long[] deptIds = Convert.toLongArray(ancestors);
        deptMapper.updateDeptStatusNormal(deptIds);
    }

    /**
     * 修改子元素关系
     * 
     * @param deptId 被修改的部门ID
     * @param newAncestors 新的父ID集合
     * @param oldAncestors 旧的父ID集合
     */
    public void updateDeptChildren(Long deptId, String newAncestors, String oldAncestors)
    {
        List<SysDept> children = deptMapper.selectChildrenDeptById(deptId);
        for (SysDept child : children)
        {
            child.setAncestors(child.getAncestors().replaceFirst(oldAncestors, newAncestors));
        }
        if (children.size() > 0)
        {
            deptMapper.updateDeptChildren(children);
        }
    }

    /**
     * 保存部门排序
     *
     * @param request 带原排序基线的变更集合
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDeptSort(SysSortChangeRequest request)
    {
        List<SysSortChangeItem> changes = SysSortChangeSupport.normalize(request);
        List<Long> deptIds = changes.stream().map(SysSortChangeItem::getId).collect(Collectors.toList());
        for (Long deptId : deptIds)
        {
            checkDeptDataScope(deptId);
        }

        List<SysDept> lockedRows = deptMapper.selectDeptOrdersForUpdate(deptIds);
        if (lockedRows == null || lockedRows.size() != changes.size())
        {
            throw new ServiceException("部分部门已不存在，请刷新后重试");
        }
        Map<Long, Integer> currentOrders = new HashMap<Long, Integer>();
        for (SysDept lockedRow : lockedRows)
        {
            currentOrders.put(lockedRow.getDeptId(), lockedRow.getOrderNum());
        }
        List<Long> conflictIds = changes.stream()
                .filter(change -> !Objects.equals(currentOrders.get(change.getId()), change.getExpectedOrderNum()))
                .map(SysSortChangeItem::getId)
                .collect(Collectors.toList());
        if (!conflictIds.isEmpty())
        {
            log.warn("department sort conflict count={} ids={}", conflictIds.size(), conflictIds);
            throw new SysSortConflictException(conflictIds);
        }

        for (SysSortChangeItem change : changes)
        {
            SysDept dept = new SysDept();
            dept.setDeptId(change.getId());
            dept.setOrderNum(change.getNewOrderNum());
            if (deptMapper.updateDeptSort(dept) != 1)
            {
                throw new ServiceException("部门已被删除或修改，请刷新后重试");
            }
        }
    }

    /**
     * 标准 JSON 差量批量排序。先完成数据权限、存在性和重复项校验，再统一写入。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDeptSort(SysSortBatchRequest request)
    {
        List<SysSortBatchRequest.Item> items = request == null ? null : request.getItems();
        if (items == null || items.isEmpty() || items.size() > 500)
        {
            throw new ServiceException("排序项数量必须在1到500之间");
        }
        Set<Long> deptIds = new LinkedHashSet<>();
        for (SysSortBatchRequest.Item item : items)
        {
            if (item == null || item.getId() == null || item.getId() <= 0)
            {
                throw new ServiceException("部门ID不正确");
            }
            if (item.getOrderNum() == null || item.getOrderNum() < 0 || item.getOrderNum() > 999999)
            {
                throw new ServiceException("部门排序值必须在0到999999之间");
            }
            if (!deptIds.add(item.getId()))
            {
                throw new ServiceException("部门排序项不能重复");
            }
            checkDeptDataScope(item.getId());
            if (deptMapper.selectDeptById(item.getId()) == null)
            {
                throw new ServiceException("部门不存在或已被删除");
            }
        }
        for (SysSortBatchRequest.Item item : items)
        {
            SysDept dept = new SysDept();
            dept.setDeptId(item.getId());
            dept.setOrderNum(item.getOrderNum());
            if (deptMapper.updateDeptSort(dept) != 1)
            {
                throw new ServiceException("部门已被删除或修改，请刷新后重试");
            }
        }
    }

    /**
     * 删除部门管理信息
     *
     * @param deptId 部门ID
     * @return 结果
     */
    @Override
    public int deleteDeptById(Long deptId)
    {
        return deptMapper.deleteDeptById(deptId);
    }

    /**
     * 查询店铺树（供选店页面使用）
     */
    @Override
    public List<SysDept> selectShopTree(SysDept dept)
    {
        return userShopService.selectAuthorizedShopTree(SecurityUtils.getUserId(), SecurityUtils.isAdmin());
    }

    /**
     * 递归列表
     */
    private void recursionFn(List<SysDept> list, SysDept t)
    {
        // 得到子节点列表
        List<SysDept> childList = getChildList(list, t);
        t.setChildren(childList);
        for (SysDept tChild : childList)
        {
            if (hasChild(list, tChild))
            {
                recursionFn(list, tChild);
            }
        }
    }

    /**
     * 得到子节点列表
     */
    private List<SysDept> getChildList(List<SysDept> list, SysDept t)
    {
        List<SysDept> tlist = new ArrayList<SysDept>();
        Iterator<SysDept> it = list.iterator();
        while (it.hasNext())
        {
            SysDept n = (SysDept) it.next();
            if (StringUtils.isNotNull(n.getParentId()) && n.getParentId().longValue() == t.getDeptId().longValue())
            {
                tlist.add(n);
            }
        }
        return tlist;
    }

    /**
     * 判断是否有子节点
     */
    private boolean hasChild(List<SysDept> list, SysDept t)
    {
        return getChildList(list, t).size() > 0 ? true : false;
    }
}

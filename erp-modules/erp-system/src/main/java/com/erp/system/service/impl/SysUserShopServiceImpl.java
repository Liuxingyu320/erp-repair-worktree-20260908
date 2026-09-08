package com.erp.system.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.constant.HttpStatus;
import com.erp.common.core.constant.UserConstants;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;
import com.erp.system.api.domain.SysDept;
import com.erp.system.domain.SysUserShop;
import com.erp.system.domain.vo.SysUserShopScopeVo;
import com.erp.system.domain.vo.SysUserShopScopePreviewVo;
import com.erp.system.mapper.SysDeptMapper;
import com.erp.system.mapper.SysUserShopMapper;
import com.erp.system.service.ISysUserShopService;

/**
 * 用户店铺授权 服务实现
 *
 * @author erp
 */
@Service
public class SysUserShopServiceImpl implements ISysUserShopService
{
    @Autowired
    private SysDeptMapper deptMapper;

    @Autowired
    private SysUserShopMapper userShopMapper;

    @Override
    public List<Long> selectShopDeptIdsByUserId(Long userId)
    {
        if (userId == null)
        {
            return Collections.emptyList();
        }
        return userShopMapper.selectShopDeptIdsByUserId(userId);
    }

    @Override
    public Map<Long, List<Long>> selectShopDeptIdsByUserIds(Long[] userIds)
    {
        List<Long> uniqueUserIds = normalizeDeptIds(userIds);
        Map<Long, List<Long>> result = new LinkedHashMap<Long, List<Long>>();
        if (uniqueUserIds.isEmpty())
        {
            return result;
        }
        for (Long userId : uniqueUserIds)
        {
            result.put(userId, new ArrayList<Long>());
        }
        List<SysUserShop> rows = userShopMapper.selectUserShopsByUserIds(uniqueUserIds);
        for (SysUserShop row : rows)
        {
            if (row.getUserId() != null && row.getDeptId() != null && result.containsKey(row.getUserId()))
            {
                result.get(row.getUserId()).add(row.getDeptId());
            }
        }
        return result;
    }

    @Override
    public SysUserShopScopeVo selectUserShopScope(Long userId, Long operatorUserId, boolean operatorAdmin)
    {
        List<Long> shopDeptIds = normalizeDeptIds(selectShopDeptIdsByUserId(userId));
        SysUserShopScopeVo scope = new SysUserShopScopeVo();
        scope.setShopIds(shopDeptIds);
        if (operatorAdmin)
        {
            scope.setEditableShopIds(shopDeptIds);
            scope.setPreservedShopIds(Collections.emptyList());
            scope.setOutOfScopeCount(0);
            return scope;
        }
        if (operatorUserId == null)
        {
            throw new ServiceException("操作人ID不能为空");
        }
        List<Long> editableShopIds = new ArrayList<Long>();
        List<Long> preservedShopIds = new ArrayList<Long>();
        for (Long shopDeptId : shopDeptIds)
        {
            if (userShopMapper.countUserShopScope(operatorUserId, shopDeptId) > 0)
            {
                editableShopIds.add(shopDeptId);
            }
            else
            {
                preservedShopIds.add(shopDeptId);
            }
        }
        scope.setEditableShopIds(editableShopIds);
        scope.setPreservedShopIds(preservedShopIds);
        scope.setOutOfScopeCount(preservedShopIds.size());
        return scope;
    }

    @Override
    public SysUserShopScopePreviewVo previewUserShops(Long userId, List<Long> shopDeptIds,
            Long operatorUserId, boolean operatorAdmin)
    {
        if (userId == null)
        {
            throw new ServiceException("用户ID不能为空");
        }
        List<Long> proposed = normalizeDeptIds(shopDeptIds);
        validateNormalShopIds(proposed);
        List<Long> current = normalizeDeptIds(userShopMapper.selectAllShopDeptIdsByUserId(userId));
        List<Long> editableBoundary = selectEditableBoundary(operatorUserId, operatorAdmin);
        Set<Long> editableSet = new LinkedHashSet<Long>(editableBoundary);
        if (!editableSet.containsAll(proposed))
        {
            throw new ServiceException("无权分配当前范围外的公司、组织、店铺或仓库");
        }

        Set<Long> currentSet = new LinkedHashSet<Long>(current);
        List<Long> preserved = current.stream()
                .filter(id -> !editableSet.contains(id)).collect(Collectors.toList());
        List<Long> editableExisting = current.stream()
                .filter(editableSet::contains).collect(Collectors.toList());
        Set<Long> proposedSet = new LinkedHashSet<Long>(proposed);
        List<Long> added = proposed.stream()
                .filter(id -> !currentSet.contains(id)).collect(Collectors.toList());
        List<Long> removed = editableExisting.stream()
                .filter(id -> !proposedSet.contains(id)).collect(Collectors.toList());

        Set<Long> finalDirect = new LinkedHashSet<Long>(preserved);
        finalDirect.addAll(proposed);
        List<SysDept> allNodes = deptMapper.selectShopAuthTreeList();
        Set<Long> currentEffective = effectiveScope(currentSet, allNodes);
        Set<Long> finalEffective = effectiveScope(finalDirect, allNodes);

        SysUserShopScopePreviewVo preview = new SysUserShopScopePreviewVo();
        preview.setNormalizedShopIds(proposed);
        preview.setDirectAddedIds(added);
        preview.setDirectRemovedIds(removed);
        preview.setPreservedShopIds(preserved);
        preview.setEffectiveAddedCount(differenceSize(finalEffective, currentEffective));
        preview.setEffectiveRemovedCount(differenceSize(currentEffective, finalEffective));
        preview.setScopeVersion(scopeVersion(current, editableBoundary));
        List<String> warnings = new ArrayList<String>();
        if (!preserved.isEmpty())
        {
            warnings.add("有" + preserved.size() + "项当前管理范围外授权会原样保留");
        }
        if (proposed.isEmpty())
        {
            warnings.add("保存后该用户在当前可编辑范围内没有直接组织授权");
        }
        if (preview.getEffectiveRemovedCount() > 0)
        {
            warnings.add("本次将减少" + preview.getEffectiveRemovedCount() + "个生效业务组织");
        }
        preview.setWarnings(warnings);
        return preview;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysUserShopScopePreviewVo savePreviewedUserShops(Long userId, List<Long> shopDeptIds,
            String expectedScopeVersion, String operName, Long operatorUserId, boolean operatorAdmin)
    {
        if (expectedScopeVersion == null || !expectedScopeVersion.matches("[0-9a-f]{64}"))
        {
            throw new ServiceException("请先预览组织授权差异后再保存");
        }
        if (userShopMapper.lockUserForShopScope(userId) == null)
        {
            throw new ServiceException("用户不存在");
        }
        SysUserShopScopePreviewVo preview = previewUserShops(userId, shopDeptIds,
                operatorUserId, operatorAdmin);
        if (!expectedScopeVersion.equals(preview.getScopeVersion()))
        {
            throw new SysUserShopScopeConflictException();
        }

        List<Long> proposed = preview.getNormalizedShopIds();
        if (operatorAdmin)
        {
            userShopMapper.deleteUserShopByUserId(userId);
            insertUserShopBindings(userId, proposed, operName);
        }
        else
        {
            Set<Long> preserved = new LinkedHashSet<Long>(preview.getPreservedShopIds());
            List<Long> editableExisting = normalizeDeptIds(userShopMapper.selectAllShopDeptIdsByUserId(userId))
                    .stream().filter(id -> !preserved.contains(id)).collect(Collectors.toList());
            userShopMapper.deleteUserShopByUserIdAndDeptIds(userId, editableExisting);
            insertUserShopBindings(userId, proposed, operName);
        }
        return previewUserShops(userId, proposed, operatorUserId, operatorAdmin);
    }

    private List<Long> selectEditableBoundary(Long operatorUserId, boolean operatorAdmin)
    {
        if (!operatorAdmin && operatorUserId == null)
        {
            throw new ServiceException("操作人ID不能为空");
        }
        List<SysDept> nodes;
        if (operatorAdmin)
        {
            nodes = deptMapper.selectShopAuthTreeList();
        }
        else
        {
            List<Long> operatorScope = normalizeDeptIds(selectShopDeptIdsByUserId(operatorUserId));
            if (operatorScope.isEmpty())
            {
                return Collections.emptyList();
            }
            nodes = deptMapper.selectShopAuthTreeListByShopIds(operatorScope);
        }
        return nodes.stream().filter(this::isAuthorizableNode).map(SysDept::getDeptId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream().sorted().collect(Collectors.toList());
    }

    private boolean isAuthorizableNode(SysDept dept)
    {
        return dept != null && Set.of("GROUP", "COMPANY", "STORE", "WAREHOUSE").contains(dept.getDeptType());
    }

    private Set<Long> effectiveScope(Set<Long> directIds, List<SysDept> nodes)
    {
        Set<Long> effective = new LinkedHashSet<Long>();
        if (directIds.isEmpty() || nodes == null)
        {
            return effective;
        }
        for (SysDept node : nodes)
        {
            if (!isAuthorizableNode(node) || node.getDeptId() == null)
            {
                continue;
            }
            if (directIds.contains(node.getDeptId()) || containsAncestor(node.getAncestors(), directIds))
            {
                effective.add(node.getDeptId());
            }
        }
        return effective;
    }

    private boolean containsAncestor(String ancestors, Set<Long> directIds)
    {
        if (ancestors == null || ancestors.isBlank())
        {
            return false;
        }
        for (String token : ancestors.split(","))
        {
            try
            {
                if (directIds.contains(Long.valueOf(token.trim())))
                {
                    return true;
                }
            }
            catch (NumberFormatException ignored)
            {
                // Invalid historical ancestor text must not broaden authorization.
            }
        }
        return false;
    }

    private int differenceSize(Set<Long> left, Set<Long> right)
    {
        Set<Long> difference = new LinkedHashSet<Long>(left);
        difference.removeAll(right);
        return difference.size();
    }

    private String scopeVersion(List<Long> current, List<Long> editableBoundary)
    {
        String direct = current.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
        String boundary = editableBoundary.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
        String payload = "direct=" + direct + "\nboundary=" + boundary;
        try
        {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException ex)
        {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    @Override
    public List<SysDept> selectAuthorizedShopTree(Long userId, boolean admin)
    {
        if (admin)
        {
            return buildDeptTree(deptMapper.selectShopAuthTreeList());
        }
        List<Long> shopDeptIds = selectShopDeptIdsByUserId(userId);
        if (StringUtils.isEmpty(shopDeptIds))
        {
            return Collections.emptyList();
        }
        return buildDeptTree(deptMapper.selectShopAuthTreeListByShopIds(shopDeptIds));
    }

    @Override
    public List<SysDept> selectAllShopTree()
    {
        return buildDeptTree(deptMapper.selectShopAuthTreeList());
    }

    @Override
    public void checkUserShopScope(Long userId, Long deptId, boolean admin)
    {
        if (deptId == null || deptId == 0)
        {
            throw new ServiceException("请先选择店铺或仓库");
        }
        if (admin)
        {
            return;
        }
        if (userShopMapper.countUserShopScope(userId, deptId) <= 0)
        {
            throw new ServiceException("当前用户无权选择该店铺或仓库", HttpStatus.FORBIDDEN);
        }
    }

    @Override
    @Deprecated
    @Transactional(rollbackFor = Exception.class)
    public int saveUserShops(Long userId, Long[] shopDeptIds, String operName)
    {
        return saveUserShops(userId, shopDeptIds, operName, null, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int saveUserShops(Long userId, Long[] shopDeptIds, String operName, Long operatorUserId, boolean operatorAdmin)
    {
        if (userId == null)
        {
            throw new ServiceException("用户ID不能为空");
        }
        List<Long> uniqueDeptIds = normalizeDeptIds(shopDeptIds);
        if (!operatorAdmin && operatorUserId == null)
        {
            throw new ServiceException("操作人ID不能为空");
        }
        validateNormalShopIds(uniqueDeptIds);
        if (operatorAdmin)
        {
            userShopMapper.deleteUserShopByUserId(userId);
            return insertUserShopBindings(userId, uniqueDeptIds, operName);
        }
        checkAssignableShopScope(operatorUserId, false, uniqueDeptIds);
        List<Long> editableExistingDeptIds = selectEditableExistingDeptIds(userId, operatorUserId);
        userShopMapper.deleteUserShopByUserIdAndDeptIds(userId, editableExistingDeptIds);
        return insertUserShopBindings(userId, uniqueDeptIds, operName);
    }

    private void validateNormalShopIds(List<Long> deptIds)
    {
        if (deptIds.isEmpty())
        {
            return;
        }
        int validShopCount = deptMapper.countNormalShopByIds(deptIds);
        if (validShopCount != deptIds.size())
        {
            throw new ServiceException("只能选择正常状态的公司、组织、店铺或仓库");
        }
    }

    private int insertUserShopBindings(Long userId, List<Long> uniqueDeptIds, String operName)
    {
        if (uniqueDeptIds.isEmpty())
        {
            return 1;
        }
        List<SysUserShop> bindings = new ArrayList<SysUserShop>();
        for (int i = 0; i < uniqueDeptIds.size(); i++)
        {
            SysUserShop userShop = new SysUserShop();
            userShop.setUserId(userId);
            userShop.setDeptId(uniqueDeptIds.get(i));
            userShop.setIsDefault(i == 0 ? UserConstants.YES : "N");
            userShop.setCreateBy(operName);
            bindings.add(userShop);
        }
        return userShopMapper.batchUserShop(bindings);
    }

    private List<Long> selectEditableExistingDeptIds(Long userId, Long operatorUserId)
    {
        List<Long> existingDeptIds = userShopMapper.selectAllShopDeptIdsByUserId(userId);
        if (StringUtils.isEmpty(existingDeptIds))
        {
            return Collections.emptyList();
        }
        List<Long> editableDeptIds = new ArrayList<Long>();
        Set<Long> uniqueExistingDeptIds = existingDeptIds.stream()
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (Long deptId : uniqueExistingDeptIds)
        {
            if (userShopMapper.countUserShopScope(operatorUserId, deptId) > 0)
            {
                editableDeptIds.add(deptId);
            }
        }
        return editableDeptIds;
    }

    private void checkAssignableShopScope(Long operatorUserId, boolean operatorAdmin, List<Long> deptIds)
    {
        if (operatorAdmin)
        {
            return;
        }
        if (operatorUserId == null)
        {
            throw new ServiceException("操作人ID不能为空");
        }
        for (Long deptId : deptIds)
        {
            if (userShopMapper.countUserShopScope(operatorUserId, deptId) <= 0)
            {
                throw new ServiceException("无权分配该店铺或仓库", HttpStatus.FORBIDDEN);
            }
        }
    }

    private List<Long> normalizeDeptIds(Long[] deptIds)
    {
        if (deptIds == null || deptIds.length == 0)
        {
            return Collections.emptyList();
        }
        Set<Long> unique = Arrays.stream(deptIds)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new ArrayList<Long>(unique);
    }

    private List<Long> normalizeDeptIds(List<Long> deptIds)
    {
        if (StringUtils.isEmpty(deptIds))
        {
            return Collections.emptyList();
        }
        return deptIds.stream()
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .collect(Collectors.toList());
    }

    private List<SysDept> buildDeptTree(List<SysDept> depts)
    {
        List<SysDept> returnList = new ArrayList<SysDept>();
        if (StringUtils.isEmpty(depts))
        {
            return returnList;
        }
        List<Long> tempList = depts.stream().map(SysDept::getDeptId).collect(Collectors.toList());
        for (SysDept dept : depts)
        {
            dept.setChildren(new ArrayList<SysDept>());
        }
        for (SysDept dept : depts)
        {
            if (!tempList.contains(dept.getParentId()))
            {
                recursionFn(depts, dept);
                returnList.add(dept);
            }
        }
        return returnList.isEmpty() ? depts : returnList;
    }

    private void recursionFn(List<SysDept> list, SysDept t)
    {
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

    private List<SysDept> getChildList(List<SysDept> list, SysDept t)
    {
        List<SysDept> tlist = new ArrayList<SysDept>();
        Iterator<SysDept> it = list.iterator();
        while (it.hasNext())
        {
            SysDept n = it.next();
            if (StringUtils.isNotNull(n.getParentId()) && n.getParentId().longValue() == t.getDeptId().longValue())
            {
                tlist.add(n);
            }
        }
        return tlist;
    }

    private boolean hasChild(List<SysDept> list, SysDept t)
    {
        return getChildList(list, t).size() > 0;
    }
}

package com.erp.system.service;

import java.util.List;
import java.util.Map;
import com.erp.system.api.domain.SysDept;
import com.erp.system.domain.vo.SysUserShopScopeVo;
import com.erp.system.domain.vo.SysUserShopScopePreviewVo;

/**
 * 用户店铺授权 服务层
 *
 * @author erp
 */
public interface ISysUserShopService
{
    List<Long> selectShopDeptIdsByUserId(Long userId);

    Map<Long, List<Long>> selectShopDeptIdsByUserIds(Long[] userIds);

    SysUserShopScopeVo selectUserShopScope(Long userId, Long operatorUserId, boolean operatorAdmin);

    SysUserShopScopePreviewVo previewUserShops(Long userId, List<Long> shopDeptIds,
            Long operatorUserId, boolean operatorAdmin);

    SysUserShopScopePreviewVo savePreviewedUserShops(Long userId, List<Long> shopDeptIds,
            String scopeVersion, String operName, Long operatorUserId, boolean operatorAdmin);

    List<SysDept> selectAuthorizedShopTree(Long userId, boolean admin);

    List<SysDept> selectAllShopTree();

    void checkUserShopScope(Long userId, Long deptId, boolean admin);

    /**
     * @deprecated prefer {@link #saveUserShops(Long, Long[], String, Long, boolean)} so normal operators cannot
     *             overwrite hidden out-of-scope bindings.
     */
    @Deprecated
    int saveUserShops(Long userId, Long[] shopDeptIds, String operName);

    int saveUserShops(Long userId, Long[] shopDeptIds, String operName, Long operatorUserId, boolean operatorAdmin);
}

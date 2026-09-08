package com.erp.inventory.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface InvDeptScopeMapper
{
    List<Long> selectSubDeptIds(@Param("deptId") Long deptId);

    List<Long> selectRelatedDeptIds(@Param("deptId") Long deptId);

    List<Long> selectActiveRelatedDeptIdsForReplenishment(
            @Param("deptId") Long deptId);

    List<Long> selectAncestorDeptIds(@Param("deptId") Long deptId);

    Long selectRawBusinessRootDeptId(@Param("deptId") Long deptId);

    List<Long> selectUserStoreScopeDeptIds(@Param("userId") Long userId);

    List<Long> selectAllStoreDeptIds();

    List<Long> selectUserAuthorizedInventoryDeptIds(@Param("userId") Long userId);

    List<Long> selectAllActiveInventoryDeptIds();

    int countDeptInScope(@Param("scopeDeptId") Long scopeDeptId, @Param("targetDeptId") Long targetDeptId);

    int countUserShopScope(@Param("userId") Long userId, @Param("deptId") Long deptId);

    String selectDeptNameById(@Param("deptId") Long deptId);

    String selectDeptTypeById(@Param("deptId") Long deptId);
}

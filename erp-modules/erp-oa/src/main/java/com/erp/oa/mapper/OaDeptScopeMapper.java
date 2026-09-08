package com.erp.oa.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.oa.domain.vo.OaLegalEntityCandidate;
import com.erp.system.api.domain.SysLegalEntity;

public interface OaDeptScopeMapper
{
    List<Long> selectSubDeptIds(@Param("deptId") Long deptId);

    List<Long> selectUserShopDeptIds(@Param("userId") Long userId);

    List<Long> selectUserAuthorizedOaDeptIds(@Param("userId") Long userId);

    List<Long> selectAllActiveOaDeptIds();

    String selectDeptName(@Param("deptId") Long deptId);

    OaLegalEntityCandidate selectLegalEntityCandidate(@Param("deptId") Long deptId);

    List<SysLegalEntity> selectActiveLegalEntities();

    SysLegalEntity selectActiveLegalEntityById(@Param("legalEntityId") Long legalEntityId);

    int countDeptInScope(@Param("scopeDeptId") Long scopeDeptId, @Param("targetDeptId") Long targetDeptId);

    int countUserShopScope(@Param("userId") Long userId, @Param("deptId") Long deptId);

    int countActiveStoreDept(@Param("deptId") Long deptId);
}

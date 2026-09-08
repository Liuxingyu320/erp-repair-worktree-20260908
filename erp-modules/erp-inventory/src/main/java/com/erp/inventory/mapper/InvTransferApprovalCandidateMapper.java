package com.erp.inventory.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

public interface InvTransferApprovalCandidateMapper
{
    Integer selectActivePostSortByCode(@Param("postCode") String postCode);

    int countActiveTargetStore(@Param("targetDeptId") Long targetDeptId);

    List<Map<String, Object>> selectDirectStoreUsersByPostCode(@Param("targetDeptId") Long targetDeptId,
            @Param("postCode") String postCode);

    List<Map<String, Object>> selectCoveredHigherPostUsers(@Param("targetDeptId") Long targetDeptId,
            @Param("managerPostSort") Integer managerPostSort,
            @Param("executiveBoundarySort") Integer executiveBoundarySort,
            @Param("excludedPostCodes") List<String> excludedPostCodes);

    List<Map<String, Object>> selectCoveredUsersByPostCode(@Param("targetDeptId") Long targetDeptId,
            @Param("postCode") String postCode);

    List<Map<String, Object>> selectUsersByDeptAndPostCode(@Param("deptId") Long deptId,
            @Param("postCode") String postCode);

    List<String> selectPostCodesByUserId(@Param("userId") Long userId);

    List<Map<String, Object>> selectSafeDisplayNamesByUserIds(@Param("userIds") List<Long> userIds);
}

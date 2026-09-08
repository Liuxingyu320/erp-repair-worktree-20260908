package com.erp.approval.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.approval.candidate.ApprovalDirectoryDept;
import com.erp.approval.candidate.ApprovalDirectoryUser;

/** Read-only view of active users, posts, permissions and responsibility scopes. */
public interface ApprovalCandidateDirectoryMapper
{
    List<ApprovalDirectoryUser> selectResponsibilityPostUsers(
            @Param("anchorDeptId") Long anchorDeptId,
            @Param("postCode") String postCode,
            @Param("permission") String permission,
            @Param("directOnly") boolean directOnly);

    List<ApprovalDirectoryUser> selectOrgLeaderUsers(
            @Param("anchorDeptId") Long anchorDeptId,
            @Param("deptType") String deptType,
            @Param("permission") String permission);

    List<ApprovalDirectoryUser> selectPermissionHolders(
            @Param("anchorDeptId") Long anchorDeptId,
            @Param("permission") String permission);

    List<ApprovalDirectoryUser> selectActiveFixedUsers(
            @Param("userIds") List<Long> userIds,
            @Param("permission") String permission);

    ApprovalDirectoryUser selectActiveUserById(
            @Param("userId") Long userId,
            @Param("permission") String permission);

    List<ApprovalDirectoryDept> selectActiveAnchors(
            @Param("scopeType") String scopeType,
            @Param("scopeId") Long scopeId,
            @Param("businessCode") String businessCode);

    ApprovalDirectoryDept selectActiveDeptById(Long deptId);

    Integer selectActivePostSortByCode(String postCode);

    List<ApprovalDirectoryUser> selectDirectStoreUsersByPostCode(
            @Param("anchorDeptId") Long anchorDeptId,
            @Param("postCode") String postCode,
            @Param("permission") String permission);

    List<ApprovalDirectoryUser> selectCoveredHigherPostUsers(
            @Param("anchorDeptId") Long anchorDeptId,
            @Param("managerPostSort") Integer managerPostSort,
            @Param("executiveBoundarySort") Integer executiveBoundarySort,
            @Param("excludedPostCodes") List<String> excludedPostCodes,
            @Param("permission") String permission);

    List<ApprovalDirectoryUser> selectCoveredUsersByPostCode(
            @Param("anchorDeptId") Long anchorDeptId,
            @Param("postCode") String postCode,
            @Param("permission") String permission);

    List<String> selectActivePostCodesByUserId(Long userId);
}

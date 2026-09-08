package com.erp.approval.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.approval.domain.ApprovalRule;
import com.erp.approval.domain.ApprovalRuleCondition;
import com.erp.approval.domain.ApprovalRuleVersion;
import com.erp.approval.domain.ApprovalVersionNode;

public interface ApprovalDefinitionMapper
{
    List<ApprovalRule> selectRuleList(ApprovalRule filter);

    ApprovalRule selectRuleById(Long ruleId);

    ApprovalRuleVersion selectRuleVersionById(Long versionId);

    ApprovalRuleVersion selectRuleVersion(@Param("ruleId") Long ruleId,
            @Param("versionNo") Integer versionNo);

    List<ApprovalRuleVersion> selectVersionsByRuleId(Long ruleId);

    ApprovalRuleVersion selectDraftVersionByRuleId(Long ruleId);

    List<ApprovalRule> selectActiveRulesForMatch(
            @Param("templateId") Long templateId,
            @Param("anchorDeptId") Long anchorDeptId,
            @Param("businessSubtype") String businessSubtype);

    List<ApprovalRule> selectActiveRulesByTemplateId(Long templateId);

    List<ApprovalRuleCondition> selectConditionsByVersionId(Long versionId);

    List<ApprovalVersionNode> selectNodesByVersionId(Long versionId);

    ApprovalVersionNode selectNodeById(Long nodeId);

    int insertRule(ApprovalRule rule);

    int updateRuleWithLock(@Param("rule") ApprovalRule rule,
            @Param("expectedVersion") Long expectedVersion);

    int insertRuleVersion(ApprovalRuleVersion version);

    int updateDraftVersionWithLock(
            @Param("version") ApprovalRuleVersion version,
            @Param("expectedVersion") Long expectedVersion);

    int publishDraftVersionWithLock(
            @Param("version") ApprovalRuleVersion version,
            @Param("expectedVersion") Long expectedVersion);

    int retirePublishedVersions(@Param("ruleId") Long ruleId,
            @Param("exceptVersionId") Long exceptVersionId,
            @Param("updateBy") String updateBy);

    int insertCondition(ApprovalRuleCondition condition);

    int insertNode(ApprovalVersionNode node);

    int deleteConditionsByVersionId(Long versionId);

    int deleteNodesByVersionId(Long versionId);
}

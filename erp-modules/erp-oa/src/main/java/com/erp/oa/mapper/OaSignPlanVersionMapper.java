package com.erp.oa.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.oa.domain.OaSignPlan;
import com.erp.oa.domain.OaSignPlanTemplate;
import com.erp.oa.domain.OaSignPlanVersion;
import com.erp.oa.domain.OaSignPlanVersionTemplate;
import com.erp.oa.domain.OaSignTemplate;

public interface OaSignPlanVersionMapper
{
    OaSignPlan lockPlanById(Long planId);

    List<OaSignPlanVersion> lockPublishedVersionsByPlanId(Long planId);

    int enableForNewMatching(Long versionId);

    List<OaSignPlanTemplate> lockPlanTemplateBindings(Long planId);

    List<OaSignTemplate> lockTemplatesByIds(@Param("templateIds") List<Long> templateIds);

    OaSignPlanVersion selectByPlanIdAndVersionHash(@Param("planId") Long planId,
            @Param("versionHash") String versionHash);

    Integer selectNextVersionNo(Long planId);

    int insertPlanVersion(OaSignPlanVersion version);

    int batchInsertPlanVersionTemplates(List<OaSignPlanVersionTemplate> templates);

    OaSignPlanVersion selectPlanVersionById(Long versionId);

    List<OaSignPlanVersion> selectPlanVersionsByIds(
            @Param("versionIds") List<Long> versionIds);

    List<OaSignPlanVersion> selectPublishedMatchingCandidates(
            @Param("scenario") String scenario,
            @Param("shopDeptId") Long shopDeptId,
            @Param("legalEntityId") Long legalEntityId);

    OaSignPlanVersion lockPlanVersionById(Long versionId);

    List<OaSignPlanVersionTemplate> selectTemplatesByVersionId(Long versionId);

    List<OaSignPlanVersionTemplate> selectTemplatesByVersionIds(
            @Param("versionIds") List<Long> versionIds);

    int disableForNewMatching(Long versionId);

    int disableOtherPublishedMatchingVersions(@Param("planId") Long planId,
            @Param("keepVersionId") Long keepVersionId);
}

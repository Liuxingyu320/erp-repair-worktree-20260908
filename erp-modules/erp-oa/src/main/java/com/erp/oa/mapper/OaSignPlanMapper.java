package com.erp.oa.mapper;

import java.util.List;
import com.erp.oa.domain.OaSignPlan;
import com.erp.oa.domain.OaSignPlanTemplate;
import com.erp.oa.domain.OaSignTemplate;

public interface OaSignPlanMapper
{
    int insertOaSignPlan(OaSignPlan plan);

    int updateOaSignPlan(OaSignPlan plan);

    OaSignPlan selectOaSignPlanById(Long planId);

    List<OaSignPlan> selectOaSignPlanList(OaSignPlan plan);

    int deletePlanTemplatesByPlanId(Long planId);

    int batchInsertPlanTemplates(List<OaSignPlanTemplate> templates);

    List<OaSignPlanTemplate> selectPlanTemplatesByPlanId(Long planId);

    List<OaSignTemplate> selectActiveTemplatesByPlanId(Long planId);
}

package com.erp.oa.service;

import java.util.List;
import com.erp.oa.domain.OaSignPlan;

public interface IOaSignPlanService
{
    List<OaSignPlan> selectPlanList(OaSignPlan plan, Long selectedShopDeptId);

    OaSignPlan getPlanDetail(Long planId, Long selectedShopDeptId);

    OaSignPlan savePlan(OaSignPlan plan, Long selectedShopDeptId);

    OaSignPlan updatePlanStatus(Long planId, String status, Long selectedShopDeptId);
}

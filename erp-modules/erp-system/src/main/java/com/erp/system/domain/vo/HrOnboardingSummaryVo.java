package com.erp.system.domain.vo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class HrOnboardingSummaryVo implements Serializable
{
    private static final long serialVersionUID = 1L;
    private Integer todayArrivalCount = 0;
    private Integer pendingConfirmCount = 0;
    private Integer accountConfigurationRiskCount = 0;
    private List<HrOnboardingListVo> todayTasks = new ArrayList<>();
    public Integer getTodayArrivalCount() { return todayArrivalCount; } public void setTodayArrivalCount(Integer v) { todayArrivalCount=v; }
    public Integer getPendingConfirmCount() { return pendingConfirmCount; } public void setPendingConfirmCount(Integer v) { pendingConfirmCount=v; }
    public Integer getAccountConfigurationRiskCount() { return accountConfigurationRiskCount; } public void setAccountConfigurationRiskCount(Integer v) { accountConfigurationRiskCount=v; }
    public List<HrOnboardingListVo> getTodayTasks() { return todayTasks; } public void setTodayTasks(List<HrOnboardingListVo> v) { todayTasks=v; }
}

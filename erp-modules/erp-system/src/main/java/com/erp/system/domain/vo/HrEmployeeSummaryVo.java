package com.erp.system.domain.vo;

/** Server-side aggregation over every data-scoped employee row. */
public class HrEmployeeSummaryVo
{
    private long totalEmployeeCount;
    private long completeEmployeeCount;
    private long incompleteEmployeeCount;
    private long accountConfigurationRiskCount;
    private int averageProfileCompletionPercent;
    private long requiredCompleteEmployeeCount;
    private long requiredIncompleteEmployeeCount;
    private int averageRequiredCompletionPercent;
    private int averageCoveragePercent;
    public long getTotalEmployeeCount(){return totalEmployeeCount;} public void setTotalEmployeeCount(long v){totalEmployeeCount=v;}
    public long getCompleteEmployeeCount(){return completeEmployeeCount;} public void setCompleteEmployeeCount(long v){completeEmployeeCount=v;}
    public long getIncompleteEmployeeCount(){return incompleteEmployeeCount;} public void setIncompleteEmployeeCount(long v){incompleteEmployeeCount=v;}
    public long getAccountConfigurationRiskCount(){return accountConfigurationRiskCount;} public void setAccountConfigurationRiskCount(long v){accountConfigurationRiskCount=v;}
    public int getAverageProfileCompletionPercent(){return averageProfileCompletionPercent;} public void setAverageProfileCompletionPercent(int v){averageProfileCompletionPercent=v;}
    public long getRequiredCompleteEmployeeCount(){return requiredCompleteEmployeeCount;}
    public void setRequiredCompleteEmployeeCount(long v){requiredCompleteEmployeeCount=v;}
    public long getRequiredIncompleteEmployeeCount(){return requiredIncompleteEmployeeCount;}
    public void setRequiredIncompleteEmployeeCount(long v){requiredIncompleteEmployeeCount=v;}
    public int getAverageRequiredCompletionPercent(){return averageRequiredCompletionPercent;}
    public void setAverageRequiredCompletionPercent(int v){averageRequiredCompletionPercent=v;}
    public int getAverageCoveragePercent(){return averageCoveragePercent;}
    public void setAverageCoveragePercent(int v){averageCoveragePercent=v;}
}

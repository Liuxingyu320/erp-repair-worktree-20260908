package com.erp.system.domain.vo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 当前员工登录资料完整度。
 */
public class SysProfileCompletionVo implements Serializable
{
    private static final long serialVersionUID = 1L;

    private boolean completionRequired;

    private List<SysProfileCompletionFieldVo> missingFields = new ArrayList<>();

    private SysProfileCompletionRequest values = new SysProfileCompletionRequest();

    private Map<String, String> completedDisplayValues = new LinkedHashMap<>();

    private Map<String, String> readonlySummary = new LinkedHashMap<>();

    public boolean isCompletionRequired()
    {
        return completionRequired;
    }

    public void setCompletionRequired(boolean completionRequired)
    {
        this.completionRequired = completionRequired;
    }

    public List<SysProfileCompletionFieldVo> getMissingFields()
    {
        return missingFields;
    }

    public void setMissingFields(List<SysProfileCompletionFieldVo> missingFields)
    {
        this.missingFields = missingFields == null ? new ArrayList<>() : missingFields;
    }

    public SysProfileCompletionRequest getValues()
    {
        return values;
    }

    public void setValues(SysProfileCompletionRequest values)
    {
        this.values = values == null ? new SysProfileCompletionRequest() : values;
    }

    public Map<String, String> getCompletedDisplayValues()
    {
        return completedDisplayValues;
    }

    public void setCompletedDisplayValues(Map<String, String> completedDisplayValues)
    {
        this.completedDisplayValues = completedDisplayValues == null ? new LinkedHashMap<>() : completedDisplayValues;
    }

    public Map<String, String> getReadonlySummary()
    {
        return readonlySummary;
    }

    public void setReadonlySummary(Map<String, String> readonlySummary)
    {
        this.readonlySummary = readonlySummary == null ? new LinkedHashMap<>() : readonlySummary;
    }
}

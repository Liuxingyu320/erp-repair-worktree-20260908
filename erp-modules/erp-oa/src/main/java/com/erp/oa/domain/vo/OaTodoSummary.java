package com.erp.oa.domain.vo;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OaTodoSummary
{
    private Long total = 0L;
    private Map<String, Long> typeCounts = new LinkedHashMap<>();
    private Map<String, Long> categoryCounts = new LinkedHashMap<>();
    private Map<String, Long> priorityCounts = new LinkedHashMap<>();
    private List<OaTodoItem> recentItems = new ArrayList<>();
    private Date generatedTime;

    public Long getTotal() { return total; }
    public void setTotal(Long total) { this.total = total; }
    public Map<String, Long> getTypeCounts() { return typeCounts; }
    public void setTypeCounts(Map<String, Long> typeCounts) { this.typeCounts = typeCounts; }
    public Map<String, Long> getCategoryCounts() { return categoryCounts; }
    public void setCategoryCounts(Map<String, Long> categoryCounts) { this.categoryCounts = categoryCounts; }
    public Map<String, Long> getPriorityCounts() { return priorityCounts; }
    public void setPriorityCounts(Map<String, Long> priorityCounts) { this.priorityCounts = priorityCounts; }
    public List<OaTodoItem> getRecentItems() { return recentItems; }
    public void setRecentItems(List<OaTodoItem> recentItems)
    {
        this.recentItems = recentItems == null ? new ArrayList<>() : recentItems;
    }
    public Date getGeneratedTime() { return generatedTime; }
    public void setGeneratedTime(Date generatedTime) { this.generatedTime = generatedTime; }
}

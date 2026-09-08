package com.erp.common.core.domain.todo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregated counters and recent items for unified todos.
 */
public class TodoSummary implements Serializable
{
    private static final long serialVersionUID = 1L;

    private String source;
    private long total;
    private long approval;
    private long execution;
    private long returned;
    private long risk;
    private long personal;
    private long urgent;
    private long important;
    private long normal;
    private Map<String, Long> typeCounts = new LinkedHashMap<>();
    private List<TodoItem> recent = new ArrayList<>();

    public String getSource()
    {
        return source;
    }

    public void setSource(String source)
    {
        this.source = source;
    }

    public long getTotal()
    {
        return total;
    }

    public void setTotal(long total)
    {
        this.total = total;
    }

    public long getApproval()
    {
        return approval;
    }

    public void setApproval(long approval)
    {
        this.approval = approval;
    }

    public long getExecution()
    {
        return execution;
    }

    public void setExecution(long execution)
    {
        this.execution = execution;
    }

    public long getReturned()
    {
        return returned;
    }

    public void setReturned(long returned)
    {
        this.returned = returned;
    }

    public long getRisk()
    {
        return risk;
    }

    public void setRisk(long risk)
    {
        this.risk = risk;
    }

    public long getPersonal()
    {
        return personal;
    }

    public void setPersonal(long personal)
    {
        this.personal = personal;
    }

    public long getUrgent()
    {
        return urgent;
    }

    public void setUrgent(long urgent)
    {
        this.urgent = urgent;
    }

    public long getImportant()
    {
        return important;
    }

    public void setImportant(long important)
    {
        this.important = important;
    }

    public long getNormal()
    {
        return normal;
    }

    public void setNormal(long normal)
    {
        this.normal = normal;
    }

    public Map<String, Long> getTypeCounts()
    {
        return typeCounts;
    }

    public void setTypeCounts(Map<String, Long> typeCounts)
    {
        this.typeCounts = typeCounts == null ? new LinkedHashMap<>() : new LinkedHashMap<>(typeCounts);
    }

    public List<TodoItem> getRecent()
    {
        return recent;
    }

    public void setRecent(List<TodoItem> recent)
    {
        this.recent = recent == null ? new ArrayList<>() : new ArrayList<>(recent);
    }

    public void recalculateTotal()
    {
        total = approval + execution + returned + risk + personal;
    }
}

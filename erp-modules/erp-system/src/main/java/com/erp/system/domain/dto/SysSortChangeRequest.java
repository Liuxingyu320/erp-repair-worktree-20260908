package com.erp.system.domain.dto;

import java.util.ArrayList;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonAnySetter;

/** Validated optimistic sort request shared by department and menu ordering. */
public class SysSortChangeRequest
{
    public static final int MAX_CHANGES = 500;

    @NotEmpty(message = "排序变更不能为空")
    @Size(max = MAX_CHANGES, message = "单次排序最多修改500项")
    @Valid
    private List<SysSortChangeItem> changes = new ArrayList<SysSortChangeItem>();

    public List<SysSortChangeItem> getChanges()
    {
        return changes;
    }

    public void setChanges(List<SysSortChangeItem> changes)
    {
        this.changes = changes == null ? new ArrayList<SysSortChangeItem>() : changes;
    }

    @JsonAnySetter
    public void rejectUnknownField(String field, Object ignored)
    {
        throw new IllegalArgumentException("排序接口不接受字段: " + field);
    }
}

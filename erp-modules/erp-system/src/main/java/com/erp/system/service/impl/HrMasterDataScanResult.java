package com.erp.system.service.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.erp.system.domain.vo.HrMasterDataIssueVo;

/**
 * Internal readiness result. Employee IDs are retained only for exact aggregation and are never exposed by the API.
 */
final class HrMasterDataScanResult
{
    private final Map<String,HrMasterDataIssueVo> issueByKey=new LinkedHashMap<>();
    private final Map<String,Set<Long>> affectedEmployeeIdsByKey=new LinkedHashMap<>();

    void add(HrMasterDataIssueVo issue,Collection<Long> affectedEmployeeIds)
    {
        if(issue==null)return;
        String key=key(issue);
        HrMasterDataIssueVo canonical=issueByKey.putIfAbsent(key,issue);
        if(canonical==null)canonical=issue;
        Set<Long> affected=affectedEmployeeIdsByKey.computeIfAbsent(key,ignored->new LinkedHashSet<>());
        if(affectedEmployeeIds!=null)
            affectedEmployeeIds.stream().filter(value->value!=null&&value>0).forEach(affected::add);
        canonical.setAffectedEmployeeCount(affected.size());
    }

    List<HrMasterDataIssueVo> issues()
    {
        return new ArrayList<>(issueByKey.values());
    }

    Set<Long> affectedEmployeeIds(Collection<HrMasterDataIssueVo> issues)
    {
        if(issues==null||issues.isEmpty())return Collections.emptySet();
        Set<Long> result=new LinkedHashSet<>();
        for(HrMasterDataIssueVo issue:issues)
            result.addAll(affectedEmployeeIdsByKey.getOrDefault(key(issue),Collections.emptySet()));
        return Collections.unmodifiableSet(result);
    }

    private String key(HrMasterDataIssueVo issue)
    {
        return String.valueOf(issue.getIssueCode())+'|'+String.valueOf(issue.getResourceType())+'|'
                +String.valueOf(issue.getResourceId());
    }
}

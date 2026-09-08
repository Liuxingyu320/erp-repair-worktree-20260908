package com.erp.system.domain.vo;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** Summary of one read-only master-data readiness scan. */
public class HrMasterDataSummaryVo
{
    private long totalIssueCount;
    private long blockingIssueCount;
    private long importantIssueCount;
    private long normalIssueCount;
    private long affectedEmployeeCount;
    private long affectedOccurrenceCount;
    private boolean readinessEnabled;
    private boolean observationMode;
    private boolean enforcementEnabled;
    private LocalDateTime generatedAt;
    private Map<String, Long> issueCodeCounts = new LinkedHashMap<>();

    public long getTotalIssueCount(){return totalIssueCount;} public void setTotalIssueCount(long v){totalIssueCount=v;}
    public long getBlockingIssueCount(){return blockingIssueCount;} public void setBlockingIssueCount(long v){blockingIssueCount=v;}
    public long getImportantIssueCount(){return importantIssueCount;} public void setImportantIssueCount(long v){importantIssueCount=v;}
    public long getNormalIssueCount(){return normalIssueCount;} public void setNormalIssueCount(long v){normalIssueCount=v;}
    public long getAffectedEmployeeCount(){return affectedEmployeeCount;} public void setAffectedEmployeeCount(long v){affectedEmployeeCount=v;}
    public long getAffectedOccurrenceCount(){return affectedOccurrenceCount;} public void setAffectedOccurrenceCount(long v){affectedOccurrenceCount=v;}
    public boolean isReadinessEnabled(){return readinessEnabled;} public void setReadinessEnabled(boolean v){readinessEnabled=v;}
    public boolean isObservationMode(){return observationMode;} public void setObservationMode(boolean v){observationMode=v;}
    public boolean isEnforcementEnabled(){return enforcementEnabled;} public void setEnforcementEnabled(boolean v){enforcementEnabled=v;}
    public LocalDateTime getGeneratedAt(){return generatedAt;} public void setGeneratedAt(LocalDateTime v){generatedAt=v;}
    public Map<String,Long> getIssueCodeCounts(){return issueCodeCounts;}
    public void setIssueCodeCounts(Map<String,Long> v){issueCodeCounts=v==null?new LinkedHashMap<>():new LinkedHashMap<>(v);}
}

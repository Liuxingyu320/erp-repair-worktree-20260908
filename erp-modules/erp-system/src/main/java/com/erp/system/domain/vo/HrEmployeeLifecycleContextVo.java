package com.erp.system.domain.vo;

import java.time.LocalDate;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnore;

/** Minimal archive action context; never serializes payroll or action snapshots. */
public class HrEmployeeLifecycleContextVo
{
    public String userId;
    public String employeeName;
    public String employeeNo;
    public String employeeStatus;
    public String postId;
    public String postName;
    public String accountStatus;
    @JsonIgnore public Long scopeDeptId;
    public String departmentName;
    public LocalDate entryDate;
    public LocalDate probationStartDate;
    public LocalDate contractStartDate;
    public LocalDate contractEndDate;
    public String contractTypeCode;
    public String contractTermCode;
    public String legalEntityId;
    public String legalEntityCode;
    public String legalEntityName;
    public Integer renewalCount;
    public String scenario;
    public LocalDate businessDate;
    public String cycleKey;
    public boolean eligible;
    public String blockedReason;
    public List<History> history = List.of();

    public static class History
    {
        public String actionId;
        public String actionType;
        public LocalDate effectiveDate;
        public String businessStatus;
        public String operatorName;
        public String deliveryStatus;
        public String taskId;
    }
}

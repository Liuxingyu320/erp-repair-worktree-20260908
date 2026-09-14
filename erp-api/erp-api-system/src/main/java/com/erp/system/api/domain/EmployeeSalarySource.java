package com.erp.system.api.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Immutable salary evidence. The current pointer is stored separately. */
public class EmployeeSalarySource extends EmployeeSalaryValues
{
    public String sourceId;
    public Long employeeId;
    public String previousSourceId;
    public String sourceType;
    public String businessId;
    public Long batchId;
    public Long rowId;
    public Long rowVersion;
    public String fileSha256;
    public String commandId;
    public String requestHash;
    public String beforeHash;
    public LocalDate effectiveDate;
    public Long operatorUserId;
    public String operatorName;
    public String reason;
    public boolean verified;
    public LocalDateTime confirmedAt;
}

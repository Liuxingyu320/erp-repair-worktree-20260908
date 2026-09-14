package com.erp.system.domain.dto;

import java.time.LocalDate;
import java.util.List;

/** Only identities, concurrency tokens and HR confirmation; amounts remain server-owned. */
public record HrOnboardSalaryArchiveRequest(Long batchId, List<Row> rows, String requestId,
        LocalDate effectiveDate, String reason, Boolean confirmed)
{
    public HrOnboardSalaryArchiveRequest(Long batchId, List<Row> rows)
    { this(batchId, rows, null, null, null, false); }
    public record Row(Long rowId, Long version, String expectedSourceId, String expectedProfileHash)
    {
        public Row(Long rowId, Long version) { this(rowId, version, null, null); }
    }
}

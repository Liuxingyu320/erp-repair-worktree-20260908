package com.erp.system.mapper;

import org.apache.ibatis.annotations.Param;
import com.erp.system.domain.SysSignProfileSupplementAudit;

/** Persistence boundary for reviewed-signing-fact idempotency and audit. */
public interface SysSignProfileSupplementAuditMapper
{
    int insertIfAbsent(SysSignProfileSupplementAudit audit);

    SysSignProfileSupplementAudit selectByRequestIdForUpdate(@Param("requestId") String requestId);

    Long lockActiveEmployee(@Param("employeeId") Long employeeId);

    int markCompleted(@Param("auditId") Long auditId,
            @Param("beforeHash") String beforeHash,
            @Param("afterHash") String afterHash);
}

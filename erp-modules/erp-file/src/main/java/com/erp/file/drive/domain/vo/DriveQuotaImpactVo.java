package com.erp.file.drive.domain.vo;

import java.util.List;

/** 保存前展示的受影响对象、分配变化、超额结果和快照哈希。 */
public record DriveQuotaImpactVo(String impactHash, String changeType,
        int affectedCount, long beforeAllocatedBytes, long afterAllocatedBytes,
        long deltaBytes, int overQuotaCount, long overQuotaBytes,
        List<String> warnings)
{
    public DriveQuotaImpactVo
    {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}

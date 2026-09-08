package com.erp.file.drive.domain.vo;

import java.util.List;

/**
 * 管理中心使用的容量池、逻辑分配和实际占用汇总。
 */
public record DriveCapacityOverviewVo(Long physicalCapacityBytes, int reservePercent,
        Long allocatableCapacityBytes, long publicPoolBytes, long personalPoolBytes,
        long organizationPoolBytes, long publicAllocatedBytes,
        long personalAllocatedBytes, long organizationAllocatedBytes,
        long actualUsedBytes, long pendingUploadBytes, long capacityAccountedBytes,
        int staleReservationCount, int cleanupFailedReservationCount,
        String enforcementMode, int version, List<String> warnings)
{
    public DriveCapacityOverviewVo
    {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public DriveCapacityOverviewVo(Long physicalCapacityBytes, int reservePercent,
            Long allocatableCapacityBytes, long publicPoolBytes, long personalPoolBytes,
            long organizationPoolBytes, long publicAllocatedBytes,
            long personalAllocatedBytes, long organizationAllocatedBytes,
            long actualUsedBytes, String enforcementMode, int version,
            List<String> warnings)
    {
        this(physicalCapacityBytes, reservePercent, allocatableCapacityBytes,
                publicPoolBytes, personalPoolBytes, organizationPoolBytes,
                publicAllocatedBytes, personalAllocatedBytes, organizationAllocatedBytes,
                actualUsedBytes, 0L, actualUsedBytes, 0, 0,
                enforcementMode, version, warnings);
    }
}

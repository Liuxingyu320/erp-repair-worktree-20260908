package com.erp.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.system.domain.maintenance.LegacyCredentialCandidate;
import com.erp.system.domain.maintenance.LegacyCredentialMigrationAudit;

public interface LegacyCredentialMaintenanceMapper
{
    String selectCurrentDatabase();

    String selectConfigValueForMaintenance(String configKey);

    List<LegacyCredentialCandidate> selectCandidates();

    List<LegacyCredentialCandidate> selectCandidatesForUpdate(@Param("userIds") List<Long> userIds);

    int insertAudit(LegacyCredentialMigrationAudit audit);

    LegacyCredentialMigrationAudit selectPreviewAudit(String batchId);

    int updatePreviewStatus(@Param("batchId") String batchId,
            @Param("expectedStatus") String expectedStatus, @Param("status") String status);

    int resetCredentialAndDisable(@Param("userId") Long userId,
            @Param("password") String encodedPassword,
            @Param("expiresAt") java.util.Date expiresAt,
            @Param("operatorUserId") Long operatorUserId);
}

package com.erp.system.service.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.system.api.domain.SysUser;
import com.erp.system.domain.maintenance.LegacyCredentialCandidate;
import java.util.List;

class LegacyCredentialAuditorTest
{
    @Test
    void classifiesSharedCredentialWithoutExposingAccountOrHash()
    {
        LegacyCredentialCandidate candidate = candidate(12L, "0", null,
                SecurityUtils.encryptPassword("maintenance-test-value"));
        LegacyCredentialAuditResult result = new LegacyCredentialAuditor().audit(
                List.of(candidate), "maintenance-test-value");

        assertEquals(1, result.getSharedMatches());
        assertEquals(1, result.getActiveSharedMatches());
        assertEquals("SHARED_ACTIVE", result.getRows().get(0).getClassification());
        assertFalse(candidate.toString().contains("maintenance-test-value"));
        assertFalse(candidate.toString().contains(candidate.getPasswordHash()));
    }

    @Test
    void nullUpdateDateWithUniqueCredentialIsChangeRequiredCandidate()
    {
        LegacyCredentialCandidate candidate = candidate(13L, "1", null,
                SecurityUtils.encryptPassword("different-value"));
        LegacyCredentialAuditResult result = new LegacyCredentialAuditor().audit(
                List.of(candidate), "maintenance-test-value");

        assertEquals(0, result.getSharedMatches());
        assertEquals(1, result.getChangeRequired());
        assertEquals("CHANGE_REQUIRED_CANDIDATE", result.getRows().get(0).getClassification());
    }

    private static LegacyCredentialCandidate candidate(Long id, String status,
            java.util.Date pwdUpdateDate, String passwordHash)
    {
        LegacyCredentialCandidate candidate = new LegacyCredentialCandidate();
        candidate.setUserId(id);
        candidate.setDeptId(3L);
        candidate.setStatus(status);
        candidate.setDelFlag("0");
        candidate.setPwdUpdateDate(pwdUpdateDate);
        candidate.setCredentialState(SysUser.CREDENTIAL_STATE_ACTIVE);
        candidate.setPasswordHash(passwordHash);
        return candidate;
    }
}


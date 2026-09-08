package com.erp.system.service.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.system.api.domain.SysUser;
import com.erp.system.domain.maintenance.LegacyCredentialCandidate;

public class LegacyCredentialAuditor
{
    public LegacyCredentialAuditResult audit(List<LegacyCredentialCandidate> candidates,
            String legacyPassword)
    {
        if (legacyPassword == null || legacyPassword.isEmpty())
        {
            throw new IllegalStateException("legacy credential configuration is unavailable");
        }
        List<LegacyCredentialCandidate> sorted = new ArrayList<>(candidates == null
                ? List.of() : candidates);
        sorted.sort(Comparator.comparing(LegacyCredentialCandidate::getUserId));
        List<LegacyCredentialAuditRow> rows = new ArrayList<>();
        StringBuilder digestMaterial = new StringBuilder();
        int shared = 0;
        int activeShared = 0;
        int changeRequired = 0;
        int temporary = 0;
        for (LegacyCredentialCandidate candidate : sorted)
        {
            String classification = classify(candidate, legacyPassword);
            if (classification.startsWith("SHARED_"))
            {
                shared++;
                if ("SHARED_ACTIVE".equals(classification)) activeShared++;
            }
            if ("CHANGE_REQUIRED_CANDIDATE".equals(classification)) changeRequired++;
            if (SysUser.CREDENTIAL_STATE_TEMPORARY.equals(candidate.getCredentialState())) temporary++;
            rows.add(new LegacyCredentialAuditRow(candidate.getUserId(), candidate.getDeptId(),
                    candidate.getStatus(), classification));
            digestMaterial.append(candidate.getUserId()).append(':')
                    .append(candidate.getDeptId()).append(':')
                    .append(candidate.getStatus()).append(':')
                    .append(classification).append('\n');
        }
        return new LegacyCredentialAuditResult(sorted.size(), shared, activeShared,
                changeRequired, temporary, sha256(digestMaterial.toString()), rows);
    }

    String classify(LegacyCredentialCandidate candidate, String legacyPassword)
    {
        if (candidate == null || candidate.getPasswordHash() == null || candidate.getPasswordHash().isEmpty())
        {
            return "NO_PASSWORD_HASH";
        }
        boolean matches;
        try
        {
            matches = SecurityUtils.matchesPassword(legacyPassword, candidate.getPasswordHash());
        }
        catch (IllegalArgumentException ex)
        {
            return "INVALID_PASSWORD_HASH";
        }
        if (matches)
        {
            return "0".equals(candidate.getStatus()) ? "SHARED_ACTIVE" : "SHARED_DISABLED";
        }
        if (candidate.getPwdUpdateDate() == null
                && SysUser.CREDENTIAL_STATE_ACTIVE.equals(candidate.getCredentialState()))
        {
            return "CHANGE_REQUIRED_CANDIDATE";
        }
        return "UNIQUE_OR_CHANGED";
    }

    private static String sha256(String value)
    {
        try
        {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException ex)
        {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}


package com.erp.system.service.support;

import java.util.Collections;
import java.util.List;

public class LegacyCredentialAuditResult
{
    private final int total;
    private final int sharedMatches;
    private final int activeSharedMatches;
    private final int changeRequired;
    private final int temporary;
    private final String digest;
    private final List<LegacyCredentialAuditRow> rows;

    public LegacyCredentialAuditResult(int total, int sharedMatches, int activeSharedMatches,
            int changeRequired, int temporary, String digest, List<LegacyCredentialAuditRow> rows)
    {
        this.total = total;
        this.sharedMatches = sharedMatches;
        this.activeSharedMatches = activeSharedMatches;
        this.changeRequired = changeRequired;
        this.temporary = temporary;
        this.digest = digest;
        this.rows = Collections.unmodifiableList(rows);
    }

    public int getTotal() { return total; }
    public int getSharedMatches() { return sharedMatches; }
    public int getActiveSharedMatches() { return activeSharedMatches; }
    public int getChangeRequired() { return changeRequired; }
    public int getTemporary() { return temporary; }
    public String getDigest() { return digest; }
    public List<LegacyCredentialAuditRow> getRows() { return rows; }
}


package com.erp.system.service.support;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "system.credential-maintenance")
public class LegacyCredentialMaintenanceProperties
{
    private boolean enabled;
    private String mode = "AUDIT";
    private String confirmation;
    private String targetDatabase;
    private List<String> approvedDatabases = new ArrayList<>();
    private String legacyConfigKey;
    private String batchId;
    private Long operatorUserId;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getConfirmation() { return confirmation; }
    public void setConfirmation(String confirmation) { this.confirmation = confirmation; }
    public String getTargetDatabase() { return targetDatabase; }
    public void setTargetDatabase(String targetDatabase) { this.targetDatabase = targetDatabase; }
    public List<String> getApprovedDatabases() { return approvedDatabases; }
    public void setApprovedDatabases(List<String> approvedDatabases) { this.approvedDatabases = approvedDatabases; }
    public String getLegacyConfigKey() { return legacyConfigKey; }
    public void setLegacyConfigKey(String legacyConfigKey) { this.legacyConfigKey = legacyConfigKey; }
    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }
    public Long getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }
}


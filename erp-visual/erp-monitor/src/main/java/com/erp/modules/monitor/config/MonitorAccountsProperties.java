package com.erp.modules.monitor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** External credentials only; deliberately has no toString containing secrets. */
@ConfigurationProperties(prefix = "erp.monitor.accounts")
public class MonitorAccountsProperties
{
    private String adminUsername;
    private String adminPassword;
    private String registrarUsername;
    private String registrarPassword;
    private boolean registrationEnabled;
    public String getAdminUsername() { return adminUsername; }
    public void setAdminUsername(String value) { adminUsername=value; }
    public String getAdminPassword() { return adminPassword; }
    public void setAdminPassword(String value) { adminPassword=value; }
    public String getRegistrarUsername() { return registrarUsername; }
    public void setRegistrarUsername(String value) { registrarUsername=value; }
    public String getRegistrarPassword() { return registrarPassword; }
    public void setRegistrarPassword(String value) { registrarPassword=value; }
    public boolean isRegistrationEnabled() { return registrationEnabled; }
    public void setRegistrationEnabled(boolean value) { registrationEnabled=value; }
}

package com.erp.system.domain.maintenance;

import java.util.Date;

/**
 * Minimal maintenance projection. The password hash is process-only and is never included in toString().
 */
public class LegacyCredentialCandidate
{
    private Long userId;
    private Long deptId;
    private String userName;
    private String passwordHash;
    private String status;
    private String delFlag;
    private Date pwdUpdateDate;
    private String credentialState;
    private Date temporaryPasswordExpiresAt;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getDeptId() { return deptId; }
    public void setDeptId(Long deptId) { this.deptId = deptId; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDelFlag() { return delFlag; }
    public void setDelFlag(String delFlag) { this.delFlag = delFlag; }
    public Date getPwdUpdateDate() { return pwdUpdateDate; }
    public void setPwdUpdateDate(Date pwdUpdateDate) { this.pwdUpdateDate = pwdUpdateDate; }
    public String getCredentialState() { return credentialState; }
    public void setCredentialState(String credentialState) { this.credentialState = credentialState; }
    public Date getTemporaryPasswordExpiresAt() { return temporaryPasswordExpiresAt; }
    public void setTemporaryPasswordExpiresAt(Date temporaryPasswordExpiresAt) { this.temporaryPasswordExpiresAt = temporaryPasswordExpiresAt; }

    @Override
    public String toString()
    {
        return "LegacyCredentialCandidate{userId=" + userId + ", deptId=" + deptId
                + ", status='" + status + "', delFlag='" + delFlag
                + "', credentialState='" + credentialState + "'}";
    }
}


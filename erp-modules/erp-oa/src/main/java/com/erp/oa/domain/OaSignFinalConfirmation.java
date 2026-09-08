package com.erp.oa.domain;

import java.util.Date;

public class OaSignFinalConfirmation
{
    private Long confirmationId;
    private Long packageId;
    private Long employeeId;
    private String finalDocumentVersion;
    private String documentRootHash;
    private String confirmationText;
    private String identityMethod;
    private String requestId;
    private String ipAddress;
    private String userAgent;
    private Date confirmedTime;

    public Long getConfirmationId() { return confirmationId; }
    public void setConfirmationId(Long confirmationId) { this.confirmationId = confirmationId; }
    public Long getPackageId() { return packageId; }
    public void setPackageId(Long packageId) { this.packageId = packageId; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public String getFinalDocumentVersion() { return finalDocumentVersion; }
    public void setFinalDocumentVersion(String finalDocumentVersion) { this.finalDocumentVersion = finalDocumentVersion; }
    public String getDocumentRootHash() { return documentRootHash; }
    public void setDocumentRootHash(String documentRootHash) { this.documentRootHash = documentRootHash; }
    public String getConfirmationText() { return confirmationText; }
    public void setConfirmationText(String confirmationText) { this.confirmationText = confirmationText; }
    public String getIdentityMethod() { return identityMethod; }
    public void setIdentityMethod(String identityMethod) { this.identityMethod = identityMethod; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public Date getConfirmedTime() { return confirmedTime; }
    public void setConfirmedTime(Date confirmedTime) { this.confirmedTime = confirmedTime; }
}

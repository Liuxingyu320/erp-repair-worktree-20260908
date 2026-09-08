package com.erp.system.domain.dto;

public class HrHealthCertificateSubmitRequest
{
    private Long certificateId;
    private Long version;

    public Long getCertificateId() { return certificateId; }
    public void setCertificateId(Long certificateId) { this.certificateId = certificateId; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}

package com.erp.oa.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/** 根据签约包冻结部门识别出的公司主体。 */
public class OaLegalEntityCandidate
{
    @JsonSerialize(using = ToStringSerializer.class)
    private Long legalEntityId;
    private String legalEntityCode;
    private String legalEntityName;
    private String unifiedSocialCreditCode;
    private String registeredAddress;
    private String legalRepresentative;
    private String contactPhone;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long sourceDeptId;
    private String sourceDeptName;

    public Long getLegalEntityId() { return legalEntityId; }
    public void setLegalEntityId(Long legalEntityId) { this.legalEntityId = legalEntityId; }
    public String getLegalEntityCode() { return legalEntityCode; }
    public void setLegalEntityCode(String legalEntityCode) { this.legalEntityCode = legalEntityCode; }
    public String getLegalEntityName() { return legalEntityName; }
    public void setLegalEntityName(String legalEntityName) { this.legalEntityName = legalEntityName; }
    public String getUnifiedSocialCreditCode() { return unifiedSocialCreditCode; }
    public void setUnifiedSocialCreditCode(String unifiedSocialCreditCode) { this.unifiedSocialCreditCode = unifiedSocialCreditCode; }
    public String getRegisteredAddress() { return registeredAddress; }
    public void setRegisteredAddress(String registeredAddress) { this.registeredAddress = registeredAddress; }
    public String getLegalRepresentative() { return legalRepresentative; }
    public void setLegalRepresentative(String legalRepresentative) { this.legalRepresentative = legalRepresentative; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
    public Long getSourceDeptId() { return sourceDeptId; }
    public void setSourceDeptId(Long sourceDeptId) { this.sourceDeptId = sourceDeptId; }
    public String getSourceDeptName() { return sourceDeptName; }
    public void setSourceDeptName(String sourceDeptName) { this.sourceDeptName = sourceDeptName; }
}

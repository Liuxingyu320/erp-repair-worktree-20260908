package com.erp.system.api.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.erp.common.core.web.domain.BaseEntity;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/** 公司法律主体主数据。 */
public class SysLegalEntity extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long legalEntityId;

    @NotBlank(message = "公司编码不能为空")
    @Size(max = 64, message = "公司编码长度不能超过64个字符")
    @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "公司编码只能包含字母、数字、点、横线和下划线")
    private String legalEntityCode;

    @NotBlank(message = "公司法定全称不能为空")
    @Size(max = 160, message = "公司法定全称长度不能超过160个字符")
    private String legalEntityName;

    @Size(max = 32, message = "统一社会信用代码长度不能超过32个字符")
    private String unifiedSocialCreditCode;

    @Size(max = 255, message = "注册地址长度不能超过255个字符")
    private String registeredAddress;

    @Size(max = 64, message = "法定代表人长度不能超过64个字符")
    private String legalRepresentative;

    @Size(max = 32, message = "联系电话长度不能超过32个字符")
    private String contactPhone;

    private String status;
    private Long version;

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
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}

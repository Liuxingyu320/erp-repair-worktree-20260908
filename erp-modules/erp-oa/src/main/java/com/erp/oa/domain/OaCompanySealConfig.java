package com.erp.oa.domain;

import jakarta.validation.constraints.NotBlank;
import java.util.Date;
import com.erp.common.core.web.domain.BaseEntity;

public class OaCompanySealConfig extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long sealId;

    private Long legalEntityId;

    private String legalEntityName;

    private String sealCode;

    @NotBlank(message = "印章名称不能为空")
    private String sealName;

    private String sealType;

    private String isDefault;

    @NotBlank(message = "印章图片不能为空")
    private String sealImageUrl;

    private String sealImageHash;

    private Date validFrom;

    private Date validTo;

    private String status;

    public Long getSealId() { return sealId; }
    public void setSealId(Long sealId) { this.sealId = sealId; }

    public Long getLegalEntityId() { return legalEntityId; }
    public void setLegalEntityId(Long legalEntityId) { this.legalEntityId = legalEntityId; }

    public String getLegalEntityName() { return legalEntityName; }
    public void setLegalEntityName(String legalEntityName) { this.legalEntityName = legalEntityName; }

    public String getSealCode() { return sealCode; }
    public void setSealCode(String sealCode) { this.sealCode = sealCode; }

    public String getSealName() { return sealName; }
    public void setSealName(String sealName) { this.sealName = sealName; }

    public String getSealType() { return sealType; }
    public void setSealType(String sealType) { this.sealType = sealType; }

    public String getIsDefault() { return isDefault; }
    public void setIsDefault(String isDefault) { this.isDefault = isDefault; }

    public String getSealImageUrl() { return sealImageUrl; }
    public void setSealImageUrl(String sealImageUrl) { this.sealImageUrl = sealImageUrl; }

    public String getSealImageHash() { return sealImageHash; }
    public void setSealImageHash(String sealImageHash) { this.sealImageHash = sealImageHash; }

    public Date getValidFrom() { return validFrom; }
    public void setValidFrom(Date validFrom) { this.validFrom = validFrom; }

    public Date getValidTo() { return validTo; }
    public void setValidTo(Date validTo) { this.validTo = validTo; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}

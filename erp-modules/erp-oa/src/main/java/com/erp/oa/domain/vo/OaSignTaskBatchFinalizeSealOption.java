package com.erp.oa.domain.vo;

import java.util.Date;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/** Safe contract-seal option; storage paths and registered hashes stay server-side. */
public class OaSignTaskBatchFinalizeSealOption
{
    @JsonSerialize(using = ToStringSerializer.class)
    private Long sealId;
    private String sealCode;
    private String sealName;
    private Boolean defaultSeal;
    private Date validFrom;
    private Date validTo;

    public Long getSealId() { return sealId; }
    public void setSealId(Long sealId) { this.sealId = sealId; }
    public String getSealCode() { return sealCode; }
    public void setSealCode(String sealCode) { this.sealCode = sealCode; }
    public String getSealName() { return sealName; }
    public void setSealName(String sealName) { this.sealName = sealName; }
    public Boolean getDefaultSeal() { return defaultSeal; }
    public void setDefaultSeal(Boolean defaultSeal) { this.defaultSeal = defaultSeal; }
    public Date getValidFrom() { return validFrom; }
    public void setValidFrom(Date validFrom) { this.validFrom = validFrom; }
    public Date getValidTo() { return validTo; }
    public void setValidTo(Date validTo) { this.validTo = validTo; }
}

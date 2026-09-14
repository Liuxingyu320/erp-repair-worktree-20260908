package com.erp.oa.domain.vo;
import java.math.BigDecimal;
import java.util.List;
import com.erp.oa.domain.OaFixedAssetConfig;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
public class OaFixedAssetConfigSnapshot {
    @JsonSerialize(using=ToStringSerializer.class) private Long shopDeptId;
    private String requestId;
    private String version;
    private String currentVersion;
    private BigDecimal annualRepairRatio;
    private List<OaFixedAssetConfig> rows;
    public Long getShopDeptId(){return shopDeptId;} public void setShopDeptId(Long v){shopDeptId=v;}
    public String getRequestId(){return requestId;} public void setRequestId(String v){requestId=v;}
    public String getVersion(){return version;} public void setVersion(String v){version=v;}
    public String getCurrentVersion(){return currentVersion;} public void setCurrentVersion(String v){currentVersion=v;}
    public BigDecimal getAnnualRepairRatio(){return annualRepairRatio;} public void setAnnualRepairRatio(BigDecimal v){annualRepairRatio=v;}
    public List<OaFixedAssetConfig> getRows(){return rows;} public void setRows(List<OaFixedAssetConfig> v){rows=v;}
}

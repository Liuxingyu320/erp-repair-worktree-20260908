package com.erp.oa.domain.dto;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.erp.oa.domain.OaFixedAssetConfig;
public class OaFixedAssetConfigBatchRequest {
    @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,64}") private String requestId;
    @NotNull @Positive private Long shopDeptId;
    @NotNull @Min(0) private Long expectedVersion;
    @NotNull @DecimalMin("0") @DecimalMax("100") private BigDecimal annualRepairRatio;
    @NotNull @Size(max=5000) @Valid private List<OaFixedAssetConfig> rows = new ArrayList<>();
    public String getRequestId(){return requestId;} public void setRequestId(String v){requestId=v;}
    public Long getShopDeptId(){return shopDeptId;} public void setShopDeptId(Long v){shopDeptId=v;}
    public Long getExpectedVersion(){return expectedVersion;} public void setExpectedVersion(Long v){expectedVersion=v;}
    public BigDecimal getAnnualRepairRatio(){return annualRepairRatio;} public void setAnnualRepairRatio(BigDecimal v){annualRepairRatio=v;}
    public List<OaFixedAssetConfig> getRows(){return rows;} public void setRows(List<OaFixedAssetConfig> v){rows=v;}
}

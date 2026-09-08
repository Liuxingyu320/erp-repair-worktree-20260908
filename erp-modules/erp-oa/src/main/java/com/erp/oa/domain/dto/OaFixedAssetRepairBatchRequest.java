package com.erp.oa.domain.dto;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public class OaFixedAssetRepairBatchRequest
{
    @NotNull(message = "店铺不能为空")
    private Long shopDeptId;

    @Valid
    @NotEmpty(message = "请选择固定资产明细")
    private List<OaFixedAssetRepairBatchItem> items;

    @NotBlank(message = "故障说明不能为空")
    private String faultDescription;

    private String imageUrls;
    private String remark;

    public Long getShopDeptId()
    {
        return shopDeptId;
    }

    public void setShopDeptId(Long shopDeptId)
    {
        this.shopDeptId = shopDeptId;
    }

    public List<OaFixedAssetRepairBatchItem> getItems()
    {
        return items;
    }

    public void setItems(List<OaFixedAssetRepairBatchItem> items)
    {
        this.items = items;
    }

    public String getFaultDescription()
    {
        return faultDescription;
    }

    public void setFaultDescription(String faultDescription)
    {
        this.faultDescription = faultDescription;
    }

    public String getImageUrls()
    {
        return imageUrls;
    }

    public void setImageUrls(String imageUrls)
    {
        this.imageUrls = imageUrls;
    }

    public String getRemark()
    {
        return remark;
    }

    public void setRemark(String remark)
    {
        this.remark = remark;
    }
}

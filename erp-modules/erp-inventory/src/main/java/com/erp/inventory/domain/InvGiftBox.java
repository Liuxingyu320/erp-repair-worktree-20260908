package com.erp.inventory.domain;

import java.math.BigDecimal;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.erp.common.core.utils.file.ImageUrlList;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.erp.common.core.annotation.Excel;
import com.erp.common.core.annotation.Excel.Type;
import com.erp.common.core.web.domain.BaseEntity;

public class InvGiftBox extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    @JsonIgnore
    private final java.util.Set<String> jsonProvidedFields = new java.util.HashSet<>();

    /** Only the JSON request subtype records presence; normalization and Excel setters do not. */
    protected final void recordJsonProvidedField(String field) { jsonProvidedFields.add(field); }

    @JsonIgnore
    public final boolean wasJsonFieldProvided(String field) { return jsonProvidedFields.contains(field); }


    private Long giftId;

    @Excel(name = "礼盒编码", type = Type.EXPORT)
    private String giftCode;

    private Long categoryId;

    @Excel(name = "上线分类")
    private String categoryName;

    @Excel(name = "产品名称")
    private String giftName;

    @Excel(name = "等级")
    private String grade;

    @Excel(name = "规格")
    private String spec;

    @Excel(name = "产品描述")
    private String productDescription;

    @Excel(name = "补货单位")
    private String replenishmentUnit;

    @Excel(name = "参考成本价（元/盒）")
    private BigDecimal costPrice;

    @Excel(name = "指导售价1")
    private BigDecimal guidePrice1;

    @Excel(name = "指导售价2")
    private BigDecimal guidePrice2;

    @Excel(name = "供应商")
    private String supplierName;

    @Excel(name = "礼盒图片")
    private String imageUrl;

    @Excel(name = "图片列表（JSON数组，[]清空）")
    private String imageUrlsText;


    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    private String status;

    private String delFlag;
    private String keyword;

    public Long getGiftId() { return giftId; }
    public void setGiftId(Long giftId) { this.giftId = giftId; }
    public String getGiftCode() { return giftCode; }
    public void setGiftCode(String giftCode) { this.giftCode = giftCode; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    @NotBlank(message = "礼盒名称不能为空")
    @Size(min = 0, max = 128, message = "礼盒名称不能超过128个字符")
    public String getGiftName() { return giftName; }
    public void setGiftName(String giftName) { this.giftName = giftName; }
    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }
    public String getSpec() { return spec; }
    public void setSpec(String spec) { this.spec = spec; }
    public String getProductDescription() { return productDescription; }
    public void setProductDescription(String productDescription) { this.productDescription = productDescription; }
    public String getReplenishmentUnit() { return replenishmentUnit; }
    public void setReplenishmentUnit(String replenishmentUnit) { this.replenishmentUnit = replenishmentUnit; }
    public BigDecimal getCostPrice() { return costPrice; }
    public void setCostPrice(BigDecimal costPrice) { this.costPrice = costPrice; }
    public BigDecimal getGuidePrice1() { return guidePrice1; }
    public void setGuidePrice1(BigDecimal guidePrice1) { this.guidePrice1 = guidePrice1; }
    public BigDecimal getGuidePrice2() { return guidePrice2; }
    public void setGuidePrice2(BigDecimal guidePrice2) { this.guidePrice2 = guidePrice2; }
    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }
    public String getImageUrl() { return ImageUrlList.cover(imageUrlsText, imageUrl); }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    @JsonIgnore
    public String getRawImageUrl() { return imageUrl; }
    @JsonIgnore
    public String getImageUrlsText() { return imageUrlsText; }
    public void setImageUrlsText(String value) { this.imageUrlsText = value; }
    public List<String> getImageUrls() { return ImageUrlList.read(imageUrlsText, imageUrl); }
    public void setImageUrls(List<String> value) { this.imageUrlsText = ImageUrlList.validateAndWrite(value); }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDelFlag() { return delFlag; }
    public void setDelFlag(String delFlag) { this.delFlag = delFlag; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
}

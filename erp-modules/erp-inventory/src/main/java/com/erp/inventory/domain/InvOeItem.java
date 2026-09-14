package com.erp.inventory.domain;

import java.math.BigDecimal;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.erp.common.core.utils.file.ImageUrlList;
import java.util.Date;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.erp.common.core.annotation.Excel;
import com.erp.common.core.annotation.Excel.Type;
import com.erp.common.core.web.domain.BaseEntity;

public class InvOeItem extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    @JsonIgnore
    private final java.util.Set<String> jsonProvidedFields = new java.util.HashSet<>();

    /** Only the JSON request subtype records presence; normalization and Excel setters do not. */
    protected final void recordJsonProvidedField(String field) { jsonProvidedFields.add(field); }

    @JsonIgnore
    public final boolean wasJsonFieldProvided(String field) { return jsonProvidedFields.contains(field); }


    private Long oeItemId;

    @Excel(name = "产品编号", type = Type.EXPORT)
    private String oeItemCode;

    private Long categoryId;

    @Excel(name = "上线分类")
    private String categoryName;

    @Excel(name = "产品类别名称")
    private String oeTypeName;

    @Excel(name = "物品名称")
    private String oeItemName;

    @Excel(name = "产品描述")
    private String itemDescription;

    @Excel(name = "订货单位")
    private String orderUnit;

    @Excel(name = "成本价")
    private BigDecimal costPrice;

    @Excel(name = "供应商名称")
    private String supplierName;

    @Excel(name = "手机")
    private String supplierPhone;

    @Excel(name = "图片")
    private String imageUrl;

    @Excel(name = "图片列表（JSON数组，[]清空）")
    private String imageUrlsText;


    @Excel(name = "同款购买链接")
    private String purchaseReferenceUrl;

    @Excel(name = "购买说明")
    private String purchaseReferenceNote;

    private String purchaseReferenceUpdatedBy;
    private Date purchaseReferenceUpdatedTime;

    /** 列表查询和展示状态：COMPLETE、INCOMPLETE、NOT_FIXED_ASSET。 */
    private String purchaseReferenceStatus;

    /**
     * 明确区分“请求未维护同款资料”和“用户主动修改/清空同款资料”。
     * 该字段不落库，主要用于全量编辑表单和Excel更新兼容。
     */
    private Boolean purchaseReferenceTouched;

    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    private String status;

    private String delFlag;
    private String keyword;

    public Long getOeItemId() { return oeItemId; }
    public void setOeItemId(Long oeItemId) { this.oeItemId = oeItemId; }
    public String getOeItemCode() { return oeItemCode; }
    public void setOeItemCode(String oeItemCode) { this.oeItemCode = oeItemCode; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public String getOeTypeName() { return oeTypeName; }
    public void setOeTypeName(String oeTypeName) { this.oeTypeName = oeTypeName; }

    @NotBlank(message = "物品名称不能为空")
    @Size(min = 0, max = 128, message = "物品名称不能超过128个字符")
    public String getOeItemName() { return oeItemName; }
    public void setOeItemName(String oeItemName) { this.oeItemName = oeItemName; }
    public String getItemDescription() { return itemDescription; }
    public void setItemDescription(String itemDescription) { this.itemDescription = itemDescription; }
    public String getOrderUnit() { return orderUnit; }
    public void setOrderUnit(String orderUnit) { this.orderUnit = orderUnit; }
    public BigDecimal getCostPrice() { return costPrice; }
    public void setCostPrice(BigDecimal costPrice) { this.costPrice = costPrice; }
    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }
    public String getSupplierPhone() { return supplierPhone; }
    public void setSupplierPhone(String supplierPhone) { this.supplierPhone = supplierPhone; }
    public String getImageUrl() { return ImageUrlList.cover(imageUrlsText, imageUrl); }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    @JsonIgnore
    public String getRawImageUrl() { return imageUrl; }
    @JsonIgnore
    public String getImageUrlsText() { return imageUrlsText; }
    public void setImageUrlsText(String value) { this.imageUrlsText = value; }
    public List<String> getImageUrls() { return ImageUrlList.read(imageUrlsText, imageUrl); }
    public void setImageUrls(List<String> value) { this.imageUrlsText = ImageUrlList.validateAndWrite(value); }

    @Size(max = 1000, message = "同款购买链接不能超过1000个字符")
    public String getPurchaseReferenceUrl() { return purchaseReferenceUrl; }
    public void setPurchaseReferenceUrl(String purchaseReferenceUrl) { this.purchaseReferenceUrl = purchaseReferenceUrl; }
    @Size(max = 500, message = "购买说明不能超过500个字符")
    public String getPurchaseReferenceNote() { return purchaseReferenceNote; }
    public void setPurchaseReferenceNote(String purchaseReferenceNote) { this.purchaseReferenceNote = purchaseReferenceNote; }
    public String getPurchaseReferenceUpdatedBy() { return purchaseReferenceUpdatedBy; }
    public void setPurchaseReferenceUpdatedBy(String purchaseReferenceUpdatedBy) { this.purchaseReferenceUpdatedBy = purchaseReferenceUpdatedBy; }
    public Date getPurchaseReferenceUpdatedTime() { return purchaseReferenceUpdatedTime; }
    public void setPurchaseReferenceUpdatedTime(Date purchaseReferenceUpdatedTime) { this.purchaseReferenceUpdatedTime = purchaseReferenceUpdatedTime; }
    public String getPurchaseReferenceStatus() { return purchaseReferenceStatus; }
    public void setPurchaseReferenceStatus(String purchaseReferenceStatus) { this.purchaseReferenceStatus = purchaseReferenceStatus; }
    public Boolean getPurchaseReferenceTouched() { return purchaseReferenceTouched; }
    public void setPurchaseReferenceTouched(Boolean purchaseReferenceTouched) { this.purchaseReferenceTouched = purchaseReferenceTouched; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDelFlag() { return delFlag; }
    public void setDelFlag(String delFlag) { this.delFlag = delFlag; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
}

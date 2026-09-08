package com.erp.inventory.domain.vo;

/**
 * Requested line content reconstructed from an immutable revision snapshot.
 */
public class InvTransferRevisionDetailVo
{
    private Long detailId;
    private String itemType;
    private Long itemId;
    private String itemCode;
    private String itemName;
    private Long productId;
    private String productName;
    private String productCode;
    private String quantity;
    private String costPrice;
    private String amount;
    private String unit;
    private String spec;
    private String grade;
    private Integer sortOrder;
    private String goodsCondition;
    private String conditionNote;
    private Long lotId;
    private Long sourceLocationId;

    public Long getDetailId() { return detailId; }
    public void setDetailId(Long value) { detailId = value; }
    public String getItemType() { return itemType; }
    public void setItemType(String value) { itemType = value; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long value) { itemId = value; }
    public String getItemCode() { return itemCode; }
    public void setItemCode(String value) { itemCode = value; }
    public String getItemName() { return itemName; }
    public void setItemName(String value) { itemName = value; }
    public Long getProductId() { return productId; }
    public void setProductId(Long value) { productId = value; }
    public String getProductName() { return productName; }
    public void setProductName(String value) { productName = value; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String value) { productCode = value; }
    public String getQuantity() { return quantity; }
    public void setQuantity(String value) { quantity = value; }
    public String getCostPrice() { return costPrice; }
    public void setCostPrice(String value) { costPrice = value; }
    public String getAmount() { return amount; }
    public void setAmount(String value) { amount = value; }
    public String getUnit() { return unit; }
    public void setUnit(String value) { unit = value; }
    public String getSpec() { return spec; }
    public void setSpec(String value) { spec = value; }
    public String getGrade() { return grade; }
    public void setGrade(String value) { grade = value; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer value) { sortOrder = value; }
    public String getGoodsCondition() { return goodsCondition; }
    public void setGoodsCondition(String value) { goodsCondition = value; }
    public String getConditionNote() { return conditionNote; }
    public void setConditionNote(String value) { conditionNote = value; }
    public Long getLotId() { return lotId; }
    public void setLotId(Long value) { lotId = value; }
    public Long getSourceLocationId() { return sourceLocationId; }
    public void setSourceLocationId(Long value) { sourceLocationId = value; }
}

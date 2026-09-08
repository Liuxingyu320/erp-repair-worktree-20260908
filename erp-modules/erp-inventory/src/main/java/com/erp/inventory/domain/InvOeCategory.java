package com.erp.inventory.domain;

import java.util.ArrayList;
import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.erp.common.core.web.domain.BaseEntity;

public class InvOeCategory extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long categoryId;
    private Long parentId;
    private String ancestors;
    private String categoryName;
    private String categoryCode;
    private Integer orderNum;
    private String status;
    private String delFlag;
    private List<InvOeCategory> children = new ArrayList<>();

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getAncestors() { return ancestors; }
    public void setAncestors(String ancestors) { this.ancestors = ancestors; }

    @NotBlank(message = "OE分类名称不能为空")
    @Size(min = 0, max = 64, message = "OE分类名称不能超过64个字符")
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public String getCategoryCode() { return categoryCode; }
    public void setCategoryCode(String categoryCode) { this.categoryCode = categoryCode; }
    public Integer getOrderNum() { return orderNum; }
    public void setOrderNum(Integer orderNum) { this.orderNum = orderNum; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDelFlag() { return delFlag; }
    public void setDelFlag(String delFlag) { this.delFlag = delFlag; }
    public List<InvOeCategory> getChildren() { return children; }
    public void setChildren(List<InvOeCategory> children) { this.children = children; }
}

package com.erp.inventory.domain.dto;

import com.erp.inventory.domain.InvOeItem;
import com.fasterxml.jackson.annotation.JsonSetter;

/** JSON-only optional-field presence. Import and Java normalization keep legacy partial-update semantics. */
public class InvOeEditRequest extends InvOeItem
{
    private static final long serialVersionUID = 1L;
    @JsonSetter("oeTypeName")
    public void readOeTypeName(String value)
    {
        super.setOeTypeName(value);
        recordJsonProvidedField("oeTypeName");
    }
    @JsonSetter("itemDescription")
    public void readItemDescription(String value)
    {
        super.setItemDescription(value);
        recordJsonProvidedField("itemDescription");
    }
    @JsonSetter("orderUnit")
    public void readOrderUnit(String value)
    {
        super.setOrderUnit(value);
        recordJsonProvidedField("orderUnit");
    }
    @JsonSetter("supplierName")
    public void readSupplierName(String value)
    {
        super.setSupplierName(value);
        recordJsonProvidedField("supplierName");
    }
    @JsonSetter("remark")
    public void readRemark(String value)
    {
        super.setRemark(value);
        recordJsonProvidedField("remark");
    }
}

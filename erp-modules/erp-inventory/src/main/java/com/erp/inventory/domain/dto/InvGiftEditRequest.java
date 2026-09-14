package com.erp.inventory.domain.dto;

import com.erp.inventory.domain.InvGiftBox;
import com.fasterxml.jackson.annotation.JsonSetter;

/** JSON-only optional-field presence. Import and Java normalization keep legacy partial-update semantics. */
public class InvGiftEditRequest extends InvGiftBox
{
    private static final long serialVersionUID = 1L;
    @JsonSetter("grade")
    public void readGrade(String value)
    {
        super.setGrade(value);
        recordJsonProvidedField("grade");
    }
    @JsonSetter("spec")
    public void readSpec(String value)
    {
        super.setSpec(value);
        recordJsonProvidedField("spec");
    }
    @JsonSetter("productDescription")
    public void readProductDescription(String value)
    {
        super.setProductDescription(value);
        recordJsonProvidedField("productDescription");
    }
    @JsonSetter("replenishmentUnit")
    public void readReplenishmentUnit(String value)
    {
        super.setReplenishmentUnit(value);
        recordJsonProvidedField("replenishmentUnit");
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

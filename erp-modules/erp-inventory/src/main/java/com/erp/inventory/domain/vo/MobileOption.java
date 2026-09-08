package com.erp.inventory.domain.vo;

import java.io.Serializable;

public class MobileOption implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long value;
    private String label;
    private String code;
    private String meta;
    private String status;
    private String type;

    public Long getValue()
    {
        return value;
    }

    public void setValue(Long value)
    {
        this.value = value;
    }

    public String getLabel()
    {
        return label;
    }

    public void setLabel(String label)
    {
        this.label = label;
    }

    public String getCode()
    {
        return code;
    }

    public void setCode(String code)
    {
        this.code = code;
    }

    public String getMeta()
    {
        return meta;
    }

    public void setMeta(String meta)
    {
        this.meta = meta;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getType()
    {
        return type;
    }

    public void setType(String type)
    {
        this.type = type;
    }
}

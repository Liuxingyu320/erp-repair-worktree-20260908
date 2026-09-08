package com.erp.system.domain.vo;

import java.io.Serializable;

/**
 * 登录资料缺失字段。
 */
public class SysProfileCompletionFieldVo implements Serializable
{
    private static final long serialVersionUID = 1L;

    private String key;

    private String label;

    public SysProfileCompletionFieldVo()
    {
    }

    public SysProfileCompletionFieldVo(String key, String label)
    {
        this.key = key;
        this.label = label;
    }

    public String getKey()
    {
        return key;
    }

    public void setKey(String key)
    {
        this.key = key;
    }

    public String getLabel()
    {
        return label;
    }

    public void setLabel(String label)
    {
        this.label = label;
    }
}

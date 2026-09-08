package com.erp.system.domain.vo;

public class HrSensitiveRevealVo
{
    private String fieldKey;
    private Object value;
    public HrSensitiveRevealVo() { }
    public HrSensitiveRevealVo(String fieldKey, Object value) { this.fieldKey=fieldKey; this.value=value; }
    public String getFieldKey(){return fieldKey;} public void setFieldKey(String v){fieldKey=v;}
    public Object getValue(){return value;} public void setValue(Object v){value=v;}
}

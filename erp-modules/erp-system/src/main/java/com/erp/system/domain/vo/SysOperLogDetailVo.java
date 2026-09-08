package com.erp.system.domain.vo;

/**
 * 操作日志脱敏详情。
 */
public class SysOperLogDetailVo extends SysOperLogListVo
{
    private static final long serialVersionUID = 1L;

    private String method;
    private Integer operatorType;
    private String deptName;
    private String operUrl;
    private String requestSummary;
    private String resultSummary;
    private String errorSummary;

    public String getMethod()
    {
        return method;
    }

    public void setMethod(String method)
    {
        this.method = method;
    }

    public Integer getOperatorType()
    {
        return operatorType;
    }

    public void setOperatorType(Integer operatorType)
    {
        this.operatorType = operatorType;
    }

    public String getDeptName()
    {
        return deptName;
    }

    public void setDeptName(String deptName)
    {
        this.deptName = deptName;
    }

    public String getOperUrl()
    {
        return operUrl;
    }

    public void setOperUrl(String operUrl)
    {
        this.operUrl = operUrl;
    }

    public String getRequestSummary()
    {
        return requestSummary;
    }

    public void setRequestSummary(String requestSummary)
    {
        this.requestSummary = requestSummary;
    }

    public String getResultSummary()
    {
        return resultSummary;
    }

    public void setResultSummary(String resultSummary)
    {
        this.resultSummary = resultSummary;
    }

    public String getErrorSummary()
    {
        return errorSummary;
    }

    public void setErrorSummary(String errorSummary)
    {
        this.errorSummary = errorSummary;
    }
}

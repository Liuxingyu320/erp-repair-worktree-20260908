package com.erp.system.domain.vo;

import java.io.Serializable;
import java.util.Date;
import com.erp.common.core.annotation.Excel;
import com.erp.common.core.annotation.Excel.ColumnType;

/**
 * 操作日志安全导出模型，不包含请求、响应和异常正文。
 */
public class SysOperLogExportVo implements Serializable
{
    private static final long serialVersionUID = 1L;

    @Excel(name = "操作日志编号", cellType = ColumnType.NUMERIC)
    private Long operId;

    @Excel(name = "操作模块")
    private String title;

    @Excel(name = "业务类型", readConverterExp = "0=其它,1=新增,2=修改,3=删除,4=授权,5=导出,6=导入,7=强退,8=生成代码,9=清空数据")
    private Integer businessType;

    @Excel(name = "HTTP方法")
    private String requestMethod;

    @Excel(name = "操作人员")
    private String operName;

    @Excel(name = "操作IP")
    private String operIp;

    @Excel(name = "操作状态", readConverterExp = "0=正常,1=异常")
    private Integer status;

    @Excel(name = "操作时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date operTime;

    @Excel(name = "耗时", suffix = "毫秒")
    private Long costTime;

    public Long getOperId()
    {
        return operId;
    }

    public void setOperId(Long operId)
    {
        this.operId = operId;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public Integer getBusinessType()
    {
        return businessType;
    }

    public void setBusinessType(Integer businessType)
    {
        this.businessType = businessType;
    }

    public String getRequestMethod()
    {
        return requestMethod;
    }

    public void setRequestMethod(String requestMethod)
    {
        this.requestMethod = requestMethod;
    }

    public String getOperName()
    {
        return operName;
    }

    public void setOperName(String operName)
    {
        this.operName = operName;
    }

    public String getOperIp()
    {
        return operIp;
    }

    public void setOperIp(String operIp)
    {
        this.operIp = operIp;
    }

    public Integer getStatus()
    {
        return status;
    }

    public void setStatus(Integer status)
    {
        this.status = status;
    }

    public Date getOperTime()
    {
        return operTime;
    }

    public void setOperTime(Date operTime)
    {
        this.operTime = operTime;
    }

    public Long getCostTime()
    {
        return costTime;
    }

    public void setCostTime(Long costTime)
    {
        this.costTime = costTime;
    }
}

package com.erp.inventory.domain.dto;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import com.erp.common.core.exception.ServiceException;

/** Allowlisted return-source filters; organization always comes from the authenticated context. */
public class InvSalesReturnSourceQuery
{
    private String keyword;
    private String orderNo;
    private String customerName;
    private String startDate;
    private String endDate;
    private Integer pageNum = 1;
    private Integer pageSize = 20;
    public void validate()
    {
        keyword = text(keyword,100); orderNo = text(orderNo,64); customerName = text(customerName,128);
        LocalDate start = date(startDate), end = date(endDate);
        if (start != null && end != null && start.isAfter(end)) throw new ServiceException("开始日期不能晚于结束日期");
        if (pageNum == null || pageNum < 1 || pageSize == null || pageSize < 1 || pageSize > 100)
            throw new ServiceException("分页参数无效，每页应为1至100条");
    }
    private static String text(String value,int max)
    {
        if (value == null) return null;
        String result=value.trim(); if (result.length()>max) throw new ServiceException("查询条件过长"); return result;
    }
    private static LocalDate date(String value)
    {
        if (value == null || value.isEmpty()) return null;
        if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new ServiceException("日期必须为YYYY-MM-DD");
        try { LocalDate parsed=LocalDate.parse(value); if(parsed.getYear()<1000) throw new ServiceException("日期超出可查询范围");return parsed; }
        catch(DateTimeParseException e) { throw new ServiceException("日期无效"); }
    }
    public String getKeyword(){return keyword;} public void setKeyword(String v){keyword=v;}
    public String getOrderNo(){return orderNo;} public void setOrderNo(String v){orderNo=v;}
    public String getCustomerName(){return customerName;} public void setCustomerName(String v){customerName=v;}
    public String getStartDate(){return startDate;} public void setStartDate(String v){startDate=v;}
    public String getEndDate(){return endDate;} public void setEndDate(String v){endDate=v;}
    public Integer getPageNum(){return pageNum;} public void setPageNum(Integer v){pageNum=v;}
    public Integer getPageSize(){return pageSize;} public void setPageSize(Integer v){pageSize=v;}
}

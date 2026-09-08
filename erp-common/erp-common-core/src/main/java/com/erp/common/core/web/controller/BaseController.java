package com.erp.common.core.web.controller;

import java.beans.PropertyEditorSupport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.erp.common.core.constant.HttpStatus;
import com.erp.common.core.utils.DateUtils;
import com.erp.common.core.utils.PageUtils;
import com.erp.common.core.utils.ServletUtils;
import com.erp.common.core.utils.StringUtils;
import com.erp.common.core.utils.sql.SqlUtil;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.core.web.page.PageDomain;
import com.erp.common.core.web.page.TableDataInfo;
import com.erp.common.core.web.page.TableSupport;

/**
 * web层通用数据处理
 * 
 * @author erp
 */
public class BaseController
{
    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    /**
     * 将前台传递过来的日期格式的字符串，自动转化为Date类型
     */
    @InitBinder
    public void initBinder(WebDataBinder binder)
    {
        // Date 类型转换
        binder.registerCustomEditor(Date.class, new PropertyEditorSupport()
        {
            @Override
            public void setAsText(String text)
            {
                setValue(DateUtils.parseDate(text));
            }
        });
    }

    /**
     * 设置请求分页数据
     */
    protected void startPage()
    {
        PageUtils.startPage();
    }

    /**
     * 设置请求排序数据
     */
    protected void startOrderBy()
    {
        PageDomain pageDomain = TableSupport.buildPageRequest();
        if (StringUtils.isNotEmpty(pageDomain.getOrderBy()))
        {
            String orderBy = SqlUtil.escapeOrderBySql(pageDomain.getOrderBy());
            PageHelper.orderBy(orderBy);
        }
    }

    /**
     * 清理分页的线程变量
     */
    protected void clearPage()
    {
        PageUtils.clearPage();
    }

    /**
     * 响应请求分页数据
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected TableDataInfo getDataTable(List<?> list)
    {
        PageDomain fallbackPageDomain = getFallbackPageDomain(list);
        List<?> rows = fallbackPageDomain == null ? list : sliceRows(list, fallbackPageDomain);
        TableDataInfo rspData = new TableDataInfo();
        rspData.setCode(HttpStatus.SUCCESS);
        rspData.setRows(rows);
        rspData.setMsg("查询成功");
        rspData.setTotal(fallbackPageDomain == null ? new PageInfo(list).getTotal() : list.size());
        return rspData;
    }

    private PageDomain getFallbackPageDomain(List<?> list)
    {
        if (list == null || list instanceof Page)
        {
            return null;
        }
        HttpServletRequest request = ServletUtils.getRequest();
        if (request == null || (request.getParameter(TableSupport.PAGE_NUM) == null && request.getParameter(TableSupport.PAGE_SIZE) == null))
        {
            return null;
        }
        PageDomain pageDomain = TableSupport.buildPageRequest();
        if (pageDomain.getPageNum() == null || pageDomain.getPageSize() == null || pageDomain.getPageNum() <= 0 || pageDomain.getPageSize() <= 0)
        {
            return null;
        }
        return pageDomain;
    }

    private List<?> sliceRows(List<?> list, PageDomain pageDomain)
    {
        int fromIndex = Math.max((pageDomain.getPageNum() - 1) * pageDomain.getPageSize(), 0);
        if (fromIndex >= list.size())
        {
            return Collections.emptyList();
        }
        int toIndex = Math.min(fromIndex + pageDomain.getPageSize(), list.size());
        return new ArrayList<>(list.subList(fromIndex, toIndex));
    }

    /**
     * 返回成功
     */
    public AjaxResult success()
    {
        return AjaxResult.success();
    }

    /**
     * 返回成功消息
     */
    public AjaxResult success(String message)
    {
        return AjaxResult.success(message);
    }

    /**
     * 返回成功消息
     */
    public AjaxResult success(Object data)
    {
        return AjaxResult.success(data);
    }

    /**
     * 返回失败消息
     */
    public AjaxResult error()
    {
        return AjaxResult.error();
    }

    /**
     * 返回失败消息
     */
    public AjaxResult error(String message)
    {
        return AjaxResult.error(message);
    }

    /**
     * 返回警告消息
     */
    public AjaxResult warn(String message)
    {
        return AjaxResult.warn(message);
    }

    /**
     * 响应返回结果
     * 
     * @param rows 影响行数
     * @return 操作结果
     */
    protected AjaxResult toAjax(int rows)
    {
        return rows > 0 ? AjaxResult.success() : AjaxResult.error();
    }

    /**
     * 响应返回结果
     * 
     * @param result 结果
     * @return 操作结果
     */
    protected AjaxResult toAjax(boolean result)
    {
        return result ? success() : error();
    }
}

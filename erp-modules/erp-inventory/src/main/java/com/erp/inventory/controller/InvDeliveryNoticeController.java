package com.erp.inventory.controller;

import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.erp.common.core.utils.poi.ExcelUtil;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.core.web.page.TableDataInfo;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessType;
import com.erp.common.security.annotation.IdempotentSubmit;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.inventory.domain.InvDeliveryNotice;
import com.erp.inventory.domain.dto.InvDeliverRequest;
import com.erp.inventory.service.IInvDeliveryNoticeService;

@RestController
@RequestMapping("/deliveryNotice")
public class InvDeliveryNoticeController extends InvBaseController
{
    @Autowired
    private IInvDeliveryNoticeService deliveryNoticeService;

    @RequiresPermissions("inv:deliveryNotice:list")
    @GetMapping("/list")
    public TableDataInfo list(InvDeliveryNotice notice, HttpServletRequest request)
    {
        startPage();
        List<InvDeliveryNotice> list = deliveryNoticeService.selectNoticeList(notice, resolveShopDeptId(request));
        return getDataTable(list);
    }

    @RequiresPermissions("inv:deliveryNotice:query")
    @GetMapping("/{noticeId}")
    public AjaxResult detail(@PathVariable("noticeId") Long noticeId, HttpServletRequest request)
    {
        return success(deliveryNoticeService.getNoticeDetail(noticeId, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:deliveryNotice:add")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "发货通知", businessType = BusinessType.INSERT)
    @PostMapping("/create/{salesOrderId}")
    public AjaxResult create(@PathVariable("salesOrderId") Long salesOrderId, HttpServletRequest request)
    {
        InvDeliveryNotice notice = deliveryNoticeService.createNotice(salesOrderId, resolveShopDeptId(request));
        return AjaxResult.success("生成发货通知成功", notice);
    }

    @RequiresPermissions("inv:deliveryNotice:deliver")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "发货通知执行发货", businessType = BusinessType.UPDATE)
    @PostMapping("/deliver/{noticeId}")
    public AjaxResult deliver(@PathVariable("noticeId") Long noticeId,
            @Validated @RequestBody InvDeliverRequest deliverRequest, HttpServletRequest request)
    {
        String message = deliveryNoticeService.deliverNotice(noticeId, deliverRequest, resolveShopDeptId(request));
        return AjaxResult.success(message);
    }

    @RequiresPermissions("inv:deliveryNotice:remove")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "发货通知取消", businessType = BusinessType.DELETE)
    @DeleteMapping("/{noticeId}")
    public AjaxResult cancel(@PathVariable("noticeId") Long noticeId, HttpServletRequest request)
    {
        deliveryNoticeService.cancelNotice(noticeId, resolveShopDeptId(request));
        return success("已取消");
    }

    @RequiresPermissions("inv:deliveryNotice:export")
    @Log(title = "发货通知", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, InvDeliveryNotice notice, HttpServletRequest request)
    {
        List<InvDeliveryNotice> list = deliveryNoticeService.selectNoticeList(notice, resolveShopDeptId(request));
        ExcelUtil<InvDeliveryNotice> util = new ExcelUtil<>(InvDeliveryNotice.class);
        util.exportExcel(response, list, "发货通知数据");
    }
}

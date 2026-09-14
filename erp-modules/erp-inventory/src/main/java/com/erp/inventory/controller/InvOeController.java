package com.erp.inventory.controller;

import java.io.ByteArrayInputStream;
import java.io.IOException;
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
import org.springframework.web.multipart.MultipartFile;
import com.erp.common.core.utils.poi.ExcelUtil;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.core.web.page.TableDataInfo;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessType;
import com.erp.common.security.annotation.IdempotentSubmit;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.inventory.domain.InvOeItem;
import com.erp.inventory.domain.dto.InvOeEditRequest;
import com.erp.inventory.service.IInvOeService;

@RestController
@RequestMapping("/oe")
public class InvOeController extends InvBaseController
{
    @Autowired
    private IInvOeService oeService;

    @RequiresPermissions("inv:oe:list")
    @GetMapping("/list")
    public TableDataInfo list(InvOeItem item)
    {
        startPage();
        List<InvOeItem> list = oeService.selectOeList(item);
        return getDataTable(list);
    }

    @RequiresPermissions("inv:oe:list")
    @GetMapping("/purchase-reference/policy")
    public AjaxResult purchaseReferencePolicy()
    {
        return success(oeService.getPurchaseReferencePolicy());
    }

    @RequiresPermissions("inv:oe:query")
    @GetMapping("/{oeItemId}")
    public AjaxResult getInfo(@PathVariable("oeItemId") Long oeItemId)
    {
        return success(oeService.selectOeById(oeItemId));
    }

    @RequiresPermissions("inv:oe:add")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "OE管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody InvOeEditRequest item, HttpServletRequest request)
    {
        return success(oeService.saveOe(item, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:oe:edit")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "OE管理", businessType = BusinessType.UPDATE)
    @PostMapping("/update")
    public AjaxResult edit(@Validated @RequestBody InvOeEditRequest item, HttpServletRequest request)
    {
        return success(oeService.saveOe(item, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:oe:remove")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "OE管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{oeItemIds}")
    public AjaxResult remove(@PathVariable Long[] oeItemIds, HttpServletRequest request)
    {
        oeService.deleteOeByIds(oeItemIds, resolveShopDeptId(request));
        return success();
    }

    @RequiresPermissions("inv:oe:export")
    @Log(title = "OE管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, InvOeItem item)
    {
        List<InvOeItem> list = oeService.selectOeList(item);
        ExcelUtil<InvOeItem> util = new ExcelUtil<>(InvOeItem.class);
        util.exportExcel(response, list, "OE资料");
    }

    @RequiresPermissions("inv:oe:import")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "OE管理", businessType = BusinessType.IMPORT)
    @PostMapping("/importData")
    public AjaxResult importData(MultipartFile file, boolean updateSupport, HttpServletRequest request) throws IOException
    {
        ExcelUtil<InvOeItem> util = new ExcelUtil<>(InvOeItem.class);
        List<InvOeItem> itemList = util.importExcel(new ByteArrayInputStream(file.getBytes()));
        String message = oeService.importOe(itemList, updateSupport, resolveShopDeptId(request));
        return success(message);
    }

    @RequiresPermissions("inv:oe:template")
    @PostMapping("/importTemplate")
    public void importTemplate(HttpServletResponse response)
    {
        ExcelUtil<InvOeItem> util = new ExcelUtil<>(InvOeItem.class);
        util.importTemplateExcel(response, "OE导入模板");
    }
}

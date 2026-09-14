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
import com.erp.inventory.domain.InvGiftBox;
import com.erp.inventory.domain.dto.InvGiftEditRequest;
import com.erp.inventory.service.IInvGiftService;

@RestController
@RequestMapping("/gift")
public class InvGiftController extends InvBaseController
{
    @Autowired
    private IInvGiftService giftService;

    @RequiresPermissions("inv:gift:list")
    @GetMapping("/list")
    public TableDataInfo list(InvGiftBox gift)
    {
        startPage();
        List<InvGiftBox> list = giftService.selectGiftList(gift);
        return getDataTable(list);
    }

    @RequiresPermissions("inv:gift:query")
    @GetMapping("/{giftId}")
    public AjaxResult getInfo(@PathVariable("giftId") Long giftId)
    {
        return success(giftService.selectGiftById(giftId));
    }

    @RequiresPermissions("inv:gift:add")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "礼盒管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody InvGiftEditRequest gift, HttpServletRequest request)
    {
        return success(giftService.saveGift(gift, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:gift:edit")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "礼盒管理", businessType = BusinessType.UPDATE)
    @PostMapping("/update")
    public AjaxResult edit(@Validated @RequestBody InvGiftEditRequest gift, HttpServletRequest request)
    {
        return success(giftService.saveGift(gift, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:gift:remove")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "礼盒管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{giftIds}")
    public AjaxResult remove(@PathVariable Long[] giftIds, HttpServletRequest request)
    {
        giftService.deleteGiftByIds(giftIds, resolveShopDeptId(request));
        return success();
    }

    @RequiresPermissions("inv:gift:export")
    @Log(title = "礼盒管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, InvGiftBox gift)
    {
        List<InvGiftBox> list = giftService.selectGiftList(gift);
        ExcelUtil<InvGiftBox> util = new ExcelUtil<>(InvGiftBox.class);
        util.exportExcel(response, list, "礼盒资料");
    }

    @RequiresPermissions("inv:gift:import")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "礼盒管理", businessType = BusinessType.IMPORT)
    @PostMapping("/importData")
    public AjaxResult importData(MultipartFile file, boolean updateSupport, HttpServletRequest request) throws IOException
    {
        ExcelUtil<InvGiftBox> util = new ExcelUtil<>(InvGiftBox.class);
        List<InvGiftBox> giftList = util.importExcel(new ByteArrayInputStream(file.getBytes()));
        String message = giftService.importGift(giftList, updateSupport, resolveShopDeptId(request));
        return success(message);
    }

    @RequiresPermissions("inv:gift:template")
    @PostMapping("/importTemplate")
    public void importTemplate(HttpServletResponse response)
    {
        ExcelUtil<InvGiftBox> util = new ExcelUtil<>(InvGiftBox.class);
        util.importTemplateExcel(response, "礼盒导入模板");
    }
}

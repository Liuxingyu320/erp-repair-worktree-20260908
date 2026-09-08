package com.erp.inventory.controller;

import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.erp.common.core.web.page.TableDataInfo;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.inventory.domain.InvDocumentStatusLog;
import com.erp.inventory.service.IInvDocumentStatusLogService;

@RestController
@RequestMapping("/audit")
public class InvDocumentStatusLogController extends InvBaseController
{
    @Autowired
    private IInvDocumentStatusLogService documentStatusLogService;

    @RequiresPermissions("inv:audit:list")
    @GetMapping("/list")
    public TableDataInfo list(InvDocumentStatusLog documentStatusLog, HttpServletRequest request)
    {
        startPage();
        List<InvDocumentStatusLog> list = documentStatusLogService.selectDocumentStatusLogList(documentStatusLog,
                resolveShopDeptId(request));
        return getDataTable(list);
    }
}

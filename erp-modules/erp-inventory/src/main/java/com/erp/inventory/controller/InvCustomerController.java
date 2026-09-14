package com.erp.inventory.controller;

import com.erp.inventory.domain.vo.InvCustomerServiceRecordQuery;
import com.erp.inventory.domain.vo.InvCustomerServiceRecordPage;

import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.text.Convert;
import com.erp.common.core.utils.poi.ExcelUtil;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.core.web.page.TableDataInfo;
import com.erp.common.core.web.page.TableSupport;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessType;
import com.erp.common.security.annotation.IdempotentSubmit;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.inventory.domain.InvCustomer;
import com.erp.inventory.domain.dto.InvCustomerServiceCardArchiveRequest;
import com.erp.inventory.domain.dto.InvCustomerServiceCardSaveRequest;
import com.erp.inventory.domain.dto.InvCustomerServiceRecordRequest;
import com.erp.inventory.domain.vo.InvCustomerServiceCardQuery;
import com.erp.inventory.domain.vo.InvCustomerServiceAuditQuery;
import com.erp.inventory.service.IInvCustomerService;
import com.erp.inventory.service.IInvCustomerServiceCardService;
import com.erp.inventory.service.BusinessFeatureGate;
import com.erp.inventory.metric.InventoryBusinessMetrics;
import com.erp.system.api.RemoteFileService;
import feign.Response;

@RestController
@RequestMapping("/customer")
public class InvCustomerController extends InvBaseController
{
    @Autowired
    private IInvCustomerService customerService;

    @Autowired
    private IInvCustomerServiceCardService customerServiceCardService;

    @Autowired
    private RemoteFileService remoteFileService;

    @Autowired
    private InventoryBusinessMetrics businessMetrics;

    @Autowired
    private BusinessFeatureGate businessFeatureGate;

    @RequiresPermissions("inv:customer:option")
    @GetMapping("/options")
    public AjaxResult options(@RequestParam(required = false) String keyword,
            HttpServletRequest request, HttpServletResponse response)
    {
        response.setHeader("Cache-Control", "no-store");
        return success(customerServiceCardService.selectOptions(keyword, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:customerCard:list")
    @GetMapping("/service-card/capabilities")
    public AjaxResult serviceCardCapabilities(HttpServletRequest request,
            HttpServletResponse response)
    {
        response.setHeader("Cache-Control", "no-store");
        return success(Map.of("writeEnabled",
                customerServiceCardService.isWriteEnabled(
                        resolveShopDeptId(request))));
    }

    @RequiresPermissions("inv:customerCard:list")
    @GetMapping("/service-card/list")
    public TableDataInfo serviceCardList(InvCustomerServiceCardQuery query,
            HttpServletRequest request, HttpServletResponse response)
    {
        response.setHeader("Cache-Control", "no-store");
        CustomerCardPage page = customerCardPage(request);
        return getDataTable(customerServiceCardService.selectList(query,
                resolveShopDeptId(request), page.pageNum(), page.pageSize()));
    }

    @RequiresPermissions("inv:customerCard:audit")
    @GetMapping("/service-card/audit")
    public TableDataInfo serviceCardAudit(InvCustomerServiceAuditQuery query,
            HttpServletRequest request, HttpServletResponse response)
    {
        response.setHeader("Cache-Control", "no-store");
        CustomerCardPage page = customerCardPage(request);
        return getDataTable(customerServiceCardService.selectAuditList(query,
                resolveShopDeptId(request), page.pageNum(), page.pageSize()));
    }

    @RequiresPermissions("inv:customerCard:audit")
    @GetMapping("/service-card/ops-summary")
    public AjaxResult serviceCardOpsSummary(HttpServletResponse response)
    {
        response.setHeader("Cache-Control", "no-store");
        return success(businessMetrics.customerOpsSummary());
    }

    @RequiresPermissions("inv:customerCard:query")
    @GetMapping("/service-card/{customerId}")
    public AjaxResult serviceCard(@PathVariable Long customerId,
            HttpServletRequest request, HttpServletResponse response)
    {
        response.setHeader("Cache-Control", "no-store");
        return success(customerServiceCardService.selectById(customerId, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:customerCard:query")
    @GetMapping("/service-card/{customerId}/records")
    public AjaxResult serviceRecords(@PathVariable Long customerId, InvCustomerServiceRecordQuery query,
            HttpServletRequest request, HttpServletResponse response)
    {
        response.setHeader("Cache-Control", "no-store");
        return success(customerServiceCardService.selectRecords(customerId, query, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:customerCard:query")
    @GetMapping("/service-card/{customerId}/photo")
    public ResponseEntity<StreamingResponseBody> serviceCardPhoto(
            @PathVariable Long customerId,
            @RequestParam(defaultValue = "preview") String mode,
            HttpServletRequest request)
    {
        Long nodeId = customerServiceCardService.resolvePhotoNode(customerId,
                resolveShopDeptId(request));
        return stream(remoteFileService.readDriveBusinessContent(nodeId, mode,
                SecurityConstants.INNER));
    }

    @RequiresPermissions("inv:customerCard:add")
    @IdempotentSubmit(timeout = 30, releaseOnSuccess = true)
    @Log(title = "客户服务卡", businessType = BusinessType.INSERT,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/service-card")
    public AjaxResult createServiceCard(@Validated @RequestBody InvCustomerServiceCardSaveRequest card,
            HttpServletRequest request)
    {
        return success(customerServiceCardService.create(card, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:customerCard:edit")
    @IdempotentSubmit(timeout = 30, releaseOnSuccess = true)
    @Log(title = "客户服务卡", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PutMapping("/service-card/{customerId}")
    public AjaxResult updateServiceCard(@PathVariable Long customerId,
            @Validated @RequestBody InvCustomerServiceCardSaveRequest card,
            HttpServletRequest request)
    {
        return success(customerServiceCardService.update(customerId, card, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:customerCard:record:add")
    @IdempotentSubmit(timeout = 30, releaseOnSuccess = true)
    @Log(title = "客户服务记录", businessType = BusinessType.INSERT,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/service-card/{customerId}/records")
    public AjaxResult addServiceRecord(@PathVariable Long customerId,
            @RequestBody InvCustomerServiceRecordRequest record,
            HttpServletRequest request)
    {
        return success(customerServiceCardService.addRecord(customerId, record, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:customerCard:archive")
    @IdempotentSubmit(timeout = 30, releaseOnSuccess = true)
    @Log(title = "客户服务卡归档", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/service-card/{customerId}/archive")
    public AjaxResult archiveServiceCard(@PathVariable Long customerId,
            @RequestBody InvCustomerServiceCardArchiveRequest archive,
            HttpServletRequest request)
    {
        return success(customerServiceCardService.archive(customerId, archive, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:customer:list")
    @GetMapping("/list")
    public TableDataInfo list(InvCustomer customer, HttpServletRequest request)
    {
        requireLegacyCustomerApi();
        recordLegacyApi("list");
        startPage();
        List<InvCustomer> list = customerService.selectCustomerList(customer, resolveShopDeptId(request));
        return getDataTable(list);
    }

    @RequiresPermissions("inv:customer:query")
    @GetMapping("/{customerId}")
    public AjaxResult getInfo(@PathVariable("customerId") Long customerId, HttpServletRequest request)
    {
        requireLegacyCustomerApi();
        recordLegacyApi("query");
        return success(customerService.selectCustomerById(customerId, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:customer:add")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "客户管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody InvCustomer customer, HttpServletRequest request)
    {
        requireLegacyCustomerApi();
        recordLegacyApi("create");
        return success(customerService.saveCustomer(customer, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:customer:edit")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "客户管理", businessType = BusinessType.UPDATE)
    @PostMapping("/update")
    public AjaxResult edit(@Validated @RequestBody InvCustomer customer, HttpServletRequest request)
    {
        requireLegacyCustomerApi();
        recordLegacyApi("update");
        return success(customerService.saveCustomer(customer, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:customer:remove")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "客户管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{customerIds}")
    public AjaxResult remove(@PathVariable Long[] customerIds, HttpServletRequest request)
    {
        requireLegacyCustomerApi();
        recordLegacyApi("delete");
        customerService.deleteCustomerByIds(customerIds, resolveShopDeptId(request));
        return success();
    }

    @RequiresPermissions("inv:customer:export")
    @Log(title = "客户管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, InvCustomer customer, HttpServletRequest request)
    {
        requireLegacyCustomerApi();
        recordLegacyApi("export");
        List<InvCustomer> list = customerService.selectCustomerList(customer, resolveShopDeptId(request));
        ExcelUtil<InvCustomer> util = new ExcelUtil<>(InvCustomer.class);
        util.exportExcel(response, list, "客户数据");
    }

    private ResponseEntity<StreamingResponseBody> stream(Response remote)
    {
        if (remote == null || remote.status() != 200 || remote.body() == null)
        {
            closeQuietly(remote);
            throw new ServiceException("客户照片暂时不可用");
        }
        HttpHeaders headers = safeHeaders(remote.headers());
        StreamingResponseBody body = output -> {
            try (InputStream input = remote.body().asInputStream())
            {
                input.transferTo(output);
            }
            finally
            {
                closeQuietly(remote);
            }
        };
        return ResponseEntity.ok().headers(headers).body(body);
    }

    private void recordLegacyApi(String operation)
    {
        if (businessMetrics != null)
        {
            businessMetrics.recordLegacyCustomerApi(operation);
        }
    }

    private void requireLegacyCustomerApi()
    {
        businessFeatureGate.requireDisabled(
                BusinessFeatureGate.CUSTOMER_SERVICE_CARD);
    }

    private CustomerCardPage customerCardPage(HttpServletRequest request)
    {
        int pageNum = Math.max(1, Convert.toInt(
                request.getParameter(TableSupport.PAGE_NUM), 1));
        int requestedPageSize = Convert.toInt(
                request.getParameter(TableSupport.PAGE_SIZE), 20);
        int pageSize = Math.min(100, Math.max(1, requestedPageSize));
        return new CustomerCardPage(pageNum, pageSize);
    }

    private record CustomerCardPage(int pageNum, int pageSize) {}

    private HttpHeaders safeHeaders(Map<String, Collection<String>> source)
    {
        HttpHeaders headers = new HttpHeaders();
        String contentType = firstHeader(source, HttpHeaders.CONTENT_TYPE);
        try
        {
            headers.setContentType(contentType == null
                    ? MediaType.APPLICATION_OCTET_STREAM
                    : MediaType.parseMediaType(contentType));
        }
        catch (IllegalArgumentException ex)
        {
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        }
        String disposition = firstHeader(source,
                HttpHeaders.CONTENT_DISPOSITION);
        if (disposition != null && !disposition.contains("\r")
                && !disposition.contains("\n"))
        {
            headers.set(HttpHeaders.CONTENT_DISPOSITION, disposition);
        }
        String length = firstHeader(source, HttpHeaders.CONTENT_LENGTH);
        try
        {
            if (length != null && Long.parseLong(length) >= 0)
            {
                headers.setContentLength(Long.parseLong(length));
            }
        }
        catch (NumberFormatException ignored)
        {
            // Unknown length is safe for chunked streaming.
        }
        headers.setCacheControl("no-store");
        headers.setPragma("no-cache");
        headers.set("X-Content-Type-Options", "nosniff");
        return headers;
    }

    private String firstHeader(Map<String, Collection<String>> headers,
            String name)
    {
        if (headers == null)
        {
            return null;
        }
        return headers.entrySet().stream()
                .filter(entry -> name.equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue).filter(value -> value != null)
                .flatMap(Collection::stream).findFirst().orElse(null);
    }

    private void closeQuietly(Response response)
    {
        if (response == null)
        {
            return;
        }
        try
        {
            response.close();
        }
        catch (RuntimeException ignored)
        {
            // The client-safe photo error remains primary.
        }
    }
}

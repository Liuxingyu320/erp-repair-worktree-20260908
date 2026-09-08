package com.erp.inventory.controller;

import java.util.ArrayList;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.erp.common.core.utils.StringUtils;
import com.erp.common.core.utils.poi.ExcelUtil;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.core.web.page.TableDataInfo;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessType;
import com.erp.common.security.annotation.IdempotentSubmit;
import com.erp.common.security.annotation.Logical;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.inventory.annotation.PersistentCommand;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.dto.InvTransferDiscrepancyResolveRequest;
import com.erp.inventory.domain.dto.InvTransferReceiptDiscrepancyAdjudicationCreateRequest;
import com.erp.inventory.domain.dto.InvTransferDraftPlanningRequest;
import com.erp.inventory.domain.dto.InvTransferShipmentReceiptCreateRequest;
import com.erp.inventory.domain.dto.InvDeliverRequest;
import com.erp.inventory.domain.dto.InvReceiveRequest;
import com.erp.inventory.domain.dto.InvTransferApprovalRequest;
import com.erp.inventory.domain.dto.InvTransferSourceConfirmRequest;
import com.erp.inventory.domain.dto.InvTransferShipmentCreateRequest;
import com.erp.inventory.service.IInvTransferApprovalService;
import com.erp.inventory.service.IInvTransferService;
import com.erp.inventory.service.impl.InvTransferCommandService;
import com.erp.inventory.service.impl.InvTransferCostVisibilityPolicy;
import com.erp.inventory.service.impl.InvTransferDraftPlanningService;
import com.erp.inventory.service.impl.InvTransferExportService;
import com.erp.inventory.service.impl.InvTransferReceiptDiscrepancyAdjudicationBasisIssueService;
import com.erp.inventory.service.impl.InvTransferReceiptDiscrepancyAdjudicationCreationService;
import com.erp.inventory.service.impl.InvTransferReceiptDiscrepancyAdjudicationPlanningService;
import com.erp.inventory.service.impl.InvTransferShipmentPlanningService;
import com.erp.inventory.service.impl.InvTransferShipmentReceiptPlanningService;

@RestController
@RequestMapping("/transfer")
public class InvTransferController extends InvBaseController
{
    private static final List<String> PROCESSING_STATUSES = List.of(
            "draft", "submitted", "approved", "reserved", "partial_delivered", "delivered",
            "partial_received", "discrepancy");
    private static final List<String> DELIVERABLE_STATUSES = List.of(
            "approved", "reserved", "partial_delivered");
    private static final List<String> RECEIVABLE_STATUSES = List.of(
            "partial_delivered", "delivered", "partial_received");
    private static final List<String> RECORD_STATUSES = List.of(
            "received", "closed", "cancelled", "rejected");
    private static final String[] BASE_EXPORT_COLUMNS = {
            "orderNo", "fromDeptName", "toDeptName", "status",
            "detailLineCount", "transferType", "submittedTime",
            "approvedTime", "deliveredTime", "receivedTime",
            "archivedTime", "closeReason" };
    private static final String[] COST_EXPORT_COLUMNS = {
            "orderNo", "fromDeptName", "toDeptName", "status",
            "detailLineCount", "totalAmount", "transferType",
            "submittedTime", "approvedTime", "deliveredTime",
            "receivedTime", "archivedTime", "closeReason" };

    @Autowired
    private IInvTransferService transferService;

    @Autowired
    private InvTransferExportService transferExportService;

    @Autowired
    private InvTransferCostVisibilityPolicy costVisibilityPolicy;

    @Autowired
    private InvTransferCommandService transferCommandService;

    @Autowired
    private InvTransferDraftPlanningService draftPlanningService;

    @Autowired
    private InvTransferShipmentPlanningService shipmentPlanningService;

    @Autowired
    private InvTransferShipmentReceiptPlanningService
            shipmentReceiptPlanningService;

    @Autowired
    private InvTransferReceiptDiscrepancyAdjudicationPlanningService
            discrepancyAdjudicationPlanningService;

    @Autowired
    private InvTransferReceiptDiscrepancyAdjudicationBasisIssueService
            discrepancyAdjudicationBasisIssueService;

    @Autowired
    private InvTransferReceiptDiscrepancyAdjudicationCreationService
            discrepancyAdjudicationCreationService;

    @RequiresPermissions("inv:transfer:list")
    @GetMapping("/list")
    public TableDataInfo list(InvTransferOrder transfer, HttpServletRequest request,
            HttpServletResponse response)
    {
        setNoStore(response);
        startPage();
        List<InvTransferOrder> list = transferService.selectTransferList(transfer, resolveShopDeptId(request));
        costVisibilityPolicy.redactOrdersIfNeeded(list);
        return getDataTable(list);
    }

    @RequiresPermissions("inv:transfer:list")
    @GetMapping("/processing")
    public TableDataInfo processing(InvTransferOrder transfer, HttpServletRequest request,
            HttpServletResponse response)
    {
        setNoStore(response);
        applyAllowedStatuses(transfer, PROCESSING_STATUSES);
        startPage();
        List<InvTransferOrder> list = transferService.selectTransferList(transfer, resolveShopDeptId(request));
        costVisibilityPolicy.redactOrdersIfNeeded(list);
        return getDataTable(list);
    }

    @RequiresPermissions("inv:transfer:list")
    @GetMapping("/ops-summary")
    public AjaxResult opsSummary(HttpServletRequest request,
            HttpServletResponse response)
    {
        setNoStore(response);
        return success(transferService.selectOpsSummary(
                resolveShopDeptId(request)));
    }

    private void setNoStore(HttpServletResponse response)
    {
        response.setHeader("Cache-Control", "no-store, max-age=0");
        response.setHeader("Pragma", "no-cache");
    }

    @RequiresPermissions("inv:transfer:records")
    @GetMapping("/records")
    public TableDataInfo records(InvTransferOrder transfer,
            HttpServletRequest request, HttpServletResponse response)
    {
        setNoStore(response);
        applyAllowedStatuses(transfer, RECORD_STATUSES);
        startPage();
        List<InvTransferOrder> list = transferService.selectTransferList(transfer, resolveShopDeptId(request));
        costVisibilityPolicy.redactOrdersIfNeeded(list);
        return getDataTable(list);
    }

    @RequiresPermissions(value = { "inv:transfer:query",
            "inv:transfer:records:query", "inv:transfer:approve",
            "inv:transfer:add", "inv:transfer:edit", "inv:transfer:submit",
            "inv:transfer:deliver", "inv:transfer:receive" },
            logical = Logical.OR)
    @GetMapping("/{transferId}")
    public AjaxResult detail(@PathVariable("transferId") Long transferId,
            HttpServletRequest request, HttpServletResponse response)
    {
        setNoStore(response);
        return success(costVisibilityPolicy.redactOrderIfNeeded(
                transferService.getTransferDetail(transferId,
                        resolveShopDeptId(request))));
    }

    @RequiresPermissions(value = { "inv:transfer:add",
            "inv:transfer:edit", "inv:transfer:submit" },
            logical = Logical.OR)
    @PostMapping("/draft-planning")
    public AjaxResult draftPlanning(
            @Validated @RequestBody
            InvTransferDraftPlanningRequest planningRequest,
            HttpServletRequest request, HttpServletResponse response)
    {
        response.setHeader("Cache-Control", "no-store");
        return success(draftPlanningService.getPlanning(planningRequest,
                resolveShopDeptId(request)));
    }

    @RequiresPermissions(value = { "inv:transfer:query",
            "inv:transfer:records:query", "inv:transfer:approve",
            "inv:transfer:add", "inv:transfer:edit", "inv:transfer:submit",
            "inv:transfer:deliver", "inv:transfer:receive" },
            logical = Logical.OR)
    @GetMapping("/{transferId}/approval-track")
    public AjaxResult approvalTrack(@PathVariable("transferId") Long transferId, HttpServletRequest request,
            HttpServletResponse response)
    {
        setNoStore(response);
        return success(transferService.getApprovalTrack(transferId, resolveShopDeptId(request)));
    }

    @RequiresPermissions(value = { "inv:transfer:query",
            "inv:transfer:records:query", "inv:transfer:approve",
            "inv:transfer:add", "inv:transfer:edit", "inv:transfer:submit" },
            logical = Logical.OR)
    @GetMapping("/{transferId}/revisions")
    public AjaxResult revisions(@PathVariable("transferId") Long transferId,
            HttpServletRequest request, HttpServletResponse response)
    {
        setNoStore(response);
        return success(costVisibilityPolicy.redactRevisionHistoryIfNeeded(
                transferService.getRevisionHistory(transferId,
                        resolveShopDeptId(request))));
    }

    @RequiresPermissions(value = { "inv:transfer:add", "inv:transfer:edit" }, logical = Logical.OR)
    @PersistentCommand
    @Log(title = "调拨管理", businessType = BusinessType.INSERT)
    @PostMapping("/save")
    public AjaxResult save(@Validated @RequestBody InvTransferOrder transfer,
            @RequestHeader(name = "X-Request-Id") String requestId,
            HttpServletRequest request)
    {
        return success(costVisibilityPolicy.redactOrderIfNeeded(
                transferCommandService.saveDraft(requestId, transfer,
                        transfer.getDetails(), resolveShopDeptId(request))));
    }

    @RequiresPermissions("inv:transfer:submit")
    @PersistentCommand
    @Log(title = "调拨提交", businessType = BusinessType.UPDATE)
    @PostMapping("/submit")
    public AjaxResult submit(@Validated @RequestBody InvTransferOrder transfer,
            @RequestHeader(name = "X-Request-Id") String requestId,
            HttpServletRequest request)
    {
        return success(costVisibilityPolicy.redactOrderIfNeeded(
                transferCommandService.submit(requestId, transfer,
                        transfer.getDetails(), resolveShopDeptId(request))));
    }

    @RequiresPermissions("inv:transfer:approve")
    @PersistentCommand
    @Log(title = "调拨审批", businessType = BusinessType.UPDATE)
    @PostMapping("/approve")
    public AjaxResult approve(@Validated @RequestBody InvTransferApprovalRequest approvalRequest,
            @RequestHeader(name = "X-Request-Id") String requestId,
            HttpServletRequest request)
    {
        transferCommandService.approve(requestId, approvalRequest,
                resolveShopDeptId(request));
        return success();
    }

    @RequiresPermissions("inv:transfer:deliver")
    @GetMapping("/{transferId}/shipment-planning")
    public AjaxResult shipmentPlanning(
            @PathVariable("transferId") Long transferId,
            HttpServletRequest request, HttpServletResponse response)
    {
        response.setHeader("Cache-Control", "no-store");
        return success(shipmentPlanningService.getPlanning(transferId,
                resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:transfer:deliver")
    @PersistentCommand
    @Log(title = "调拨出库", businessType = BusinessType.UPDATE)
    @PostMapping("/deliver/{transferId}")
    public AjaxResult deliver(@PathVariable("transferId") Long transferId,
            @RequestBody(required = false) InvDeliverRequest deliverRequest,
            @RequestHeader(name = "X-Request-Id") String requestId,
            HttpServletRequest request)
    {
        transferCommandService.deliver(requestId, transferId, deliverRequest,
                resolveShopDeptId(request));
        return success();
    }

    @RequiresPermissions("inv:transfer:deliver")
    @PersistentCommand
    @Log(title = "调拨V2明细库存发货", businessType = BusinessType.UPDATE)
    @PostMapping("/{transferId}/shipments")
    public AjaxResult createShipmentV2(
            @PathVariable("transferId") Long transferId,
            @Validated @RequestBody InvTransferShipmentCreateRequest requestBody,
            @RequestHeader(name = "X-Request-Id") String requestId,
            HttpServletRequest request)
    {
        return success(transferCommandService.createShipmentV2(requestId,
                transferId, requestBody, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:transfer:deliver")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "异店调货调出店确认", businessType = BusinessType.UPDATE)
    @PostMapping("/source-confirm/{transferId}")
    public AjaxResult confirmSource(
            @PathVariable("transferId") Long transferId,
            @Validated @RequestBody InvTransferSourceConfirmRequest confirmRequest,
            HttpServletRequest request)
    {
        return success(transferService.confirmSourceTransfer(transferId,
                confirmRequest, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:transfer:receive")
    @GetMapping("/shipments/{shipmentId}/receipt-planning")
    public AjaxResult receiptPlanning(
            @PathVariable("shipmentId") Long shipmentId,
            HttpServletRequest request, HttpServletResponse response)
    {
        response.setHeader("Cache-Control", "no-store");
        return success(shipmentReceiptPlanningService.getPlanning(
                shipmentId, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:transfer:receive")
    @PersistentCommand
    @Log(title = "调拨V2明细库存收货", businessType = BusinessType.UPDATE)
    @PostMapping("/shipments/{shipmentId}/receipts")
    public AjaxResult createReceiptV2(
            @PathVariable("shipmentId") Long shipmentId,
            @Validated @RequestBody
            InvTransferShipmentReceiptCreateRequest requestBody,
            @RequestHeader(name = "X-Request-Id") String requestId,
            HttpServletRequest request)
    {
        return success(transferCommandService.createReceiptV2(requestId,
                shipmentId, requestBody, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:transfer:receive")
    @PersistentCommand
    @Log(title = "调拨入库", businessType = BusinessType.UPDATE)
    @PostMapping("/receive/{transferId}")
    public AjaxResult receive(@PathVariable("transferId") Long transferId,
            @RequestHeader(name = "X-Request-Id") String requestId,
            HttpServletRequest request)
    {
        transferCommandService.receive(requestId, transferId,
                resolveShopDeptId(request));
        return success();
    }

    @RequiresPermissions("inv:transfer:receive")
    @PersistentCommand
    @Log(title = "调拨批次收货", businessType = BusinessType.UPDATE)
    @PostMapping("/shipment/receive/{shipmentId}")
    public AjaxResult receiveShipment(@PathVariable("shipmentId") Long shipmentId,
            @RequestBody(required = false) InvReceiveRequest receiveRequest,
            @RequestHeader(name = "X-Request-Id") String requestId,
            HttpServletRequest request)
    {
        transferCommandService.receiveShipment(requestId, shipmentId,
                receiveRequest, resolveShopDeptId(request));
        return success();
    }

    @RequiresPermissions(value = { "inv:transfer:query", "inv:transfer:discrepancy:handle" }, logical = Logical.OR)
    @GetMapping("/{transferId}/discrepancies")
    public AjaxResult discrepancies(@PathVariable("transferId") Long transferId,
            HttpServletRequest request, HttpServletResponse response)
    {
        response.setHeader("Cache-Control", "no-store");
        return success(costVisibilityPolicy.redactDiscrepanciesIfNeeded(
                transferService.getTransferDiscrepancies(transferId,
                        resolveShopDeptId(request))));
    }

    @RequiresPermissions(value = { "inv:transfer:query", "inv:transfer:discrepancy:handle" }, logical = Logical.OR)
    @GetMapping("/discrepancies/{discrepancyId}")
    public AjaxResult discrepancy(@PathVariable("discrepancyId") Long discrepancyId,
            HttpServletRequest request, HttpServletResponse response)
    {
        response.setHeader("Cache-Control", "no-store");
        return success(costVisibilityPolicy.redactDiscrepancyIfNeeded(
                transferService.getTransferDiscrepancy(discrepancyId,
                        resolveShopDeptId(request))));
    }

    @RequiresPermissions("inv:transfer:discrepancy:adjudicate")
    @GetMapping("/discrepancies/{discrepancyCaseId}/adjudication-planning")
    public AjaxResult discrepancyAdjudicationPlanning(
            @PathVariable("discrepancyCaseId") Long discrepancyCaseId,
            HttpServletRequest request, HttpServletResponse response)
    {
        response.setHeader("Cache-Control", "no-store");
        return success(discrepancyAdjudicationPlanningService.getPlanning(
                discrepancyCaseId, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:transfer:discrepancy:adjudicate")
    @Log(title = "调拨差异裁决依据签发",
            businessType = BusinessType.OTHER)
    @PostMapping("/discrepancies/{discrepancyCaseId}/adjudication-basis")
    public AjaxResult issueDiscrepancyAdjudicationBasis(
            @PathVariable("discrepancyCaseId") Long discrepancyCaseId,
            HttpServletRequest request, HttpServletResponse response)
    {
        response.setHeader("Cache-Control", "no-store");
        return success(discrepancyAdjudicationBasisIssueService.issue(
                discrepancyCaseId, resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:transfer:discrepancy:adjudicate")
    @PersistentCommand
    @Log(title = "调拨差异裁决计划提交",
            businessType = BusinessType.UPDATE)
    @PostMapping("/discrepancies/{discrepancyCaseId}/adjudications")
    public AjaxResult createDiscrepancyAdjudication(
            @PathVariable("discrepancyCaseId") Long discrepancyCaseId,
            @Validated @RequestBody
            InvTransferReceiptDiscrepancyAdjudicationCreateRequest requestBody,
            @RequestHeader(name = "X-Request-Id") String requestId,
            HttpServletRequest request, HttpServletResponse response)
    {
        response.setHeader("Cache-Control", "no-store");
        return success(discrepancyAdjudicationCreationService.create(
                requestBody.toCommand(requestId, discrepancyCaseId),
                resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:transfer:discrepancy:handle")
    @PersistentCommand
    @Log(title = "调拨差异处理", businessType = BusinessType.UPDATE)
    @PostMapping("/discrepancies/{discrepancyId}/resolve")
    public AjaxResult resolveDiscrepancy(@PathVariable("discrepancyId") Long discrepancyId,
            @RequestBody InvTransferDiscrepancyResolveRequest resolveRequest,
            @RequestHeader(name = "X-Request-Id") String requestId,
            HttpServletRequest request)
    {
        transferCommandService.resolveDiscrepancy(requestId, discrepancyId,
                resolveRequest, resolveShopDeptId(request));
        return success();
    }

    @RequiresPermissions("inv:transfer:remove")
    @PersistentCommand
    @Log(title = "调拨草稿删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/delete/{transferId}")
    public AjaxResult deleteDraft(@PathVariable("transferId") Long transferId,
            @RequestHeader(name = "X-Request-Id") String requestId,
            HttpServletRequest request)
    {
        transferCommandService.deleteDraft(requestId, transferId,
                resolveShopDeptId(request));
        return success();
    }

    @RequiresPermissions("inv:transfer:remove")
    @PersistentCommand
    @Log(title = "调拨取消", businessType = BusinessType.DELETE)
    @DeleteMapping("/{transferId}")
    public AjaxResult cancel(@PathVariable("transferId") Long transferId,
            @RequestHeader(name = "X-Request-Id") String requestId,
            HttpServletRequest request)
    {
        transferCommandService.cancel(requestId, transferId,
                resolveShopDeptId(request));
        return success();
    }

    @RequiresPermissions("inv:transfer:submit")
    @PersistentCommand
    @Log(title = "调拨撤回审批", businessType = BusinessType.UPDATE)
    @PostMapping("/{transferId}/withdraw")
    public AjaxResult withdraw(@PathVariable("transferId") Long transferId,
            @RequestHeader(name = "X-Request-Id") String requestId,
            HttpServletRequest request)
    {
        return success(transferCommandService.withdraw(requestId, transferId,
                resolveShopDeptId(request)));
    }

    @RequiresPermissions("inv:transfer:export")
    @Log(title = "调拨管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, InvTransferOrder transfer, HttpServletRequest request)
    {
        setNoStore(response);
        applyAllowedStatuses(transfer, PROCESSING_STATUSES);
        exportRows(response, transfer, request, "调拨处理中数据");
    }

    @RequiresPermissions("inv:transfer:records:export")
    @Log(title = "调拨记录", businessType = BusinessType.EXPORT)
    @PostMapping("/records/export")
    public void exportRecords(HttpServletResponse response, InvTransferOrder transfer, HttpServletRequest request)
    {
        setNoStore(response);
        applyAllowedStatuses(transfer, RECORD_STATUSES);
        exportRows(response, transfer, request, "调拨记录数据");
    }

    private void exportRows(HttpServletResponse response,
            InvTransferOrder transfer, HttpServletRequest request,
            String sheetName)
    {
        List<InvTransferOrder> list = transferExportService.selectForExport(
                transfer, resolveShopDeptId(request));
        boolean canViewCost = costVisibilityPolicy.canViewCost();
        costVisibilityPolicy.redactOrdersIfNeeded(list);
        ExcelUtil<InvTransferOrder> util = new ExcelUtil<>(
                InvTransferOrder.class);
        util.showColumn(canViewCost ? COST_EXPORT_COLUMNS
                : BASE_EXPORT_COLUMNS);
        util.exportExcel(response, list, sheetName);
    }

    private void applyAllowedStatuses(InvTransferOrder transfer, List<String> allowedStatuses)
    {
        List<String> requestedStatuses = resolveRequestedStatuses(transfer);
        if (!requestedStatuses.isEmpty())
        {
            List<String> statuses = new ArrayList<>();
            for (String status : requestedStatuses)
            {
                if (allowedStatuses.contains(status))
                {
                    statuses.add(status);
                }
            }
            transfer.getParams().put("statuses", statuses.isEmpty() ? List.of("__none__") : statuses);
        }
        else
        {
            transfer.getParams().put("statuses", allowedStatuses);
        }
        transfer.setStatus(null);
        transfer.setStatuses(null);
    }

    private List<String> resolveRequestedStatuses(InvTransferOrder transfer)
    {
        String status = transfer.getStatus();
        if (!StringUtils.isEmpty(status))
        {
            return List.of(status);
        }
        if ("deliverable".equals(transfer.getStatusGroup()))
        {
            return DELIVERABLE_STATUSES;
        }
        if ("receivable".equals(transfer.getStatusGroup()))
        {
            return RECEIVABLE_STATUSES;
        }
        if (transfer.getStatuses() != null && !transfer.getStatuses().isEmpty())
        {
            return transfer.getStatuses();
        }
        return List.of();
    }
}

package com.erp.inventory.service.impl;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.vo.InvSpecialistReadVo;
import com.erp.inventory.domain.vo.InvSpecialistActionContext;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.inventory.constant.InvItemTypes;
import com.erp.inventory.constant.InvStatusConstants;
import com.erp.inventory.domain.InvInboundRecord;
import com.erp.inventory.domain.InvGiftBox;
import com.erp.inventory.domain.InvOeItem;
import com.erp.inventory.domain.InvProduct;
import com.erp.inventory.domain.InvPurchaseDetail;
import com.erp.inventory.domain.InvPurchaseOrder;
import com.erp.inventory.domain.InvQualityInspection;
import com.erp.inventory.domain.InvQualityInspectionAttachment;
import com.erp.inventory.domain.InvReceiptBatch;
import com.erp.inventory.domain.InvReceiptBatchDetail;
import com.erp.inventory.domain.InvStock;
import com.erp.inventory.domain.InvStockLog;
import com.erp.inventory.domain.InvSupplier;
import com.erp.inventory.domain.dto.InvPurchaseReceiveResult;
import com.erp.inventory.domain.dto.InvReceiveItem;
import com.erp.inventory.domain.dto.InvReceiveRequest;
import com.erp.inventory.domain.dto.InvQualityCheckItem;
import com.erp.inventory.domain.dto.InvQualityCheckRequest;
import com.erp.inventory.domain.vo.InventoryItemSnapshot;
import com.erp.inventory.mapper.InvInboundRecordMapper;
import com.erp.inventory.mapper.InvNumberSequenceMapper;
import com.erp.inventory.mapper.InvPurchaseDetailMapper;
import com.erp.inventory.mapper.InvPurchaseOrderMapper;
import com.erp.inventory.mapper.InvQualityInspectionAttachmentMapper;
import com.erp.inventory.mapper.InvQualityInspectionMapper;
import com.erp.inventory.mapper.InvReceiptBatchDetailMapper;
import com.erp.inventory.mapper.InvReceiptBatchMapper;
import com.erp.inventory.mapper.InvPurchaseReturnDetailMapper;
import com.erp.inventory.mapper.InvStockLogMapper;
import com.erp.inventory.mapper.InvStockMapper;
import com.erp.inventory.mapper.InvSupplierMapper;
import com.erp.inventory.service.IInvPurchaseService;
import com.erp.inventory.service.IInvGiftService;
import com.erp.inventory.service.IInvOeService;
import com.erp.inventory.service.IInvProductService;
import com.erp.inventory.service.IInvSupplierService;

@Service
public class InvPurchaseServiceImpl extends InvBaseService implements IInvPurchaseService
{
    private static final Logger log = LoggerFactory.getLogger(InvPurchaseServiceImpl.class);
    private static final String PURCHASE_WAREHOUSE_CONTEXT_MESSAGE = "请选择仓库";
    private static final String PURCHASE_WAREHOUSE_MATCH_MESSAGE = "收货仓库必须为当前仓库";
    private static final String RECEIPT_BATCH_PENDING = "pending";
    private static final String RECEIPT_BATCH_PARTIAL = "partial";
    private static final String RECEIPT_BATCH_COMPLETED = "completed";
    private static final String PURCHASE_RECEIVE_COMMAND = "PURCHASE_RECEIVE";
    private static final String LEGACY_WHOLE_ORDER_QC_MESSAGE =
            "请选择具体收货批次进行质检，旧整单入口不能处理已分批收货记录";
    private static final int MAX_PURCHASE_LINES = 200;
    private static final int MAX_QUALITY_ATTACHMENTS = 5;
    private static final int MAX_ATTACHMENT_URL_LENGTH = 2048;
    private static final Set<String> QUALITY_DEFECT_LEVELS = Set.of("minor", "major", "critical");
    private static final List<String> TRUSTED_LOCAL_ATTACHMENT_PREFIXES = List.of(
            "/file/public/", "/profile/", "/uploads/erp-new-2/");

    @Autowired
    private InvPurchaseOrderMapper purchaseOrderMapper;

    @Autowired
    private InvQualityCommandExecutor qualityCommandExecutor;

    @Autowired
    private InvPurchaseReturnDetailMapper purchaseReturnDetailMapper;

    @Autowired
    private InvPurchaseDetailMapper purchaseDetailMapper;

    @Autowired
    private InvStockMapper stockMapper;

    @Autowired
    private InvStockLogMapper stockLogMapper;

    @Autowired
    private InvInboundRecordMapper inboundRecordMapper;

    @Autowired
    private InvReceiptBatchMapper receiptBatchMapper;

    @Autowired
    private InvReceiptBatchDetailMapper receiptBatchDetailMapper;

    @Autowired
    private InvQualityInspectionMapper qualityInspectionMapper;

    @Autowired
    private InvQualityInspectionAttachmentMapper qualityInspectionAttachmentMapper;

    @Autowired
    private InvNumberSequenceMapper numberSequenceMapper;

    @Autowired
    private InventoryItemResolver itemResolver;

    @Autowired
    private InvSupplierMapper supplierMapper;

    @Autowired
    private IInvSupplierService supplierService;

    @Autowired
    private IInvProductService productService;

    @Autowired
    private IInvOeService oeService;

    @Autowired
    private IInvGiftService giftService;

    @Override
    public InvSpecialistActionContext getActionContext(Long orderId, Long selectedShopDeptId)
    {
        Long warehouseId = requireWarehouseContext(selectedShopDeptId, PURCHASE_WAREHOUSE_CONTEXT_MESSAGE);
        InvPurchaseOrder order = assertAndGetScopedPurchase(orderId, selectedShopDeptId);
        assertPurchaseBelongsToSelectedWarehouse(order, warehouseId);
        return InvSpecialistActionContext.purchase(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvPurchaseOrder saveDraft(InvPurchaseOrder order, List<InvPurchaseDetail> details, Long selectedShopDeptId)
    {
        Long shopDeptId = requireWarehouseContext(selectedShopDeptId, PURCHASE_WAREHOUSE_CONTEXT_MESSAGE);
        InvPurchaseOrder locked = null;
        if (order != null && order.getOrderId() != null)
        {
            locked = requireLockedPurchase(order.getOrderId());
            assertPurchaseBelongsToSelectedWarehouse(locked, shopDeptId);
            InvStateGuard.requireDraftForEdit(locked.getStatus());
        }
        normalizeAndValidateDraft(order, details);
        if (details != null) itemResolver.lockReferences(details.stream().filter(java.util.Objects::nonNull)
                .map(detail -> InventoryItemResolver.referenceKey(detail.getItemType(), detail.getItemId(), detail.getProductId())).toList());
        applyCatalogItemsForDraft(order, details, shopDeptId);
        applySupplierSnapshot(order, shopDeptId);
        if (order.getOrderId() == null)
        {
            order.setShopDeptId(shopDeptId);
            order.setApplicantId(SecurityUtils.getUserId());
            order.setApplicantName(SecurityUtils.getUsername());
            order.setApplicantDeptId(SecurityUtils.getLoginUser().getSysUser().getDeptId());
            order.setCreateBy(SecurityUtils.getUsername());
            order.setStatus(InvStatusConstants.DRAFT);
            order.setOrderNo(generateOrderNo("PO"));
            if (purchaseOrderMapper.insertInvPurchaseOrder(order) != 1 || order.getOrderId() == null)
            {
                throw new ServiceException("采购单新增失败");
            }
            bindDetailsToOrder(details, order.getOrderId());
            if (purchaseDetailMapper.batchInsertInvPurchaseDetail(details) != details.size())
            {
                throw new ServiceException("采购明细新增失败");
            }
        }
        else
        {
            order.setShopDeptId(locked.getShopDeptId());
            order.setUpdateBy(SecurityUtils.getUsername());
            if (purchaseOrderMapper.updatePurchaseContent(order) != 1)
            {
                throw new ServiceException("采购草稿已变化，请刷新后重试");
            }
            List<InvPurchaseDetail> existingDetails = purchaseDetailMapper
                    .selectInvPurchaseDetailByOrderIdForUpdate(order.getOrderId());
            if (existingDetails == null || existingDetails.isEmpty())
            {
                throw new ServiceException("采购草稿原明细不存在，请刷新后重试");
            }
            if (purchaseDetailMapper.deleteInvPurchaseDetailByOrderId(order.getOrderId())
                    != existingDetails.size())
            {
                throw new ServiceException("采购草稿原明细已变化，请刷新后重试");
            }
            bindDetailsToOrder(details, order.getOrderId());
            if (purchaseDetailMapper.batchInsertInvPurchaseDetail(details) != details.size())
            {
                throw new ServiceException("采购明细保存失败");
            }
        }
        return projectBusinessStage(purchaseOrderMapper.selectInvPurchaseOrderById(order.getOrderId()));
    }

    private static void bindDetailsToOrder(List<InvPurchaseDetail> details, Long orderId)
    {
        for (InvPurchaseDetail detail : details)
        {
            detail.setOrderId(orderId);
        }
    }

    private static void normalizeAndValidateDraft(InvPurchaseOrder order, List<InvPurchaseDetail> details)
    {
        if (order == null)
        {
            throw new ServiceException("采购单不能为空");
        }
        String title = trimToNull(order.getOrderTitle());
        if (title == null)
        {
            throw new ServiceException("采购主题不能为空");
        }
        if (title.length() > 128)
        {
            throw new ServiceException("采购主题不能超过128个字符");
        }
        order.setOrderTitle(title);
        String remark = trimToNull(order.getRemark());
        if (remark != null && remark.length() > 500)
        {
            throw new ServiceException("采购备注不能超过500个字符");
        }
        order.setRemark(remark);
        if (details == null || details.isEmpty())
        {
            throw new ServiceException("请添加至少一条采购明细");
        }
        if (details.size() > MAX_PURCHASE_LINES)
        {
            throw new ServiceException("采购明细不能超过" + MAX_PURCHASE_LINES + "行");
        }
        for (InvPurchaseDetail detail : details)
        {
            if (detail == null)
            {
                throw new ServiceException("采购明细不能为空");
            }
            validateDecimal(detail.getQuantity(), true, 4, "采购数量");
            if (detail.getUnitPrice() != null)
            {
                validateDecimal(detail.getUnitPrice(), false, 6, "采购进价");
            }
        }
    }

    private static void validateDecimal(BigDecimal value, boolean positive, int maximumScale, String label)
    {
        if (value == null || (positive ? value.compareTo(BigDecimal.ZERO) <= 0
                : value.compareTo(BigDecimal.ZERO) < 0))
        {
            throw new ServiceException(label + (positive ? "必须大于0" : "不能小于0"));
        }
        BigDecimal normalized = value.stripTrailingZeros();
        int scale = Math.max(normalized.scale(), 0);
        int integerDigits = Math.max(normalized.precision() - normalized.scale(), 0);
        if (scale > maximumScale || integerDigits > 18 - maximumScale)
        {
            throw new ServiceException(label + "精度无效");
        }
    }

    static void applyCatalogProductsForDraft(InvPurchaseOrder order, List<InvPurchaseDetail> details,
            Map<Long, InvProduct> productsById)
    {
        if (order == null)
        {
            throw new ServiceException("采购单不能为空");
        }
        if (details == null || details.isEmpty())
        {
            throw new ServiceException("请添加至少一条采购明细");
        }
        if (order.getOrderDate() == null)
        {
            order.setOrderDate(new Date());
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        Set<String> supplierNames = new LinkedHashSet<>();
        Map<Long, InvProduct> productMap = productsById == null ? new HashMap<>() : productsById;
        for (InvPurchaseDetail detail : details)
        {
            if (detail == null || detail.getProductId() == null)
            {
                throw new ServiceException("请选择采购商品");
            }
            InvProduct product = productMap.get(detail.getProductId());
            if (product == null)
            {
                throw new ServiceException("商品不存在: " + detail.getProductId());
            }
            if (!"0".equals(product.getStatus()))
            {
                throw new ServiceException("商品 [" + product.getProductName() + "] 已停用");
            }
            String supplierName = trimToNull(product.getSupplierName());
            if (supplierName == null)
            {
                throw new ServiceException("商品 [" + product.getProductName() + "] 未绑定供应商，请先在商品管理维护供应商");
            }

            detail.setProductName(product.getProductName());
            detail.setSku(product.getSku());
            detail.setSpec(product.getSpec());
            detail.setUnit(product.getUnit());

            BigDecimal quantity = detail.getQuantity();
            if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0)
            {
                throw new ServiceException("商品 [" + product.getProductName() + "] 采购数量必须大于0");
            }
            BigDecimal unitPrice = detail.getUnitPrice();
            if (unitPrice == null)
            {
                unitPrice = product.getPurchasePrice() != null ? product.getPurchasePrice() : BigDecimal.ZERO;
            }
            if (unitPrice.compareTo(BigDecimal.ZERO) < 0)
            {
                throw new ServiceException("商品 [" + product.getProductName() + "] 进价不能小于0");
            }
            BigDecimal amount = quantity.multiply(unitPrice);
            detail.setUnitPrice(unitPrice);
            detail.setAmount(amount);
            detail.setReceivedQuantity(BigDecimal.ZERO);
            detail.setWarehouseId(null);

            supplierNames.add(supplierName);
            totalAmount = totalAmount.add(amount);
        }

        String supplierSummary = String.join("、", supplierNames);
        if (supplierSummary.length() > 128)
        {
            throw new ServiceException("采购单供应商名称过长，请拆分采购单");
        }
        order.setSupplierName(supplierSummary);
        order.setTotalAmount(totalAmount);
    }

    private void applyCatalogItemsForDraft(InvPurchaseOrder order, List<InvPurchaseDetail> details, Long selectedWarehouseId)
    {
        if (order == null)
        {
            throw new ServiceException("采购单不能为空");
        }
        if (details == null || details.isEmpty())
        {
            throw new ServiceException("请添加至少一条采购明细");
        }
        if (order.getOrderDate() == null)
        {
            order.setOrderDate(new Date());
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        Set<String> supplierNames = new LinkedHashSet<>();
        Set<String> itemKeys = new HashSet<>();
        for (InvPurchaseDetail detail : details)
        {
            InventoryItemSnapshot item = resolvePurchaseItem(detail, selectedWarehouseId);
            applyItemSnapshot(detail, item);
            detail.setWarehouseId(selectedWarehouseId);

            BigDecimal quantity = detail.getQuantity();
            if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0)
            {
                throw new ServiceException("物料 [" + item.getItemName() + "] 采购数量必须大于0");
            }
            BigDecimal unitPrice = detail.getUnitPrice();
            if (unitPrice == null)
            {
                unitPrice = item.getPurchasePrice() != null ? item.getPurchasePrice() : BigDecimal.ZERO;
            }
            if (unitPrice.compareTo(BigDecimal.ZERO) < 0)
            {
                throw new ServiceException("物料 [" + item.getItemName() + "] 进价不能小于0");
            }
            BigDecimal amount = quantity.multiply(unitPrice);
            detail.setUnitPrice(unitPrice);
            detail.setAmount(amount);
            detail.setReceivedQuantity(BigDecimal.ZERO);

            String supplierName = trimToNull(item.getSupplierName());
            if (supplierName != null)
            {
                supplierNames.add(supplierName);
            }
            totalAmount = totalAmount.add(amount);
        }

        String supplierSummary = String.join("、", supplierNames);
        if (supplierSummary.length() > 128)
        {
            throw new ServiceException("采购单供应商名称过长，请拆分采购单");
        }
        order.setSupplierName(supplierSummary);
        order.setTotalAmount(totalAmount);
    }

    private void applySupplierSnapshot(InvPurchaseOrder order, Long selectedWarehouseId)
    {
        if (order == null)
        {
            throw new ServiceException("采购单不能为空");
        }
        if (order.getSupplierId() == null)
        {
            throw new ServiceException("请选择供应商档案");
        }
        InvSupplier supplier = supplierMapper.selectInvSupplierById(order.getSupplierId());
        if (supplier == null || !"0".equals(supplier.getStatus()) || !"0".equals(supplier.getCooperationStatus()))
        {
            throw new ServiceException("所选供应商不存在、已停用或已暂停合作");
        }
        assertRelatedShopVisible(supplier.getShopDeptId(), selectedWarehouseId, "无权使用所选供应商");

        String supplierSummary = order.getSupplierName();
        if (supplierSummary != null && !supplierSummary.isBlank())
        {
            for (String name : supplierSummary.split("、"))
            {
                String normalized = name == null ? "" : name.trim();
                if (!normalized.isEmpty() && !"礼盒".equals(normalized)
                        && !supplier.getSupplierName().equals(normalized))
                {
                    throw new ServiceException("采购物料供应商 [" + normalized + "] 与所选供应商 ["
                            + supplier.getSupplierName() + "] 不一致，请拆分采购单");
                }
            }
        }
        order.setSupplierName(supplier.getSupplierName());
    }

    private InventoryItemSnapshot resolvePurchaseItem(InvPurchaseDetail detail, Long selectedWarehouseId)
    {
        if (detail == null)
        {
            throw new ServiceException("请选择采购物料");
        }
        String itemType = InvItemTypes.normalize(detail.getItemType());
        Long itemId = InvItemTypes.resolveItemId(itemType, detail.getItemId(), detail.getProductId());
        if (itemId == null)
        {
            throw new ServiceException("请选择采购物料");
        }
        InventoryItemSnapshot item = itemResolver.resolve(itemType, itemId, detail.getProductId());
        if (!"0".equals(item.getStatus()))
        {
            throw new ServiceException("物料 [" + item.getItemName() + "] 已停用");
        }
        if (InvItemTypes.PRODUCT.equals(item.getItemType()) && item.getOwnerDeptId() != null)
        {
            assertRelatedShopVisible(item.getOwnerDeptId(), selectedWarehouseId, "无权采购该商品");
        }
        return item;
    }

    private void applyItemSnapshot(InvPurchaseDetail detail, InventoryItemSnapshot item)
    {
        detail.setItemType(item.getItemType());
        detail.setItemId(item.getItemId());
        detail.setItemCode(item.getItemCode());
        detail.setItemName(item.getItemName());
        detail.setProductId(item.getProductId());
        detail.setProductName(item.getItemName());
        detail.setSku(item.getItemCode());
        detail.setSpec(item.getSpec());
        detail.setUnit(item.getUnit());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvPurchaseOrder submitPurchase(InvPurchaseOrder order, List<InvPurchaseDetail> details, Long selectedShopDeptId)
    {
        InvPurchaseOrder saved = saveDraft(order, details, selectedShopDeptId);
        transitionPurchaseStatus(saved.getOrderId(), InvStatusConstants.DRAFT,
                InvStatusConstants.SUBMITTED, null, "采购草稿已变化，请刷新后重试");
        return projectBusinessStage(purchaseOrderMapper.selectInvPurchaseOrderById(saved.getOrderId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvPurchaseOrder submitSavedPurchase(Long orderId, Long selectedShopDeptId)
    {
        Long selectedWarehouseId = requireWarehouseContext(selectedShopDeptId, PURCHASE_WAREHOUSE_CONTEXT_MESSAGE);
        InvPurchaseOrder locked = requireLockedPurchase(orderId);
        assertPurchaseBelongsToSelectedWarehouse(locked, selectedWarehouseId);
        InvStateGuard.requireDraftForEdit(locked.getStatus());
        transitionPurchaseStatus(orderId, InvStatusConstants.DRAFT,
                InvStatusConstants.SUBMITTED, null, "采购草稿已变化，请刷新后重试");
        return projectBusinessStage(purchaseOrderMapper.selectInvPurchaseOrderById(orderId));
    }

    @Override
    public InvPurchaseOrder getPurchaseDraft(Long orderId, Long selectedShopDeptId)
    {
        InvPurchaseOrder draft = getPurchaseDetail(orderId, selectedShopDeptId);
        InvStateGuard.requireDraftForEdit(draft.getStatus());
        return InvSpecialistReadVo.purchase(draft);
    }

    @Override
    public InvPurchaseOrder getReceiveContext(Long orderId, Long selectedShopDeptId)
    {
        InvPurchaseOrder order = getPurchaseDetail(orderId, selectedShopDeptId);
        if (!InvStatusConstants.SUBMITTED.equals(order.getStatus()))
            throw new ServiceException("当前采购单状态不可收货");
        return InvSpecialistReadVo.purchase(order);
    }

    @Override
    public InvPurchaseOrder getPurchaseDetail(Long orderId, Long selectedShopDeptId)
    {
        Long selectedWarehouseId = requireWarehouseContext(selectedShopDeptId, PURCHASE_WAREHOUSE_CONTEXT_MESSAGE);
        InvPurchaseOrder order = assertAndGetScopedPurchase(orderId, selectedShopDeptId);
        assertPurchaseBelongsToSelectedWarehouse(order, selectedWarehouseId);
        order.setDetails(purchaseDetailMapper.selectInvPurchaseDetailByOrderId(orderId));
        applyReturnableQuantities(order);
        return projectBusinessStage(order);
    }

    @Override
    public List<InvPurchaseOrder> selectPurchaseList(InvPurchaseOrder order, Long selectedShopDeptId)
    {
        Long warehouseId = requireWarehouseContext(selectedShopDeptId, PURCHASE_WAREHOUSE_CONTEXT_MESSAGE);
        if (order == null)
        {
            order = new InvPurchaseOrder();
        }
        applyExactWarehouseQuery(order, warehouseId);
        return projectBusinessStages(purchaseOrderMapper.selectInvPurchaseOrderList(order));
    }

    @Override
    public List<InvPurchaseOrder> selectMyPurchases(InvPurchaseOrder order, Long selectedShopDeptId)
    {
        Long warehouseId = requireWarehouseContext(selectedShopDeptId, PURCHASE_WAREHOUSE_CONTEXT_MESSAGE);
        if (order == null)
        {
            order = new InvPurchaseOrder();
        }
        order.setApplicantId(SecurityUtils.getUserId());
        applyExactWarehouseQuery(order, warehouseId);
        return projectBusinessStages(purchaseOrderMapper.selectMyInvPurchaseOrderList(order));
    }

    private static void applyExactWarehouseQuery(InvPurchaseOrder order, Long warehouseId)
    {
        order.setShopDeptId(warehouseId);
        order.getParams().remove("scopeDeptIds");
        order.getParams().remove("dataScope");
    }

    @Override
    public List<InvSupplier> selectPurchaseSuppliers(InvSupplier supplier, Long selectedShopDeptId)
    {
        requireWarehouseContext(selectedShopDeptId, PURCHASE_WAREHOUSE_CONTEXT_MESSAGE);
        InvSupplier query = supplier == null ? new InvSupplier() : supplier;
        query.setStatus("0");
        query.setCooperationStatus("0");
        return supplierService.selectSupplierList(query, selectedShopDeptId);
    }

    @Override
    public List<InvProduct> selectPurchaseProducts(InvProduct product, Long selectedShopDeptId)
    {
        requireWarehouseContext(selectedShopDeptId, PURCHASE_WAREHOUSE_CONTEXT_MESSAGE);
        InvProduct query = product == null ? new InvProduct() : product;
        query.setStatus("0");
        return productService.selectProductList(query, selectedShopDeptId);
    }

    @Override
    public List<InvOeItem> selectPurchaseOeItems(InvOeItem item, Long selectedShopDeptId)
    {
        requireWarehouseContext(selectedShopDeptId, PURCHASE_WAREHOUSE_CONTEXT_MESSAGE);
        InvOeItem query = item == null ? new InvOeItem() : item;
        query.setStatus("0");
        return oeService.selectOeList(query);
    }

    @Override
    public List<InvGiftBox> selectPurchaseGifts(InvGiftBox gift, Long selectedShopDeptId)
    {
        requireWarehouseContext(selectedShopDeptId, PURCHASE_WAREHOUSE_CONTEXT_MESSAGE);
        InvGiftBox query = gift == null ? new InvGiftBox() : gift;
        query.setStatus("0");
        return giftService.selectGiftList(query);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvPurchaseReceiveResult receivePurchase(Long orderId, InvReceiveRequest receiveRequest,
            Long selectedShopDeptId, String requestId)
    {
        Long selectedWarehouseId = requireWarehouseContext(selectedShopDeptId, PURCHASE_WAREHOUSE_CONTEXT_MESSAGE);
        Long warehouseId = requireAndValidateReceiveWarehouseId(receiveRequest, selectedWarehouseId);
        Date arrivedTime = requireReceiveArrivedTime(receiveRequest);
        String normalizedRequestId = InvQualityCommandExecutor.requireRequestId(requestId);
        InvPurchaseOrder locked = requireLockedPurchase(orderId);
        assertPurchaseBelongsToSelectedWarehouse(locked, selectedWarehouseId);
        Map<String, Object> payload = freezeReceivePayload(orderId, receiveRequest);
        return qualityCommandExecutor.execute(normalizedRequestId, PURCHASE_RECEIVE_COMMAND,
                selectedWarehouseId, "purchase:" + orderId, payload, InvPurchaseReceiveResult.class,
                () -> createReceiptBatch(orderId, receiveRequest, warehouseId, arrivedTime,
                        locked, normalizedRequestId));
    }

    private InvPurchaseReceiveResult createReceiptBatch(Long orderId, InvReceiveRequest receiveRequest,
            Long warehouseId, Date arrivedTime, InvPurchaseOrder locked, String requestId)
    {
        InvStateGuard.requireSubmittedForReceive(locked.getStatus());
        InvPurchaseOrder order = locked;
        List<InvPurchaseDetail> details = purchaseDetailMapper.selectInvPurchaseDetailByOrderIdForUpdate(orderId);
        if (details == null || details.isEmpty())
        {
            throw new ServiceException("采购单无明细");
        }

        Map<Long, InvPurchaseDetail> detailMap = new HashMap<>();
        for (InvPurchaseDetail d : details)
        {
            detailMap.put(d.getDetailId(), d);
        }

        List<InvReceiveItem> validItems = validateReceiveItems(receiveRequest, detailMap);
        BigDecimal batchTotal = validItems.stream()
                .map(InvReceiveItem::getReceiveQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        InvReceiptBatch receiptBatch = new InvReceiptBatch();
        receiptBatch.setBatchNo(generateOrderNo("RB"));
        receiptBatch.setPurchaseOrderId(orderId);
        receiptBatch.setOrderNo(order.getOrderNo());
        receiptBatch.setWarehouseId(warehouseId);
        receiptBatch.setSupplierBatchNo(trimToNull(receiveRequest.getSupplierBatchNo()));
        receiptBatch.setDeliveryNoteNo(trimToNull(receiveRequest.getDeliveryNoteNo()));
        receiptBatch.setArrivedTime(arrivedTime);
        receiptBatch.setStatus(RECEIPT_BATCH_PENDING);
        receiptBatch.setReceivedUserId(SecurityUtils.getUserId());
        receiptBatch.setReceivedBy(SecurityUtils.getUsername());
        receiptBatch.setTotalQuantity(batchTotal);
        receiptBatch.setPendingQuantity(batchTotal);
        receiptBatch.setCreateBy(SecurityUtils.getUsername());
        receiptBatch.setRemark(trimToNull(receiveRequest.getRemark()));
        if (receiptBatchMapper.insertInvReceiptBatch(receiptBatch) != 1
                || receiptBatch.getBatchId() == null)
        {
            throw new ServiceException("收货批次新增失败");
        }

        for (InvReceiveItem item : validItems)
        {
            BigDecimal toReceive = item.getReceiveQuantity();
            InvPurchaseDetail detail = detailMap.get(item.getDetailId());

            InvReceiptBatchDetail batchDetail = new InvReceiptBatchDetail();
            batchDetail.setBatchId(receiptBatch.getBatchId());
            batchDetail.setPurchaseOrderId(orderId);
            batchDetail.setPurchaseDetailId(detail.getDetailId());
            batchDetail.setItemType(InvItemTypes.normalize(detail.getItemType()));
            batchDetail.setItemId(InvItemTypes.resolveItemId(
                    batchDetail.getItemType(), detail.getItemId(), detail.getProductId()));
            batchDetail.setProductId(detail.getProductId());
            batchDetail.setItemCode(trimToNull(detail.getItemCode()) != null ? detail.getItemCode() : detail.getSku());
            batchDetail.setItemName(trimToNull(detail.getItemName()) != null ? detail.getItemName() : detail.getProductName());
            batchDetail.setUnit(detail.getUnit());
            batchDetail.setReceivedQuantity(toReceive);
            batchDetail.setPendingQuantity(toReceive);
            batchDetail.setInspectedQuantity(BigDecimal.ZERO);
            batchDetail.setAcceptedQuantity(BigDecimal.ZERO);
            batchDetail.setRejectedQuantity(BigDecimal.ZERO);
            batchDetail.setConcessionQuantity(BigDecimal.ZERO);
            if (receiptBatchDetailMapper.insertInvReceiptBatchDetail(batchDetail) != 1
                    || batchDetail.getBatchDetailId() == null)
            {
                throw new ServiceException("收货批次明细新增失败");
            }

            InvInboundRecord inbound = new InvInboundRecord();
            inbound.setPurchaseOrderId(orderId);
            inbound.setPurchaseDetailId(detail.getDetailId());
            inbound.setOrderNo(order.getOrderNo());
            inbound.setItemType(detail.getItemType());
            inbound.setItemId(detail.getItemId());
            inbound.setProductId(detail.getProductId());
            inbound.setShopDeptId(warehouseId);
            inbound.setWarehouseId(warehouseId);
            inbound.setReceiptBatchId(receiptBatch.getBatchId());
            inbound.setReceiptBatchDetailId(batchDetail.getBatchDetailId());
            inbound.setQuantity(toReceive);
            inbound.setInspectedQuantity(BigDecimal.ZERO);
            inbound.setAcceptedQuantity(BigDecimal.ZERO);
            inbound.setRejectedQuantity(BigDecimal.ZERO);
            inbound.setConcessionQuantity(BigDecimal.ZERO);
            inbound.setQcResult(InvStatusConstants.QC_PENDING);
            inbound.setCreateBy(SecurityUtils.getUsername());
            inbound.setCreateTime(new Date());
            if (inboundRecordMapper.insertInvInboundRecord(inbound) != 1)
            {
                throw new ServiceException("待检入库记录新增失败");
            }

            detail.setWarehouseId(warehouseId);
            detail.setReceivedQuantity(detail.getReceivedQuantity() != null
                    ? detail.getReceivedQuantity().add(toReceive) : toReceive);
            if (purchaseDetailMapper.updateInvPurchaseDetail(detail) != 1)
            {
                throw new ServiceException("采购明细收货数量更新失败");
            }
        }

        transitionPurchaseStatus(orderId, InvStatusConstants.SUBMITTED,
                InvStatusConstants.SUBMITTED, InvStatusConstants.QC_PENDING,
                "采购单收货状态已变化，请刷新后重试");

        InvPurchaseReceiveResult result = new InvPurchaseReceiveResult();
        result.setRequestId(requestId);
        result.setPurchaseOrderId(orderId);
        result.setWarehouseId(warehouseId);
        result.setReceiptBatchId(receiptBatch.getBatchId());
        result.setBatchNo(receiptBatch.getBatchNo());
        result.setArrivedTime(arrivedTime);
        result.setReceivedQuantity(batchTotal);
        return result;
    }

    static List<InvReceiveItem> validateReceiveItems(InvReceiveRequest receiveRequest,
            Map<Long, InvPurchaseDetail> detailMap)
    {
        if (receiveRequest == null || receiveRequest.getItems() == null)
        {
            throw new ServiceException("收货明细不能为空");
        }
        List<InvReceiveItem> validItems = new ArrayList<>();
        Set<Long> seenDetailIds = new HashSet<>();
        for (InvReceiveItem item : receiveRequest.getItems())
        {
            if (item == null)
            {
                throw new ServiceException("收货明细不能为空");
            }
            if (item.getDetailId() == null || !seenDetailIds.add(item.getDetailId()))
            {
                throw new ServiceException("收货明细不能重复");
            }
            validateDecimal(item.getReceiveQuantity(), true, 4, "收货数量");
            InvPurchaseDetail detail = detailMap.get(item.getDetailId());
            if (detail == null)
            {
                throw new ServiceException("明细ID不存在: " + item.getDetailId());
            }
            BigDecimal ordered = detail.getQuantity() != null ? detail.getQuantity() : BigDecimal.ZERO;
            BigDecimal received = detail.getReceivedQuantity() != null
                    ? detail.getReceivedQuantity() : BigDecimal.ZERO;
            BigDecimal remaining = ordered.subtract(received).max(BigDecimal.ZERO);
            if (item.getReceiveQuantity().compareTo(remaining) > 0)
            {
                throw new ServiceException("物料 [" + displayItemName(detail) + "] 收货数量超过未收数量");
            }
            validItems.add(item);
        }
        if (validItems.isEmpty())
        {
            throw new ServiceException("请至少录入一条有效收货数量");
        }
        return validItems;
    }

    static Date requireReceiveArrivedTime(InvReceiveRequest receiveRequest)
    {
        if (receiveRequest == null || receiveRequest.getArrivedTime() == null)
        {
            throw new ServiceException("实际到货时间不能为空");
        }
        return receiveRequest.getArrivedTime();
    }

    @Override
    public List<InvReceiptBatch> selectReceiptBatches(Long orderId, Long selectedShopDeptId)
    {
        Long selectedWarehouseId = requireWarehouseContext(selectedShopDeptId, PURCHASE_WAREHOUSE_CONTEXT_MESSAGE);
        InvPurchaseOrder order = assertAndGetScopedPurchase(orderId, selectedShopDeptId);
        assertPurchaseBelongsToSelectedWarehouse(order, selectedWarehouseId);
        return assembleReceiptTrace(receiptBatchMapper.selectByOrderId(orderId),
                orderId, selectedWarehouseId, true);
    }

    @Override
    public List<InvReceiptBatch> selectPendingReceiptBatches(Long orderId, Long selectedShopDeptId)
    {
        Long selectedWarehouseId = requireWarehouseContext(selectedShopDeptId, PURCHASE_WAREHOUSE_CONTEXT_MESSAGE);
        InvPurchaseOrder order = assertAndGetScopedPurchase(orderId, selectedShopDeptId);
        assertPurchaseBelongsToSelectedWarehouse(order, selectedWarehouseId);
        return assembleReceiptTrace(receiptBatchMapper.selectPendingByOrderId(orderId),
                orderId, selectedWarehouseId, false);
    }

    private List<InvReceiptBatch> assembleReceiptTrace(List<InvReceiptBatch> batches,
            Long orderId, Long selectedWarehouseId, boolean includeInspections)
    {
        if (batches == null)
        {
            return new ArrayList<>();
        }
        for (InvReceiptBatch batch : batches)
        {
            if (batch == null || !orderId.equals(batch.getPurchaseOrderId())
                    || !selectedWarehouseId.equals(batch.getWarehouseId()))
            {
                throw new ServiceException("收货轨迹包含其他采购单或仓库数据");
            }
            batch.setDetails(receiptBatchDetailMapper.selectByBatchId(batch.getBatchId()));
            if (includeInspections)
            {
                List<InvQualityInspection> inspections = qualityInspectionMapper
                        .selectByReceiptBatchId(batch.getBatchId());
                if (inspections == null)
                {
                    inspections = new ArrayList<>();
                }
                for (InvQualityInspection inspection : inspections)
                {
                    inspection.setAttachments(qualityInspectionAttachmentMapper
                            .selectByInspectionId(inspection.getInspectionId()));
                }
                batch.setInspections(inspections);
            }
        }
        return batches;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void qualityCheckBatch(Long orderId, InvQualityCheckRequest qualityCheckRequest, Long selectedShopDeptId)
    {
        Long selectedWarehouseId = requireWarehouseContext(selectedShopDeptId, PURCHASE_WAREHOUSE_CONTEXT_MESSAGE);
        if (qualityCheckRequest == null || qualityCheckRequest.getReceiptBatchId() == null
                || qualityCheckRequest.getItems() == null || qualityCheckRequest.getItems().isEmpty())
        {
            throw new ServiceException("质检批次和明细不能为空");
        }

        InvPurchaseOrder locked = requireLockedPurchase(orderId);
        assertPurchaseBelongsToSelectedWarehouse(locked, selectedWarehouseId);
        qualityCommandExecutor.execute(qualityCheckRequest.getRequestId(), "PURCHASE_QUALITY_CHECK",
                selectedWarehouseId, "purchase:" + orderId + ":batch:" + qualityCheckRequest.getReceiptBatchId(),
                qualityCheckRequest, Boolean.class, () -> {
                    applyQualityCheckBatch(orderId, qualityCheckRequest, selectedWarehouseId, locked);
                    return Boolean.TRUE;
                });
    }

    private void applyQualityCheckBatch(Long orderId, InvQualityCheckRequest qualityCheckRequest,
            Long selectedWarehouseId, InvPurchaseOrder locked)
    {
        InvStateGuard.requireSubmittedForQualityCheck(locked.getStatus());
        InvStateGuard.requirePendingQualityCheck(locked.getQcStatus());
        InvPurchaseOrder order = locked;

        InvReceiptBatch batch = receiptBatchMapper.selectByIdForUpdate(qualityCheckRequest.getReceiptBatchId());
        if (batch == null || !orderId.equals(batch.getPurchaseOrderId()))
        {
            throw new ServiceException("收货批次不存在或不属于当前采购单");
        }
        if (!selectedWarehouseId.equals(batch.getWarehouseId()))
        {
            throw new ServiceException("只能质检当前仓库的收货批次");
        }
        if (RECEIPT_BATCH_COMPLETED.equals(batch.getStatus())
                || zero(batch.getPendingQuantity()).compareTo(BigDecimal.ZERO) <= 0)
        {
            throw new ServiceException("该收货批次已完成质检");
        }

        List<InvReceiptBatchDetail> batchDetails = receiptBatchDetailMapper
                .selectByBatchIdForUpdate(batch.getBatchId());
        Map<Long, InvReceiptBatchDetail> batchDetailMap = new HashMap<>();
        for (InvReceiptBatchDetail detail : batchDetails)
        {
            batchDetailMap.put(detail.getBatchDetailId(), detail);
        }
        List<InvPurchaseDetail> purchaseDetails = purchaseDetailMapper
                .selectInvPurchaseDetailByOrderIdForUpdate(orderId);
        PurchaseDetailIndex purchaseDetailIndex = indexPurchaseDetails(purchaseDetails);
        Set<Long> seenBatchDetailIds = new HashSet<>();
        Date inspectionTime = new Date();

        for (InvQualityCheckItem item : qualityCheckRequest.getItems())
        {
            if (item == null || item.getBatchDetailId() == null
                    || !seenBatchDetailIds.add(item.getBatchDetailId()))
            {
                throw new ServiceException("质检明细不能为空或重复");
            }
            InvReceiptBatchDetail batchDetail = batchDetailMap.get(item.getBatchDetailId());
            if (batchDetail == null)
            {
                throw new ServiceException("质检明细不属于当前收货批次: " + item.getBatchDetailId());
            }
            validateQualityCheckQuantities(item, batchDetail.getPendingQuantity());
            validateQualityMetadata(item);

            InvInboundRecord inbound = inboundRecordMapper
                    .selectByBatchDetailIdForUpdate(batchDetail.getBatchDetailId());
            if (inbound == null || !orderId.equals(inbound.getPurchaseOrderId()))
            {
                throw new ServiceException("收货批次缺少对应的入库记录");
            }
            InvPurchaseDetail purchaseDetail = purchaseDetailIndex.byDetailId.get(batchDetail.getPurchaseDetailId());
            if (purchaseDetail == null)
            {
                throw new ServiceException("收货批次缺少对应的采购明细");
            }

            String conclusion = resolveQualityConclusion(
                    item.getAcceptedQuantity(), item.getRejectedQuantity(), item.getConcessionQuantity());
            InvQualityInspection inspection = new InvQualityInspection();
            inspection.setInspectionNo(generateOrderNo("QI"));
            inspection.setReceiptBatchId(batch.getBatchId());
            inspection.setBatchDetailId(batchDetail.getBatchDetailId());
            inspection.setPurchaseOrderId(orderId);
            inspection.setPurchaseDetailId(batchDetail.getPurchaseDetailId());
            inspection.setInspectedQuantity(item.getInspectedQuantity());
            inspection.setAcceptedQuantity(item.getAcceptedQuantity());
            inspection.setRejectedQuantity(item.getRejectedQuantity());
            inspection.setConcessionQuantity(item.getConcessionQuantity());
            inspection.setConclusion(conclusion);
            inspection.setDefectLevel(trimToNull(item.getDefectLevel()));
            inspection.setDefectReason(trimToNull(item.getDefectReason()));
            inspection.setInspectorUserId(SecurityUtils.getUserId());
            inspection.setInspectorName(SecurityUtils.getUsername());
            inspection.setInspectionTime(inspectionTime);
            inspection.setCreateBy(SecurityUtils.getUsername());
            inspection.setRemark(trimToNull(item.getRemark()));
            if (qualityInspectionMapper.insertInvQualityInspection(inspection) != 1
                    || inspection.getInspectionId() == null)
            {
                throw new ServiceException("质检记录新增失败");
            }
            insertQualityAttachments(inspection.getInspectionId(), item.getAttachmentUrls());

            BigDecimal stockAccepted = item.getAcceptedQuantity().add(item.getConcessionQuantity());
            if (stockAccepted.compareTo(BigDecimal.ZERO) > 0)
            {
                postPurchaseStock(order, purchaseDetail, inbound, stockAccepted,
                        item.getConcessionQuantity().compareTo(BigDecimal.ZERO) > 0
                                ? "采购入库-含让步接收" : "采购入库-质检合格");
            }
            if (item.getRejectedQuantity().compareTo(BigDecimal.ZERO) > 0)
            {
                BigDecimal received = zero(purchaseDetail.getReceivedQuantity());
                purchaseDetail.setReceivedQuantity(
                        received.subtract(item.getRejectedQuantity()).max(BigDecimal.ZERO));
                if (purchaseDetailMapper.updateInvPurchaseDetail(purchaseDetail) != 1)
                {
                    throw new ServiceException("采购明细拒收数量回退失败");
                }
            }

            batchDetail.setInspectedQuantity(zero(batchDetail.getInspectedQuantity()).add(item.getInspectedQuantity()));
            batchDetail.setAcceptedQuantity(zero(batchDetail.getAcceptedQuantity()).add(item.getAcceptedQuantity()));
            batchDetail.setRejectedQuantity(zero(batchDetail.getRejectedQuantity()).add(item.getRejectedQuantity()));
            batchDetail.setConcessionQuantity(zero(batchDetail.getConcessionQuantity()).add(item.getConcessionQuantity()));
            batchDetail.setPendingQuantity(zero(batchDetail.getReceivedQuantity())
                    .subtract(batchDetail.getInspectedQuantity()).max(BigDecimal.ZERO));
            if (receiptBatchDetailMapper.updateInspectionProgress(batchDetail) != 1)
            {
                throw new ServiceException("收货批次质检进度更新失败");
            }

            inbound.setQualityInspectionId(inspection.getInspectionId());
            inbound.setInspectedQuantity(batchDetail.getInspectedQuantity());
            inbound.setAcceptedQuantity(batchDetail.getAcceptedQuantity());
            inbound.setRejectedQuantity(batchDetail.getRejectedQuantity());
            inbound.setConcessionQuantity(batchDetail.getConcessionQuantity());
            inbound.setQcResult(batchDetail.getPendingQuantity().compareTo(BigDecimal.ZERO) > 0
                    ? InvStatusConstants.QC_PENDING
                    : resolveQualityConclusion(batchDetail.getAcceptedQuantity(),
                            batchDetail.getRejectedQuantity(), batchDetail.getConcessionQuantity()));
            inbound.setQcUser(SecurityUtils.getUsername());
            inbound.setQcTime(inspectionTime);
            inbound.setQcRemark(trimToNull(item.getDefectReason()));
            if (inboundRecordMapper.updateInvInboundRecord(inbound) != 1)
            {
                throw new ServiceException("待检入库记录更新失败");
            }
        }

        BigDecimal batchPending = batchDetails.stream()
                .map(detail -> zero(detail.getPendingQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (receiptBatchMapper.updateProgress(batch.getBatchId(), batchPending,
                batchPending.compareTo(BigDecimal.ZERO) > 0
                        ? RECEIPT_BATCH_PARTIAL : RECEIPT_BATCH_COMPLETED,
                SecurityUtils.getUsername()) != 1)
        {
            throw new ServiceException("收货批次状态更新失败");
        }

        refreshPurchaseQualityConclusion(orderId, purchaseDetails);
    }

    static void validateQualityCheckQuantities(InvQualityCheckItem item, BigDecimal pendingQuantity)
    {
        if (item == null || item.getInspectedQuantity() == null
                || item.getAcceptedQuantity() == null || item.getRejectedQuantity() == null
                || item.getConcessionQuantity() == null)
        {
            throw new ServiceException("质检数量不能为空");
        }
        validateDecimal(item.getInspectedQuantity(), true, 4, "本次检验数量");
        validateDecimal(item.getAcceptedQuantity(), false, 4, "合格数量");
        validateDecimal(item.getRejectedQuantity(), false, 4, "拒收数量");
        validateDecimal(item.getConcessionQuantity(), false, 4, "让步数量");
        BigDecimal classified = item.getAcceptedQuantity()
                .add(item.getRejectedQuantity()).add(item.getConcessionQuantity());
        if (classified.compareTo(item.getInspectedQuantity()) != 0)
        {
            throw new ServiceException("合格+拒收+让步数量必须等于本次检验数量");
        }
        if (item.getInspectedQuantity().compareTo(zero(pendingQuantity)) > 0)
        {
            throw new ServiceException("本次检验数量不能超过待检数量");
        }
        if ((item.getRejectedQuantity().compareTo(BigDecimal.ZERO) > 0
                || item.getConcessionQuantity().compareTo(BigDecimal.ZERO) > 0)
                && trimToNull(item.getDefectReason()) == null)
        {
            throw new ServiceException("存在拒收或让步数量时必须填写原因");
        }
    }

    private static void validateQualityMetadata(InvQualityCheckItem item)
    {
        String defectLevel = trimToNull(item.getDefectLevel());
        if (defectLevel != null && !QUALITY_DEFECT_LEVELS.contains(defectLevel))
        {
            throw new ServiceException("质检缺陷级别无效");
        }
        if (trimToNull(item.getDefectReason()) != null
                && item.getDefectReason().trim().length() > 500)
        {
            throw new ServiceException("质检原因不能超过500个字符");
        }
        if (trimToNull(item.getRemark()) != null && item.getRemark().trim().length() > 500)
        {
            throw new ServiceException("质检备注不能超过500个字符");
        }
        item.setDefectLevel(defectLevel);
        item.setDefectReason(trimToNull(item.getDefectReason()));
        item.setRemark(trimToNull(item.getRemark()));
        validateAttachmentUrls(item.getAttachmentUrls());
    }

    private static String resolveQualityConclusion(BigDecimal accepted, BigDecimal rejected, BigDecimal concession)
    {
        if (zero(concession).compareTo(BigDecimal.ZERO) > 0
                || (zero(accepted).compareTo(BigDecimal.ZERO) > 0
                    && zero(rejected).compareTo(BigDecimal.ZERO) > 0))
        {
            return InvStatusConstants.QC_CONCESSION;
        }
        if (zero(accepted).compareTo(BigDecimal.ZERO) > 0)
        {
            return InvStatusConstants.QC_PASSED;
        }
        return InvStatusConstants.QC_REJECTED;
    }

    private void postPurchaseStock(InvPurchaseOrder order, InvPurchaseDetail detail,
            InvInboundRecord inbound, BigDecimal acceptedQuantity, String remark)
    {
        String itemType = InvItemTypes.normalize(inbound.getItemType());
        Long itemId = InvItemTypes.resolveItemId(itemType, inbound.getItemId(), inbound.getProductId());
        BigDecimal unitPrice = detail.getUnitPrice() != null ? detail.getUnitPrice() : BigDecimal.ZERO;
        BigDecimal incomingCost = acceptedQuantity.multiply(unitPrice);
        InvStock stock = stockMapper.selectInvStockByItemShopWarehouseForUpdate(
                itemType, itemId, inbound.getShopDeptId(), inbound.getWarehouseId());
        BigDecimal beforeQty = BigDecimal.ZERO;
        if (stock == null)
        {
            stock = new InvStock();
            stock.setItemType(itemType);
            stock.setItemId(itemId);
            stock.setProductId(inbound.getProductId());
            stock.setShopDeptId(inbound.getShopDeptId());
            stock.setWarehouseId(inbound.getWarehouseId());
            stock.setCurrentQuantity(acceptedQuantity);
            stock.setAvailableQuantity(acceptedQuantity);
            stock.setCostPrice(unitPrice);
            stock.setTotalCost(incomingCost);
            stock.setLastInTime(new Date());
            stock.setCreateBy(SecurityUtils.getUsername());
            if (stockMapper.insertInvStock(stock) != 1)
            {
                throw new ServiceException("采购库存新增失败");
            }
        }
        else
        {
            beforeQty = zero(stock.getCurrentQuantity());
            if (stockMapper.addInvStockWithCost(stock.getStockId(), stock.getVersion(),
                    acceptedQuantity, incomingCost, SecurityUtils.getUsername()) != 1)
            {
                throw new ServiceException("采购库存已变化，请刷新后重试");
            }
            stock = stockMapper.selectInvStockById(stock.getStockId());
            if (stock == null)
            {
                throw new ServiceException("采购库存更新结果不存在");
            }
        }
        InvStockLog log = new InvStockLog();
        log.setItemType(itemType);
        log.setItemId(itemId);
        log.setProductId(inbound.getProductId());
        log.setShopDeptId(inbound.getShopDeptId());
        log.setWarehouseId(inbound.getWarehouseId());
        log.setMovementType(InvStatusConstants.MOVEMENT_PURCHASE_IN);
        log.setBusinessType("purchase");
        log.setBusinessId(order.getOrderId());
        log.setBusinessNo(order.getOrderNo());
        log.setChangeQuantity(acceptedQuantity);
        log.setBeforeQuantity(beforeQty);
        log.setAfterQuantity(stock.getCurrentQuantity());
        log.setCostPrice(stock.getCostPrice());
        log.setCreateBy(SecurityUtils.getUsername());
        log.setCreateTime(new Date());
        log.setRemark(remark);
        if (stockLogMapper.insertInvStockLog(log) != 1)
        {
            throw new ServiceException("采购库存日志新增失败");
        }
    }

    private void insertQualityAttachments(Long inspectionId, List<String> attachmentUrls)
    {
        if (attachmentUrls == null || attachmentUrls.isEmpty())
        {
            return;
        }
        List<InvQualityInspectionAttachment> attachments = new ArrayList<>();
        Set<String> seenUrls = new LinkedHashSet<>();
        for (String rawUrl : attachmentUrls)
        {
            String url = trimToNull(rawUrl);
            if (url == null || !seenUrls.add(url))
            {
                continue;
            }
            String path = url.contains("?") ? url.substring(0, url.indexOf('?')) : url;
            String fileName = path.substring(path.lastIndexOf('/') + 1);
            int extensionIndex = fileName.lastIndexOf('.');
            InvQualityInspectionAttachment attachment = new InvQualityInspectionAttachment();
            attachment.setInspectionId(inspectionId);
            attachment.setFileName(fileName);
            attachment.setFileUrl(url);
            attachment.setFileType(extensionIndex >= 0 ? fileName.substring(extensionIndex + 1) : null);
            attachment.setCreateBy(SecurityUtils.getUsername());
            attachment.setCreateTime(new Date());
            attachments.add(attachment);
        }
        if (!attachments.isEmpty())
        {
            if (qualityInspectionAttachmentMapper.batchInsert(attachments) != attachments.size())
            {
                throw new ServiceException("质检附件保存失败");
            }
        }
    }

    private static void validateAttachmentUrls(List<String> attachmentUrls)
    {
        if (attachmentUrls == null)
        {
            return;
        }
        if (attachmentUrls.size() > MAX_QUALITY_ATTACHMENTS)
        {
            throw new ServiceException("质检附件不能超过" + MAX_QUALITY_ATTACHMENTS + "个");
        }
        Set<String> uniqueUrls = new HashSet<>();
        for (String rawUrl : attachmentUrls)
        {
            String url = trimToNull(rawUrl);
            if (url == null || url.length() > MAX_ATTACHMENT_URL_LENGTH
                    || url.indexOf('\r') >= 0 || url.indexOf('\n') >= 0
                    || !isSafeAttachmentUrl(url))
            {
                throw new ServiceException("质检附件地址无效");
            }
            if (!uniqueUrls.add(url))
            {
                throw new ServiceException("质检附件不能重复");
            }
        }
    }

    static boolean isSafeAttachmentUrl(String value)
    {
        try
        {
            URI uri = new URI(value);
            if (uri.getFragment() != null || uri.getUserInfo() != null)
            {
                return false;
            }
            String path = uri.getPath();
            String rawPath = uri.getRawPath();
            String lowerRawPath = rawPath == null ? "" : rawPath.toLowerCase();
            if (path == null || !path.equals(uri.normalize().getPath())
                    || lowerRawPath.contains("%2e") || lowerRawPath.contains("%2f")
                    || lowerRawPath.contains("%5c"))
            {
                return false;
            }
            String fileName = path.substring(path.lastIndexOf('/') + 1);
            int extensionIndex = fileName.lastIndexOf('.');
            if (fileName.isBlank() || fileName.length() > 255
                    || (extensionIndex >= 0 && fileName.length() - extensionIndex - 1 > 32))
            {
                return false;
            }
            if (uri.isAbsolute() || uri.getRawAuthority() != null
                    || !value.startsWith("/") || value.startsWith("//"))
            {
                return false;
            }
            return TRUSTED_LOCAL_ATTACHMENT_PREFIXES.stream().anyMatch(path::startsWith);
        }
        catch (URISyntaxException exception)
        {
            return false;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void qualityCheckWithRequest(Long orderId, String qcResult, String qcRemark,
            Long selectedShopDeptId, String requestId)
    {
        Long warehouseId = requireWarehouseContext(selectedShopDeptId, PURCHASE_WAREHOUSE_CONTEXT_MESSAGE);
        InvPurchaseOrder locked = requireLockedPurchase(orderId);
        assertPurchaseBelongsToSelectedWarehouse(locked, warehouseId);
        Map<String, String> payload = new java.util.LinkedHashMap<>();
        payload.put("qcResult", qcResult);
        payload.put("qcRemark", qcRemark);
        qualityCommandExecutor.execute(requestId, "PURCHASE_QUALITY_CHECK_LEGACY", warehouseId,
                "purchase:" + orderId, payload, Boolean.class, () -> {
                    qualityCheck(orderId, qcResult, qcRemark, selectedShopDeptId);
                    return Boolean.TRUE;
                });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void qualityCheck(Long orderId, String qcResult, String qcRemark, Long selectedShopDeptId)
    {
        log.warn("legacy_purchase_qc_api orderId={} user={} result={}",
                orderId, SecurityUtils.getUserId(), qcResult);
        Long selectedWarehouseId = requireWarehouseContext(selectedShopDeptId, PURCHASE_WAREHOUSE_CONTEXT_MESSAGE);
        InvPurchaseOrder order = assertAndGetScopedPurchase(orderId, selectedShopDeptId);
        assertPurchaseBelongsToSelectedWarehouse(order, selectedWarehouseId);
        InvStateGuard.requireSubmittedForQualityCheck(order.getStatus());
        InvStateGuard.requirePendingQualityCheck(order.getQcStatus());
        if (!InvStatusConstants.QC_PASSED.equals(qcResult) && !InvStatusConstants.QC_REJECTED.equals(qcResult) && !InvStatusConstants.QC_CONCESSION.equals(qcResult))
        {
            throw new ServiceException("无效的质检结果");
        }

        InvPurchaseOrder locked = requireLockedPurchase(orderId);
        assertPurchaseBelongsToSelectedWarehouse(locked, selectedWarehouseId);
        InvStateGuard.requireSubmittedForQualityCheck(locked.getStatus());
        InvStateGuard.requirePendingQualityCheck(locked.getQcStatus());
        order = locked;

        List<InvPurchaseDetail> details = purchaseDetailMapper.selectInvPurchaseDetailByOrderIdForUpdate(orderId);
        List<InvInboundRecord> lockedInbound = inboundRecordMapper.selectByOrderIdForUpdate(orderId);
        List<InvInboundRecord> inboundRecords = historicalPendingInboundRecords(lockedInbound);
        if (inboundRecords.isEmpty())
        {
            if (hasBatchBackedReceiptFacts(orderId, lockedInbound))
            {
                throw new ServiceException(LEGACY_WHOLE_ORDER_QC_MESSAGE);
            }
            throw new ServiceException("采购单无待检入库记录");
        }
        if (InvStatusConstants.QC_PASSED.equals(qcResult) || InvStatusConstants.QC_CONCESSION.equals(qcResult))
        {
            PurchaseDetailIndex detailIndex = indexPurchaseDetails(details);

            // 合格/让步接收：按入库记录的仓库更新库存 + 写变动日志
            for (InvInboundRecord inbound : inboundRecords)
            {
                BigDecimal received = inbound.getQuantity() != null ? inbound.getQuantity() : BigDecimal.ZERO;
                if (received.compareTo(BigDecimal.ZERO) <= 0) continue;

                InvPurchaseDetail detail = resolveInboundPurchaseDetail(inbound, detailIndex);
                String itemType = InvItemTypes.normalize(inbound.getItemType());
                Long itemId = InvItemTypes.resolveItemId(itemType, inbound.getItemId(), inbound.getProductId());
                BigDecimal unitPrice = detail.getUnitPrice() != null ? detail.getUnitPrice() : BigDecimal.ZERO;
                BigDecimal incomingCost = received.multiply(unitPrice);
                InvStock stock = stockMapper.selectInvStockByItemShopWarehouseForUpdate(
                        itemType, itemId, inbound.getShopDeptId(), inbound.getWarehouseId());
                BigDecimal beforeQty = BigDecimal.ZERO;
                if (stock == null)
                {
                    stock = new InvStock();
                    stock.setItemType(itemType);
                    stock.setItemId(itemId);
                    stock.setProductId(inbound.getProductId());
                    stock.setShopDeptId(inbound.getShopDeptId());
                    stock.setWarehouseId(inbound.getWarehouseId());
                    stock.setCurrentQuantity(received);
                    stock.setAvailableQuantity(received);
                    stock.setCostPrice(unitPrice);
                    stock.setTotalCost(incomingCost);
                    stock.setLastInTime(new Date());
                    stock.setCreateBy(SecurityUtils.getUsername());
                    if (stockMapper.insertInvStock(stock) != 1)
                    {
                        throw new ServiceException("采购库存新增失败");
                    }
                }
                else
                {
                    beforeQty = stock.getCurrentQuantity();
                    if (stockMapper.addInvStockWithCost(stock.getStockId(), stock.getVersion(),
                            received, incomingCost, SecurityUtils.getUsername()) != 1)
                    {
                        throw new ServiceException("采购库存已变化，请刷新后重试");
                    }
                    stock = stockMapper.selectInvStockById(stock.getStockId());
                    if (stock == null)
                    {
                        throw new ServiceException("采购库存更新结果不存在");
                    }
                }
                InvStockLog log = new InvStockLog();
                log.setItemType(itemType);
                log.setItemId(itemId);
                log.setProductId(inbound.getProductId());
                log.setShopDeptId(inbound.getShopDeptId());
                log.setWarehouseId(inbound.getWarehouseId());
                log.setMovementType(InvStatusConstants.MOVEMENT_PURCHASE_IN);
                log.setBusinessType("purchase");
                log.setBusinessId(orderId);
                log.setBusinessNo(order.getOrderNo());
                log.setChangeQuantity(received);
                log.setBeforeQuantity(beforeQty);
                log.setAfterQuantity(stock.getCurrentQuantity());
                log.setCostPrice(stock.getCostPrice());
                log.setCreateBy(SecurityUtils.getUsername());
                log.setCreateTime(new Date());
                log.setRemark(InvStatusConstants.QC_PASSED.equals(qcResult) ? "采购入库-质检合格" : "采购入库-让步接收");
                if (stockLogMapper.insertInvStockLog(log) != 1)
                {
                    throw new ServiceException("采购库存日志新增失败");
                }
            }
        }
        else if (InvStatusConstants.QC_REJECTED.equals(qcResult))
        {
            // 不合格：只回退本批待检入库数量，保留历史已通过批次
            rollbackRejectedPendingReceipts(details, inboundRecords);
            java.util.Set<Long> affectedDetailIds = new java.util.HashSet<>();
            if (inboundRecords != null)
            {
                for (InvInboundRecord inbound : inboundRecords)
                {
                    if (inbound.getPurchaseDetailId() != null)
                    {
                        affectedDetailIds.add(inbound.getPurchaseDetailId());
                    }
                }
            }
            for (InvPurchaseDetail detail : details)
            {
                if (affectedDetailIds.contains(detail.getDetailId()))
                {
                    if (purchaseDetailMapper.updateInvPurchaseDetail(detail) != 1)
                    {
                        throw new ServiceException("采购明细拒收数量回退失败");
                    }
                }
            }
        }
        // 更新入库记录的质检信息
        if (inboundRecords != null)
        {
            for (InvInboundRecord inbound : inboundRecords)
            {
                inbound.setQcResult(qcResult);
                inbound.setQcUser(SecurityUtils.getUsername());
                inbound.setQcTime(new Date());
                inbound.setQcRemark(qcRemark);
                if (inboundRecordMapper.updateInvInboundRecord(inbound) != 1)
                {
                    throw new ServiceException("待检入库记录更新失败");
                }
            }
        }

        refreshPurchaseQualityConclusion(orderId, details);
    }

    static String resolveStatusAfterAcceptedQualityCheck(List<InvPurchaseDetail> details)
    {
        if (details == null || details.isEmpty())
        {
            return InvStatusConstants.SUBMITTED;
        }
        boolean allReceived = details.stream().allMatch(detail ->
        {
            BigDecimal quantity = detail.getQuantity() != null ? detail.getQuantity() : BigDecimal.ZERO;
            BigDecimal receivedQuantity = detail.getReceivedQuantity() != null
                    ? detail.getReceivedQuantity() : BigDecimal.ZERO;
            return receivedQuantity.compareTo(quantity) >= 0;
        });
        return allReceived ? InvStatusConstants.RECEIVED : InvStatusConstants.SUBMITTED;
    }

    static String resolveBusinessStage(InvPurchaseOrder order)
    {
        if (order == null)
        {
            return null;
        }
        if (InvStatusConstants.CANCELLED.equals(order.getStatus()))
        {
            return "cancelled";
        }
        if (InvStatusConstants.DRAFT.equals(order.getStatus()))
        {
            return "draft";
        }
        if (InvStatusConstants.QC_PENDING.equals(order.getQcStatus()))
        {
            return "pending_qc";
        }
        if (InvStatusConstants.QC_REJECTED.equals(order.getQcStatus()))
        {
            return "qc_rejected";
        }
        if (InvStatusConstants.RECEIVED.equals(order.getStatus()))
        {
            return "completed";
        }

        BigDecimal received = order.getReceivedQuantity() == null
                ? BigDecimal.ZERO : order.getReceivedQuantity();
        BigDecimal remaining = order.getRemainingQuantity();
        if (received.compareTo(BigDecimal.ZERO) > 0
                && (remaining == null || remaining.compareTo(BigDecimal.ZERO) > 0))
        {
            return "partial_received";
        }
        return "pending_receive";
    }

    private static List<InvPurchaseOrder> projectBusinessStages(List<InvPurchaseOrder> orders)
    {
        if (orders != null)
        {
            orders.forEach(InvPurchaseServiceImpl::projectBusinessStage);
        }
        return orders;
    }

    private static InvPurchaseOrder projectBusinessStage(InvPurchaseOrder order)
    {
        if (order != null)
        {
            order.setBusinessStage(resolveBusinessStage(order));
        }
        return order;
    }

    private void applyReturnableQuantities(InvPurchaseOrder order)
    {
        if (order == null || order.getOrderId() == null || order.getDetails() == null)
        {
            return;
        }
        for (InvPurchaseDetail detail : order.getDetails())
        {
            BigDecimal received = detail.getReceivedQuantity() == null
                    ? BigDecimal.ZERO : detail.getReceivedQuantity();
            BigDecimal returned = detail.getDetailId() == null
                    ? BigDecimal.ZERO
                    : purchaseReturnDetailMapper.sumHistoricalReturnQuantityByPurchaseDetailId(
                            order.getOrderId(), detail.getDetailId(), null);
            returned = returned == null ? BigDecimal.ZERO : returned;
            detail.setHistoricalReturnedQuantity(returned);
            detail.setReturnableQuantity(received.subtract(returned).max(BigDecimal.ZERO));
        }
    }

    static Long requireReceiveWarehouseId(Long warehouseId)
    {
        if (warehouseId == null || warehouseId == 0)
        {
            throw new ServiceException("收货仓库不能为空");
        }
        return warehouseId;
    }

    static List<InvInboundRecord> pendingQualityCheckRecords(List<InvInboundRecord> inboundRecords)
    {
        List<InvInboundRecord> pendingRecords = new ArrayList<>();
        if (inboundRecords == null || inboundRecords.isEmpty())
        {
            return pendingRecords;
        }
        for (InvInboundRecord inbound : inboundRecords)
        {
            if (inbound == null)
            {
                continue;
            }
            if (inbound.getQcResult() == null || InvStatusConstants.QC_PENDING.equals(inbound.getQcResult()))
            {
                pendingRecords.add(inbound);
            }
        }
        return pendingRecords;
    }

    static boolean isBatchBackedInbound(InvInboundRecord inbound)
    {
        return inbound != null
                && (inbound.getReceiptBatchId() != null || inbound.getReceiptBatchDetailId() != null);
    }

    static List<InvInboundRecord> historicalPendingInboundRecords(List<InvInboundRecord> inboundRecords)
    {
        List<InvInboundRecord> historicalPending = new ArrayList<>();
        for (InvInboundRecord inbound : pendingQualityCheckRecords(inboundRecords))
        {
            if (!isBatchBackedInbound(inbound))
            {
                historicalPending.add(inbound);
            }
        }
        return historicalPending;
    }

    static Map<String, Object> freezeReceivePayload(Long purchaseOrderId, InvReceiveRequest request)
    {
        List<InvReceiveItem> items = requireReceivePayloadItems(request);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("arrivedTime", request.getArrivedTime() == null
                ? null : request.getArrivedTime().getTime());
        payload.put("deliveryNoteNo", trimToNull(request.getDeliveryNoteNo()));
        payload.put("purchaseOrderId", purchaseOrderId);
        payload.put("remark", trimToNull(request.getRemark()));
        payload.put("supplierBatchNo", trimToNull(request.getSupplierBatchNo()));
        payload.put("warehouseId", request.getWarehouseId());
        List<InvReceiveItem> sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparing(InvReceiveItem::getDetailId));
        List<Map<String, Object>> frozenItems = new ArrayList<>();
        for (InvReceiveItem item : sorted)
        {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("detailId", item.getDetailId());
            line.put("receiveQuantity", canonicalQuantity(item.getReceiveQuantity()));
            frozenItems.add(line);
        }
        payload.put("items", frozenItems);
        return payload;
    }

    static List<InvReceiveItem> requireReceivePayloadItems(InvReceiveRequest request)
    {
        if (request == null || request.getItems() == null)
        {
            throw new ServiceException("收货明细不能为空");
        }
        List<InvReceiveItem> items = new ArrayList<>();
        Set<Long> seenDetailIds = new HashSet<>();
        for (InvReceiveItem item : request.getItems())
        {
            if (item == null)
            {
                throw new ServiceException("收货明细不能为空");
            }
            if (item.getDetailId() == null || !seenDetailIds.add(item.getDetailId()))
            {
                throw new ServiceException("收货明细不能重复");
            }
            items.add(item);
        }
        if (items.isEmpty())
        {
            throw new ServiceException("请至少录入一条有效收货数量");
        }
        return items;
    }

    static String canonicalQuantity(BigDecimal quantity)
    {
        return quantity == null ? null : quantity.stripTrailingZeros().toPlainString();
    }

    private boolean hasBatchBackedReceiptFacts(Long orderId, List<InvInboundRecord> inboundRecords)
    {
        if (inboundRecords != null)
        {
            for (InvInboundRecord inbound : inboundRecords)
            {
                if (isBatchBackedInbound(inbound))
                {
                    return true;
                }
            }
        }
        List<InvReceiptBatch> batches = receiptBatchMapper.selectByOrderIdForUpdate(orderId);
        return batches != null && !batches.isEmpty();
    }

    private void refreshPurchaseQualityConclusion(Long orderId, List<InvPurchaseDetail> purchaseDetails)
    {
        List<InvReceiptBatchDetail> allBatchDetails =
                receiptBatchDetailMapper.selectByOrderIdForUpdate(orderId);
        List<InvInboundRecord> allInbound = inboundRecordMapper.selectByOrderIdForUpdate(orderId);
        PurchaseQualityTotals totals = summarizePurchaseQualityFacts(allBatchDetails, allInbound);
        String nextQcStatus;
        String nextStatus;
        if (totals.anyPending)
        {
            nextQcStatus = InvStatusConstants.QC_PENDING;
            nextStatus = InvStatusConstants.SUBMITTED;
        }
        else
        {
            nextQcStatus = resolveQualityConclusion(totals.accepted, totals.rejected, totals.concession);
            nextStatus = totals.accepted.add(totals.concession).compareTo(BigDecimal.ZERO) > 0
                    ? resolveStatusAfterAcceptedQualityCheck(purchaseDetails)
                    : InvStatusConstants.SUBMITTED;
        }
        transitionPurchaseStatus(orderId, InvStatusConstants.SUBMITTED,
                nextStatus, nextQcStatus, "采购单质检状态已变化，请刷新后重试");
    }

    static PurchaseQualityTotals summarizePurchaseQualityFacts(
            List<InvReceiptBatchDetail> batchDetails, List<InvInboundRecord> inboundRecords)
    {
        PurchaseQualityTotals totals = new PurchaseQualityTotals();
        Map<Long, InvReceiptBatchDetail> detailsById = new HashMap<>();
        if (batchDetails != null)
        {
            for (InvReceiptBatchDetail detail : batchDetails)
            {
                if (detail == null || detail.getBatchDetailId() == null)
                {
                    continue;
                }
                detailsById.put(detail.getBatchDetailId(), detail);
                if (zero(detail.getPendingQuantity()).compareTo(BigDecimal.ZERO) > 0)
                {
                    totals.anyPending = true;
                }
                totals.accepted = totals.accepted.add(zero(detail.getAcceptedQuantity()));
                totals.rejected = totals.rejected.add(zero(detail.getRejectedQuantity()));
                totals.concession = totals.concession.add(zero(detail.getConcessionQuantity()));
            }
        }
        if (inboundRecords == null)
        {
            return totals;
        }
        for (InvInboundRecord inbound : inboundRecords)
        {
            if (inbound == null)
            {
                continue;
            }
            boolean hasBatchId = inbound.getReceiptBatchId() != null;
            boolean hasBatchDetailId = inbound.getReceiptBatchDetailId() != null;
            if (hasBatchId || hasBatchDetailId)
            {
                requireMatchingBatchBackedInbound(inbound, detailsById);
                continue;
            }
            if (inbound.getQcResult() == null
                    || InvStatusConstants.QC_PENDING.equals(inbound.getQcResult()))
            {
                totals.anyPending = true;
                continue;
            }
            BigDecimal accepted = zero(inbound.getAcceptedQuantity());
            BigDecimal rejected = zero(inbound.getRejectedQuantity());
            BigDecimal concession = zero(inbound.getConcessionQuantity());
            BigDecimal classified = accepted.add(rejected).add(concession);
            BigDecimal quantity = zero(inbound.getQuantity());
            if (classified.compareTo(BigDecimal.ZERO) > 0)
            {
                if (quantity.compareTo(BigDecimal.ZERO) > 0
                        && classified.compareTo(quantity) != 0)
                {
                    throw new ServiceException(
                            "历史无批次入库数量与质检分类不一致，已阻断自动修复 inboundId="
                                    + inbound.getInboundId());
                }
                totals.accepted = totals.accepted.add(accepted);
                totals.rejected = totals.rejected.add(rejected);
                totals.concession = totals.concession.add(concession);
                continue;
            }
            if (InvStatusConstants.QC_PASSED.equals(inbound.getQcResult()))
            {
                totals.accepted = totals.accepted.add(quantity);
            }
            else if (InvStatusConstants.QC_REJECTED.equals(inbound.getQcResult()))
            {
                totals.rejected = totals.rejected.add(quantity);
            }
            else if (InvStatusConstants.QC_CONCESSION.equals(inbound.getQcResult()))
            {
                totals.concession = totals.concession.add(quantity);
            }
            else
            {
                throw new ServiceException(
                        "历史无批次入库质检结论无法识别，已阻断自动修复 inboundId="
                                + inbound.getInboundId());
            }
        }
        return totals;
    }

    static void requireMatchingBatchBackedInbound(InvInboundRecord inbound,
            Map<Long, InvReceiptBatchDetail> detailsById)
    {
        if (inbound.getReceiptBatchId() == null || inbound.getReceiptBatchDetailId() == null)
        {
            throw new ServiceException(
                    "入库记录缺少收货批次或批次明细标识，已阻断自动修复 inboundId="
                            + inbound.getInboundId());
        }
        InvReceiptBatchDetail detail = detailsById.get(inbound.getReceiptBatchDetailId());
        if (detail == null)
        {
            throw new ServiceException(
                    "入库记录与收货批次明细不一致，已阻断自动修复 inboundId="
                            + inbound.getInboundId());
        }
        if (!Objects.equals(inbound.getReceiptBatchId(), detail.getBatchId()))
        {
            throw new ServiceException(
                    "入库记录收货批次与明细批次不一致，已阻断自动修复 inboundId="
                            + inbound.getInboundId());
        }
        if (inbound.getPurchaseDetailId() == null || detail.getPurchaseDetailId() == null
                || !Objects.equals(inbound.getPurchaseDetailId(), detail.getPurchaseDetailId()))
        {
            throw new ServiceException(
                    "入库记录采购明细与收货批次明细不一致，已阻断自动修复 inboundId="
                            + inbound.getInboundId());
        }
    }

    static final class PurchaseQualityTotals
    {
        private boolean anyPending;
        private BigDecimal accepted = BigDecimal.ZERO;
        private BigDecimal rejected = BigDecimal.ZERO;
        private BigDecimal concession = BigDecimal.ZERO;
    }

    static void rollbackRejectedPendingReceipts(List<InvPurchaseDetail> details, List<InvInboundRecord> pendingRecords)
    {
        if (details == null || details.isEmpty() || pendingRecords == null || pendingRecords.isEmpty())
        {
            return;
        }
        PurchaseDetailIndex detailIndex = indexPurchaseDetails(details);
        for (InvInboundRecord inbound : pendingRecords)
        {
            if (inbound == null)
            {
                continue;
            }
            BigDecimal rejectedQuantity = inbound.getQuantity() != null ? inbound.getQuantity() : BigDecimal.ZERO;
            if (rejectedQuantity.compareTo(BigDecimal.ZERO) <= 0)
            {
                continue;
            }
            InvPurchaseDetail detail = resolveInboundPurchaseDetail(inbound, detailIndex);
            BigDecimal receivedQuantity = detail.getReceivedQuantity() != null
                    ? detail.getReceivedQuantity() : BigDecimal.ZERO;
            BigDecimal nextReceivedQuantity = receivedQuantity.subtract(rejectedQuantity);
            detail.setReceivedQuantity(nextReceivedQuantity.compareTo(BigDecimal.ZERO) < 0
                    ? BigDecimal.ZERO : nextReceivedQuantity);
        }
    }

    private static PurchaseDetailIndex indexPurchaseDetails(List<InvPurchaseDetail> details)
    {
        PurchaseDetailIndex index = new PurchaseDetailIndex();
        for (InvPurchaseDetail detail : details)
        {
            if (detail == null)
            {
                continue;
            }
            if (detail.getDetailId() != null)
            {
                index.byDetailId.put(detail.getDetailId(), detail);
            }
            if (detail.getProductId() != null)
            {
                List<InvPurchaseDetail> productDetails = index.byProductId.computeIfAbsent(detail.getProductId(),
                        ignored -> new ArrayList<>());
                productDetails.add(detail);
            }
        }
        return index;
    }

    private static InvPurchaseDetail resolveInboundPurchaseDetail(InvInboundRecord inbound, PurchaseDetailIndex index)
    {
        if (inbound == null)
        {
            throw new ServiceException("入库记录不能为空");
        }
        if (inbound.getPurchaseDetailId() != null)
        {
            InvPurchaseDetail detail = index.byDetailId.get(inbound.getPurchaseDetailId());
            if (detail == null)
            {
                throw new ServiceException("入库记录明细不在采购单中: " + inbound.getPurchaseDetailId());
            }
            String inboundItemType = InvItemTypes.normalize(inbound.getItemType());
            Long inboundItemId = InvItemTypes.resolveItemId(inboundItemType, inbound.getItemId(), inbound.getProductId());
            String detailItemType = InvItemTypes.normalize(detail.getItemType());
            Long detailItemId = InvItemTypes.resolveItemId(detailItemType, detail.getItemId(), detail.getProductId());
            if (inboundItemId == null || detailItemId == null || !inboundItemType.equals(detailItemType)
                    || !inboundItemId.equals(detailItemId))
            {
                throw new ServiceException("入库记录物料与采购明细不一致: " + inboundItemType + "/" + inboundItemId);
            }
            return detail;
        }
        List<InvPurchaseDetail> productDetails = inbound.getProductId() == null
                ? null : index.byProductId.get(inbound.getProductId());
        if (productDetails == null || productDetails.isEmpty())
        {
            throw new ServiceException("入库记录商品不在采购明细中: " + inbound.getProductId());
        }
        if (productDetails.size() > 1)
        {
            throw new ServiceException("历史入库记录缺少采购明细ID，无法区分重复商品行: " + inbound.getProductId());
        }
        return productDetails.get(0);
    }

    private static class PurchaseDetailIndex
    {
        private final Map<Long, InvPurchaseDetail> byDetailId = new HashMap<>();
        private final Map<Long, List<InvPurchaseDetail>> byProductId = new HashMap<>();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelPurchase(Long orderId, Long selectedShopDeptId)
    {
        Long selectedWarehouseId = requireWarehouseContext(selectedShopDeptId, PURCHASE_WAREHOUSE_CONTEXT_MESSAGE);
        InvPurchaseOrder locked = requireLockedPurchase(orderId);
        assertPurchaseBelongsToSelectedWarehouse(locked, selectedWarehouseId);
        InvStateGuard.requireCancelableDocument(locked.getStatus());
        requireNoPurchaseReceiveActivity(locked);
        transitionPurchaseStatus(orderId, locked.getStatus(), InvStatusConstants.CANCELLED,
                null, "采购单状态已变化，请刷新后重试");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePurchase(Long orderId, Long selectedShopDeptId)
    {
        Long selectedWarehouseId = requireWarehouseContext(selectedShopDeptId, PURCHASE_WAREHOUSE_CONTEXT_MESSAGE);
        InvPurchaseOrder locked = requireLockedPurchase(orderId);
        assertPurchaseBelongsToSelectedWarehouse(locked, selectedWarehouseId);
        InvStateGuard.require(locked.getStatus(), Set.of(InvStatusConstants.DRAFT, InvStatusConstants.CANCELLED), "删除");
        List<InvPurchaseDetail> existingDetails = purchaseDetailMapper
                .selectInvPurchaseDetailByOrderIdForUpdate(orderId);
        if (existingDetails == null || existingDetails.isEmpty())
        {
            throw new ServiceException("采购明细不存在，请刷新后重试");
        }
        inboundRecordMapper.deleteByOrderId(orderId);
        if (purchaseDetailMapper.deleteInvPurchaseDetailByOrderId(orderId) != existingDetails.size())
        {
            throw new ServiceException("采购明细已变化，请刷新后重试");
        }
        if (purchaseOrderMapper.deleteInvPurchaseOrderByIds(new Long[]{orderId}) != 1)
        {
            throw new ServiceException("采购单已变化，请刷新后重试");
        }
    }

    private InvPurchaseOrder requireLockedPurchase(Long orderId)
    {
        if (orderId == null || orderId <= 0)
        {
            throw new ServiceException("采购单标识无效");
        }
        InvPurchaseOrder locked = purchaseOrderMapper.selectInvPurchaseOrderByIdForUpdate(orderId);
        if (locked == null)
        {
            throw new ServiceException("采购单不存在或已被删除");
        }
        return locked;
    }

    private void transitionPurchaseStatus(Long orderId, String expectedStatus,
            String status, String qcStatus, String failureMessage)
    {
        if (purchaseOrderMapper.transitionPurchaseStatus(orderId, expectedStatus, status,
                qcStatus, SecurityUtils.getUsername()) != 1)
        {
            throw new ServiceException(failureMessage);
        }
    }

    private InvPurchaseOrder assertAndGetScopedPurchase(Long orderId, Long selectedShopDeptId)
    {
        InvPurchaseOrder db = purchaseOrderMapper.selectInvPurchaseOrderById(orderId);
        if (db == null)
        {
            throw new ServiceException("采购单不存在");
        }
        assertShopVisible(db.getShopDeptId(), selectedShopDeptId, "无权访问该店铺采购单");
        return db;
    }

    private Long requireAndValidateReceiveWarehouseId(InvReceiveRequest receiveRequest, Long selectedWarehouseId)
    {
        Long warehouseId = requireReceiveWarehouseId(receiveRequest == null ? null : receiveRequest.getWarehouseId());
        assertDeptType(warehouseId, DEPT_TYPE_WAREHOUSE, PURCHASE_WAREHOUSE_MATCH_MESSAGE);
        if (!selectedWarehouseId.equals(warehouseId))
        {
            throw new ServiceException(PURCHASE_WAREHOUSE_MATCH_MESSAGE);
        }
        return warehouseId;
    }

    private void assertPurchaseBelongsToSelectedWarehouse(InvPurchaseOrder order, Long selectedWarehouseId)
    {
        assertDeptType(order.getShopDeptId(), DEPT_TYPE_WAREHOUSE, PURCHASE_WAREHOUSE_CONTEXT_MESSAGE);
        if (!selectedWarehouseId.equals(order.getShopDeptId()))
        {
            throw new ServiceException("只能操作当前仓库采购单");
        }
    }

    private static void requireNoPurchaseReceiveActivity(InvPurchaseOrder order)
    {
        if (order == null)
        {
            throw new ServiceException("采购单不存在");
        }
        if (trimToNull(order.getQcStatus()) != null)
        {
            throw new ServiceException("采购单已发生收货，不能取消");
        }
        BigDecimal receivedQuantity = order.getReceivedQuantity() != null
                ? order.getReceivedQuantity() : BigDecimal.ZERO;
        if (receivedQuantity.compareTo(BigDecimal.ZERO) > 0)
        {
            throw new ServiceException("采购单已发生收货，不能取消");
        }
    }

    private static String trimToNull(String value)
    {
        if (value == null)
        {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static BigDecimal zero(BigDecimal value)
    {
        return value != null ? value : BigDecimal.ZERO;
    }

    private static String displayItemName(InvPurchaseDetail detail)
    {
        if (detail == null)
        {
            return "-";
        }
        String itemName = trimToNull(detail.getItemName());
        return itemName != null ? itemName
                : (trimToNull(detail.getProductName()) != null ? detail.getProductName()
                    : String.valueOf(detail.getDetailId()));
    }

    private String generateOrderNo(String prefix)
    {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        numberSequenceMapper.insertOrUpdateSequence(prefix, dateStr, 0, prefix);
        numberSequenceMapper.incrementAndGetSequence(prefix, dateStr);
        Long seq = numberSequenceMapper.selectLastInsertId();
        return prefix + dateStr + String.format("%04d", seq);
    }
}

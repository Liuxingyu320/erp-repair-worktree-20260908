package com.erp.inventory.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSON;
import com.erp.approval.api.domain.ApprovalStartRequest;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.auth.AuthUtil;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.inventory.constant.InvStatusConstants;
import com.erp.inventory.domain.InvStock;
import com.erp.inventory.domain.InvStockCheck;
import com.erp.inventory.domain.InvStockCheckApprovalStartOutbox;
import com.erp.inventory.domain.InvStockCheckDetail;
import com.erp.inventory.domain.InvStockCheckApprovalInstance;
import com.erp.inventory.domain.dto.InvStockCheckAssignmentRequest;
import com.erp.inventory.domain.vo.InvStockCheckAdjustmentResult;
import com.erp.inventory.domain.vo.InvStockCheckCounterCandidate;
import com.erp.inventory.mapper.InvNumberSequenceMapper;
import com.erp.inventory.mapper.InvStockCheckDetailMapper;
import com.erp.inventory.mapper.InvStockCheckMapper;
import com.erp.inventory.mapper.InvStockMapper;
import com.erp.inventory.service.IInvStockCheckService;
import com.erp.inventory.service.IInvStockCheckApprovalService;

@Service
public class InvStockCheckServiceImpl extends InvBaseService implements IInvStockCheckService
{
    private static final Set<String> CHECK_SCOPES = Set.of("all", "category", "selected", "sample");
    private static final BigDecimal DEFAULT_RECOUNT_THRESHOLD = BigDecimal.ZERO;
    private static final int MAX_SAMPLE_SIZE = 1000;

    @Autowired
    private InvStockCheckMapper checkMapper;

    @Autowired
    private InvStockCheckDetailMapper checkDetailMapper;

    @Autowired
    private InvStockMapper stockMapper;

    @Autowired
    private InvNumberSequenceMapper numberSequenceMapper;

    @Autowired
    private InvStockCheckAdjustmentService adjustmentService;

    @Autowired
    private IInvStockCheckApprovalService approvalService;

    @Autowired
    private InventoryUnifiedApprovalService unifiedApprovalService;

    @Autowired
    private InvStockCheckApprovalStartOutboxService approvalStartOutboxService;

    @Autowired
    private InvStockCheckApprovalStartAfterCommitTrigger approvalStartAfterCommitTrigger;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvStockCheck createCheck(InvStockCheck stockCheck, Long selectedShopDeptId)
    {
        if (stockCheck == null)
        {
            throw new ServiceException("盘点参数不能为空");
        }
        Long shopDeptId = resolveAndValidateShopDept(selectedShopDeptId);
        Long inventoryDeptId = resolveInventoryDeptId(stockCheck.getWarehouseId(), shopDeptId);
        assertWritableInventoryDept(inventoryDeptId, selectedShopDeptId, "只能盘点当前组织库存");
        normalizeCreateConfig(stockCheck);
        applyCounterSnapshot(stockCheck, inventoryDeptId);
        stockCheck.setCheckNo(generateOrderNo("SC"));
        stockCheck.setShopDeptId(inventoryDeptId);
        stockCheck.setWarehouseId(inventoryDeptId);
        stockCheck.setCheckDate(stockCheck.getCheckDate() != null ? stockCheck.getCheckDate() : new Date());
        stockCheck.setStatus(InvStatusConstants.DRAFT);
        stockCheck.setCreateBy(currentUsername());
        checkMapper.insertInvStockCheck(stockCheck);

        Set<String> requestedProductIds = "selected".equals(stockCheck.getCheckScope())
                ? resolveRequestedProductIds(stockCheck.getDetails()) : Collections.emptySet();

        // 拉取该店铺/仓库当前库存快照作为盘点明细。
        List<Map<String, Object>> rows = new ArrayList<>(
                checkDetailMapper.selectStockForCheck(inventoryDeptId, inventoryDeptId));
        if (rows.isEmpty())
        {
            throw new ServiceException("当前店铺/仓库无库存数据");
        }
        rows = filterSnapshotRows(stockCheck, rows, requestedProductIds);

        List<InvStockCheckDetail> details = new ArrayList<>();
        for (Map<String, Object> row : rows)
        {
            Long productId = toLong(row.get("productId"));
            String productName = (String) row.get("productName");
            String productCode = (String) row.get("productCode");
            String unit = (String) row.get("unit");
            String spec = (String) row.get("spec");
            BigDecimal currentQty = toDecimal(row.get("currentQuantity"));
            BigDecimal costPrice = toDecimal(row.get("costPrice"));

            InvStockCheckDetail d = new InvStockCheckDetail();
            d.setCheckId(stockCheck.getCheckId());
            String itemType = row.get("itemType") == null ? "product" : row.get("itemType").toString();
            Long itemId = toLong(row.get("itemId"));
            if (itemId == null) itemId = productId;
            stockCheckItemKey(itemType, itemId);
            d.setItemType(itemType);
            d.setItemId(itemId);
            d.setProductId("product".equals(itemType) ? itemId : null);
            d.setProductName(productName);
            d.setProductCode(productCode);
            d.setUnit(unit);
            d.setSpec(spec);
            d.setBookQty(currentQty);
            d.setCostPrice(costPrice);
            d.setDiffQty(BigDecimal.ZERO);
            d.setDiffType("none");
            d.setRecountRequired("0");
            details.add(d);
        }
        checkDetailMapper.batchInsertInvStockCheckDetail(details);

        return getCheckDetail(stockCheck.getCheckId(), selectedShopDeptId);
    }

    private void normalizeCreateConfig(InvStockCheck stockCheck)
    {
        String scope = stockCheck.getCheckScope();
        if (scope == null || scope.isBlank())
        {
            scope = stockCheck.getDetails() == null || stockCheck.getDetails().isEmpty()
                    ? "all" : "selected";
        }
        scope = scope.trim().toLowerCase();
        if (!CHECK_SCOPES.contains(scope))
        {
            throw new ServiceException("不支持的盘点范围: " + scope);
        }
        stockCheck.setCheckScope(scope);
        stockCheck.setBlindCheck("1".equals(stockCheck.getBlindCheck()) ? "1" : "0");
        if (stockCheck.getCounterUserId() == null)
        {
            throw new ServiceException("请选择盘点人");
        }
        validateAssignmentDeadline(stockCheck.getDeadline());
        stockCheck.setRecountThreshold(normalizeRecountThreshold(stockCheck.getRecountThreshold()));

        if ("category".equals(scope) && stockCheck.getCategoryId() == null)
        {
            throw new ServiceException("请选择盘点商品分类");
        }
        if (!"category".equals(scope))
        {
            stockCheck.setCategoryId(null);
        }
        if ("sample".equals(scope))
        {
            Integer sampleSize = stockCheck.getSampleSize();
            if (sampleSize == null || sampleSize <= 0 || sampleSize > MAX_SAMPLE_SIZE)
            {
                throw new ServiceException("抽盘数量必须在1到" + MAX_SAMPLE_SIZE + "之间");
            }
        }
        else
        {
            stockCheck.setSampleSize(null);
        }
    }

    @Override
    public List<InvStockCheckCounterCandidate> selectCounterCandidates(Long warehouseId,
            String keyword, Long selectedShopDeptId)
    {
        Long shopDeptId = resolveAndValidateShopDept(selectedShopDeptId);
        Long inventoryDeptId = resolveInventoryDeptId(warehouseId, shopDeptId);
        assertWritableInventoryDept(inventoryDeptId, selectedShopDeptId, "只能查询当前组织盘点人");
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        if (normalizedKeyword != null && normalizedKeyword.length() > 64)
        {
            normalizedKeyword = normalizedKeyword.substring(0, 64);
        }
        if (normalizedKeyword != null && normalizedKeyword.isEmpty())
        {
            normalizedKeyword = null;
        }
        return checkMapper.selectCounterCandidates(inventoryDeptId, normalizedKeyword);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvStockCheck assignCounter(Long checkId, InvStockCheckAssignmentRequest assignment,
            Long selectedShopDeptId)
    {
        if (assignment == null || assignment.getCounterUserId() == null)
        {
            throw new ServiceException("请选择盘点人");
        }
        validateAssignmentDeadline(assignment.getDeadline());
        InvStockCheck check = assertAndGetScopedCheck(checkId, selectedShopDeptId);
        assertWritableInventoryDept(check.getShopDeptId(), selectedShopDeptId, "只能调整当前组织盘点指派");
        InvStateGuard.requireStockCheckAssignment(check.getStatus());

        InvStockCheck locked = checkMapper.selectInvStockCheckByIdForUpdate(checkId);
        if (locked == null)
        {
            throw new ServiceException("盘点单不存在");
        }
        InvStateGuard.requireStockCheckAssignment(locked.getStatus());
        InvStockCheckCounterCandidate candidate = requireCounterCandidate(
                locked.getShopDeptId(), assignment.getCounterUserId());

        InvStockCheck update = new InvStockCheck();
        update.setCheckId(checkId);
        update.setCounterUserId(candidate.getUserId());
        update.setCounterName(resolveCounterDisplayName(candidate));
        update.setDeadline(assignment.getDeadline());
        update.setUpdateBy(currentUsername());
        checkMapper.updateInvStockCheck(update);
        return getCheckDetail(checkId, selectedShopDeptId);
    }

    private List<Map<String, Object>> filterSnapshotRows(InvStockCheck stockCheck,
            List<Map<String, Object>> rows, Set<String> requestedProductIds)
    {
        String scope = stockCheck.getCheckScope();
        if ("all".equals(scope))
        {
            return rows;
        }
        List<Map<String, Object>> selectedRows = new ArrayList<>();
        if ("selected".equals(scope))
        {
            for (Map<String, Object> row : rows)
            {
                if (requestedProductIds.contains(stockCheckItemKey(
                        row.get("itemType") == null ? "product" : row.get("itemType").toString(),
                        toLong(row.get("itemId")) == null ? toLong(row.get("productId")) : toLong(row.get("itemId")))))
                {
                    selectedRows.add(row);
                }
            }
        }
        else if ("category".equals(scope))
        {
            for (Map<String, Object> row : rows)
            {
                if (matchesCategory(row, stockCheck.getCategoryId()))
                {
                    selectedRows.add(row);
                }
            }
        }
        else
        {
            selectedRows.addAll(rows);
            Collections.shuffle(selectedRows, new Random(stockCheck.getCheckNo().hashCode()));
            if (selectedRows.size() > stockCheck.getSampleSize())
            {
                selectedRows = new ArrayList<>(selectedRows.subList(0, stockCheck.getSampleSize()));
            }
        }
        if (selectedRows.isEmpty())
        {
            throw new ServiceException("所选盘点范围当前无库存数据");
        }
        return selectedRows;
    }

    private boolean matchesCategory(Map<String, Object> row, Long categoryId)
    {
        Long rowCategoryId = toLong(row.get("categoryId"));
        if (categoryId.equals(rowCategoryId))
        {
            return true;
        }
        String ancestors = row.get("categoryAncestors") == null
                ? "" : row.get("categoryAncestors").toString();
        for (String ancestor : ancestors.split(","))
        {
            if (String.valueOf(categoryId).equals(ancestor.trim()))
            {
                return true;
            }
        }
        return false;
    }

    private String stockCheckItemKey(String type, Long id)
    {
        if (!Set.of("product", "oe", "gift").contains(type) || id == null || id <= 0)
        {
            throw new ServiceException("盘点物料身份不完整，请重新选择盘点范围");
        }
        return type + ":" + id;
    }

    private Set<String> resolveRequestedProductIds(List<InvStockCheckDetail> details)
    {
        Set<String> productIds = new LinkedHashSet<>();
        if (details == null || details.isEmpty())
        {
            throw new ServiceException("请选择盘点商品");
        }
        for (InvStockCheckDetail detail : details)
        {
            if (detail != null)
            {
                productIds.add(stockCheckItemKey(detail.getItemType(), detail.getItemId()));
            }
        }
        if (productIds.isEmpty())
        {
            throw new ServiceException("请选择盘点商品");
        }
        return productIds;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvStockCheck saveDraft(InvStockCheck stockCheck, Long selectedShopDeptId)
    {
        if (stockCheck == null || stockCheck.getCheckId() == null)
        {
            throw new ServiceException("盘点单ID不能为空");
        }
        InvStockCheck check = assertAndGetScopedCheck(stockCheck.getCheckId(), selectedShopDeptId);
        assertWritableInventoryDept(check.getShopDeptId(), selectedShopDeptId, "只能盘点当前组织库存");
        InvStateGuard.requireStockCheckInput(check.getStatus());

        InvStockCheck locked = checkMapper.selectInvStockCheckByIdForUpdate(stockCheck.getCheckId());
        InvStateGuard.requireStockCheckInput(locked.getStatus());
        assertCounterOrManager(locked);

        InvStockCheck update = new InvStockCheck();
        update.setCheckId(stockCheck.getCheckId());
        update.setCheckDate(stockCheck.getCheckDate());
        BigDecimal recountThreshold = stockCheck.getRecountThreshold() == null
                ? normalizeRecountThreshold(locked.getRecountThreshold())
                : normalizeRecountThreshold(stockCheck.getRecountThreshold());
        update.setRecountThreshold(recountThreshold);
        update.setRemark(stockCheck.getRemark());
        update.setUpdateBy(SecurityUtils.getUsername());
        saveDraftDetails(locked, stockCheck.getDetails(), false, recountThreshold);
        checkMapper.updateInvStockCheck(update);
        return getCheckDetail(stockCheck.getCheckId(), selectedShopDeptId);
    }

    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public InvStockCheck getCheckDetail(Long checkId, Long selectedShopDeptId)
    {
        InvStockCheck check = assertAndGetScopedCheck(checkId, selectedShopDeptId);
        List<InvStockCheckDetail> details = checkDetailMapper.selectInvStockCheckDetailByCheckId(checkId);
        InvStockCheckRestartPolicy.decorate(check, details);
        if (isBlindInput(check))
        {
            check.setLastInvalidDetailSnapshot(null);
            hideBlindSnapshot(details);
        }
        check.setDetails(details);
        return check;
    }

    @Override
    public List<InvStockCheck> selectCheckList(InvStockCheck stockCheck, Long selectedShopDeptId)
    {
        appendShopScope(stockCheck, selectedShopDeptId);
        List<InvStockCheck> checks = checkMapper.selectInvStockCheckList(stockCheck);
        for (InvStockCheck check : checks)
        {
            if (isBlindInput(check))
            {
                check.setLastInvalidDetailSnapshot(null);
                check.setProfitItemCount(null);
                check.setLossItemCount(null);
                check.setTotalDiffQuantity(null);
            }
        }
        return checks;
    }

    @Override
    public List<InvStockCheck> selectApprovalTodoList(InvStockCheck stockCheck,
            Long selectedShopDeptId)
    {
        if (stockCheck == null)
        {
            stockCheck = new InvStockCheck();
        }
        appendShopScope(stockCheck, selectedShopDeptId);
        stockCheck.getParams().put("approvalUserId", SecurityUtils.getUserId());
        return checkMapper.selectInvStockCheckApprovalTodoList(stockCheck);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvStockCheck inputActualQty(Long checkId, InvStockCheck stockCheck, Long selectedShopDeptId)
    {
        if (stockCheck == null)
        {
            stockCheck = new InvStockCheck();
        }
        stockCheck.setCheckId(checkId);
        return saveDraft(stockCheck, selectedShopDeptId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitCheck(Long checkId, List<InvStockCheckDetail> details, Long selectedShopDeptId)
    {
        InvStockCheck check = assertAndGetScopedCheck(checkId, selectedShopDeptId);
        assertWritableInventoryDept(check.getShopDeptId(), selectedShopDeptId, "只能盘点当前组织库存");
        InvStateGuard.requireStockCheckSubmit(check.getStatus());

        InvStockCheck locked = checkMapper.selectInvStockCheckByIdForUpdate(checkId);
        InvStateGuard.requireStockCheckSubmit(locked.getStatus());
        assertCounterOrManager(locked);

        if (details != null && !details.isEmpty())
        {
            saveDraftDetails(locked, details, false,
                    normalizeRecountThreshold(locked.getRecountThreshold()));
        }
        List<InvStockCheckDetail> lockedDetails =
                checkDetailMapper.selectInvStockCheckDetailByCheckIdForUpdate(checkId);
        ensureAllActualQtyEntered(lockedDetails);
        InvStockCheckAdjustmentResult evaluation =
                adjustmentService.evaluate(locked, lockedDetails, false);

        InvStockCheck update = new InvStockCheck();
        update.setCheckId(checkId);
        Date now = new Date();
        Long userId = currentUserId();
        String username = currentUsername();
        update.setSubmittedUserId(userId);
        update.setSubmittedBy(username);
        update.setSubmittedTime(now);
        update.setUpdateBy(username);
        ApprovalStartRequest nativeRequest = null;
        if (evaluation.hasSnapshotChanges())
        {
            update.setStatus(InvStatusConstants.INVALIDATED);
            update.setLastInvalidReason("库存快照已变化，需要重新盘点");
            update.setLastInvalidDetailSnapshot(JSON.toJSONString(evaluation.getSnapshotChanges()));
            update.setLastInvalidatedTime(now);
        }
        else if (!hasDifference(lockedDetails))
        {
            update.setStatus(InvStatusConstants.COMPLETED);
        }
        else
        {
            update.setStatus(InvStatusConstants.PENDING_APPROVAL);
            if (unifiedApprovalService.useNativeStockCheck(locked))
            {
                int nextRound = (locked.getApprovalRound() == null
                        ? 0 : locked.getApprovalRound()) + 1;
                nativeRequest = unifiedApprovalService
                        .buildStockCheckStartRequest(locked, lockedDetails,
                                nextRound);
                update.setApprovalRound(nativeRequest.getBusinessRound());
                update.setApprovalEngine(
                        InventoryUnifiedApprovalService.ENGINE_NATIVE);
            }
            else
            {
                InvStockCheckApprovalInstance instance =
                        approvalService.createPendingApproval(locked,
                                lockedDetails);
                update.setApprovalInstanceId(instance.getInstanceId());
                update.setApprovalRound(instance.getRoundNo());
                update.setApprovalEngine(
                        InventoryUnifiedApprovalService.ENGINE_LEGACY);
            }
        }
        checkMapper.updateInvStockCheck(update);
        if (nativeRequest != null)
        {
            InvStockCheck submitted = checkMapper
                    .selectInvStockCheckByIdForUpdate(checkId);
            InvStockCheckApprovalStartOutbox outbox =
                    approvalStartOutboxService.enqueue(submitted,
                            nativeRequest, username);
            approvalStartAfterCommitTrigger.trigger(outbox.getOutboxId());
        }
    }

    private void saveDraftDetails(InvStockCheck locked, List<InvStockCheckDetail> details,
            boolean requireAnyDetail, BigDecimal recountThreshold)
    {
        if (details == null || details.isEmpty())
        {
            if (requireAnyDetail)
            {
                throw new ServiceException("请录入盘点明细");
            }
            return;
        }

        List<InvStockCheckDetail> existingDetails = checkDetailMapper.selectInvStockCheckDetailByCheckIdForUpdate(locked.getCheckId());
        InvStockCheckRestartPolicy.validateInputs(locked, details, existingDetails);
        java.util.Map<Long, InvStockCheckDetail> detailMap = new java.util.HashMap<>();
        for (InvStockCheckDetail d : existingDetails)
        {
            detailMap.put(d.getDetailId(), d);
        }

        for (InvStockCheckDetail input : details)
        {
            InvStockCheckDetail db = detailMap.get(input.getDetailId());
            if (db == null)
            {
                throw new ServiceException("明细ID不存在: " + input.getDetailId());
            }
            if (input.getActualQty() != null && input.getActualQty().compareTo(BigDecimal.ZERO) < 0)
            {
                throw new ServiceException("实盘数量不能为负");
            }
            if (input.getRecountQty() != null && input.getRecountQty().compareTo(BigDecimal.ZERO) < 0)
            {
                throw new ServiceException("复盘数量不能为负");
            }
            boolean actualUnchanged = db.getActualQty() != null && input.getActualQty() != null
                    && db.getActualQty().compareTo(input.getActualQty()) == 0;
            boolean recountRequired = requiresRecount(
                    db.getBookQty(), input.getActualQty(), recountThreshold);
            boolean preserveRecount = recountRequired && actualUnchanged && !input.isRecountQtySpecified();
            BigDecimal recountQty = !recountRequired ? null
                    : (preserveRecount ? db.getRecountQty() : input.getRecountQty());
            db.setActualQty(input.getActualQty());
            db.setRecountRequired(recountRequired ? "1" : "0");
            db.setRecountQty(recountQty);
            if (!preserveRecount)
            {
                db.setRecountBy(recountQty == null ? null : SecurityUtils.getUsername());
                db.setRecountTime(recountQty == null ? null : new Date());
            }
            BigDecimal finalQty = recountQty == null ? input.getActualQty() : recountQty;
            BigDecimal diffQty = calculateDiffQty(finalQty, db.getBookQty());
            db.setDiffQty(diffQty);
            db.setDiffType(resolveDiffType(diffQty));
            checkDetailMapper.updateInvStockCheckDetail(db);
        }
    }


    private Long resolveInventoryDeptId(Long warehouseId, Long fallbackDeptId)
    {
        if (warehouseId != null && warehouseId != 0)
        {
            return warehouseId;
        }
        return fallbackDeptId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelCheck(Long checkId, Long selectedShopDeptId)
    {
        InvStockCheck check = assertAndGetScopedCheck(checkId, selectedShopDeptId);
        InvStateGuard.requireStockCheckCancelable(check.getStatus());
        InvStockCheck locked = checkMapper.selectInvStockCheckByIdForUpdate(checkId);
        InvStateGuard.requireStockCheckCancelable(locked.getStatus());
        if (InvStatusConstants.PENDING_APPROVAL.equals(locked.getStatus()))
        {
            if (InventoryUnifiedApprovalService.ENGINE_NATIVE.equalsIgnoreCase(
                    locked.getApprovalEngine() == null ? ""
                            : locked.getApprovalEngine()))
            {
                throw new ServiceException("该盘点单正在统一审批中，请使用撤回审批操作");
            }
            approvalService.cancelRunning(checkId);
        }
        InvStockCheck update = new InvStockCheck();
        update.setCheckId(checkId);
        update.setStatus(InvStatusConstants.CANCELLED);
        update.setUpdateBy(SecurityUtils.getUsername());
        checkMapper.updateInvStockCheck(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String withdrawApproval(Long checkId, Long selectedShopDeptId)
    {
        assertAndGetScopedCheck(checkId, selectedShopDeptId);
        InvStockCheck locked = checkMapper.selectInvStockCheckByIdForUpdate(checkId);
        if (locked == null)
        {
            throw new ServiceException("盘点单不存在");
        }
        if (!InvStatusConstants.PENDING_APPROVAL.equals(locked.getStatus())
                || !InventoryUnifiedApprovalService.ENGINE_NATIVE
                        .equalsIgnoreCase(locked.getApprovalEngine() == null
                                ? "" : locked.getApprovalEngine()))
        {
            throw new ServiceException("只有统一审批中的盘点单可以撤回");
        }
        if (!Objects.equals(SecurityUtils.getUserId(),
                locked.getSubmittedUserId()))
        {
            throw new ServiceException("只有盘点审批申请人可以撤回");
        }
        unifiedApprovalService.withdraw(
                InventoryUnifiedApprovalService.STOCK_CHECK, checkId,
                locked.getApprovalRound(), locked.getApprovalInstanceId(),
                "申请人撤回盘点审批");
        return "撤回请求已提交，审批结果同步后盘点单将恢复为草稿";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCheck(Long[] checkIds, Long selectedShopDeptId)
    {
        for (Long checkId : checkIds)
        {
            assertAndGetScopedCheck(checkId, selectedShopDeptId);
            InvStockCheck check = checkMapper.selectInvStockCheckByIdForUpdate(checkId);
            if (check == null)
            {
                throw new ServiceException("盘点单不存在");
            }
            InvStateGuard.require(check.getStatus(),
                    java.util.Set.of(InvStatusConstants.DRAFT), "删除草稿");
            if (check.getApprovalRound() != null && check.getApprovalRound() > 0)
            {
                throw new ServiceException("该盘点单已有审批记录，不能物理删除");
            }
            checkDetailMapper.deleteInvStockCheckDetailByCheckId(checkId);
        }
        checkMapper.deleteInvStockCheckByIds(checkIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restartCheck(Long checkId, Long selectedShopDeptId)
    {
        InvStockCheck check = assertAndGetScopedCheck(checkId, selectedShopDeptId);
        assertWritableInventoryDept(check.getShopDeptId(), selectedShopDeptId, "只能盘点当前组织库存");
        InvStateGuard.requireStockCheckRestart(check.getStatus());
        InvStockCheck locked = checkMapper.selectInvStockCheckByIdForUpdate(checkId);
        InvStateGuard.requireStockCheckRestart(locked.getStatus());
        assertCounterOrManager(locked);
        List<InvStockCheckDetail> details =
                checkDetailMapper.selectInvStockCheckDetailByCheckIdForUpdate(checkId);
        if (details == null || details.isEmpty())
        {
            throw new ServiceException("盘点单无明细");
        }
        Set<Long> changed = InvStockCheckRestartPolicy.invalidatedIds(locked);
        Map<Long, BigDecimal> quantities = new LinkedHashMap<>();
        Map<Long, BigDecimal> costs = new LinkedHashMap<>();
        for (InvStockCheckDetail detail : details)
        {
            InvStock stock = stockMapper.selectInvStockByItemShopWarehouseForUpdate(
                    detail.getItemType(), detail.getItemId(), locked.getShopDeptId(), locked.getWarehouseId());
            BigDecimal currentQty = stock == null || stock.getCurrentQuantity() == null
                    ? BigDecimal.ZERO : stock.getCurrentQuantity();
            BigDecimal costPrice = stock == null || stock.getCostPrice() == null
                    ? (detail.getCostPrice() == null ? BigDecimal.ZERO : detail.getCostPrice())
                    : stock.getCostPrice();
            quantities.put(detail.getDetailId(), currentQty);
            costs.put(detail.getDetailId(), costPrice);
            BigDecimal oldQty = detail.getBookQty() == null ? BigDecimal.ZERO : detail.getBookQty();
            if (oldQty.compareTo(currentQty) != 0) changed.add(detail.getDetailId());
        }
        String history = InvStockCheckRestartPolicy.appendRound(locked, details, changed, SecurityUtils.getUsername());
        for (InvStockCheckDetail detail : details)
        {
            if (changed.contains(detail.getDetailId()))
                checkDetailMapper.resetSnapshot(detail.getDetailId(), quantities.get(detail.getDetailId()), costs.get(detail.getDetailId()));
            else
                checkDetailMapper.refreshSnapshotCost(detail.getDetailId(), costs.get(detail.getDetailId()));
        }
        InvStockCheck update = new InvStockCheck();
        update.setCheckId(checkId);
        update.setRestartReferenceSnapshot(history);
        update.setStatus(InvStatusConstants.DRAFT);
        update.setUpdateBy(SecurityUtils.getUsername());
        checkMapper.updateInvStockCheck(update);
    }

    private InvStockCheck assertAndGetScopedCheck(Long checkId, Long selectedShopDeptId)
    {
        InvStockCheck db = checkMapper.selectInvStockCheckById(checkId);
        if (db == null)
        {
            throw new ServiceException("盘点单不存在");
        }
        assertShopVisible(db.getShopDeptId(), selectedShopDeptId, "无权访问该店铺盘点单");
        return db;
    }

    private void applyCounterSnapshot(InvStockCheck stockCheck, Long inventoryDeptId)
    {
        InvStockCheckCounterCandidate candidate = requireCounterCandidate(
                inventoryDeptId, stockCheck.getCounterUserId());
        stockCheck.setCounterUserId(candidate.getUserId());
        stockCheck.setCounterName(resolveCounterDisplayName(candidate));
    }

    private InvStockCheckCounterCandidate requireCounterCandidate(Long inventoryDeptId, Long userId)
    {
        InvStockCheckCounterCandidate candidate = checkMapper.selectCounterCandidate(inventoryDeptId, userId);
        if (candidate == null)
        {
            throw new ServiceException("所选盘点人账号无效、无当前库存组织权限或缺少盘点提交权限");
        }
        return candidate;
    }

    private String resolveCounterDisplayName(InvStockCheckCounterCandidate candidate)
    {
        if (candidate.getDisplayName() != null && !candidate.getDisplayName().isBlank())
        {
            return candidate.getDisplayName().trim();
        }
        return candidate.getUserName();
    }

    private void validateAssignmentDeadline(Date deadline)
    {
        if (deadline == null)
        {
            throw new ServiceException("请选择盘点截止时间");
        }
        if (!deadline.after(new Date()))
        {
            throw new ServiceException("盘点截止时间必须晚于当前时间");
        }
    }

    private void assertCounterOrManager(InvStockCheck check)
    {
        if (check != null && Objects.equals(check.getCounterUserId(), currentUserId()))
        {
            return;
        }
        if (hasPermission("inv:stockCheck:edit"))
        {
            return;
        }
        throw new ServiceException("仅指定盘点人可录入、提交或重新盘点");
    }

    protected Long currentUserId()
    {
        return SecurityUtils.getUserId();
    }

    protected String currentUsername()
    {
        return SecurityUtils.getUsername();
    }

    protected boolean hasPermission(String permission)
    {
        return AuthUtil.hasPermi(permission);
    }

    private String generateOrderNo(String prefix)
    {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        numberSequenceMapper.insertOrUpdateSequence(prefix, dateStr, 0, prefix);
        numberSequenceMapper.incrementAndGetSequence(prefix, dateStr);
        Long seq = numberSequenceMapper.selectLastInsertId();
        return prefix + dateStr + String.format("%04d", seq);
    }

    private void ensureAllActualQtyEntered(List<InvStockCheckDetail> details)
    {
        if (details == null || details.isEmpty())
        {
            throw new ServiceException("盘点单无明细");
        }
        for (InvStockCheckDetail detail : details)
        {
            if (detail.getActualQty() == null)
            {
                throw new ServiceException("商品 [" + detail.getProductName() + "] 未录入实盘数量");
            }
            if ("1".equals(detail.getRecountRequired()) && detail.getRecountQty() == null)
            {
                throw new ServiceException("商品 [" + detail.getProductName() + "] 达到复盘阈值，请录入复盘数量");
            }
        }
    }

    static boolean requiresRecount(BigDecimal bookQty, BigDecimal actualQty, BigDecimal threshold)
    {
        if (actualQty == null || threshold == null || threshold.compareTo(BigDecimal.ZERO) <= 0)
        {
            return false;
        }
        BigDecimal safeBookQty = bookQty == null ? BigDecimal.ZERO : bookQty;
        return actualQty.subtract(safeBookQty).abs().compareTo(threshold) >= 0;
    }

    private BigDecimal normalizeRecountThreshold(BigDecimal threshold)
    {
        BigDecimal value = threshold == null ? DEFAULT_RECOUNT_THRESHOLD : threshold;
        if (value.compareTo(BigDecimal.ZERO) < 0)
        {
            throw new ServiceException("复盘阈值不能为负");
        }
        return value;
    }

    private boolean isBlindInput(InvStockCheck check)
    {
        return check != null && "1".equals(check.getBlindCheck())
                && (InvStatusConstants.DRAFT.equals(check.getStatus())
                    || InvStatusConstants.REJECTED.equals(check.getStatus())
                    || InvStatusConstants.RETURNED.equals(check.getStatus()));
    }

    private void hideBlindSnapshot(List<InvStockCheckDetail> details)
    {
        if (details == null)
        {
            return;
        }
        for (InvStockCheckDetail detail : details)
        {
            detail.setBookQty(null);
            detail.setPreviousBookQty(null);
            detail.setDiffQty(null);
            detail.setDiffType("none");
        }
    }

    private boolean hasDifference(List<InvStockCheckDetail> details)
    {
        for (InvStockCheckDetail detail : details)
        {
            if (detail.getDiffQty() != null && detail.getDiffQty().compareTo(BigDecimal.ZERO) != 0)
            {
                return true;
            }
        }
        return false;
    }

    private BigDecimal calculateDiffQty(BigDecimal actualQty, BigDecimal bookQty)
    {
        if (actualQty == null)
        {
            return null;
        }
        BigDecimal safeBookQty = bookQty != null ? bookQty : BigDecimal.ZERO;
        return actualQty.subtract(safeBookQty);
    }

    static String resolveDiffType(BigDecimal diffQty)
    {
        if (diffQty == null || diffQty.compareTo(BigDecimal.ZERO) == 0)
        {
            return "none";
        }
        return diffQty.compareTo(BigDecimal.ZERO) > 0 ? "profit" : "loss";
    }

    private BigDecimal toDecimal(Object val)
    {
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof BigDecimal) return (BigDecimal) val;
        try { return new BigDecimal(val.toString()); }
        catch (Exception e) { return BigDecimal.ZERO; }
    }

    private Long toLong(Object val)
    {
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).longValue();
        try { return Long.valueOf(val.toString()); }
        catch (Exception e) { return null; }
    }
}

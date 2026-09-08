package com.erp.oa.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.oa.domain.OaFixedAssetConfig;
import com.erp.oa.domain.OaFixedAssetQuota;
import com.erp.oa.domain.OaFixedAssetQuotaLedger;
import com.erp.oa.domain.OaFixedAssetQuotaMonth;
import com.erp.oa.domain.OaFixedAssetRepair;
import com.erp.oa.domain.dto.OaFixedAssetRepairBatchItem;
import com.erp.oa.domain.dto.OaFixedAssetRepairBatchRequest;
import com.erp.oa.domain.vo.OaFixedAssetQuotaSummary;
import com.erp.oa.domain.vo.OaFixedAssetRepairPrecheckVo;
import com.erp.oa.exception.OaFixedAssetValidationException;
import com.erp.oa.mapper.OaDeptScopeMapper;
import com.erp.oa.mapper.OaFixedAssetConfigMapper;
import com.erp.oa.mapper.OaFixedAssetQuotaLedgerMapper;
import com.erp.oa.mapper.OaFixedAssetQuotaMapper;
import com.erp.oa.mapper.OaFixedAssetQuotaMonthMapper;
import com.erp.oa.mapper.OaFixedAssetRepairMapper;
import com.erp.oa.service.IOaFixedAssetService;

@Service
public class OaFixedAssetServiceImpl implements IOaFixedAssetService
{
    private static final BigDecimal DEFAULT_ANNUAL_RATIO = new BigDecimal("20.00");
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100.00");
    private static final BigDecimal TWELVE = new BigDecimal("12.00");
    private static final String STATUS_NORMAL = "0";
    private static final String REPAIR_STATUS_PENDING_CONFIRM = "pending_confirm";
    private static final String REPAIR_STATUS_SUBMITTED = "submitted";
    private static final String EXCEPTION_YES = "Y";
    private static final String EXCEPTION_NO = "N";
    private static final String TYPE_NORMAL_SUBMIT = "normal_submit";
    private static final String TYPE_ADVANCE = "advance_future_months";
    private static final String TYPE_SPECIAL = "special_extra";
    private static final Long NO_VISIBLE_SHOP_DEPT_ID = -1L;

    @Autowired
    private OaFixedAssetConfigMapper configMapper;

    @Autowired
    private OaFixedAssetQuotaMapper quotaMapper;

    @Autowired
    private OaFixedAssetRepairMapper repairMapper;

    @Autowired
    private OaFixedAssetQuotaLedgerMapper ledgerMapper;

    @Autowired
    private OaFixedAssetQuotaMonthMapper monthMapper;

    @Autowired
    private OaDeptScopeMapper deptScopeMapper;

    @Autowired
    private ShopScopeService shopScopeService;

    private Clock clock = Clock.systemDefaultZone();

    @Override
    public List<OaFixedAssetConfig> selectConfigList(OaFixedAssetConfig config, Long selectedShopDeptId)
    {
        OaFixedAssetConfig query = config == null ? new OaFixedAssetConfig() : config;
        appendUserShopScope(query, query.getShopDeptId(), "无权访问该店铺固定资产");
        return configMapper.selectConfigList(query);
    }

    @Override
    public OaFixedAssetConfig selectConfigById(Long configId, Long selectedShopDeptId)
    {
        OaFixedAssetConfig config = configMapper.selectConfigById(configId);
        if (config == null)
        {
            throw new ServiceException("固定资产配置不存在");
        }
        assertUserShopVisible(config.getShopDeptId(), "无权访问该店铺固定资产");
        return config;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaFixedAssetConfig saveConfig(OaFixedAssetConfig config, Long selectedShopDeptId)
    {
        Long shopDeptId = resolveWritableShop(config.getShopDeptId(), selectedShopDeptId);
        if (StringUtils.isEmpty(config.getStatus()))
        {
            config.setStatus(STATUS_NORMAL);
        }
        OaFixedAssetConfig oeSnapshot = assertAndGetActiveOeItem(config.getOeItemId());
        if (STATUS_NORMAL.equals(config.getStatus()))
        {
            assertPurchaseReferenceComplete(oeSnapshot);
        }
        config.setShopDeptId(shopDeptId);
        if (config.getAssetUnitPrice() == null || config.getAssetUnitPrice().compareTo(BigDecimal.ZERO) <= 0)
        {
            config.setAssetUnitPrice(oeSnapshot.getAssetUnitPrice());
        }
        config.setAssetAmount(resolveAssetAmount(config));
        if (config.getConfigId() == null)
        {
            config.setCreateBy(SecurityUtils.getUsername());
            configMapper.insertConfig(config);
        }
        else
        {
            selectConfigById(config.getConfigId(), selectedShopDeptId);
            config.setUpdateBy(SecurityUtils.getUsername());
            configMapper.updateConfig(config);
        }
        rebuildQuota(shopDeptId, currentYear(), config.getAnnualRepairRatio());
        return configMapper.selectConfigById(config.getConfigId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteConfigById(Long configId, Long selectedShopDeptId)
    {
        OaFixedAssetConfig config = selectConfigById(configId, selectedShopDeptId);
        int rows = configMapper.deleteConfigById(configId);
        rebuildQuota(config.getShopDeptId(), currentYear(), null);
        return rows;
    }

    @Override
    public OaFixedAssetQuotaSummary getQuotaSummary(Long shopDeptId, Integer quotaYear, Long selectedShopDeptId)
    {
        Long targetShopDeptId = resolveReadableShop(shopDeptId, selectedShopDeptId);
        Integer year = quotaYear == null ? currentYear() : quotaYear;
        OaFixedAssetQuota quota = quotaMapper.selectQuota(targetShopDeptId, year);
        if (quota == null)
        {
            quota = rebuildQuota(targetShopDeptId, year, null);
        }
        int releasedMonthCount = currentReleasedMonthCount(year);
        ensureMonthlyQuotaSnapshots(targetShopDeptId, year, releasedMonthCount, quota.getAnnualRepairRatio());
        BigDecimal usedAmount = money(ledgerMapper.sumUsedQuotaAmount(targetShopDeptId, year));
        BigDecimal releasedAmount = money(monthMapper.sumReleasedQuotaAmount(targetShopDeptId, year, releasedMonthCount));
        BigDecimal availableAmount = releasedAmount.subtract(usedAmount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal futureAdvanceAmount = usedAmount.subtract(releasedAmount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        OaFixedAssetQuotaSummary summary = new OaFixedAssetQuotaSummary();
        summary.setShopDeptId(targetShopDeptId);
        summary.setQuotaYear(year);
        summary.setAnnualRepairRatio(money(quota.getAnnualRepairRatio()));
        summary.setAssetTotalAmount(money(quota.getAssetTotalAmount()));
        summary.setAnnualQuotaAmount(money(quota.getAnnualQuotaAmount()));
        summary.setMonthlyQuotaAmount(money(quota.getMonthlyQuotaAmount()));
        summary.setReleasedQuotaAmount(releasedAmount);
        summary.setUsedQuotaAmount(usedAmount);
        summary.setAvailableQuotaAmount(availableAmount);
        summary.setFutureAdvanceAmount(futureAdvanceAmount);
        return summary;
    }

    @Override
    public List<OaFixedAssetRepair> selectRepairList(OaFixedAssetRepair repair, Long selectedShopDeptId)
    {
        OaFixedAssetRepair query = repair == null ? new OaFixedAssetRepair() : repair;
        query.setShopDeptId(resolveRepairCurrentShop(query.getShopDeptId(), selectedShopDeptId, "查看"));
        return repairMapper.selectRepairList(query);
    }

    @Override
    public OaFixedAssetRepair selectRepairById(Long repairId, Long selectedShopDeptId)
    {
        OaFixedAssetRepair repair = repairMapper.selectRepairById(repairId);
        if (repair == null)
        {
            throw new ServiceException("固定资产维修上报不存在");
        }
        Long shopDeptId = resolveRepairCurrentShop(null, selectedShopDeptId, "查看");
        if (!shopDeptId.equals(repair.getShopDeptId()))
        {
            throw new ServiceException("只能查看当前门店固定资产维修单");
        }
        return repair;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaFixedAssetRepair submitRepair(OaFixedAssetRepair repair, Long selectedShopDeptId)
    {
        Long shopDeptId = resolveRepairCurrentShop(repair.getShopDeptId(), selectedShopDeptId, "上报");
        OaFixedAssetConfig assetConfig = assertAndGetConfiguredOeAsset(shopDeptId, repair.getOeItemId());
        Integer year = currentYear();
        BigDecimal quantity = resolveRepairQuantity(repair);
        assertRepairQuantityWithinConfig(quantity, assetConfig);
        BigDecimal usageAmount = calculateQuotaUsageAmount(assetConfig, quantity);
        lockQuotaForSubmission(shopDeptId, year);
        OaFixedAssetQuotaSummary summary = getQuotaSummary(shopDeptId, year, selectedShopDeptId);
        assertAvailableQuotaEnough(usageAmount, summary.getAvailableQuotaAmount(), assetConfig);
        repair.setShopDeptId(shopDeptId);
        repair.setOeItemName(assetConfig.getOeItemName());
        repair.setRepairQuantity(quantity);
        repair.setEstimatedRepairAmount(usageAmount);
        repair.setAvailableQuotaAmount(summary.getAvailableQuotaAmount());
        repair.setExceptionApproved(EXCEPTION_NO);
        repair.setExceptionType(null);
        repair.setStatus(REPAIR_STATUS_SUBMITTED);
        repair.setApplicantId(SecurityUtils.getUserId());
        repair.setApplicantName(SecurityUtils.getUsername());
        repair.setCreateBy(SecurityUtils.getUsername());
        repair.setSubmittedTime(new Date());
        repairMapper.insertRepair(repair);
        insertLedger(repair, year, TYPE_NORMAL_SUBMIT, usageAmount);
        return repairMapper.selectRepairById(repair.getRepairId());
    }

    @Override
    public OaFixedAssetRepairPrecheckVo precheckRepair(OaFixedAssetRepair repair,
            Long selectedShopDeptId)
    {
        if (repair == null)
        {
            throw new ServiceException("上报信息不能为空");
        }
        Long shopDeptId = resolveRepairCurrentShop(repair.getShopDeptId(),
                selectedShopDeptId, "预校验");
        OaFixedAssetConfig assetConfig = assertAndGetConfiguredOeAsset(
                shopDeptId, repair.getOeItemId());
        BigDecimal quantity = resolveRepairQuantity(repair);
        assertRepairQuantityWithinConfig(quantity, assetConfig);
        BigDecimal usageAmount = calculateQuotaUsageAmount(assetConfig,
                quantity);
        OaFixedAssetQuotaSummary summary = getQuotaSummary(shopDeptId,
                currentYear(), selectedShopDeptId);
        return buildPrecheck(assetConfig, usageAmount,
                summary.getAvailableQuotaAmount());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<OaFixedAssetRepair> submitRepairBatch(OaFixedAssetRepairBatchRequest request, Long selectedShopDeptId)
    {
        if (request == null || request.getItems() == null || request.getItems().isEmpty())
        {
            throw new ServiceException("请选择固定资产明细");
        }
        Long shopDeptId = resolveRepairCurrentShop(request.getShopDeptId(), selectedShopDeptId, "上报");
        Integer year = currentYear();
        List<PendingRepairRow> pendingRows = new ArrayList<>();
        Set<Long> selectedOeItemIds = new HashSet<>();
        BigDecimal totalUsageAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (OaFixedAssetRepairBatchItem item : request.getItems())
        {
            if (item == null || item.getOeItemId() == null || item.getOeItemId() <= 0)
            {
                throw new ServiceException("请选择本店已配置的OE固定资产");
            }
            if (!selectedOeItemIds.add(item.getOeItemId()))
            {
                throw new ServiceException("同一固定资产请合并坏掉数量后再上报");
            }
            OaFixedAssetConfig assetConfig = assertAndGetConfiguredOeAsset(shopDeptId, item.getOeItemId());
            OaFixedAssetRepair quantityHolder = new OaFixedAssetRepair();
            quantityHolder.setRepairQuantity(item.getRepairQuantity());
            BigDecimal quantity = resolveRepairQuantity(quantityHolder);
            assertRepairQuantityWithinConfig(quantity, assetConfig);
            BigDecimal usageAmount = calculateQuotaUsageAmount(assetConfig, quantity);
            totalUsageAmount = totalUsageAmount.add(usageAmount).setScale(2, RoundingMode.HALF_UP);
            pendingRows.add(new PendingRepairRow(assetConfig, quantity, usageAmount));
        }

        lockQuotaForSubmission(shopDeptId, year);
        OaFixedAssetQuotaSummary summary = getQuotaSummary(shopDeptId, year, selectedShopDeptId);
        assertAvailableQuotaEnough(totalUsageAmount, summary.getAvailableQuotaAmount(),
                pendingRows.get(0).assetConfig);

        List<OaFixedAssetRepair> savedRows = new ArrayList<>();
        for (PendingRepairRow pending : pendingRows)
        {
            OaFixedAssetRepair repair = new OaFixedAssetRepair();
            repair.setShopDeptId(shopDeptId);
            repair.setOeItemId(pending.assetConfig.getOeItemId());
            repair.setOeItemName(pending.assetConfig.getOeItemName());
            repair.setRepairQuantity(pending.quantity);
            repair.setEstimatedRepairAmount(pending.usageAmount);
            repair.setAvailableQuotaAmount(summary.getAvailableQuotaAmount());
            repair.setFaultDescription(request.getFaultDescription());
            repair.setImageUrls(request.getImageUrls());
            repair.setRemark(request.getRemark());
            repair.setExceptionApproved(EXCEPTION_NO);
            repair.setExceptionType(null);
            repair.setStatus(REPAIR_STATUS_SUBMITTED);
            repair.setApplicantId(SecurityUtils.getUserId());
            repair.setApplicantName(SecurityUtils.getUsername());
            repair.setCreateBy(SecurityUtils.getUsername());
            repair.setSubmittedTime(new Date());
            repairMapper.insertRepair(repair);
            insertLedger(repair, year, TYPE_NORMAL_SUBMIT, pending.usageAmount);
            savedRows.add(repairMapper.selectRepairById(repair.getRepairId()));
        }
        return savedRows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaFixedAssetRepair approveExceptionRepair(OaFixedAssetRepair repair, Long selectedShopDeptId)
    {
        throw new OaFixedAssetValidationException(
                "FIXED_ASSET_EXCEPTION_APPROVAL_DISABLED",
                "固定资产超额异常批准已下线；超过额度请按同款购买参考自行购买且无需上报");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaFixedAssetRepair confirmApprovedRepair(Long repairId, Long selectedShopDeptId)
    {
        throw new OaFixedAssetValidationException(
                "FIXED_ASSET_EXCEPTION_APPROVAL_DISABLED",
                "固定资产超额异常批准已下线；历史待确认记录仅保留查看");
    }

    private OaFixedAssetQuota rebuildQuota(Long shopDeptId, Integer quotaYear, BigDecimal annualRatio)
    {
        OaFixedAssetQuota existing = quotaMapper.selectQuota(shopDeptId, quotaYear);
        BigDecimal ratio = annualRatio != null ? annualRatio
                : existing != null && existing.getAnnualRepairRatio() != null ? existing.getAnnualRepairRatio() : DEFAULT_ANNUAL_RATIO;
        BigDecimal assetTotal = money(configMapper.sumAssetAmountByShop(shopDeptId));
        BigDecimal annualQuota = assetTotal.multiply(ratio).divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
        BigDecimal monthlyQuota = annualQuota.divide(TWELVE, 2, RoundingMode.HALF_UP);

        OaFixedAssetQuota quota = existing == null ? new OaFixedAssetQuota() : existing;
        quota.setShopDeptId(shopDeptId);
        quota.setQuotaYear(quotaYear);
        quota.setAnnualRepairRatio(money(ratio));
        quota.setAssetTotalAmount(assetTotal);
        quota.setAnnualQuotaAmount(annualQuota);
        quota.setMonthlyQuotaAmount(monthlyQuota);
        if (existing == null)
        {
            quota.setCreateBy(SecurityUtils.getUsername());
            quotaMapper.insertQuota(quota);
        }
        else
        {
            quota.setUpdateBy(SecurityUtils.getUsername());
            quotaMapper.updateQuota(quota);
        }
        refreshCurrentAndFutureMonthlyQuotaSnapshots(shopDeptId, quotaYear, ratio);
        return quota;
    }

    private void ensureMonthlyQuotaSnapshots(Long shopDeptId, Integer quotaYear, int throughMonth, BigDecimal annualRatio)
    {
        if (throughMonth <= 0)
        {
            return;
        }
        BigDecimal ratio = resolveAnnualRatio(shopDeptId, quotaYear, annualRatio);
        for (int month = 1; month <= throughMonth; month++)
        {
            BigDecimal assetTotal = calculateEffectiveAssetTotalAmount(shopDeptId, quotaYear, month);
            BigDecimal monthlyQuota = calculateMonthlyQuotaAmount(assetTotal, ratio);
            OaFixedAssetQuotaMonth existing = monthMapper.selectMonthQuota(shopDeptId, quotaYear, month);
            if (existing == null || shouldCorrectUnearnedMonth(existing, assetTotal))
            {
                upsertMonthQuota(shopDeptId, quotaYear, month, ratio, assetTotal, monthlyQuota);
            }
        }
    }

    private void refreshCurrentAndFutureMonthlyQuotaSnapshots(Long shopDeptId, Integer quotaYear, BigDecimal annualRatio)
    {
        int startMonth = Math.max(1, currentReleasedMonthCount(quotaYear));
        BigDecimal ratio = resolveAnnualRatio(shopDeptId, quotaYear, annualRatio);
        for (int month = startMonth; month <= 12; month++)
        {
            BigDecimal assetTotal = calculateEffectiveAssetTotalAmount(shopDeptId, quotaYear, month);
            BigDecimal monthlyQuota = calculateMonthlyQuotaAmount(assetTotal, ratio);
            upsertMonthQuota(shopDeptId, quotaYear, month, ratio, assetTotal, monthlyQuota);
        }
    }

    private void upsertMonthQuota(Long shopDeptId, Integer quotaYear, Integer quotaMonth, BigDecimal annualRatio,
            BigDecimal assetTotal, BigDecimal monthlyQuota)
    {
        OaFixedAssetQuotaMonth existing = monthMapper.selectMonthQuota(shopDeptId, quotaYear, quotaMonth);
        OaFixedAssetQuotaMonth monthQuota = existing == null ? new OaFixedAssetQuotaMonth() : existing;
        monthQuota.setShopDeptId(shopDeptId);
        monthQuota.setQuotaYear(quotaYear);
        monthQuota.setQuotaMonth(quotaMonth);
        monthQuota.setAnnualRepairRatio(money(annualRatio));
        monthQuota.setAssetTotalAmount(money(assetTotal));
        monthQuota.setMonthlyQuotaAmount(money(monthlyQuota));
        if (existing == null)
        {
            monthQuota.setCreateBy(SecurityUtils.getUsername());
            monthMapper.insertMonthQuota(monthQuota);
        }
        else
        {
            monthQuota.setUpdateBy(SecurityUtils.getUsername());
            monthMapper.updateMonthQuota(monthQuota);
        }
    }

    private BigDecimal resolveAnnualRatio(Long shopDeptId, Integer quotaYear, BigDecimal annualRatio)
    {
        if (annualRatio != null)
        {
            return money(annualRatio);
        }
        OaFixedAssetQuota quota = quotaMapper.selectQuota(shopDeptId, quotaYear);
        return quota != null && quota.getAnnualRepairRatio() != null ? money(quota.getAnnualRepairRatio()) : DEFAULT_ANNUAL_RATIO;
    }

    private BigDecimal calculateMonthlyQuotaAmount(BigDecimal assetTotal, BigDecimal annualRatio)
    {
        return money(assetTotal).multiply(money(annualRatio))
                .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP)
                .divide(TWELVE, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateEffectiveAssetTotalAmount(Long shopDeptId, Integer quotaYear, Integer quotaMonth)
    {
        OaFixedAssetConfig query = new OaFixedAssetConfig();
        query.setShopDeptId(shopDeptId);
        query.setStatus(STATUS_NORMAL);
        List<OaFixedAssetConfig> configs = configMapper.selectConfigList(query);
        if (configs == null || configs.isEmpty())
        {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        YearMonth quotaYearMonth = YearMonth.of(quotaYear, quotaMonth);
        BigDecimal total = BigDecimal.ZERO;
        for (OaFixedAssetConfig config : configs)
        {
            if (config == null || config.getShopDeptId() == null || !shopDeptId.equals(config.getShopDeptId()))
            {
                continue;
            }
            if (!resolveConfigEffectiveMonth(config, quotaYear).isAfter(quotaYearMonth))
            {
                total = total.add(resolveAssetAmount(config));
            }
        }
        return money(total);
    }

    private YearMonth resolveConfigEffectiveMonth(OaFixedAssetConfig config, Integer quotaYear)
    {
        Date createTime = config == null ? null : config.getCreateTime();
        if (createTime == null)
        {
            return YearMonth.of(quotaYear, 1);
        }
        LocalDate createdDate = createTime.toInstant().atZone(clock.getZone()).toLocalDate();
        YearMonth effectiveMonth = YearMonth.from(createdDate);
        if (isInLastFiveCalendarDays(createdDate))
        {
            effectiveMonth = effectiveMonth.plusMonths(1);
        }
        return effectiveMonth;
    }

    private boolean isInLastFiveCalendarDays(LocalDate date)
    {
        return date.getDayOfMonth() >= date.lengthOfMonth() - 4;
    }

    private boolean shouldCorrectUnearnedMonth(OaFixedAssetQuotaMonth existing, BigDecimal expectedAssetTotal)
    {
        return money(expectedAssetTotal).compareTo(BigDecimal.ZERO) == 0
                && existing != null
                && money(existing.getAssetTotalAmount()).compareTo(BigDecimal.ZERO) > 0;
    }

    private BigDecimal resolveRepairQuantity(OaFixedAssetRepair repair)
    {
        BigDecimal quantity = repair.getRepairQuantity() == null ? BigDecimal.ONE : repair.getRepairQuantity();
        quantity = quantity.setScale(2, RoundingMode.HALF_UP);
        if (quantity.compareTo(BigDecimal.ZERO) <= 0)
        {
            throw new ServiceException("坏掉数量必须大于0");
        }
        return quantity;
    }

    private void assertRepairQuantityWithinConfig(BigDecimal quantity, OaFixedAssetConfig assetConfig)
    {
        if (assetConfig.getAssetQuantity() != null && quantity.compareTo(assetConfig.getAssetQuantity()) > 0)
        {
            throw new ServiceException("坏掉数量不能超过店铺配置数量");
        }
    }

    private BigDecimal calculateQuotaUsageAmount(OaFixedAssetConfig assetConfig, BigDecimal quantity)
    {
        BigDecimal unitPrice = money(assetConfig.getAssetUnitPrice());
        if (unitPrice.compareTo(BigDecimal.ZERO) <= 0)
        {
            throw new ServiceException("固定资产单价未配置，不能上报");
        }
        return unitPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
    }

    private void assertAvailableQuotaEnough(BigDecimal usageAmount,
            BigDecimal availableQuotaAmount, OaFixedAssetConfig assetConfig)
    {
        if (usageAmount.compareTo(money(availableQuotaAmount)) > 0)
        {
            OaFixedAssetRepairPrecheckVo precheck = buildPrecheck(assetConfig,
                    usageAmount, availableQuotaAmount);
            throw new OaFixedAssetValidationException(
                    "FIXED_ASSET_QUOTA_EXCEEDED", precheck.getMessage(),
                    precheck);
        }
    }

    private OaFixedAssetRepairPrecheckVo buildPrecheck(
            OaFixedAssetConfig assetConfig, BigDecimal usageAmount,
            BigDecimal availableQuotaAmount)
    {
        OaFixedAssetRepairPrecheckVo result = new OaFixedAssetRepairPrecheckVo();
        boolean allowed = money(usageAmount).compareTo(
                money(availableQuotaAmount)) <= 0;
        List<String> missingReferenceFields = missingPurchaseReferenceFields(assetConfig);
        boolean referenceReady = missingReferenceFields.isEmpty();
        result.setAllowed(allowed);
        result.setErrorCode(allowed ? null : "FIXED_ASSET_QUOTA_EXCEEDED");
        result.setMessage(allowed ? "当前额度可上报"
                : referenceReady
                    ? "当前可用额度不足，不能上报；请按同款购买参考自行购买且无需上报"
                    : "当前可用额度不足，不能上报；同款资料尚未完善，请联系仓库");
        result.setQuotaUsageAmount(money(usageAmount));
        result.setAvailableQuotaAmount(money(availableQuotaAmount));
        result.setOeItemId(assetConfig.getOeItemId());
        result.setOeItemCode(assetConfig.getOeItemCode());
        result.setOeItemName(assetConfig.getOeItemName());
        result.setItemDescription(assetConfig.getItemDescription());
        result.setOrderUnit(assetConfig.getOrderUnit());
        result.setImageUrl(assetConfig.getImageUrl());
        result.setPurchaseReferenceUrl(assetConfig.getPurchaseReferenceUrl());
        result.setPurchaseReferenceNote(assetConfig.getPurchaseReferenceNote());
        result.setPurchaseReferenceReady(referenceReady);
        result.setMissingPurchaseReferenceFields(missingReferenceFields);
        return result;
    }

    /**
     * 提交事务内先创建年度额度行，再锁住该门店年度唯一行。所有提交在
     * 同一门店、同一年度串行重算可用额，避免并发请求同时通过校验。
     */
    private void lockQuotaForSubmission(Long shopDeptId, Integer quotaYear)
    {
        quotaMapper.insertQuotaIfAbsent(shopDeptId, quotaYear,
                DEFAULT_ANNUAL_RATIO, SecurityUtils.getUsername());
        OaFixedAssetQuota locked = quotaMapper.selectQuotaForUpdate(shopDeptId,
                quotaYear);
        if (locked == null)
        {
            throw new ServiceException("固定资产额度初始化失败，请稍后重试");
        }
        rebuildQuota(shopDeptId, quotaYear,
                locked.getAnnualRepairRatio());
    }

    private void insertLedger(OaFixedAssetRepair repair, Integer year, String movementType, BigDecimal amount)
    {
        OaFixedAssetQuotaLedger ledger = new OaFixedAssetQuotaLedger();
        ledger.setRepairId(repair.getRepairId());
        ledger.setShopDeptId(repair.getShopDeptId());
        ledger.setQuotaYear(year);
        ledger.setMovementType(movementType);
        ledger.setAmount(amount);
        ledger.setCreateBy(SecurityUtils.getUsername());
        ledgerMapper.insertLedger(ledger);
    }

    private static class PendingRepairRow
    {
        private final OaFixedAssetConfig assetConfig;
        private final BigDecimal quantity;
        private final BigDecimal usageAmount;

        private PendingRepairRow(OaFixedAssetConfig assetConfig, BigDecimal quantity, BigDecimal usageAmount)
        {
            this.assetConfig = assetConfig;
            this.quantity = quantity;
            this.usageAmount = usageAmount;
        }
    }

    private OaFixedAssetConfig assertAndGetActiveOeItem(Long oeItemId)
    {
        if (oeItemId == null || oeItemId <= 0)
        {
            throw new ServiceException("请选择OE器皿");
        }
        OaFixedAssetConfig oeSnapshot = configMapper.selectOeItemSnapshot(oeItemId);
        if (oeSnapshot == null)
        {
            throw new ServiceException("OE器皿不存在或已停用");
        }
        return oeSnapshot;
    }

    private void assertPurchaseReferenceComplete(OaFixedAssetConfig oeSnapshot)
    {
        List<String> missingFields = missingPurchaseReferenceFields(oeSnapshot);
        if (!missingFields.isEmpty())
        {
            throw new OaFixedAssetValidationException(
                    "FIXED_ASSET_PURCHASE_REFERENCE_INCOMPLETE",
                    "该OE缺少：" + String.join("、", missingFields)
                            + "；请先到库存管理-OE管理完善同款资料");
        }
    }

    private List<String> missingPurchaseReferenceFields(OaFixedAssetConfig config)
    {
        List<String> missingFields = new ArrayList<>();
        addMissingReferenceField(missingFields, "器皿编码", config == null ? null : config.getOeItemCode());
        addMissingReferenceField(missingFields, "器皿名称", config == null ? null : config.getOeItemName());
        addMissingReferenceField(missingFields, "器皿图片", config == null ? null : config.getImageUrl());
        addMissingReferenceField(missingFields, "规格/描述", config == null ? null : config.getItemDescription());
        addMissingReferenceField(missingFields, "领用单位", config == null ? null : config.getOrderUnit());
        String referenceUrl = config == null ? null : config.getPurchaseReferenceUrl();
        if (StringUtils.isBlank(referenceUrl)
                || !StringUtils.startsWithIgnoreCase(referenceUrl.trim(), "https://"))
        {
            missingFields.add("同款购买链接");
        }
        addMissingReferenceField(missingFields, "购买说明", config == null ? null : config.getPurchaseReferenceNote());
        return missingFields;
    }

    private void addMissingReferenceField(List<String> missingFields, String label, String value)
    {
        if (StringUtils.isBlank(value))
        {
            missingFields.add(label);
        }
    }

    private OaFixedAssetConfig assertAndGetConfiguredOeAsset(Long shopDeptId, Long oeItemId)
    {
        if (oeItemId == null || oeItemId <= 0)
        {
            throw new ServiceException("请选择本店已配置的OE固定资产");
        }
        OaFixedAssetConfig config = configMapper.selectActiveConfigByShopAndOeItem(shopDeptId, oeItemId);
        if (config == null)
        {
            throw new ServiceException("请选择本店已配置的OE固定资产");
        }
        return config;
    }

    private Long resolveWritableShop(Long targetShopDeptId, Long selectedShopDeptId)
    {
        Long target = targetShopDeptId == null || targetShopDeptId == 0
                ? shopScopeService.resolveRequiredShopDept(selectedShopDeptId) : targetShopDeptId;
        assertUserShopVisible(target, "无权操作该店铺固定资产");
        return target;
    }

    private Long resolveReadableShop(Long targetShopDeptId, Long selectedShopDeptId)
    {
        if (targetShopDeptId != null && targetShopDeptId != 0)
        {
            assertUserShopVisible(targetShopDeptId, "无权访问该店铺固定资产");
            return targetShopDeptId;
        }
        Long selected = shopScopeService.resolveRequiredShopDept(selectedShopDeptId);
        assertUserShopVisible(selected, "无权访问该店铺固定资产");
        return selected;
    }

    private Long resolveRepairCurrentShop(Long requestShopDeptId, Long selectedShopDeptId, String action)
    {
        Long currentShopDeptId = shopScopeService.resolveRequiredShopDept(selectedShopDeptId);
        assertUserShopVisible(currentShopDeptId, "无权" + action + "当前门店固定资产维修单");
        if (requestShopDeptId != null && requestShopDeptId != 0 && !currentShopDeptId.equals(requestShopDeptId))
        {
            throw new ServiceException("只能" + action + "当前门店固定资产维修单");
        }
        return currentShopDeptId;
    }

    private void assertUserShopVisible(Long shopDeptId, String message)
    {
        if (shopDeptId == null || shopDeptId == 0)
        {
            throw new ServiceException("请先选择店铺或仓库");
        }
        if (!SecurityUtils.isAdmin() && deptScopeMapper.countUserShopScope(SecurityUtils.getUserId(), shopDeptId) <= 0)
        {
            throw new ServiceException(message);
        }
    }

    private void appendUserShopScope(com.erp.common.core.web.domain.BaseEntity entity, Long targetShopDeptId, String message)
    {
        if (entity == null)
        {
            return;
        }
        if (targetShopDeptId != null && targetShopDeptId != 0)
        {
            assertUserShopVisible(targetShopDeptId, message);
            return;
        }
        if (SecurityUtils.isAdmin())
        {
            return;
        }
        List<Long> scopeDeptIds = deptScopeMapper.selectUserShopDeptIds(SecurityUtils.getUserId());
        entity.getParams().put("scopeDeptIds", scopeDeptIds == null || scopeDeptIds.isEmpty()
                ? Collections.singletonList(NO_VISIBLE_SHOP_DEPT_ID) : scopeDeptIds);
    }

    private BigDecimal resolveAssetAmount(OaFixedAssetConfig config)
    {
        BigDecimal amount = config.getAssetAmount();
        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0)
        {
            return money(amount);
        }
        return money(config.getAssetQuantity()).multiply(money(config.getAssetUnitPrice())).setScale(2, RoundingMode.HALF_UP);
    }

    private int currentYear()
    {
        return LocalDate.now(clock).getYear();
    }

    private int currentReleasedMonthCount(Integer quotaYear)
    {
        LocalDate today = LocalDate.now(clock);
        if (quotaYear == null || quotaYear < today.getYear())
        {
            return 12;
        }
        if (quotaYear > today.getYear())
        {
            return 0;
        }
        return today.getMonthValue();
    }

    private BigDecimal money(BigDecimal value)
    {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }
}

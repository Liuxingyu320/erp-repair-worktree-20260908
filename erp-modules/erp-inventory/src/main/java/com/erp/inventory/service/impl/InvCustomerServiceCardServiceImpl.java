package com.erp.inventory.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.domain.R;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.system.api.RemoteFileService;
import com.erp.system.api.domain.DriveBusinessFile;
import com.erp.inventory.domain.InvCustomer;
import com.erp.inventory.domain.InvCustomerServiceChangeLog;
import com.erp.inventory.domain.InvCustomerServiceProfile;
import com.erp.inventory.domain.InvCustomerServiceRecord;
import com.erp.inventory.domain.dto.InvCustomerServiceCardArchiveRequest;
import com.erp.inventory.domain.dto.InvCustomerServiceCardSaveRequest;
import com.erp.inventory.domain.dto.InvCustomerServiceRecordRequest;
import com.erp.inventory.domain.vo.InvCustomerOptionVo;
import com.erp.inventory.domain.vo.InvCustomerServiceCardQuery;
import com.erp.inventory.domain.vo.InvCustomerServiceCardVo;
import com.erp.inventory.domain.vo.InvCustomerServiceAuditQuery;
import com.erp.inventory.domain.vo.InvCustomerServiceAuditVo;
import com.erp.inventory.mapper.InvCustomerMapper;
import com.erp.inventory.mapper.InvCustomerServiceCardMapper;
import com.erp.inventory.metric.InventoryBusinessMetrics;
import com.erp.inventory.service.BusinessFeatureGate;
import com.erp.inventory.service.IInvCustomerServiceCardService;
import com.github.pagehelper.PageHelper;

@Service
public class InvCustomerServiceCardServiceImpl extends InvBaseService
        implements IInvCustomerServiceCardService
{
    private final InvCustomerServiceCardMapper mapper;
    private final InvCustomerMapper customerMapper;
    private final RemoteFileService remoteFileService;
    private final BusinessFeatureGate businessFeatureGate;
    private final InventoryBusinessMetrics metrics;

    @Autowired
    public InvCustomerServiceCardServiceImpl(InvCustomerServiceCardMapper mapper,
            InvCustomerMapper customerMapper,
            RemoteFileService remoteFileService,
            BusinessFeatureGate businessFeatureGate,
            InventoryBusinessMetrics metrics)
    {
        this.mapper = mapper;
        this.customerMapper = customerMapper;
        this.remoteFileService = remoteFileService;
        this.businessFeatureGate = businessFeatureGate;
        this.metrics = metrics;
    }

    public InvCustomerServiceCardServiceImpl(InvCustomerServiceCardMapper mapper,
            InvCustomerMapper customerMapper,
            RemoteFileService remoteFileService)
    {
        this(mapper, customerMapper, remoteFileService, null, null);
    }

    public InvCustomerServiceCardServiceImpl(InvCustomerServiceCardMapper mapper,
            InvCustomerMapper customerMapper,
            RemoteFileService remoteFileService,
            BusinessFeatureGate businessFeatureGate)
    {
        this(mapper, customerMapper, remoteFileService, businessFeatureGate,
                null);
    }

    @Override
    public List<InvCustomerOptionVo> selectOptions(String keyword, Long selectedShopDeptId)
    {
        Long shopDeptId = requireStoreContext(selectedShopDeptId, "请先选择门店");
        return mapper.selectOptions(shopDeptId, keyword);
    }

    @Override
    public List<InvCustomerServiceCardVo> selectList(InvCustomerServiceCardQuery query,
            Long selectedShopDeptId, int pageNum, int pageSize)
    {
        PageHelper.clearPage();
        Long shopDeptId = requireStoreContext(selectedShopDeptId, "请先选择门店");
        InvCustomerServiceCardQuery safeQuery = query == null
                ? new InvCustomerServiceCardQuery() : query;
        safeQuery.setShopDeptId(shopDeptId);
        startPage(pageNum, pageSize);
        return mapper.selectCardList(safeQuery);
    }

    @Override
    public boolean isWriteEnabled(Long selectedShopDeptId)
    {
        Long shopDeptId = requireStoreContext(selectedShopDeptId, "请先选择门店");
        return businessFeatureGate != null && businessFeatureGate.isEnabledForShop(
                BusinessFeatureGate.CUSTOMER_SERVICE_CARD,
                BusinessFeatureGate.CUSTOMER_SERVICE_CARD_ALLOWED_SHOPS,
                shopDeptId);
    }

    @Override
    public List<InvCustomerServiceAuditVo> selectAuditList(
            InvCustomerServiceAuditQuery query, Long selectedShopDeptId,
            int pageNum, int pageSize)
    {
        PageHelper.clearPage();
        Long shopDeptId = requireStoreContext(selectedShopDeptId, "请先选择门店");
        InvCustomerServiceAuditQuery safeQuery = query == null
                ? new InvCustomerServiceAuditQuery() : query;
        safeQuery.setShopDeptId(shopDeptId);
        if (safeQuery.getCustomerId() != null)
        {
            assertScopedCard(safeQuery.getCustomerId(), shopDeptId);
        }
        startPage(pageNum, pageSize);
        return mapper.selectAuditList(safeQuery);
    }

    private void startPage(int pageNum, int pageSize)
    {
        PageHelper.startPage(Math.max(1, pageNum),
                Math.min(100, Math.max(1, pageSize)));
    }

    @Override
    public InvCustomerServiceCardVo selectById(Long customerId, Long selectedShopDeptId)
    {
        Long shopDeptId = requireStoreContext(selectedShopDeptId, "请先选择门店");
        InvCustomerServiceCardVo card = assertScopedCard(customerId, shopDeptId);
        card.setServiceRecords(mapper.selectRecords(customerId));
        return card;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvCustomerServiceCardVo create(InvCustomerServiceCardSaveRequest request,
            Long selectedShopDeptId)
    {
        Long shopDeptId = requireStoreContext(selectedShopDeptId, "请先选择门店");
        requireWriteEnabled(shopDeptId);
        validateSaveRequest(request, false);
        String requestKey = requestKey(request.getRequestKey());
        if (!StringUtils.isBlank(request.getRequestKey()))
        {
            Long existingCustomerId = mapper.selectCreatedCustomerIdByRequestKey(
                    shopDeptId, requestKey);
            if (existingCustomerId != null)
            {
                recordCustomerWrite("idempotent_replay");
                return selectById(existingCustomerId, shopDeptId);
            }
        }
        validatePhotoBinding(request.getPhotoNodeId());
        InvCustomer customer = new InvCustomer();
        customer.setCustomerName(request.getCustomerName().trim());
        customer.setCustomerCode(trimToNull(request.getCustomerCode()));
        customer.setContactPerson(trimToNull(request.getContactPerson()));
        customer.setContactPhone(trimToNull(request.getContactPhone()));
        customer.setShopDeptId(shopDeptId);
        customer.setStatus("0");
        customer.setCreateBy(SecurityUtils.getUsername());
        customerMapper.insertInvCustomer(customer);

        InvCustomerServiceProfile profile = toProfile(customer.getCustomerId(), request);
        profile.setCreateBy(SecurityUtils.getUsername());
        mapper.insertProfile(profile);
        writeChangeLog(customer.getCustomerId(), shopDeptId, requestKey,
                "CREATE", "ALL", null, auditSummary(request), request.getSourceClient());
        InvCustomerServiceCardVo created = selectById(customer.getCustomerId(),
                shopDeptId);
        recordCustomerWrite("create_success");
        return created;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvCustomerServiceCardVo update(Long customerId,
            InvCustomerServiceCardSaveRequest request, Long selectedShopDeptId)
    {
        Long shopDeptId = requireStoreContext(selectedShopDeptId, "请先选择门店");
        requireWriteEnabled(shopDeptId);
        validateSaveRequest(request, true);
        String requestKey = requestKey(request.getRequestKey());
        InvCustomerServiceCardVo before = assertScopedCard(customerId, shopDeptId);
        if (!StringUtils.isBlank(request.getRequestKey())
                && mapper.selectChangeLogIdByRequestKey(customerId, requestKey) != null)
        {
            recordCustomerWrite("idempotent_replay");
            return selectById(customerId, shopDeptId);
        }
        requireActive(before);
        if (!Objects.equals(before.getPhotoNodeId(), request.getPhotoNodeId()))
        {
            validatePhotoBinding(request.getPhotoNodeId());
        }
        if (!request.getVersion().equals(before.getVersion()))
        {
            recordCustomerWrite("optimistic_conflict");
            throw new ServiceException("客户服务卡已被其他人修改，请刷新后重试");
        }
        if (mapper.updateCustomerCore(customerId, request.getCustomerName().trim(),
                trimToNull(request.getCustomerCode()), trimToNull(request.getContactPerson()),
                trimToNull(request.getContactPhone()), SecurityUtils.getUsername()) != 1)
        {
            throw new ServiceException("客户服务卡已归档或不存在，请刷新后重试");
        }
        InvCustomerServiceProfile profile = toProfile(customerId, request);
        profile.setVersion(request.getVersion());
        profile.setUpdateBy(SecurityUtils.getUsername());
        if (mapper.countProfile(customerId) == 0)
        {
            profile.setCreateBy(SecurityUtils.getUsername());
            mapper.insertProfile(profile);
        }
        else if (mapper.updateProfile(profile) != 1)
        {
            recordCustomerWrite("optimistic_conflict");
            throw new ServiceException("客户服务卡已被其他人修改，请刷新后重试");
        }
        writeChangeLog(customerId, shopDeptId, requestKey, "UPDATE",
                changedFields(before, request), auditSummary(before), auditSummary(request),
                request.getSourceClient());
        InvCustomerServiceCardVo updated = selectById(customerId, shopDeptId);
        recordCustomerWrite("update_success");
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvCustomerServiceCardVo addRecord(Long customerId,
            InvCustomerServiceRecordRequest request, Long selectedShopDeptId)
    {
        Long shopDeptId = requireStoreContext(selectedShopDeptId, "请先选择门店");
        requireWriteEnabled(shopDeptId);
        InvCustomerServiceCardVo before = assertScopedCard(customerId, shopDeptId);
        if (request == null)
        {
            throw new ServiceException("服务记录不能为空");
        }
        String requestKey = requestKey(request.getRequestKey());
        if (!StringUtils.isBlank(request.getRequestKey())
                && mapper.selectRecordIdByRequestKey(customerId, requestKey) != null)
        {
            recordCustomerWrite("idempotent_replay");
            return selectById(customerId, shopDeptId);
        }
        requireActive(before);
        if (request.getPartySize() != null && request.getPartySize() <= 0)
        {
            throw new ServiceException("到店人数必须大于0");
        }
        if (request.getConsumptionAmount() != null
                && request.getConsumptionAmount().compareTo(BigDecimal.ZERO) < 0)
        {
            throw new ServiceException("消费金额不能小于0");
        }
        Date serviceDate = request.getServiceDate() == null ? new Date() : request.getServiceDate();
        InvCustomerServiceRecord record = new InvCustomerServiceRecord();
        record.setCustomerId(customerId);
        record.setServiceDate(serviceDate);
        record.setServiceUserId(SecurityUtils.getUserId());
        record.setServiceUserName(SecurityUtils.getUsername());
        record.setPartySize(request.getPartySize());
        record.setTeaServed(trimToNull(request.getTeaServed()));
        record.setPreferenceSnapshot(trimToNull(request.getPreferenceSnapshot()));
        record.setCautionSnapshot(trimToNull(request.getCautionSnapshot()));
        record.setServiceNote(trimToNull(request.getServiceNote()));
        record.setConsumptionAmount(request.getConsumptionAmount());
        record.setShopDeptId(shopDeptId);
        record.setRequestKey(requestKey);
        record.setSourceClient(defaultSource(request.getSourceClient()));
        record.setCreateBy(SecurityUtils.getUsername());
        mapper.insertRecord(record);
        if (mapper.touchLastVisit(customerId, serviceDate,
                SecurityUtils.getUsername()) != 1)
        {
            throw new ServiceException("客户服务卡已归档或不存在，请刷新后重试");
        }
        writeChangeLog(customerId, shopDeptId, requestKey, "ADD_RECORD",
                "serviceRecord", null, "recordId=" + record.getRecordId(), request.getSourceClient());
        InvCustomerServiceCardVo updated = selectById(customerId, shopDeptId);
        recordCustomerWrite("record_success");
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvCustomerServiceCardVo archive(Long customerId,
            InvCustomerServiceCardArchiveRequest request, Long selectedShopDeptId)
    {
        Long shopDeptId = requireStoreContext(selectedShopDeptId, "请先选择门店");
        requireWriteEnabled(shopDeptId);
        InvCustomerServiceCardVo before = assertScopedCard(customerId, shopDeptId);
        if (request == null || request.getVersion() == null || StringUtils.isBlank(request.getReason()))
        {
            throw new ServiceException("版本和归档原因不能为空");
        }
        String requestKey = requestKey(request.getRequestKey());
        if (!StringUtils.isBlank(request.getRequestKey())
                && mapper.selectChangeLogIdByRequestKey(customerId, requestKey) != null)
        {
            recordCustomerWrite("idempotent_replay");
            return selectById(customerId, shopDeptId);
        }
        requireActive(before);
        if (!request.getVersion().equals(before.getVersion()))
        {
            recordCustomerWrite("optimistic_conflict");
            throw new ServiceException("客户服务卡已被其他人修改，请刷新后重试");
        }
        if (mapper.countProfile(customerId) == 0)
        {
            InvCustomerServiceProfile profile = new InvCustomerServiceProfile();
            profile.setCustomerId(customerId);
            profile.setCreateBy(SecurityUtils.getUsername());
            mapper.insertProfile(profile);
        }
        if (mapper.bumpProfileVersion(customerId, request.getVersion(), SecurityUtils.getUsername()) != 1)
        {
            recordCustomerWrite("optimistic_conflict");
            throw new ServiceException("客户服务卡已被其他人修改，请刷新后重试");
        }
        if (mapper.archiveCustomer(customerId, SecurityUtils.getUsername()) != 1)
        {
            throw new ServiceException("客户服务卡已归档或不存在，请刷新后重试");
        }
        writeChangeLog(customerId, shopDeptId, requestKey,
                "ARCHIVE", "status", "status=" + before.getStatus(),
                "status=1;reasonPresent=Y", request.getSourceClient());
        InvCustomerServiceCardVo archived = selectById(customerId, shopDeptId);
        recordCustomerWrite("archive_success");
        return archived;
    }

    @Override
    public Long resolvePhotoNode(Long customerId, Long selectedShopDeptId)
    {
        Long shopDeptId = requireStoreContext(selectedShopDeptId, "请先选择门店");
        InvCustomerServiceCardVo card = assertScopedCard(customerId,
                shopDeptId);
        if (card.getPhotoNodeId() == null)
        {
            throw new ServiceException("客户照片不存在");
        }
        return card.getPhotoNodeId();
    }

    private InvCustomerServiceCardVo assertScopedCard(Long customerId, Long shopDeptId)
    {
        InvCustomerServiceCardVo card = mapper.selectCardById(customerId);
        if (card == null || !shopDeptId.equals(card.getShopDeptId()))
        {
            if (metrics != null) metrics.recordCustomerCardScopeDenied();
            throw new ServiceException("客户服务卡不存在或无权访问");
        }
        return card;
    }

    private void recordCustomerWrite(String outcome)
    {
        if (metrics != null) metrics.recordCustomerCardWrite(outcome);
    }

    private void requireActive(InvCustomerServiceCardVo card)
    {
        if (!"0".equals(card.getStatus()))
        {
            throw new ServiceException("客户服务卡已归档，不能继续维护");
        }
    }

    private void requireWriteEnabled(Long shopDeptId)
    {
        if (businessFeatureGate == null)
        {
            throw new ServiceException("FEATURE_CONFIG_UNAVAILABLE: "
                    + BusinessFeatureGate.CUSTOMER_SERVICE_CARD);
        }
        businessFeatureGate.requireEnabledForShop(
                BusinessFeatureGate.CUSTOMER_SERVICE_CARD,
                BusinessFeatureGate.CUSTOMER_SERVICE_CARD_ALLOWED_SHOPS,
                shopDeptId);
    }

    private void validateSaveRequest(InvCustomerServiceCardSaveRequest request, boolean requireVersion)
    {
        if (request == null || StringUtils.isBlank(request.getCustomerName()))
        {
            throw new ServiceException("客户姓名不能为空");
        }
        if (requireVersion && request.getVersion() == null)
        {
            throw new ServiceException("客户服务卡版本不能为空");
        }
        if (request.getBudgetMin() != null && request.getBudgetMin().compareTo(BigDecimal.ZERO) < 0
                || request.getBudgetMax() != null && request.getBudgetMax().compareTo(BigDecimal.ZERO) < 0)
        {
            throw new ServiceException("人均预算不能小于0");
        }
        if (request.getBudgetMin() != null && request.getBudgetMax() != null
                && request.getBudgetMin().compareTo(request.getBudgetMax()) > 0)
        {
            throw new ServiceException("最低人均预算不能高于最高预算");
        }
    }

    private InvCustomerServiceProfile toProfile(Long customerId, InvCustomerServiceCardSaveRequest request)
    {
        InvCustomerServiceProfile profile = new InvCustomerServiceProfile();
        profile.setCustomerId(customerId);
        profile.setPhotoNodeId(request.getPhotoNodeId());
        profile.setTeaPreferences(trimToNull(request.getTeaPreferences()));
        profile.setPreferenceTags(trimToNull(request.getPreferenceTags()));
        profile.setBrewingServicePreferences(trimToNull(request.getBrewingServicePreferences()));
        profile.setCautions(trimToNull(request.getCautions()));
        profile.setBudgetMin(request.getBudgetMin());
        profile.setBudgetMax(request.getBudgetMax());
        profile.setLastVisitDate(request.getLastVisitDate());
        return profile;
    }

    private void validatePhotoBinding(Long photoNodeId)
    {
        if (photoNodeId == null)
        {
            return;
        }
        R<DriveBusinessFile> result = remoteFileService
                .validateDriveBusinessFile(photoNodeId, "CUSTOMER_PHOTO",
                        SecurityConstants.INNER);
        if (result == null || R.isError(result) || result.getData() == null
                || !photoNodeId.equals(result.getData().getNodeId()))
        {
            throw new ServiceException("客户照片不可用或无权绑定");
        }
    }

    private void writeChangeLog(Long customerId, Long shopDeptId, String requestKey,
            String changeType, String fields, String before, String after, String sourceClient)
    {
        InvCustomerServiceChangeLog log = new InvCustomerServiceChangeLog();
        log.setCustomerId(customerId);
        log.setRequestKey(requestKey);
        log.setChangeType(changeType);
        log.setChangedFields(fields);
        log.setBeforeSummary(before);
        log.setAfterSummary(after);
        log.setOperatorUserId(SecurityUtils.getUserId());
        log.setOperatorName(SecurityUtils.getUsername());
        log.setShopDeptId(shopDeptId);
        log.setSourceClient(defaultSource(sourceClient));
        mapper.insertChangeLog(log);
    }

    private String changedFields(InvCustomerServiceCardVo before,
            InvCustomerServiceCardSaveRequest after)
    {
        List<String> fields = new ArrayList<>();
        addChanged(fields, "customerName", before.getCustomerName(), after.getCustomerName());
        addChanged(fields, "customerCode", before.getCustomerCode(), after.getCustomerCode());
        addChanged(fields, "contactPerson", before.getContactPerson(), after.getContactPerson());
        addChanged(fields, "contactPhone", before.getContactPhone(), after.getContactPhone());
        addChanged(fields, "photoNodeId", before.getPhotoNodeId(), after.getPhotoNodeId());
        addChanged(fields, "teaPreferences", before.getTeaPreferences(), after.getTeaPreferences());
        addChanged(fields, "preferenceTags", before.getPreferenceTags(), after.getPreferenceTags());
        addChanged(fields, "brewingServicePreferences", before.getBrewingServicePreferences(), after.getBrewingServicePreferences());
        addChanged(fields, "cautions", before.getCautions(), after.getCautions());
        addChanged(fields, "budgetMin", before.getBudgetMin(), after.getBudgetMin());
        addChanged(fields, "budgetMax", before.getBudgetMax(), after.getBudgetMax());
        addChanged(fields, "lastVisitDate", before.getLastVisitDate(), after.getLastVisitDate());
        return fields.isEmpty() ? "NONE" : String.join(",", fields);
    }

    private void addChanged(List<String> fields, String field, Object before, Object after)
    {
        if (!Objects.equals(before, after)) fields.add(field);
    }

    private String auditSummary(InvCustomerServiceCardSaveRequest request)
    {
        return "namePresent=Y;phonePresent=" + yesNo(request.getContactPhone())
                + ";preferencePresent=" + yesNo(request.getTeaPreferences())
                + ";cautionsPresent=" + yesNo(request.getCautions())
                + ";budgetPresent=" + (request.getBudgetMin() != null || request.getBudgetMax() != null ? "Y" : "N");
    }

    private String auditSummary(InvCustomerServiceCardVo card)
    {
        return "namePresent=Y;phonePresent=" + yesNo(card.getContactPhone())
                + ";preferencePresent=" + yesNo(card.getTeaPreferences())
                + ";cautionsPresent=" + yesNo(card.getCautions())
                + ";budgetPresent=" + (card.getBudgetMin() != null || card.getBudgetMax() != null ? "Y" : "N");
    }

    private String yesNo(String value) { return StringUtils.isBlank(value) ? "N" : "Y"; }
    private String trimToNull(String value) { return StringUtils.isBlank(value) ? null : value.trim(); }
    private String requestKey(String value)
    {
        if (StringUtils.isBlank(value))
        {
            throw new ServiceException("请求标识不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > 128)
        {
            throw new ServiceException("请求标识长度不能超过128");
        }
        return normalized;
    }
    private String defaultSource(String value) { return StringUtils.isBlank(value) ? "DESKTOP" : value.trim().toUpperCase(); }
}

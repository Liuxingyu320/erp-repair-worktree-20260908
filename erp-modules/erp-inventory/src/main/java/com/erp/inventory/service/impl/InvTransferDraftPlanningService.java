package com.erp.inventory.service.impl;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.inventory.constant.InvItemTypes;
import com.erp.inventory.constant.InvTransferTypes;
import com.erp.inventory.domain.InvTransferDetail;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.dto.InvTransferDraftPlanningRequest;
import com.erp.inventory.domain.vo.InventoryItemSnapshot;
import com.erp.inventory.domain.vo.InvTransferDraftPlanningVo;
import com.erp.inventory.mapper.InvDeptScopeMapper;

/** Produces a read-only, fail-closed stock preview for a transfer draft. */
@Service
public class InvTransferDraftPlanningService extends InvBaseService
{
    private final InventoryItemResolver itemResolver;
    private final InvTransferReservationService reservationService;
    private final Clock clock;

    @Autowired
    public InvTransferDraftPlanningService(
            InventoryItemResolver itemResolver,
            InvTransferReservationService reservationService,
            InvDeptScopeMapper deptScopeMapper,
            ShopScopeService shopScopeService)
    {
        this(itemResolver, reservationService, deptScopeMapper,
                shopScopeService, Clock.systemUTC());
    }

    InvTransferDraftPlanningService(
            InventoryItemResolver itemResolver,
            InvTransferReservationService reservationService,
            InvDeptScopeMapper deptScopeMapper,
            ShopScopeService shopScopeService,
            Clock clock)
    {
        this.itemResolver = itemResolver;
        this.reservationService = reservationService;
        this.deptScopeMapper = deptScopeMapper;
        this.shopScopeService = shopScopeService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public InvTransferDraftPlanningVo getPlanning(
            InvTransferDraftPlanningRequest request,
            Long selectedShopDeptId)
    {
        if (request == null)
        {
            throw new ServiceException("调拨草稿规划请求不能为空");
        }
        Long selectedDeptId = resolveAndValidateShopDept(
                selectedShopDeptId);
        InvTransferOrder order = route(request);
        new InvTransferDirectionPolicy(deptScopeMapper, shopScopeService)
                .validateCreation(order, selectedDeptId, false);
        // 补货规划只读取仓库库存；不应要求门店用户拥有仓库操作权。
        // 方向策略已将收货目标绑定为当前已授权门店。
        if (!InvTransferTypes.WAREHOUSE.equals(order.getTransferType()))
        {
            List<Long> authorizedDeptIds =
                    resolveAuthorizedInventoryDeptIds();
            assertAuthorizedInventoryDept(order.getFromDeptId(),
                    authorizedDeptIds,
                    "无权规划该调拨来源组织");
            assertAuthorizedInventoryDept(order.getToDeptId(),
                    authorizedDeptIds,
                    "无权规划该调拨目标组织");
        }
        Set<Long> itemOwnerScopeDeptIds =
                new InvTransferSourceItemScopePolicy(deptScopeMapper)
                        .resolveOwnerScopeDeptIds(order);

        List<InvTransferDetail> details = resolveDetails(request,
                itemOwnerScopeDeptIds, order.getTransferType());
        List<InvTransferReservationService.DraftAvailability>
                availability = reservationService.previewForDraft(order,
                        details);
        Map<ItemKey, InvTransferDetail> detailByKey = new HashMap<>();
        for (InvTransferDetail detail : details)
        {
            detailByKey.put(new ItemKey(detail.getItemType(),
                    detail.getItemId()), detail);
        }

        List<InvTransferDraftPlanningVo.Line> lines = new ArrayList<>();
        List<String> blockingReasons = new ArrayList<>();
        for (InvTransferReservationService.DraftAvailability item :
                availability)
        {
            InvTransferDetail detail = detailByKey.get(new ItemKey(
                    item.itemType(), item.itemId()));
            if (detail == null)
            {
                throw new ServiceException("调拨草稿规划明细归属无效");
            }
            List<String> blockers = new ArrayList<>();
            if (item.shortageQuantity().signum() > 0)
            {
                blockers.add("物料 [" + detail.getItemName().trim()
                        + "] 可用库存不足，缺口 "
                        + decimal(item.shortageQuantity()));
            }
            blockingReasons.addAll(blockers);
            lines.add(new InvTransferDraftPlanningVo.Line(
                    item.itemType(), identifier(item.itemId()),
                    identifier(detail.getProductId()),
                    nullableText(detail.getItemCode()),
                    detail.getItemName().trim(),
                    nullableText(detail.getSpec()),
                    nullableText(detail.getUnit()),
                    decimal(item.requestedQuantity()),
                    decimal(item.availableQuantity()),
                    decimal(item.shortageQuantity()),
                    blockers.isEmpty() ? "sufficient" : "insufficient",
                    List.copyOf(blockers)));
        }

        String generatedAt = DateTimeFormatter.ISO_INSTANT.format(
                clock.instant());
        return new InvTransferDraftPlanningVo(
                planVersion(selectedDeptId, order, availability),
                order.getTransferType(), organization(order.getFromDeptId()),
                organization(order.getToDeptId()), generatedAt,
                List.copyOf(lines), blockingReasons.isEmpty(),
                List.copyOf(blockingReasons), "server");
    }

    private InvTransferOrder route(InvTransferDraftPlanningRequest request)
    {
        if (request.getFromDeptId() == null
                || request.getToDeptId() == null)
        {
            throw new ServiceException("调拨路线不能为空");
        }
        InvTransferOrder order = new InvTransferOrder();
        order.setTransferType(InvTransferTypes.requireSupported(
                request.getTransferType()));
        order.setFromDeptId(request.getFromDeptId());
        order.setFromWarehouseId(request.getFromDeptId());
        order.setToDeptId(request.getToDeptId());
        order.setToWarehouseId(request.getToDeptId());
        order.setReturnReasonCode(request.getReturnReasonCode());
        order.setReturnReasonText(request.getReturnReasonText());
        return order;
    }

    private List<InvTransferDetail> resolveDetails(
            InvTransferDraftPlanningRequest request,
            Set<Long> itemOwnerScopeDeptIds,
            String transferType)
    {
        if (request.getDetails() == null || request.getDetails().isEmpty())
        {
            throw new ServiceException("请至少添加一条调拨明细");
        }
        List<InvTransferDetail> result = new ArrayList<>();
        for (InvTransferDraftPlanningRequest.Line line :
                request.getDetails())
        {
            if (line == null || line.getItemId() == null
                    || line.getQuantity() == null
                    || line.getQuantity().signum() <= 0)
            {
                throw new ServiceException("调拨草稿包含无效明细");
            }
            String itemType = InvItemTypes.normalize(line.getItemType());
            if (!InvTransferTypes.allowsItemType(transferType, itemType))
            {
                throw new ServiceException("当前调拨类型不支持该物料类型");
            }
            InventoryItemSnapshot item = itemResolver.resolve(itemType,
                    line.getItemId(), line.getProductId());
            if (item.getItemId() == null
                    || !item.getItemId().equals(line.getItemId())
                    || !"0".equals(item.getStatus()))
            {
                throw new ServiceException("调拨物料不存在、已停用或归属无效");
            }
            if (line.getProductId() != null
                    && !line.getProductId().equals(item.getProductId()))
            {
                throw new ServiceException("调拨商品标识与物料主数据不一致");
            }
            if (item.getItemName() == null || item.getItemName().isBlank())
            {
                throw new ServiceException("调拨物料主数据不完整");
            }
            new InvTransferSourceItemScopePolicy(deptScopeMapper)
                    .assertEligible(transferType, item,
                            itemOwnerScopeDeptIds,
                            "无权规划该调拨物料");
            InvTransferDetail detail = new InvTransferDetail();
            detail.setItemType(item.getItemType());
            detail.setItemId(item.getItemId());
            detail.setProductId(item.getProductId());
            detail.setItemCode(item.getItemCode());
            detail.setItemName(item.getItemName());
            detail.setProductCode(item.getItemCode());
            detail.setProductName(item.getItemName());
            detail.setSpec(item.getSpec());
            detail.setUnit(item.getUnit());
            detail.setGrade(item.getGrade());
            detail.setQuantity(line.getQuantity());
            result.add(detail);
        }
        return result;
    }

    private InvTransferDraftPlanningVo.Organization organization(Long deptId)
    {
        String name = deptScopeMapper.selectDeptNameById(deptId);
        String kind = deptScopeMapper.selectDeptTypeById(deptId);
        if (name == null || name.isBlank() || kind == null
                || kind.isBlank())
        {
            throw new ServiceException("调拨组织主数据不完整");
        }
        return new InvTransferDraftPlanningVo.Organization(
                identifier(deptId), name.trim(), kind.trim().toLowerCase());
    }

    private String planVersion(Long selectedDeptId, InvTransferOrder order,
            List<InvTransferReservationService.DraftAvailability> lines)
    {
        StringBuilder canonical = new StringBuilder()
                .append(selectedDeptId).append('|')
                .append(order.getTransferType()).append('|')
                .append(order.getFromDeptId()).append('|')
                .append(order.getToDeptId());
        for (InvTransferReservationService.DraftAvailability line : lines)
        {
            canonical.append('|').append(line.itemType())
                    .append(':').append(line.itemId())
                    .append(':').append(decimal(line.requestedQuantity()))
                    .append(':').append(decimal(line.availableQuantity()))
                    .append(':').append(line.stockId())
                    .append(':').append(line.stockVersion());
        }
        try
        {
            return HexFormat.of().formatHex(MessageDigest
                    .getInstance("SHA-256").digest(canonical.toString()
                            .getBytes(StandardCharsets.UTF_8)));
        }
        catch (Exception exception)
        {
            throw new IllegalStateException("无法生成调拨草稿规划版本",
                    exception);
        }
    }

    private String identifier(Long value)
    {
        return value == null ? null : String.valueOf(value);
    }

    private String decimal(BigDecimal value)
    {
        if (value == null || value.signum() == 0) return "0";
        return value.stripTrailingZeros().toPlainString();
    }

    private String nullableText(String value)
    {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record ItemKey(String itemType, Long itemId)
    {
    }
}

package com.erp.inventory.service.impl;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.InvStock;
import com.erp.inventory.domain.vo.InvReportProductOption;
import com.erp.inventory.domain.vo.InvReportItemOption;
import com.erp.inventory.domain.vo.InvReportSummary;
import com.erp.inventory.mapper.InvReportMapper;
import com.erp.inventory.service.IInvReportService;

@Service
public class InvReportServiceImpl extends InvBaseService implements IInvReportService
{
    private static final Set<String> WARNING_STATUSES =
            Set.of("low", "empty", "unconfigured");

    @Autowired
    private InvReportMapper reportMapper;

    @Override
    public InvReportSummary selectReportSummary(InvStock stock, Long selectedShopDeptId)
    {
        InvStock query = prepareReportQuery(stock, selectedShopDeptId);
        InvReportSummary summary = reportMapper.selectReportSummary(query);
        return summary == null ? new InvReportSummary() : summary;
    }

    @Override
    public List<InvStock> selectStockWarningList(InvStock stock, Long selectedShopDeptId)
    {
        InvStock query = prepareReportQuery(stock, selectedShopDeptId);
        query.setStockScope("warning");
        return reportMapper.selectStockWarningList(query);
    }

    @Override
    public List<InvReportProductOption> selectProductOptions(String keyword, Integer limit,
            Long selectedShopDeptId)
    {
        String normalizedKeyword = normalizeKeyword(keyword);
        int boundedLimit = normalizeOptionLimit(limit);
        InvStock query = prepareReportQuery(new InvStock(), selectedShopDeptId);
        query.getParams().put("keyword", normalizedKeyword);
        query.getParams().put("optionLimit", boundedLimit);
        return reportMapper.selectReportProductOptions(query);
    }

    @Override
    public List<InvReportItemOption> selectItemOptions(String itemType, String keyword, Integer limit,
            Long selectedShopDeptId)
    {
        InvStock query = new InvStock();
        query.setItemType(itemType);
        prepareReportQuery(query, selectedShopDeptId);
        String normalized = normalizeKeyword(keyword);
        query.getParams().put("keyword", normalized == null ? null
                : normalized.replace("!", "!!").replace("%", "!%").replace("_", "!_"));
        query.getParams().put("optionLimit", normalizeOptionLimit(limit));
        return reportMapper.selectReportItemOptions(query);
    }

    private void normalizeMaterialFilter(InvStock query)
    {
        String type = query.getItemType() == null ? null : query.getItemType().trim();
        if (type != null && type.isEmpty()) type = null;
        if (type != null && !Set.of("product", "oe", "gift").contains(type))
            throw new ServiceException("报表物料类型无效");
        if (query.getProductId() != null)
        {
            if (query.getProductId() <= 0 || (type != null && !"product".equals(type))
                    || (query.getItemId() != null && !query.getProductId().equals(query.getItemId())))
                throw new ServiceException("报表商品与物料筛选不一致");
            type = "product";
            query.setItemId(query.getProductId());
        }
        if (query.getItemId() != null && (query.getItemId() <= 0 || type == null))
            throw new ServiceException("报表物料编号必须同时指定有效类型");
        // Existing categoryId is the product-category contract. Do not apply an equal OE/gift ID.
        if (query.getCategoryId() != null && (query.getCategoryId() <= 0
                || (type != null && !"product".equals(type))))
            throw new ServiceException("商品分类不能用于其他物料类型");
        query.setItemType(type);
    }

    private InvStock prepareReportQuery(InvStock stock, Long selectedShopDeptId)
    {
        InvStock query = stock == null ? new InvStock() : stock;
        normalizeMaterialFilter(query);
        validateBusinessDateRange(query);
        normalizeWarningStatus(query);
        query.setSelectedWarehouseId(selectedShopDeptId);
        Long deptId = resolveAndValidateShopDept(requireSelectedShopDept(selectedShopDeptId));
        query.setShopDeptId(deptId);
        query.setWarehouseId(deptId);
        query.getParams().remove("scopeDeptIds");
        return query;
    }

    private void normalizeWarningStatus(InvStock query)
    {
        String status = query.getStockStatus();
        if (status == null || status.trim().isEmpty())
        {
            query.setStockStatus(null);
            return;
        }
        String normalized = status.trim();
        if (!WARNING_STATUSES.contains(normalized))
        {
            throw new ServiceException("库存预警状态无效");
        }
        query.setStockStatus(normalized);
    }

    private String normalizeKeyword(String keyword)
    {
        if (keyword == null)
        {
            return null;
        }
        String normalized = Normalizer.normalize(keyword, Normalizer.Form.NFKC).trim();
        if (normalized.isEmpty())
        {
            return null;
        }
        if (normalized.codePointCount(0, normalized.length()) > 100)
        {
            throw new ServiceException("商品关键词长度不能超过100个字符");
        }
        return normalized;
    }

    private int normalizeOptionLimit(Integer limit)
    {
        int value = limit == null ? 20 : limit;
        if (value < 1)
        {
            throw new ServiceException("商品候选数量必须大于0");
        }
        if (value > 50)
        {
            throw new ServiceException("商品候选数量不能超过50");
        }
        return value;
    }

    private void validateBusinessDateRange(InvStock query)
    {
        String begin = normalizedDate(query.getParams().get("beginTime"), "经营开始日期");
        String end = normalizedDate(query.getParams().get("endTime"), "经营结束日期");
        if (begin != null)
        {
            query.getParams().put("beginTime", begin);
        }
        else
        {
            query.getParams().remove("beginTime");
        }
        if (end != null)
        {
            query.getParams().put("endTime", end);
        }
        else
        {
            query.getParams().remove("endTime");
        }
        if (begin != null && end != null
                && LocalDate.parse(begin).isAfter(LocalDate.parse(end)))
        {
            throw new ServiceException("经营开始日期不能晚于结束日期");
        }
    }

    private String normalizedDate(Object raw, String label)
    {
        if (raw == null)
        {
            return null;
        }
        String value = String.valueOf(raw).trim();
        if (value.isEmpty())
        {
            return null;
        }
        try
        {
            LocalDate parsed = LocalDate.parse(value);
            if (!parsed.toString().equals(value))
            {
                throw new DateTimeParseException("non-canonical", value, 0);
            }
            return value;
        }
        catch (DateTimeParseException ex)
        {
            throw new ServiceException(label + "必须是有效的YYYY-MM-DD");
        }
    }
}

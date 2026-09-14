package com.erp.inventory.service.impl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.InvStockCheck;
import com.erp.inventory.domain.InvStockCheckDetail;

/** Private restart history. Client reference values never participate in inventory writes. */
final class InvStockCheckRestartPolicy
{
    private InvStockCheckRestartPolicy() { }

    static String version(InvStockCheck check)
    {
        JSONObject history = history(check);
        return history == null ? "0" : history.getString("snapshotVersion");
    }

    static void validateInputs(InvStockCheck check, List<InvStockCheckDetail> inputs,
            List<InvStockCheckDetail> stored)
    {
        String expected = version(check);
        if (inputs == null || inputs.isEmpty()) return;
        Set<Long> ids = new HashSet<>();
        for (InvStockCheckDetail row : stored) ids.add(row.getDetailId());
        Set<Long> seen = new HashSet<>();
        for (InvStockCheckDetail input : inputs)
        {
            if (input == null || !ids.contains(input.getDetailId()) || !seen.add(input.getDetailId()))
                throw new ServiceException("盘点明细不存在或重复，请刷新后重试");
            String supplied = input.getSnapshotVersion();
            if (!("0".equals(expected) && (supplied == null || "0".equals(supplied)))
                    && !expected.equals(supplied))
                throw new ServiceException("盘点已重新开始，请刷新后重新核对数量");
            if (input.getActualQty() != null && input.getActualQty().signum() < 0)
                throw new ServiceException("实盘数量不能为负");
            if (input.getRecountQty() != null && input.getRecountQty().signum() < 0)
                throw new ServiceException("复盘数量不能为负");
        }
    }

    static Set<Long> invalidatedIds(InvStockCheck check)
    {
        Set<Long> ids = new HashSet<>();
        String raw = check.getLastInvalidDetailSnapshot();
        if (raw == null || raw.isBlank()) return ids;
        try
        {
            JSONArray changes = JSON.parseArray(raw);
            if (changes == null) throw new IllegalArgumentException();
            for (int index = 0; index < changes.size(); index++)
            {
                JSONObject row = changes.getJSONObject(index);
                Long id = row == null ? null : row.getLong("detailId");
                if (id == null) throw new IllegalArgumentException();
                ids.add(id);
            }
            return ids;
        }
        catch (RuntimeException error)
        {
            throw new ServiceException("盘点失效记录无法读取，暂不能重新盘点");
        }
    }

    static String appendRound(InvStockCheck check, List<InvStockCheckDetail> rows,
            Set<Long> changed, String operator)
    {
        JSONObject data = history(check);
        if (data == null) { data = new JSONObject(); data.put("rounds", new JSONArray()); }
        String token = UUID.randomUUID().toString();
        JSONObject round = new JSONObject();
        round.put("snapshotVersion", token);
        round.put("restartedAt", System.currentTimeMillis());
        round.put("operator", operator);
        JSONArray records = new JSONArray();
        for (InvStockCheckDetail row : rows)
        {
            JSONObject record = new JSONObject();
            record.put("detailId", String.valueOf(row.getDetailId()));
            record.put("checkId", String.valueOf(row.getCheckId()));
            record.put("productId", row.getProductId() == null ? null : String.valueOf(row.getProductId()));
            record.put("productCode", row.getProductCode());
            record.put("unit", row.getUnit());
            record.put("spec", row.getSpec());
            record.put("itemType", row.getItemType());
            record.put("itemId", String.valueOf(row.getItemId()));
            record.put("productName", row.getProductName());
            record.put("bookQty", row.getBookQty());
            record.put("actualQty", row.getActualQty());
            record.put("recountRequired", row.getRecountRequired());
            record.put("recountQty", row.getRecountQty());
            record.put("recountBy", row.getRecountBy());
            record.put("recountTime", row.getRecountTime() == null ? null : row.getRecountTime().getTime());
            record.put("diffQty", row.getDiffQty());
            record.put("diffType", row.getDiffType());
            record.put("costPrice", row.getCostPrice());
            record.put("changed", changed.contains(row.getDetailId()));
            records.add(record);
        }
        round.put("details", records);
        data.getJSONArray("rounds").add(round);
        data.put("snapshotVersion", token);
        return JSON.toJSONString(data);
    }

    static void decorate(InvStockCheck check, List<InvStockCheckDetail> rows)
    {
        JSONObject data = history(check);
        Map<String, JSONObject> references = new HashMap<>();
        if (data != null)
        {
            JSONArray rounds = data.getJSONArray("rounds");
            JSONArray details = rounds.getJSONObject(rounds.size() - 1).getJSONArray("details");
            for (int i = 0; i < details.size(); i++)
            {
                JSONObject record = details.getJSONObject(i);
                references.put(record.getString("detailId"), record);
            }
        }
        if (rows == null) return;
        for (InvStockCheckDetail row : rows)
        {
            row.setSnapshotVersion(data == null ? "0" : data.getString("snapshotVersion"));
            JSONObject record = references.get(String.valueOf(row.getDetailId()));
            row.setPreviousBookQty(record == null ? null : record.getBigDecimal("bookQty"));
            row.setPreviousActualQty(record == null ? null : record.getBigDecimal("actualQty"));
            row.setPreviousRecountQty(record == null ? null : record.getBigDecimal("recountQty"));
            row.setNeedsSnapshotReview(record != null && record.getBooleanValue("changed") && row.getActualQty() == null);
        }
    }

    private static JSONObject history(InvStockCheck check)
    {
        String raw = check.getRestartReferenceSnapshot();
        if (raw == null || raw.isBlank()) return null;
        try
        {
            JSONObject data = JSON.parseObject(raw);
            String token = data.getString("snapshotVersion");
            JSONArray rounds = data.getJSONArray("rounds");
            if (token == null || !UUID.fromString(token).toString().equals(token)
                    || rounds == null || rounds.isEmpty()) throw new IllegalArgumentException();
            JSONObject latest = rounds.getJSONObject(rounds.size() - 1);
            if (!token.equals(latest.getString("snapshotVersion")) || latest.getJSONArray("details") == null)
                throw new IllegalArgumentException();
            return data;
        }
        catch (RuntimeException error)
        {
            throw new ServiceException("盘点重启记录无法读取，请联系管理员核对");
        }
    }
}

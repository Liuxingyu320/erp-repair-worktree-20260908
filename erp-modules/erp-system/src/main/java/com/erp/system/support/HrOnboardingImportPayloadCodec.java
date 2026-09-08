package com.erp.system.support;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.erp.system.domain.HrOnboarding;
import com.erp.system.domain.HrOnboardingImportRow;

/** Versioned staging envelope. Supports rows written before the envelope was introduced. */
@Component
public class HrOnboardingImportPayloadCodec
{
    public String encode(HrOnboarding payload, Map<String,String> rawSourceValues)
    {
        Map<String,Object> envelope = new LinkedHashMap<>();
        envelope.put("schemaVersion", 1);
        envelope.put("payload", payload);
        envelope.put("rawSourceValues", rawSourceValues == null ? Collections.emptyMap() : rawSourceValues);
        return JSON.toJSONString(envelope);
    }

    public void hydrate(HrOnboardingImportRow row)
    {
        if (row == null || row.getPayloadJson() == null || row.getPayloadJson().trim().isEmpty()) return;
        JSONObject value = JSON.parseObject(row.getPayloadJson());
        if (value.containsKey("payload"))
        {
            row.setPayload(value.getObject("payload", HrOnboarding.class));
            Map<String,String> raw = new LinkedHashMap<>();
            JSONObject rawJson = value.getJSONObject("rawSourceValues");
            if (rawJson != null) for (String key : rawJson.keySet()) raw.put(key, rawJson.getString(key));
            row.setRawSourceValues(raw);
        }
        else row.setPayload(value.toJavaObject(HrOnboarding.class));
    }
}

package com.erp.common.core.utils.file;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import com.alibaba.fastjson2.JSON;
import com.erp.common.core.exception.ServiceException;

/** Ordered image references. Null means omitted; an empty JSON array means explicitly cleared. */
public final class ImageUrlList
{
    private ImageUrlList() { }

    public static List<String> read(String stored, String legacy)
    {
        String raw = stored == null ? legacy : stored;
        if (raw == null || raw.isBlank()) return new ArrayList<>();
        if (raw.trim().startsWith("[")) return new ArrayList<>(JSON.parseArray(raw, String.class));
        return new ArrayList<>(java.util.Arrays.stream(raw.split("[,\\r\\n]+"))
                .map(String::trim).filter(value -> !value.isEmpty()).toList());
    }

    public static String cover(String stored, String legacy)
    {
        List<String> urls = read(stored, legacy);
        return urls.isEmpty() ? "" : urls.get(0);
    }

    public static String validateAndWrite(List<String> urls)
    {
        if (urls == null) throw new ServiceException("图片列表不能为空值，清空请传空列表");
        if (urls.size() > 5) throw new ServiceException("图片最多上传5张");
        List<String> normalized = new ArrayList<>();
        for (String raw : urls)
        {
            String value = raw == null ? "" : raw.trim();
            if (value.isEmpty() || value.length() > 1000 || value.contains(",")
                    || value.contains("\\") || value.startsWith("//")
                    || value.chars().anyMatch(c -> c < 32 || c == 127))
                throw new ServiceException("图片地址格式不正确");
            try
            {
                URI uri = URI.create(value);
                if (uri.getUserInfo() != null || (uri.getScheme() != null && uri.getHost() == null) || (uri.getScheme() != null
                        && !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))))
                    throw new IllegalArgumentException();
            }
            catch (IllegalArgumentException invalid) { throw new ServiceException("图片地址格式不正确"); }
            if (!normalized.contains(value)) normalized.add(value);
        }
        return JSON.toJSONString(normalized);
    }

    /** A legacy cover-only edit must not erase an already saved gallery. */
    public static String prepare(String requested, String legacy, String persisted, String persistedLegacy)
    {
        if (requested != null) return validateAndWrite(read(requested, null));
        if (legacy == null) return null;
        List<String> existing = read(persisted, persistedLegacy);
        if (existing.size() > 1)
        {
            if (legacy.trim().equals(existing.get(0))) return null;
            throw new ServiceException("该资料已有多张图片，请刷新页面后使用多图编辑保存");
        }
        return validateAndWrite(read(null, legacy));
    }
}

package com.erp.file.drive.service;

import java.text.Normalizer;
import java.util.Locale;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.exception.DriveException;
import org.springframework.stereotype.Service;

@Service
public class DriveNamePolicy
{
    private static final int MAX_NAME_CODE_POINTS = 200;
    private static final int MAX_SEARCH_CODE_POINTS = 100;

    public String normalize(String value)
    {
        String normalized = normalizeText(value);
        if (normalized.isEmpty() || ".".equals(normalized) || "..".equals(normalized)
                || normalized.indexOf('/') >= 0 || normalized.indexOf('\\') >= 0
                || hasControlCharacter(normalized)
                || codePointLength(normalized) > MAX_NAME_CODE_POINTS)
        {
            throw invalidName();
        }
        return normalized;
    }

    public String normalizeDisplayName(String value)
    {
        return normalize(value);
    }

    public String normalizedKey(String value)
    {
        return normalize(value).toLowerCase(Locale.ROOT);
    }

    public String searchPattern(String keyword)
    {
        String normalized = normalizeText(keyword).toLowerCase(Locale.ROOT);
        if (hasControlCharacter(normalized)
                || codePointLength(normalized) > MAX_SEARCH_CODE_POINTS)
        {
            throw invalidName();
        }
        return normalized
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }

    private static String normalizeText(String value)
    {
        if (value == null)
        {
            return "";
        }
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFKC);
    }

    private static boolean hasControlCharacter(String value)
    {
        return value.codePoints().anyMatch(Character::isISOControl);
    }

    private static int codePointLength(String value)
    {
        return value.codePointCount(0, value.length());
    }

    private static DriveException invalidName()
    {
        return new DriveException(DriveErrorCodes.DRIVE_NAME_CONFLICT, "文件名称不合法");
    }
}

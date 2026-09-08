package com.erp.inventory.util;

import java.nio.charset.Charset;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

public final class InventoryCodeUtils
{
    private static final Charset GBK = Charset.forName("GBK");
    private static final int MAX_CODE_LENGTH = 64;
    private static final int PRODUCT_RANDOM_DIGITS = 6;
    private static final int MAX_RANDOM_ATTEMPTS = 100;
    private static final int[] PINYIN_AREAS = {
            1601, 1637, 1833, 2078, 2274, 2302, 2433, 2594, 2787, 3106, 3212, 3472,
            3635, 3722, 3730, 3858, 4027, 4086, 4390, 4558, 4684, 4925, 5249, 5600
    };
    private static final char[] PINYIN_LETTERS = {
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'J', 'K', 'L', 'M',
            'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'W', 'X', 'Y', 'Z'
    };

    private InventoryCodeUtils()
    {
    }

    public static String resolveCategoryCode(String categoryName, Predicate<String> exists)
    {
        String upper = limit(categoryInitials(categoryName).toUpperCase(Locale.ROOT), MAX_CODE_LENGTH);
        if (!exists.test(upper))
        {
            return upper;
        }
        String lower = upper.toLowerCase(Locale.ROOT);
        if (!exists.test(lower))
        {
            return lower;
        }
        for (int index = 1; index < 10000; index++)
        {
            String suffix = String.valueOf(index);
            String candidate = limit(lower, MAX_CODE_LENGTH - suffix.length()) + suffix;
            if (!exists.test(candidate))
            {
                return candidate;
            }
        }
        throw new IllegalStateException("分类编码已用尽，请调整分类名称");
    }

    public static String generateProductCode(String categoryCode, Predicate<String> exists)
    {
        String prefix = normalizePrefix(categoryCode);
        for (int attempt = 0; attempt < MAX_RANDOM_ATTEMPTS; attempt++)
        {
            String candidate = prefix + "-" + randomDigits(PRODUCT_RANDOM_DIGITS);
            if (!exists.test(candidate))
            {
                return candidate;
            }
        }
        String fallbackSuffix = String.valueOf(System.currentTimeMillis());
        String fallbackPrefix = limit(prefix, MAX_CODE_LENGTH - fallbackSuffix.length() - 1);
        String fallback = fallbackPrefix + "-" + fallbackSuffix;
        if (!exists.test(fallback))
        {
            return fallback;
        }
        throw new IllegalStateException("商品编码生成失败，请稍后重试");
    }

    private static String categoryInitials(String value)
    {
        String text = value == null ? "" : value.trim();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length(); i++)
        {
            char ch = text.charAt(i);
            if (Character.isLetterOrDigit(ch) && ch < 128)
            {
                builder.append(Character.toUpperCase(ch));
                continue;
            }
            Character initial = pinyinInitial(ch);
            if (initial != null)
            {
                builder.append(initial);
            }
        }
        return builder.length() == 0 ? "CAT" : builder.toString();
    }

    private static Character pinyinInitial(char ch)
    {
        try
        {
            byte[] bytes = String.valueOf(ch).getBytes(GBK);
            if (bytes.length < 2)
            {
                return null;
            }
            int high = bytes[0] & 0xff;
            int low = bytes[1] & 0xff;
            int area = (high - 160) * 100 + (low - 160);
            for (int i = PINYIN_AREAS.length - 1; i >= 0; i--)
            {
                if (area >= PINYIN_AREAS[i])
                {
                    return PINYIN_LETTERS[i];
                }
            }
        }
        catch (Exception ignored)
        {
            return null;
        }
        return null;
    }

    private static String normalizePrefix(String categoryCode)
    {
        String prefix = categoryCode == null ? "" : categoryCode.trim();
        if (prefix.isEmpty())
        {
            prefix = "CAT";
        }
        return limit(prefix, MAX_CODE_LENGTH - PRODUCT_RANDOM_DIGITS - 1);
    }

    private static String randomDigits(int length)
    {
        int bound = (int) Math.pow(10, length);
        int value = ThreadLocalRandom.current().nextInt(bound);
        return String.format("%0" + length + "d", value);
    }

    private static String limit(String value, int max)
    {
        if (value == null)
        {
            return "";
        }
        if (value.length() <= max)
        {
            return value;
        }
        return value.substring(0, Math.max(0, max));
    }
}

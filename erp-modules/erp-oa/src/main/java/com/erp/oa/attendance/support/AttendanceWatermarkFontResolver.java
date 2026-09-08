package com.erp.oa.attendance.support;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import com.erp.common.core.exception.ServiceException;

/** Selects a real installed font that can render every watermark character. */
@Component
public class AttendanceWatermarkFontResolver
{
    static final List<String> CANDIDATES = List.of("PingFang SC", "STHeiti",
            "Heiti SC", "Songti SC", "Noto Sans CJK SC",
            "Microsoft YaHei", "SimSun", "WenQuanYi Micro Hei",
            "SansSerif");

    public Font resolve(String text, int style, int size)
    {
        String sample = text == null ? "员工门店时间地点坐标精度编号" : text;
        Set<String> available = new LinkedHashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment()
                        .getAvailableFontFamilyNames()));
        for (String name : CANDIDATES)
        {
            if (available.contains(name))
            {
                Font font = new Font(name, style, size);
                if (font.canDisplayUpTo(sample) < 0) return font;
            }
        }
        // Some distributions use a different family name for an otherwise
        // suitable CJK font.  Search installed families before failing closed.
        for (String name : available)
        {
            Font font = new Font(name, style, size);
            if (font.canDisplayUpTo(sample) < 0) return font;
        }
        throw new ServiceException("ATTENDANCE_CJK_FONT_UNAVAILABLE");
    }
}

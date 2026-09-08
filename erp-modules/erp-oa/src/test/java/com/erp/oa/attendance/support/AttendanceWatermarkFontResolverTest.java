package com.erp.oa.attendance.support;

import static org.assertj.core.api.Assertions.assertThat;
import java.awt.Font;
import org.junit.jupiter.api.Test;

class AttendanceWatermarkFontResolverTest
{
    @Test
    void selectedInstalledFontCanRenderChineseWatermark()
    {
        String sample = "员工张三门店长沙店时间地点坐标精度编号";
        Font font = new AttendanceWatermarkFontResolver().resolve(sample,
                Font.BOLD, 20);
        assertThat(font.canDisplayUpTo(sample)).isEqualTo(-1);
        assertThat(font.getSize()).isEqualTo(20);
    }
}

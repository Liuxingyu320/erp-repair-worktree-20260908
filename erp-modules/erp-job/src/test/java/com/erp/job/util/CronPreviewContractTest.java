package com.erp.job.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.quartz.CronExpression;

/** Same fixed timezone/base/expected fixtures as cronSafeRoundTrip.test.js. */
class CronPreviewContractTest
{
    @Test
    void fixedQuartzExamplesAgreeWithBoundedBrowserPreview() throws Exception
    {
        TimeZone zone = TimeZone.getTimeZone("Asia/Shanghai");
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        format.setTimeZone(zone);
        Date from = format.parse("2026-09-13 00:00:00");
        String[][] fixtures = {
            { "0 0 9 ? * 6#2", "2026-10-09 09:00:00" },
            { "0 0 9 ? * 7#5", "2026-10-31 09:00:00" },
            { "0 0 9 ? * 1L", "2026-09-27 09:00:00" },
            { "0 0 9 1W * ?", "2026-10-01 09:00:00" },
            { "0 0 0 29 2 ?", "2028-02-29 00:00:00" },
            { "0 0 0 LW * ?", "2026-09-30 00:00:00" },
            { "0 0 0 * * ? 2030-2035", "2030-01-01 00:00:00" },
            { "0 0 0 * * ? 2030/2", "2030-01-01 00:00:00" }
        };
        for (String[] fixture : fixtures)
        {
            CronExpression cron = new CronExpression(fixture[0]);
            cron.setTimeZone(zone);
            assertEquals(fixture[1], format.format(cron.getNextValidTimeAfter(from)), fixture[0]);
        }
    }
}

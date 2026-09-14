package com.erp.system.domain.vo;

import java.util.List;
import com.erp.common.core.web.page.TableDataInfo;

/** Keeps the existing rows/total/code/msg contract and adds separate totals. */
public class SysNoticeReadPage extends TableDataInfo
{
    private static final long serialVersionUID = 1L;
    private final SysNoticeReadSummary summary;
    public SysNoticeReadPage(List<SysNoticeReadUserVo> rows, long total, SysNoticeReadSummary summary)
    {
        super(rows, total);
        setCode(200);
        setMsg("查询成功");
        this.summary = summary;
    }
    public SysNoticeReadSummary getSummary() { return summary; }
}

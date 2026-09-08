package com.erp.common.core.utils.html;

import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

/**
 * 富文本白名单净化工具。
 */
public final class HtmlSanitizer
{
    private static final PolicyFactory NOTICE_POLICY = Sanitizers.BLOCKS
            .and(Sanitizers.FORMATTING)
            .and(Sanitizers.TABLES)
            .and(Sanitizers.LINKS)
            .and(Sanitizers.IMAGES);

    private HtmlSanitizer()
    {
    }

    /**
     * 净化公告等需要保留基础排版的富文本。
     *
     * @param html 原始富文本
     * @return 仅包含白名单元素和属性的富文本；输入为 null 时返回 null
     */
    public static String sanitize(String html)
    {
        return html == null ? null : NOTICE_POLICY.sanitize(html);
    }
}

package com.erp.system.service.support;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Component;

/**
 * Applies the single allowlist used for notice rich text.
 */
@Component
public class NoticeHtmlSanitizer
{
    private static final PolicyFactory NOTICE_POLICY = new HtmlPolicyBuilder()
            .allowElements("p", "br", "h1", "h2", "h3", "h4", "h5", "h6")
            .allowElements("strong", "b", "em", "i", "u", "s")
            .allowElements("ul", "ol", "li", "blockquote", "pre", "code")
            .allowElements("a")
            .allowUrlProtocols("http", "https", "mailto")
            .allowAttributes("href", "title").onElements("a")
            .allowWithoutAttributes("a")
            .requireRelNofollowOnLinks()
            .toFactory();

    public String sanitize(String html)
    {
        return html == null ? null : NOTICE_POLICY.sanitize(html);
    }
}

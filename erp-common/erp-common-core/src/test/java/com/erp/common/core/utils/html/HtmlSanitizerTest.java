package com.erp.common.core.utils.html;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("富文本安全净化")
class HtmlSanitizerTest
{
    @Test
    @DisplayName("移除可执行标记但保留可见正文")
    void removesExecutableMarkupButKeepsVisibleText()
    {
        String input = "<p onclick=\"alert(1)\">正文<script>alert(2)</script>"
                + "<a href=\"javascript:alert(3)\">链接</a>"
                + "<img src=\"x\" onerror=\"alert(4)\"></p>";

        String sanitized = HtmlSanitizer.sanitize(input);

        assertThat(sanitized).contains("正文", "链接");
        assertThat(sanitized).doesNotContainIgnoringCase("<script", "onclick", "javascript:", "onerror");
    }

    @Test
    @DisplayName("保留公告需要的安全富文本")
    void keepsApprovedRichText()
    {
        String input = "<h2>标题</h2><p><strong>重点</strong></p>"
                + "<ul><li>一</li></ul>"
                + "<table><tbody><tr><td>值</td></tr></tbody></table>"
                + "<a href=\"https://example.com/help\">帮助</a>";

        assertThat(HtmlSanitizer.sanitize(input))
                .contains("<h2>标题</h2>", "<strong>重点</strong>", "<ul>", "<table>",
                        "https://example.com/help");
    }

    @Test
    @DisplayName("空内容保持稳定")
    void handlesNullAndBlankContent()
    {
        assertThat(HtmlSanitizer.sanitize(null)).isNull();
        assertThat(HtmlSanitizer.sanitize("")).isEmpty();
        assertThat(HtmlSanitizer.sanitize("   ")).isEqualTo("   ");
    }
}

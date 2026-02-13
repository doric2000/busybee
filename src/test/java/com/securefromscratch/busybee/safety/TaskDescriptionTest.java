package com.securefromscratch.busybee.safety;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TaskDescriptionTest {

    @Test
    void allowsMultilineLinksImagesAndFormatting() {
        TaskDescription desc = new TaskDescription(
                "Line1\n" +
                "<b>bold</b> <i>italic</i> <u>underline</u> " +
                "<a href=\"https://example.com\">link</a> " +
                "<img src=\"https://example.com/a.png\" alt=\"a\">\n" +
                "Line2"
        );

        String html = desc.value();

        assertTrue(html.contains("<br>"), "Expected newlines to be preserved as <br>");
        assertTrue(html.contains("<b>bold</b>") || html.contains("<strong>bold</strong>"), "Expected bold formatting to remain");
        assertTrue(html.contains("<i>italic</i>") || html.contains("<em>italic</em>"), "Expected italic formatting to remain");
        assertTrue(html.contains("<u>underline</u>"), "Expected underline formatting to remain");
        assertTrue(html.contains("href=\"https://example.com\""), "Expected https link to remain");
        assertTrue(html.contains("<img") && html.contains("src=\"https://example.com/a.png\""), "Expected https image src to remain");
    }

    @Test
    void blocksScriptTags() {
        TaskDescription desc = new TaskDescription("Hello <script>alert(1)</script> World");
        String html = desc.value();

        assertFalse(html.toLowerCase().contains("<script"), "Script tag should be removed");
        assertFalse(html.toLowerCase().contains("alert(1)"), "Script content should not remain");
    }

    @Test
    void blocksJavascriptLinks() {
        TaskDescription desc = new TaskDescription("<a href=\"javascript:alert(1)\">x</a>");
        String html = desc.value();

        assertFalse(html.toLowerCase().contains("javascript:"), "javascript: links must be blocked");
    }

    @Test
    void stripsEventHandlers() {
        TaskDescription desc = new TaskDescription("<img src=\"https://example.com/a.png\" onerror=\"alert(1)\">");
        String html = desc.value();

        assertFalse(html.toLowerCase().contains("onerror"), "Event handler attributes must be removed");
        assertTrue(html.contains("src=\"https://example.com/a.png\""), "Image src should remain");
    }
}

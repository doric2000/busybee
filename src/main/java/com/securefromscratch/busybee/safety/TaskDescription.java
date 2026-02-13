package com.securefromscratch.busybee.safety;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public record TaskDescription(String value) {
    public static final int MAX_LENGTH = 2000;
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskDescription.class);

    // desc is rendered on the client via innerHTML, so we must sanitize to prevent XSS.
    // Allowed functionality (per professor): multiline text + links (<a>) + images (<img>) + bold/italic/underline.
    private static final Safelist SAFE_HTML = new Safelist()
            .addTags("a", "img", "b", "strong", "i", "em", "u", "br")
            .addAttributes("a", "href", "title")
            .addAttributes("img", "src", "alt", "title")
            .addProtocols("a", "href", "http", "https", "mailto")
            .addProtocols("img", "src", "http", "https")
            .preserveRelativeLinks(true);

    private static final Document.OutputSettings OUTPUT_SETTINGS = new Document.OutputSettings()
            .prettyPrint(false);

    @JsonCreator
    public TaskDescription(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("desc: required");
        }

        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("desc: length must be <= " + MAX_LENGTH);
        }

        // Textarea sends plain text; preserve user-entered newlines as HTML line breaks.
        String preProcessed = value.replace("\r\n", "\n").replace("\r", "\n").replace("\n", "<br>\n");
        String sanitized = Jsoup.clean(preProcessed, "", SAFE_HTML, OUTPUT_SETTINGS);

        // Log only metadata (no raw user input).
        if (!sanitized.equals(preProcessed)) {
            LOGGER.warn("Task description sanitized; lengthBefore={}, lengthAfter={}", preProcessed.length(), sanitized.length());
        }

        this.value = sanitized;
    }
    @JsonValue
    public String value() { return value; }
}

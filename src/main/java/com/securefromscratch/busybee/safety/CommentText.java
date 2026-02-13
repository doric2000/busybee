package com.securefromscratch.busybee.safety;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

public class CommentText {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommentText.class);
    private static final int MIN_LENGTH = 1;
    private static final int MAX_LENGTH = 500;
    private static final Pattern SAFE_INPUT_PATTERN = Pattern.compile("^[a-zA-Z0-9\\u0590-\\u05FF\\s.,!?\"'():;\\-_/]*$");

    private final String m_text;

    @JsonCreator
    public CommentText(String text) {
        if (text == null || text.isBlank()) {
            LOGGER.warn("Invalid comment text: empty");
            throw new IllegalArgumentException("Comment text is required");
        }
        if (text.length() < MIN_LENGTH || text.length() > MAX_LENGTH) {
            LOGGER.warn("Invalid comment text length: {}", text.length());
            throw new IllegalArgumentException("Comment text length is invalid");
        }
        if (!SAFE_INPUT_PATTERN.matcher(text).matches()) {
            LOGGER.warn("Invalid comment text: '{}'", text);
            throw new IllegalArgumentException("Comment text contains invalid characters");
        }
        m_text = text;
    }

    @JsonValue
    public String value() {
        return m_text;
    }

    public String get() {
        return m_text;
    }
}

package com.securefromscratch.busybee.safety;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.regex.Pattern;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public record TaskName(String value) {
    public static final int MIN_LENGTH = 1;
    public static final int MAX_LENGTH = 100;
    // Allow spaces/tabs but disallow newlines in a task name.
    private static final Pattern SAFE_INPUT_PATTERN = Pattern.compile("^[a-zA-Z0-9 \t.,!?\\-_()\\u0590-\\u05FF]*$");
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskName.class);
    @JsonCreator
    public TaskName(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("name: required");
        }
        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("name: length must be between " + MIN_LENGTH + " and " + MAX_LENGTH);
        }
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("name: must be single-line (no newlines)");
        }
        if (!SAFE_INPUT_PATTERN.matcher(value).matches()) {
            // Avoid logging raw user input (could be sensitive)
            LOGGER.warn("Invalid task name characters; length={}", value.length());
            throw new IllegalArgumentException("name: contains invalid characters");
        }
        this.value = value;
    }
    @JsonValue
    public String value() { return value; }
}

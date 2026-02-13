package com.securefromscratch.busybee.safety;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.regex.Pattern;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public record ResponsibilityName(String value) {
    private static final Pattern SAFE_INPUT_PATTERN = Pattern.compile("^[a-zA-Z0-9\\s.,!?\\-_()\\u0590-\\u05FF]*$");
    private static final Logger LOGGER = LoggerFactory.getLogger(ResponsibilityName.class);
    @JsonCreator
    public ResponsibilityName(String value) {
        if (value != null && !SAFE_INPUT_PATTERN.matcher(value).matches()) {
            LOGGER.warn("Invalid responsibility name: '{}'", value);
            throw new IllegalArgumentException("Responsibility name contains invalid characters");
        }
        this.value = value;
    }
    @JsonValue
    public String value() { return value; }
}

package com.securefromscratch.busybee.safety;

import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.regex.Pattern;

public record Username(@NotNull String value) {
    public static final int MIN_LENGTH = 1;
    public static final int MAX_LENGTH = 20;
    // Requirement: username is a letter followed by zero or more letters/digits/spaces.
    public static final Pattern SAFE_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9 ]*$");

    @JsonCreator
    public Username(String value) {
        if (value == null) {
            throw new IllegalArgumentException("username: cannot be blank");
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("username: cannot be blank");
        }
        if (trimmed.length() < MIN_LENGTH || trimmed.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("username: length must be between " + MIN_LENGTH + " and " + MAX_LENGTH);
        }
        if (!SAFE_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("username: must match " + SAFE_PATTERN.pattern());
        }
        this.value = trimmed;
    }
    @JsonValue
    public String value() { return value; }
}

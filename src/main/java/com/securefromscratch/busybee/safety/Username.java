package com.securefromscratch.busybee.safety;

import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.regex.Pattern;

public record Username(@NotNull String value) {
    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 20;
    public static final Pattern SAFE_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,20}$");

    @JsonCreator
    public Username(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("username: cannot be blank");
        }
        if (!SAFE_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("username: must match " + SAFE_PATTERN.pattern());
        }
        this.value = value;
    }
    @JsonValue
    public String value() { return value; }
}

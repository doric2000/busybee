package com.securefromscratch.busybee.safety;

import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public record Username(@NotNull String value) {
    @JsonCreator
    public Username(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Username cannot be blank");
        if (!value.matches("[a-zA-Z0-9_]{3,20}")) throw new IllegalArgumentException("Username contains invalid characters");
        this.value = value;
    }
    @JsonValue
    public String value() { return value; }
}

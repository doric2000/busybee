package com.securefromscratch.busybee.safety;

import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public record Password(@NotNull String value) {
    @JsonCreator
    public Password(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Password cannot be blank");
        if (!value.matches("[a-zA-Z0-9!@#$%^&*()_+=-]{8,32}")) throw new IllegalArgumentException("Password contains invalid characters");
        this.value = value;
    }
    @JsonValue
    public String value() { return value; }
}

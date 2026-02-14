package com.securefromscratch.busybee.safety;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

public class ImageName {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImageName.class);
    private static final int MIN_LENGTH = 1;
    private static final int MAX_LENGTH = 64;
    private static final Pattern SAFE_INPUT_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._/-]*$");

    private final String m_name;

    @JsonCreator
    public ImageName(String imgName) {
        if (imgName == null || imgName.isBlank()) {
            throw new IllegalArgumentException("Image name is required");
        }
        if (imgName.length() < MIN_LENGTH || imgName.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Image name length is invalid");
        }
        if (!SAFE_INPUT_PATTERN.matcher(imgName).matches()) {
            LOGGER.warn("Invalid image name characters; length={}", imgName.length());
            throw new IllegalArgumentException("Image name contains invalid characters");
        }
        if (imgName.contains("\\")) {
            LOGGER.warn("Invalid image name path separator; length={}", imgName.length());
            throw new IllegalArgumentException("Image name contains invalid path separator");
        }
        if (imgName.contains("..")) {
            LOGGER.warn("Invalid image name path traversal attempt; length={}", imgName.length());
            throw new IllegalArgumentException("Image name contains invalid sequence");
        }
        m_name = imgName;
    }

    @JsonValue
    public String value() {
        return m_name;
    }

    public String get() {
        return m_name;
    }
}

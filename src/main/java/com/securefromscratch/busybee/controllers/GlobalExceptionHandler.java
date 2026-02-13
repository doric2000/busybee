package com.securefromscratch.busybee.controllers;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import jakarta.validation.ConstraintViolationException;

@ControllerAdvice
@ResponseBody
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record ErrorResponse(String error) {}

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> illegalArgument(IllegalArgumentException ex) {
        // Avoid logging user-provided content (PII/credentials). Type is enough for debugging.
        LOGGER.warn("Rejected request: IllegalArgumentException");
        String message = Optional.ofNullable(ex.getMessage()).orElse("request: invalid");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(message));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validationFailed(MethodArgumentNotValidException ex) {
        LOGGER.warn("Rejected request: validation failed");
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .orElse("request: invalid");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> constraintViolation(ConstraintViolationException ex) {
        LOGGER.warn("Rejected request: constraint violation");
        String message = ex.getConstraintViolations().stream()
                .findFirst()
                .map(v -> Optional.ofNullable(v.getMessage()).orElse("request: invalid"))
                .orElse("request: invalid");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> messageNotReadable(HttpMessageNotReadableException ex) {
        // Commonly triggered by malformed JSON or wrong field types.
        LOGGER.warn("Rejected request: unreadable request body");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("request: malformed"));
    }

    @ExceptionHandler(NoSuchFileException.class)
    public ResponseEntity<ErrorResponse> resourceNotFound(NoSuchFileException ex) {
        // Don't leak file paths.
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("resource: not found"));
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<ErrorResponse> io(IOException ex) {
        // Unexpected IO errors shouldn't look like 404.
        LOGGER.error("Server IO failure", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("server: io error"));
    }
}

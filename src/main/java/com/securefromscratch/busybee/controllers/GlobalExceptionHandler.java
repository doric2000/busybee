package com.securefromscratch.busybee.controllers;

import com.securefromscratch.busybee.storage.TaskNotFoundException;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.ConstraintViolationException;

@ControllerAdvice
@ResponseBody
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record ErrorResponse(String error) {}

    private static String requestPath(HttpServletRequest request) {
        return request != null ? request.getRequestURI() : "?";
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> illegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        // Avoid logging user-provided content (PII/credentials). Path + type is enough.
        LOGGER.warn("Rejected request: path={}, type=IllegalArgumentException", requestPath(request));
        String message = Optional.ofNullable(ex.getMessage()).orElse("request: invalid");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(message));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validationFailed(MethodArgumentNotValidException ex, HttpServletRequest request) {
        LOGGER.warn("Rejected request: path={}, type=MethodArgumentNotValidException", requestPath(request));
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .orElse("request: invalid");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> constraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        LOGGER.warn("Rejected request: path={}, type=ConstraintViolationException", requestPath(request));
        String message = ex.getConstraintViolations().stream()
                .findFirst()
                .map(v -> Optional.ofNullable(v.getMessage()).orElse("request: invalid"))
                .orElse("request: invalid");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> messageNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        // Commonly triggered by malformed JSON or wrong field types.
        LOGGER.warn("Rejected request: path={}, type=HttpMessageNotReadableException", requestPath(request));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("request: malformed"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> responseStatus(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatusCode statusCode = ex.getStatusCode();
        LOGGER.warn("Rejected request: path={}, type=ResponseStatusException, status={}", requestPath(request), statusCode.value());
        String message = Optional.ofNullable(ex.getReason()).orElse("request: rejected");
        return ResponseEntity.status(statusCode).body(new ErrorResponse(message));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ErrorResponse> security(SecurityException ex, HttpServletRequest request) {
        // SecurityException messages may contain filesystem paths; don't leak.
        LOGGER.warn("Rejected request: path={}, type=SecurityException", requestPath(request));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("request: invalid path"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> accessDenied(AccessDeniedException ex, HttpServletRequest request) {
        String user = request != null && request.getUserPrincipal() != null
                ? request.getUserPrincipal().getName()
                : "anonymous";
        LOGGER.warn("Authorization failure: user={} path={}", user, requestPath(request));
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse("access denied"));
    }

    @ExceptionHandler(TaskNotFoundException.class)
    public ResponseEntity<ErrorResponse> taskNotFound(TaskNotFoundException ex, HttpServletRequest request) {
        // Avoid logging the taskId. Path + type is sufficient.
        LOGGER.warn("Rejected request: path={}, type=TaskNotFoundException", requestPath(request));
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("task: not found"));
    }

    @ExceptionHandler(NoSuchFileException.class)
    public ResponseEntity<ErrorResponse> resourceNotFound(NoSuchFileException ex) {
        // Don't leak file paths.
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("resource: not found"));
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<ErrorResponse> io(IOException ex, HttpServletRequest request) {
        // Unexpected IO errors shouldn't look like 404.
        // Includes failures like "cannot save task" (saveTasks throws IOException).
        LOGGER.error("Server IO failure: path={}, type={}", requestPath(request), ex.getClass().getSimpleName(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("server: io error"));
    }
}

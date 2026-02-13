package com.securefromscratch.busybee.controllers;

import java.io.IOException;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;

public class GlobalExceptionHandler {
    @ExceptionHandler(IOException.class)
    public ResponseEntity<String> resourceNotFound(IOException ex) {
        return ResponseEntity.notFound().build();
    }
}

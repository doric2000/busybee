package com.securefromscratch.busybee.controllers;

import com.securefromscratch.busybee.auth.UsernamePasswordDetailsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.web.csrf.CsrfToken;

import jakarta.validation.constraints.NotNull;
import com.securefromscratch.busybee.safety.Username;
import com.securefromscratch.busybee.safety.Password;

@Controller
public class UsersController {
    private final UsernamePasswordDetailsService usersStorage;
    private final PasswordEncoder passwordEncoder;
    private final CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();

    public UsersController(UsernamePasswordDetailsService usersStorage, PasswordEncoder passwordEncoder) {
        this.usersStorage = usersStorage;
        this.passwordEncoder = passwordEncoder;
    }

    // טיפוסים מיוחדים מוגדרים תחת safety
    public record NewUserCreationFields(@NotNull Username username, @NotNull Password password) {}
    public record ErrorCreatingUser(String error) {}
    public record UserCreationSuccess(String redirectTo) {}

    @PostMapping("/register")
    public ResponseEntity registerNewUser(
            @Valid @RequestBody NewUserCreationFields request
    ) {
        // Validation happens in the safety types + Bean Validation. Errors propagate to GlobalExceptionHandler.
        String encodedPassword = passwordEncoder.encode(request.password().value());
        usersStorage.createUser(request.username().value(), encodedPassword, new String[]{"TRIAL"});
        return ResponseEntity.ok(new UserCreationSuccess("main/main.html"));
    }

}

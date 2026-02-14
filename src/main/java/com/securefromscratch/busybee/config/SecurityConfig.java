package com.securefromscratch.busybee.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import jakarta.servlet.http.HttpServletRequest;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityConfig.class);

    private static String safeLogUser(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String sanitized = value
                .replace('\r', '_')
                .replace('\n', '_')
                .replace('\t', '_');
        if (sanitized.length() > 64) {
            return sanitized.substring(0, 64) + "...";
        }
        return sanitized;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        SavedRequestAwareAuthenticationSuccessHandler successHandler = new SavedRequestAwareAuthenticationSuccessHandler();
        successHandler.setDefaultTargetUrl("/main/main.html");
        successHandler.setAlwaysUseDefaultTargetUrl(true);

        SimpleUrlAuthenticationFailureHandler failureHandler = new SimpleUrlAuthenticationFailureHandler("/login?error");

        SimpleUrlLogoutSuccessHandler logoutSuccessHandler = new SimpleUrlLogoutSuccessHandler();
        logoutSuccessHandler.setDefaultTargetUrl("/");

        return http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/index.html", "/register", "/register/**", "/gencsrftoken", "/static/**", "/*.css", "/*.js", "/*.webp", "/*.png", "/error").permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                // Prevent browser redirects to the login page for media fetches.
                // This makes <img src="/image?..."> fail with 401 instead of HTML login.
                .defaultAuthenticationEntryPointFor(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                    new AntPathRequestMatcher("/image")
                )
                .defaultAuthenticationEntryPointFor(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                    new AntPathRequestMatcher("/attachment")
                )
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    String user = request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : "anonymous";
                    LOGGER.warn("Authorization failure: user={} path={}", safeLogUser(user), request.getRequestURI());
                    response.sendError(HttpStatus.FORBIDDEN.value(), "access denied");
                })
            )
            .formLogin(form -> form
                .successHandler((request, response, authentication) -> {
                    LOGGER.info("Login success: user={}", safeLogUser(authentication.getName()));
                    successHandler.onAuthenticationSuccess(request, response, authentication);
                })
                .failureHandler((request, response, exception) -> {
                    String user = safeLogUser(request.getParameter("username"));
                    LOGGER.warn("Login failed: user={} remote={}", user, request.getRemoteAddr());
                    failureHandler.onAuthenticationFailure(request, response, exception);
                })
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessHandler((request, response, authentication) -> {
                    String user = authentication != null ? authentication.getName() : "anonymous";
                    LOGGER.info("Logout: user={}", safeLogUser(user));
                    logoutSuccessHandler.onLogoutSuccess(request, response, authentication);
                })
            )
            .build();

    }
}

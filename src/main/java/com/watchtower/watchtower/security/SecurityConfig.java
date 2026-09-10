package com.watchtower.watchtower.security;

import com.watchtower.watchtower.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * Stateless JWT auth: no sessions, no CSRF (nothing cookie-based to forge),
 * a token-parsing filter ahead of the standard auth filter, and custom
 * 401/403 handlers so unauthenticated/unauthorized responses stay in the
 * app's own ErrorResponse shape instead of Spring Security's default pages.
 * <p>
 * Only approve/reject are role-gated (APPROVER) - every other /incidents/**
 * route just needs a valid, authenticated user (VIEWER or APPROVER), per the
 * doc's role table. /mcp/** stays open: it's the agent's own in-process tool
 * surface, not part of this phase's user-facing auth scope.
 */
@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, ObjectMapper objectMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Allows the Next.js dev server (Phase 8) to call this API cross-origin.
     * Origins are restricted to local dev ports rather than wildcarded,
     * since credentials (the Authorization header) are involved.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:3000"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/health").permitAll()
                        .requestMatchers("/mcp/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/incidents/*/approve", "/incidents/*/reject").hasRole("APPROVER")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(this::handleUnauthenticated)
                        .accessDeniedHandler(this::handleAccessDenied))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private void handleUnauthenticated(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex) throws IOException {
        writeError(response, HttpStatus.UNAUTHORIZED, "Unauthorized", "Authentication is required");
    }

    private void handleAccessDenied(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex) throws IOException {
        writeError(response, HttpStatus.FORBIDDEN, "Forbidden", "You do not have permission to perform this action");
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String error, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                new ErrorResponse(Instant.now(), status.value(), error, List.of(message)));
    }
}

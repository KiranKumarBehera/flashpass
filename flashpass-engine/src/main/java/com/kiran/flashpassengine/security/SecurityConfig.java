package com.kiran.flashpassengine.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setContentType("application/json");
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().write("{\"status\":401,\"error\":\"UNAUTHORIZED\",\"message\":\"Authentication required. Valid Bearer JWT token missing or expired.\"}");
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setContentType("application/json");
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.getWriter().write("{\"status\":403,\"error\":\"FORBIDDEN\",\"message\":\"Access Denied: Insufficient permissions for this resource.\"}");
                })
            )
            .authorizeHttpRequests(auth -> auth
                // Allow all CORS pre-flight OPTIONS requests
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // Public Authentication Endpoints
                .requestMatchers("/api/auth/login", "/api/auth/register").permitAll()

                // Public Browsing & Viewing Catalog
                .requestMatchers(HttpMethod.GET, "/api/events/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/venues/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/seats/**").permitAll()

                // Public Simulation & Real-time WebSockets
                .requestMatchers("/api/queue/**").permitAll()
                .requestMatchers("/api/scalability/**").permitAll()
                .requestMatchers("/api/seats/*/race-test").permitAll()
                .requestMatchers("/ws-flashpass/**").permitAll()

                // Organizer & Admin Protected Endpoints
                .requestMatchers(HttpMethod.POST, "/api/venues/**").hasAnyAuthority("ROLE_ORGANIZER", "ROLE_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/events").hasAnyAuthority("ROLE_ORGANIZER", "ROLE_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/events/*/reset").hasAnyAuthority("ROLE_ORGANIZER", "ROLE_ADMIN")
                .requestMatchers("/api/analytics/**").hasAnyAuthority("ROLE_ORGANIZER", "ROLE_ADMIN")

                // Authenticated User Endpoints (Lock, Book, Release, Pay, Tickets)
                .requestMatchers("/api/seats/*/lock").authenticated()
                .requestMatchers("/api/seats/*/book").authenticated()
                .requestMatchers("/api/seats/*/release").authenticated()
                .requestMatchers("/api/tickets/my-tickets").authenticated()
                .requestMatchers("/api/payments/**").authenticated()
                .requestMatchers("/api/auth/me").authenticated()

                // All other endpoints require authentication
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Idempotency-Key", "X-Requested-With", "Accept"));
        config.setExposedHeaders(Arrays.asList("Authorization", "Idempotency-Key"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}

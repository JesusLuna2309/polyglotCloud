package com.jesusLuna.polyglotCloud.security;

import java.util.Arrays;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                // 🌐 SWAGGER/OpenAPI - Acceso público
                .requestMatchers(
                    "/swagger-ui/**",
                    "/swagger-ui.html", 
                    "/v3/api-docs/**",
                    "/api-docs/**",
                    "/webjars/**"
                ).permitAll()
                
                // 🔓 ENDPOINTS PÚBLICOS DE AUTENTICACIÓN
                .requestMatchers(
                    "/auth/login",
                    "/auth/register", 
                    "/auth/refresh-token",
                    "/auth/verify-email"
                ).permitAll()
                
                // 📊 ACTUATOR (opcional)
                .requestMatchers("/actuator/**").permitAll()
                
                // 🔒 TODO LO DEMÁS REQUIERE AUTENTICACIÓN
                .anyRequest().authenticated()
            );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.asList("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}

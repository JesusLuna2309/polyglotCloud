package com.jesusLuna.polyglotCloud.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * Security configuration properties for login attempt limits and lockout durations
 */
@Configuration
@ConfigurationProperties(prefix = "app.security")
@Data
public class SecurityProperties {

    /**
     * Maximum failed login attempts before temporary lockout
     * Default: 5
     */
    private int maxFailedAttemptsTemp = 5;

    /**
     * Maximum failed login attempts before permanent lockout and deactivation
     * Default: 10
     */
    private int maxFailedAttemptsPerm = 10;

    /**
     * Lockout duration in minutes for temporary lockout (after maxFailedAttemptsTemp)
     * Default: 30 minutes
     */
    private int lockoutDurationMinutes = 30;

    /**
     * Lockout duration in days for permanent lockout (after maxFailedAttemptsPerm)
     * Default: 1 day
     */
    private int lockoutDurationDays = 1;
}

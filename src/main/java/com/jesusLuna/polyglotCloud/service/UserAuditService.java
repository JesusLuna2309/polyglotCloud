package com.jesusLuna.polyglotCloud.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.jesusLuna.polyglotCloud.models.LoginAttempt;
import com.jesusLuna.polyglotCloud.models.User;
import com.jesusLuna.polyglotCloud.repository.LoginAttemptRepository;
import com.jesusLuna.polyglotCloud.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAuditService {

    private final UserRepository userRepository;
    private final LoginAttemptRepository loginAttemptRepository;

    /**
     * Registra intento fallido en transacción separada e independiente
     * No se revierte aunque falle la transacción principal
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailedLoginAttempt(UUID userId) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            
            if (user != null) {
                user.recordFailedLogin();
                userRepository.saveAndFlush(user);
                
                log.info("Failed login attempt recorded for user: {} (attempts: {})",
                        user.getUsername(), user.getFailedLoginAttempts());

                // Verificar si se bloqueó
                if (!user.isAccountNonLocked()) {
                    log.warn("User account locked: {} (attempts: {})", 
                            user.getUsername(), user.getFailedLoginAttempts());
                }
                
                if (!user.isActive()) {
                    log.warn("User account disabled: {} (attempts: {})", 
                            user.getUsername(), user.getFailedLoginAttempts());
                }
            }
        } catch (Exception ex) {
            log.error("Error recording failed login attempt for user: {}", userId, ex);
        }
    }

    /**
     * Records a failed login attempt with full details (IP address, user agent, failure reason)
     * Uses a separate transaction that won't rollback even if the main transaction fails
     * @return Updated user with incremented failed login attempts, or null if user doesn't exist
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public User recordFailedLoginAttempt(UUID userId, String ipAddress, String userAgent, String failureReason) {
        try {
            User user = userId != null ? userRepository.findById(userId).orElse(null) : null;
            
            // Record login attempt in database
            LoginAttempt loginAttempt = LoginAttempt.builder()
                    .user(user)
                    .attemptTimestamp(Instant.now())
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .success(false)
                    .failureReason(failureReason)
                    .build();
            
            loginAttemptRepository.saveAndFlush(loginAttempt);
            
            // Only increment user failed login counter for invalid password attempts.
            // Do NOT increment for "Account is locked" or "Account is disabled" - otherwise
            // trying while locked would push count to 10 and permanently block.
            // Pattern: 5 wrong passwords → 30 min lock → after unlock, 6th–9th wrong → 10th → 1 day + deactivate.
            boolean shouldIncrementCounter = user != null && "Invalid password".equals(failureReason);
            
            if (user != null) {
                if (shouldIncrementCounter) {
                    user.recordFailedLogin();
                    user = userRepository.saveAndFlush(user);
                }
                
                log.info("Failed login attempt recorded for user: {} (attempts: {}) - Reason: {}",
                        user.getUsername(), user.getFailedLoginAttempts(), failureReason);

                if (shouldIncrementCounter) {
                    if (!user.isAccountNonLocked()) {
                        log.warn("User account locked: {} (attempts: {})", 
                                user.getUsername(), user.getFailedLoginAttempts());
                    }
                    if (!user.isActive()) {
                        log.warn("User account disabled: {} (attempts: {})", 
                                user.getUsername(), user.getFailedLoginAttempts());
                    }
                }
            } else {
                log.info("Failed login attempt recorded for unknown user - Reason: {}", failureReason);
            }
            
            return user;
        } catch (Exception ex) {
            log.error("Error recording failed login attempt for user: {}", userId, ex);
            return null;
        }
    }

    /**
     * Records a successful login attempt with full details (IP address, user agent)
     * Uses a separate transaction that won't rollback even if the main transaction fails
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccessfulLoginAttempt(UUID userId, String ipAddress, String userAgent) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            
            if (user != null) {
                // Record login attempt in database
                LoginAttempt loginAttempt = LoginAttempt.builder()
                        .user(user)
                        .attemptTimestamp(Instant.now())
                        .ipAddress(ipAddress)
                        .userAgent(userAgent)
                        .success(true)
                        .failureReason(null)
                        .build();
                
                loginAttemptRepository.saveAndFlush(loginAttempt);
                
                log.info("Successful login attempt recorded for user: {}", user.getUsername());
            } else {
                log.warn("Attempted to record successful login for non-existent user: {}", userId);
            }
        } catch (Exception ex) {
            log.error("Error recording successful login attempt for user: {}", userId, ex);
        }
    }
}

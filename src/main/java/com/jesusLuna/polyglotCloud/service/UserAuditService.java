package com.jesusLuna.polyglotCloud.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.jesusLuna.polyglotCloud.models.User;
import com.jesusLuna.polyglotCloud.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAuditService {

    private final UserRepository userRepository;

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
}

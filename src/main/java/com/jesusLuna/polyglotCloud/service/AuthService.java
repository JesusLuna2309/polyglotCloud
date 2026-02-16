package com.jesusLuna.polyglotCloud.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jesusLuna.polyglotCloud.DTO.UserDTO;
import com.jesusLuna.polyglotCloud.DTO.UserDTO.AuthResponseWithCookies;
import com.jesusLuna.polyglotCloud.Exception.BusinessRuleException;
import com.jesusLuna.polyglotCloud.Exception.ResourceNotFoundException;
import com.jesusLuna.polyglotCloud.models.User;
import com.jesusLuna.polyglotCloud.repository.UserRepository;
import com.jesusLuna.polyglotCloud.Security.JwtTokenProvider;
import com.jesusLuna.polyglotCloud.Security.PostQuantumPasswordEncoder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PostQuantumPasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailService emailService;
    private final UserAuditService userAuditService;

    //TODO: Agregar readonly en @Transactional(readOnly = true) a los métodos que no modifiquen datos (en el service)


    @Transactional
    public UserDTO.UserResponse register(UserDTO.UserRegistrationRequest request) {
        log.info("Registering new user: {}", request.username());
        
        // Validate uniqueness
        if (userRepository.existsByUsernameAndDeletedAtIsNull(request.username())) {
            throw new BusinessRuleException("Username already exists: " + request.username());
        }
        
        if (userRepository.existsByEmailAndDeletedAtIsNull(request.email())) {
            throw new BusinessRuleException("Email already exists: " + request.email());
        }
        
        // Create user
        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .active(true)
                .emailVerified(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        
        // Hash password con post-quantum security
        user.changePassword(passwordEncoder.encode(request.password()));
        
        // Generate post-quantum email verification token
        String verificationToken = user.generateEmailVerificationToken(passwordEncoder);
        
        // Save user
        user = userRepository.save(user);
        
        // 🎉 ENVIAR EMAIL DE VERIFICACIÓN
        try {
            emailService.sendEmailVerification(user.getEmail(), user.getUsername(), verificationToken);
            log.info("User registered and verification email sent: {}", user.getUsername());
        } catch (Exception e) {
            log.error("User registered but failed to send verification email: {}", user.getUsername(), e);
            // No fallar el registro por un email - el usuario puede reenviar
        }
        
        return toUserResponse(user);
    }

    @Transactional
        public UserDTO.AuthResponseWithCookies login(UserDTO.UserLoginRequest request, String ipAddress, String userAgent) {
        String login = request.login();
        String password = request.password();
        
        log.info("Login attempt for: {}", login);
        
        // Find user by username or email
        User user = userRepository.findByUsernameOrEmailAndDeletedAtIsNull(request.login(), request.login())
                .orElseThrow(() -> new BusinessRuleException("Invalid credentials"));
        
        // Verificar si la cuenta está activa
        if (!user.isActive()) {
            throw new BusinessRuleException("Account is disabled");
        }
        
        // Verificar si la cuenta está bloqueada
        if (!user.isAccountNonLocked()) {
            throw new BusinessRuleException("Account is locked. Please try again later.");
        }
        
        try {
            // Verificar contraseña manualmente (más control)
            if (!passwordEncoder.matches(password, user.getPasswordHash())) {
                throw new BadCredentialsException("Invalid credentials");
            }
            
            log.debug("Authentication successful for: {}", login);
            
            // Check if email is verified
            if (!user.isEmailVerified()) {
                throw new BusinessRuleException("Email not verified. Please verify your email before logging in.");
            }
            
            // Record successful login
            user.recordSuccessfulLogin(ipAddress);
            userRepository.save(user);
            
            // Generate JWT token
            String token = jwtTokenProvider.generateToken(
                    user.getId(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getRole()
            );
            
            Instant expiresAt = jwtTokenProvider.getExpirationFromToken(token);
            
            log.info("Login successful for user: {}", user.getUsername());
            
           // 1. Crear el refresh token
            var refreshTokenEntity = refreshTokenService.createRefreshToken(user.getId(), ipAddress, userAgent);
            
            // 2. Crear respuesta JSON (Solo Access Token y User)
            var responseBody = new UserDTO.UserLoginResponse(
                    token,
                    toUserResponse(user),
                    expiresAt
            );

            // 3. Devolver todo empaquetado
            return new AuthResponseWithCookies(
                    responseBody,
                    refreshTokenEntity.getToken(),
                    refreshTokenEntity.getExpiresAt()
            );
            
        } catch (Exception ex) {
            log.warn("Login failed for: {} - {}", login, ex.getMessage());
            
            // ✅ USAR EL SERVICIO SEPARADO - TRANSACCIÓN INDEPENDIENTE
            userAuditService.recordFailedLoginAttempt(user.getId());
            
            throw ex; // Re-throw para que el controlador maneje la respuesta
        }
    }

    @Transactional
    public void logout(String refreshTokenString) {
        log.info("Logging out user");
        
        try {
            refreshTokenService.revokeRefreshToken(refreshTokenString);
            log.info("User logged out successfully");
        } catch (ResourceNotFoundException ex) {
            log.warn("Refresh token not found during logout");
            // Don't throw exception, logout should be idempotent
        }
    }

    @Transactional
    public void logoutAll(UUID userId) {
        log.info("Logging out user from all devices: {}", userId);
        
        refreshTokenService.revokeAllUserTokens(userId);
        
        log.info("User logged out from all devices successfully");
    }

    @Transactional
    public UserDTO.UserResponse verifyEmail(String token) {
        log.info("Verifying email with token: {}", token);
        
        User user = userRepository.findByEmailVerificationToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid verification token"));
        
        boolean verified = user.verifyEmailToken(token);
        
        if (!verified) {
            throw new BusinessRuleException("Verification token has expired");
        }
        
        userRepository.save(user);
        
        log.info("Email verified successfully for user: {}", user.getUsername());
        
        return toUserResponse(user);
    }

    @Transactional
    public UserDTO.AuthResponseWithCookies refreshTokens(String refreshTokenString, String ipAddress, String userAgent) {
        log.info("Refreshing tokens using refresh token");
        
        // Validate and rotate refresh token
        var newRefreshToken = refreshTokenService.rotateRefreshToken(refreshTokenString, ipAddress, userAgent);
        
        // Get user
        User user = userRepository.findById(newRefreshToken.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        if (!user.isActive() || !user.isEmailVerified()) {
            throw new BusinessRuleException("User account is not active or email not verified");
        }
        
        // Generate new access token
        String accessToken = jwtTokenProvider.generateToken(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole()
        );
        
        Instant expiresAt = jwtTokenProvider.getExpirationFromToken(accessToken);
        
        log.info("Tokens refreshed successfully for user: {}", user.getUsername());
        
        var responseBody = new UserDTO.UserLoginResponse(
                accessToken,
                toUserResponse(user),
                expiresAt
        );
        
        return new AuthResponseWithCookies(
                responseBody,
                newRefreshToken.getToken(),
                newRefreshToken.getExpiresAt()
        );
    }




    private UserDTO.UserResponse toUserResponse(User user) {
        return new UserDTO.UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.isActive(),
                user.isEmailVerified(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
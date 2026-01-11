package com.jesusLuna.polyglotCloud.service;

import java.time.Instant;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jesusLuna.polyglotCloud.DTO.UserDTO;
import com.jesusLuna.polyglotCloud.Exception.BusinessRuleException;
import com.jesusLuna.polyglotCloud.Exception.ResourceNotFoundException;
import com.jesusLuna.polyglotCloud.Security.JwtTokenProvider;
import com.jesusLuna.polyglotCloud.Security.PostQuantumPasswordEncoder;
import com.jesusLuna.polyglotCloud.models.User;
import com.jesusLuna.polyglotCloud.repository.UserRespository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRespository userRepository;
    private final PostQuantumPasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final JwtTokenProvider jwtTokenProvider;


    @Transactional
    public UserDTO.UserResponse register(UserDTO.UserRegistrationRequest request) {
        log.info("Registering new user: {}", request.username());
        
        // Validate uniqueness
        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessRuleException("Username already exists: " + request.username());
        }
        
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessRuleException("Email already exists: " + request.email());
        }
        
        // Create user
        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .active(true)
                .emailVerified(false)
                .build();
        
        // Hash password
        user.changePassword(passwordEncoder.encode(request.password()));
        
        // TODO: Generate email verification token
        /*
        String verificationToken = user.generateEmailVerificationToken();
        log.debug("Generated email verification token for user: {}", request.username());
        */
        
        // TODO: Send verification email (not implemented in MVP)
        log.warn("Email verification not implemented - please check database for token");
        
        // Save user
        user = userRepository.save(user);
        
        log.info("User registered successfully: {}", user.getUsername());
        
        return toUserResponse(user);
    }

    @Transactional
    public UserDTO.UserLoginResponse login(UserDTO.UserLoginRequest request, String ipAddress, String userAgent) {
        String login = request.login();
        String password = request.password();
        
        log.info("Login attempt for: {}", login);
        
        // Find user by username or email
        User user = userRepository.findByUsernameOrEmail(login, login)
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
            
            // Generate refresh token
            var refreshToken = refreshTokenService.createRefreshToken(user.getId(), ipAddress, userAgent);
            
            log.info("Login successful for user: {}", user.getUsername());
            
            return new UserDTO.UserLoginResponse( // ← Cambiado de UserDto a UserDTO
                    token,
                    refreshToken.getToken(),
                    toUserResponse(user),
                    expiresAt,
                    refreshToken.getExpiresAt()
            );
            
        } catch (BadCredentialsException ex) {
            log.warn("Invalid credentials for: {}", login);
            
            // Record failed login
            user.recordFailedLogin();
            userRepository.save(user);
            
            throw new BusinessRuleException("Invalid credentials");
            
        } catch (Exception ex) {
            log.error("Login error for user: {}", login, ex);
            throw new BusinessRuleException("Login failed");
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
    public void logoutAll(java.util.UUID userId) {
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
    public UserDTO.UserLoginResponse refreshTokens(String refreshTokenString, String ipAddress, String userAgent) {
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
        
        return new UserDTO.UserLoginResponse(
                accessToken,
                newRefreshToken.getToken(),
                toUserResponse(user),
                expiresAt,
                newRefreshToken.getExpiresAt()
        );
    }

    @Transactional(readOnly = true)
    public UserDTO.UserLoginResponse refreshToken(java.util.UUID userId) {
        log.info("Refreshing token for user: {}", userId);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        if (!user.isActive() || !user.isEmailVerified()) {
            throw new BusinessRuleException("User account is not active or email not verified");
        }
        
        // Generate new JWT token
        String token = jwtTokenProvider.generateToken(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole()
        );
        
        Instant expiresAt = jwtTokenProvider.getExpirationFromToken(token);
        
        log.info("Token refreshed successfully for user: {}", user.getUsername());
        
        return new UserDTO.UserLoginResponse(
                token,
                null,  // No new refresh token in old endpoint
                toUserResponse(user),
                expiresAt,
                null
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

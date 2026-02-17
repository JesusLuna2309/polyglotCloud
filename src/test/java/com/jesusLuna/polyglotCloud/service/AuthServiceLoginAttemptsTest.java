package com.jesusLuna.polyglotCloud.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jesusLuna.polyglotCloud.DTO.UserDTO;
import com.jesusLuna.polyglotCloud.Exception.LoginFailedException;
import com.jesusLuna.polyglotCloud.models.User;
import com.jesusLuna.polyglotCloud.models.enums.Role;
import com.jesusLuna.polyglotCloud.repository.UserRepository;
import com.jesusLuna.polyglotCloud.Security.JwtTokenProvider;
import com.jesusLuna.polyglotCloud.Security.PostQuantumPasswordEncoder;

/**
 * Test suite for AuthService login attempts and blocking logic
 * 
 * Test Cases Covered:
 * 1. Successful login resets counter and clears lock
 * 2. Invalid password increments counter and shows remaining attempts
 * 3. Account locked shows "Account temporarily blocked" message with minutes
 * 4. Account disabled throws exception without incrementing counter
 * 5. Email not verified throws exception without incrementing counter
 * 6. User not found throws exception without incrementing counter
 * 7. Remaining attempts calculation is correct (5 - current attempts)
 * 8. After 5 attempts, account is temporarily locked (30 min)
 * 9. After 10 attempts, account is permanently locked (1 day) and deactivated
 * 10. Login attempts are recorded in LoginAttempt table
 * 11. Lock expiration allows login attempts to continue
 * 12. Counter continues from previous value after lock expires
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Login Attempts Tests")
class AuthServiceLoginAttemptsTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PostQuantumPasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private EmailService emailService;

    @Mock
    private UserAuditService userAuditService;

    @InjectMocks
    private AuthService authService;

    private User testUser;
    private UUID userId;
    private String username = "testuser";
    private String email = "test@example.com";
    private String correctPassword = "correctPassword123";
    private String wrongPassword = "wrongPassword";
    private String ipAddress = "192.168.1.1";
    private String userAgent = "Mozilla/5.0";

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        testUser = User.builder()
                .id(userId)
                .username(username)
                .email(email)
                .passwordHash("$argon2id$v=19$m=65536,t=3,p=4$hashed")
                .role(Role.USER)
                .active(true)
                .emailVerified(true)
                .failedLoginAttempts(0)
                .lockedUntil(null)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("TC-1: Successful login resets counter and clears lock")
    void testSuccessfulLoginResetsCounter() {
        // Given - User has failed attempts but lock has expired (so they can login)
        testUser.setFailedLoginAttempts(3);
        testUser.setLockedUntil(Instant.now().minusSeconds(60)); // Lock expired, user can login
        UserDTO.UserLoginRequest request = new UserDTO.UserLoginRequest(username, correctPassword);
        
        when(userRepository.findByUsernameOrEmailAndDeletedAtIsNull(username, username))
                .thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(correctPassword, testUser.getPasswordHash())).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            // Simulate recordSuccessfulLogin being called
            user.recordSuccessfulLogin(ipAddress);
            return user;
        });
        when(jwtTokenProvider.generateToken(any(), any(), any(), any())).thenReturn("token");
        when(jwtTokenProvider.getExpirationFromToken(anyString())).thenReturn(Instant.now().plusSeconds(3600));
        when(refreshTokenService.createRefreshToken(any(), any(), any())).thenReturn(
                com.jesusLuna.polyglotCloud.models.RefreshToken.builder()
                        .id(UUID.randomUUID())
                        .token("refresh-token")
                        .userId(userId)
                        .expiresAt(Instant.now().plusSeconds(604800))
                        .build());

        // When
        UserDTO.AuthResponseWithCookies response = authService.login(request, ipAddress, userAgent);

        // Then
        assertNotNull(response);
        assertEquals(0, testUser.getFailedLoginAttempts(), "Counter should be reset");
        assertNull(testUser.getLockedUntil(), "Lock should be cleared");
        verify(userAuditService).recordSuccessfulLoginAttempt(eq(userId), eq(ipAddress), eq(userAgent));
    }

    @Test
    @DisplayName("TC-2: Invalid password increments counter and shows remaining attempts")
    void testInvalidPasswordShowsRemainingAttempts() {
        // Given
        testUser.setFailedLoginAttempts(2); // 2 failed attempts
        User updatedUser = User.builder()
                .id(userId)
                .username(username)
                .email(email)
                .passwordHash(testUser.getPasswordHash())
                .role(Role.USER)
                .active(true)
                .emailVerified(true)
                .failedLoginAttempts(3) // After increment
                .lockedUntil(null)
                .build();
        
        UserDTO.UserLoginRequest request = new UserDTO.UserLoginRequest(username, wrongPassword);
        
        when(userRepository.findByUsernameOrEmailAndDeletedAtIsNull(username, username))
                .thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(wrongPassword, testUser.getPasswordHash())).thenReturn(false);
        when(userAuditService.recordFailedLoginAttempt(eq(userId), eq(ipAddress), eq(userAgent), eq("Invalid password")))
                .thenReturn(updatedUser);

        // When & Then
        LoginFailedException exception = assertThrows(LoginFailedException.class, () -> {
            authService.login(request, ipAddress, userAgent);
        });

        assertEquals(2, exception.getRemainingAttempts(), "Should show 2 remaining attempts (5 - 3)");
        assertEquals("Invalid credentials", exception.getMessage());
        verify(userAuditService).recordFailedLoginAttempt(eq(userId), eq(ipAddress), eq(userAgent), eq("Invalid password"));
    }

    @Test
    @DisplayName("TC-3: Account locked shows 'Account temporarily blocked' message with minutes")
    void testAccountLockedShowsTemporaryBlockMessage() {
        // Given
        testUser.setFailedLoginAttempts(5);
        testUser.setLockedUntil(Instant.now().plusSeconds(1500)); // 25 minutes remaining
        UserDTO.UserLoginRequest request = new UserDTO.UserLoginRequest(username, correctPassword);
        
        when(userRepository.findByUsernameOrEmailAndDeletedAtIsNull(username, username))
                .thenReturn(Optional.of(testUser));
        when(userAuditService.recordFailedLoginAttempt(eq(userId), eq(ipAddress), eq(userAgent), eq("Account is locked")))
                .thenReturn(testUser);

        // When & Then
        LoginFailedException exception = assertThrows(LoginFailedException.class, () -> {
            authService.login(request, ipAddress, userAgent);
        });

        assertTrue(exception.getMessage().contains("Account temporarily blocked"));
        assertTrue(exception.getMessage().contains("minute"));
        assertTrue(exception.isAccountLocked());
        assertNotNull(exception.getLockedUntil());
        assertNull(exception.getRemainingAttempts(), "No remaining attempts when already locked");
    }

    @Test
    @DisplayName("TC-4: Account disabled throws exception without incrementing counter")
    void testAccountDisabledDoesNotIncrementCounter() {
        // Given
        testUser.setActive(false);
        testUser.setFailedLoginAttempts(2);
        UserDTO.UserLoginRequest request = new UserDTO.UserLoginRequest(username, correctPassword);
        
        when(userRepository.findByUsernameOrEmailAndDeletedAtIsNull(username, username))
                .thenReturn(Optional.of(testUser));
        when(userAuditService.recordFailedLoginAttempt(eq(userId), eq(ipAddress), eq(userAgent), eq("Account is disabled")))
                .thenReturn(testUser);

        // When & Then
        com.jesusLuna.polyglotCloud.Exception.BusinessRuleException exception = 
                assertThrows(com.jesusLuna.polyglotCloud.Exception.BusinessRuleException.class, () -> {
            authService.login(request, ipAddress, userAgent);
        });

        assertEquals("Account is disabled", exception.getMessage());
        assertEquals(2, testUser.getFailedLoginAttempts(), "Counter should remain at 2");
        verify(userAuditService).recordFailedLoginAttempt(eq(userId), eq(ipAddress), eq(userAgent), eq("Account is disabled"));
    }

    @Test
    @DisplayName("TC-5: Email not verified throws exception without incrementing counter")
    void testEmailNotVerifiedDoesNotIncrementCounter() {
        // Given
        testUser.setEmailVerified(false);
        testUser.setFailedLoginAttempts(1);
        UserDTO.UserLoginRequest request = new UserDTO.UserLoginRequest(username, correctPassword);
        
        when(userRepository.findByUsernameOrEmailAndDeletedAtIsNull(username, username))
                .thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(correctPassword, testUser.getPasswordHash())).thenReturn(true);
        when(userAuditService.recordFailedLoginAttempt(eq(userId), eq(ipAddress), eq(userAgent), eq("Email not verified")))
                .thenReturn(testUser);

        // When & Then
        LoginFailedException exception = assertThrows(LoginFailedException.class, () -> {
            authService.login(request, ipAddress, userAgent);
        });

        assertTrue(exception.getMessage().contains("Email not verified"));
        assertEquals(1, testUser.getFailedLoginAttempts(), "Counter should remain at 1");
        verify(userAuditService).recordFailedLoginAttempt(eq(userId), eq(ipAddress), eq(userAgent), eq("Email not verified"));
    }

    @Test
    @DisplayName("TC-6: User not found throws exception without incrementing counter")
    void testUserNotFoundDoesNotIncrementCounter() {
        // Given
        UserDTO.UserLoginRequest request = new UserDTO.UserLoginRequest("nonexistent", wrongPassword);
        
        when(userRepository.findByUsernameOrEmailAndDeletedAtIsNull("nonexistent", "nonexistent"))
                .thenReturn(Optional.empty());
        when(userAuditService.recordFailedLoginAttempt(isNull(), eq(ipAddress), eq(userAgent), eq("User not found")))
                .thenReturn(null);

        // When & Then
        com.jesusLuna.polyglotCloud.Exception.BusinessRuleException exception = 
                assertThrows(com.jesusLuna.polyglotCloud.Exception.BusinessRuleException.class, () -> {
            authService.login(request, ipAddress, userAgent);
        });

        assertEquals("Invalid credentials", exception.getMessage());
        verify(userAuditService).recordFailedLoginAttempt(isNull(), eq(ipAddress), eq(userAgent), eq("User not found"));
    }

    @Test
    @DisplayName("TC-7: Remaining attempts calculation is correct")
    void testRemainingAttemptsCalculation() {
        // Given - User has 2 failed attempts
        testUser.setFailedLoginAttempts(2);
        User updatedUser = User.builder()
                .id(userId)
                .username(username)
                .email(email)
                .passwordHash(testUser.getPasswordHash())
                .role(Role.USER)
                .active(true)
                .emailVerified(true)
                .failedLoginAttempts(3) // After increment
                .lockedUntil(null)
                .build();
        
        UserDTO.UserLoginRequest request = new UserDTO.UserLoginRequest(username, wrongPassword);
        
        when(userRepository.findByUsernameOrEmailAndDeletedAtIsNull(username, username))
                .thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(wrongPassword, testUser.getPasswordHash())).thenReturn(false);
        when(userAuditService.recordFailedLoginAttempt(eq(userId), eq(ipAddress), eq(userAgent), eq("Invalid password")))
                .thenReturn(updatedUser);

        // When
        LoginFailedException exception = assertThrows(LoginFailedException.class, () -> {
            authService.login(request, ipAddress, userAgent);
        });

        // Then - Should show 2 remaining (5 - 3 = 2)
        assertEquals(2, exception.getRemainingAttempts());
    }

    @Test
    @DisplayName("TC-8: After 5 attempts, account is temporarily locked (30 min)")
    void testTemporaryLockAfter5Attempts() {
        // Given - User has 4 failed attempts
        testUser.setFailedLoginAttempts(4);
        User updatedUser = User.builder()
                .id(userId)
                .username(username)
                .email(email)
                .passwordHash(testUser.getPasswordHash())
                .role(Role.USER)
                .active(true)
                .emailVerified(true)
                .failedLoginAttempts(5) // After increment
                .lockedUntil(Instant.now().plusSeconds(1800)) // 30 min lock
                .build();
        
        UserDTO.UserLoginRequest request = new UserDTO.UserLoginRequest(username, wrongPassword);
        
        when(userRepository.findByUsernameOrEmailAndDeletedAtIsNull(username, username))
                .thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(wrongPassword, testUser.getPasswordHash())).thenReturn(false);
        when(userAuditService.recordFailedLoginAttempt(eq(userId), eq(ipAddress), eq(userAgent), eq("Invalid password")))
                .thenReturn(updatedUser);

        // When
        LoginFailedException exception = assertThrows(LoginFailedException.class, () -> {
            authService.login(request, ipAddress, userAgent);
        });

        // Then
        assertEquals(0, exception.getRemainingAttempts(), "No remaining attempts after 5");
        assertNotNull(exception.getLockedUntil(), "Should be locked");
        assertTrue(exception.isAccountLocked());
    }

    @Test
    @DisplayName("TC-9: After 10 attempts, account is permanently locked (1 day) and deactivated")
    void testPermanentLockAfter10Attempts() {
        // Given - User has 9 failed attempts, lock expired
        testUser.setFailedLoginAttempts(9);
        testUser.setLockedUntil(Instant.now().minusSeconds(60)); // Lock expired
        User updatedUser = User.builder()
                .id(userId)
                .username(username)
                .email(email)
                .passwordHash(testUser.getPasswordHash())
                .role(Role.USER)
                .active(false) // Deactivated
                .emailVerified(true)
                .failedLoginAttempts(10) // After increment
                .lockedUntil(Instant.now().plusSeconds(86400)) // 1 day lock
                .build();
        
        UserDTO.UserLoginRequest request = new UserDTO.UserLoginRequest(username, wrongPassword);
        
        when(userRepository.findByUsernameOrEmailAndDeletedAtIsNull(username, username))
                .thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(wrongPassword, testUser.getPasswordHash())).thenReturn(false);
        when(userAuditService.recordFailedLoginAttempt(eq(userId), eq(ipAddress), eq(userAgent), eq("Invalid password")))
                .thenReturn(updatedUser);

        // When
        LoginFailedException exception = assertThrows(LoginFailedException.class, () -> {
            authService.login(request, ipAddress, userAgent);
        });

        // Then
        assertTrue(exception.isAccountLocked());
        assertTrue(exception.isAccountDisabled(), "Account should be deactivated");
        assertNotNull(exception.getLockedUntil());
    }

    @Test
    @DisplayName("TC-10: Login attempts are recorded in LoginAttempt table")
    void testLoginAttemptsAreRecorded() {
        // Given
        testUser.setFailedLoginAttempts(1);
        User updatedUser = User.builder()
                .id(userId)
                .username(username)
                .email(email)
                .passwordHash(testUser.getPasswordHash())
                .role(Role.USER)
                .active(true)
                .emailVerified(true)
                .failedLoginAttempts(2)
                .lockedUntil(null)
                .build();
        
        UserDTO.UserLoginRequest request = new UserDTO.UserLoginRequest(username, wrongPassword);
        
        when(userRepository.findByUsernameOrEmailAndDeletedAtIsNull(username, username))
                .thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(wrongPassword, testUser.getPasswordHash())).thenReturn(false);
        when(userAuditService.recordFailedLoginAttempt(eq(userId), eq(ipAddress), eq(userAgent), eq("Invalid password")))
                .thenReturn(updatedUser);

        // When
        assertThrows(LoginFailedException.class, () -> {
            authService.login(request, ipAddress, userAgent);
        });

        // Then
        verify(userAuditService).recordFailedLoginAttempt(eq(userId), eq(ipAddress), eq(userAgent), eq("Invalid password"));
    }

    @Test
    @DisplayName("TC-11: Lock expiration allows login attempts to continue")
    void testLockExpirationAllowsLoginAttempts() {
        // Given - User had 5 attempts, lock expired
        testUser.setFailedLoginAttempts(5);
        testUser.setLockedUntil(Instant.now().minusSeconds(60)); // Lock expired
        User updatedUser = User.builder()
                .id(userId)
                .username(username)
                .email(email)
                .passwordHash(testUser.getPasswordHash())
                .role(Role.USER)
                .active(true)
                .emailVerified(true)
                .failedLoginAttempts(6) // Counter continues
                .lockedUntil(Instant.now().plusSeconds(1800)) // New lock
                .build();
        
        UserDTO.UserLoginRequest request = new UserDTO.UserLoginRequest(username, wrongPassword);
        
        when(userRepository.findByUsernameOrEmailAndDeletedAtIsNull(username, username))
                .thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(wrongPassword, testUser.getPasswordHash())).thenReturn(false);
        when(userAuditService.recordFailedLoginAttempt(eq(userId), eq(ipAddress), eq(userAgent), eq("Invalid password")))
                .thenReturn(updatedUser);

        // When
        LoginFailedException exception = assertThrows(LoginFailedException.class, () -> {
            authService.login(request, ipAddress, userAgent);
        });

        // Then
        assertEquals(6, updatedUser.getFailedLoginAttempts(), "Counter should continue from 5 to 6");
        assertNotNull(exception.getLockedUntil(), "Should be locked again");
    }

    @Test
    @DisplayName("TC-12: Counter continues from previous value after lock expires")
    void testCounterContinuesAfterLockExpires() {
        // Given - User had 5 attempts, lock expired
        testUser.setFailedLoginAttempts(5);
        testUser.setLockedUntil(Instant.now().minusSeconds(60)); // Lock expired
        User updatedUser = User.builder()
                .id(userId)
                .username(username)
                .email(email)
                .passwordHash(testUser.getPasswordHash())
                .role(Role.USER)
                .active(true)
                .emailVerified(true)
                .failedLoginAttempts(6) // Continues from 5
                .lockedUntil(Instant.now().plusSeconds(1800))
                .build();
        
        UserDTO.UserLoginRequest request = new UserDTO.UserLoginRequest(username, wrongPassword);
        
        when(userRepository.findByUsernameOrEmailAndDeletedAtIsNull(username, username))
                .thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(wrongPassword, testUser.getPasswordHash())).thenReturn(false);
        when(userAuditService.recordFailedLoginAttempt(eq(userId), eq(ipAddress), eq(userAgent), eq("Invalid password")))
                .thenReturn(updatedUser);

        // When
        LoginFailedException exception = assertThrows(LoginFailedException.class, () -> {
            authService.login(request, ipAddress, userAgent);
        });

        // Then
        assertEquals(6, updatedUser.getFailedLoginAttempts(), "Counter should continue from 5 to 6");
        assertNotNull(exception.getLockedUntil(), "Should be locked again");
    }
}

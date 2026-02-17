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

import com.jesusLuna.polyglotCloud.config.SecurityProperties;
import com.jesusLuna.polyglotCloud.models.LoginAttempt;
import com.jesusLuna.polyglotCloud.models.User;
import com.jesusLuna.polyglotCloud.models.enums.Role;
import com.jesusLuna.polyglotCloud.repository.LoginAttemptRepository;
import com.jesusLuna.polyglotCloud.repository.UserRepository;

/**
 * Test suite for UserAuditService
 * 
 * Test Cases Covered:
 * 1. Invalid password attempts increment counter
 * 2. Account locked attempts do NOT increment counter
 * 3. Account disabled attempts do NOT increment counter
 * 4. Email not verified attempts do NOT increment counter
 * 5. User not found attempts are recorded but no counter increment
 * 6. Successful login attempts are recorded
 * 7. Counter increments correctly: 1→2→3→4→5 (temp lock) →6→7→8→9→10 (perm lock)
 * 8. Temporary lock (30 min) after 5 attempts
 * 9. Permanent lock (1 day) + deactivate after 10 attempts
 * 10. Counter resets on successful login
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserAuditService Tests")
class UserAuditServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private LoginAttemptRepository loginAttemptRepository;

    @Mock
    private SecurityProperties securityProperties;

    @InjectMocks
    private UserAuditService userAuditService;

    private User testUser;
    private UUID userId;
    private String ipAddress = "192.168.1.1";
    private String userAgent = "Mozilla/5.0";

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        testUser = User.builder()
                .id(userId)
                .username("testuser")
                .email("test@example.com")
                .passwordHash("$argon2id$v=19$m=65536,t=3,p=4$hashed")
                .role(Role.USER)
                .active(true)
                .emailVerified(true)
                .failedLoginAttempts(0)
                .lockedUntil(null)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        
        // Setup default security properties for tests (lenient since not all tests use them)
        lenient().when(securityProperties.getMaxFailedAttemptsTemp()).thenReturn(5);
        lenient().when(securityProperties.getMaxFailedAttemptsPerm()).thenReturn(10);
        lenient().when(securityProperties.getLockoutDurationMinutes()).thenReturn(30);
        lenient().when(securityProperties.getLockoutDurationDays()).thenReturn(1);
    }

    @Test
    @DisplayName("TC-1: Invalid password attempt increments counter")
    void testInvalidPasswordIncrementsCounter() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(loginAttemptRepository.saveAndFlush(any(LoginAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        // Service calls recordFailedLogin() internally, so mock should just return the user as-is
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        User result = userAuditService.recordFailedLoginAttempt(userId, ipAddress, userAgent, "Invalid password");

        // Then
        assertNotNull(result);
        assertEquals(1, result.getFailedLoginAttempts(), "Counter should increment to 1");
        verify(userRepository).saveAndFlush(any(User.class));
        verify(loginAttemptRepository).saveAndFlush(any(LoginAttempt.class));
    }

    @Test
    @DisplayName("TC-2: Account locked attempt does NOT increment counter")
    void testAccountLockedDoesNotIncrementCounter() {
        // Given
        testUser.setFailedLoginAttempts(5);
        testUser.setLockedUntil(Instant.now().plusSeconds(1800)); // 30 min lock
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(loginAttemptRepository.saveAndFlush(any(LoginAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        User result = userAuditService.recordFailedLoginAttempt(userId, ipAddress, userAgent, "Account is locked");

        // Then
        assertNotNull(result);
        assertEquals(5, result.getFailedLoginAttempts(), "Counter should remain at 5");
        verify(userRepository, never()).saveAndFlush(any(User.class));
        verify(loginAttemptRepository).saveAndFlush(any(LoginAttempt.class));
    }

    @Test
    @DisplayName("TC-3: Account disabled attempt does NOT increment counter")
    void testAccountDisabledDoesNotIncrementCounter() {
        // Given
        testUser.setActive(false);
        testUser.setFailedLoginAttempts(3);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(loginAttemptRepository.saveAndFlush(any(LoginAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        User result = userAuditService.recordFailedLoginAttempt(userId, ipAddress, userAgent, "Account is disabled");

        // Then
        assertNotNull(result);
        assertEquals(3, result.getFailedLoginAttempts(), "Counter should remain at 3");
        verify(userRepository, never()).saveAndFlush(any(User.class));
        verify(loginAttemptRepository).saveAndFlush(any(LoginAttempt.class));
    }

    @Test
    @DisplayName("TC-4: Email not verified attempt does NOT increment counter")
    void testEmailNotVerifiedDoesNotIncrementCounter() {
        // Given
        testUser.setEmailVerified(false);
        testUser.setFailedLoginAttempts(2);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(loginAttemptRepository.saveAndFlush(any(LoginAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        User result = userAuditService.recordFailedLoginAttempt(userId, ipAddress, userAgent, "Email not verified");

        // Then
        assertNotNull(result);
        assertEquals(2, result.getFailedLoginAttempts(), "Counter should remain at 2");
        verify(userRepository, never()).saveAndFlush(any(User.class));
        verify(loginAttemptRepository).saveAndFlush(any(LoginAttempt.class));
    }

    @Test
    @DisplayName("TC-5: User not found attempt is recorded but no counter increment")
    void testUserNotFoundIsRecorded() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        when(loginAttemptRepository.saveAndFlush(any(LoginAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        User result = userAuditService.recordFailedLoginAttempt(userId, ipAddress, userAgent, "User not found");

        // Then
        assertNull(result);
        verify(userRepository, never()).saveAndFlush(any(User.class));
        verify(loginAttemptRepository).saveAndFlush(any(LoginAttempt.class));
    }

    @Test
    @DisplayName("TC-6: Successful login attempt is recorded")
    void testSuccessfulLoginIsRecorded() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(loginAttemptRepository.saveAndFlush(any(LoginAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        userAuditService.recordSuccessfulLoginAttempt(userId, ipAddress, userAgent);

        // Then
        verify(loginAttemptRepository).saveAndFlush(argThat(attempt -> 
            Boolean.TRUE.equals(attempt.getSuccess()) && 
            attempt.getUser() != null &&
            attempt.getUser().getId().equals(userId) &&
            ipAddress.equals(attempt.getIpAddress())
        ));
    }

    @Test
    @DisplayName("TC-7: Counter progression 1→2→3→4→5 triggers temporary lock")
    void testCounterProgressionToTemporaryLock() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(loginAttemptRepository.saveAndFlush(any(LoginAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        // Service calls recordFailedLogin() internally, so mock should just return the user as-is
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When - Attempts 1-5
        for (int i = 1; i <= 5; i++) {
            testUser.setFailedLoginAttempts(i - 1);
            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
            User result = userAuditService.recordFailedLoginAttempt(userId, ipAddress, userAgent, "Invalid password");
            
            // Then
            if (i == 5) {
                assertTrue(result.getFailedLoginAttempts() >= 5, "Should have 5+ attempts");
                assertNotNull(result.getLockedUntil(), "Should be locked after 5 attempts");
                assertTrue(result.getLockedUntil().isAfter(Instant.now()), "Lock should be in the future");
            }
        }
    }

    @Test
    @DisplayName("TC-8: Counter progression 6→7→8→9→10 triggers permanent lock")
    void testCounterProgressionToPermanentLock() {
        // Given
        testUser.setFailedLoginAttempts(5);
        testUser.setLockedUntil(Instant.now().minusSeconds(60)); // Lock expired
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(loginAttemptRepository.saveAndFlush(any(LoginAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        // Service calls recordFailedLogin() internally, so mock should just return the user as-is
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When - Attempts 6-10
        for (int i = 6; i <= 10; i++) {
            testUser.setFailedLoginAttempts(i - 1);
            testUser.setLockedUntil(i == 6 ? null : Instant.now().minusSeconds(60)); // Clear lock for 6th attempt
            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
            User result = userAuditService.recordFailedLoginAttempt(userId, ipAddress, userAgent, "Invalid password");
            
            // Then
            if (i == 10) {
                assertTrue(result.getFailedLoginAttempts() >= 10, "Should have 10+ attempts");
                assertNotNull(result.getLockedUntil(), "Should be locked after 10 attempts");
                assertFalse(result.isActive(), "Account should be deactivated after 10 attempts");
            }
        }
    }

    @Test
    @DisplayName("TC-9: Temporary lock (30 min) after 5 attempts")
    void testTemporaryLockAfter5Attempts() {
        // Given
        testUser.setFailedLoginAttempts(4);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(loginAttemptRepository.saveAndFlush(any(LoginAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        // Service calls recordFailedLogin() internally, so mock should just return the user as-is
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        User result = userAuditService.recordFailedLoginAttempt(userId, ipAddress, userAgent, "Invalid password");

        // Then
        assertEquals(5, result.getFailedLoginAttempts());
        assertNotNull(result.getLockedUntil(), "Should be locked");
        long diffMinutes = java.time.Duration.between(Instant.now(), result.getLockedUntil()).toMinutes();
        assertTrue(diffMinutes >= 29 && diffMinutes <= 30, "Lock should be approximately 30 minutes");
    }

    @Test
    @DisplayName("TC-10: Permanent lock (1 day) + deactivate after 10 attempts")
    void testPermanentLockAfter10Attempts() {
        // Given
        testUser.setFailedLoginAttempts(9);
        testUser.setLockedUntil(Instant.now().minusSeconds(60)); // Previous lock expired
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(loginAttemptRepository.saveAndFlush(any(LoginAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        // Service calls recordFailedLogin() internally, so mock should just return the user as-is
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        User result = userAuditService.recordFailedLoginAttempt(userId, ipAddress, userAgent, "Invalid password");

        // Then
        assertEquals(10, result.getFailedLoginAttempts());
        assertNotNull(result.getLockedUntil(), "Should be locked");
        assertFalse(result.isActive(), "Account should be deactivated");
        long diffHours = java.time.Duration.between(Instant.now(), result.getLockedUntil()).toHours();
        assertTrue(diffHours >= 23 && diffHours <= 24, "Lock should be approximately 1 day");
    }

    @Test
    @DisplayName("TC-11: Counter resets on successful login")
    void testCounterResetsOnSuccessfulLogin() {
        // Given
        testUser.setFailedLoginAttempts(7);
        testUser.setLockedUntil(Instant.now().plusSeconds(1800));
        testUser.recordSuccessfulLogin(ipAddress);
        
        // Then
        assertEquals(0, testUser.getFailedLoginAttempts(), "Counter should reset to 0");
        assertNull(testUser.getLockedUntil(), "Lock should be cleared");
        assertNotNull(testUser.getLastLoginAt(), "Last login should be set");
    }

    @Test
    @DisplayName("TC-12: Multiple invalid password attempts while locked do NOT increment counter")
    void testMultipleAttemptsWhileLockedDoNotIncrement() {
        // Given
        testUser.setFailedLoginAttempts(5);
        testUser.setLockedUntil(Instant.now().plusSeconds(1800)); // Locked for 30 min
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(loginAttemptRepository.saveAndFlush(any(LoginAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When - Try 5 times while locked
        for (int i = 0; i < 5; i++) {
            User result = userAuditService.recordFailedLoginAttempt(userId, ipAddress, userAgent, "Account is locked");
            assertEquals(5, result.getFailedLoginAttempts(), "Counter should remain at 5");
        }

        // Then
        verify(userRepository, never()).saveAndFlush(any(User.class));
        verify(loginAttemptRepository, times(5)).saveAndFlush(any(LoginAttempt.class));
    }

    @Test
    @DisplayName("TC-13: After lock expires, invalid password increments counter from previous value")
    void testAfterLockExpiresCounterContinues() {
        // Given - User had 5 attempts, lock expired
        testUser.setFailedLoginAttempts(5);
        testUser.setLockedUntil(Instant.now().minusSeconds(60)); // Lock expired
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(loginAttemptRepository.saveAndFlush(any(LoginAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        // Service calls recordFailedLogin() internally, so mock should just return the user as-is
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When - Invalid password after lock expired
        User result = userAuditService.recordFailedLoginAttempt(userId, ipAddress, userAgent, "Invalid password");

        // Then
        assertEquals(6, result.getFailedLoginAttempts(), "Counter should continue from 5 to 6");
        assertNotNull(result.getLockedUntil(), "Should be locked again (6 >= 5)");
    }
}

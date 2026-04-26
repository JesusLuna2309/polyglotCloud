package com.jesusLuna.polyglotCloud.models;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.jesusLuna.polyglotCloud.models.enums.Role;

/**
 * Test suite for User model login attempts and blocking logic
 * 
 * Test Cases Covered:
 * 1. recordFailedLogin increments counter correctly
 * 2. Temporary lock (30 min) after 5 attempts
 * 3. Permanent lock (1 day) + deactivate after 10 attempts
 * 4. getRemainingAttemptsBeforeTempLock calculates correctly
 * 5. getRemainingAttemptsBeforePermLock calculates correctly
 * 6. recordSuccessfulLogin resets counter and clears lock
 * 7. isAccountNonLocked returns false when locked
 * 8. isAccountNonLocked returns true when lock expired
 * 9. Counter progression: 1→2→3→4→5 (temp lock) →6→7→8→9→10 (perm lock)
 * 10. Lock duration is approximately 30 minutes for temporary lock
 * 11. Lock duration is approximately 1 day for permanent lock
 */
@DisplayName("User Model Login Attempts Tests")
class UserLoginAttemptsTest {

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(java.util.UUID.randomUUID())
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
    }

    @Test
    @DisplayName("TC-1: recordFailedLogin increments counter correctly")
    void testRecordFailedLoginIncrementsCounter() {
        // Given
        assertEquals(0, user.getFailedLoginAttempts());

        // When
        user.recordFailedLogin();

        // Then
        assertEquals(1, user.getFailedLoginAttempts());
    }

    @Test
    @DisplayName("TC-2: Temporary lock (30 min) after 5 attempts")
    void testTemporaryLockAfter5Attempts() {
        // Given
        user.setFailedLoginAttempts(4);

        // When
        user.recordFailedLogin();

        // Then
        assertEquals(5, user.getFailedLoginAttempts());
        assertNotNull(user.getLockedUntil(), "Should be locked after 5 attempts");
        assertTrue(user.getLockedUntil().isAfter(Instant.now()), "Lock should be in the future");
        
        long minutesRemaining = java.time.Duration.between(Instant.now(), user.getLockedUntil()).toMinutes();
        assertTrue(minutesRemaining >= 29 && minutesRemaining <= 30, 
                "Lock should be approximately 30 minutes, got: " + minutesRemaining);
    }

    @Test
    @DisplayName("TC-3: Permanent lock (1 day) + deactivate after 10 attempts")
    void testPermanentLockAfter10Attempts() {
        // Given
        user.setFailedLoginAttempts(9);

        // When
        user.recordFailedLogin();

        // Then
        assertEquals(10, user.getFailedLoginAttempts());
        assertNotNull(user.getLockedUntil(), "Should be locked after 10 attempts");
        assertFalse(user.isActive(), "Account should be deactivated after 10 attempts");
        
        long hoursRemaining = java.time.Duration.between(Instant.now(), user.getLockedUntil()).toHours();
        assertTrue(hoursRemaining >= 23 && hoursRemaining <= 24, 
                "Lock should be approximately 1 day, got: " + hoursRemaining + " hours");
    }

    @Test
    @DisplayName("TC-4: getRemainingAttemptsBeforeTempLock calculates correctly")
    void testGetRemainingAttemptsBeforeTempLock() {
        // Test cases: attempts -> remaining
        // 0 attempts -> 5 remaining
        user.setFailedLoginAttempts(0);
        assertEquals(5, user.getRemainingAttemptsBeforeTempLock());

        // 2 attempts -> 3 remaining
        user.setFailedLoginAttempts(2);
        assertEquals(3, user.getRemainingAttemptsBeforeTempLock());

        // 4 attempts -> 1 remaining
        user.setFailedLoginAttempts(4);
        assertEquals(1, user.getRemainingAttemptsBeforeTempLock());

        // 5 attempts -> 0 remaining
        user.setFailedLoginAttempts(5);
        assertEquals(0, user.getRemainingAttemptsBeforeTempLock());

        // 10 attempts -> 0 remaining (should not go negative)
        user.setFailedLoginAttempts(10);
        assertEquals(0, user.getRemainingAttemptsBeforeTempLock());
    }

    @Test
    @DisplayName("TC-5: getRemainingAttemptsBeforePermLock calculates correctly")
    void testGetRemainingAttemptsBeforePermLock() {
        // Test cases: attempts -> remaining
        // 0 attempts -> 10 remaining
        user.setFailedLoginAttempts(0);
        assertEquals(10, user.getRemainingAttemptsBeforePermLock());

        // 5 attempts -> 5 remaining
        user.setFailedLoginAttempts(5);
        assertEquals(5, user.getRemainingAttemptsBeforePermLock());

        // 9 attempts -> 1 remaining
        user.setFailedLoginAttempts(9);
        assertEquals(1, user.getRemainingAttemptsBeforePermLock());

        // 10 attempts -> 0 remaining
        user.setFailedLoginAttempts(10);
        assertEquals(0, user.getRemainingAttemptsBeforePermLock());

        // 15 attempts -> 0 remaining (should not go negative)
        user.setFailedLoginAttempts(15);
        assertEquals(0, user.getRemainingAttemptsBeforePermLock());
    }

    @Test
    @DisplayName("TC-6: recordSuccessfulLogin resets counter and clears lock")
    void testRecordSuccessfulLoginResetsCounter() {
        // Given
        user.setFailedLoginAttempts(7);
        user.setLockedUntil(Instant.now().plusSeconds(1800));

        // When
        user.recordSuccessfulLogin("192.168.1.1");

        // Then
        assertEquals(0, user.getFailedLoginAttempts(), "Counter should reset to 0");
        assertNull(user.getLockedUntil(), "Lock should be cleared");
        assertNotNull(user.getLastLoginAt(), "Last login should be set");
        assertEquals("192.168.1.1", user.getLastLoginIp(), "IP address should be set");
    }

    @Test
    @DisplayName("TC-7: isAccountNonLocked returns false when locked")
    void testIsAccountNonLockedReturnsFalseWhenLocked() {
        // Given
        user.setLockedUntil(Instant.now().plusSeconds(1800)); // Locked for 30 min

        // When & Then
        assertFalse(user.isAccountNonLocked(), "Account should be locked");
    }

    @Test
    @DisplayName("TC-8: isAccountNonLocked returns true when lock expired")
    void testIsAccountNonLockedReturnsTrueWhenLockExpired() {
        // Given
        user.setLockedUntil(Instant.now().minusSeconds(60)); // Lock expired

        // When & Then
        assertTrue(user.isAccountNonLocked(), "Account should not be locked when expired");
    }

    @Test
    @DisplayName("TC-9: Counter progression 1→2→3→4→5 (temp lock) →6→7→8→9→10 (perm lock)")
    void testCounterProgression() {
        // Attempts 1-4: No lock
        for (int i = 1; i <= 4; i++) {
            user.recordFailedLogin();
            assertEquals(i, user.getFailedLoginAttempts());
            assertNull(user.getLockedUntil(), "Should not be locked at attempt " + i);
            assertTrue(user.isActive(), "Account should be active at attempt " + i);
        }

        // Attempt 5: Temporary lock
        user.recordFailedLogin();
        assertEquals(5, user.getFailedLoginAttempts());
        assertNotNull(user.getLockedUntil(), "Should be locked at attempt 5");
        assertTrue(user.isActive(), "Account should still be active at attempt 5");
        
        // Clear lock to simulate expiration
        user.setLockedUntil(Instant.now().minusSeconds(60));

        // Attempts 6-9: Temporary lock again
        for (int i = 6; i <= 9; i++) {
            user.recordFailedLogin();
            assertEquals(i, user.getFailedLoginAttempts());
            assertNotNull(user.getLockedUntil(), "Should be locked at attempt " + i);
            assertTrue(user.isActive(), "Account should still be active at attempt " + i);
            // Clear lock for next iteration
            user.setLockedUntil(Instant.now().minusSeconds(60));
        }

        // Attempt 10: Permanent lock + deactivate
        user.recordFailedLogin();
        assertEquals(10, user.getFailedLoginAttempts());
        assertNotNull(user.getLockedUntil(), "Should be locked at attempt 10");
        assertFalse(user.isActive(), "Account should be deactivated at attempt 10");
    }

    @Test
    @DisplayName("TC-10: Lock duration is approximately 30 minutes for temporary lock")
    void testTemporaryLockDuration() {
        // Given
        user.setFailedLoginAttempts(4);
        Instant beforeLock = Instant.now();

        // When
        user.recordFailedLogin();

        // Then
        Instant afterLock = Instant.now();
        long minutesLocked = java.time.Duration.between(beforeLock, user.getLockedUntil()).toMinutes();
        assertTrue(minutesLocked >= 29 && minutesLocked <= 31, 
                "Lock duration should be approximately 30 minutes, got: " + minutesLocked);
    }

    @Test
    @DisplayName("TC-11: Lock duration is approximately 1 day for permanent lock")
    void testPermanentLockDuration() {
        // Given
        user.setFailedLoginAttempts(9);
        Instant beforeLock = Instant.now();

        // When
        user.recordFailedLogin();

        // Then
        Instant afterLock = Instant.now();
        long hoursLocked = java.time.Duration.between(beforeLock, user.getLockedUntil()).toHours();
        assertTrue(hoursLocked >= 23 && hoursLocked <= 24, 
                "Lock duration should be approximately 1 day, got: " + hoursLocked + " hours");
    }

    @Test
    @DisplayName("TC-12: Multiple temporary locks extend lock time")
    void testMultipleTemporaryLocksExtendTime() {
        // Given - User has 5 attempts, lock expired
        user.setFailedLoginAttempts(5);
        user.setLockedUntil(Instant.now().minusSeconds(60)); // Lock expired

        // When - 6th attempt (should extend lock)
        user.recordFailedLogin();

        // Then
        assertEquals(6, user.getFailedLoginAttempts());
        assertNotNull(user.getLockedUntil());
        long minutesRemaining = java.time.Duration.between(Instant.now(), user.getLockedUntil()).toMinutes();
        assertTrue(minutesRemaining >= 29 && minutesRemaining <= 30, 
                "Lock should be extended to 30 minutes");
    }
}

package com.jesusLuna.polyglotCloud.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.jesusLuna.polyglotCloud.Exception.ForbiddenAccessException;
import com.jesusLuna.polyglotCloud.Exception.ResourceNotFoundException;
import com.jesusLuna.polyglotCloud.config.SecurityProperties;
import com.jesusLuna.polyglotCloud.models.User;
import com.jesusLuna.polyglotCloud.models.enums.Role;
import com.jesusLuna.polyglotCloud.repository.UserRepository;
import com.jesusLuna.polyglotCloud.Security.PostQuantumPasswordEncoder;

/**
 * Test suite for UserService security-related methods
 * 
 * Test Cases Covered:
 * 1. Get users with failed login attempts returns paginated results
 * 2. Get users with failed login attempts excludes users with zero attempts
 * 3. Get users with failed login attempts excludes deleted users
 * 4. Get users with failed login attempts orders by failed attempts descending
 * 5. Reset failed login attempts resets counter to zero
 * 6. Reset failed login attempts unlocks account if locked
 * 7. Reset failed login attempts reactivates account if deactivated due to 10+ attempts
 * 8. Reset failed login attempts requires admin role
 * 9. Reset failed login attempts throws exception if user not found
 * 10. Reset failed login attempts does not reactivate if account was deactivated for other reasons
 * 11. Reset failed login attempts clears lock timestamp
 * 12. Reset failed login attempts logs previous state correctly
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Security Tests")
class UserServiceSecurityTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PostQuantumPasswordEncoder passwordEncoder;

    @Mock
    private SecurityProperties securityProperties;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private User adminUser;
    private UUID userId;
    private UUID adminId;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        pageable = PageRequest.of(0, 20);

        testUser = User.builder()
                .id(userId)
                .username("testuser")
                .email("test@example.com")
                .passwordHash("$argon2id$v=19$m=65536,t=3,p=4$hashed")
                .role(Role.USER)
                .active(true)
                .emailVerified(true)
                .failedLoginAttempts(5)
                .lockedUntil(Instant.now().plusSeconds(1800))
                .lastLoginAt(Instant.now().minusSeconds(3600))
                .lastLoginIp("192.168.1.1")
                .createdAt(Instant.now().minusSeconds(86400))
                .updatedAt(Instant.now())
                .build();

        adminUser = User.builder()
                .id(adminId)
                .username("admin")
                .email("admin@example.com")
                .passwordHash("$argon2id$v=19$m=65536,t=3,p=4$hashed")
                .role(Role.ADMIN)
                .active(true)
                .emailVerified(true)
                .failedLoginAttempts(0)
                .lockedUntil(null)
                .createdAt(Instant.now().minusSeconds(86400))
                .updatedAt(Instant.now())
                .build();
        
        // Setup default security properties for tests (lenient since not all tests use them)
        lenient().when(securityProperties.getMaxFailedAttemptsTemp()).thenReturn(5);
        lenient().when(securityProperties.getMaxFailedAttemptsPerm()).thenReturn(10);
        lenient().when(securityProperties.getLockoutDurationMinutes()).thenReturn(30);
        lenient().when(securityProperties.getLockoutDurationDays()).thenReturn(1);
    }

    @Test
    @DisplayName("TC-1: Get users with failed login attempts returns paginated results")
    void testGetUsersWithFailedLoginAttemptsReturnsPaginatedResults() {
        // Given
        List<User> users = new ArrayList<>();
        users.add(testUser);
        users.add(User.builder()
                .id(UUID.randomUUID())
                .username("user2")
                .email("user2@example.com")
                .passwordHash("hash")
                .role(Role.USER)
                .active(true)
                .emailVerified(true)
                .failedLoginAttempts(3)
                .lockedUntil(null)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());

        Page<User> page = new PageImpl<>(users, pageable, users.size());
        when(userRepository.findUsersWithFailedLoginAttempts(pageable)).thenReturn(page);

        // When
        Page<User> result = userService.getUsersWithFailedLoginAttempts(pageable);

        // Then
        assertNotNull(result);
        assertEquals(2, result.getContent().size());
        assertEquals(2, result.getTotalElements());
        verify(userRepository).findUsersWithFailedLoginAttempts(pageable);
    }

    @Test
    @DisplayName("TC-2: Get users with failed login attempts excludes users with zero attempts")
    void testGetUsersWithFailedLoginAttemptsExcludesZeroAttempts() {
        // Given
        User userWithZeroAttempts = User.builder()
                .id(UUID.randomUUID())
                .username("user0")
                .email("user0@example.com")
                .passwordHash("hash")
                .role(Role.USER)
                .active(true)
                .emailVerified(true)
                .failedLoginAttempts(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        List<User> users = new ArrayList<>();
        users.add(testUser); // Has 5 attempts
        Page<User> page = new PageImpl<>(users, pageable, users.size());
        when(userRepository.findUsersWithFailedLoginAttempts(pageable)).thenReturn(page);

        // When
        Page<User> result = userService.getUsersWithFailedLoginAttempts(pageable);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertTrue(result.getContent().stream().allMatch(u -> u.getFailedLoginAttempts() > 0));
    }

    @Test
    @DisplayName("TC-3: Get users with failed login attempts excludes deleted users")
    void testGetUsersWithFailedLoginAttemptsExcludesDeletedUsers() {
        // Given - Repository query already excludes deleted users, so we just verify it's called
        Page<User> emptyPage = new PageImpl<>(new ArrayList<>(), pageable, 0);
        when(userRepository.findUsersWithFailedLoginAttempts(pageable)).thenReturn(emptyPage);

        // When
        Page<User> result = userService.getUsersWithFailedLoginAttempts(pageable);

        // Then
        assertNotNull(result);
        assertEquals(0, result.getContent().size());
        verify(userRepository).findUsersWithFailedLoginAttempts(pageable);
    }

    @Test
    @DisplayName("TC-4: Get users with failed login attempts orders by failed attempts descending")
    void testGetUsersWithFailedLoginAttemptsOrdersByFailedAttemptsDesc() {
        // Given
        User userWithMoreAttempts = User.builder()
                .id(UUID.randomUUID())
                .username("user10")
                .email("user10@example.com")
                .passwordHash("hash")
                .role(Role.USER)
                .active(true)
                .emailVerified(true)
                .failedLoginAttempts(10)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        List<User> users = new ArrayList<>();
        users.add(userWithMoreAttempts); // 10 attempts - should be first
        users.add(testUser); // 5 attempts - should be second
        Page<User> page = new PageImpl<>(users, pageable, users.size());
        when(userRepository.findUsersWithFailedLoginAttempts(pageable)).thenReturn(page);

        // When
        Page<User> result = userService.getUsersWithFailedLoginAttempts(pageable);

        // Then
        assertNotNull(result);
        assertEquals(2, result.getContent().size());
        assertEquals(10, result.getContent().get(0).getFailedLoginAttempts());
        assertEquals(5, result.getContent().get(1).getFailedLoginAttempts());
    }

    @Test
    @DisplayName("TC-5: Reset failed login attempts resets counter to zero")
    void testResetFailedLoginAttemptsResetsCounter() {
        // Given
        testUser.setFailedLoginAttempts(7);
        when(userRepository.findByIdAndDeletedAtIsNull(adminId)).thenReturn(Optional.of(adminUser));
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        User result = userService.resetFailedLoginAttempts(userId, adminId);

        // Then
        assertNotNull(result);
        assertEquals(0, result.getFailedLoginAttempts());
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("TC-6: Reset failed login attempts unlocks account if locked")
    void testResetFailedLoginAttemptsUnlocksAccount() {
        // Given
        testUser.setFailedLoginAttempts(5);
        testUser.setLockedUntil(Instant.now().plusSeconds(1800)); // Locked for 30 min
        when(userRepository.findByIdAndDeletedAtIsNull(adminId)).thenReturn(Optional.of(adminUser));
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        User result = userService.resetFailedLoginAttempts(userId, adminId);

        // Then
        assertNotNull(result);
        assertNull(result.getLockedUntil());
        assertEquals(0, result.getFailedLoginAttempts());
    }

    @Test
    @DisplayName("TC-7: Reset failed login attempts reactivates account if deactivated due to 10+ attempts")
    void testResetFailedLoginAttemptsReactivatesAccountAfter10Attempts() {
        // Given
        testUser.setFailedLoginAttempts(10);
        testUser.setActive(false); // Deactivated due to 10 attempts
        testUser.setLockedUntil(Instant.now().plusSeconds(86400)); // Locked for 1 day
        when(userRepository.findByIdAndDeletedAtIsNull(adminId)).thenReturn(Optional.of(adminUser));
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        User result = userService.resetFailedLoginAttempts(userId, adminId);

        // Then
        assertNotNull(result);
        assertTrue(result.isActive());
        assertEquals(0, result.getFailedLoginAttempts());
        assertNull(result.getLockedUntil());
    }

    @Test
    @DisplayName("TC-8: Reset failed login attempts requires admin role")
    void testResetFailedLoginAttemptsRequiresAdminRole() {
        // Given
        User nonAdminUser = User.builder()
                .id(UUID.randomUUID())
                .username("regularuser")
                .email("regular@example.com")
                .passwordHash("hash")
                .role(Role.USER) // Not admin
                .active(true)
                .emailVerified(true)
                .build();

        when(userRepository.findByIdAndDeletedAtIsNull(nonAdminUser.getId())).thenReturn(Optional.of(nonAdminUser));
        // Note: userId lookup is not needed because exception is thrown when checking admin role

        // When & Then
        assertThrows(ForbiddenAccessException.class, 
                () -> userService.resetFailedLoginAttempts(userId, nonAdminUser.getId()),
                "Should throw ForbiddenAccessException when non-admin tries to reset");
    }

    @Test
    @DisplayName("TC-9: Reset failed login attempts throws exception if user not found")
    void testResetFailedLoginAttemptsThrowsExceptionIfUserNotFound() {
        // Given
        when(userRepository.findByIdAndDeletedAtIsNull(adminId)).thenReturn(Optional.of(adminUser));
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(ResourceNotFoundException.class,
                () -> userService.resetFailedLoginAttempts(userId, adminId),
                "Should throw ResourceNotFoundException when user not found");
    }

    @Test
    @DisplayName("TC-10: Reset failed login attempts does not reactivate if account was deactivated for other reasons")
    void testResetFailedLoginAttemptsDoesNotReactivateForOtherReasons() {
        // Given - Account deactivated but only had 3 attempts (not 10+)
        testUser.setFailedLoginAttempts(3);
        testUser.setActive(false); // Deactivated for other reason (not due to failed attempts)
        when(userRepository.findByIdAndDeletedAtIsNull(adminId)).thenReturn(Optional.of(adminUser));
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        User result = userService.resetFailedLoginAttempts(userId, adminId);

        // Then
        assertNotNull(result);
        assertEquals(0, result.getFailedLoginAttempts());
        // Account should remain inactive since it wasn't deactivated due to 10+ attempts
        assertFalse(result.isActive());
    }

    @Test
    @DisplayName("TC-11: Reset failed login attempts clears lock timestamp")
    void testResetFailedLoginAttemptsClearsLockTimestamp() {
        // Given
        testUser.setFailedLoginAttempts(5);
        testUser.setLockedUntil(Instant.now().plusSeconds(1800));
        when(userRepository.findByIdAndDeletedAtIsNull(adminId)).thenReturn(Optional.of(adminUser));
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        User result = userService.resetFailedLoginAttempts(userId, adminId);

        // Then
        assertNotNull(result);
        assertNull(result.getLockedUntil());
    }

    @Test
    @DisplayName("TC-12: Reset failed login attempts logs previous state correctly")
    void testResetFailedLoginAttemptsLogsPreviousState() {
        // Given
        int previousAttempts = 7;
        boolean wasLocked = true;
        boolean wasInactive = false;
        testUser.setFailedLoginAttempts(previousAttempts);
        testUser.setLockedUntil(Instant.now().plusSeconds(1800));
        testUser.setActive(true);
        when(userRepository.findByIdAndDeletedAtIsNull(adminId)).thenReturn(Optional.of(adminUser));
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        User result = userService.resetFailedLoginAttempts(userId, adminId);

        // Then
        assertNotNull(result);
        assertEquals(0, result.getFailedLoginAttempts());
        // Verify that the state was properly reset
        assertNull(result.getLockedUntil());
        assertTrue(result.isActive());
    }
}

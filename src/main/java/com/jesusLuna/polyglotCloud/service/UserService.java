package com.jesusLuna.polyglotCloud.service;

import java.util.UUID;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jesusLuna.polyglotCloud.DTO.UserDTO;
import com.jesusLuna.polyglotCloud.Exception.BusinessRuleException;
import com.jesusLuna.polyglotCloud.Exception.ForbiddenAccessException;
import com.jesusLuna.polyglotCloud.Exception.ResourceNotFoundException;
import com.jesusLuna.polyglotCloud.models.User;
import com.jesusLuna.polyglotCloud.models.enums.Role;
import com.jesusLuna.polyglotCloud.repository.UserRepository;
import com.jesusLuna.polyglotCloud.Security.PostQuantumPasswordEncoder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PostQuantumPasswordEncoder passwordEncoder;

    @Transactional
    public User createUser(UserDTO.UserRegistrationRequest request, UUID creatorId) {
        log.debug("Creating user with username: {} by admin: {}", request.username(), creatorId);

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
                .role(Role.USER) // Default role
                .active(true)
                .emailVerified(false)
                .build();

        user.changePassword(passwordEncoder.encode(request.password()));

        User saved = userRepository.save(user);
        log.info("User created successfully with id: {} by admin: {}", saved.getId(), creatorId);
        return saved;
    }
    @Cacheable(value = "users", key = "#id")
    public User getUserById(UUID id) {
    log.debug("Fetching active user with id: {}", id);
    return userRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    public User getUserByUsername(String username) {
        log.debug("Fetching active user with username: {}", username);
        return userRepository.findByUsernameAndDeletedAtIsNull(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
    }

    public Page<User> listAllUsers(Pageable pageable) {
        log.debug("Listing all active users with pageable: {}", pageable);
        return userRepository.findByDeletedAtIsNull(pageable);
    }

    public Page<User> searchUsers(UserDTO.UserSearchFilters filters, Pageable pageable) {
        log.debug("Searching users with filters: {}", filters);
        return userRepository.searchUsers(
                filters.query(),
                filters.role(),
                filters.active(),
                pageable
        );
    }

    public Page<User> listUsersByRole(Role role, Pageable pageable) {
        log.debug("Listing users by role: {} with pageable: {}", role, pageable);
        return userRepository.findByRoleAndDeletedAtIsNull(role, pageable);
    }

    public Page<User> listActiveUsers(Pageable pageable) {
        log.debug("Listing active users with pageable: {}", pageable);
        return userRepository.findByActiveTrueAndDeletedAtIsNull(pageable);
    }

    @CachePut(value = "users", key = "#result.id")
    @Transactional
    public User updateUserProfile(UUID userId, UserDTO.UserUpdateRequest request, UUID requesterId) {
        log.debug("Updating user profile {} by user {}", userId, requesterId);

        User user = getUserById(userId);
        User requester = getUserById(requesterId);

        // Validate authorization: user can update own profile, or requester is admin
        if (!userId.equals(requesterId) && !requester.canAdministrate()) {
            throw new ForbiddenAccessException("You don't have permission to update this user");
        }

        // Update fields if provided
        if (request.username() != null) {
            user.setUsername(request.username());
        }

        if (request.email() != null && !request.email().equals(user.getEmail())) {
            // Validate email uniqueness
            if (userRepository.existsByEmailAndDeletedAtIsNull(request.email())) {
                throw new BusinessRuleException("Email already exists: " + request.email());
            }
            user.setEmail(request.email());
            user.setEmailVerified(false); // Require re-verification
            log.info("User {} email changed, verification required", userId);
        }

        User updated = userRepository.save(user);
        log.info("User profile updated successfully: {}", userId);
        return updated;
    }

    @Transactional
    public void changePassword(UUID userId, UserDTO.UserPasswordChangeRequest request, UUID requesterId) {
        log.debug("Changing password for user: {}", userId);

        // Fetch user first to ensure it exists
        User user = getUserById(userId);

        // Only user can change their own password
        if (!userId.equals(requesterId)) {
            throw new ForbiddenAccessException("You can only change your own password");
        }

        // Validate current password
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException("Current password is incorrect");
        }

        // Change password
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        log.info("Password changed successfully for user: {}", userId);
    }

    @Transactional
    public User updateUserRole(UUID userId, Role newRole, UUID adminId) {
        log.debug("Updating role for user {} to {} by admin {}", userId, newRole, adminId);

        User admin = getUserById(adminId);
        if (!admin.canAdministrate()) {
            throw new ForbiddenAccessException("Only administrators can change user roles");
        }

        // Prevent admin from demoting themselves
        if (adminId.equals(userId) && newRole != Role.ADMIN) {
            throw new ForbiddenAccessException("Administrators cannot demote themselves");
        }

        User user = getUserById(userId);
        Role oldRole = user.getRole();

        // Prevent removing the last admin
        if (oldRole == Role.ADMIN && newRole != Role.ADMIN) {
            long adminCount = userRepository.countByRole(Role.ADMIN);
            if (adminCount == 1) {
                throw new ForbiddenAccessException("Cannot remove the last administrator");
            }
        }

        user.setRole(newRole);

        User updated = userRepository.save(user);
        log.info("User {} role changed from {} to {} by admin {}", userId, oldRole, newRole, adminId);
        return updated;
    }

    @Transactional
    public User updateUserStatus(UUID userId, boolean active, UUID adminId) {
        log.debug("Updating status for user {} to {} by admin {}", userId, active, adminId);

        User admin = getUserById(adminId);
        if (!admin.canAdministrate()) {
            throw new ForbiddenAccessException("Only administrators can change user status");
        }

        User user = getUserById(userId);
        user.setActive(active);

        User updated = userRepository.save(user);
        log.info("User {} status changed to {} by admin {}", userId, active, adminId);
        return updated;
    }

    @CacheEvict(value = "users", key = "#id")
    @Transactional
    public void deleteUser(UUID userId, UUID adminId) {
        log.debug("Soft deleting user {} by admin {}", userId, adminId);

        User admin = getUserById(adminId);
        if (!admin.canAdministrate()) {
            throw new ForbiddenAccessException("Only administrators can delete users");
        }

        // Evitar que el admin se elimine a sí mismo
        if (userId.equals(adminId)) {
            throw new BusinessRuleException("Administrators cannot delete their own account");
        }

        User user = getUserById(userId);
        
        // Verificar si ya está eliminado
        if (user.isDeleted()) {
            throw new BusinessRuleException("User is already deleted");
        }
        
        // Verificar si es el último admin
        if (user.getRole() == Role.ADMIN) {
            long adminCount = userRepository.countByRoleAndDeletedAtIsNull(Role.ADMIN);
            if (adminCount <= 1) {
                throw new BusinessRuleException("Cannot delete the last administrator");
            }
        }

        // ✅ USAR EL MÉTODO SOFT DELETE DEL MODELO
        user.softDelete();
        
        userRepository.save(user);
        log.info("User {} soft deleted successfully by admin {}", userId, adminId);
    }

    @Transactional
    public User restoreUser(UUID userId, UUID adminId) {
        log.debug("Restoring deleted user {} by admin {}", userId, adminId);

        User admin = getUserById(adminId);
        if (!admin.canAdministrate()) {
            throw new ForbiddenAccessException("Only administrators can restore users");
        }

        // Buscar incluyendo eliminados
        User user = userRepository.findByIdIncludingDeleted(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (!user.isDeleted()) {
            throw new BusinessRuleException("User is not deleted");
        }

        // ✅ USAR EL MÉTODO RESTORE DEL MODELO
        user.restore();
        
        User restored = userRepository.save(user);
        log.info("User {} restored successfully by admin {}", userId, adminId);
        return restored;
    }

    @CacheEvict(value = "users", allEntries = true)
    public void clearAllUsersCache() {
        log.info("Clearing all users cache");
    }

    public boolean isUsernameAvailable(String username) {
        return !userRepository.existsByUsernameAndDeletedAtIsNull(username);
    }

    public boolean isEmailAvailable(String email) {
        return !userRepository.existsByEmailAndDeletedAtIsNull(email);
    }
}
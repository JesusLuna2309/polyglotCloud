package com.jesusLuna.polyglotCloud.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jesusLuna.polyglotCloud.security.PostQuantumPasswordEncoder;
import com.jesusLuna.polyglotCloud.dto.UserDTO;
import com.jesusLuna.polyglotCloud.exception.BusinessRuleException;
import com.jesusLuna.polyglotCloud.exception.ForbiddenAccessException;
import com.jesusLuna.polyglotCloud.exception.ResourceNotFoundException;
import com.jesusLuna.polyglotCloud.models.User;
import com.jesusLuna.polyglotCloud.models.enums.Role;
import com.jesusLuna.polyglotCloud.repository.UserRespository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UserService {

    private final UserRespository userRepository;
    private final PostQuantumPasswordEncoder passwordEncoder;

    @Transactional
    public User createUser(UserDTO.UserRegistrationRequest request, UUID creatorId) {
        log.debug("Creating user with username: {} by admin: {}", request.username(), creatorId);

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
                .role(Role.USER) // Default role
                .active(true)
                .emailVerified(false)
                .build();

        user.changePassword(passwordEncoder.encode(request.password()));

        User saved = userRepository.save(user);
        log.info("User created successfully with id: {} by admin: {}", saved.getId(), creatorId);
        return saved;
    }

    public User getUserById(UUID id) {
        log.debug("Fetching user with id: {}", id);
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    public User getUserByUsername(String username) {
        log.debug("Fetching user with username: {}", username);
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
    }

    public Page<User> listAllUsers(Pageable pageable) {
        log.debug("Listing all users with pageable: {}", pageable);
        return userRepository.findAll(pageable);
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
        return userRepository.findByRole(role, pageable);
    }

    public Page<User> listActiveUsers(Pageable pageable) {
        log.debug("Listing active users with pageable: {}", pageable);
        return userRepository.findByActiveTrue(pageable);
    }

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
            if (userRepository.existsByEmail(request.email())) {
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

    @Transactional
    public void deleteUser(UUID userId, UUID adminId) {
        log.debug("Soft deleting user {} by admin {}", userId, adminId);

        if (userId.equals(adminId)) {
            throw new BusinessRuleException("Administrators cannot delete their own account");
        }

        updateUserStatus(userId, false, adminId);
        log.info("User {} soft deleted by admin {}", userId, adminId);
    }

    public boolean isUsernameAvailable(String username) {
        return !userRepository.existsByUsername(username);
    }

    public boolean isEmailAvailable(String email) {
        return !userRepository.existsByEmail(email);
    }
}
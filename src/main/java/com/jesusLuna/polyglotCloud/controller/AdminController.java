package com.jesusLuna.polyglotCloud.controller;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jesusLuna.polyglotCloud.DTO.UserDTO;
import com.jesusLuna.polyglotCloud.Exception.ForbiddenAccessException;
import com.jesusLuna.polyglotCloud.Exception.ResourceNotFoundException;
import com.jesusLuna.polyglotCloud.models.User;
import com.jesusLuna.polyglotCloud.repository.UserRepository;
import com.jesusLuna.polyglotCloud.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/users")
@Tag(
    name = "Admin User Management",
    description = "Administrative endpoints for user security management"
)
public class AdminController {

    private final UserService userService;
    private final UserRepository userRepository;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/security-alerts")
    @Operation(
        summary = "Get users with failed login attempts",
        description = "Retrieve a paginated list of users who have failed login attempts. " +
                     "Useful for security monitoring and identifying potential security issues."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Security alerts retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "Access denied - Admin role required")
    })
    public ResponseEntity<Page<UserDTO.SecurityAlertResponse>> getSecurityAlerts(
            @PageableDefault(size = 20, sort = "failedLoginAttempts", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal UserDetails userDetails) {

        // Validate authentication
        if (userDetails == null) {
            throw new ForbiddenAccessException("Authentication required");
        }

        // Get users with failed login attempts
        Page<User> users = userService.getUsersWithFailedLoginAttempts(pageable);

        // Map to SecurityAlertResponse DTO
        Page<UserDTO.SecurityAlertResponse> response = users.map(user -> {
            boolean isLocked = user.getLockedUntil() != null && 
                              user.getLockedUntil().isAfter(Instant.now());
            
            return new UserDTO.SecurityAlertResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFailedLoginAttempts(),
                user.getLockedUntil(),
                user.getLastLoginAt(),
                user.getLastLoginIp(),
                isLocked,
                user.isActive(),
                user.getCreatedAt()
            );
        });

        log.info("Security alerts retrieved: {} users with failed login attempts", response.getTotalElements());
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/reset-failed-login")
    @Operation(
        summary = "Reset failed login counter",
        description = "Reset the failed login attempts counter for a user. " +
                     "This will unlock the account if it was locked and clear the failed attempts counter. " +
                     "Useful when a legitimate user is locked out or after security review."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Failed login counter reset successfully"),
        @ApiResponse(responseCode = "403", description = "Access denied - Admin role required"),
        @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<UserDTO.SecurityAlertResponse> resetFailedLogin(
            @Parameter(description = "User ID to reset failed login attempts for")
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {

        // Validate authentication
        if (userDetails == null) {
            throw new ForbiddenAccessException("Authentication required");
        }

        // Get admin user
        String loginIdentifier = userDetails.getUsername();
        User admin = userRepository.findByUsernameOrEmailAndDeletedAtIsNull(loginIdentifier, loginIdentifier)
                .orElseThrow(() -> new ResourceNotFoundException("Admin user not found"));

        // Reset failed login attempts
        User updated = userService.resetFailedLoginAttempts(id, admin.getId());

        // Map to SecurityAlertResponse
        boolean isLocked = updated.getLockedUntil() != null && 
                          updated.getLockedUntil().isAfter(Instant.now());
        
        UserDTO.SecurityAlertResponse response = new UserDTO.SecurityAlertResponse(
            updated.getId(),
            updated.getUsername(),
            updated.getEmail(),
            updated.getFailedLoginAttempts(),
            updated.getLockedUntil(),
            updated.getLastLoginAt(),
            updated.getLastLoginIp(),
            isLocked,
            updated.isActive(),
            updated.getCreatedAt()
        );

        log.info("Failed login attempts reset for user {} by admin {}", id, admin.getId());
        return ResponseEntity.ok(response);
    }
}

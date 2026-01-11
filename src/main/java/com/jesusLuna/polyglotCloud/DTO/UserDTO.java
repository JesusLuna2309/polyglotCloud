package com.jesusLuna.polyglotCloud.DTO;

import java.time.Instant;
import java.util.UUID;

import com.jesusLuna.polyglotCloud.models.enums.Role;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class UserDTO {

    public record UserResponse(
            UUID id,
            String username,
            String email,
            Role role,
            boolean active,
            boolean emailVerified,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record UserAdminResponse(
            UUID id,
            String username,
            String email,
            Role role,
            boolean active,
            boolean emailVerified,
            int failedLoginAttempts,
            Instant lockedUntil,
            Instant lastLoginAt,
            String lastLoginIp,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record UserPublicResponse(
            UUID id,
            String lastName,
            Instant createdAt
    ) {}

    public record UserRegistrationRequest(
            @NotBlank(message = "Username is required")
            @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
            String username,
            
            @NotBlank(message = "Email is required")
            @Email(message = "Email must be valid")
            @Size(max = 255, message = "Email cannot exceed 255 characters")
            String email,
            
            @NotBlank(message = "Password is required")
            @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
            String password,
            
            @Size(max = 100, message = "First name cannot exceed 100 characters")
            String firstName,
            
            @Size(max = 100, message = "Last name cannot exceed 100 characters")
            String lastName
    ) {}

    public record UserLoginRequest(
            @NotBlank(message = "Username or email is required")
            String login,
            
            @NotBlank(message = "Password is required")
            String password
    ) {}

    public record UserLoginResponse(
            String token,
            String refreshToken,
            UserResponse user,
            Instant expiresAt,
            Instant refreshExpiresAt
    ) {}

    public record UserUpdateRequest(
            @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
            String username,
            
            @Email(message = "Email must be valid")
            @Size(max = 255, message = "Email cannot exceed 255 characters")
            String email
    ) {}

    public record UserPasswordChangeRequest(
            @NotBlank(message = "Current password is required")
            String currentPassword,
            
            @NotBlank(message = "New password is required")
            @Size(min = 8, max = 100, message = "New password must be at least 8 characters")
            String newPassword
    ) {}

    public record UserRoleUpdateRequest(
            @NotNull(message = "Role is required")
            Role role
    ) {}

    public record UserSearchFilters(
            String query,        // Busca en username, email, firstName, lastName
            Role role,           // Filtra por rol
            Boolean active,      // Filtra por estado activo
            Boolean emailVerified // Filtra por email verificado
    ) {}

    public record ErrorResponse(
            String message,
            String detail,
            String path,
            Instant timestamp
    ) {}
}

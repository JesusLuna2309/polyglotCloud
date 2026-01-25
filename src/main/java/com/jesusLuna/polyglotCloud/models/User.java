package com.jesusLuna.polyglotCloud.models;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import com.jesusLuna.polyglotCloud.models.enums.Role;
import com.jesusLuna.polyglotCloud.security.PostQuantumPasswordEncoder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

        private static final int MAX_FAILED_ATTEMPTS_TEMP = 5;
        private static final int MAX_FAILED_ATTEMPTS_PERM = 10;
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @NotBlank
    @Email
    @Size(max = 255)
    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @NotBlank
    @Size(min = 3, max = 50)
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$")
    @Column(nullable = false, unique = true, length = 50, updatable = false)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 60)
    @Setter(AccessLevel.NONE)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Role role = Role.USER;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private boolean emailVerified = false;

    @Column(name = "email_verification_token", length = 64)
    @Setter(AccessLevel.NONE)
    @Getter(AccessLevel.NONE)
    private String emailVerificationToken;

    @Column(name = "last_password_change")
    private Instant lastPasswordChange;

    @Column(name = "email_verification_expires")
    private Instant emailVerificationExpires;

    @Column(name = "password_reset_token", length = 64)
    @Setter(AccessLevel.NONE)
    @Getter(AccessLevel.NONE)
    private String passwordResetToken;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Size(max = 45)
    @Column(name = "last_login_ip", length = 45, nullable = true)
    private String lastLoginIp;

    @Transient
    public boolean isAccountNonLocked() {
        return lockedUntil == null || lockedUntil.isBefore(Instant.now());
    }

    @Min(0)
    @Column(name = "failed_login_attempts", nullable = false)
    @Builder.Default
    private int failedLoginAttempts = 0;

    @Column(name = "password_reset_expires")
    private Instant passwordResetExpires;

    @Transient
    public boolean canModerate() {
        return role.hasPermission(Role.MODERATOR);
    }

    @Transient
    public boolean canAdministrate() {
        return role.hasPermission(Role.ADMIN);
    }

    public void recordFailedLogin() {
        this.failedLoginAttempts++;
        if (this.failedLoginAttempts >= MAX_FAILED_ATTEMPTS_TEMP) {
            this.lockedUntil = Instant.now().plus(30, ChronoUnit.MINUTES);
        }
        if (this.failedLoginAttempts >= MAX_FAILED_ATTEMPTS_PERM) {
            this.lockedUntil = Instant.now().plus(1, ChronoUnit.DAYS);
            this.active = false;
        }
    }


    public void recordSuccessfulLogin(String ipAddress) {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
        this.lastLoginAt = Instant.now();
        this.lastLoginIp = ipAddress;
    }

    public boolean verifyEmailToken(String token) {
        if (token == null || !token.equals(this.emailVerificationToken))
            return false;
        if (emailVerificationExpires == null || emailVerificationExpires.isBefore(Instant.now()))
            return false;
        this.emailVerified = true;
        this.emailVerificationToken = null;
        this.emailVerificationExpires = null;
        return true;
    }


    public void changePassword(String newPasswordHash) {
        // ✅ Validación más flexible y correcta
        if (newPasswordHash == null || newPasswordHash.trim().isEmpty()) {
            throw new IllegalArgumentException("Password hash cannot be null or empty");
        }
        
        // ✅ Validación de longitud mínima razonable (no exacta)
        if (newPasswordHash.length() < 50) {
            throw new IllegalArgumentException("Invalid password hash format");
        }
        
        if (newPasswordHash.equals(this.passwordHash)) {
            throw new IllegalArgumentException("New password must be different from the old one");
        }
        
        this.passwordHash = newPasswordHash;
        this.lastPasswordChange = Instant.now();
        this.passwordResetToken = null;
        this.passwordResetExpires = null;
    }

    /**
     * Genera token de verificación usando criptografía post-cuántica
     */
    public String generateEmailVerificationToken(PostQuantumPasswordEncoder encoder) {
        this.emailVerificationToken = encoder.generateEmailVerificationToken();
        this.emailVerificationExpires = Instant.now().plus(24, ChronoUnit.HOURS);
        return this.emailVerificationToken;
    }

        /**
     * Soft delete - marca como eliminado sin borrar de BD
     */
    public void softDelete() {
        this.deletedAt = Instant.now();
        this.active = false;
        this.emailVerificationToken = null;
        this.passwordResetToken = null;
    }
    
    /**
     * Restaurar usuario soft-deleted
     */
    public void restore() {
        this.deletedAt = null;
        this.active = true;
    }
    
    /**
     * Verifica si el usuario está soft-deleted
     */
    @Transient
    public boolean isDeleted() {
        return this.deletedAt != null;
    }
    
    /**
     * Verifica si el usuario está disponible (no eliminado)
     */
    @Transient
    public boolean isAvailable() {
        return this.deletedAt == null && this.active;
    }

    
}
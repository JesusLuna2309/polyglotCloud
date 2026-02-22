package com.jesusLuna.polyglotCloud.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.jesusLuna.polyglotCloud.models.User;
import com.jesusLuna.polyglotCloud.models.enums.Role;

@Repository
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User>{

    
    Optional<User> findByIdAndDeletedAtIsNull(UUID id);

    Optional<User> findByUsernameAndDeletedAtIsNull(String username);

    Optional<User> findByEmailAndDeletedAtIsNull(String email);
    Optional<User> findByUsernameOrEmailAndDeletedAtIsNull(String username, String email);

    Optional<User> findByUsernameOrEmailAndPasswordHashAndDeletedAtIsNull(String username, String email, String passwordHash);

    Optional<User> findByEmailVerificationToken(String token); // VERIFY EMAIL

    Optional<User> findByPasswordResetToken(String token); // RESET PASSWORD

    Page<User> findAllByDeletedAtIsNull(Pageable pageable);


    // Validaciones

    Page<User> findByActiveTrueAndDeletedAtIsNull(Pageable pageable);

    Page<User> findByRoleAndDeletedAtIsNull(Role role, Pageable pageable);

    Page<User> findByEmailVerifiedFalseAndDeletedAtIsNull(Pageable pageable);

    Page<User> findByDeletedAtIsNull(Pageable pageable);

    List<User> findByActiveAndDeletedAtIsNull(boolean enabled);

    List<User> findByEmailVerifiedAndDeletedAtIsNull(boolean emailVerified);

    List<User> findByEmailVerifiedFalseAndDeletedAtIsNull();

    List<User> findByEmailVerifiedTrueAndDeletedAtIsNull();

    @Query("SELECT DISTINCT u FROM User u JOIN Snippet s ON s.user.id = u.id WHERE u.deletedAt IS NULL")
    List<User> findUsersWithSnippets();


    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdIncludingDeleted(@Param("id") UUID id);

    boolean existsByUsernameAndDeletedAtIsNull(String username);
    boolean existsByEmailAndDeletedAtIsNull(String email);
    
    boolean existsByEmailAndDeletedAtIsNullAndIdNot(String email, UUID id);

    boolean existsByUsernameAndDeletedAtIsNullAndIdNot(String username, UUID id);


    @Query("SELECT u FROM User u " +
        "WHERE (:query IS NULL OR " +
        "LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
        "LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%'))) " +
        "AND (:role IS NULL OR u.role = :role) " +
        "AND (:active IS NULL OR u.active = :active)")
    Page<User> searchUsers(
        @Param("query") String query,
        @Param("role") Role role,
        @Param("active") Boolean active,
        Pageable pageable);


        
    @Query("SELECT COUNT(u) FROM User u WHERE u.role = :role")
    long countByRole(@Param("role") Role role);

    long countByRoleAndDeletedAtIsNull(Role role);
    
    long countByActiveAndDeletedAtIsNull(boolean active);

    long countByEmailVerifiedAndDeletedAtIsNull(boolean emailVerified);

    /**
     * Find users with failed login attempts (for security alerts)
     * Returns users where failed_login_attempts > 0 and not deleted
     */
    @Query("SELECT u FROM User u WHERE u.failedLoginAttempts > 0 AND u.deletedAt IS NULL ORDER BY u.failedLoginAttempts DESC, u.lastLoginAt DESC NULLS LAST")
    Page<User> findUsersWithFailedLoginAttempts(Pageable pageable);

}
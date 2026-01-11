package com.jesusLuna.polyglotCloud.repository;

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
public interface UserRespository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User>{

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByUsernameOrEmail(String username, String email);

    Optional<User> findByUsernameOrEmailAndPassword(String username, String email, String password);

    Optional<User> findByEmailVerificationToken(String token); // VERIFY EMAIL

    Optional<User> findByPasswordResetToken(String token); // RESET PASSWORD


    // Validaciones
    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Page<User> findByActiveTrue(Pageable pageable);

    Page<User> findByRole(Role role, Pageable pageable);

    Page<User> findByEmailVerifiedFalse(Pageable pageable);


    @Query("""
                        SELECT u FROM User u
                        WHERE (:query IS NULL OR
                        LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%')) OR
                        LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')) OR
                        LOWER(u.firstName) LIKE LOWER(CONCAT('%', :query, '%')) OR
                        LOWER(u.lastName) LIKE LOWER(CONCAT('%', :query, '%')))
                        AND (:role IS NULL OR u.role = :role)
                        AND (:active IS NULL OR u.active = :active)
                        """)
        Page<User> searchUsers(
                        @Param("query") String query,
                        @Param("role") Role role,
                        @Param("active") Boolean active,
                        Pageable pageable);


        
    @Query("SELECT COUNT(u) FROM User u WHERE u.role = :role")
    long countByRole(@Param("role") Role role);

}

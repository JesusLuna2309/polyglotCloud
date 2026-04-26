package com.jesusLuna.polyglotCloud.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.jesusLuna.polyglotCloud.models.LoginAttempt;

@Repository
public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, UUID> {

    /**
     * Find all login attempts for a specific user
     */
    Page<LoginAttempt> findByUserIdOrderByAttemptTimestampDesc(UUID userId, Pageable pageable);

    /**
     * Find all failed login attempts for a specific user
     */
    @Query("SELECT la FROM LoginAttempt la WHERE la.user.id = :userId AND la.success = false ORDER BY la.attemptTimestamp DESC")
    Page<LoginAttempt> findFailedAttemptsByUserId(@Param("userId") UUID userId, Pageable pageable);

    /**
     * Count failed login attempts for a user within a time period
     */
    @Query("SELECT COUNT(la) FROM LoginAttempt la WHERE la.user.id = :userId AND la.success = false AND la.attemptTimestamp >= :since")
    long countFailedAttemptsSince(@Param("userId") UUID userId, @Param("since") java.time.Instant since);

    /**
     * Find login attempts by IP address
     */
    Page<LoginAttempt> findByIpAddressOrderByAttemptTimestampDesc(String ipAddress, Pageable pageable);
}

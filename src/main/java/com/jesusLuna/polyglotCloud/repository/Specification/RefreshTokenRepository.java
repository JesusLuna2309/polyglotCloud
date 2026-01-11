package com.jesusLuna.polyglotCloud.repository.Specification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.jesusLuna.polyglotCloud.models.RefreshToken;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

        Optional<RefreshToken> findByToken(String token);

        @Query("SELECT rt FROM RefreshToken rt WHERE rt.userId = :userId AND rt.revoked = false")
        List<RefreshToken> findActiveByUserId(@Param("userId") UUID userId);

        @Modifying
        @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.userId = :userId AND rt.revoked = false")
        int revokeAllByUserId(@Param("userId") UUID userId);

        @Modifying
        @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now")
        int deleteExpiredTokens(@Param("now") Instant now);

        @Modifying
        @Query("DELETE FROM RefreshToken rt WHERE rt.revoked = true")
        int deleteRevokedTokens();

        @Query("""
        SELECT CASE WHEN COUNT(rt) > 0 THEN true ELSE false END
        FROM RefreshToken rt
        WHERE rt.token = :token
        AND rt.revoked = false
        AND rt.expiresAt > CURRENT_TIMESTAMP
        """)
        boolean isTokenValid(@Param("token") String token);

        @Query("""
        SELECT COUNT(rt)
        FROM RefreshToken rt
        WHERE rt.userId = :userId
        AND rt.revoked = false
        AND rt.expiresAt > :now
        """)
        long countActiveTokensByUserId(@Param("userId") UUID userId, @Param("now") Instant now);

}

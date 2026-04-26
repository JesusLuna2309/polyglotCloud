package com.jesusLuna.polyglotCloud.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jesusLuna.polyglotCloud.dto.RateLimitsDTO.AbuseStats;
import com.jesusLuna.polyglotCloud.dto.RateLimitsDTO.RateLimitStats;
import com.jesusLuna.polyglotCloud.service.AbuseDetectionService;
import com.jesusLuna.polyglotCloud.service.RateLimitService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/security")
@Tag(name = "Security Monitoring", description = "Security and abuse monitoring endpoints")
@PreAuthorize("hasRole('ADMIN')")
public class SecurityMonitoringController {

    private final RateLimitService rateLimitService;
    private final AbuseDetectionService abuseDetectionService;

    @GetMapping("/rate-limit/stats/{userId}")
    @Operation(
        summary = "Get user rate limit statistics",
        description = """
            Retrieves current rate limiting statistics for a specific user in the 'translations' category.
            Shows how many requests they've made, remaining requests, reset time, and blocking status.
            
            **Requires ADMIN role**
            """,
        operationId = "getUserRateLimitStats"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200", 
            description = "Statistics retrieved successfully",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RateLimitStats.class),
                examples = @ExampleObject(
                    name = "Normal user",
                    summary = "User who has made some requests but is not blocked",
                    value = """
                    {
                      "userId": "123e4567-e89b-12d3-a456-426614174000",
                      "category": "translations",
                      "currentUsage": 8,
                      "limit": 20,
                      "remainingRequests": 12,
                      "resetTime": "2024-01-01T10:15:00Z",
                      "isBlocked": false,
                      "blockExpiresAt": null
                    }
                    """
                )
            )
        ),
        @ApiResponse(
            responseCode = "200", 
            description = "User blocked for exceeding limit",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RateLimitStats.class),
                examples = @ExampleObject(
                    name = "Blocked user",
                    summary = "User who exceeded rate limit and is temporarily blocked",
                    value = """
                    {
                        "userId": "987fcdeb-51a2-43d7-8f9e-123456789abc",
                        "category": "translations",
                        "currentUsage": 20,
                        "limit": 20,
                        "remainingRequests": 0,
                        "resetTime": "2024-01-01T10:15:00Z",
                        "isBlocked": true,
                        "blockExpiresAt": "2024-01-01T10:30:00Z"
                    }
                    """
                )
            )
        ),
        @ApiResponse(
            responseCode = "403", 
            description = "Access denied - ADMIN role required",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    value = """
                    {
                        "message": "Access denied",
                        "detail": "ADMIN role required",
                        "path": "/admin/security/rate-limit/stats/{userId}",
                        "timestamp": "2024-01-01T10:00:00Z"
                    }
                    """
                )
            )
        ),
        @ApiResponse(
            responseCode = "404", 
            description = "User not found or no rate limiting data available",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    value = """
                    {
                        "message": "User not found",
                        "detail": "No rate limit data found for user ID: 123e4567-e89b-12d3-a456-426614174000",
                        "path": "/admin/security/rate-limit/stats/{userId}",
                        "timestamp": "2024-01-01T10:00:00Z"
                    }
                    """
                )
            )
        )
    })
    public ResponseEntity<RateLimitStats> getUserRateLimitStats(
            @PathVariable UUID userId) {
        
        var stats = rateLimitService.getUserRateLimitStats(userId, "translations");
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/abuse/stats/{identifier}")
    @Operation(
        summary = "Get abuse statistics for an identifier",
        description = """
            Retrieves abuse detection statistics for a specific identifier (IP, userID, etc).
            Allows querying different types of abuse such as rate limiting, failed login attempts, spam, etc.
            
            **Available abuse types:**
            - `USER_RATE_LIMIT`: Rate limit violations by user
            - `IP_RATE_LIMIT`: Rate limit violations by IP
            - `FAILED_LOGIN_ATTEMPTS`: Failed login attempts
            - `SPAM_DETECTION`: Spam behavior detection
            - `SUSPICIOUS_BEHAVIOR`: General suspicious behavior
            
            **Requires ADMIN role**
            """,
        operationId = "getAbuseStats"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200", 
            description = "Abuse statistics retrieved successfully",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = AbuseStats.class),
                examples = {
                    @ExampleObject(
                        name = "IP with violations",
                        summary = "IP address that has violated rate limits multiple times",
                        value = """
                        {
                            "identifier": "192.168.1.100",
                            "abuseType": "IP_RATE_LIMIT",
                            "violationCount": 5,
                            "firstViolation": "2024-01-01T09:30:00Z",
                            "lastViolation": "2024-01-01T10:45:00Z",
                            "isCurrentlyBlocked": true,
                            "blockExpiresAt": "2024-01-01T11:45:00Z",
                            "severity": "HIGH"
                        }
                        """
                    ),
                    @ExampleObject(
                        name = "Clean user",
                        summary = "User with no violations detected",
                        value = """
                        {
                            "identifier": "user123",
                            "abuseType": "USER_RATE_LIMIT",
                            "violationCount": 0,
                            "firstViolation": null,
                            "lastViolation": null,
                            "isCurrentlyBlocked": false,
                            "blockExpiresAt": null,
                            "severity": "NONE"
                        }
                        """
                    ),
                    @ExampleObject(
                        name = "Failed logins",
                        summary = "User with multiple failed login attempts",
                        value = """
                        {
                            "identifier": "malicious_user@example.com",
                            "abuseType": "FAILED_LOGIN_ATTEMPTS",
                            "violationCount": 8,
                            "firstViolation": "2024-01-01T08:00:00Z",
                            "lastViolation": "2024-01-01T10:30:00Z",
                            "isCurrentlyBlocked": true,
                            "blockExpiresAt": "2024-01-01T12:00:00Z",
                            "severity": "CRITICAL"
                        }
                        """
                    )
                }
            )
        ),
        @ApiResponse(
            responseCode = "400", 
            description = "Invalid abuse type",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    value = """
                    {
                        "message": "Invalid abuse type",
                        "detail": "Abuse type 'INVALID_TYPE' is not supported. Valid types: USER_RATE_LIMIT, IP_RATE_LIMIT, FAILED_LOGIN_ATTEMPTS, SPAM_DETECTION, SUSPICIOUS_BEHAVIOR",
                        "path": "/admin/security/abuse/stats/{identifier}",
                        "timestamp": "2024-01-01T10:00:00Z"
                    }
                    """
                )
            )
        ),
        @ApiResponse(
            responseCode = "403", 
            description = "Access denied - ADMIN role required"
        )
    })
    public ResponseEntity<AbuseStats> getAbuseStats(
            @PathVariable String identifier,
            @RequestParam(defaultValue = "USER_RATE_LIMIT") String abuseType) {
        
        var stats = abuseDetectionService.getAbuseStats(identifier, abuseType);
        return ResponseEntity.ok(stats);
    }
}

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
    @Operation(summary = "Get user rate limit stats")
    public ResponseEntity<RateLimitStats> getUserRateLimitStats(
            @PathVariable UUID userId) {
        
        var stats = rateLimitService.getUserRateLimitStats(userId, "translations");
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/abuse/stats/{identifier}")
    @Operation(summary = "Get abuse stats for identifier")
    public ResponseEntity<AbuseStats> getAbuseStats(
            @PathVariable String identifier,
            @RequestParam(defaultValue = "USER_RATE_LIMIT") String abuseType) {
        
        var stats = abuseDetectionService.getAbuseStats(identifier, abuseType);
        return ResponseEntity.ok(stats);
    }
}

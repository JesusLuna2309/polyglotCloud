package com.jesusLuna.polyglotCloud.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jesusLuna.polyglotCloud.models.enums.RateLimitType;
import com.jesusLuna.polyglotCloud.service.AbuseDetectionService;
import com.jesusLuna.polyglotCloud.service.RateLimitService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/rate-limits")
@Tag(name = "Admin - Rate Limiting", description = "Administrative endpoints for rate limit management")
@SecurityRequirement(name = "Bearer Authentication")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRateLimitController {

    private final RateLimitService rateLimitService;
    private final AbuseDetectionService abuseDetectionService;

    @GetMapping("/status/{userId}")
    @Operation(summary = "Get rate limit status for user")
    public ResponseEntity<Map<String, Object>> getRateLimitStatus(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "127.0.0.1") String ipAddress) {
        
        var translationLimit = rateLimitService.getRateLimitInfo(
            RateLimitType.TRANSLATION_REQUEST_USER, userId, ipAddress);
        var dailyLimit = rateLimitService.getRateLimitInfo(
            RateLimitType.TRANSLATION_REQUEST_USER_DAILY, userId, ipAddress);
        
        int violations = abuseDetectionService.getViolationCount(userId.toString());
        boolean isAbusive = abuseDetectionService.isMarkedAsAbusive(userId.toString());
        
        Map<String, Object> status = Map.of(
            "userId", userId,
            "ipAddress", ipAddress,
            "translationRateLimit", translationLimit,
            "dailyRateLimit", dailyLimit,
            "violations", violations,
            "isMarkedAsAbusive", isAbusive
        );
        
        return ResponseEntity.ok(status);
    }

    @GetMapping("/ip-status/{ipAddress}")
    @Operation(summary = "Get rate limit status for IP address")
    public ResponseEntity<Map<String, Object>> getIpRateLimitStatus(@PathVariable String ipAddress) {
        
        var ipLimit = rateLimitService.getRateLimitInfo(
            RateLimitType.TRANSLATION_REQUEST_IP, null, ipAddress);
        var dailyIpLimit = rateLimitService.getRateLimitInfo(
            RateLimitType.TRANSLATION_REQUEST_IP_DAILY, null, ipAddress);
        
        int violations = abuseDetectionService.getViolationCount(ipAddress);
        boolean isBlocked = abuseDetectionService.isIpBlocked(ipAddress);
        
        Map<String, Object> status = Map.of(
            "ipAddress", ipAddress,
            "ipRateLimit", ipLimit,
            "dailyIpLimit", dailyIpLimit,
            "violations", violations,
            "isBlocked", isBlocked
        );
        
        return ResponseEntity.ok(status);
    }

    @DeleteMapping("/violations/{identifier}")
    @Operation(summary = "Clear violations for user or IP")
    public ResponseEntity<Void> clearViolations(@PathVariable String identifier) {
        abuseDetectionService.clearViolations(identifier);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/block-ip/{ipAddress}")
    @Operation(summary = "Temporarily block an IP address")
    public ResponseEntity<Void> blockIp(
            @PathVariable String ipAddress,
            @RequestParam(defaultValue = "3600") long durationSeconds) {
        
        abuseDetectionService.markIpAsSuspicious(ipAddress, 
            java.time.Duration.ofSeconds(durationSeconds));
        
        return ResponseEntity.noContent().build();
    }
}
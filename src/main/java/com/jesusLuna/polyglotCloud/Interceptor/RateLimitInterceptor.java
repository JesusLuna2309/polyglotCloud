package com.jesusLuna.polyglotCloud.Interceptor;

import java.util.UUID;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.jesusLuna.polyglotCloud.exception.RateLimitExceededException;
import com.jesusLuna.polyglotCloud.models.CustomUserPrincipal;
import com.jesusLuna.polyglotCloud.service.AbuseDetectionService;
import com.jesusLuna.polyglotCloud.service.RateLimitService;
import com.jesusLuna.polyglotCloud.util.HttpRequestUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitService rateLimitService;
    private final AbuseDetectionService abuseDetectionService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) 
            throws Exception {
        
        String requestPath = request.getRequestURI();
        String method = request.getMethod();
        String ipAddress = HttpRequestUtils.getClientIpAddress(request);
        
        // Solo aplicar rate limiting a endpoints específicos
        if (shouldApplyRateLimit(requestPath, method)) {
            
            // Verificar si la IP está bloqueada por abuso
            if (abuseDetectionService.isIpBlocked(ipAddress)) {
                log.warn("Request blocked from suspicious IP: {} for path: {}", ipAddress, requestPath);
                response.setStatus(429); // Too Many Requests
                response.setHeader("X-RateLimit-Blocked", "IP temporarily blocked due to suspicious activity");
                return false;
            }
            
            // Obtener usuario actual si está autenticado
            UUID userId = getCurrentUserId();
            
            try {
                // Aplicar rate limiting específico según el endpoint
                if (requestPath.contains("/translations") && "POST".equals(method)) {
                    rateLimitService.checkMultipleRateLimits(userId, ipAddress);
                } else if (requestPath.contains("/votes") && "POST".equals(method)) {
                    rateLimitService.checkVoteRateLimit(userId, ipAddress);
                }
                
                // Agregar headers informativos de rate limit
                addRateLimitHeaders(response, userId, ipAddress);
                
            } catch (RateLimitExceededException e) {
                log.warn("Rate limit exceeded for request: {} {} from user: {} IP: {}", 
                        method, requestPath, userId, ipAddress);
                
                response.setStatus(429); // Too Many Requests
                response.setHeader("X-RateLimit-Limit", String.valueOf(e.getLimitType().getMaxRequests()));
                response.setHeader("X-RateLimit-Retry-After", String.valueOf(e.getRetryAfter().getSeconds()));
                response.setHeader("X-RateLimit-Type", e.getLimitType().getDescription());
                
                return false;
            }
        }
        
        return true;
    }

    private boolean shouldApplyRateLimit(String path, String method) {
        // Aplicar rate limiting a endpoints críticos
        return (path.contains("/translations") && "POST".equals(method)) ||
               (path.contains("/votes") && "POST".equals(method)) ||
               (path.contains("/versions") && "POST".equals(method));
    }

    private UUID getCurrentUserId() {
        try {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() && 
                authentication.getPrincipal() instanceof CustomUserPrincipal principal) {
                return principal.getUser().getId();
            }
        } catch (Exception e) {
            log.debug("Could not extract user ID from security context: {}", e.getMessage());
        }
        return null;
    }

    private void addRateLimitHeaders(HttpServletResponse response, UUID userId, String ipAddress) {
        try {
            var limitInfo = rateLimitService.getRateLimitInfo(
                userId != null ? 
                    com.jesusLuna.polyglotCloud.models.enums.RateLimitType.TRANSLATION_REQUEST_USER :
                    com.jesusLuna.polyglotCloud.models.enums.RateLimitType.TRANSLATION_REQUEST_IP,
                userId, 
                ipAddress
            );
            
            response.setHeader("X-RateLimit-Limit", String.valueOf(limitInfo.maxRequests()));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(limitInfo.remainingRequests()));
            response.setHeader("X-RateLimit-Reset", String.valueOf(limitInfo.resetTime().getSeconds()));
            
        } catch (Exception e) {
            log.debug("Could not add rate limit headers: {}", e.getMessage());
        }
    }
}
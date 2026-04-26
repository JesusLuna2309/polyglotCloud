package com.jesusLuna.polyglotCloud.security;

import java.io.IOException;
import java.util.UUID;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.jesusLuna.polyglotCloud.models.CustomUserPrincipal;
import com.jesusLuna.polyglotCloud.service.AbuseDetectionService;
import com.jesusLuna.polyglotCloud.service.RateLimitService;
import com.jesusLuna.polyglotCloud.util.HttpRequestUtils;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class GlobalRateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final AbuseDetectionService abuseDetectionService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        
        String requestUri = request.getRequestURI();
        String method = request.getMethod();
        String ipAddress = HttpRequestUtils.getClientIpAddress(request);
        
        // Excluir endpoints estáticos y de health check
        if (shouldSkipRateLimit(requestUri, method)) {
            filterChain.doFilter(request, response);
            return;
        }
        
        try {
            // 🔐 RATE LIMITING PARA AUTH
            if (requestUri.startsWith("/auth")) {
                rateLimitService.checkAuthRateLimit(ipAddress);
            }
            
            // 🔄 RATE LIMITING PARA TRANSLATIONS
            else if (requestUri.startsWith("/translations")) {
                // Verificar bloqueo por IP primero
                if (abuseDetectionService.isIpBlocked(ipAddress)) {
                    sendRateLimitResponse(response, "IP temporarily blocked due to abuse");
                    return;
                }
                
                var authentication = SecurityContextHolder.getContext().getAuthentication();
                if (authentication != null && authentication.getPrincipal() instanceof CustomUserPrincipal) {
                    CustomUserPrincipal userPrincipal = (CustomUserPrincipal) authentication.getPrincipal();
                    UUID userId = userPrincipal.getUser().getId();
                    
                    // Verificar si el usuario está bloqueado
                    rateLimitService.checkUserBlocked(userId);
                    
                    // Rate limiting específico para traducciones
                    rateLimitService.checkTranslationRateLimit(userId);
                } else {
                    // Usuario no autenticado intentando traducir
                    rateLimitService.checkIpRateLimit(ipAddress, "translations");
                }
            }
            
            // 🌐 RATE LIMITING GENERAL para otros endpoints
            else {
                var authentication = SecurityContextHolder.getContext().getAuthentication();
                if (authentication != null && authentication.getPrincipal() instanceof CustomUserPrincipal) {
                    CustomUserPrincipal userPrincipal = (CustomUserPrincipal) authentication.getPrincipal();
                    UUID userId = userPrincipal.getUser().getId();
                    
                    // Rate limiting general para usuarios autenticados
                    rateLimitService.checkGeneralUserRateLimit(userId);
                } else {
                    // Rate limiting para usuarios anónimos
                    rateLimitService.checkIpRateLimit(ipAddress, "general");
                }
            }
            
        } catch (Exception e) {
            log.warn("Rate limit check failed for {} {} from {}: {}", 
                    method, requestUri, ipAddress, e.getMessage());
            sendRateLimitResponse(response, e.getMessage());
            return;
        }
        
        filterChain.doFilter(request, response);
    }

    /**
     * Endpoints que no necesitan rate limiting
     */
    private boolean shouldSkipRateLimit(String uri, String method) {
        return uri.startsWith("/actuator") ||
                uri.startsWith("/swagger-ui") ||
                uri.startsWith("/v3/api-docs") ||
                uri.startsWith("/webjars") ||
                uri.equals("/") ||
                (method.equals("OPTIONS"));
    }

    /**
     * Envía respuesta de rate limit alcanzado
     */
    private void sendRateLimitResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(429);
        response.setHeader("Content-Type", "application/json");
        response.setHeader("Retry-After", "60");
        response.getWriter().write(String.format(
            "{\"error\":\"%s\",\"status\":429,\"timestamp\":\"%s\"}", 
            message, 
            java.time.Instant.now()
        ));
    }
}

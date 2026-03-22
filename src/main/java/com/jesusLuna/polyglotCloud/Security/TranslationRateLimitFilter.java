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
public class TranslationRateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final AbuseDetectionService abuseDetectionService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        
        String requestUri = request.getRequestURI();
        
        // Solo aplicar rate limiting a endpoints de traducción
        if (requestUri.startsWith("/translations")) {
            String ipAddress = HttpRequestUtils.getClientIpAddress(request);
            
            try {
                // Verificar bloqueo por IP primero
                if (abuseDetectionService.isIpBlocked(ipAddress)) {
                    response.setStatus(429);
                    response.getWriter().write("{\"error\":\"IP temporarily blocked due to abuse\"}");
                    return;
                }
                
                // Rate limiting por IP (siempre)
                rateLimitService.checkIpRateLimit(ipAddress, "translations");
                
                // Rate limiting por usuario (si está autenticado)
                var authentication = SecurityContextHolder.getContext().getAuthentication();
                if (authentication != null && authentication.getPrincipal() instanceof CustomUserPrincipal) {
                    CustomUserPrincipal userPrincipal = (CustomUserPrincipal) authentication.getPrincipal();
                    UUID userId = userPrincipal.getUser().getId();
                    
                    // Verificar si el usuario está bloqueado
                    rateLimitService.checkUserBlocked(userId);
                    
                    // Rate limiting por usuario
                    rateLimitService.checkUserRateLimit(userId, "translations");
                }
                
            } catch (Exception e) {
                log.warn("Rate limit or abuse check failed: {}", e.getMessage());
                response.setStatus(429);
                response.setHeader("Content-Type", "application/json");
                response.getWriter().write("{\"error\":\"" + e.getMessage() + "\"}");
                return;
            }
        }
        
        filterChain.doFilter(request, response);
    }
}

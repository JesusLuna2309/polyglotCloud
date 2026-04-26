package com.jesusLuna.polyglotCloud.controller;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jesusLuna.polyglotCloud.DTO.UserDTO;
import com.jesusLuna.polyglotCloud.service.AuthService;
import com.jesusLuna.polyglotCloud.util.HttpRequestUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
@Tag(
    name = "Authentication",
    description = "Endpoints for user authentication and registration"
)
public class AuthController {


    private final AuthService authService;

// --- INYECCIÓN DE PROPIEDADES (DINÁMICO) ---
    
    @Value("${app.jwt.cookie.name}")
    private String cookieName;

    //TODO: Configurar en el JWT la expiracion de la cookie
    @Value("${app.jwt.cookie.expiration-s}")
    private long cookieExpirationSeconds;

    @Value("${app.jwt.cookie.secure}")
    private boolean cookieSecure;

    @Value("${app.jwt.cookie.same-site}")
    private String cookieSameSite;

    // -------------------------------------------;

    @PostMapping("/register")
    @Operation(summary = "Register new user", description = "Creates a new user account")
    public ResponseEntity<UserDTO.UserResponse> register(
            @Valid @RequestBody UserDTO.UserRegistrationRequest request) {
        
        log.info("Registration request received for username: {}", request.username());
        UserDTO.UserResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    @Operation(summary = "Login user", description = "Authenticates user, returns JWT in body and Refresh Token in HttpOnly Cookie")
    public ResponseEntity<UserDTO.UserLoginResponse> login(
            @Valid @RequestBody UserDTO.UserLoginRequest request,
            HttpServletRequest httpRequest) {
        
        log.info("Login request received for: {}", request.login());
        
        String ipAddress = HttpRequestUtils.getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        
        // Llamamos al servicio que nos devuelve el Wrapper (Body + Cookie Data)
        UserDTO.AuthResponseWithCookies result = authService.login(request, ipAddress, userAgent);
        
        // Creamos la Cookie HttpOnly
        ResponseCookie cookie = createRefreshTokenCookie(result.refreshToken(), 7 * 24 * 60 * 60); // 7 días

        // Devolvemos: Access Token en JSON + Refresh Token en Header Set-Cookie
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(result.body());
    }

    //TODO: Implementar la logica de la verificacion de email
    @GetMapping("/verify-email")
    @Operation(summary = "Verify email", description = "Verifies user email with verification token")
    public ResponseEntity<UserDTO.UserResponse> verifyEmail(
            @RequestParam String token) {
        
        log.info("Email verification request received");
        
        UserDTO.UserResponse response = authService.verifyEmail(token);
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh Token", description = "Uses the HttpOnly Cookie to get a new Access Token")
    public ResponseEntity<UserDTO.UserLoginResponse> refreshToken(
            @CookieValue(name = "${app.jwt.cookie.name}") String refreshToken, // Lee la cookie automáticamente
            HttpServletRequest httpRequest
    ) {
        String ipAddress = HttpRequestUtils.getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        log.info("Refresh token request received");

        // Llamamos al servicio para rotar tokens
        UserDTO.AuthResponseWithCookies result = authService.refreshTokens(refreshToken, ipAddress, userAgent);

        // Actualizamos la Cookie (rotación)
        ResponseCookie newCookie = createRefreshTokenCookie(result.refreshToken(), 7 * 24 * 60 * 60);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, newCookie.toString())
                .body(result.body());
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout user", description = "Logs out user by revoking refresh token and clearing cookie")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "${app.jwt.cookie.name}", required = false) String refreshToken
    ) {
        log.info("Logout request received");
        
        if (refreshToken != null) {
            authService.logout(refreshToken);
        }
        
        // Creamos una cookie vacía que expira ya (0 segundos) para borrarla del navegador
        ResponseCookie cleanCookie = createRefreshTokenCookie("", 0);
        
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cleanCookie.toString())
                .build();
    }

    // --- Helper Privado para crear la Cookie ---
    private ResponseCookie createRefreshTokenCookie(String token, long maxAge) {
        return ResponseCookie.from(cookieName, token) // Usa el nombre de application.properties
                .httpOnly(true)
                .secure(cookieSecure)  // Usa true/false según application.properties
                .path("/auth")
                .maxAge(maxAge)
                .sameSite(cookieSameSite) // Usa Strict/Lax según properties
                .build();
    }

}
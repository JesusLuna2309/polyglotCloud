package com.jesusLuna.polyglotCloud.Exception;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import com.jesusLuna.polyglotCloud.DTO.UserDTO;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Maneja errores de validación (@Valid)
     * Ahora Swagger mostrará los errores de validación específicos
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(
            MethodArgumentNotValidException ex, WebRequest request) {
        
        Map<String, Object> response = new HashMap<>();
        Map<String, String> errors = new HashMap<>();
        
        // Extraer todos los errores de validación
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        response.put("timestamp", Instant.now());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "Validation Failed");
        response.put("message", "Los datos enviados no son válidos");
        response.put("path", request.getDescription(false).replace("uri=", ""));
        response.put("validationErrors", errors);
        
        log.warn("Validation errors: {}", errors);
        
        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Maneja excepciones de login fallido con información de intentos restantes
     */
    @ExceptionHandler(LoginFailedException.class)
    public ResponseEntity<UserDTO.LoginErrorResponse> handleLoginFailedException(
            LoginFailedException ex, WebRequest request) {
        
        // Format message with minutes remaining if account is locked
        String message = ex.getMessage();
        if (ex.isAccountLocked() && ex.getLockedUntil() != null) {
            long minutesRemaining = java.time.Duration.between(Instant.now(), ex.getLockedUntil()).toMinutes();
            if (minutesRemaining > 0) {
                message = String.format("Account temporarily blocked. Try again in %d minute(s).", minutesRemaining);
            } else {
                message = "Account is locked. Please try again later.";
            }
        }
        
        UserDTO.LoginErrorResponse error = new UserDTO.LoginErrorResponse(
            message,
            "Login failed",
            request.getDescription(false).replace("uri=", ""),
            Instant.now(),
            ex.getRemainingAttempts(),
            ex.getLockedUntil(),
            ex.isAccountLocked(),
            ex.isAccountDisabled()
        );
        
        log.warn("Login failed: {} - Remaining attempts: {} - Account locked: {}", 
                message, ex.getRemainingAttempts(), ex.isAccountLocked());
        
        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Maneja excepciones de reglas de negocio
     */
    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<UserDTO.ErrorResponse> handleBusinessRuleException(
            BusinessRuleException ex, WebRequest request) {
        
        UserDTO.ErrorResponse error = new UserDTO.ErrorResponse(
            ex.getMessage(),
            ex.getErrorCode() != null ? "Error code: " + ex.getErrorCode() : "Business rule violation",
            request.getDescription(false).replace("uri=", ""),
            Instant.now()
        );
        
        log.warn("Business rule exception: {}", ex.getMessage());
        
        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Maneja excepciones de recursos no encontrados
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<UserDTO.ErrorResponse> handleResourceNotFoundException(
            ResourceNotFoundException ex, WebRequest request) {
        
        UserDTO.ErrorResponse error = new UserDTO.ErrorResponse(
            ex.getMessage(),
            "The requested resource was not found",
            request.getDescription(false).replace("uri=", ""),
            Instant.now()
        );
        
        log.warn("Resource not found: {}", ex.getMessage());
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Maneja excepciones de acceso prohibido
     */
    @ExceptionHandler(ForbiddenAccessException.class)
    public ResponseEntity<UserDTO.ErrorResponse> handleForbiddenAccessException(
            ForbiddenAccessException ex, WebRequest request) {
        
        UserDTO.ErrorResponse error = new UserDTO.ErrorResponse(
            ex.getMessage(),
            "Access denied",
            request.getDescription(false).replace("uri=", ""),
            Instant.now()
        );
        
        log.warn("Forbidden access: {}", ex.getMessage());
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    /**
     * Maneja excepciones de acceso denegado de Spring Security (@PreAuthorize)
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<UserDTO.ErrorResponse> handleAccessDeniedException(
            AccessDeniedException ex, WebRequest request) {
        
        UserDTO.ErrorResponse error = new UserDTO.ErrorResponse(
            "Access denied. You do not have permission to perform this action.",
            "Access denied",
            request.getDescription(false).replace("uri=", ""),
            Instant.now()
        );
        
        log.warn("Access denied: {}", ex.getMessage());
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    /**
     * Maneja todas las demás excepciones
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<UserDTO.ErrorResponse> handleGlobalException(
            Exception ex, WebRequest request) {
        
        UserDTO.ErrorResponse error = new UserDTO.ErrorResponse(
            "Internal server error",
            ex.getMessage(),
            request.getDescription(false).replace("uri=", ""),
            Instant.now()
        );
        
        log.error("Unexpected error: ", ex);
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
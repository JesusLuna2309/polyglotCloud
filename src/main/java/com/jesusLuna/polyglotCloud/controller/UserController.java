package com.jesusLuna.polyglotCloud.controller;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jesusLuna.polyglotCloud.dto.UserDTO;
import com.jesusLuna.polyglotCloud.exception.ForbiddenAccessException;
import com.jesusLuna.polyglotCloud.exception.ResourceNotFoundException;
import com.jesusLuna.polyglotCloud.mapper.UserMapper;
import com.jesusLuna.polyglotCloud.models.User;
import com.jesusLuna.polyglotCloud.models.enums.Role;
import com.jesusLuna.polyglotCloud.repository.UserRespository;
import com.jesusLuna.polyglotCloud.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
@Tag(
    name = "User Management",
    description = "Endpoints for user management"
)
public class UserController {

    private final UserService userService;

    private final UserRespository userRepository;

    private final UserMapper userMapper;

    @GetMapping
    @Operation(summary = "List users", description = "List and filter users (Admin only)")
    public ResponseEntity<Page<UserDTO.UserAdminResponse>> listUsers(
            @Parameter(description = "Search query for username, email, first name, or last name")
            @RequestParam(required = false) String query,
            @Parameter(description = "Filter by role")
            @RequestParam(required = false) Role role,
            @Parameter(description = "Filter by active status")
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal UserDetails userDetails) { // Cambiado a UserDetails

        Page<User> users;

        // Lógica de búsqueda/filtrado (mantén tu lógica actual, está bien)
        if (query == null && role != null && active == null) {
            users = userService.listUsersByRole(role, pageable);
        } else if (query != null || role != null || active != null) {
            UserDTO.UserSearchFilters filters = new UserDTO.UserSearchFilters(query, role, active, null);
            users = userService.searchUsers(filters, pageable);
        } else {
            users = userService.listAllUsers(pageable);
        }

        // Usa el mapper inyectado en lugar del método estático
        Page<UserDTO. UserAdminResponse> response = users.map(userMapper::toAdminResponse);
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user details", description = "Get details of a specific user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User found",
                    content = @Content(schema = @Schema(oneOf = {
                            UserDTO.UserAdminResponse.class,
                            UserDTO.UserProfileResponse.class,
                            UserDTO.UserPublicResponse.class
                    })))
    })
    public ResponseEntity<Object> getUser(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {

        // 1. Obtenemos el usuario solicitado
        User user = userService. getUserById(id);

        // 2. Si NO hay sesión activa -> Respuesta pública
        if (userDetails == null) {
            return ResponseEntity.ok(userMapper.toPublicResponse(user));
        }

        // 3. Buscamos al usuario que hace la petición (el logueado)
        String loginIdentifier = userDetails.getUsername();
        User requester = userRepository.findByUsernameOrEmailAndDeletedAtIsNull(loginIdentifier, loginIdentifier)
                .orElseThrow(() -> new ResourceNotFoundException("Requester user not found"));

        // 4.  Lógica de permisos según rol y ownership
        // A) Si es Admin -> Ve toda la info (Admin Response)
        if (requester.getRole() == Role.ADMIN) {
            return ResponseEntity.ok(userMapper. toAdminResponse(user));
        }
        
        // B) Si es su propio perfil -> Ve su perfil completo (Profile Response)
        if (id.equals(requester.getId())) {
            return ResponseEntity. ok(userMapper.toProfileResponse(user));
        }
        
        // C) Si es otro usuario normal viendo a alguien más -> Solo info pública
        return ResponseEntity.ok(userMapper.toPublicResponse(user));
    }
    @PutMapping("/{id}")
    @Operation(summary = "Update user profile", description = "Update user profile information")
    public ResponseEntity<Object> updateUserProfile(
            @PathVariable UUID id,
            @Valid @RequestBody UserDTO.UserUpdateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        // 1. El usuario DEBE estar autenticado para actualizar
        if (userDetails == null) {
            throw new ForbiddenAccessException("Authentication required to update profile");
        }

        // 2. Buscamos al usuario que hace la petición (requester)
        String loginIdentifier = userDetails.getUsername();
        User requester = userRepository.findByUsernameOrEmailAndDeletedAtIsNull(loginIdentifier, loginIdentifier)
                .orElseThrow(() -> new ResourceNotFoundException("Requester user not found"));

        // 3. Verificamos permisos:  Solo puede actualizar si es Admin O es su propio perfil
        if (requester.getRole() != Role.ADMIN && ! id.equals(requester.getId())) {
            throw new ForbiddenAccessException("You can only update your own profile");
        }

        // 4. Ejecutamos la actualización
        User updated = userService.updateUserProfile(id, request, requester.getId());

        // 5. Devolvemos la respuesta adecuada según quién hizo la actualización
        if (requester.getRole() == Role.ADMIN) {
            // Admin ve respuesta completa
            return ResponseEntity.ok(userMapper.toAdminResponse(updated));
        } else {
            // Usuario normal ve su perfil actualizado
            return ResponseEntity.ok(userMapper.toProfileResponse(updated));
        }
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update user status", description = "Activate or deactivate user account (Admin only)")
    public ResponseEntity<UserDTO.UserAdminResponse> updateUserStatus(
            @PathVariable UUID id,
            @Parameter(description = "Active status (true to activate, false to deactivate)")
            @RequestParam boolean active,
            @AuthenticationPrincipal UserDetails userDetails) {

        // 1. Validación de autenticación
        if (userDetails == null) {
            throw new ForbiddenAccessException("Authentication required");
        }

        // 2. Buscamos al admin que hace la petición
        String loginIdentifier = userDetails.getUsername();
        User requester = userRepository.findByUsernameOrEmailAndDeletedAtIsNull(loginIdentifier, loginIdentifier)
                .orElseThrow(() -> new ResourceNotFoundException("Requester user not found"));

        // 3. Actualizamos el estado del usuario (auditoría:  quién hizo el cambio)
        User updated = userService. updateUserStatus(id, active, requester.getId());

        // 4. Devolvemos la respuesta completa de admin
        return ResponseEntity.ok(userMapper.toAdminResponse(updated));
    }

    @PutMapping("/{id}/role")
    @Operation(summary = "Update user role", description = "Update user role (Admin only)")
    public ResponseEntity<UserDTO.UserAdminResponse> updateUserRole(
            @PathVariable UUID id,
            @Valid @RequestBody UserDTO.UserRoleUpdateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        // 1. Validación de autenticación
        if (userDetails == null) {
            throw new ForbiddenAccessException("Authentication required");
        }

        // 2. Buscamos al admin que hace la petición
        String loginIdentifier = userDetails.getUsername();
        User requester = userRepository.findByUsernameOrEmailAndDeletedAtIsNull(loginIdentifier, loginIdentifier)
                .orElseThrow(() -> new ResourceNotFoundException("Requester user not found"));

        // 3. Actualizamos el rol del usuario (auditoría:  quién hizo el cambio)
        User updated = userService.updateUserRole(id, request.role(), requester.getId());

        // 4. Devolvemos la respuesta completa de admin
        return ResponseEntity.ok(userMapper.toAdminResponse(updated));
    }

    @PutMapping("/{id}/password")
    @Operation(summary = "Change password", description = "Change user password")
    public ResponseEntity<Void> changePassword(
            @PathVariable UUID id,
            @Valid @RequestBody UserDTO.UserPasswordChangeRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        // 1. Validación de autenticación (obligatorio para cambiar contraseña)
        if (userDetails == null) {
            throw new ForbiddenAccessException("Authentication required to change password");
        }

        // 2. Buscamos al usuario que hace la petición
        String loginIdentifier = userDetails.getUsername();
        User requester = userRepository.findByUsernameOrEmailAndDeletedAtIsNull(loginIdentifier, loginIdentifier)
                .orElseThrow(() -> new ResourceNotFoundException("Requester user not found"));

        // 3. Validación de permisos:  Solo puede cambiar su propia contraseña (o ser Admin)
        if (requester.getRole() != Role. ADMIN && ! id.equals(requester.getId())) {
            throw new ForbiddenAccessException("You can only change your own password");
        }

        // 4. Ejecutamos el cambio de contraseña
        userService.changePassword(id, request, requester.getId());

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete user", description = "Soft delete user account (Admin only)")
    public ResponseEntity<Void> deleteUser(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {

        // 1. Validación de autenticación (por si acaso, aunque @PreAuthorize ya lo cubre)
        if (userDetails == null) {
            throw new ForbiddenAccessException("Authentication required");
        }

        // 2. Buscamos al usuario que hace la petición (el admin)
        String loginIdentifier = userDetails.getUsername();
        User requester = userRepository.findByUsernameOrEmailAndDeletedAtIsNull(loginIdentifier, loginIdentifier)
                .orElseThrow(() -> new ResourceNotFoundException("Requester user not found"));

        // 3. Ejecutamos el soft delete pasando el ID del admin (para auditoría)
        userService.deleteUser(id, requester.getId());

        return ResponseEntity.noContent().build();
    }


}

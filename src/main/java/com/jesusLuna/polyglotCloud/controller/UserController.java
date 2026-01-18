package com.jesusLuna.polyglotCloud.controller;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jesusLuna.polyglotCloud.dto.UserDTO;
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
        User requester = userRepository.findByUsernameOrEmail(loginIdentifier, loginIdentifier)
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
}

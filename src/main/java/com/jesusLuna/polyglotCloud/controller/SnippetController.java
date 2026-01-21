package com.jesusLuna.polyglotCloud.controller;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jesusLuna.polyglotCloud.dto.SnippetDTO;
import com.jesusLuna.polyglotCloud.exception.ForbiddenAccessException;
import com.jesusLuna.polyglotCloud.mapper.SnippetMapper;
import com.jesusLuna.polyglotCloud.models.Snippet;
import com.jesusLuna.polyglotCloud.models.User;
import com.jesusLuna.polyglotCloud.models.enums.Role;
import com.jesusLuna.polyglotCloud.models.enums.SnippetStatus;
import com.jesusLuna.polyglotCloud.repository.UserRespository;
import com.jesusLuna.polyglotCloud.service.SnippetService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/snippets")
@Tag(
    name = "Snippets",
    description = "Endpoints para gestión de snippets de código"
)
public class SnippetController {

    private final SnippetService snippetService;
    private final SnippetMapper snippetMapper;
    private final UserRespository userRepository;

    @GetMapping
    @Operation(summary = "Listar snippets", description = "Obtiene una lista paginada. Si no hay filtros, devuelve los del usuario actual.")
    public ResponseEntity<Page<SnippetDTO.SnippetSummaryResponse>> listSnippets(
            // Filtros opcionales
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) SnippetStatus status,
            
            // Paginación
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            
            // Inyectamos al usuario autenticado (¡Adiós SecurityContextHolder manual!)
            @AuthenticationPrincipal User userPrincipal
    ) {

        Page<Snippet> snippets;

       // 1. Si piden snippets de un usuario específico
        if (userId != null) {
            
            // A) Soy YO mismo O soy ADMIN -> Veo TODO (Borradores, privados, etc)
            if (userId.equals(userPrincipal.getId()) || userPrincipal.getRole() == Role.ADMIN) {
                snippets = snippetService.listSnippetsByUser(userId, pageable);
            }
            
            // B) Es OTRO usuario -> Solo veo sus PUBLICOS (Modo "Ver Perfil")
            else {
                 // ¡Necesitas este método en tu servicio!
                snippets = snippetService.listSnippetsByUserAndStatus(SnippetStatus.PUBLISHED, userId, pageable);
            }
            
        }
        // 2. Si piden filtrar por estado (ej: solo PUBLISHED)
        else if (status != null) {
             // Aquí asumo que quieres ver TUS snippets con ese estado
            snippets = snippetService.listSnippetsByUserAndStatus(status,userPrincipal.getId(), pageable);
        }
        // 3. Por defecto: Listar MIS snippets (todos los estados)
        else {
            snippets = snippetService.listSnippetsByUser(userPrincipal.getId(), pageable);
        }

        // Convertir entidad a DTO
        Page<SnippetDTO.SnippetSummaryResponse> response = snippets.map(snippetMapper::toSummaryResponse);
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/public")
    @Operation(summary = "Listar snippets públicos", description = "Muro público: obtiene snippets paginados con estado PUBLISHED")
    public ResponseEntity<Page<SnippetDTO.SnippetPublicResponse>> listPublicSnippets(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        
        // REUTILIZACIÓN: Usamos el método genérico filtrando por estado PUBLISHED
        // (Asumiendo que tienes este método en tu servicio, si no, créalo o usa el específico que tenías)
        Page<Snippet> snippets = snippetService.listSnippetsByStatus(SnippetStatus.PUBLISHED, pageable);
        
        // MAPPER: Usamos la interfaz de MapStruct inyectada
        Page<SnippetDTO.SnippetPublicResponse> response = snippets.map(snippetMapper::toPublicResponse);
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/search")
    @Operation(summary = "Search snippets", description = "Full-text search in snippets")
    public ResponseEntity<Page<SnippetDTO.SnippetPublicResponse>> searchSnippets(
            @RequestParam(required = false) String query, // Hazlo opcional por si quieren filtrar solo por lenguaje
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID languageId,
            @RequestParam(required = false) SnippetStatus status,
            @RequestParam(required = false) Boolean isPublic, // ¡Ojo! Añadiste este filtro al Repository, añádelo aquí también
            @PageableDefault(size = 20) Pageable pageable) {

        // Llamamos al servicio (que llamará a tu repositorio super optimizado)
        Page<Snippet> snippets = snippetService.searchSnippets(query, userId, languageId, status, isPublic, pageable);
        
        // Usamos el Mapper oficial
        Page<SnippetDTO.SnippetPublicResponse> response = snippets.map(snippetMapper::toPublicResponse);
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener detalle de snippet", description = "Obtiene los datos completos")
    public ResponseEntity<SnippetDTO.SnippetDetailResponse> getSnippet(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) { // Usamos la interfaz estándar
        
        Snippet snippet = snippetService.getSnippetById(id);
        
        // Verificamos si es privado
        if (!Boolean.TRUE.equals(snippet.isPublic())) {
            
            // 1. Si no está logueado -> Error
            if (userDetails == null) {
                throw new ForbiddenAccessException("This snippet is private. Please login.");
            }

            // 2. Buscamos al usuario por Username O Email
            // Pasamos el mismo valor a los dos campos
            String loginIdentifier = userDetails.getUsername();
            
            User currentUser = userRepository.findByUsernameOrEmailAndDeletedAtIsNull(loginIdentifier, loginIdentifier)
                    .orElseThrow(() -> new ForbiddenAccessException("User credentials not found"));

            // 3. Comparamos IDs
            if (!snippet.getUser().getId().equals(currentUser.getId())) {
                throw new ForbiddenAccessException("You don't have permission to view this snippet");
            }
        }
        
        return ResponseEntity.ok(snippetMapper.toDetailResponse(snippet));
    }

    @PostMapping
    @Operation(summary = "Crear snippet", description = "Crea un nuevo snippet de código")
    public ResponseEntity<SnippetDTO.SnippetDetailResponse> createSnippet(
            @Valid @RequestBody SnippetDTO.SnippetCreateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        // 1. Obtenemos el ID del usuario logueado
        String loginIdentifier = userDetails.getUsername();
        
        User currentUser = userRepository.findByUsernameOrEmailAndDeletedAtIsNull(loginIdentifier, loginIdentifier)
                .orElseThrow(() -> new ForbiddenAccessException("User not found"));

        // 2. Creamos el snippet
        Snippet created = snippetService.createSnippet(request, currentUser.getId());
        
        // 3. Usamos el mapper inyectado
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(snippetMapper.toDetailResponse(created));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar snippet", description = "Actualiza los datos de un snippet existente")
    public ResponseEntity<SnippetDTO.SnippetDetailResponse> updateSnippet(
            @PathVariable UUID id,
            @Valid @RequestBody SnippetDTO.SnippetUpdateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        // 1. Obtenemos el ID del usuario logueado
        String loginIdentifier = userDetails.getUsername();
        
        User currentUser = userRepository.findByUsernameOrEmailAndDeletedAtIsNull(loginIdentifier, loginIdentifier)
                .orElseThrow(() -> new ForbiddenAccessException("User not found"));

        // 2. Actualizamos el snippet (el servicio se encargará de verificar permisos)
        Snippet updated = snippetService.updateSnippet(id, request, currentUser.getId());
        
        // 3. Usamos el mapper inyectado
        return ResponseEntity.ok(snippetMapper.toDetailResponse(updated));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar snippet", description = "Elimina lógicamente un snippet (soft delete)")
    public ResponseEntity<Void> deleteSnippet(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        // 1. Obtenemos el ID del usuario logueado
        String loginIdentifier = userDetails.getUsername();
        
        User currentUser = userRepository.findByUsernameOrEmailAndDeletedAtIsNull(loginIdentifier, loginIdentifier)
                .orElseThrow(() -> new ForbiddenAccessException("User not found"));

        // 2. Eliminamos el snippet (el servicio verificará permisos)
        snippetService.deleteSnippet(id, currentUser.getId());
        
        // 3. Devolvemos 204 No Content (estándar para DELETE exitoso)
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{originalId}/translate")
    @Operation(
        summary = "Traducir snippet a otro lenguaje",
        description = "Crea una nueva versión del snippet en un lenguaje diferente, manteniendo la vinculación"
    )
    public ResponseEntity<SnippetDTO.SnippetDetailResponse> translateSnippet(
            @PathVariable UUID originalId,
            @Valid @RequestBody SnippetDTO.SnippetTranslateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        // 1. Obtener usuario actual
        String loginIdentifier = userDetails.getUsername();
        User currentUser = userRepository.findByUsernameOrEmailAndDeletedAtIsNull(loginIdentifier, loginIdentifier)
                .orElseThrow(() -> new ForbiddenAccessException("User not found"));

        // 2. Crear la traducción (el servicio manejará la lógica de vinculación)
        Snippet translation = snippetService.translateSnippet(originalId, request, currentUser.getId());
        
        // 3. Devolver la nueva versión
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(snippetMapper.toDetailResponse(translation));
    }
}
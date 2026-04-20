package com.jesusLuna.polyglotCloud.controller;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jesusLuna.polyglotCloud.dto.TranslationVoteDTO;
import com.jesusLuna.polyglotCloud.exception.ForbiddenAccessException;
import com.jesusLuna.polyglotCloud.models.User;
import com.jesusLuna.polyglotCloud.repository.UserRepository;
import com.jesusLuna.polyglotCloud.service.TranslationVoteService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/translations/votes")
@Tag(name = "Translation Votes", description = "Endpoints for voting on translations and retrieving rankings")
public class TranslationVoteController {

    private final TranslationVoteService voteService;
    private final UserRepository userRepository;


    @PostMapping("/{versionId}/vote")
    @PreAuthorize("hasRole('USER')")
    @Operation(
        summary = "Votar en una versión de traducción",
        description = "Permite dar upvote (+1) o downvote (-1) a una versión de traducción"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Voto registrado exitosamente"),
        @ApiResponse(responseCode = "200", description = "Voto actualizado exitosamente"),
        @ApiResponse(responseCode = "400", description = "No se puede votar en tu propia traducción"),
        @ApiResponse(responseCode = "404", description = "Versión no encontrada"),
        @ApiResponse(responseCode = "401", description = "Autenticación requerida")
    })
    public ResponseEntity<TranslationVoteDTO.VoteResponse> vote (
            @PathVariable UUID versionId,
            @Valid @RequestBody TranslationVoteDTO.VoteRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails)
            {
                log.info("Vote request from user: {} for version: {} with vote: {}", 
                userDetails.getUsername(), versionId, request.voteType());

                 // Obtener usuario actual
                String loginIdentifier = userDetails.getUsername();
                User currentUser = userRepository.findByUsernameOrEmailAndDeletedAtIsNull(loginIdentifier, loginIdentifier)
                        .orElseThrow(() -> new ForbiddenAccessException("User not found"));

                // Procesar voto
                TranslationVoteDTO.VoteResponse response = voteService.vote(versionId, request, currentUser.getId());

                // Determinar código de respuesta
                HttpStatus status = response.createdAt().equals(response.updatedAt()) ? 
                    HttpStatus.CREATED : HttpStatus.OK;

                return ResponseEntity.status(status).body(response);
            }

    @PutMapping("/{versionId}/vote")
    @PreAuthorize("hasRole('USER')")
    @Operation(
        summary = "Cambiar voto existente",
        description = "Cambia un voto existente de upvote a downvote o viceversa"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Voto actualizado exitosamente"),
        @ApiResponse(responseCode = "404", description = "Voto no encontrado"),
        @ApiResponse(responseCode = "401", description = "Autenticación requerida")
    })
    public ResponseEntity<TranslationVoteDTO.VoteResponse> updateVote(
            @PathVariable UUID versionId,
            @Valid @RequestBody TranslationVoteDTO.VoteRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

        log.info("Vote update request from user: {} for version: {} with new vote: {}", 
                userDetails.getUsername(), versionId, request.voteType());

        // Obtener usuario actual
        String loginIdentifier = userDetails.getUsername();
        User currentUser = userRepository.findByUsernameOrEmailAndDeletedAtIsNull(loginIdentifier, loginIdentifier)
                .orElseThrow(() -> new ForbiddenAccessException("User not found"));

        // Actualizar voto
        TranslationVoteDTO.VoteResponse response = voteService.vote(versionId, request, currentUser.getId());

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{versionId}/vote")
    @PreAuthorize("hasRole('USER')")
    @Operation(
        summary = "Eliminar voto",
        description = "Elimina tu voto de una versión de traducción"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Voto eliminado exitosamente"),
        @ApiResponse(responseCode = "404", description = "Voto no encontrado"),
        @ApiResponse(responseCode = "401", description = "Autenticación requerida")
    })
    public ResponseEntity<Void> removeVote(
            @PathVariable UUID versionId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

        log.info("Vote removal request from user: {} for version: {}", 
                userDetails.getUsername(), versionId);

        // Obtener usuario actual
        String loginIdentifier = userDetails.getUsername();
        User currentUser = userRepository.findByUsernameOrEmailAndDeletedAtIsNull(loginIdentifier, loginIdentifier)
                .orElseThrow(() -> new ForbiddenAccessException("User not found"));

        // Eliminar voto
        voteService.removeVote(versionId, currentUser.getId());

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{versionId}/stats")
    @PreAuthorize("hasRole('USER')")
    @Operation(
        summary = "Obtener estadísticas de votos",
        description = "Obtiene estadísticas de votación para una versión específica"
    )
    @ApiResponse(responseCode = "200", description = "Estadísticas obtenidas exitosamente")
    public ResponseEntity<TranslationVoteDTO.VoteStats> getVoteStats(
            @PathVariable UUID versionId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

        log.debug("Vote stats request for version: {}", versionId);

        // Obtener usuario actual (opcional para stats)
        String loginIdentifier = userDetails.getUsername();
        User currentUser = userRepository.findByUsernameOrEmailAndDeletedAtIsNull(loginIdentifier, loginIdentifier)
                .orElse(null);

        UUID currentUserId = currentUser != null ? currentUser.getId() : null;

        // Obtener estadísticas
        TranslationVoteDTO.VoteStats stats = voteService.getVoteStats(versionId, currentUserId);

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/{versionId}/votes")
    @PreAuthorize("hasRole('USER')")
    @Operation(
        summary = "Listar votos de una versión",
        description = "Obtiene lista paginada de todos los votos de una versión"
    )
    @ApiResponse(responseCode = "200", description = "Votos obtenidos exitosamente")
    public ResponseEntity<Page<TranslationVoteDTO.VoteResponse>> getVersionVotes(
            @PathVariable UUID versionId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) 
            Pageable pageable) {

        log.debug("Version votes request for version: {}", versionId);

        Page<TranslationVoteDTO.VoteResponse> votes = voteService.getVersionVotes(versionId, pageable);

        return ResponseEntity.ok(votes);
    }

    @GetMapping("/my-votes")
    @PreAuthorize("hasRole('USER')")
    @Operation(
        summary = "Obtener mis votos",
        description = "Obtiene lista paginada de todos los votos del usuario actual"
    )
    @ApiResponse(responseCode = "200", description = "Votos del usuario obtenidos exitosamente")
    public ResponseEntity<Page<TranslationVoteDTO.VoteResponse>> getMyVotes(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) 
            Pageable pageable,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

        log.debug("My votes request from user: {}", userDetails.getUsername());

        // Obtener usuario actual
        String loginIdentifier = userDetails.getUsername();
        User currentUser = userRepository.findByUsernameOrEmailAndDeletedAtIsNull(loginIdentifier, loginIdentifier)
                .orElseThrow(() -> new ForbiddenAccessException("User not found"));

        Page<TranslationVoteDTO.VoteResponse> votes = voteService.getUserVotes(currentUser.getId(), pageable);

        return ResponseEntity.ok(votes);
    }
}

package com.jesusLuna.polyglotCloud.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jesusLuna.polyglotCloud.dto.TranslationVoteDTO;
import com.jesusLuna.polyglotCloud.exception.BusinessRuleException;
import com.jesusLuna.polyglotCloud.exception.ResourceNotFoundException;
import com.jesusLuna.polyglotCloud.mapper.TranslationVoteMapper;
import com.jesusLuna.polyglotCloud.models.Translations.TranslationVersion;
import com.jesusLuna.polyglotCloud.models.Translations.TranslationVote;
import com.jesusLuna.polyglotCloud.models.User;
import com.jesusLuna.polyglotCloud.models.enums.VoteType;
import com.jesusLuna.polyglotCloud.repository.TranslationVersionRepository;
import com.jesusLuna.polyglotCloud.repository.TranslationVoteRepository;
import com.jesusLuna.polyglotCloud.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TranslationVoteService {

    private final TranslationVoteRepository voteRepository;
    private final TranslationVersionRepository versionRepository;
    private final UserRepository userRepository;
    private final TranslationVoteMapper voteMapper;
    private final CacheService cacheService;

    @Transactional
    public TranslationVoteDTO.VoteResponse vote(
            UUID versionId, 
            TranslationVoteDTO.VoteRequest request, 
            UUID userId) {
        
        log.info("User {} voting {} on version {}", userId, request.voteType(), versionId);

        // Validar versión
        TranslationVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("Translation version", "id", versionId));

        // Validar usuario
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        // Validar que no vote su propia versión
        if (version.getAuthor().getId().equals(userId)) {
            throw new BusinessRuleException("Cannot vote on your own translation version");
        }

        // Buscar voto existente
        var existingVote = voteRepository.findByTranslationVersionIdAndUserId(versionId, userId);
        TranslationVote vote;

        if (existingVote.isPresent()) {
            // Cambiar voto existente
            vote = existingVote.get();
            
            if (vote.getVoteType() == request.voteType()) {
                throw new BusinessRuleException("You have already cast this type of vote on this version");
            }
            
            log.info("Changing vote from {} to {} for version {}", 
                    vote.getVoteType(), request.voteType(), versionId);
            vote.changeVoteType(request.voteType());
        } else {
            // Crear nuevo voto
            vote = TranslationVote.builder()
                    .translationVersion(version)
                    .user(user)
                    .voteType(request.voteType())
                    .build();
        }

        TranslationVote savedVote = voteRepository.save(vote);

        // Recalcular puntuaciones de la versión
        updateVersionScores(version);

        // Limpiar cache relacionado
        clearVersionCaches(version);

        // Verificar si esta versión debería ser auto-aprobada
        checkForAutoApproval(version);

        return voteMapper.toResponse(savedVote);
    }

    @Transactional
    public void removeVote(UUID versionId, UUID userId) {
        log.info("User {} removing vote from version {}", userId, versionId);

        TranslationVote vote = voteRepository.findByTranslationVersionIdAndUserId(versionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Vote not found for this user and version"));

        TranslationVersion version = vote.getTranslationVersion();
        
        voteRepository.delete(vote);

        // Recalcular puntuaciones
        updateVersionScores(version);
        clearVersionCaches(version);
    }

    public TranslationVoteDTO.VoteStats getVoteStats(UUID versionId, UUID currentUserId) {
        TranslationVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("Translation version", "id", versionId));

        // Obtener voto del usuario actual si existe
        var userVote = currentUserId != null ? 
                voteRepository.findByTranslationVersionIdAndUserId(versionId, currentUserId) : 
                null;

        // Usar constructor del record
        return new TranslationVoteDTO.VoteStats(
            versionId,
            version.getUpvotesCount(),
            version.getDownvotesCount(),
            version.getTotalScore(),
            version.getTotalVotes(),
            version.getApprovalRate(),
            userVote.isPresent(),
            userVote.map(TranslationVote::getVoteType).orElse(null)
        );
    }

    public Page<TranslationVoteDTO.VoteResponse> getVersionVotes(UUID versionId, Pageable pageable) {
        // Verificar que la versión existe
        if (!versionRepository.existsById(versionId)) {
            throw new ResourceNotFoundException("Translation version", "id", versionId);
        }

        Page<TranslationVote> votes = voteRepository
                .findByTranslationVersionIdOrderByCreatedAtDesc(versionId, pageable);

        return votes.map(voteMapper::toResponse);
    }

    public Page<TranslationVoteDTO.VoteResponse> getUserVotes(UUID userId, Pageable pageable) {
        // Verificar que el usuario existe
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User", "id", userId);
        }

        Page<TranslationVote> votes = voteRepository
                .findByUserIdOrderByCreatedAtDesc(userId, pageable);

        return votes.map(voteMapper::toResponse);
    }

    /**
     * Obtiene versiones ordenadas por puntuación para una traducción
     */
    public TranslationVoteDTO.TopVersions getTopVersions(UUID translationId, UUID currentUserId) {
        List<TranslationVersion> allVersions = versionRepository
                .findByTranslationIdOrderByVersionNumberAsc(translationId);

        if (allVersions.isEmpty()) {
            return new TranslationVoteDTO.TopVersions(translationId, List.of(), List.of());
        }

        // Obtener votos del usuario actual para estas versiones si está logueado
        Map<UUID, VoteType> userVotes = Map.of();
        if (currentUserId != null) {
            List<UUID> versionIds = allVersions.stream()
                    .map(TranslationVersion::getId)
                    .collect(Collectors.toList());
            
            userVotes = voteRepository.findByVersionIdsAndUserId(versionIds, currentUserId)
                    .stream()
                    .collect(Collectors.toMap(
                        vote -> vote.getTranslationVersion().getId(),
                        TranslationVote::getVoteType
                    ));
        }

        final Map<UUID, VoteType> finalUserVotes = userVotes;

        // Convertir a DTO con estadísticas de votación
        List<TranslationVoteDTO.VersionWithVotes> versionsWithVotes = allVersions.stream()
                .map(version -> mapVersionWithVotes(version, finalUserVotes))
                .collect(Collectors.toList());

        // Top por puntuación (mejores primero)
        List<TranslationVoteDTO.VersionWithVotes> topByScore = versionsWithVotes.stream()
                .sorted((v1, v2) -> Integer.compare(
                    v2.voteStats().totalScore(), 
                    v1.voteStats().totalScore()
                ))
                .limit(5)
                .collect(Collectors.toList());

        // Recientes (últimas primero)
        List<TranslationVoteDTO.VersionWithVotes> recent = versionsWithVotes.stream()
                .sorted((v1, v2) -> v2.createdAt().compareTo(v1.createdAt()))
                .limit(5)
                .collect(Collectors.toList());

        return new TranslationVoteDTO.TopVersions(translationId, topByScore, recent);
    }

    @Transactional
    protected void updateVersionScores(TranslationVersion version) {
        // Refrescar la versión con sus votos
        version = versionRepository.findById(version.getId()).orElseThrow();
        
        // Recalcular puntuaciones
        version.recalculateScores();
        
        versionRepository.save(version);
        
        log.debug("Updated scores for version {}: {} upvotes, {} downvotes, {} total score",
                version.getId(), version.getUpvotesCount(), version.getDownvotesCount(), version.getTotalScore());
    }

    private void clearVersionCaches(TranslationVersion version) {
        // Limpiar caches relacionados
        cacheService.delete("translation::" + version.getTranslation().getId());
        cacheService.deletePattern("version::*");
        cacheService.deletePattern("vote::*");
    }

    private void checkForAutoApproval(TranslationVersion version) {
        // Configuración de auto-aprobación
        final int AUTO_APPROVAL_THRESHOLD = 5; // Puntuación mínima para auto-aprobación
        
        if (version.hasHighEnoughScoreForAutoApproval(AUTO_APPROVAL_THRESHOLD)) {
            log.info("Version {} has reached auto-approval threshold with score {}", 
                    version.getId(), version.getTotalScore());
            
            // Aquí puedes disparar eventos o notificaciones para moderadores
            // O directamente auto-aprobar si es la política de tu aplicación
        }
    }

    private TranslationVoteDTO.VersionWithVotes mapVersionWithVotes(
            TranslationVersion version, 
            Map<UUID, VoteType> userVotes) {
        
        VoteType userVoteType = userVotes.get(version.getId());
        
        // Usa el constructor del record directamente
        TranslationVoteDTO.VoteStats voteStats = new TranslationVoteDTO.VoteStats(
            version.getId(),           // versionId
            version.getUpvotesCount(), // upvotesCount
            version.getDownvotesCount(), // downvotesCount
            version.getTotalScore(),   // totalScore
            version.getTotalVotes(),   // totalVotes
            version.getApprovalRate(), // approvalRate
            userVoteType != null,      // userHasVoted
            userVoteType               // userVoteType
        );

        // Usar constructor del record
        return new TranslationVoteDTO.VersionWithVotes(
            version.getId(),
            version.getVersionNumber(),
            version.getTranslatedCode(),
            version.getAuthor().getUsername(),
            version.getAuthor().getId(),
            version.getChangeNotes(),
            version.getIsCurrentVersion(),
            voteStats,
            version.getCreatedAt()
        );
    }
}
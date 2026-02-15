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
import org.springframework.web.bind.annotation.RestController;

import com.jesusLuna.polyglotCloud.dto.TranslationVoteDTO;
import com.jesusLuna.polyglotCloud.service.TranslationVoteService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/translations")
@Tag(name = "Translation Rankings", description = "Community rankings and top translations")
@SecurityRequirement(name = "Bearer Authentication")
public class TranslationRankingController {

    private final TranslationVoteService voteService;

    @GetMapping("/{translationId}/top-versions")
    @Operation(
        summary = "Get top translation versions",
        description = "Retrieves the best-rated and most recent versions of a translation"
    )
    @ApiResponse(responseCode = "200", description = "Top versions retrieved successfully")
    public ResponseEntity<TranslationVoteDTO.TopVersions> getTopVersions(
            @PathVariable UUID translationId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

        UUID currentUserId = userDetails != null ? UUID.fromString(userDetails.getUsername()) : null;
        
        TranslationVoteDTO.TopVersions topVersions = voteService.getTopVersions(translationId, currentUserId);
        
        return ResponseEntity.ok(topVersions);
    }

    @GetMapping("/users/{userId}/votes")
    @Operation(
        summary = "Get user votes",
        description = "Retrieves voting history for a specific user"
    )
    @ApiResponse(responseCode = "200", description = "User votes retrieved successfully")
    public ResponseEntity<Page<TranslationVoteDTO.VoteResponse>> getUserVotes(
            @PathVariable UUID userId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) 
            Pageable pageable) {

        Page<TranslationVoteDTO.VoteResponse> votes = voteService.getUserVotes(userId, pageable);
        
        return ResponseEntity.ok(votes);
    }
}

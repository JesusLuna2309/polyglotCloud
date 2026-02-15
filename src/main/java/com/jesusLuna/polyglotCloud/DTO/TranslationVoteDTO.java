package com.jesusLuna.polyglotCloud.dto;

import java.time.Instant;
import java.util.UUID;

import com.jesusLuna.polyglotCloud.models.enums.VoteType;

import jakarta.validation.constraints.NotNull;

public class TranslationVoteDTO {

    public record VoteRequest(
            @NotNull(message = "Vote type is required")
            VoteType voteType
    ) {}

    public record VoteResponse(
            UUID id,
            UUID versionId,
            UUID userId,
            String username,
            VoteType voteType,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record VoteStats(
            UUID versionId,
            Integer upvotesCount,
            Integer downvotesCount,
            Integer totalScore,
            Integer totalVotes,
            Double approvalRate,
            Boolean userHasVoted,
            VoteType userVoteType
    ) {}

    public record VersionWithVotes(
            UUID id,
            Integer versionNumber,
            String translatedCode,
            String authorName,
            UUID authorId,
            String changeNotes,
            Boolean isCurrentVersion,
            VoteStats voteStats,
            Instant createdAt
    ) {}

    public record TopVersions(
            UUID translationId,
            java.util.List<VersionWithVotes> topVersionsByScore,
            java.util.List<VersionWithVotes> recentVersions
    ) {}
}
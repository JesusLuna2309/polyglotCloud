package com.jesusLuna.polyglotCloud.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.jesusLuna.polyglotCloud.dto.TranslationVersionDTO;
import com.jesusLuna.polyglotCloud.models.Translations.TranslationVersion;

@Mapper(componentModel = "spring")
public interface TranslationVersionMapper {

    @Mapping(source = "translation.id", target = "translationId")
    @Mapping(source = "author.username", target = "authorName")
    @Mapping(source = "author.id", target = "authorId")
    TranslationVersionDTO.VersionResponse toResponse(TranslationVersion version);

    @Mapping(source = "author.username", target = "authorName")
    @Mapping(source = "author.id", target = "authorId")
    TranslationVersionDTO.VersionSummary toSummary(TranslationVersion version);

    List<TranslationVersionDTO.VersionSummary> toSummaryList(List<TranslationVersion> versions);

    default TranslationVersionDTO.VersionHistory toHistory(List<TranslationVersion> versions) {
        if (versions.isEmpty()) {
            return new TranslationVersionDTO.VersionHistory(null, 0, 0, List.of());
        }
        
        TranslationVersion firstVersion = versions.get(0);
        List<TranslationVersionDTO.VersionSummary> summaries = toSummaryList(versions);
        
        Integer currentVersion = versions.stream()
                .filter(TranslationVersion::getIsCurrentVersion)
                .map(TranslationVersion::getVersionNumber)
                .findFirst()
                .orElse(versions.size());

        return new TranslationVersionDTO.VersionHistory(
            firstVersion.getTranslation().getId(),
            versions.size(),
            currentVersion,
            summaries
        );
    }
}

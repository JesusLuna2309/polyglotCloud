package com.jesusLuna.polyglotCloud.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.jesusLuna.polyglotCloud.dto.TranslationVoteDTO;
import com.jesusLuna.polyglotCloud.models.Translations.TranslationVote;

@Mapper(componentModel = "spring")
public interface TranslationVoteMapper {

    @Mapping(source = "translationVersion.id", target = "versionId")
    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "user.username", target = "username")
    TranslationVoteDTO.VoteResponse toResponse(TranslationVote vote);
}
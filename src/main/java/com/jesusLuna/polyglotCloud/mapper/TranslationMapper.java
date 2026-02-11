package com.jesusLuna.polyglotCloud.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.jesusLuna.polyglotCloud.dto.TranslationDTO;
import com.jesusLuna.polyglotCloud.models.Translation;

@Mapper(componentModel = "spring")
public interface TranslationMapper {

    @Mapping(source = "sourceSnippet.id", target = "snippetId")
    @Mapping(source = "sourceLanguage.name", target = "sourceLanguage")
    @Mapping(source = "targetLanguage.name", target = "targetLanguage")
    TranslationDTO.TranslationResponse toResponse(Translation translation);

    TranslationDTO.TranslationStatusResponse toStatusResponse(Translation translation);
}

package com.jesusLuna.polyglotCloud.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.jesusLuna.polyglotCloud.DTO.TranslationDTO;
import com.jesusLuna.polyglotCloud.models.Translations.Translation;

@Mapper(componentModel = "spring")
public interface TranslationMapper {

    @Mapping(source = "sourceSnippet.id", target = "snippetId")
    @Mapping(source = "sourceLanguage.name", target = "sourceLanguage")
    @Mapping(source = "targetLanguage.name", target = "targetLanguage")
    @Mapping(expression = "java(translation.getVersions().size())", target = "totalVersions")
    TranslationDTO.TranslationResponse toResponse(Translation translation);
    
    @Mapping(expression = "java(translation.getVersions().size())", target = "totalVersions")
    TranslationDTO.TranslationStatusResponse toStatusResponse(Translation translation);
}

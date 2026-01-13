package com.jesusLuna.polyglotCloud.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.jesusLuna.polyglotCloud.dto.SnippetDTO;
import com.jesusLuna.polyglotCloud.models.Snippet;

@Mapper(componentModel = "spring")
public interface SnippetMapper {

    // MapStruct empareja los campos por nombre automáticamente.
    // Si se llaman igual (title -> title), no necesitas hacer nada.
    
    SnippetDTO.SnippetSummaryResponse toSummaryResponse(Snippet snippet);

    // Si tuvieras campos con nombres distintos, usarías esto:
    // @Mapping(source = "creationDate", target = "createdAt")
    // SnippetDTO.SnippetSummaryResponse toSummaryResponse(Snippet snippet);
    @Mapping(source = "user.username" , target = "authorName")
    @Mapping(source = "language.name", target = "languageName")
    SnippetDTO.SnippetPublicResponse toPublicResponse(Snippet snippet);
    
    @Mapping(source = "language.name", target = "languageName")
    @Mapping(source = "user.username", target = "authorName")
    @Mapping(source = "user.id", target = "authorId")
    SnippetDTO.SnippetDetailResponse toDetailResponse(Snippet snippet);
}
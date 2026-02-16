package com.jesusLuna.polyglotCloud.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.jesusLuna.polyglotCloud.DTO.UserDTO;
import com.jesusLuna.polyglotCloud.models.User;

@Mapper(componentModel = "spring")
public interface UserMapper {

    /**
     * Convierte User -> UserAdminResponse (Para panel de administración)
     * Incluye toda la información sensible
     */
    UserDTO. UserAdminResponse toAdminResponse(User user);

    /**
     * Convierte User -> UserPublicResponse (Para vistas públicas)
     * Solo información básica visible para todos
     */
    @Mapping(source= "username", target= "username")
    UserDTO.UserPublicResponse toPublicResponse(User user);

    /**
     * Convierte User -> UserProfileResponse (Para el perfil del propio usuario)
     * Información completa pero sin datos sensibles de admin
     */
    UserDTO.UserProfileResponse toProfileResponse(User user);
}

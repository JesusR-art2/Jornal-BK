package com.jornal.users;

import com.jornal.users.dto.RegisterRequest;
import com.jornal.users.dto.UserResponse;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "name", ignore = true)                // se compone con el name_order del país
    @Mapping(target = "phone", ignore = true)               // se normaliza a E.164 en el servicio
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "createdIn", ignore = true)
    @Mapping(target = "updatedIn", ignore = true)
    @Mapping(target = "verificationState", ignore = true)
    @Mapping(target = "locale", ignore = true)              // lo resuelve el servicio
    @Mapping(target = "countryCode", ignore = true)
    @Mapping(target = "termsVersion", ignore = true)
    @Mapping(target = "termsAcceptedAt", ignore = true)
    @Mapping(target = "privacyVersion", ignore = true)
    @Mapping(target = "privacyAcceptedAt", ignore = true)
    User toEntity(RegisterRequest request);

    @Mapping(target = "consentUpToDate", ignore = true)
    UserResponse toResponse(User user);
}

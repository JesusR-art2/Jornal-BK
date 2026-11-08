package com.jornal.Users;

import com.jornal.Users.DTO.RegisterRequest;
import com.jornal.Users.DTO.UserResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target="id", ignore = true)
    @Mapping(target="passwordHash", ignore = true)
    @Mapping(target="active", ignore = true)
    @Mapping(target="createdIn", ignore = true)
    @Mapping(target="updatedIn", ignore = true)
    @Mapping(target = "verificationState", ignore = true)


    User toEntity(RegisterRequest request);

    UserResponse toResponse(User user);


}

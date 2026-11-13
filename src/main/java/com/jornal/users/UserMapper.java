package com.jornal.users;

import com.jornal.users.dto.RegisterRequest;
import com.jornal.users.dto.UpdateUserRequest;
import com.jornal.users.dto.UserResponse;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target="id", ignore = true)
    @Mapping(target="passwordHash", ignore = true)
    @Mapping(target="active", ignore = true)
    @Mapping(target="createdIn", ignore = true)
    @Mapping(target="updatedIn", ignore = true)
    @Mapping(target = "verificationState", ignore = true)
    @BeanMapping(ignoreByDefault = true,
            nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "name", source = "name")
    @Mapping(target = "phone", source = "phone")
    void updateFromRequest(UpdateUserRequest request, @MappingTarget User user);

    User toEntity(RegisterRequest request);

    UserResponse toResponse(User user);


}

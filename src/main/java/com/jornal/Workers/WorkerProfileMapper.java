package com.jornal.Workers;

import com.jornal.Workers.DTO.WorkerProfileRequest;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface WorkerProfileMapper {

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "bio", source = "bio")
    @Mapping(target = "workRadiusKm", source = "workRadiusKm")
    void update(WorkerProfileRequest request, @MappingTarget WorkerProfile profile);

}

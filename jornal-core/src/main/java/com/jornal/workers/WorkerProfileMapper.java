package com.jornal.workers;

import com.jornal.workers.dto.WorkerProfileRequest;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface WorkerProfileMapper {

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "bio", source = "bio")
    @Mapping(target = "workRadiusKm", source = "workRadiusKm")
    @Mapping(target = "baseLatitude", source = "baseLatitude")
    @Mapping(target = "baseLongitude", source = "baseLongitude")
    void update(WorkerProfileRequest request, @MappingTarget WorkerProfile profile);

}

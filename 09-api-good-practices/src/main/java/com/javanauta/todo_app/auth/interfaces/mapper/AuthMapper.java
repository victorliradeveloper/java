package com.javanauta.todo_app.auth.interfaces.mapper;

import com.javanauta.todo_app.auth.domain.model.User;
import com.javanauta.todo_app.auth.interfaces.dto.request.RegisterRequestDTO;
import com.javanauta.todo_app.auth.interfaces.dto.response.AuthResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AuthMapper {

    @Mapping(target = "id", ignore = true)
    User toEntity(RegisterRequestDTO request);

    @Mapping(target = "name", source = "user.name")
    @Mapping(target = "token", source = "token")
    AuthResponseDTO toResponse(User user, String token);
}

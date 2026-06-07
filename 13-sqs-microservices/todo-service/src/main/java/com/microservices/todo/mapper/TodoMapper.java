package com.microservices.todo.mapper;

import com.microservices.todo.dto.request.TodoRequestDTO;
import com.microservices.todo.dto.request.TodoUpdateDTO;
import com.microservices.todo.dto.response.TodoResponseDTO;
import com.microservices.todo.infrastructure.entity.Todo;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface TodoMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "completed", constant = "false")
    Todo toEntity(TodoRequestDTO dto);

    TodoResponseDTO toResponse(Todo todo);

    // Os tres source/target abaixo sao redundantes — MapStruct ja casa
    // por nome quando source e target tem o mesmo identificador. O certo
    // seria omitir, mantidos aqui apenas como exemplo didatico.
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(source = "title",       target = "title")
    @Mapping(source = "description", target = "description")
    @Mapping(source = "completed",   target = "completed")
    @Mapping(source = "priority",    target = "priority")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(TodoUpdateDTO dto, @MappingTarget Todo todo);
}

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

// componentModel = "spring" -> a implementação gerada vira um @Component
// e pode ser injetada via construtor (como qualquer bean do Spring).
@Mapper(componentModel = "spring")
public interface TodoMapper {

    // Converte o DTO de entrada (POST /todos) na entidade JPA que será salva.
    // O TodoRequestDTO só tem 'title' e 'description'. A entidade Todo tem
    // mais campos (id, createdAt, completed) que precisam ser tratados:

    // 'id' -> ignorado: quem gera é o banco (@GeneratedValue na entidade).
    //   Se não ignorássemos, o MapStruct reclamaria em compilação que não
    //   sabe de onde tirar o valor (não existe no DTO).
    @Mapping(target = "id", ignore = true)

    // 'createdAt' -> ignorado: é preenchido pelo @PrePersist da entidade
    //   no momento do save. Não vem do DTO nem do cliente.
    @Mapping(target = "createdAt", ignore = true)

    // 'completed' -> constante 'false': todo recém-criado começa como
    //   não-concluído. É o valor fixo que você tinha no .completed(false)
    //   do builder no service.
    @Mapping(target = "completed", constant = "false")
    Todo toEntity(TodoRequestDTO dto);

    // Converte a entidade na resposta da API. Todos os campos têm o mesmo
    // nome nos dois lados, então o MapStruct mapeia automaticamente sem
    // precisar de @Mapping.
    TodoResponseDTO toResponse(Todo todo);

    // Atualiza uma entidade existente (PUT /todos/{id}) com os campos do DTO.
    // O @MappingTarget diz: "não crie um Todo novo, modifique este aqui".

    // nullValuePropertyMappingStrategy = IGNORE -> se um campo do DTO vier
    // null, NÃO sobrescreve o valor atual da entidade. É o equivalente aos
    // 'if (dto.title() != null) todo.setTitle(...)' que você tinha no service:
    // permite update parcial (PATCH-like) sem apagar campos não enviados.
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)

    // 'id' e 'createdAt' -> ignorados aqui também: não devem ser alterados
    // num update, mesmo que aparecessem no DTO. (Hoje o TodoUpdateDTO nem
    // tem esses campos, mas deixar explícito protege contra mudanças futuras
    // e silencia o aviso do MapStruct sobre "unmapped target property".)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void updateEntity(TodoUpdateDTO dto, @MappingTarget Todo todo);
}

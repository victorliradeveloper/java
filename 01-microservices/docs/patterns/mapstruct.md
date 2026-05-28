# MapStruct

## Visão Geral

**MapStruct** é um *annotation processor* Java que gera, em tempo de
compilação, código para converter um objeto em outro. Em vez de escrever
`destino.setX(origem.getX())` linha por linha (ou usar reflection cara
em runtime), você declara uma **interface** com a assinatura do mapeamento
e o MapStruct gera a implementação concreta.

```java
@Mapper(componentModel = "spring")
public interface TodoMapper {
    Todo toEntity(TodoRequestDTO dto);
    TodoResponseDTO toResponse(Todo todo);
}
```

Esse código gerado vira um `@Component` do Spring (graças ao
`componentModel = "spring"`) e pode ser injetado via construtor como
qualquer outro bean.

---

## Por que usar

| Problema sem MapStruct | Como MapStruct resolve |
|---|---|
| 20 linhas de `setX(getX())` em cada conversão, fácil esquecer um campo | Gera tudo automaticamente; campo novo aparece como aviso de compilação se não for mapeado |
| Conversor manual silenciosamente desatualiza quando entidade muda | Falha de compilação se houver campo novo na entidade sem origem definida |
| Bibliotecas baseadas em reflection (ModelMapper, Dozer) são lentas e mascaram erros pra runtime | Gera código Java puro — mesma performance de código escrito à mão; erros são pegos em compile-time |
| Lógica de transformação misturada no service | Mapeamento isolado em uma interface focada |

A frase de uma linha: **MapStruct elimina código repetitivo de conversão
sem custo de runtime, e pega erros antes do app subir**.

---

## Quando usar

Adote MapStruct quando o caso for **conversão estrutural entre beans**:

- Entidade JPA ↔ DTO de request/response
- Evento (record) → entidade que será persistida
- DTO de update parcial sobreposto sobre entidade existente
  (`@MappingTarget` + `NullValuePropertyMappingStrategy.IGNORE`)
- Sub-objetos aninhados com nomes/estruturas levemente diferentes

A regra de ouro: **se você ia escrever `.builder()` ou vários `setX(...)`
copiando campos, MapStruct ganha**.

---

## Quando **não** usar

- **Destino não é um bean Java**. Ex.: setar variáveis em um `Context` do
  Thymeleaf, montar um `MimeMessage`, popular um `Map<String, Object>`.
  MapStruct gera chamadas a setters tipados — sem setters, não cabe.
  Esse é o caso do `notification-service` no projeto, e por isso ele
  **não tem mapper** (ver §5 do ticket
  [`mapstruct-rollout.md`](../../.spec/issues/closed/mapstruct-rollout.md)).
- **Lógica de negócio**, não cópia de campos. Se a "conversão" precisa
  de queries, validações ou decisões — isso pertence ao service.
- **DTO sem comportamento de mapeamento real**. Se A e B têm exatamente
  os mesmos campos e nomes, um construtor já resolve.
- **Cuidado com `@Mapper` ≠ `@Jackson2JavaTypeMapper`**: o
  `DefaultJackson2JavaTypeMapper` no `RabbitMQConfig` dos consumers
  **não é MapStruct** — é resolução de tipo Java a partir do header
  `__TypeId__` do Spring AMQP. Mesma palavra, padrão completamente
  diferente.

---

## Mapeamento implícito: omitir `@Mapping` quando os nomes batem

MapStruct **infere automaticamente** o mapeamento quando o nome do
campo no destino é igual ao nome do campo (ou parâmetro) na origem.
Anotações `@Mapping(source = "x", target = "x")` redundantes podem ser
removidas — o código gerado é exatamente o mesmo.

### Exemplo no projeto

Versão verbosa do `TodoAuditLogMapper` (que existiu por pouco tempo):

```java
@Mapping(target = "messageId",   source = "messageId")        // redundante
@Mapping(target = "aggregateId", source = "event.todoId")
@Mapping(target = "title",       source = "event.title")      // redundante
@Mapping(target = "eventType",   source = "event.action")
@Mapping(target = "occurredAt",  source = "event.occurredAt") // redundante
@Mapping(target = "recordedAt",  expression = "java(java.time.LocalDateTime.now())")
TodoAuditLog toAuditLog(TodoEvent event, String messageId);
```

Versão enxuta — só sobra o que é **necessário**:

```java
@Mapping(target = "aggregateId", source = "event.todoId")
@Mapping(target = "eventType",   source = "event.action")
@Mapping(target = "recordedAt",  expression = "java(java.time.LocalDateTime.now())")
TodoAuditLog toAuditLog(TodoEvent event, String messageId);
```

A implementação gerada no `target/generated-sources/.../TodoAuditLogMapperImpl.java`
continua copiando os 6 campos. Os três removidos foram resolvidos por inferência:

| Campo | Origem inferida |
|---|---|
| `messageId` | Parâmetro `String messageId` com o mesmo nome |
| `title` | `event.title()` — mesmo nome no único bean parâmetro |
| `occurredAt` | `event.occurredAt()` — idem |

### Quando ainda **precisa** ser explícito

- **Nomes diferentes**: `todoId` → `aggregateId`, `action` → `eventType`.
- **`expression`, `constant` ou `ignore`**: não são cópia — exigem `@Mapping`.
- **Ambiguidade**: se houvesse um campo `messageId` dentro de `TodoEvent` **e** o parâmetro `String messageId`, MapStruct falharia o build pedindo desambiguação.

### Quanto confiar na inferência

Confio porque:

1. **Os testes cobrem cada campo individualmente** — se a inferência quebrasse,
   `TodoAuditLogMapperTest` viraria vermelho na hora.
2. **O código gerado é legível** — abrir o `*Impl.java` em `target/generated-sources`
   confirma o que foi mapeado em ~10 segundos.
3. **Renomear quebra explicitamente**: se alguém renomear `title` → `name`
   na entidade, o match implícito some e o `Impl` para de copiar — os testes
   pegam imediatamente.

> Para um modo paranoico opcional, dá pra adicionar
> `unmappedTargetPolicy = ReportingPolicy.ERROR` ao `@Mapper`: o build
> passa a falhar se algum campo do destino ficar sem origem. Não está
> ativado no projeto, mas é uma rede de segurança fácil de ligar depois.

---

## Onde está sendo usado no projeto

### todo-service — [`TodoMapper.java`](../../todo-service/src/main/java/com/microservices/todo/mapper/TodoMapper.java)

Três operações:

| Método | Origem → Destino | Recursos usados |
|---|---|---|
| `toEntity(TodoRequestDTO)` | DTO de criação → entidade JPA | `@Mapping(ignore = true)` em `id` e `createdAt`; `@Mapping(constant = "false")` em `completed` |
| `toResponse(Todo)` | Entidade → DTO de resposta | Mapeamento implícito (nomes iguais, zero anotações) |
| `updateEntity(TodoUpdateDTO, @MappingTarget Todo)` | DTO parcial sobreposto na entidade | `@BeanMapping(nullValuePropertyMappingStrategy = IGNORE)` — campo `null` no DTO não sobrescreve valor atual |

Cobertura de teste em
[`TodoMapperUpdateTest.java`](../../todo-service/src/test/java/com/microservices/todo/mapper/TodoMapperUpdateTest.java)
— garante o comportamento de update parcial, que é a regra mais frágil
do mapper.

### audit-service — [`TodoAuditLogMapper.java`](../../audit-service/src/main/java/com/microservices/audit/mapper/TodoAuditLogMapper.java)

Conversão `TodoEvent + messageId → TodoAuditLog`:

```java
@Mapping(target = "messageId", source = "messageId")
@Mapping(target = "aggregateId", source = "event.todoId")
@Mapping(target = "title", source = "event.title")
@Mapping(target = "eventType", source = "event.action")
@Mapping(target = "occurredAt", source = "event.occurredAt")
@Mapping(target = "recordedAt", expression = "java(java.time.LocalDateTime.now())")
TodoAuditLog toAuditLog(TodoEvent event, String messageId);
```

Pontos interessantes:

- **Múltiplas origens**: o `messageId` (vem do header AMQP) e o `event`
  (vem do payload) são parâmetros separados — o mapeamento `source = "event.X"`
  desce no objeto, enquanto `source = "messageId"` aponta para o parâmetro
  direto.
- **`expression`**: o `recordedAt` é gerado no momento da conversão.
  Outras alternativas seriam um `default method` ou uma factory `@Named`,
  mas `expression` é a forma mais direta para uma única linha.

Usado em
[`TodoAuditListener.onTodoEvent(...)`](../../audit-service/src/main/java/com/microservices/audit/listener/TodoAuditListener.java),
que antes montava o `TodoAuditLog` via `.builder()` de 7 linhas e agora
chama `mapper.toAuditLog(event, messageId)`.

Cobertura em
[`TodoAuditLogMapperTest.java`](../../audit-service/src/test/java/com/microservices/audit/mapper/TodoAuditLogMapperTest.java).

---

## Setup no `pom.xml`

Os serviços que usam MapStruct (`todo-service`, `audit-service`)
compartilham exatamente o mesmo setup. Três peças:

### 1. Propriedades

```xml
<properties>
    <mapstruct.version>1.6.3</mapstruct.version>
    <lombok-mapstruct-binding.version>0.2.0</lombok-mapstruct-binding.version>
</properties>
```

### 2. Dependência

```xml
<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct</artifactId>
    <version>${mapstruct.version}</version>
</dependency>
```

### 3. Annotation processors no `maven-compiler-plugin`

```xml
<annotationProcessorPaths>
    <path>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
    </path>
    <path>
        <groupId>org.mapstruct</groupId>
        <artifactId>mapstruct-processor</artifactId>
        <version>${mapstruct.version}</version>
    </path>
    <path>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok-mapstruct-binding</artifactId>
        <version>${lombok-mapstruct-binding.version}</version>
    </path>
</annotationProcessorPaths>
```

**A ordem importa** quando há Lombok no mesmo projeto. Sem o
`lombok-mapstruct-binding`, o MapStruct roda **antes** do Lombok gerar
os getters/setters e quebra a compilação porque "não encontra" os
acessores que ainda não existem.

---

## Como testar um mapper

Mappers do MapStruct podem ser testados **sem subir o Spring**:

```java
private final TodoAuditLogMapper mapper = Mappers.getMapper(TodoAuditLogMapper.class);
```

`Mappers.getMapper(...)` carrega a implementação gerada via reflection.
Roda em milissegundos, sem `@SpringBootTest`, sem Postgres, sem Rabbit.
Ideal porque o mapper é puro — não tem dependências injetadas.

Rodar:

```powershell
.\mvnw -f todo-service\pom.xml test "-Dtest=TodoMapperUpdateTest"
.\mvnw -f audit-service\pom.xml test "-Dtest=TodoAuditLogMapperTest"
```

---

## Anatomia das anotações usadas no projeto

| Anotação | O que faz | Onde está |
|---|---|---|
| `@Mapper(componentModel = "spring")` | Marca a interface como mapper; implementação gerada vira `@Component` | Topo de todo mapper |
| `@Mapping(source, target)` | Define mapeamento explícito de campo | `TodoAuditLogMapper` |
| `@Mapping(target, ignore = true)` | Não copia esse campo | `TodoMapper.toEntity` (`id`, `createdAt`) |
| `@Mapping(target, constant = "...")` | Atribui valor literal fixo | `TodoMapper.toEntity` (`completed = "false"`) |
| `@Mapping(target, expression = "java(...)")` | Atribui resultado de expressão Java | `TodoAuditLogMapper` (`recordedAt`) |
| `@BeanMapping(nullValuePropertyMappingStrategy = IGNORE)` | Em update parcial, `null` do DTO não sobrescreve campo atual | `TodoMapper.updateEntity` |
| `@MappingTarget` | Sinaliza o objeto **existente** a ser mutado, em vez de criar novo | `TodoMapper.updateEntity` |

---

## Onde olhar a implementação gerada

Após compilar, o MapStruct deposita o código gerado em:

```
<servico>/target/generated-sources/annotations/com/microservices/<...>/mapper/<NomeDoMapper>Impl.java
```

Vale a pena abrir essa classe pelo menos uma vez para entender que é
**Java puro com setters** — sem reflection, sem mágica em runtime. É o
que justifica a performance equivalente a código escrito à mão.

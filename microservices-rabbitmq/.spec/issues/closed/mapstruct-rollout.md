# MapStruct Rollout

> Plano de ação para padronizar o uso de **MapStruct** nos microserviços do projeto.
> Status: **OPEN** · Criado em 2026-05-27.

---

## 1. Contexto

O `todo-service` já adota MapStruct 1.6.3 com sucesso no `TodoMapper`:
conversão `TodoRequestDTO ↔ Todo ↔ TodoResponseDTO` + `updateEntity`
com `NullValuePropertyMappingStrategy.IGNORE` (update parcial).

Os demais serviços convertem objetos **manualmente** via `.builder()` /
`ctx.setVariable(...)`. Antes de propagar o MapStruct cegamente, este
documento avalia onde a ferramenta **realmente ganha** e onde forçá-la
seria over-engineering.

A meta não é "usar MapStruct em todo lugar". É:

- Eliminar conversão manual onde MapStruct cabe naturalmente.
- Padronizar a forma de fazer mapeamento entre os serviços.
- Documentar explicitamente os pontos onde decidimos **não** usar.

---

## 2. Diagnóstico por serviço

| Serviço | Onde existe conversão hoje | MapStruct cabe? | Decisão |
|---|---|---|---|
| `todo-service` | `TodoMapper` (já implementado) | ✅ Já em uso | **Referência** — nada a fazer |
| `audit-service` | `TodoAuditListener.onTodoEvent()` constrói `TodoAuditLog` via `.builder()` (6 campos) | ✅ Encaixe direto | **Adotar** |
| `notification-service` | `EmailService.renderTemplate()` faz `ctx.setVariable(...)` no Thymeleaf `Context`; nenhuma entidade ↔ DTO | ⚠️ Destino é `Context`, não bean | **Não adotar** (ver §5) |
| `api-gateway` | — | N/A | **Fora de escopo** |
| `eureka-server` | — | N/A | **Fora de escopo** |

---

## 3. Tarefas — `audit-service`

### 3.1 Adicionar dependências no `audit-service/pom.xml`

Replicar exatamente o setup do `todo-service` (versões alinhadas):

```xml
<properties>
    <mapstruct.version>1.6.3</mapstruct.version>
    <lombok-mapstruct-binding.version>0.2.0</lombok-mapstruct-binding.version>
</properties>

<dependencies>
    <dependency>
        <groupId>org.mapstruct</groupId>
        <artifactId>mapstruct</artifactId>
        <version>${mapstruct.version}</version>
    </dependency>
</dependencies>
```

E no `maven-compiler-plugin` adicionar os `annotationProcessorPaths`:
`lombok`, `mapstruct-processor`, `lombok-mapstruct-binding` — **nessa ordem**.
Sem o `lombok-mapstruct-binding`, MapStruct gera o impl antes dos
getters/setters do Lombok existirem e quebra a compilação.

### 3.2 Criar `TodoAuditLogMapper`

```
audit-service/src/main/java/com/microservices/audit/mapper/TodoAuditLogMapper.java
```

```java
@Mapper(componentModel = "spring")
public interface TodoAuditLogMapper {

    @Mapping(target = "messageId", source = "messageId")
    @Mapping(target = "aggregateId", source = "event.todoId")
    @Mapping(target = "title", source = "event.title")
    @Mapping(target = "eventType", source = "event.action")
    @Mapping(target = "occurredAt", source = "event.occurredAt")
    @Mapping(target = "recordedAt", expression = "java(java.time.LocalDateTime.now())")
    TodoAuditLog toAuditLog(TodoEvent event, String messageId);
}
```

Observações:
- `messageId` vem do header AMQP, não do payload — por isso é parâmetro separado.
- `recordedAt` é gerado no momento da conversão (= momento da gravação).
- Não há `ignore` necessário: todos os campos da entidade estão mapeados.

### 3.3 Refatorar `TodoAuditListener`

Substituir o bloco do `builder()` por uma chamada ao mapper:

```java
private final TodoAuditLogMapper mapper;
...
TodoAuditLog auditLog = mapper.toAuditLog(event, messageId);
boolean inserted = repository.insertIfAbsent(auditLog);
```

Verificar que os comentários do listener (`@RabbitListener`, dedupe, etc.)
**permanecem inalterados** — eles documentam o fluxo, não a construção do objeto.

### 3.4 Cobertura de teste

Criar `TodoAuditLogMapperTest` (puro, sem Spring, via `Mappers.getMapper(...)`):

- Mapeia todos os campos corretamente quando `event` está completo.
- `messageId` parâmetro vira a chave primária.
- `recordedAt` é preenchido com horário próximo ao `LocalDateTime.now()` (margem de 1 segundo).
- Mantém `occurredAt` exatamente como veio do event (não recalcula).

---

## 4. Critérios de aceite

- [ ] `audit-service` compila sem warnings de MapStruct.
- [ ] `TodoAuditListener` não tem mais `.builder()` para criar `TodoAuditLog`.
- [ ] `TodoAuditLogMapperTest` cobre os 4 cenários da §3.4 e passa em verde.
- [ ] Smoke test manual: publicar um Todo via gateway, verificar registro novo em `todo_audit_log` com `aggregate_id`, `title`, `event_type`, `occurred_at`, `recorded_at`, `message_id` preenchidos como antes da refatoração.
- [ ] Versões de MapStruct e `lombok-mapstruct-binding` idênticas entre `todo-service` e `audit-service`.

---

## 5. Por que **não** aplicar em `notification-service`

O único ponto de "conversão" em `notification-service` é
`EmailService.renderTemplate(event)`:

```java
Context ctx = new Context();
ctx.setVariable("todoId", event.todoId());
ctx.setVariable("title", event.title());
ctx.setVariable("action", event.action());
ctx.setVariable("occurredAt", event.occurredAt().format(FMT));
ctx.setVariable("headerColor", HEADER_COLOR.getOrDefault(event.action(), DEFAULT_HEADER_COLOR));
```

Problemas para encaixar MapStruct aqui:

1. **Destino é um `Context` do Thymeleaf**, não um bean Java. MapStruct gera código que chama setters; `Context.setVariable(String, Object)` é uma API key-value, não um bean.
2. **`headerColor` é derivado** de `action` via lookup em `Map`, com fallback. Não é cópia de campo — é lógica condicional. Cabe melhor num método privado do serviço (onde já está).
3. **`occurredAt` é formatado** (`DateTimeFormatter`). Em MapStruct daria pra resolver com `@Mapping(qualifiedByName=...)`, mas vira mais indireção que linha direta.
4. Inventar um DTO `EmailTemplateVars` intermediário só pra usar MapStruct
   adiciona uma classe sem ganho funcional — apenas para uniformidade visual.

**Conclusão**: manter `renderTemplate` como está. Se um dia surgir uma
entidade JPA no `notification-service` (ex.: tabela `notification_log` com
DTO de resposta para um futuro endpoint), reabrir este plano e adotar
MapStruct na hora.

---

## 6. Fora de escopo

- `api-gateway` e `eureka-server`: nenhum domínio/DTO a mapear.
- Migrar `TodoMapper` do `todo-service`: já está em conformidade.
- Criar um módulo Maven "common-mappers" compartilhado: não há reuso
  real de mapper entre serviços (cada serviço tem sua própria cópia de
  `TodoEvent`, que é intencional por **desacoplamento de schema**).

---

## 7. Riscos e mitigações

| Risco | Mitigação |
|---|---|
| Versão do MapStruct diferente entre serviços causa comportamento divergente | Travar `1.6.3` igual ao `todo-service`; revisar no critério de aceite |
| Ordem errada de annotation processors quebra compile | Copiar bloco `<annotationProcessorPaths>` literalmente do `todo-service/pom.xml` |
| Regressão silenciosa em `recordedAt` (era `LocalDateTime.now()` direto no listener) | Teste explícito com tolerância de 1s na §3.4 |
| Refator do listener mexer no fluxo de dedupe (`insertIfAbsent`) sem querer | Diff revisto deve afetar **só** o trecho de construção do `TodoAuditLog`; nada antes do `repository.insertIfAbsent(...)` |

---

## 8. Esforço estimado

- Setup do `pom.xml` no audit-service: **5 min**
- Criar `TodoAuditLogMapper` + refatorar listener: **15 min**
- Testes do mapper: **15 min**
- Validação manual end-to-end: **10 min**

**Total: ~45 min** (uma sessão).

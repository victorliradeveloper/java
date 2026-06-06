# MapStruct — Quando declarar `source/target` explícitos (mesmo redundando)

MapStruct casa campos por nome automaticamente. Se `dto.title` e `entity.title`
existem, ele liga sem você precisar anotar nada. **Então quando declarar
`@Mapping(source = "...", target = "...")` mesmo sendo redundante?**

A resposta padrão é: **nunca**. Mas existem casos de borda. Aqui vai a
discussão honesta.

---

## A regra default: não declare o óbvio

```java
@Mapping(source = "title",       target = "title")        // redundante
@Mapping(source = "description", target = "description")  // redundante
@Mapping(source = "completed",   target = "completed")    // redundante
void updateEntity(TodoUpdateDTO dto, @MappingTarget Todo todo);
```

O bytecode gerado é **idêntico** ao da versão sem essas linhas. Sem ganho
funcional. Os argumentos contra:

| Custo | Detalhe |
|---|---|
| Ruído visual | 3 linhas a mais sem informação nova |
| Falsa segurança em refactor | Renomear o campo no IDE não atualiza a string `"title"` automaticamente — vira erro só em compilação |
| Quebra a convenção | A própria filosofia do MapStruct é "anotar só onde a convenção não cobre" |

---

## Casos em que vale a pena (mesmo redundando)

### 1. Documentação para humanos

Se o mapper é uma fronteira importante da aplicação e você quer que o leitor
veja **explicitamente** todos os campos que vão ser mapeados:

```java
@Mapping(source = "title",       target = "title")
@Mapping(source = "description", target = "description")
@Mapping(source = "completed",   target = "completed")
@Mapping(target = "id", ignore = true)
// ...
void updateEntity(...);
```

A leitura fica: "esses 3 campos vão, esse aqui é ignorado, esse aqui vira
constante". Lista completa, sem precisar abrir o DTO pra saber o que existe.

**Trade-off**: defasa quando o DTO mudar e ninguém atualizar o mapper.

### 2. Política de compilação rígida (`unmappedSourcePolicy = ERROR`)

```java
@Mapper(componentModel = "spring", unmappedSourcePolicy = ReportingPolicy.ERROR)
```

Com isso, **todo campo do source precisa estar mapeado** ou o build falha.
Útil em projetos onde "esquecer de mapear um campo novo do DTO" é bug
crítico. Aí declarar explícito **força** que mudanças no DTO obriguem
mudança no mapper.

### 3. Onboarding / projeto didático

Em código de exemplo (caso deste projeto), declarar explícito ajuda
quem está aprendendo a visualizar o que MapStruct está fazendo. Por isso
o `updateEntity` tem comentário avisando que é redundante e foi
mantido por motivo didático.

### 4. Documentar uma escolha de design que poderia mudar

Hipotético: você sabe que o campo `completed` pode vir a ser renomeado
no DTO no futuro. Marcar `source = "completed", target = "completed"`
**agora** sinaliza que o mapping é consciente — quando renomear, ficará
óbvio que precisa atualizar o mapper.

Mas honestamente: isso é raro e o IDE/CI deveria pegar isso.

---

## Recomendação prática

| Situação | Declarar explícito? |
|---|---|
| Código de produção, equipe sênior | **Não.** Confie na convenção |
| Onboarding / didático / docs vivas | Pode, com comentário avisando |
| Política `unmappedSourcePolicy = ERROR` | Sim, é exigido |
| Você quer "documentar visualmente" todos os mappings | Pode, mas considere se o esforço vale |

---

## Como está neste projeto

`updateEntity` tem `source/target` explícitos com este comentário acima:

```java
// Os tres source/target abaixo sao redundantes — MapStruct ja casa
// por nome quando source e target tem o mesmo identificador. O certo
// seria omitir, mantidos aqui apenas como exemplo didatico.
```

Nos outros métodos (`toEntity`, `toResponse`), seguimos a convenção
default: nada de redundância.

---

## Referência cruzada

- Conceito: [`source-target`](./source-target.md)
- Anotações de override: [`mapStruct`](./mapStruct.md)

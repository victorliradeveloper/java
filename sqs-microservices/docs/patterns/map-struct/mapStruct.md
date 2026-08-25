# MapStruct — `ignore = true` explicado

Vou usar uma analogia, esquece código por um minuto.

## Analogia: secretária preenchendo ficha de hospital

Imagina que você tem **dois papéis**:

**Papel A — Ficha que o paciente preencheu** (DTO):
```
Nome: João
Sintoma: febre
```

**Papel B — Ficha oficial do hospital** (Entity), que tem mais campos:
```
Nome: ______
Sintoma: ______
Número do paciente: ______
Data de internação: ______
Quarto: ______
Já recebeu alta? ______
```

Você contrata uma **secretária** (o MapStruct) e fala: "copia do papel A pro papel B".

## O problema

A secretária olha o papel A, copia o que dá:
- Nome → João ✓
- Sintoma → febre ✓

Aí ela chega no **"Número do paciente"** e trava: *"esse campo não tem no papel A. O que faço? Invento? Deixo em branco? Te aviso?"*

Por default ela manda um aviso: "ó, não soube o que fazer com 4 campos".

## `ignore = true` é o que você fala pra ela

```
ignore = true significa:
"Esquece esse campo. Não copia nada, não me avisa, não invente.
Alguém vai preencher depois."
```

No código:

```java
@Mapping(target = "id", ignore = true)             // "número do paciente"
@Mapping(target = "createdAt", ignore = true)      // "data de internação"
@Mapping(target = "updatedAt", ignore = true)      // "última atualização"
@Mapping(target = "completed", constant = "false") // "já recebeu alta? = NÃO"
Todo toEntity(TodoRequestDTO dto);
```

Você está dizendo pra secretária:

- **id**: "deixa em branco, eu preencho depois" → o `TodoService.create` faz `entity.setId(UUID.randomUUID()...)`.
- **createdAt** / **updatedAt**: "deixa em branco, eu preencho depois" → service faz `entity.setCreatedAt(now)`.
- **completed**: "**sempre** marca NÃO" → todo novo nunca nasce concluído. Isso é regra fixa, então é `constant = "false"`.

## Diferença entre `ignore` e `constant`

| | Significado | Quem decide o valor |
|---|---|---|
| `ignore = true` | "deixa em branco, vou preencher depois" | Outra parte do código (o service) |
| `constant = "false"` | "preenche com esse valor fixo, sempre" | O próprio MapStruct, na hora |

## Resumindo numa frase

`ignore = true` é dizer pro MapStruct: **"esse campo não é problema seu, alguém vai cuidar dele depois — não me dê warning"**.

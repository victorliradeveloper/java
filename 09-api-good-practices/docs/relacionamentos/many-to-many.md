# ManyToMany — Muitos para Muitos

## Resumo Rápido

Um registro de A pode se relacionar com **vários** registros de B,
e um registro de B pode se relacionar com **vários** registros de A.

---

## Diagrama UML

```
┌─────────────────────┐                    ┌─────────────────────┐
│       Estudante      │                    │       Curso         │
├─────────────────────┤                    ├─────────────────────┤
│ - id: Long          │                    │ - id: Long          │
│ - nome: String      │                    │ - titulo: String    │
│ - email: String     │                    │ - cargaHoraria: Int │
├─────────────────────┤                    ├─────────────────────┤
│ + getCursos()       │                    │ + getEstudantes()   │
└─────────────────────┘                    └─────────────────────┘
          │                                          │
          │  *                                    *  │
          └──────────────────┬───────────────────────┘
                             │
                  ┌──────────▼──────────┐
                  │  estudante_curso    │  ← tabela de junção
                  │  (join table)       │    gerada automaticamente
                  ├─────────────────────┤
                  │ estudante_id (FK)   │
                  │ curso_id (FK)       │
                  └─────────────────────┘
```

---

## Exemplo no Banco de Dados

```
estudante                    estudante_curso              curso
─────────────────────        ─────────────────────        ─────────────────────
id │ nome                    estudante_id │ curso_id      id │ titulo
───┼──────────               ─────────────┼─────────      ───┼──────────────────
1  │ Ana                     1            │ 1             1  │ Java
2  │ Bruno                   1            │ 2             2  │ Spring Boot
3  │ Carla                   2            │ 1             3  │ Docker
                             3            │ 1
                             3            │ 3
```

- Ana faz: Java, Spring Boot
- Bruno faz: Java
- Carla faz: Java, Docker

---

## Implementação JPA

```java
@Entity
public class Estudante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;

    @ManyToMany
    @JoinTable(
        name = "estudante_curso",
        joinColumns = @JoinColumn(name = "estudante_id"),
        inverseJoinColumns = @JoinColumn(name = "curso_id")
    )
    private List<Curso> cursos = new ArrayList<>();
}
```

```java
@Entity
public class Curso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String titulo;

    @ManyToMany(mappedBy = "cursos")
    private List<Estudante> estudantes = new ArrayList<>();
}
```

---

## Lado Dono vs Lado Inverso

```
Estudante  →  dono do relacionamento   (tem o @JoinTable)
Curso      →  lado inverso             (tem o mappedBy)

Regra: quem tem @JoinTable controla a tabela de junção.
```

---

## Quando Usar ManyToMany com Entidade Intermediária

Se a tabela de junção precisar de campos extras (data de matrícula, nota, etc.),
crie uma entidade intermediária no lugar do @ManyToMany simples:

```
┌─────────────┐       ┌──────────────────────┐       ┌─────────────┐
│  Estudante  │ 1   * │      Matricula        │ *   1 │    Curso    │
├─────────────┤       ├──────────────────────┤       ├─────────────┤
│ id          │───────│ estudante_id (FK)    │───────│ id          │
│ nome        │       │ curso_id (FK)        │       │ titulo      │
└─────────────┘       │ dataMatricula        │       └─────────────┘
                      │ nota                 │
                      └──────────────────────┘
```

```java
@Entity
public class Matricula {

    @ManyToOne
    @JoinColumn(name = "estudante_id")
    private Estudante estudante;

    @ManyToOne
    @JoinColumn(name = "curso_id")
    private Curso curso;

    private LocalDate dataMatricula;
    private Double nota;
}
```

---

## Resumo

| Característica | Valor |
|----------------|-------|
| Cardinalidade | * para * |
| Tabela de junção | Sim (gerada automaticamente) |
| Campos extras na junção | Usar entidade intermediária |
| Lado dono | Quem tem @JoinTable |
| Lado inverso | Quem tem mappedBy |
| Risco principal | Consultas N+1 — sempre usar JOIN FETCH |

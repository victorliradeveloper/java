# ManyToOne — Muitos para Um

## Resumo Rápido

**Vários** registros de A pertencem a **um** registro de B.
É o lado oposto do OneToMany — e é o lado que carrega a FK no banco.

---

## Diagrama UML

```
┌─────────────────────┐                    ┌─────────────────────┐
│       Pedido        │                    │      Cliente        │
├─────────────────────┤                    ├─────────────────────┤
│ - id: Long          │  *             1   │ - id: Long          │
│ - total: BigDecimal │────────────────────│ - nome: String      │
│ - status: String    │                    │ - email: String     │
│ - cliente_id (FK)   │                    ├─────────────────────┤
├─────────────────────┤                    │ + getPedidos()      │
│ + getCliente()      │                    └─────────────────────┘
└─────────────────────┘
        │
        └── muitos Pedidos apontam para um Cliente
```

---

## Exemplo no Banco de Dados

```
pedido
──────────────────────────────────────────────
id │ total   │ status    │ cliente_id (FK)
───┼─────────┼───────────┼─────────────────
1  │ 150,00  │ PAGO      │ 1              ──┐
2  │ 320,00  │ PENDENTE  │ 1              ──┤─── todos apontam para Ana
3  │ 99,00   │ CANCELADO │ 1              ──┘
4  │ 80,00   │ PAGO      │ 2              ──── aponta para Bruno

cliente
──────────────────────
id │ nome
───┼──────────────────
1  │ Ana Lima
2  │ Bruno Silva
```

---

## Implementação JPA

```java
@Entity
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private BigDecimal total;
    private String status;

    @ManyToOne
    @JoinColumn(name = "cliente_id")   // ← FK no banco
    private Cliente cliente;
}
```

```java
@Entity
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;

    // lado inverso — opcional, só se precisar navegar daqui
    @OneToMany(mappedBy = "cliente")
    private List<Pedido> pedidos = new ArrayList<>();
}
```

---

## ManyToOne é o Lado Dono

```
LADO DONO         →  Pedido   (@ManyToOne + @JoinColumn)
LADO INVERSO      →  Cliente  (@OneToMany + mappedBy)

O lado dono é quem:
  ✓ controla a FK no banco
  ✓ precisa ter o objeto referenciado setado antes de salvar
  ✓ determina o que o JPA persiste no relacionamento
```

Se você salvar um `Pedido` sem setar o `Cliente`, a FK ficará nula.
Se você só adicionar o pedido na lista do `Cliente`, **nada é salvo** — o JPA ignora o lado inverso.

```java
// ERRADO — não persiste a FK
cliente.getPedidos().add(pedido);
clienteRepository.save(cliente);

// CORRETO — persiste a FK
pedido.setCliente(cliente);
pedidoRepository.save(pedido);
```

---

## FetchType — Eager vs Lazy

```java
@ManyToOne(fetch = FetchType.LAZY)   // recomendado
@ManyToOne(fetch = FetchType.EAGER)  // padrão do JPA — cuidado
```

```
EAGER (padrão do @ManyToOne):
  Sempre carrega o Cliente junto com o Pedido.
  Mesmo quando você não precisa do Cliente.
  Pode gerar queries desnecessárias em escala.

LAZY:
  Só carrega o Cliente quando você acessa pedido.getCliente().
  Mais eficiente — use como padrão.
  Requer sessão ativa (cuidado com LazyInitializationException fora da transação).
```

---

## Comparação com OneToMany

```
OneToMany  →  perspectiva do pai   (Cliente vê seus Pedidos)
ManyToOne  →  perspectiva do filho (Pedido vê seu Cliente)

São o mesmo relacionamento, visto de lados diferentes.

Cliente  ──(OneToMany)──►  [Pedido, Pedido, Pedido]
Pedido   ──(ManyToOne)──►  Cliente
```

Na prática, você define os dois em um relacionamento bidirecional.
Se precisar apenas de um lado, prefira o **ManyToOne** — é o mais simples e eficiente.

---

## Outros Exemplos Comuns

```
Funcionario  *──────►  1  Departamento
Comentario   *──────►  1  Post
Endereco     *──────►  1  Usuario
Produto      *──────►  1  Categoria
Item         *──────►  1  Pedido
```

---

## Resumo

| Característica | Valor |
|----------------|-------|
| Cardinalidade | * para 1 |
| FK | No lado ManyToOne (tabela filho) |
| Lado dono | Sempre o ManyToOne |
| FetchType padrão | EAGER (mude para LAZY) |
| Risco principal | EAGER desnecessário gerando queries extras |
| Erro comum | Setar só o lado inverso e achar que persiste |

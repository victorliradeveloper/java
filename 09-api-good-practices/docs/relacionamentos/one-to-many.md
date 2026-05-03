# OneToMany — Um para Muitos

## Resumo Rápido

Um registro de A pode se relacionar com **vários** registros de B,
mas cada registro de B pertence a **apenas um** registro de A.

---

## Diagrama UML

```
┌─────────────────────┐                    ┌─────────────────────┐
│       Cliente       │                    │       Pedido        │
├─────────────────────┤                    ├─────────────────────┤
│ - id: Long          │                    │ - id: Long          │
│ - nome: String      │  1             *   │ - total: BigDecimal │
│ - email: String     │────────────────────│ - status: String    │
├─────────────────────┤                    │ - cliente_id (FK)   │
│ + getPedidos()      │                    ├─────────────────────┤
└─────────────────────┘                    │ + getCliente()      │
                                           └─────────────────────┘
```

---

## Exemplo no Banco de Dados

```
cliente                          pedido
──────────────────────           ──────────────────────────────────
id │ nome                        id │ total   │ status   │ cliente_id
───┼──────────────               ───┼─────────┼──────────┼───────────
1  │ Ana Lima                    1  │ 150,00  │ PAGO     │ 1
2  │ Bruno Silva                 2  │ 320,00  │ PENDENTE │ 1
                                 3  │ 80,00   │ PAGO     │ 2
```

- Ana tem 2 pedidos (id 1 e 2)
- Bruno tem 1 pedido (id 3)
- A FK fica na tabela do lado "muitos" (pedido)

---

## Implementação JPA

```java
@Entity
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;

    @OneToMany(mappedBy = "cliente", cascade = CascadeType.ALL)
    private List<Pedido> pedidos = new ArrayList<>();
}
```

```java
@Entity
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private BigDecimal total;

    @ManyToOne
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;
}
```

---

## Onde fica a FK?

```
A FK SEMPRE fica no lado "muitos" (Many).

Cliente (1) ──────────────────── Pedido (*)
                                    │
                                    └── cliente_id  ← FK aqui
```

Nunca na tabela do lado "um" — isso causaria múltiplas colunas ou uma tabela de junção desnecessária.

---

## Cascade — O que é e quando usar

```
CascadeType.ALL       → qualquer operação no pai reflete no filho
CascadeType.PERSIST   → ao salvar Cliente, salva os Pedidos junto
CascadeType.REMOVE    → ao deletar Cliente, deleta os Pedidos junto
CascadeType.MERGE     → ao atualizar Cliente, atualiza os Pedidos junto
```

Cuidado com `CascadeType.REMOVE` em relacionamentos grandes —
deletar um Cliente pode apagar centenas de Pedidos sem aviso.

---

## Bidirecional vs Unidirecional

```
BIDIRECIONAL (mais comum):
  Cliente sabe dos Pedidos  →  @OneToMany(mappedBy = "cliente")
  Pedido sabe do Cliente    →  @ManyToOne + @JoinColumn

UNIDIRECIONAL (evitar no OneToMany):
  Só Cliente sabe dos Pedidos
  JPA gera uma tabela de junção extra desnecessária — pior performance
```

**Prefira sempre o bidirecional em OneToMany.**

---

## Fluxo de Funcionamento

```
POST /clientes/1/pedidos  (criar pedido para cliente 1)

     ClienteController
           │
           ▼
     ClienteService
           │  busca cliente por id
           ▼
     Cliente (id=1, nome="Ana")
           │
           │  adiciona pedido na lista
           ▼
     pedido.setCliente(cliente)   ← aponta o lado ManyToOne
     clienteRepository.save()     ← cascata salva o pedido junto

           │
           ▼
     INSERT INTO pedido (total, status, cliente_id)
     VALUES (150.00, 'PENDENTE', 1)
```

---

## Resumo

| Característica | Valor |
|----------------|-------|
| Cardinalidade | 1 para * |
| FK | No lado "muitos" (tabela filho) |
| Tabela de junção | Não (evitar unidirecional) |
| Lado dono | ManyToOne (Pedido) |
| Lado inverso | OneToMany com mappedBy (Cliente) |
| Risco principal | N+1 queries — usar JOIN FETCH ou @EntityGraph |

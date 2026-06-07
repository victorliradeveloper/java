# Índices no MongoDB

Imagina sua collection `todos` com 5 documentos:

```
Linha 1: { id: 1, titulo: "Estudar",  prioridade: "HIGH" }
Linha 2: { id: 2, titulo: "Comprar",  prioridade: "LOW" }
Linha 3: { id: 3, titulo: "Ligar",    prioridade: "HIGH" }
Linha 4: { id: 4, titulo: "Pagar",    prioridade: "MEDIUM" }
Linha 5: { id: 5, titulo: "Treinar",  prioridade: "HIGH" }
```

Você faz: `find({ prioridade: "HIGH" })`.

---

## SEM índice — o Mongo trabalha assim

```
Linha 1 → prioridade = "HIGH"   ✓ achei
Linha 2 → prioridade = "LOW"    ✗
Linha 3 → prioridade = "HIGH"   ✓ achei
Linha 4 → prioridade = "MEDIUM" ✗
Linha 5 → prioridade = "HIGH"   ✓ achei
```

Ele **abriu as 5 linhas**, olhou o campo `prioridade` em cada uma, e juntou as que combinavam. Lê tudo, sempre.

---

## COM índice em `prioridade`

O Mongo cria uma **segunda estrutura** dentro da collection, separada dos documentos, que parece com isto:

```
Tabelinha de índice (vive ao lado dos documentos):

  "HIGH"   →  Linhas 1, 3, 5
  "LOW"    →  Linha 2
  "MEDIUM" →  Linha 4
```

Agora o `find({ prioridade: "HIGH" })` vira:

```
Olha na tabelinha: "HIGH" aponta pra linhas 1, 3, 5.
Abre só essas 3 linhas.
Pronto.
```

**Ele não abriu as linhas 2 e 4.** Não precisou. A tabelinha de índice já disse "essas duas não interessam".

---

## O que muda em escala

- 5 docs: ler todos vs ler 3 → diferença de nada.
- 10 milhões de docs com 100 deles `HIGH`:
  - Sem índice → abre 10 milhões.
  - Com índice → consulta a tabelinha, abre só 100.

---

## Resumindo a ideia

Índice é uma **segunda estrutura de dados, vivendo ao lado da collection**, que já tem os valores agrupados por campo + os ponteiros pra onde os documentos estão. Quando você filtra por aquele campo, o Mongo consulta essa tabelinha em vez de varrer tudo.

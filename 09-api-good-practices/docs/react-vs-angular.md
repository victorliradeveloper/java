# React vs Angular — Guia de Decisão Técnica

## TL;DR

| Situação | Escolha |
|----------|---------|
| Time pequeno, produto novo, precisam de velocidade | **React** |
| Empresa grande, time grande, produto complexo e longo prazo | **Angular** |
| Precisa de liberdade para montar a stack | **React** |
| Quer tudo incluso sem decisão de arquitetura | **Angular** |
| Mercado freelance / startup | **React** |
| Banco, governo, enterprise com padrões rígidos | **Angular** |

---

## O que cada um é, de verdade

**React** é uma **biblioteca de UI**. Ele só resolve renderização. Tudo mais (roteamento, estado global, formulários, HTTP, testes) você decide e monta.

**Angular** é um **framework completo**. Ele já resolve tudo: roteamento, estado, formulários, HTTP, DI, testes. Você segue o caminho que ele define.

---

## Tradeoffs Principais

### 1. Curva de Aprendizado

**React**
- Aprende JSX + hooks em poucos dias
- A dificuldade real vem depois: qual estado usar? Context? Zustand? Redux? Jotai? Qual roteador? TanStack Router? React Router?
- Curva lenta no começo, íngreme no meio

**Angular**
- TypeScript obrigatório, Decorators, Modules, DI, Observables (RxJS), Signals (novo)
- A curva é íngreme logo no início
- Depois que aprende, tudo tem um lugar certo

---

### 2. Tamanho e Composição do Time

**React** funciona melhor com times **pequenos e experientes** que conseguem tomar boas decisões de arquitetura. Com time grande sem alinhamento, o código React vira um caos — cada dev usa uma abordagem diferente para estado, fetch, etc.

**Angular** funciona melhor com times **grandes**. A rigidez do framework é proposital: todo mundo escreve código parecido. Um dev novo lê o código de outro e entende imediatamente.

---

### 3. Controle vs Convenção

```
React:   você controla tudo   →  mais flexível, mais decisões, mais risco
Angular: o framework controla →  menos flexível, menos decisões, mais previsível
```

**Exemplo concreto — formulários:**

- **React:** você escolhe entre `react-hook-form`, `formik`, `zod` + validação manual, etc.
- **Angular:** `ReactiveFormsModule` já está lá, com validação e estado de campos padronizados.

---

### 4. Performance

Ambos são performáticos para a maioria dos casos. As diferenças surgem em extremos:

| Cenário | Vantagem |
|---------|----------|
| Bundle inicial pequeno | React |
| Renderização de listas muito grandes | React (concurrent mode) |
| Detecção de mudança previsível | Angular (Zone.js + Signals) |
| SSR / hidratação | React (Next.js mais maduro) |

---

### 5. Ecossistema e Mercado

**React**
- Maior ecossistema de bibliotecas
- Mais vagas no mercado geral
- Next.js para SSR é referência hoje
- Mantido pela Meta + Vercel + comunidade enorme

**Angular**
- Google mantém e usa em produção (Google Workspace)
- Muito forte em enterprise / corporativo
- Versões com LTS longo (18 meses de suporte ativo)
- Angular Material como design system oficial

---

### 6. Manutenção a Longo Prazo

**React** — o risco é **sua arquitetura**. Se você tomou decisões ruins de estado ou estrutura nos primeiros meses, vai pagar caro depois.

**Angular** — o risco é **o próprio framework mudar**. AngularJS → Angular 2 foi uma reescrita completa. Desde então as migrações têm sido suaves via `ng update`.

---

## Quando Escolher React

- Startup ou produto novo que precisa de velocidade
- Time pequeno (1–5 devs) com experiência frontend
- Vai usar SSR / SSG com Next.js
- App com muita interatividade e estado complexo no cliente
- Precisa de ecossistema rico de UI libs (shadcn, MUI, Radix, etc.)
- Mercado freelance ou produto SaaS

## Quando Escolher Angular

- Enterprise com time grande (10+ devs) ou rotatividade alta
- Produto de longa duração onde padronização importa mais que velocidade
- Time com background Java / C# (TypeScript + OO + DI parece natural)
- Corporativo / governo onde LTS e suporte são requisitos
- Quer evitar "decisão de stack" e focar em produto

---

## Comparativo Rápido

| Critério | React | Angular |
|----------|-------|---------|
| Tipo | Biblioteca | Framework completo |
| Linguagem | JavaScript / TypeScript | TypeScript (obrigatório) |
| Curva inicial | Baixa | Alta |
| Curva avançada | Alta | Baixa |
| Roteamento | Terceiro (React Router, TanStack) | Embutido |
| Estado global | Terceiro (Redux, Zustand, Jotai) | Services + Signals |
| Formulários | Terceiro (react-hook-form, formik) | ReactiveFormsModule |
| HTTP Client | Terceiro (axios, fetch, TanStack Query) | HttpClient embutido |
| DI (Injeção de dependência) | Não nativo | Nativo e central |
| SSR | Next.js | Angular Universal |
| Flexibilidade | Alta | Baixa |
| Padronização | Baixa | Alta |
| Ideal para | Produtos ágeis, startups | Enterprise, times grandes |

---

## Analogia Final

> **React** é como alugar um apartamento vazio.
> Você decora do jeito que quiser, mas você que compra os móveis.
>
> **Angular** é como alugar um apartamento mobiliado.
> Você não escolheu os móveis, mas está pronto para morar no primeiro dia.

Nenhum é melhor. Depende se você quer **liberdade** ou **previsibilidade**.

# Diretrizes de Engenharia para IA

## Independência

- Nunca modifique ou manipule dados de produção em nenhuma circunstância.
- Nunca envie alterações diretamente para as branches `main` ou `master`.
- Nunca altere, simule ou interfira em APIs de terceiros sem autorização explícita.
- Nunca realize commits, pushs ou publique código automaticamente em repositórios Git.

---

## Arquitetura

- Siga a arquitetura e a estrutura de pastas já existentes no projeto.
- Respeite os padrões de design e decisões de engenharia já estabelecidos.
- Não introduza novos padrões arquiteturais sem uma justificativa clara.
- Evite abstrações prematuras e complexidade desnecessária.
- Não duplique regras de negócio já existentes.
- Mantenha responsabilidades isoladas e coesas.

---

## Padrões de Código

- Escreva código limpo, legível e de fácil manutenção.
- Prefira soluções simples e explícitas em vez de implementações excessivamente complexas.
- Mantenha funções pequenas e focadas em uma única responsabilidade.
- Reutilize utilitários, serviços e componentes já existentes sempre que possível.
- Evite dependências e bibliotecas desnecessárias.
- Evite código morto, variáveis não utilizadas e código comentado.
- Nunca deixe TODOs, placeholders ou implementações incompletas.
- Não utilize números mágicos; extraia constantes com nomes significativos.
- Siga as convenções de nomenclatura e padrões já utilizados no projeto.
- Preserve a consistência com o restante da base de código.

---

## Refatoração

- Não refatore arquivos ou módulos não relacionados à tarefa.
- Não renomeie APIs públicas, variáveis, componentes ou campos de banco sem necessidade.
- Não remova funcionalidades existentes sem solicitação explícita.
- Preserve compatibilidade retroativa sempre que possível.
- Evite grandes refatorações em tarefas focadas apenas em features.

---

## Segurança

- Nunca exponha segredos, credenciais, tokens ou variáveis de ambiente.
- Nunca registre informações sensíveis ou pessoais em logs.
- Valide e sanitize todas as entradas externas.
- Não ignore autenticação, autorização ou camadas de validação.
- Siga práticas seguras de desenvolvimento por padrão.

---

## Testes

- Adicione ou atualize testes ao modificar regras de negócio.
- Garanta que todos os testes existentes continuem passando após as alterações.
- Não remova testes apenas para fazer o build passar.
- Não utilize mocks incorretos para comportamentos críticos de negócio.
- Cubra cenários de erro e casos de borda quando relevante.
- Mantenha os testes determinísticos e fáceis de manter.

---

## Performance

- Evite re-renderizações, loops ou cálculos desnecessários.
- Evite queries N+1 e acessos ineficientes ao banco de dados.
- Busque apenas os dados necessários para cada operação.
- Prefira soluções leves, eficientes e escaláveis.
- Considere impacto de performance e escalabilidade antes de introduzir novas lógicas.

---

## Regras de Frontend

- Preserve o comportamento responsivo da aplicação.
- Mantenha acessibilidade e HTML semântico.
- Reutilize componentes e padrões visuais já existentes.
- Evite complexidade desnecessária no gerenciamento de estado.
- Não introduza regressões de acessibilidade.
- Evite estilos inline, salvo quando realmente necessário.

---

## Regras de Backend

- Não introduza mudanças quebrando APIs sem aviso prévio.
- Preserve compatibilidade retroativa sempre que possível.
- Respeite os limites entre serviços e fluxos de validação existentes.
- Siga os padrões REST, gRPC e OpenAPI definidos no projeto.
- Centralize regras de negócio de forma consistente.

---

## Regras de Banco de Dados

- Nunca delete ou sobrescreva dados de produção.
- Não execute migrations destrutivas automaticamente.
- Prefira alterações de schema aditivas e compatíveis retroativamente.
- Evite mudanças desnecessárias na estrutura do banco.
- Preserve integridade e segurança dos dados.

---

## Git e Versionamento

- Não modifique o histórico do Git.
- Não utilize force push.
- Não crie commits automaticamente.
- Não altere pipelines de CI/CD sem aprovação explícita.
- Mantenha alterações limitadas ao escopo solicitado.
- Evite refatorações não relacionadas em pull requests.
- Utilize mensagens de commit claras e padronizadas.

---

## Comunicação

- Solicite esclarecimentos quando os requisitos estiverem ambíguos.
- Comunique claramente premissas, riscos e trade-offs.
- Não afirme que algo foi testado sem realmente ter sido.
- Não apresente suposições como fatos.
- Informe explicitamente limitações ou incertezas quando necessário.
- Explique decisões arquiteturais importantes antes da implementação.

---

## Documentação

- Atualize a documentação sempre que houver mudanças de comportamento ou APIs.
- Mantenha a documentação alinhada com a implementação atual.
- Adicione comentários apenas quando a intenção não estiver clara pelo código.
- Prefira código autoexplicativo em vez de comentários excessivos.

---

## Erros Comuns de IA que Devem Ser Evitados

- Não gerar implementações falsas ou APIs inexistentes.
- Não assumir que bibliotecas, funções ou serviços existem sem validação.
- Não ignorar silenciosamente erros ou casos extremos.
- Não simplificar regras de negócio sem compreender o domínio.
- Não substituir implementações existentes sem comparar comportamentos.
- Não introduzir efeitos colaterais ocultos.
- Não alterar arquivos de configuração de ambiente sem explicação.
- Não otimizar prematuramente sem evidências reais.
- Não priorizar redução de código em detrimento de legibilidade e manutenção.

---

## Código Morto Introduzido Pela Alteração

- Remova qualquer código morto introduzido durante a implementação da tarefa.
- Não deixe imports, variáveis, funções, componentes ou estados sem uso criados pela alteração.
- Não mantenha implementações antigas após substituí-las por novas versões.
- Sempre limpe código temporário, logs de debug e experimentações antes de finalizar.
- Ao alterar uma implementação existente, remova imediatamente o código que se tornou obsoleto devido à mudança.
- Não preserve código redundante criado durante refatorações ou ajustes.
- Garanta que toda alteração deixe a base de código mais consistente do que antes da implementação.

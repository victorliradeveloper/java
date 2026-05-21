# Preparação do commit

## Mensagem sugerida

```
feat: add email notifications for todo events
```

### Versão com corpo (opcional, se quiser mais detalhe no log)

```
feat: add email notifications for todo events

- notification-service: consome TodoEvent via SQS e envia e-mail HTML
- template Thymeleaf com header colorido por ação (CREATED/UPDATED/DELETED)
- credenciais SMTP + destinatário vêm do .env (gitignored)
- @ConfigurationProperties para notification.mail.{from,to}
- EmailDeliveryException dedicada para falhas de envio
- @SqsListener reutiliza constantes de SqsConfig
- adiciona .env.example e docs/sqs-template-send.md
```

---

## Arquivos a commitar

### Modificados (`M`)

| Arquivo                                                                                              | O que mudou                                                            |
| ---------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------- |
| `.gitignore`                                                                                         | Ignora `.env`                                                          |
| `docker-compose.yml`                                                                                 | Env vars `SPRING_MAIL_*` e `NOTIFICATION_MAIL_*` do `.env`             |
| `notification-service/pom.xml`                                                                       | + `spring-boot-starter-mail`, `spring-boot-starter-thymeleaf`, `spring-boot-configuration-processor` |
| `notification-service/src/main/java/com/microservices/notification/NotificationServiceApplication.java` | `@EnableConfigurationProperties(NotificationMailProperties.class)`     |
| `notification-service/src/main/java/com/microservices/notification/listener/TodoEventListener.java`  | Injeta `EmailService`; usa `SqsConfig.QUEUE_*` em vez de strings       |
| `notification-service/src/main/resources/application.yml`                                            | Bloco `spring.mail.*` + `notification.mail.*` (fail-fast nas env vars) |

### Novos (`??`)

| Arquivo                                                                                                       | O que é                                              |
| ------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------- |
| `.env.example`                                                                                                | Template das vars de ambiente (sem segredo)          |
| `docs/sqs-template-send.md`                                                                                   | Doc dos overloads de `SqsTemplate.send`              |
| `notification-service/src/main/java/com/microservices/notification/config/NotificationMailProperties.java`    | Record `@ConfigurationProperties("notification.mail")` |
| `notification-service/src/main/java/com/microservices/notification/exception/EmailDeliveryException.java`     | Exception dedicada                                   |
| `notification-service/src/main/java/com/microservices/notification/service/EmailService.java`                 | Serviço que monta e envia o e-mail HTML              |
| `notification-service/src/main/resources/templates/email/todo-event.html`                                     | Template Thymeleaf do e-mail                         |

---

## ⚠️ NÃO commitar

| Arquivo  | Razão                                                                              |
| -------- | ---------------------------------------------------------------------------------- |
| `.env`   | Contém App Password real do Gmail. Já está no `.gitignore`, então git não vai pegar — mas confirme com `git status` antes do `git add`. |

---

## Comandos prontos

Adicionar todos os arquivos relevantes (especificando, não com `git add .`):

```powershell
git add `
  .gitignore `
  docker-compose.yml `
  .env.example `
  docs/sqs-template-send.md `
  notification-service/pom.xml `
  notification-service/src/main/java/com/microservices/notification/NotificationServiceApplication.java `
  notification-service/src/main/java/com/microservices/notification/config/NotificationMailProperties.java `
  notification-service/src/main/java/com/microservices/notification/exception/EmailDeliveryException.java `
  notification-service/src/main/java/com/microservices/notification/listener/TodoEventListener.java `
  notification-service/src/main/java/com/microservices/notification/service/EmailService.java `
  notification-service/src/main/resources/application.yml `
  notification-service/src/main/resources/templates/email/todo-event.html
```

Conferir antes do commit:

```powershell
git status
git diff --cached --stat
```

Commit (versão curta):

```powershell
git commit -m "feat: add email notifications for todo events"
```

Commit (versão com corpo) — use heredoc no Git Bash, ou `-m` múltiplos no PowerShell:

```powershell
git commit -m "feat: add email notifications for todo events" `
           -m "- notification-service consome TodoEvent via SQS e envia e-mail HTML" `
           -m "- template Thymeleaf com header colorido por ação (CREATED/UPDATED/DELETED)" `
           -m "- credenciais SMTP + destinatário vêm do .env (gitignored)" `
           -m "- @ConfigurationProperties para notification.mail.{from,to}" `
           -m "- EmailDeliveryException dedicada para falhas de envio" `
           -m "- @SqsListener reutiliza constantes de SqsConfig" `
           -m "- adiciona .env.example e docs/sqs-template-send.md"
```

---

## Alternativa: dois commits separados

Se preferir histórico mais granular:

1. `feat: add email notifications for todo events` — todos os arquivos exceto `docs/sqs-template-send.md`
2. `docs: add sqs-template-send reference` — só `docs/sqs-template-send.md`

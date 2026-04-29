# Download Manager — Serviço de Downloads Assíncronos

Serviço backend que gerencia downloads de forma assíncrona, com controle de concorrência, rastreamento de progresso em tempo real e API REST.

---

## Stack

- Java 25
- Spring Boot 3.2
- Spring Web
- ExecutorService (controle manual de threads)
- ConcurrentHashMap (estado thread-safe)
- AtomicInteger / AtomicReference

---

## Estrutura do projeto

```
threads/
├── pom.xml
└── src/main/java/com/example/downloadmanager/
    ├── DownloadManagerApplication.java
    ├── config/
    │   └── ThreadPoolConfig.java
    ├── controller/
    │   └── DownloadController.java
    ├── service/
    │   └── DownloadService.java
    ├── task/
    │   └── DownloadTask.java
    ├── manager/
    │   └── DownloadManager.java
    ├── model/
    │   ├── DownloadStatus.java
    │   └── DownloadStatusEnum.java
    └── dto/
        ├── StartDownloadRequest.java
        ├── StartDownloadResponse.java
        └── DownloadStatusResponse.java
```

---

## Arquitetura em camadas

```
Cliente HTTP
     │
     ▼
DownloadController       ← recebe e responde requisições REST
     │
     ▼
DownloadService          ← orquestra: gera ID, registra, submete tarefa
     │
     ├──► DownloadManager   ← estado central (ConcurrentHashMap)
     │
     └──► ExecutorService   ← pool de threads fixo
               │
               ▼
         DownloadTask       ← executa o download, atualiza progresso
               │
               ▼
         DownloadManager    ← salva progresso em tempo real
```

---

## Componentes explicados

### DownloadManagerApplication
Ponto de entrada da aplicação Spring Boot. Sem configurações extras.

---

### ThreadPoolConfig
Define o `ExecutorService` como um bean Spring com pool fixo de threads.

```java
Executors.newFixedThreadPool(5)
```

**Por que pool fixo?**
Evita criar threads sem limite. Se 20 downloads chegarem ao mesmo tempo, apenas 5 rodam em paralelo — os outros esperam na fila do executor.

O `destroyMethod = "shutdown"` garante que o Spring encerra o pool corretamente ao parar a aplicação.

```properties
# application.properties
download.thread-pool.size=5
```

Você pode mudar o tamanho do pool sem mexer no código.

---

### DownloadStatus (modelo de domínio)
Representa o estado de um download em execução.

```java
private final AtomicInteger progress;
private final AtomicReference<DownloadStatusEnum> status;
```

**Por que Atomic?**
O objeto `DownloadStatus` é compartilhado entre duas threads:
- a thread do executor (que escreve o progresso)
- a thread do servidor HTTP (que lê para responder ao cliente)

`AtomicInteger` e `AtomicReference` garantem que leitura e escrita são seguras sem precisar de `synchronized`.

---

### DownloadStatusEnum
```
STARTED   → download registrado, ainda não começou a rodar
RUNNING   → progresso entre 1% e 99%
COMPLETED → progresso chegou a 100%
FAILED    → thread foi interrompida
```

---

### DownloadManager
Repositório em memória. Armazena todos os downloads ativos.

```java
ConcurrentHashMap<String, DownloadStatus> downloads
```

**Por que ConcurrentHashMap?**
Múltiplas threads podem ler e escrever ao mesmo tempo sem travar o mapa inteiro. Diferente de `HashMap` (não thread-safe) ou `Collections.synchronizedMap` (trava tudo).

---

### DownloadTask (Runnable)
A lógica do download roda aqui. Simula um download em 10 chunks de 10%, com 500ms de pausa entre cada um.

```
0% → 10% → 20% → ... → 100%  (cada passo: 500ms)
Total: ~5 segundos por download
```

A cada passo, chama `status.updateProgress(progress)` — que atualiza o `DownloadStatus` no manager em tempo real.

Se a thread for interrompida (ex: shutdown da aplicação), o status é marcado como `FAILED`.

---

### DownloadService
Orquestra tudo:

1. Gera um `UUID` único como ID do download
2. Cria o `DownloadStatus` e registra no `DownloadManager`
3. Submete um `DownloadTask` para o `ExecutorService`
4. Retorna o status inicial imediatamente (sem bloquear)

O cliente recebe resposta na hora — o download roda em background.

---

### DownloadController
Expõe a API REST. Não contém lógica de negócio.

---

### DTOs
Camada de separação entre domínio e API.

`DownloadStatus` usa `AtomicInteger` internamente — se fosse serializado direto pelo Jackson, o JSON ficaria errado. Os DTOs fazem o mapeamento correto:

```java
DownloadStatusResponse.from(downloadStatus)
// extrai .getProgress() e .getStatus() → tipos simples (int, enum)
```

---

## API REST

### Iniciar um download
```
POST /downloads
Content-Type: application/json

{
  "fileName": "video.mp4"
}
```

Resposta `202 Accepted`:
```json
{
  "id": "a3f1c2d4-...",
  "fileName": "video.mp4",
  "status": "STARTED"
}
```

---

### Consultar progresso por ID
```
GET /downloads/{id}
```

Resposta `200 OK`:
```json
{
  "id": "a3f1c2d4-...",
  "fileName": "video.mp4",
  "progress": 60,
  "status": "RUNNING",
  "startedAt": "2026-04-27T10:30:00"
}
```

---

### Listar todos os downloads
```
GET /downloads
```

Retorna array com todos os downloads registrados na memória.

---

## Como rodar

```bash
mvn spring-boot:run
```

Ou via jar:
```bash
mvn package -DskipTests
java -jar target/download-manager-1.0.0.jar
```

Servidor disponível em: `http://localhost:8080`

---

## Fluxo completo

```
1. POST /downloads           → service gera ID, registra no manager, submete task
2. ExecutorService           → coloca task na fila do pool
3. Thread disponível         → pega a task e começa a executar
4. DownloadTask.run()        → atualiza progresso no DownloadStatus a cada 500ms
5. GET /downloads/{id}       → manager busca o DownloadStatus e retorna snapshot atual
```

---

## Conceitos de concorrência aplicados

| Conceito | Onde | Por que |
|---|---|---|
| `ExecutorService` (pool fixo) | `ThreadPoolConfig` | Controla quantas threads rodam em paralelo |
| `ConcurrentHashMap` | `DownloadManager` | Mapa thread-safe sem lock global |
| `AtomicInteger` | `DownloadStatus` | Atualização de progresso sem `synchronized` |
| `AtomicReference` | `DownloadStatus` | Troca de enum de forma atômica |
| `Runnable` | `DownloadTask` | Unidade de trabalho enviada ao executor |
| `UUID` | `DownloadService` | ID único gerado sem colisão entre threads |

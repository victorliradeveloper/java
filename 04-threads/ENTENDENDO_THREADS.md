# Entendendo Threads — Do zero ao projeto

## O que é uma Thread?

Imagine que você está em uma lanchonete. O único atendente recebe seu pedido, vai para a cozinha, prepara o lanche, volta e te entrega. Enquanto ele está fazendo o seu lanche, **ninguém mais é atendido**. Você precisa esperar o lanche inteiro ficar pronto para a próxima pessoa ser atendida.

Isso é um programa **sem threads** — ele faz uma coisa de cada vez, em sequência.

Agora imagine que a lanchonete contrata mais atendentes. Cada um cuida de um pedido ao mesmo tempo. Enquanto um faz o seu lanche, outro já está atendendo a próxima pessoa.

Isso é um programa **com threads** — várias tarefas acontecem ao mesmo tempo, em paralelo.

> **Thread** é uma "linha de execução" dentro do seu programa. Um programa pode ter várias threads rodando ao mesmo tempo, cada uma fazendo uma tarefa diferente.

---

## Por que usar Threads?

Sem threads, se um usuário pedir para baixar um arquivo de 1GB, o servidor ficaria **travado** esperando esse download terminar antes de responder qualquer outra requisição.

Com threads, cada download roda em paralelo — o servidor continua atendendo outros usuários normalmente enquanto os downloads acontecem em segundo plano.

---

## O problema das Threads: dados compartilhados

Imagine dois atendentes tentando escrever no **mesmo bloco de pedidos** ao mesmo tempo. Um pode sobrescrever o que o outro escreveu e o pedido fica errado.

Com threads acontece o mesmo: quando duas threads tentam ler e escrever a mesma variável ao mesmo tempo, os dados podem ser corrompidos. Isso se chama **condição de corrida** (race condition).

Para evitar isso, o Java oferece ferramentas **thread-safe** — estruturas de dados feitas especialmente para funcionar com segurança em ambientes com múltiplas threads.

---

## Como esse projeto usa Threads

Este projeto é um gerenciador de downloads assíncrono. Quando você dispara um download via API, ele não trava o servidor esperando terminar — ele delega o trabalho para uma thread e responde imediatamente.

---

## Os arquivos e o papel de cada um

### `ThreadPoolConfig.java` — A equipe de atendentes

```java
Executors.newFixedThreadPool(5)
```

Aqui é criado um **pool de threads** com 5 threads fixas.

Um pool é como contratar 5 atendentes de uma vez. Eles ficam esperando trabalho chegar. Quando um download é solicitado, um atendente livre pega a tarefa. Se todos os 5 estiverem ocupados, o próximo download **espera na fila** até um ficar livre.

Isso é muito mais eficiente do que criar uma thread nova do zero a cada download (seria como contratar e demitir um atendente para cada pedido).

---

### `DownloadTask.java` — O que cada atendente faz

```java
public class DownloadTask implements Runnable {
    public void run() { ... }
}
```

Esta classe implementa `Runnable`, que é a interface do Java que diz: *"esse objeto pode ser executado por uma thread"*. O método `run()` é o trabalho que a thread vai executar.

Dentro do `run()`, o download é simulado assim:

```
progresso 10% → espera 500ms
progresso 20% → espera 500ms
progresso 30% → espera 500ms
...
progresso 100% → COMPLETED
```

O `Thread.sleep(500)` simula o tempo que um download real levaria para transferir dados. A thread "dorme" por 500ms entre cada etapa, sem bloquear as outras threads.

Repare nessa linha:
```java
log.info("[{}] ...", Thread.currentThread().getName(), ...)
```

Ela imprime o nome da thread atual — por exemplo `pool-1-thread-3` — mostrando exatamente qual atendente está cuidando de qual download.

Se a thread for interrompida no meio do processo:
```java
} catch (InterruptedException e) {
    Thread.currentThread().interrupt(); // avisa que foi interrompida
    status.markFailed();               // marca o download como falho
}
```

---

### `DownloadManager.java` — O quadro compartilhado entre todos

```java
private final ConcurrentHashMap<String, DownloadStatus> downloads = new ConcurrentHashMap<>();
```

Este é o "quadro de pedidos" que todos os atendentes consultam e atualizam. Como múltiplas threads acessam ele ao mesmo tempo, é usado `ConcurrentHashMap` em vez de um `HashMap` comum.

| | HashMap | ConcurrentHashMap |
|---|---|---|
| Thread-safe? | Não | Sim |
| Risco de corrida? | Sim | Não |
| Uso correto aqui? | Não | Sim |

---

### `DownloadStatus.java` — O estado de cada download

```java
private final AtomicInteger progress;
private final AtomicReference<DownloadStatusEnum> status;
```

Os campos `progress` e `status` usam tipos **Atomic**. Isso garante que quando uma thread atualiza o progresso e outra thread lê esse valor ao mesmo tempo, não haverá dados corrompidos.

Pense assim: uma operação atômica é como um cofre com uma única chave — enquanto alguém está usando, ninguém mais consegue entrar.

---

### `DownloadService.java` — O gerente que coordena tudo

```java
public DownloadStatus start(String fileName) {
    String id = UUID.randomUUID().toString();   // gera um ID único
    DownloadStatus status = new DownloadStatus(id, fileName);

    downloadManager.register(status);           // registra no quadro
    executor.submit(new DownloadTask(id, downloadManager)); // envia para uma thread

    return status;                              // responde imediatamente
}
```

O ponto mais importante está na última linha: `return status` é chamado **antes** do download terminar. O servidor responde ao cliente na hora, e o download continua rodando em paralelo numa thread separada.

Isso é o que chamamos de **processamento assíncrono**.

---

## O fluxo completo passo a passo

```
1. Você faz POST /downloads
         │
         ▼
2. DownloadService gera um ID único e cria o DownloadStatus
         │
         ▼
3. DownloadManager registra o status no ConcurrentHashMap
         │
         ▼
4. executor.submit() coloca o DownloadTask na fila do pool
         │
         ▼
5. Uma das 5 threads livres pega a tarefa e começa a rodar
         │
         ▼
6. Servidor já respondeu "202 ACCEPTED" — você não espera!
         │
         ▼
7. Em paralelo, a thread vai atualizando o progresso:
         10% → 20% → 30% → ... → 100% (COMPLETED)
         │
         ▼
8. Você consulta GET /downloads/{id} e vê o progresso atual
```

---

## Resumo visual

| Conceito | Analogia | No projeto |
|---|---|---|
| Thread | Atendente da lanchonete | Linha de execução que processa um download |
| Thread Pool | Equipe de 5 atendentes | `Executors.newFixedThreadPool(5)` |
| Runnable | A função do atendente | `DownloadTask implements Runnable` |
| ConcurrentHashMap | Quadro de pedidos compartilhado | `DownloadManager` |
| AtomicInteger | Contador sem conflito entre threads | Progresso do download |
| Assíncrono | Você senta enquanto o lanche é preparado | API responde imediatamente, download roda em background |

---

## O que acontece se chegarem 10 downloads ao mesmo tempo?

O pool tem 5 threads. Os primeiros 5 downloads começam imediatamente, cada um em uma thread diferente. Os outros 5 ficam **na fila** do `ExecutorService`. Conforme as threads terminam um download, elas pegam automaticamente o próximo da fila.

```
Downloads 1-5  →  rodam imediatamente nas 5 threads
Downloads 6-10 →  ficam na fila, aguardando uma thread ficar livre
```

Isso protege o servidor de ser sobrecarregado com infinitas threads ao mesmo tempo.

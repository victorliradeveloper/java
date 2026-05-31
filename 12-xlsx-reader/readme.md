# **Projeto — Importador de Planilha Excel (Catálogo de Fornecedor)**

> API REST em Java 21 + Spring Boot 3 que importa catálogo de produtos via planilha `.xlsx` do fornecedor, valida, grava no banco e gera relatório de erros em CSV. Usa Apache POI em modo streaming.

---

## **Por que este projeto**

É o caso de uso mais comum em **e-commerce B2B brasileiro**: o fornecedor manda toda semana uma `.xlsx` com produtos, preços e estoque. O sistema importa, valida, grava no banco e devolve um relatório de erros.

Wine, Magalu, MELI, Centauro, Renner — todos têm time inteiro mantendo importadores de Excel.

---

## **O que o projeto faz**

```
fornecedor envia "catalogo-semana-20.xlsx"
        ↓
POST /catalogos/importacoes  (multipart/form-data)
        ↓
InputStream do upload → Apache POI lê linha a linha
        ↓
valida (SKU duplicado? preço negativo? campo faltando?)
        ↓
grava produtos válidos no banco (JPA)
        ↓
gera "erros-20240514.csv" com linhas rejeitadas (Writer)
        ↓
GET /catalogos/importacoes/{id}/erros    → download do CSV
GET /catalogos/exportacoes/produtos.xlsx → exporta catálogo atual
```

---

## **Stack**

| Tecnologia | Versão | Por quê |
| --- | --- | --- |
| Java | 21 | Records, streams funcionais, virtual threads |
| Spring Boot | 3.4.x | Suporte nativo a Java 21 |
| Spring Web | — | REST + multipart upload + StreamingResponseBody |
| Spring Data JPA | — | Persistência |
| Apache POI | 5.3.0 | Leitura/escrita de XLSX — licença Apache 2.0 |
| PostgreSQL | 16 | Banco principal |
| H2 | — | Testes |
| Maven | 3.9+ | Build |

---

## **Conceitos de I/O que cobre**

| Conceito | Onde aparece |
| --- | --- |
| `InputStream` | `MultipartFile.getInputStream()` do upload |
| Apache POI streaming (`SXSSFWorkbook` / SAX) | Ler XLSX de 100k linhas sem estourar heap |
| `BufferedWriter` + `PrintWriter` | Gerar CSV de erros |
| **BOM UTF-8** (`﻿`) | Sem isso, Excel abre o CSV com acentos quebrados |
| `OutputStreamWriter` com charset explícito | Garantir UTF-8 no CSV |
| `OutputStream` | Download do XLSX exportado |
| `try-with-resources` em cadeia | `InputStream → Workbook → Sheet → Row` |
| Idempotência | Reimportar a mesma planilha sem duplicar produtos |

---

## **Dependências no `pom.xml`**

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>org.apache.poi</groupId>
        <artifactId>poi-ooxml</artifactId>
        <version>5.3.0</version>
    </dependency>
    <dependency>
        <groupId>com.h2database</groupId>
        <artifactId>h2</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

POI tem **dois modos** — aprender a diferença é gold em entrevista:

| Modo | Quando usar |
| --- | --- |
| `XSSFWorkbook` (DOM) | Planilhas pequenas — carrega tudo na memória |
| `SXSSFWorkbook` / SAX streaming | Planilhas grandes — lê linha a linha, libera memória |

---

## **Domínio — o que vamos importar**

O sistema gerencia **produtos** de um catálogo. Cada produto tem SKU, nome, preço, estoque e categoria.

```
Planilha do fornecedor — "catalogo-semana-20.xlsx"
┌──────────┬────────────────────┬────────────┬─────────┬──────────────┐
│ SKU      │ Nome               │ Preço      │ Estoque │ Categoria    │
├──────────┼────────────────────┼────────────┼─────────┼──────────────┤
│ VIN-001  │ Malbec Reserva     │ R$ 89,90   │ 120     │ Vinho Tinto  │
│ VIN-002  │ Chardonnay 2022    │ R$ 65,00   │ 85      │ Vinho Branco │
│ VIN-003  │ Espumante Brut     │ R$ 110,00  │ 40      │ Espumante    │
└──────────┴────────────────────┴────────────┴─────────┴──────────────┘
```

---

## **Arquitetura Hexagonal**

```
adapter/in/rest
    ├── CatalogoController          ← POST /catalogos/importacoes (multipart)
    └── ExportacaoController        ← GET /catalogos/produtos.xlsx

application/
    ├── ImportarCatalogoService     ← orquestra: lê planilha + valida + grava
    └── ExportarCatalogoService     ← orquestra: busca produtos + gera XLSX

domain/
    ├── model/
    │   ├── Produto.java
    │   ├── Importacao.java
    │   └── LinhaInvalida.java
    └── port/
        ├── in/
        │   ├── ImportarCatalogoUseCase.java
        │   └── ExportarCatalogoUseCase.java
        └── out/
            ├── ProdutoRepository.java
            ├── PlanilhaLeitor.java          ← porta para POI (leitura)
            ├── PlanilhaEscritor.java        ← porta para POI (escrita)
            └── RelatorioErroEscritor.java   ← porta para CSV

adapter/out/
    ├── persistence/
    │   ├── ProdutoEntity.java
    │   ├── ProdutoJpaRepository.java
    │   └── ProdutoRepositoryAdapter.java
    ├── excel/
    │   ├── PoiXlsxLeitor.java       ← InputStream + SAX streaming
    │   └── PoiXlsxEscritor.java     ← OutputStream + SXSSF
    └── csv/
        └── CsvErroEscritor.java     ← BufferedWriter + BOM UTF-8
```

---

## **Estrutura de pacotes**

```
src/main/java/com/exemplo/catalogo/
│
├── domain/
│   ├── model/
│   │   ├── Produto.java
│   │   ├── Importacao.java
│   │   └── LinhaInvalida.java
│   └── port/
│       ├── in/
│       │   ├── ImportarCatalogoUseCase.java
│       │   └── ExportarCatalogoUseCase.java
│       └── out/
│           ├── ProdutoRepository.java
│           ├── PlanilhaLeitor.java
│           ├── PlanilhaEscritor.java
│           └── RelatorioErroEscritor.java
│
├── application/
│   ├── ImportarCatalogoService.java
│   └── ExportarCatalogoService.java
│
└── adapter/
    ├── in/
    │   └── rest/
    │       ├── CatalogoController.java
    │       └── ExportacaoController.java
    └── out/
        ├── persistence/
        │   ├── ProdutoEntity.java
        │   ├── ProdutoJpaRepository.java
        │   └── ProdutoRepositoryAdapter.java
        ├── excel/
        │   ├── PoiXlsxLeitor.java
        │   └── PoiXlsxEscritor.java
        └── csv/
            └── CsvErroEscritor.java
```

---

## **Domain**

### **`Produto.java`**

```java
public class Produto {
    private String sku;
    private String nome;
    private BigDecimal preco;
    private Integer estoque;
    private String categoria;

    // construtor, getters
}
```

### **`LinhaInvalida.java`**

```java
public class LinhaInvalida {
    private int numeroLinha;
    private String motivo;
    private String dadosOriginais;

    // construtor, getters
}
```

### **`Importacao.java`**

```java
public class Importacao {
    private Long id;
    private LocalDateTime dataInicio;
    private LocalDateTime dataFim;
    private int totalLinhas;
    private List<LinhaInvalida> erros;

    public int totalErros() {
        return erros.size();
    }

    // construtor, getters
}
```

### **`ImportarCatalogoUseCase.java`**

```java
public interface ImportarCatalogoUseCase {
    Importacao importar(InputStream planilha) throws IOException;
}
```

### **`PlanilhaLeitor.java`**

```java
public interface PlanilhaLeitor {
    Stream<LinhaPlanilha> ler(InputStream entrada) throws IOException;
}
```

### **`RelatorioErroEscritor.java`**

```java
public interface RelatorioErroEscritor {
    void escrever(List<LinhaInvalida> erros, OutputStream destino) throws IOException;
}
```

---

## **Application**

### **`ImportarCatalogoService.java`**

```java
@Service
public class ImportarCatalogoService implements ImportarCatalogoUseCase {

    private final PlanilhaLeitor leitor;
    private final ProdutoRepository produtoRepository;

    public ImportarCatalogoService(PlanilhaLeitor leitor,
                                    ProdutoRepository produtoRepository) {
        this.leitor = leitor;
        this.produtoRepository = produtoRepository;
    }

    @Override
    public Importacao importar(InputStream planilha) throws IOException {
        List<LinhaInvalida> erros = new ArrayList<>();
        List<Produto> validos = new ArrayList<>();

        try (Stream<LinhaPlanilha> linhas = leitor.ler(planilha)) {
            linhas.forEach(linha -> {
                try {
                    validos.add(linha.paraProduto());
                } catch (ValidacaoException e) {
                    erros.add(new LinhaInvalida(
                        linha.numero(), e.getMessage(), linha.bruto()));
                }
            });
        }

        produtoRepository.salvarTodos(validos);
        return new Importacao(LocalDateTime.now(),
                              validos.size() + erros.size(), erros);
    }
}
```

---

## **O coração do projeto — `PoiXlsxLeitor.java`**

Aqui mora o I/O real. POI em modo streaming lê o XLSX direto do `InputStream` do upload, **sem carregar a planilha inteira na memória**.

```java
@Component
public class PoiXlsxLeitor implements PlanilhaLeitor {

    @Override
    public Stream<LinhaPlanilha> ler(InputStream entrada) throws IOException {
        // SXSSFWorkbook NÃO é para leitura — é para escrita streaming.
        // Para leitura streaming usa-se OPCPackage + XSSFReader + SAX.
        // Para didática, exemplo com XSSFWorkbook (DOM) abaixo:

        Workbook workbook = new XSSFWorkbook(entrada);
        Sheet sheet = workbook.getSheetAt(0);

        Spliterator<Row> spliterator = Spliterators.spliteratorUnknownSize(
                sheet.rowIterator(), Spliterator.ORDERED);

        return StreamSupport.stream(spliterator, false)
                .skip(1) // pula cabeçalho
                .map(this::mapearLinha)
                .onClose(() -> {
                    try { workbook.close(); }
                    catch (IOException e) { throw new RuntimeException(e); }
                });
    }

    private LinhaPlanilha mapearLinha(Row row) {
        return new LinhaPlanilha(
            row.getRowNum() + 1,
            getString(row, 0),   // SKU
            getString(row, 1),   // Nome
            getDecimal(row, 2),  // Preço
            getInt(row, 3),      // Estoque
            getString(row, 4)    // Categoria
        );
    }

    private String getString(Row row, int col) {
        Cell cell = row.getCell(col);
        return cell == null ? "" : cell.getStringCellValue().trim();
    }

    private BigDecimal getDecimal(Row row, int col) {
        Cell cell = row.getCell(col);
        return cell == null ? null : BigDecimal.valueOf(cell.getNumericCellValue());
    }

    private Integer getInt(Row row, int col) {
        Cell cell = row.getCell(col);
        return cell == null ? null : (int) cell.getNumericCellValue();
    }
}
```

---

## **Escritor de CSV de erros — `CsvErroEscritor.java`**

Aqui aparece o **BOM UTF-8** — sem ele, o Excel abre o CSV com acentos quebrados.

```java
@Component
public class CsvErroEscritor implements RelatorioErroEscritor {

    private static final String BOM = "﻿";

    @Override
    public void escrever(List<LinhaInvalida> erros, OutputStream destino)
            throws IOException {

        try (Writer w = new OutputStreamWriter(destino, StandardCharsets.UTF_8);
             BufferedWriter bw = new BufferedWriter(w);
             PrintWriter pw = new PrintWriter(bw)) {

            pw.print(BOM); // ← Excel precisa disso para reconhecer UTF-8
            pw.println("linha;motivo;dados");

            for (LinhaInvalida erro : erros) {
                pw.printf("%d;%s;%s%n",
                    erro.getNumeroLinha(),
                    erro.getMotivo(),
                    erro.getDadosOriginais());
            }
            // pw.flush() é chamado automaticamente no close()
        }
    }
}
```

---

## **Adapter de entrada — REST**

### **`CatalogoController.java`**

```java
@RestController
@RequestMapping("/catalogos")
public class CatalogoController {

    private final ImportarCatalogoUseCase importar;
    private final RelatorioErroEscritor erroEscritor;

    public CatalogoController(ImportarCatalogoUseCase importar,
                              RelatorioErroEscritor erroEscritor) {
        this.importar = importar;
        this.erroEscritor = erroEscritor;
    }

    @PostMapping("/importacoes")
    public ResponseEntity<ImportacaoResponse> importar(
            @RequestParam("arquivo") MultipartFile arquivo) throws IOException {

        try (InputStream in = arquivo.getInputStream()) {
            Importacao resultado = importar.importar(in);
            return ResponseEntity.ok(ImportacaoResponse.from(resultado));
        }
    }

    @GetMapping("/importacoes/{id}/erros")
    public ResponseEntity<StreamingResponseBody> baixarErros(@PathVariable Long id) {

        List<LinhaInvalida> erros = /* buscar por id */;

        StreamingResponseBody body = out -> erroEscritor.escrever(erros, out);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=erros-" + id + ".csv")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(body);
    }
}
```

---

## **Fluxo completo de I/O**

```
cliente envia multipart  →  MultipartFile.getInputStream()
                                      ↓
                              PoiXlsxLeitor.ler()
                                      ↓
                          Stream<LinhaPlanilha>  ← lazy, não carrega tudo
                                      ↓
                          valida + acumula erros
                                      ↓
                          produtoRepository.salvarTodos()
                                      ↓
                          Importacao (com id e lista de erros)
                                      ↓
                          GET /erros → CsvErroEscritor
                                      ↓
                  OutputStreamWriter(UTF-8) → BufferedWriter → PrintWriter
                                      ↓
                          StreamingResponseBody  ← Spring gerencia flush
                                      ↓
                          cliente baixa "erros-42.csv" com BOM UTF-8
```

Sem arquivo temporário. Sem carregar a planilha inteira na memória do servidor.

---

## **Configuração**

### **`application.yml`**

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/catalogo_db
    username: postgres
    password: postgres
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 50MB
  threads:
    virtual:
      enabled: true
```

---

## **Testando**

```bash
# importar planilha
curl -X POST http://localhost:8080/catalogos/importacoes \
     -F "arquivo=@catalogo-semana-20.xlsx"

# baixar relatório de erros
curl http://localhost:8080/catalogos/importacoes/1/erros -o erros.csv

# exportar catálogo atual
curl http://localhost:8080/catalogos/produtos.xlsx -o produtos.xlsx
```

---

## **Ordem de implementação**

1. Crie o projeto em [start.spring.io](https://start.spring.io/) com Web, JPA, PostgreSQL
2. Adicione a dependência do POI manualmente no `pom.xml`
3. Suba o banco: `docker run -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=catalogo_db -p 5432:5432 postgres:16`
4. Implemente o domínio (`Produto`, `Importacao`, `LinhaInvalida`, interfaces de porta)
5. Implemente `PoiXlsxLeitor` com uma planilha local fixa (sem upload ainda) e teste lendo as linhas
6. Implemente `CsvErroEscritor` testando a saída com `FileOutputStream` local — abra o CSV no Excel para validar acentos
7. Implemente o adapter de persistência (JPA)
8. Implemente `ImportarCatalogoService` ligando os três
9. Implemente `CatalogoController` e teste o upload via curl
10. Implemente `PoiXlsxEscritor` (SXSSF) e o endpoint de exportação

> O passo 5 é crítico: testar a leitura isolada com uma planilha local antes de integrar com o upload multipart do Spring.

---

## **O que você vai aprender**

- Como o Apache POI usa `InputStream`/`OutputStream` — você controla de onde vem e para onde vai
- Diferença prática entre POI DOM (`XSSFWorkbook`) vs streaming (`SXSSFWorkbook` / SAX)
- Por que `new FileWriter("erros.csv")` quebra acento (charset default do SO)
- Como fazer Excel abrir CSV em UTF-8 corretamente (o tal do BOM `﻿`)
- `MultipartFile.getInputStream()` e por que **nunca** chamar `.getBytes()` em arquivo grande
- Validação em lote com acumulação de erros (não falhar na primeira linha ruim)
- Como devolver download em streaming via `StreamingResponseBody`
- Composição de streams: `OutputStream → OutputStreamWriter → BufferedWriter → PrintWriter`

---

## **Desafios extras**

- Adicionar exportação assíncrona: `POST /catalogos/exportacoes` retorna ID, e `GET /catalogos/exportacoes/{id}` baixa o arquivo quando pronto
- Suportar múltiplas abas (Sheets) na mesma planilha — uma para cada categoria
- Adicionar coluna calculada na exportação (ex: margem de lucro)
- Implementar idempotência: reimportar a mesma planilha não duplica produtos (chave: SKU)
- Migrar `PoiXlsxLeitor` de DOM para SAX streaming — testar com planilha de 100k linhas e medir memória
- Escrever teste unitário para `CsvErroEscritor` verificando que o BOM `﻿` está presente no início do `OutputStream`

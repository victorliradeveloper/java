# Troubleshooting

## Erro de conexão com MongoDB no Compass — "Authentication failed"

### Sintoma

Ao tentar conectar no MongoDB Compass usando a URL:

```
mongodb://root:mongo123@localhost:27017/?authSource=admin&directConnection=true
```

O Compass retornava o erro **Authentication failed**, mesmo com o container Docker rodando e saudável.

### Causa

O Windows tinha o **MongoDB instalado localmente como serviço do sistema**, ocupando a porta `27017`. Quando o Compass tentava conectar, ele batia no MongoDB local (sem autenticação configurada) e não no container Docker — por isso as credenciais eram rejeitadas.

Diagnóstico confirmado com:

```powershell
Get-Service -Name "MongoDB"
netstat -ano | findstr ":27017"
```

Resultado: serviço `MongoDB` com status `Running` e dois processos distintos escutando na porta `27017`.

### Solução

Alterar a porta exposta do container Docker de `27017` para `27020`, evitando o conflito com o serviço local.

**docker-compose.yml:**
```yaml
ports:
  - "27020:27017"
```

**application.properties:**
```properties
spring.data.mongodb.uri=mongodb://root:mongo123@localhost:27020/bookstore-mongo?authSource=admin
```

**URL de conexão no Compass:**
```
mongodb://root:mongo123@127.0.0.1:27020/?authSource=admin&directConnection=true
```

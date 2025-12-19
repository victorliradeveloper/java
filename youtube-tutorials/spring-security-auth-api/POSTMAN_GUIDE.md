# 📮 Guia de Uso - Postman Collection

Este guia explica como importar e usar a collection do Postman para testar a API de autenticação.

## 📥 Como Importar

### 1. Importar a Collection

1. Abra o Postman
2. Clique em **Import** (canto superior esquerdo)
3. Selecione o arquivo `Spring-Security-Auth-API.postman_collection.json`
4. Clique em **Import**

### 2. Importar o Environment (Opcional mas Recomendado)

1. Clique em **Import** novamente
2. Selecione o arquivo `Spring-Security-Auth-API.postman_environment.json`
3. Clique em **Import**
4. No canto superior direito, selecione o environment: **"Spring Security Auth API - Local"**

## 🚀 Como Usar

### Passo 1: Registrar um Usuário

1. Abra a pasta **Authentication**
2. Execute **"Register User"** ou **"Register Admin"**
3. Modifique os dados se necessário:
   ```json
   {
       "login": "seu_usuario",
       "password": "sua_senha",
       "role": "USER"  // ou "ADMIN"
   }
   ```
4. Clique em **Send**

### Passo 2: Fazer Login

1. Execute **"Login"** ou **"Login Admin"**
2. Use as mesmas credenciais do registro
3. Clique em **Send**
4. ✅ **O token será automaticamente salvo** na variável `token` ou `admin_token`

### Passo 3: Testar Endpoints Protegidos

#### Listar Produtos (qualquer usuário autenticado)
1. Execute **"Get All Products"**
2. O token será automaticamente incluído no header
3. Clique em **Send**

#### Criar Produto (apenas ADMIN)
1. Execute **"Create Product"**
2. Modifique os dados do produto se necessário:
   ```json
   {
       "name": "Nome do Produto",
       "price": 100
   }
   ```
3. Clique em **Send**
4. ✅ Deve funcionar se você fez login como ADMIN

#### Testar Autorização (USER tentando criar produto)
1. Execute **"Create Product (as USER - Should Fail)"**
2. Este endpoint usa o token de USER (não ADMIN)
3. Deve retornar **403 Forbidden**

## 🔑 Variáveis da Collection

A collection usa as seguintes variáveis:

- **`token`**: Token JWT do usuário comum (preenchido automaticamente após login)
- **`admin_token`**: Token JWT do admin (preenchido automaticamente após login como admin)

**Nota:** As URLs estão configuradas diretamente como `http://localhost:8080`. Se precisar mudar a porta, edite manualmente cada requisição.

## 📋 Endpoints Disponíveis

### Authentication
- ✅ **Register User** - Registra usuário comum
- ✅ **Register Admin** - Registra usuário admin
- ✅ **Login** - Login e recebe token (salva em `token`)
- ✅ **Login Admin** - Login como admin (salva em `admin_token`)

### Products
- ✅ **Get All Products** - Lista produtos (requer autenticação)
- ✅ **Create Product** - Cria produto (requer role ADMIN)
- ✅ **Create Product (as USER - Should Fail)** - Testa autorização

## 🧪 Testes Automáticos

A collection inclui testes automáticos que verificam:
- Status code das respostas
- Estrutura das respostas JSON
- Salvamento automático de tokens

Você pode ver os resultados dos testes na aba **Test Results** após enviar uma requisição.

## 💡 Dicas

1. **Ordem Recomendada:**
   - Primeiro: Registrar usuário
   - Segundo: Fazer login
   - Terceiro: Testar endpoints protegidos

2. **Tokens Expiram:**
   - Os tokens expiram em 2 horas
   - Se receber 403, faça login novamente

3. **Verificar Token:**
   - Vá em **Environments** → **Spring Security Auth API - Local**
   - Veja se `token` ou `admin_token` está preenchido

4. **Modificar URL:**
   - Se sua API estiver em outra porta, edite manualmente as URLs em cada requisição

## 🐛 Troubleshooting

**Problema: Token não está sendo salvo**
- Verifique se o environment está selecionado
- Verifique se o login retornou status 200
- Veja a aba **Test Results** para erros

**Problema: 403 Forbidden**
- Token pode ter expirado → Faça login novamente
- Verifique se está usando o token correto (USER vs ADMIN)

**Problema: Connection Refused**
- Verifique se a aplicação está rodando: `.\mvnw.cmd spring-boot:run`
- Verifique se o PostgreSQL está rodando: `docker-compose up -d`
- Verifique se a porta está correta (padrão: 8080)

## 📝 Exemplo de Fluxo Completo

```
1. Register Admin
   → Status: 200 OK

2. Login Admin
   → Status: 200 OK
   → Token salvo em admin_token

3. Create Product
   → Status: 200 OK
   → Produto criado com sucesso

4. Register User
   → Status: 200 OK

5. Login
   → Status: 200 OK
   → Token salvo em token

6. Get All Products
   → Status: 200 OK
   → Lista de produtos retornada

7. Create Product (as USER)
   → Status: 403 Forbidden
   → Acesso negado (esperado!)
```

---

**Pronto para testar!** 🚀


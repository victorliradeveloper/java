-- Inicializacao executada uma unica vez pelo entrypoint do postgres
-- (apenas quando o volume esta vazio). Cria databases adicionais por
-- servico, espelhando o padrao "um DB por microservico" do projeto 15.
--
-- O DB tododb ja eh criado pelo POSTGRES_DB do docker-compose.
-- Aqui criamos apenas os secundarios.

CREATE DATABASE notificationdb OWNER todo_user;
GRANT ALL PRIVILEGES ON DATABASE notificationdb TO todo_user;

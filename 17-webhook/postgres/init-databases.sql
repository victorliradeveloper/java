-- orderdb ja eh criado automaticamente via POSTGRES_DB no docker-compose.
-- Aqui criamos apenas as bases secundarias. Roda uma unica vez na primeira
-- inicializacao do volume (docker-entrypoint-initdb.d so executa quando o
-- data dir esta vazio).
CREATE DATABASE paymentdb;

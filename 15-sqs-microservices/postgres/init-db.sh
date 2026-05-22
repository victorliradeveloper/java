#!/bin/bash
# Cria o database e usuario do notification-service. O todo-service ja usa
# tododb/todo_user via POSTGRES_DB/POSTGRES_USER do container.
#
# Este script roda apenas no primeiro boot do volume postgres-data (padrao
# do docker-entrypoint do postgres). Se o volume ja existe e nao tem o
# database notificationdb, remova o volume com `docker-compose down -v`.
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE USER notification_user WITH PASSWORD 'notification_pass';
    CREATE DATABASE notificationdb OWNER notification_user;
    GRANT ALL PRIVILEGES ON DATABASE notificationdb TO notification_user;
EOSQL

#!/bin/bash
# Inicia o replica set rs0 com um unico no (suficiente para transacoes
# multi-documento em dev). Idempotente: se ja inicializado, retorna OK.
#
# Executado por um container one-shot (servico mongo-setup no docker-compose)
# apos o mongod estar respondendo ping.
set -e

echo "[init-replset] aguardando mongod responder..."
until mongosh --host mongo:27017 --quiet --eval "db.adminCommand('ping').ok" >/dev/null 2>&1; do
  sleep 2
done

STATUS=$(mongosh --host mongo:27017 --quiet --eval "try { rs.status().ok } catch (e) { 0 }")
if [ "$STATUS" = "1" ]; then
  echo "[init-replset] replica set ja iniciado, nada a fazer."
  exit 0
fi

echo "[init-replset] iniciando rs0..."
mongosh --host mongo:27017 --quiet --eval "
  rs.initiate({_id: 'rs0', members: [{_id: 0, host: 'mongo:27017'}]})
"

echo "[init-replset] aguardando primary..."
until [ "$(mongosh --host mongo:27017 --quiet --eval 'rs.status().myState')" = "1" ]; do
  sleep 1
done
echo "[init-replset] pronto."

#!/bin/bash
# Gera certificados mTLS para o payment-service
# Uso: chmod +x generate-certs.sh && ./generate-certs.sh

set -e

CERTS_DIR="./payment-service/src/main/resources/certs"
PASSWORD="changeit"

mkdir -p "$CERTS_DIR"
cd "$CERTS_DIR"

echo ">>> Gerando CA (Certificate Authority)..."
openssl genrsa -out ca.key 4096
openssl req -new -x509 -days 365 -key ca.key -out ca.crt \
  -subj "/CN=Ecommerce-CA/O=Ecommerce/C=BR"

echo ">>> Gerando certificado do servidor payment-service..."
openssl genrsa -out payment-server.key 2048
openssl req -new -key payment-server.key -out payment-server.csr \
  -subj "/CN=payment-service/O=Ecommerce/C=BR"
openssl x509 -req -days 365 \
  -in payment-server.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
  -out payment-server.crt

echo ">>> Gerando certificado de cliente para order-service..."
openssl genrsa -out order-client.key 2048
openssl req -new -key order-client.key -out order-client.csr \
  -subj "/CN=order-service/O=Ecommerce/C=BR"
openssl x509 -req -days 365 \
  -in order-client.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
  -out order-client.crt

echo ">>> Criando keystores PKCS12..."
# Keystore do payment-service (contém o certificado do servidor)
openssl pkcs12 -export \
  -in payment-server.crt -inkey payment-server.key \
  -out payment-keystore.p12 -name payment-service \
  -password pass:$PASSWORD

# Keystore do order-service (contém o certificado de cliente)
openssl pkcs12 -export \
  -in order-client.crt -inkey order-client.key \
  -out order-client-keystore.p12 -name order-service-client \
  -password pass:$PASSWORD

echo ">>> Criando truststores..."
# Truststore do payment-service (confia na CA — aceita clientes assinados por ela)
keytool -import -file ca.crt -alias ecommerce-ca \
  -keystore payment-truststore.p12 -storetype PKCS12 \
  -storepass $PASSWORD -noprompt

# Truststore do order-service (confia no certificado do payment-service)
keytool -import -file payment-server.crt -alias payment-service \
  -keystore order-truststore.p12 -storetype PKCS12 \
  -storepass $PASSWORD -noprompt

echo ""
echo "Certificados gerados em: $CERTS_DIR"
echo ""
echo "Ative o mTLS no payment-service com:"
echo "  SPRING_PROFILES_ACTIVE=mtls"
echo ""
echo "Ative o cliente mTLS no order-service com:"
echo "  SPRING_PROFILES_ACTIVE=mtls"

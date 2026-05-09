# Gera certificados mTLS para o payment-service (Windows)
# Requer: OpenSSL instalado (via Git Bash, Chocolatey ou WSL)
# Uso: .\generate-certs.ps1

$CertsDir = ".\payment-service\src\main\resources\certs"
$Password  = "changeit"

New-Item -ItemType Directory -Force -Path $CertsDir | Out-Null
Set-Location $CertsDir

Write-Host ">>> Gerando CA..."
openssl genrsa -out ca.key 4096
openssl req -new -x509 -days 365 -key ca.key -out ca.crt `
  -subj "/CN=Ecommerce-CA/O=Ecommerce/C=BR"

Write-Host ">>> Gerando certificado do payment-service..."
openssl genrsa -out payment-server.key 2048
openssl req -new -key payment-server.key -out payment-server.csr `
  -subj "/CN=payment-service/O=Ecommerce/C=BR"
openssl x509 -req -days 365 `
  -in payment-server.csr -CA ca.crt -CAkey ca.key -CAcreateserial `
  -out payment-server.crt

Write-Host ">>> Gerando certificado de cliente para order-service..."
openssl genrsa -out order-client.key 2048
openssl req -new -key order-client.key -out order-client.csr `
  -subj "/CN=order-service/O=Ecommerce/C=BR"
openssl x509 -req -days 365 `
  -in order-client.csr -CA ca.crt -CAkey ca.key -CAcreateserial `
  -out order-client.crt

Write-Host ">>> Criando keystores PKCS12..."
openssl pkcs12 -export `
  -in payment-server.crt -inkey payment-server.key `
  -out payment-keystore.p12 -name payment-service `
  -password "pass:$Password"

openssl pkcs12 -export `
  -in order-client.crt -inkey order-client.key `
  -out order-client-keystore.p12 -name order-service-client `
  -password "pass:$Password"

Write-Host ">>> Criando truststores..."
keytool -import -file ca.crt -alias ecommerce-ca `
  -keystore payment-truststore.p12 -storetype PKCS12 `
  -storepass $Password -noprompt

keytool -import -file payment-server.crt -alias payment-service `
  -keystore order-truststore.p12 -storetype PKCS12 `
  -storepass $Password -noprompt

Write-Host ""
Write-Host "Certificados gerados em: $CertsDir"
Write-Host "Ative o mTLS com: SPRING_PROFILES_ACTIVE=mtls"

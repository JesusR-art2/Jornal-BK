#!/bin/sh
# Certificado TLS autofirmado SOLO para desarrollo. Se genera una vez y se guarda en el volumen de certificados,
# así la excepción que acepta el navegador sigue sirviendo después de reiniciar. Producción usa certificados
# públicos por dominio regional (ver documento.md, «Simplificaciones»).
set -eu

DIR=/etc/nginx/certs
CERT="$DIR/jornal-dev.crt"
KEY="$DIR/jornal-dev.key"

if [ -s "$CERT" ] && [ -s "$KEY" ]; then
  echo "dev-certificate: usando $CERT"
  exit 0
fi

mkdir -p "$DIR"
openssl req -x509 -newkey rsa:2048 -nodes -days 825 \
  -keyout "$KEY" -out "$CERT" \
  -subj "/CN=jornal.localhost/O=Jornal (desarrollo)" \
  -addext "subjectAltName=DNS:localhost,DNS:jornal.localhost,DNS:*.jornal.localhost,IP:127.0.0.1" \
  -addext "keyUsage=digitalSignature,keyEncipherment" \
  -addext "extendedKeyUsage=serverAuth" 2> /dev/null
chmod 600 "$KEY"
echo "dev-certificate: certificado autofirmado nuevo en $CERT"

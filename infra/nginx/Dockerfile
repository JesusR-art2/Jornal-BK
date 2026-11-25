# Gateway de desarrollo: Nginx con un certificado TLS autofirmado que se genera al primer arranque.
FROM nginx:1.27-alpine
RUN apk add --no-cache openssl
COPY 40-dev-certificate.sh /docker-entrypoint.d/40-dev-certificate.sh
RUN chmod +x /docker-entrypoint.d/40-dev-certificate.sh

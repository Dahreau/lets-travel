FROM postgres:18-alpine

# Certificat genere au build (pas bind-mount) : Postgres refuse une cle aux permissions trop ouvertes.
RUN apk add --no-cache openssl \
    && mkdir -p /etc/postgresql/certs \
    && openssl req -x509 -newkey rsa:2048 -nodes \
       -keyout /etc/postgresql/certs/server.key \
       -out /etc/postgresql/certs/server.crt \
       -days 365 -subj /CN=postgres.local \
    && chown postgres:postgres /etc/postgresql/certs/server.key /etc/postgresql/certs/server.crt \
    && chmod 600 /etc/postgresql/certs/server.key \
    && chmod 644 /etc/postgresql/certs/server.crt

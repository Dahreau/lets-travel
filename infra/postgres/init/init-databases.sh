#!/bin/bash
set -e

create_db_and_user() {
	local db="$1"
	local user="$2"
	local password="$3"

	psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
		DO
		\$\$
		BEGIN
			IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '$user') THEN
				CREATE USER $user WITH PASSWORD '$password';
			END IF;
		END
		\$\$;
	EOSQL

	psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
		-c "ALTER USER $user WITH PASSWORD '$password';"

	if ! psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -tAc "SELECT 1 FROM pg_database WHERE datname = '$db'" | grep -q 1; then
		psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -c "CREATE DATABASE $db OWNER $user;"
	fi

	psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -c "GRANT ALL PRIVILEGES ON DATABASE $db TO $user;"
}

create_db_and_user "auth_db" "auth_user" "$AUTH_DB_PASSWORD"
create_db_and_user "user_db" "user_user" "$USER_DB_PASSWORD"
create_db_and_user "travel_db" "travel_user" "$TRAVEL_DB_PASSWORD"
create_db_and_user "payment_db" "payment_user" "$PAYMENT_DB_PASSWORD"

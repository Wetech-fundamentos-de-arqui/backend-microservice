#!/bin/bash
# Runs once, the first time the MySQL volume is created (ADR-006): one schema and one user per service,
# each user limited to its own schema.
set -e

create_schema() {
  local schema="$1" user="$2" password="$3"
  mysql -uroot -p"$MYSQL_ROOT_PASSWORD" <<SQL
CREATE DATABASE IF NOT EXISTS \`${schema}\`;
CREATE USER IF NOT EXISTS '${user}'@'%' IDENTIFIED BY '${password}';
GRANT ALL PRIVILEGES ON \`${schema}\`.* TO '${user}'@'%';
SQL
}

create_schema weride_iam          weride_iam          "$DB_PASSWORD_IAM"
create_schema weride_profile      weride_profile      "$DB_PASSWORD_PROFILE"
create_schema weride_garage       weride_garage       "$DB_PASSWORD_GARAGE"
create_schema weride_location     weride_location     "$DB_PASSWORD_LOCATION"
create_schema weride_booking      weride_booking      "$DB_PASSWORD_BOOKING"
create_schema weride_ride         weride_ride         "$DB_PASSWORD_RIDE"
create_schema weride_plan         weride_plan         "$DB_PASSWORD_PLAN"
create_schema weride_notification weride_notification "$DB_PASSWORD_NOTIFICATION"

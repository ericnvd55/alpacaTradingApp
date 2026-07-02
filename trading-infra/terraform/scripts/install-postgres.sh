#!/bin/bash
set -euo pipefail

apt-get update -y
apt-get install -y postgresql postgresql-contrib pgbouncer

PG_VERSION=$(ls /etc/postgresql/)

# Listen on all interfaces
sed -i "s/#listen_addresses = 'localhost'/listen_addresses = '*'/" \
  /etc/postgresql/${PG_VERSION}/main/postgresql.conf

# Create trading database and user
sudo -u postgres psql <<'SQL'
CREATE USER trading WITH PASSWORD 'changeme';
CREATE DATABASE trading OWNER trading;
SQL

# Allow GKE pod CIDR to connect
echo "host trading trading 10.1.0.0/16 scram-sha-256" \
  >> /etc/postgresql/${PG_VERSION}/main/pg_hba.conf

# Configure PgBouncer
cat > /etc/pgbouncer/pgbouncer.ini <<'PGBOUNCER'
[databases]
trading = host=127.0.0.1 port=5432 dbname=trading

[pgbouncer]
listen_addr = 0.0.0.0
listen_port = 6432
auth_type = scram-sha-256
auth_file = /etc/pgbouncer/userlist.txt
pool_mode = transaction
max_client_conn = 100
default_pool_size = 10
PGBOUNCER

echo '"trading" "changeme"' > /etc/pgbouncer/userlist.txt
chmod 640 /etc/pgbouncer/userlist.txt
chown pgbouncer:pgbouncer /etc/pgbouncer/userlist.txt

systemctl enable postgresql pgbouncer
systemctl restart postgresql pgbouncer

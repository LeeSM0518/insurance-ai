FROM pgvector/pgvector:pg17

RUN apt-get update && \
    apt-get install -y --no-install-recommends \
      build-essential postgresql-server-dev-17 git ca-certificates && \
    git clone https://github.com/pgbigm/pg_bigm.git /tmp/pg_bigm && \
    cd /tmp/pg_bigm && make USE_PGXS=1 && make USE_PGXS=1 install && \
    apt-get purge -y build-essential postgresql-server-dev-17 git && \
    apt-get autoremove -y && rm -rf /var/lib/apt/lists/* /tmp/pg_bigm

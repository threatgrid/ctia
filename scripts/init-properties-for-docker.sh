#!/bin/bash


ip=127.0.0.1
command -v docker-machine >/dev/null 2>&1 && \
    ip=$(docker-machine ip)

if [ -z "${ip}" ]; then
    ip="localhost"
fi

echo "Detected IP for docker machine: $ip"

resourcefile="./resources/ctia.properties"

if [ -f $resourcefile ]; then
    backupfile="$resourcefile.backup.$$"
    echo "Backup $resourcefile"
    echo "to $backupfile"
    mv $resourcefile $backupfile
fi

cat > $resourcefile <<EOF
ctia.store.es.default.host=$ip
ctia.store.es.default.port=9200
ctia.hook.es.host=$ip
ctia.hook.es.port=9200
ctia.hook.redis.host=$ip
ctia.hook.redismq.host=$ip
EOF

echo "$resourcefile initialized"

# Kafka for SMS

SMS delivery requires a running Kafka broker. `Connection to node -1
(localhost/127.0.0.1:9092) could not be established` means the application cannot
connect to its configured broker. Declaring topics in Java does not start Kafka.

## Local development (application running from the IDE)

Start Docker Desktop, then run from the `vikoba` directory:

```powershell
docker compose -f compose.kafka.yml up -d --wait
```

Start or restart the Java application after the broker is healthy. The `dev`
profile connects to `localhost:9092`. Spring creates `vikoba.sms`,
`vikoba.sms.retry`, and `vikoba.sms.dlq` during startup.

```powershell
docker compose -f compose.kafka.yml ps
docker compose -f compose.kafka.yml exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --list
```

The named volume preserves messages across container recreation. The restart
policy restarts Kafka when Docker starts unless the container was explicitly
stopped. Enable Docker Desktop startup at sign-in for this workstation.
Do not run `down -v` unless you intend to erase the Kafka data.

The host port is bound to loopback. This single-broker plaintext setup is for
local development, not a highly available production cluster.

## Production

Set `KAFKA_BOOTSTRAP_SERVERS` to reachable broker addresses. The default
`kafka:9092` only works when that hostname resolves from the application, such
as containers sharing a Docker network. `localhost` inside a container means
that same container. Brokers must advertise addresses reachable by the client.
Configure Spring's `spring.kafka.properties.*` security settings when the
production broker requires TLS or SASL; the Boot-managed producer now honors
these settings alongside the consumers and admin client.

The application can start even if Kafka is temporarily unavailable. Kafka clients
reconnect automatically, and the local broker can create missing topics when
clients connect. Start Kafka before the application for immediate SMS delivery.
Producer metadata waits are limited to five seconds during an outage. SMS still
requires a running broker; request a new OTP if a notification was already
marked FAILED.

Reference: https://kafka.apache.org/41/getting-started/docker/

# Sessions

Once authenticated, the user is identified by a session cookie (`SESSION`). The session holds the
Spring Security authentication.

## In-memory sessions (default)

By default, sessions are kept in the memory of the gateway: they are lost when the gateway restarts,
and they are not shared between several gateway instances.

The session timeout is the standard Spring Boot property `server.reactive.session.timeout`. The
official datadir sets it to 24 hours:

```yaml
server:
  reactive:
    session:
      timeout: 1440m
```

## Redis sessions

To share sessions between several gateway instances, or to keep them across restarts, store them in
Redis:

```yaml
spring:
  session:
    redis:
      enabled: true
      host: redis
      port: 6379
      password: ${SPRING_SESSION_REDIS_PASSWORD:}
```

| Property | Default | Description |
|---|---|---|
| `spring.session.redis.enabled` | `false` | Store the sessions in Redis. |
| `spring.session.redis.host` | `localhost` | Redis host. |
| `spring.session.redis.port` | `6379` | Redis port. |
| `spring.session.redis.password` | empty | Redis password (none when empty). |

These four properties are read by the gateway itself; they configure a connection to a **standalone**
Redis server (no Sentinel, no Cluster, no TLS options). Spring Boot's own `spring.data.redis.*`
auto-configuration is disabled in the gateway.

The official datadir maps them to environment variables:

```yaml
spring:
  session:
    redis:
      enabled: ${SPRING_SESSION_REDIS_ENABLED:false}
      host: ${SPRING_SESSION_REDIS_HOST:redis}
      port: ${SPRING_SESSION_REDIS_PORT:6379}
      password: ${SPRING_SESSION_REDIS_PASSWORD:password}
```

Sessions are serialized as JSON, so their content can be inspected in Redis.

!!! note
    The sessions are managed by Spring Session (`@EnableRedisWebSession`), whose default maximum
    inactive interval is 30 minutes. Check that the effective timeout is the one you expect after
    enabling Redis.

The repository `docker-compose.yml` contains a commented-out `redis` service to test this setup.

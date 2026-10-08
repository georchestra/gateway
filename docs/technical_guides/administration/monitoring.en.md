# Monitoring

The gateway exposes the [Spring Boot Actuator](https://docs.spring.io/spring-boot/reference/actuator/index.html)
endpoints on a dedicated **management port**, `8090` (`management.server.port`).

!!! danger "Never expose the management port"
    The embedded configuration exposes **all** the actuator endpoints
    (`management.endpoints.web.exposure.include: "*"`), including `env`, `configprops` and
    `shutdown` (with unrestricted access: a `POST /actuator/shutdown` stops the gateway). Port `8090`
    must only be reachable from your internal network / monitoring system.

## Useful endpoints

| Endpoint | Description |
|---|---|
| `/actuator/health` | Health status, with details. |
| `/actuator/health/liveness`, `/actuator/health/readiness` | Liveness and readiness probes, e.g. for Kubernetes (`management.endpoint.health.probes.enabled: true`). |
| `/actuator/info` | Build, Java and environment information. |
| `/actuator/metrics` | Metrics. Spring Cloud Gateway publishes `spring.cloud.gateway.requests` (per route). |
| `/actuator/prometheus` | Metrics in the Prometheus format. |
| `/actuator/gateway/routes` | Routes currently loaded. |
| `/actuator/loggers` | Read and change the log levels at runtime. |

Example of Kubernetes probes:

```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8090
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8090
```

## Restricting the exposed endpoints

The embedded settings can be overridden in the datadir, for example:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics, prometheus
  endpoint:
    shutdown:
      access: none
```

## OpenTelemetry

The Docker image is built with the
[Paketo OpenTelemetry buildpack](https://github.com/paketo-buildpacks/opentelemetry), which adds the
OpenTelemetry Java agent to the image. Refer to the buildpack documentation to enable it at runtime and
to the OpenTelemetry documentation for the `OTEL_*` environment variables.

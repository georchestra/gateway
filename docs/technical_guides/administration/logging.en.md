# Logging

## Log levels

Log levels are set with the standard Spring Boot `logging.level.<logger>` properties, in
`logging.yaml` in the official datadir. The embedded configuration uses:

```yaml
logging:
  level:
    root: warn
    '[org.springframework]': info
    '[org.springframework.cloud.gateway]': info
    '[org.springframework.security]': info
    '[org.springframework.security.oauth2]': debug
    '[reactor.netty.http]': info
    '[org.georchestra.gateway]': info
```

See [Debug](../installation/debug.md#raising-log-levels) for the most useful loggers.

## JSON logs

With the `json-logs` Spring profile, the logs are written to the console as JSON documents (Logstash
encoder), one per line, including all the MDC attributes described below. Without this profile, the
default Spring Boot console format is used.

```bash
SPRING_PROFILES_ACTIVE=json-logs
```

## MDC (Mapped Diagnostic Context)

The gateway adds contextual attributes to the log events of each request. The user and organization
attributes (`enduser.uuid`, `enduser.org.*`, `enduser.firstname`, `enduser.lastname`) are only added
for proxied requests (requests matching a route). They are visible in the
JSON logs (and are available to custom Logback patterns).

`logging.mdc.enabled` (default `true`) enables the feature. The attributes are selected with the
following properties:

### Request attributes — `logging.mdc.include.http.*`

| Property | Default | MDC attribute(s) |
|---|---|---|
| `id` | `true` | `http.request.id`: value of the `trace-id`, `http.request.id` or `X-Request-ID` request header, or a new ULID. |
| `method` | `true` | `http.request.method` |
| `path` | `true` | `http.request.path` (without query string) |
| `query-string` | `false` | `http.request.query-string` |
| `parameters` | `false` | `http.request.parameter.<name>` for each query parameter |
| `headers` | `false` | `http.request.header.<name>` for each header matching `headers-pattern` (except `Cookie`) |
| `headers-pattern` | `.*` | Java regular expression selecting the header names |
| `cookies` | `false` | `http.request.cookie.<name>` for each cookie |
| `remote-addr` | `false` | `http.request.remote-addr` |
| `remote-host` | `false` | Not populated in the gateway (WebFlux). |
| `session-id` | `false` | Not populated in the gateway (WebFlux). |

### User attributes — `logging.mdc.include.user.*`

| Property | Default | MDC attribute(s) |
|---|---|---|
| `id` | `true` | `enduser.id` (authentication name) and `enduser.uuid` (geOrchestra user id) |
| `roles` | `false` | `enduser.roles` (authorities of the authentication, comma-separated) |
| `org` | `false` | `enduser.org.id` (organization short name) and `enduser.org.uuid` (organization id) |
| `extras` | `false` | `enduser.firstname`, `enduser.lastname`, and `enduser.org.fullname` when `org` is enabled |
| `auth-method` | `false` | `enduser.auth-method` (type of the authentication, e.g. `OAuth2AuthenticationToken`) |

### Application attributes — `logging.mdc.include.app.*`

| Property | Default | MDC attribute |
|---|---|---|
| `name` | `true` | `application.name` (`spring.application.name`) |
| `version` | `true` | `application.version` |
| `profile` | `true` | `application.profile` (active profiles) |
| `instance-id` | `false` | `application.instance-id` (`spring.application.instance-id`) |

!!! warning "Datadir property names"
    Some versions of the official `logging.yaml` use `logging.mdc.include.application.*` and
    `active-profiles`: these names are not recognized. The correct prefix is
    `logging.mdc.include.app` and the profiles flag is `profile`.

## Access log

The access log writes one line per proxied request (requests matching a route; the pages of the
gateway itself such as `/login` or `/whoami` are not logged), `<METHOD> <STATUS> <URL>`, to the
`org.georchestra.gateway.accesslog` logger. Requests are selected with lists of Java regular
expressions matched against the full request URL:

```yaml
logging:
  accesslog:
    enabled: true
    info:
      - .*/(?:ows|ogc|wms|wfs|wcs|wps)(?:/.*|\?.*)?$
    debug:
      - .*/console/.*
    trace:
      - ^(?!.*/web/wicket/resource/)(?!.*\.(png|jpg|jpeg|gif|svg|webp|ico)(\?.*)?$).*$
  level:
    org.georchestra.gateway.accesslog: info
```

| Property | Default | Description |
|---|---|---|
| `logging.accesslog.enabled` | `true` | Enables the access log filter. |
| `logging.accesslog.info` | empty | URLs logged at `INFO` level. |
| `logging.accesslog.debug` | empty | URLs logged at `DEBUG` level. |
| `logging.accesslog.trace` | empty | URLs logged at `TRACE` level. |

A request is logged at the first level, in the order `info`, `debug`, `trace`, whose patterns match
its URL **and** that is enabled for the `org.georchestra.gateway.accesslog` logger. With the default
empty lists, nothing is logged. In the example above, with the logger at `info`, only OGC service
requests are logged; set the logger to `debug` to also log the console requests.

The access log entries carry the MDC attributes, plus `http.request.method`, `http.status_code` and
`http.request.url`.

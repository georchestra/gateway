# Services and routes

Proxying an application through the gateway involves two independent pieces of configuration:

- a **service**, under `georchestra.gateway.services.<name>`: the back-end URL (`target`), and
  optionally its own [access rules](access_rules.md) and [`sec-*` headers](headers.md);
- a **route**, under `spring.cloud.gateway.server.webflux.routes`: which requests are forwarded to which
  URL, with which filters. Routes are plain
  [Spring Cloud Gateway routes](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/request-predicates-factories.html).

## Declaring a service

```yaml
georchestra:
  gateway:
    services:
      geoserver:
        target: http://geoserver:8080/geoserver/
        access-rules:
        - intercept-url: /geoserver/**
          anonymous: true
        headers:
          json-user: true
```

| Property | Type | Description |
|---|---|---|
| `georchestra.gateway.services.<name>.target` | URI | URL of the back-end service. |
| `georchestra.gateway.services.<name>.access-rules` | list | Access rules specific to this service. See [Access rules](access_rules.md). |
| `georchestra.gateway.services.<name>.headers` | object | `sec-*` headers for this service, merged over `georchestra.gateway.default-headers`. See [Security headers](headers.md). |

`<name>` is free: it is only used to reference the target in the routes and in the logs.

## Declaring a route

```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          routes:
          - id: geoserver
            uri: ${georchestra.gateway.services.geoserver.target}
            predicates:
            - Path=/geoserver/**
```

Always use the `${georchestra.gateway.services.<name>.target}` placeholder as the route `uri`.

!!! important "How a route is linked to a service"
    When a request is proxied, the gateway looks for the service whose `target` is **exactly equal**
    to the `uri` of the matched route. Only that service's `headers` are applied. If no service has
    the same URI, only the `default-headers` are applied. If several services have the same `target`,
    the first one declared wins.

    Spring Cloud Gateway adds the default port to route URIs that have none (`:80` for `http`,
    `:443` for `https`), while service targets are kept as written. **Always write the port in the
    targets** (e.g. `http://geoserver:8080/geoserver/`, `https://geoserver.example.org:443/geoserver/`),
    otherwise the target never matches its route and only the `default-headers` are applied.

    Access rules, on the contrary, are **not** linked to routes: all access rules (of all services and
    the global ones) are evaluated against the request path. See [Access rules](access_rules.md).

The full Spring Cloud Gateway syntax is available: other predicates (`Host`, `Method`, `Query`, ...),
filters (`RewritePath`, `RedirectTo`, `StripPrefix`, ...), per-route `metadata` (e.g. CORS), etc.
The gateway adds a few filters and predicates of its own, described in
[Filters and predicates](filters.md).

### Examples from the official datadir

Redirect the root of the site to the datahub:

```yaml
- id: root
  uri: ${georchestra.gateway.services.datahub.target}
  predicates:
  - Path=/
  filters:
  - RedirectTo=308, /datahub/
```

Allow cross-origin requests to GeoNetwork and share its `XSRF-TOKEN` cookie with the datahub:

```yaml
- id: geonetwork
  uri: ${georchestra.gateway.services.geonetwork.target}
  predicates:
  - Path=/geonetwork/**
  filters:
  - DedupeResponseHeader=Access-Control-Allow-Origin, RETAIN_FIRST
  - name: CookieAffinity
    args:
      name: XSRF-TOKEN
      from: /geonetwork
      to: /datahub
  metadata:
    cors:
      allowedOrigins: '*'
      allowedMethods:
        - GET
        - POST
      allowedHeaders: '*'
      maxAge: 30
```

## Default filters

The embedded `application.yml` applies the following filters to **every** route
(`spring.cloud.gateway.server.webflux.default-filters`):

| Filter | Role |
|---|---|
| `SecureHeaders` | Adds security headers to the responses (Spring Cloud Gateway filter, configured below). |
| `RemoveSecurityHeaders` | Removes the incoming `sec-*` request headers, to prevent impersonation. |
| `AddSecHeaders` | Adds the `sec-*` headers of the authenticated user. |
| `PreserveHostHeader` | Sends the original `Host` header to the back-end. |
| `ApplicationError` | Replaces back-end error responses by the gateway error pages, for browser requests. |
| `LoginParamRedirect` | Redirects anonymous users to `/login` when the URL contains a `login` query parameter. |

See [Filters and predicates](filters.md) for details on the geOrchestra-specific ones.

!!! warning
    If you redefine `default-filters` in the datadir, the list above is replaced. Always keep at
    least `RemoveSecurityHeaders` and `AddSecHeaders`, otherwise clients could forge `sec-*` headers or
    the back-ends would not receive the user identity.

The default configuration of the `SecureHeaders` filter is:

```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          filter:
            secure-headers:
              enabled: true
              disable:
                - content-security-policy
                - x-frame-options
                - xss-protection-header
              referrer-policy: no-referrer
```

`disable` entries are compared with the lower-cased header names: `xss-protection-header` matches
nothing, so the `X-Xss-Protection` header is still sent. Use `x-xss-protection` to disable it.

The official datadir overrides `referrer-policy` with `strict-origin`. See the
[Spring Cloud Gateway documentation](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories/secureheaders-factory.html)
for all the options.

## Other embedded Spring Cloud Gateway settings

| Property | Value | Effect |
|---|---|---|
| `spring.cloud.gateway.server.webflux.global-filter.websocket-routing.enabled` | `true` | WebSocket requests are proxied. |
| `spring.cloud.gateway.server.webflux.metrics.enabled` | `true` | Gateway metrics are published (see [Monitoring](monitoring.md)). |
| `server.compression.enabled` | `true` | Responses are compressed. |
| `server.http2.enabled` | `${server.ssl.enabled}` | HTTP/2 is enabled only when the gateway itself serves TLS. |
| `server.forward-headers-strategy` | `FRAMEWORK` | `X-Forwarded-*` / `Forwarded` headers are taken into account. |

## Adding a new application

1. Declare the service and its target in `gateway.yaml`:

    ```yaml
    georchestra:
      gateway:
        services:
          myapp:
            target: http://myapp:8080/myapp/
            access-rules:
            - intercept-url: /myapp/admin/**
              allowed-roles: ADMINISTRATOR
            - intercept-url: /myapp/**
              anonymous: true
    ```

2. Declare the route in `routes.yaml`:

    ```yaml
    - id: myapp
      uri: ${georchestra.gateway.services.myapp.target}
      predicates:
      - Path=/myapp/**
    ```

3. Optionally enable more `sec-*` headers for this service with `georchestra.gateway.services.myapp.headers`.
4. Restart the gateway and check the route with `/actuator/gateway/routes` on the management port.

# Filters and predicates

Besides all the standard
[Spring Cloud Gateway filters](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories.html)
and [predicates](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/request-predicates-factories.html),
the gateway provides the following ones. They can be used in `default-filters` or in the `filters` /
`predicates` of a route.

## Filters

### AddSecHeaders

Adds the [`sec-*` headers](headers.md) of the current user to the proxied request. Part of the default
filters. No argument.

```yaml
filters:
- AddSecHeaders
```

### RemoveSecurityHeaders

Removes the incoming request headers whose name starts with `sec-` (case-insensitive), so that clients
cannot impersonate a user. Part of the default filters. No argument.

### RemoveHeaders

Removes the request headers matching a Java regular expression. Argument: `regEx`.

The expression is matched (with `matches()`, i.e. against the whole string) against
`<header name>: <header value>`, for each value of each header. A header is removed when one of its
values matches.

Quote the value in YAML when the expression contains `: ` or other YAML special characters.

```yaml
filters:
# remove the Authorization header when it is not a Bearer token
- "RemoveHeaders=(?i)^Authorization: (?!Bearer ).*"
# remove all X-Debug-* headers
- "RemoveHeaders=(?i)x-debug-.*"
```

!!! note
    Since the value is part of the matched string, a pattern matching only the header name (e.g.
    `(?i)Authorization`) does **not** match a header that has a value. Add `.*` or `:.*` to match any
    value.

### CookieAffinity

Duplicates a cookie set by a back-end on another path, so that two applications can share it (for
example the GeoNetwork `XSRF-TOKEN` cookie with the datahub). For each `Set-Cookie` response header
whose cookie name is `name` and path is `from`, a copy of the cookie with path `to` is added (same
value, domain, `HttpOnly`, `Secure` and max-age).

| Argument | Description |
|---|---|
| `name` | Cookie name. |
| `from` | Path of the cookie set by the back-end. |
| `to` | Path of the copy. |

```yaml
filters:
- name: CookieAffinity
  args:
    name: XSRF-TOKEN
    from: /geonetwork
    to: /datahub
```

### ApplicationError

When the back-end answers with a 4xx or 5xx status to a `GET`, `HEAD`, `OPTIONS` or `TRACE` request
whose `Accept` header is compatible with `text/html` (including `text/*` and `*/*`, e.g. the default of
`curl`), the response is replaced by the gateway error page for that
status (see [UI customization](ui_customization.md#error-pages)). Other requests (API calls, `POST`,
...) get the back-end response untouched. Part of the default filters. No argument.

### LoginParamRedirect

For `GET`, `HEAD`, `OPTIONS` and `TRACE` requests having a `login` query parameter (e.g.
`/datahub/?login`), redirects anonymous users to `/login`; authenticated users are not redirected. Part
of the default filters. No argument.

### RouteProfile

Enables a route only when a Spring profile is active. When the condition is not met, the gateway
answers with the given status code without proxying the request.

| Argument | Default | Description |
|---|---|---|
| `profile` | | Profile that must be active. Prefix it with `!` to require that the profile is **not** active. |
| `statusCode` | `404` | Status returned when the condition is not met. |

```yaml
filters:
- RouteProfile=dev,403
```

### StripBasePath

Removes a path prefix from the request before proxying it. Argument: `prefix`, which must start with
`/` and must not end with `/` (unless it is `/`). The request path is left unchanged when it does not
start with the prefix.

```yaml
filters:
- StripBasePath=/myapp
```

## Predicates

### QueryParam

Matches requests having a query parameter with the given name, whatever its value. Argument: `param`.

```yaml
predicates:
- QueryParam=token
```

### RegExpQuery

Matches requests having a query parameter whose **name** matches a regular expression and, optionally,
whose **value** matches another one. The value is checked on the first parameter whose name matches.

| Argument | Description |
|---|---|
| `paramRegexp` | Java regular expression for the parameter name. |
| `valueRegexp` | Optional Java regular expression for the value. |

```yaml
# matches ?SERVICE=WMS, ?service=wms, ...
predicates:
- RegExpQuery=(?i:service),(?i:wms)
```

## Global filters

The following filters are applied to every proxied request and need no configuration:

| Filter | Role |
|---|---|
| `ResolveGeorchestraUserGlobalFilter` | Resolves the geOrchestra user of the request (and its organization), applying [role mappings](roles_mappings.md) and [account creation](accounts.md). |
| `ResolveTargetGlobalFilter` | Resolves the service matching the route, to compute the headers to send. |
| `MdcUserAndOrgGlobalFilter` | Adds the user and organization to the logging MDC. See [Logging](logging.md). |
| `GlobalUriFilter` | Prevents already percent-encoded URLs from being encoded a second time when proxied (e.g. `%27` becoming `%2527`). |

# Debug

## Startup log

When the gateway is ready, it logs a line such as:

```
gateway-service ready. Data dir: /etc/georchestra. Routes: 12. Instance-id: ..., cpus: 4, max memory: 1.60 GB
```

Check that the data directory is the expected one and that the number of routes matches your
`routes.yaml`. The enabled authentication methods are also logged at startup:
`georchestra LDAP security enabled`, `georchestra OAuth2 security enabled` /
`georchestra OAuth2 security disabled`.

## Raising log levels

Log levels are standard Spring Boot `logging.level.*` properties, set in `logging.yaml` or as
environment variables (e.g. `LOGGING_LEVEL_ORG_GEORCHESTRA_GATEWAY=debug`).

Useful loggers:

| Logger | Content |
|---|---|
| `org.georchestra.gateway.security` | Authentication, user resolution. |
| `org.georchestra.gateway.security.ldap` | LDAP configuration and authentication. |
| `org.georchestra.gateway.security.oauth2` | OAuth2 / OpenID Connect mapping, claims extraction. |
| `org.georchestra.gateway.config.security.accessrules` | Access rules applied at startup. |
| `org.georchestra.gateway.filter.headers` | `sec-*` headers added to each proxied request (`debug`) or skipped (`trace`). |
| `org.georchestra.gateway.accounts.admin.ldap` | LDAP account and organization creation. |
| `org.georchestra.gateway.accesslog` | Access log (see [Logging](../administration/logging.md)). |
| `org.springframework.security` | Spring Security decisions. |
| `org.springframework.cloud.gateway` | Route matching and proxying. |
| `reactor.netty.http` | HTTP client / server traffic. |

The official datadir `logging.yaml` defines two convenience profiles, `logging_debug` and
`logging_debug_security`, which raise the gateway (respectively Spring Security and gateway security)
loggers from `warn` to `info`. For `debug` levels, set `logging.level.*` explicitly.

## Inspecting the current user

`GET /whoami` returns the geOrchestra user resolved for the current session and the underlying
Spring Security authentication object (see [Gateway endpoints](../../user_guide/endpoints.md)). It
is the quickest way to check the roles, organization and username a user gets, after role mappings.

## Inspecting the routes

The Spring Cloud Gateway actuator endpoint is exposed on the management port:

```bash
curl http://localhost:8090/actuator/gateway/routes
```

The health endpoint is `http://localhost:8090/actuator/health`. See
[Monitoring](../administration/monitoring.md).

## Common problems

**The gateway does not start: `Could not resolve placeholder 'ldapHost'`** (or `ldapScheme`,
`ldapPort`): the `default` LDAP configuration is enabled but `default.properties` was not found or does
not define these properties. Check `georchestra.datadir`.

**The gateway does not start: `LDAP account creation requires an extended LDAP configuration`**:
`createNonExistingUsersInLDAP` is enabled but no enabled LDAP configuration has `extended: true`.
See [Account creation](../administration/accounts.md).

**Startup fails with a validation error on `ldap.[name].…`**: a required LDAP property is missing for
an enabled LDAP configuration (URL, base DN, users RDN, users search filter except for Active
Directory, roles RDN, roles search filter, and orgs RDN for extended configurations). See [LDAP authentication](../administration/ldap.md).

**A back-end application does not receive the `sec-*` headers configured for its service** (only the
`default-headers`): the headers of a service are only applied when the route `uri` is *exactly* equal
to the service `target`, and Spring Cloud Gateway adds `:80` / `:443` to route URIs without port.
Write the port in the target. See
[Security headers](../administration/headers.md#how-headers-are-resolved-for-a-request).

**OAuth2 redirect URIs or login redirections use `http` or an internal host name instead of the public
URL**: the reverse proxy in front of the gateway does not send the `X-Forwarded-Proto` /
`X-Forwarded-Host` (or `Forwarded`) headers, or the gateway does not trust them (see
`spring.cloud.gateway.server.webflux.trusted-proxies` in the official `application.yaml`).

**A browser gets a *503 Service Unavailable* page**: the gateway could not connect to the back-end
(unknown host or connection refused). Check the service `target` URL.

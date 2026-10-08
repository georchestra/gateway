# Presentation

## Role in a geOrchestra platform

The gateway is the only component exposed to the outside (usually behind a TLS-terminating reverse
proxy or ingress). All the other geOrchestra applications are only reachable through it.

```
                       ┌──────────────────────── geOrchestra Gateway ───────────────────────┐
 Browser / client ───▶ │ authentication ─▶ access rules ─▶ route ─▶ sec-* headers ─▶ proxy │ ───▶ console, geoserver,
                       └───────┬───────────────────────────────────────────────────────────┘      geonetwork, datahub, ...
                               │
                  LDAP / OAuth2-OIDC provider / trusted reverse proxy
```

The back-end applications do not authenticate users themselves: they trust the `sec-*` HTTP request
headers added by the gateway (`sec-username`, `sec-roles`, `sec-org`, ...). This is why the gateway
always **removes** any incoming `sec-*` header sent by the client before adding its own.

## What happens to a request

1. **Authentication** (Spring Security filter chain). Depending on the configuration, the user can be
   authenticated by:
    - the login form (`POST /login`) or HTTP Basic, against one or several LDAP directories;
    - an OAuth2 / OpenID Connect provider (`/oauth2/authorization/{provider}`);
    - pre-authentication HTTP headers set by a trusted reverse proxy.

    The authenticated session is kept in a cookie-based web session, stored in memory or in Redis.
2. **Authorization**. The request path is checked against the configured
   [access rules](../administration/access_rules.md). An anonymous user trying to access a protected
   URL is redirected to the login page; an authenticated user without the required role gets a
   *403 Forbidden*.
3. **Routing**. The request is matched against the Spring Cloud Gateway
   [routes](../administration/services_and_routes.md) (usually by path prefix) to find the back-end
   service to forward it to.
4. **User resolution**. The authentication is converted into a *geOrchestra user* (username, email,
   organization, roles, ...). At this step [role mappings](../administration/roles_mappings.md) are
   applied and, when enabled, the [LDAP account is created](../administration/accounts.md) for users
   coming from an external identity provider.
5. **Headers**. Incoming `sec-*` headers are removed, then the
   [`sec-*` headers](../administration/headers.md) enabled for the target service are added.
6. **Proxying**. The request is sent to the back-end. If the back-end answers with an error to a
   browser request, the gateway renders its own error page.

## Code organization

The gateway is a Maven multi-module project:

| Module | Content |
|---|---|
| `gateway` | The Spring Boot application: security, routing filters, user resolution, account creation, login/logout pages. |
| `modules/logging` | MDC (Mapped Diagnostic Context) propagation for WebFlux and the access log. |
| `modules/org-resolvers` | `OrganizationNameResolver` interface, used to resolve an organization name from an identifier. |
| `modules/sirene` | Implementation of `OrganizationNameResolver` calling the French INSEE SIRENE API. |

It is built on Spring Boot 3, Spring Cloud Gateway (WebFlux / Reactor Netty), Spring Security and
the geOrchestra `ldap-account-management` library. See [Architecture](../contribute/architecture.md)
for details.

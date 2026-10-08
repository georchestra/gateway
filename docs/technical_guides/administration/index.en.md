# Administration

This section details the configuration of each feature of the gateway. All the properties mentioned
here have been checked against the source code; the complete list is in the
[configuration reference](properties.md).

## Routing and authorization

- [Services and routes](services_and_routes.md) — declare the back-end services and the routes that
  forward requests to them.
- [Access rules](access_rules.md) — role-based access control per URL pattern.
- [Security headers](headers.md) — the `sec-*` headers sent to the back-ends.
- [Role mappings](roles_mappings.md) — grant additional roles based on the roles of the user.
- [Filters and predicates](filters.md) — the custom Spring Cloud Gateway filters and predicates
  provided by the gateway.

## Authentication

- [LDAP authentication](ldap.md) — one or several LDAP directories, geOrchestra extended LDAP,
  Active Directory.
- [OAuth2 and OpenID Connect](oauth2.md) — external identity providers, claims mapping.
- [Header pre-authentication](preauth.md) — trust an authenticating reverse proxy.
- [Account creation](accounts.md) — create LDAP accounts and organizations on first login,
  moderated sign-up, SIRENE organization names, account creation events.

## User interface and operations

- [UI customization](ui_customization.md) — login, logout and error pages, geOrchestra header,
  redirections.
- [Sessions](sessions.md) — session timeout, Redis-backed sessions.
- [Logging](logging.md) — log levels, JSON logs, MDC, access log.
- [Monitoring](monitoring.md) — Actuator endpoints and metrics.

## Reference

- [Configuration reference](properties.md) — every gateway-specific property, with its type and
  default value.

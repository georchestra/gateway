# Prerequisites

## Runtime

- **Java 21** or later, if you run the JAR directly. The Docker image already contains a JRE.
- The gateway listens on two ports:
    - `8080`: the proxied applications, the login page, etc. (`server.port`);
    - `8090`: the Spring Boot Actuator management endpoints (`management.server.port`).
      **This port must not be exposed publicly** (see [Monitoring](../administration/monitoring.md)).

## geOrchestra datadir

The gateway reads its configuration from the **geOrchestra data directory** (datadir), by default
`/etc/georchestra` (property `georchestra.datadir`). It uses:

- `default.properties`: properties shared by all geOrchestra applications (LDAP host, base DN, header
  URL, ...);
- the `gateway/` sub-directory: the gateway configuration files.

Start from the official datadir: [georchestra/datadir](https://github.com/georchestra/datadir)
(directory [`gateway`](https://github.com/georchestra/datadir/tree/master/gateway)). See
[Configuration files](configuration.md).

!!! warning
    The default LDAP configuration embedded in the gateway uses the `ldapScheme`, `ldapHost` and
    `ldapPort` properties without default value. They are normally provided by `default.properties`.
    If you enable the `default` LDAP configuration without them, the gateway fails to start with an
    unresolved placeholder error.

## LDAP directory

A standard geOrchestra platform uses the geOrchestra OpenLDAP directory
([georchestra/ldap](https://hub.docker.com/r/georchestra/ldap)) as its user database. It is
required to:

- log in with a username and password ([LDAP authentication](../administration/ldap.md));
- provide organization information to the back-ends (`sec-org`, `sec-orgname`, ...);
- [create accounts](../administration/accounts.md) for users coming from an external identity
  provider or from pre-authentication.

To create accounts, the gateway needs an LDAP account with **write** access (`adminDn` /
`adminPassword`).

Other LDAP servers, including Active Directory, can also be used for authentication only.

## Optional components

- **Redis**, to store the web sessions outside of the gateway memory, e.g. to run several gateway
  instances or keep sessions across restarts. See [Sessions](../administration/sessions.md).
- An **OAuth2 / OpenID Connect provider** (Keycloak, Google, GitHub, ProConnect, ...), registered with
  the redirect URI `{baseUrl}/login/oauth2/code/{registrationId}`. See
  [OAuth2 and OpenID Connect](../administration/oauth2.md).
- An **INSEE SIRENE API key**, to resolve the names of French organizations from their SIRET number.
  See [Account creation](../administration/accounts.md#organization-name-resolution).

## Reverse proxy

The gateway is usually deployed behind a reverse proxy that terminates TLS. It is configured with
`server.forward-headers-strategy: FRAMEWORK` so that it honours the `X-Forwarded-*` / `Forwarded`
headers sent by that proxy (needed to build correct redirect URLs, e.g. for OAuth2).

If you enable [header pre-authentication](../administration/preauth.md), the reverse proxy **must**
remove the `sec-georchestra-preauthenticated` and `preauth-*` headers from the client requests.

---
hide:
  - navigation
  - toc
---

# geOrchestra Gateway

## What is it?

The **geOrchestra Gateway** is the single entry point of a [geOrchestra](https://www.georchestra.org/)
Spatial Data Infrastructure. Every HTTP request sent by a browser or a client to the platform goes through it.

It is a [Spring Cloud Gateway](https://spring.io/projects/spring-cloud-gateway) (WebFlux) application that
replaces the historical *security-proxy* + *CAS* pair. It:

- **routes** incoming requests to the geOrchestra back-end applications (console, GeoServer, GeoNetwork,
  datahub, MapStore, ...);
- **authenticates** users against one or several LDAP directories, OAuth2 / OpenID Connect providers,
  or a trusted reverse proxy (HTTP header pre-authentication);
- **authorizes** requests with role-based access rules defined per URL pattern;
- **forwards the user identity** to the back-end applications through the standard geOrchestra
  `sec-*` HTTP request headers;
- optionally **creates the LDAP account** (and organization) of users that log in for the first time through
  an external identity provider;
- serves the **login page**, the logout flow and the error pages, which can be customized.

## What it does not do

- It does not manage users, roles or organizations: this is the role of the geOrchestra **console**.
  The gateway only creates accounts on first login when this feature is enabled.
- It is not an identity provider: it consumes LDAP, OAuth2/OpenID Connect or pre-authentication headers,
  but does not issue tokens to third parties.
- It does not terminate TLS by default (`server.ssl.enabled: false`): it is usually deployed behind a
  reverse proxy or an ingress controller.

## Where to start?

- You are a **user** of a geOrchestra platform: read the [user guide](user_guide/index.md).
- You **install or configure** a geOrchestra platform: read the
  [installation](technical_guides/installation/index.md) and
  [administration](technical_guides/administration/index.md) guides.
- You want to **contribute** to the code: read the [contribution guide](technical_guides/contribute/index.md).

## Useful links

- Source code: [github.com/georchestra/georchestra-gateway](https://github.com/georchestra/georchestra-gateway)
- Default configuration (datadir): [github.com/georchestra/datadir](https://github.com/georchestra/datadir/tree/master/gateway)
- Docker image: [georchestra/gateway](https://hub.docker.com/r/georchestra/gateway)
- geOrchestra website: [www.georchestra.org](https://www.georchestra.org/)

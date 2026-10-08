# Technical guides

These guides are intended for the people who **install**, **configure** and **develop** the
geOrchestra Gateway.

## [Installation](installation/index.md)

- [Presentation](installation/presentation.md): what the gateway does with a request, and how it
  fits in a geOrchestra platform.
- [Prerequisites](installation/prerequisites.md): Java, LDAP, datadir, optional Redis.
- [Installation](installation/installation.md): Docker image, standalone JAR, building from source.
- [Configuration files](installation/configuration.md): how the configuration is loaded from the
  geOrchestra datadir.
- [Debug](installation/debug.md): logs, diagnostic endpoints, common errors.
- [Tests](installation/tests.md): checking that an installation works.

## [Administration](administration/index.md)

Detailed configuration of each feature: routing, access rules, `sec-*` headers, authentication
(LDAP, OAuth2/OpenID Connect, pre-authentication), account creation, UI customization, sessions,
logging and monitoring, plus a [reference of all configuration properties](administration/properties.md).

## [Contribute](contribute/index.md)

How to build, test and extend the gateway: [architecture](contribute/architecture.md) of the code
and [extension points](contribute/extensions.md).

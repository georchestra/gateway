# Configuration files

The gateway is a Spring Boot application: its configuration is a set of
[Spring Boot properties](https://docs.spring.io/spring-boot/reference/features/external-config.html),
read from YAML / properties files, environment variables and system properties.

## Loading order

1. The gateway embeds a default `application.yml` (see
   [`gateway/src/main/resources/application.yml`](https://github.com/georchestra/georchestra-gateway/blob/main/gateway/src/main/resources/application.yml)).
2. This file imports, if they exist:
    - `${georchestra.datadir}/default.properties`
    - `${georchestra.datadir}/gateway/gateway.yaml`

    `georchestra.datadir` defaults to `/etc/georchestra`.
3. In the official datadir, `gateway/gateway.yaml` in turn imports the other files of the directory:

    ```yaml
    spring.config.import: application.yaml, security.yaml, routes.yaml, roles-mappings.yaml, logging.yaml
    ```

Values coming from imported files override the values of the file that imports them, so the datadir
always overrides the embedded defaults. Environment variables and Java system properties (`-D...`)
override all files.

!!! warning "Lists are replaced, not merged"
    When a property holding a list (for example `spring.cloud.gateway.server.webflux.default-filters`
    or `georchestra.gateway.global-access-rules`) is defined in the datadir, it **replaces** the whole
    list defined in the embedded `application.yml`. Copy the default entries you want to keep.

## The `gateway` directory of the datadir

The file split below is the one of the official [datadir](https://github.com/georchestra/datadir/tree/master/gateway).
It is only a convention: any property can be put in any of the imported files.

| File | Content | Documentation |
|---|---|---|
| `gateway.yaml` | Imports the other files. `georchestra.gateway.*` properties: default headers, global access rules, services (targets, per-service access rules and headers). | [Services and routes](../administration/services_and_routes.md), [Access rules](../administration/access_rules.md), [Security headers](../administration/headers.md) |
| `application.yaml` | Generic Spring Boot / Spring Cloud Gateway settings: server, session timeout, secure headers, Redis sessions, UI customization. | [Sessions](../administration/sessions.md), [UI customization](../administration/ui_customization.md) |
| `security.yaml` | `georchestra.gateway.security.*`: LDAP, OAuth2/OIDC, pre-authentication, account creation. `spring.security.oauth2.client.*`: OAuth2 client registrations. | [LDAP](../administration/ldap.md), [OAuth2/OIDC](../administration/oauth2.md), [Pre-authentication](../administration/preauth.md), [Account creation](../administration/accounts.md) |
| `routes.yaml` | `spring.cloud.gateway.server.webflux.routes`: the routes. | [Services and routes](../administration/services_and_routes.md) |
| `roles-mappings.yaml` | `georchestra.gateway.roles-mappings`. | [Role mappings](../administration/roles_mappings.md) |
| `logging.yaml` | Log levels, access log and MDC settings. | [Logging](../administration/logging.md) |
| `templates/` | Optional custom login, success and error pages, and static resources. | [UI customization](../administration/ui_customization.md) |

!!! warning "Properties that need their exact name"
    Some properties are read on their exact name, without Spring Boot relaxed binding:

    - `georchestra.gateway.security.ldap.<name>.enabled` must be declared with this exact name in a
      YAML/properties file or as a `-D` system property. An environment variable can override the
      value of a declared configuration, but cannot declare a new one.
    - `georchestra.gateway.security.createNonExistingUsersInLDAP` must be written in camel case in YAML
      and properties files: the kebab-case form (`create-non-existing-users-in-ldap`) does not
      activate the feature.

    The same applies to `georchestra.gateway.logoutUrl`, `georchestra.gateway.loginRedirectAllowList`
    and `georchestra.gateway.headerEnabled`: write them in camel case (`logout-url` is not read).

## Properties read from `default.properties`

`default.properties` contains properties shared by all geOrchestra applications. The gateway reads
the following ones (values between parentheses are the defaults applied when the property is not
defined):

| Property | Used for |
|---|---|
| `ldapScheme`, `ldapHost`, `ldapPort` | URL of the `default` LDAP configuration embedded in the gateway. |
| `ldapBaseDn` (default `dc=georchestra,dc=org`) | Base DN of the `default` LDAP configuration. |
| `ldapUsersRdn` (`ou=users`), `ldapUserSearchFilter` (`(uid={0})`) | Users of the `default` LDAP configuration. |
| `ldapRolesRdn` (`ou=roles`), `ldapRolesSearchFilter` (`(member={0})`) | Roles of the `default` LDAP configuration. |
| `ldapOrgsRdn` (`ou=orgs`) | Organizations of the `default` LDAP configuration. |
| `defaultOrganization` | Default value of `georchestra.gateway.security.defaultOrganization`. |
| `headerUrl`, `headerHeight`, `headerScript`, `headerConfigFile`, `logoUrl`, `georchestraStylesheet`, `useLegacyHeader` | geOrchestra header and look of the login, success and error pages. See [UI customization](../administration/ui_customization.md). |

The official `security.yaml` also uses `${ldapAdminDn:...}` and `${ldapAdminPassword:...}`
placeholders for the LDAP admin credentials.

## Profiles

Spring profiles can be activated with `SPRING_PROFILES_ACTIVE` (or `-Dspring.profiles.active`).
Profile-specific settings are written in YAML documents starting with
`spring.config.activate.on-profile: <profile>`.

Profiles with a built-in meaning:

| Profile | Effect |
|---|---|
| `json-logs` | Logs are written to the console as JSON (Logstash encoder) and include the MDC attributes. See [Logging](../administration/logging.md). |
| `dev` | Development only: imports `../datadir/default.properties`, `../datadir/gateway/gateway.yaml` and `../datadir/gateway/security.yaml`, relative to the working directory. |

The official `logging.yaml` also defines the `logging_debug` and `logging_debug_security` profiles,
which raise the log levels.

## Spring Cloud Gateway properties prefix

The gateway uses Spring Cloud Gateway 4.3, where the server properties live under
`spring.cloud.gateway.server.webflux.*` (for example
`spring.cloud.gateway.server.webflux.routes`). The legacy `spring.cloud.gateway.*` names used by
older datadirs are still accepted: Spring Cloud Gateway migrates them at startup and logs a
deprecation warning. Prefer the new prefix.

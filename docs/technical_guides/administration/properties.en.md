# Configuration reference

This page lists all the configuration properties defined or read by the gateway code. Standard Spring
Boot, Spring Security and Spring Cloud Gateway properties can also be used: refer to their
documentation.

"Default" is the value applied when the property is not set anywhere. When the embedded
`application.yml` sets another value, it is given in the "Embedded" column.

Spring Boot relaxed binding applies to the properties bound to Java classes (e.g. `default-headers`
and `defaultHeaders` are equivalent), with the exceptions listed in
[Configuration files](../installation/configuration.md).

## General

| Property | Type | Default | Embedded | Description |
|---|---|---|---|---|
| `georchestra.datadir` | path | | `/etc/georchestra` | geOrchestra datadir. |
| `georchestra.gateway.logoutUrl` | URL | `/?logout` | | Redirection after logout. |
| `georchestra.gateway.loginRedirectAllowList` | list of URL prefixes | empty | | Allowed targets of `/login?redirect=`. |
| `georchestra.gateway.headerEnabled` | boolean | `true` | | Show the geOrchestra header on the login page. |

## Routing, headers and access rules

Defined in `GatewayConfigProperties` (prefix `georchestra.gateway`).

| Property | Type | Default | Description |
|---|---|---|---|
| `georchestra.gateway.services.<name>.target` | URI | | Back-end URL. See [Services and routes](services_and_routes.md). |
| `georchestra.gateway.services.<name>.headers.*` | headers | | Per-service `sec-*` headers. |
| `georchestra.gateway.services.<name>.access-rules` | list of rules | empty | Per-service access rules. |
| `georchestra.gateway.default-headers.*` | headers | all disabled | Default `sec-*` headers. |
| `georchestra.gateway.global-access-rules` | list of rules | empty | Global access rules. |
| `georchestra.gateway.roles-mappings` | map of role pattern → list of roles | empty | See [Role mappings](roles_mappings.md). |

**Headers** (`default-headers.*` and `services.<name>.headers.*`), all booleans, see
[Security headers](headers.md): `proxy`, `userid`, `username`, `roles`, `org`, `email`, `firstname`,
`lastname`, `tel`, `address`, `title`, `notes`, `last-updated`, `json-user`, `orgname`, `orgid`,
`org-last-updated`, `json-organization`.

**Access rules** (`access-rules[*]` and `global-access-rules[*]`), see [Access rules](access_rules.md):

| Property | Type | Default |
|---|---|---|
| `intercept-url` | list of path patterns | empty (invalid) |
| `forbidden` | boolean | `false` |
| `anonymous` | boolean | `false` |
| `allowed-roles` | list of roles | empty |

## Security

Defined in `GeorchestraGatewaySecurityConfigProperties` (prefix `georchestra.gateway.security`).

| Property | Type | Default | Embedded | Description |
|---|---|---|---|---|
| `createNonExistingUsersInLDAP` | boolean | off (feature disabled when not set) | `false` | Create LDAP accounts on first login. The Java field defaults to `true`, but the feature is only activated by an explicit `true`. See [Account creation](accounts.md). |
| `defaultOrganization` | string | empty | `${defaultOrganization:}` | Organization of new accounts without organization. |
| `moderatedSignup` | boolean | `false` | | Accounts created for pre-authenticated users without `preauth-provider` are pending. OAuth2 accounts use `oidc.config...moderatedSignup` instead. |
| `disableLdapFormLogin` | boolean | `false` | | Hide the LDAP login form. |
| `delayAfterLoginSeconds` | integer | `0` | `0` | Delay of the "Login successful" page. `0` disables it. |
| `disableUidTransformation` | string | empty | | OAuth2 registration id whose usernames are not transformed. |

### LDAP — `georchestra.gateway.security.ldap.<name>.*`

See [LDAP authentication](ldap.md).

| Property | Type | Default | Embedded (`default` only) |
|---|---|---|---|
| `enabled` | boolean | `false` | `false` |
| `extended` | boolean | `false` | `true` |
| `activeDirectory` | boolean | `false` | |
| `url` | string | | `${ldapScheme}://${ldapHost}:${ldapPort}` |
| `baseDn` | string | | `${ldapBaseDn:dc=georchestra,dc=org}` |
| `adminDn` | string | | |
| `adminPassword` | string | | |
| `users.rdn` | string | | `${ldapUsersRdn:ou=users}` |
| `users.searchFilter` | string | `(&(objectClass=user)(userPrincipalName={0}))` for Active Directory, none otherwise | `${ldapUserSearchFilter:(uid={0})}` |
| `users.returningAttributes` | list | all attributes | |
| `roles.rdn` | string | | `${ldapRolesRdn:ou=roles}` |
| `roles.searchFilter` | string | | `${ldapRolesSearchFilter:(member={0})}` |
| `orgs.rdn` | string | `ou=orgs` (when the `orgs` block exists) | `${ldapOrgsRdn:ou=orgs}` |
| `orgs.pendingRdn` | string | `ou=pendingorgs` (when the `orgs` block exists) | |

### Header pre-authentication

| Property | Type | Default | Description |
|---|---|---|---|
| `georchestra.gateway.security.header-authentication.enabled` | boolean | `false` | See [Header pre-authentication](preauth.md). |

### OAuth2 / OpenID Connect

See [OAuth2 and OpenID Connect](oauth2.md).

| Property | Type | Default | Embedded | Description |
|---|---|---|---|---|
| `georchestra.gateway.security.oauth2.enabled` | boolean | `false` | `false` | Enable OAuth2 / OIDC login. |
| `georchestra.gateway.security.oauth2.proxy.enabled` | boolean | `false` | `false` | Use an HTTP proxy for the OAuth2 client. |
| `georchestra.gateway.security.oauth2.proxy.host` | string | | `localhost` | Proxy host. |
| `georchestra.gateway.security.oauth2.proxy.port` | integer | | `8000` | Proxy port. |
| `georchestra.gateway.security.oauth2.proxy.username` | string | | `jack` | Proxy user. |
| `georchestra.gateway.security.oauth2.proxy.password` | string | | `insecure` | Proxy password. |
| `spring.security.oauth2.client.provider.<id>.end-session-uri` | URL | | | End session endpoint, for providers without discovery metadata. |

The embedded proxy host, port, user and password are placeholders, only used when
`proxy.enabled` is `true`.

**Claims mapping** — `georchestra.gateway.security.oidc.claims.*` (global) and
`georchestra.gateway.security.oidc.claims.provider.<id>.*` (per provider):

| Property | Type | Default | Scope |
|---|---|---|---|
| `id.path`, `id.value` | list of strings | empty | global and provider |
| `organization.path`, `organization.value` | list of strings | empty | global and provider |
| `roles.json.path`, `roles.json.value` | list of strings | empty | global and provider |
| `roles.uppercase` | boolean | `true` | global and provider |
| `roles.normalize` | boolean | `true` | global and provider |
| `roles.append` | boolean | `true` | global and provider |
| `roles.splitcsv` | boolean | `false` | global and provider |
| `roles.remove-prefix` | string | | global and provider |
| `organizationUid.path`, `organizationUid.value` | list of strings | empty | provider only |
| `email.path`, `email.value` | list of strings | empty | provider only |
| `familyName.path`, `familyName.value` | list of strings | empty | provider only |
| `givenName.path`, `givenName.value` | list of strings | empty | provider only |

"Provider only" properties can be set at the global level but are ignored there.

**Provider options** — `georchestra.gateway.security.oidc.config.*` (global) and
`georchestra.gateway.security.oidc.config.provider.<id>.*` (per provider, wins). See
[Account creation](accounts.md#per-provider-options).

| Property | Type | Default | Scope |
|---|---|---|---|
| `searchEmail` | boolean | `false` | global and provider |
| `moderatedSignup` | boolean | `false` | global and provider |
| `overrideExistingOrgName` | boolean | `false` | global and provider |
| `orgNameResolvers` | list of strings | empty | global and provider |
| `authoritative` | boolean | `false` | provider only |

### Account creation events and SIRENE

| Property | Type | Default | Description |
|---|---|---|---|
| `georchestra.gateway.security.events.accountcreated.url` | URL | | When set, `POST` an event to this URL for each account created. See [Account creation](accounts.md#account-creation-events). |
| `georchestra.gateway.security.sirene.enabled` | boolean | `false` | Enable the SIRENE organization name resolver. |
| `georchestra.gateway.security.sirene.api-key` | string | | INSEE API key. |
| `georchestra.gateway.security.sirene.base-url` | URL | `https://api.insee.fr/api-sirene/3.11` | SIRENE API base URL. |

## Sessions

See [Sessions](sessions.md).

| Property | Type | Default | Description |
|---|---|---|---|
| `spring.session.redis.enabled` | boolean | `false` | Store sessions in Redis. |
| `spring.session.redis.host` | string | `localhost` | Redis host. |
| `spring.session.redis.port` | integer | `6379` | Redis port. |
| `spring.session.redis.password` | string | empty | Redis password. |

## User interface

See [UI customization](ui_customization.md). These properties have no `georchestra.` prefix: they are
shared with the other geOrchestra applications through `default.properties`.

| Property | Type | Default |
|---|---|---|
| `headerScript` | URL | `https://cdn.jsdelivr.net/gh/georchestra/header@dist/header.js` |
| `headerUrl` | URL | `/header/` |
| `useLegacyHeader` | boolean | `false` |
| `headerHeight` | integer | `80` |
| `headerConfigFile` | URL | empty |
| `logoUrl` | URL | empty |
| `georchestraStylesheet` | URL | empty |

The gateway also reads `spring.messages.basename` (additional message bundles) and
`spring.webflux.static-path-pattern` (or `spring.mvc.static-path-pattern`) to build the URLs of the
OAuth2 provider icons.

## Logging

See [Logging](logging.md).

| Property | Type | Default |
|---|---|---|
| `logging.mdc.enabled` | boolean | `true` |
| `logging.mdc.include.http.id` | boolean | `true` |
| `logging.mdc.include.http.method` | boolean | `true` |
| `logging.mdc.include.http.path` | boolean | `true` |
| `logging.mdc.include.http.query-string` | boolean | `false` |
| `logging.mdc.include.http.parameters` | boolean | `false` |
| `logging.mdc.include.http.headers` | boolean | `false` |
| `logging.mdc.include.http.headers-pattern` | regular expression | `.*` |
| `logging.mdc.include.http.cookies` | boolean | `false` |
| `logging.mdc.include.http.remote-addr` | boolean | `false` |
| `logging.mdc.include.http.remote-host` | boolean | `false` |
| `logging.mdc.include.http.session-id` | boolean | `false` |
| `logging.mdc.include.user.id` | boolean | `true` |
| `logging.mdc.include.user.roles` | boolean | `false` |
| `logging.mdc.include.user.org` | boolean | `false` |
| `logging.mdc.include.user.extras` | boolean | `false` |
| `logging.mdc.include.user.auth-method` | boolean | `false` |
| `logging.mdc.include.app.name` | boolean | `true` |
| `logging.mdc.include.app.version` | boolean | `true` |
| `logging.mdc.include.app.profile` | boolean | `true` |
| `logging.mdc.include.app.instance-id` | boolean | `false` |
| `logging.accesslog.enabled` | boolean | `true` |
| `logging.accesslog.info` | list of regular expressions | empty |
| `logging.accesslog.debug` | list of regular expressions | empty |
| `logging.accesslog.trace` | list of regular expressions | empty |

## Other properties read by the gateway

| Property | Description |
|---|---|
| `info.instance-id` | Logged at startup. Embedded value: `${spring.application.name}:${spring.application.instance_id:${spring.cloud.client.ip-address}}:${server.port}`. |
| `spring.application.instance-id` | Value of the `application.instance-id` MDC attribute (`null` when not set; the embedded configuration does not set it). |
| `logging.accesslog.webflux.enabled` | Only used by the plain WebFlux access log filter, which is not active in the gateway. |
| `ldapScheme`, `ldapHost`, `ldapPort`, `ldapBaseDn`, `ldapUsersRdn`, `ldapUserSearchFilter`, `ldapRolesRdn`, `ldapRolesSearchFilter`, `ldapOrgsRdn`, `defaultOrganization` | Placeholders of the embedded `application.yml`, normally defined in `default.properties`. `ldapScheme`, `ldapHost` and `ldapPort` have no fallback. See [Configuration files](../installation/configuration.md#properties-read-from-defaultproperties). |

## Embedded Spring settings

Main standard properties set by the embedded `application.yml`:

| Property | Value |
|---|---|
| `server.port` | `8080` |
| `server.compression.enabled` | `true` |
| `server.http2.enabled` | `${server.ssl.enabled}` |
| `server.ssl.enabled` | `false` |
| `server.forward-headers-strategy` | `FRAMEWORK` |
| `spring.application.name` | `gateway-service` |
| `spring.thymeleaf.cache` | `false` |
| `spring.cloud.gateway.server.webflux.default-filters` | `SecureHeaders`, `RemoveSecurityHeaders`, `AddSecHeaders`, `PreserveHostHeader`, `ApplicationError`, `LoginParamRedirect` |
| `spring.cloud.gateway.server.webflux.global-filter.websocket-routing.enabled` | `true` |
| `spring.cloud.gateway.server.webflux.metrics.enabled` | `true` |
| `spring.cloud.gateway.server.webflux.filter.secure-headers.*` | see [Services and routes](services_and_routes.md#default-filters) |
| `management.server.port` | `8090` |
| `management.endpoints.web.exposure.include` | `*` |
| `management.endpoint.health.probes.enabled` | `true` |
| `management.endpoint.health.show-details` | `always` |

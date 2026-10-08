# Architecture

## Technical stack

- Java 21, Spring Boot 3, Spring Cloud Gateway 4.3 (`spring-cloud-starter-gateway-server-webflux`):
  reactive application running on Reactor Netty.
- Spring Security (WebFlux): form login, HTTP Basic, OAuth2 client, LDAP.
- geOrchestra libraries: `georchestra-commons` (`SecurityHeaders`, `GeorchestraUser`, `Organization`,
  and the `UsersApi` / `OrganizationsApi` interfaces) and `georchestra-ldap-account-management` (LDAP
  DAOs and the LDAP implementations of these APIs, used to read and create accounts).
- Thymeleaf for the login, success and error pages.
- Optional Spring Session Data Redis for the sessions.

## Maven modules

```
georchestra-gateway-parent
├── modules
│   ├── logging          georchestra-gateway-logging: MDC propagation, access log
│   ├── org-resolvers    georchestra-gateway-org-resolvers: OrganizationNameResolver SPI
│   └── sirene           georchestra-gateway-sirene: SIRENE implementation of the SPI
└── gateway              georchestra-gateway: the application
```

## Packages of the `gateway` module

| Package | Content |
|---|---|
| `org.georchestra.gateway.app` | `GeorchestraGatewayApplication` (main class), `LoginLogoutController` (`/login`, `/success`), `WhoamiController` (`/whoami`). |
| `org.georchestra.gateway.autoconfigure.*` | Spring Boot auto-configurations, see below. |
| `org.georchestra.gateway.model` | Configuration model: `GatewayConfigProperties` (`georchestra.gateway.*`), `Service`, `RoleBasedAccessRule`, `HeaderMappings`, and helpers to store the user / organization / target in the request attributes. |
| `org.georchestra.gateway.security` | `GatewaySecurityConfiguration` (security filter chain), `GeorchestraGatewaySecurityConfigProperties` (`georchestra.gateway.security.*`), `GeorchestraUserMapper`, `ResolveGeorchestraUserGlobalFilter`, `RolesMappingsUserCustomizer`, extension interfaces. |
| `...security.accessrules` | `AccessRulesCustomizer`: turns the access rules into Spring Security authorization rules. |
| `...security.ldap`, `.basic`, `.extended` | LDAP authentication providers and user mappers. |
| `...security.oauth2` | OAuth2 / OIDC configuration, user mappers, claims mapping, logout. |
| `...security.preauth` | Header pre-authentication. |
| `org.georchestra.gateway.accounts.admin` | `AccountManager`, `CreateAccountUserCustomizer` and the LDAP implementation `LdapAccountsManager`. |
| `org.georchestra.gateway.accounts.events` | `AccountCreatedEventSender`: HTTP notification of account creations. |
| `org.georchestra.gateway.filter.global` | Global filters and the `ApplicationError` / `LoginParamRedirect` filter factories. |
| `org.georchestra.gateway.filter.headers` | `AddSecHeaders`, `RemoveHeaders`, `RemoveSecurityHeaders`, `CookieAffinity` filter factories and the `HeaderContributor`s. |
| `org.georchestra.gateway.handler.predicate` | `QueryParam` predicate factory. |
| `org.georchestra.gateway.session.redis` | Redis session configuration. |
| `org.geoserver.cloud.gateway.*` | Filters and predicates coming from GeoServer Cloud: `GlobalUriFilter`, `RouteProfile`, `StripBasePath`, `RegExpQuery`. |

## Auto-configurations

Features are wired by auto-configurations listed in
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:

| Auto-configuration | Condition | Provides |
|---|---|---|
| `FiltersAutoConfiguration` | always | Global filters, filter and predicate factories, `HeaderFiltersConfiguration`. |
| `RoutePredicateFactoriesAutoConfiguration` | always | `QueryParam` predicate. |
| `ErrorCustomizerAutoConfiguration` | always | `CustomErrorAttributes` (503 for connection errors, 403 for access denied). |
| `WebSecurityAutoConfiguration` | Spring Boot default web security | `GatewaySecurityConfiguration`, `AccessRulesConfiguration`. |
| `LdapSecurityAutoConfiguration` | at least one `georchestra.gateway.security.ldap.<name>.enabled=true` | `LdapAuthenticationConfiguration` (basic and extended LDAP). |
| `OAuth2SecurityAutoConfiguration` | `georchestra.gateway.security.oauth2.enabled=true` | `OAuth2Configuration`. |
| `HeaderPreAuthenticationAutoConfiguration` | `georchestra.gateway.security.header-authentication.enabled=true` | `HeaderPreAuthenticationConfiguration`. |
| `GeorchestraLdapAccountsCreationAutoConfiguration` | LDAP enabled, `ldap.default.enabled=true` and `createNonExistingUsersInLDAP=true` | `GeorchestraLdapAccountManagementConfiguration`; fails if there is no extended LDAP configuration. |
| `EventsAutoConfiguration` | account creation enabled and `georchestra.gateway.security.events.accountcreated.url` set | `AccountCreatedEventSender`. |
| `RedisSessionAutoConfiguration` | `spring.session.redis.enabled=true` | `RedisSessionConfiguration`. |
| `GatewayMdcAutoConfiguration` (logging module) | reactive application with Spring Cloud Gateway | MDC and access log global filters (`logging.mdc.enabled`, `logging.accesslog.enabled`). |
| `LoggingMDCWebFluxAutoConfiguration` (logging module) | reactive application | MDC web filter (`logging.mdc.enabled`). |
| `AccessLogWebFluxAutoConfiguration` (logging module) | reactive application **without** Spring Cloud Gateway | Access log web filter. Not active in the gateway. |
| `SireneAutoConfiguration` (sirene module) | `georchestra.gateway.security.sirene.enabled=true` | `SireneOrganizationNameResolver`. |

## Request processing

```
HTTP request
 │
 ├─ Spring Security WebFilterChain (GatewaySecurityConfiguration)
 │    ├─ pre-authentication filter (if enabled), OAuth2 redirect parameter filter
 │    ├─ form login (POST /login), HTTP Basic, OAuth2 login, logout (GET /logout)
 │    └─ authorization: access rules (AccessRulesCustomizer)
 │
 ├─ Spring Cloud Gateway: route matching (predicates)
 │
 └─ Gateway filter chain, ordered:
      ApplicationError (highest precedence)
      default filters / route filters (SecureHeaders, RemoveSecurityHeaders, ...)
      RouteToRequestUrlFilter
      ResolveGeorchestraUserGlobalFilter   user + organization → exchange attributes
      ResolveTargetGlobalFilter            service headers and access rules → exchange attributes
      AddSecHeaders                        sec-* headers from the HeaderContributors
      MdcUserAndOrgGlobalFilter
      load balancer, GlobalUriFilter
      Netty routing filter                 proxies the request
```

### User resolution

`ResolveGeorchestraUserGlobalFilter` calls `GeorchestraUserMapper.resolve(Authentication)`:

1. the `GeorchestraUserMapperExtension` beans are called in order until one returns a user.
   `OpenIdConnectUserMapper` has the highest precedence (it must run before `OAuth2UserMapper`); the
   other ones (`OAuth2UserMapper`, `GeorchestraLdapAuthenticatedUserMapper`,
   `BasicLdapAuthenticatedUserMapper`, `PreauthenticatedUserMapperExtension`) use the default order
   `0` and each only handles its own type of authentication;
2. the `GeorchestraUserCustomizerExtension` beans are applied in order to the user:
   `RolesMappingsUserCustomizer`, then `CreateAccountUserCustomizer` (lowest precedence, when account
   creation is enabled).

The resolved user and, for extended LDAP users, its organization, are stored in the exchange
attributes (`GeorchestraUsers`, `GeorchestraOrganizations`) for the following filters.

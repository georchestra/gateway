# Extension points

The gateway is assembled from Spring beans implementing a few interfaces. Adding a bean implementing
one of them (in the gateway itself, or in a module added to the classpath with its own
auto-configuration) is enough to extend the behaviour.

## `ServerHttpSecurityCustomizer`

`org.georchestra.gateway.security.ServerHttpSecurityCustomizer` customizes the Spring Security
`ServerHttpSecurity` before the security filter chain is built. All the beans are applied, sorted by
`getOrder()` (default `0`). The LDAP, OAuth2, pre-authentication and access rules features are
implemented this way.

```java
@Bean
ServerHttpSecurityCustomizer myCustomizer() {
    return http -> http.addFilterAt(new MyWebFilter(), SecurityWebFiltersOrder.FIRST);
}
```

## `GeorchestraUserMapperExtension`

`org.georchestra.gateway.security.GeorchestraUserMapperExtension` converts a Spring Security
`Authentication` into a `GeorchestraUser`. The extensions are called in `getOrder()` order; the first one
returning a non-empty `Optional` wins. Implement it to support a new kind of authentication.

```java
public interface GeorchestraUserMapperExtension extends Ordered {
    Optional<GeorchestraUser> resolve(Authentication authToken);
    default int getOrder() { return 0; }
}
```

## `GeorchestraUserCustomizerExtension`

`org.georchestra.gateway.security.GeorchestraUserCustomizerExtension` is a
`BiFunction<Authentication, GeorchestraUser, GeorchestraUser>` applied to the user returned by the
mappers, in `getOrder()` order. Role mappings (`RolesMappingsUserCustomizer`, order `0`) and account
creation (`CreateAccountUserCustomizer`, lowest precedence) are customizers.

```java
@Bean
GeorchestraUserCustomizerExtension addDefaultRole() {
    return (auth, user) -> {
        user.getRoles().add("ROLE_MY_DEFAULT");
        return user;
    };
}
```

## `HeaderContributor`

`org.georchestra.gateway.filter.headers.HeaderContributor` is the abstract class used by the
`AddSecHeaders` filter to add request headers. Each `HeaderContributor` bean is called for each proxied
request:

```java
public abstract Consumer<HttpHeaders> prepare(ServerWebExchange exchange);
```

The resolved user, organization and target configuration are available with
`GeorchestraUsers.resolve(exchange)`, `GeorchestraOrganizations.resolve(exchange)` and
`GeorchestraTargetConfig.getTarget(exchange)`.

## `OrganizationNameResolver`

`org.georchestra.gateway.orgresolvers.OrganizationNameResolver` (module `org-resolvers`) resolves an
organization name from an identifier when the gateway creates or updates organizations
(see [Account creation](../administration/accounts.md#organization-name-resolution)).

```java
public interface OrganizationNameResolver {
    /** Name of the entry to use in orgNameResolvers, e.g. "sirene" */
    String getOrgNameResolverEntry();
    Optional<ResolvedOrganization> resolve(String organizationIdentifier);
}
```

The SIRENE module is the reference implementation. The account manager receives a single, optional
resolver: only one `OrganizationNameResolver` bean can be active at a time.

## `AccountCreated` events

When the gateway creates an account, it publishes an
`org.georchestra.gateway.accounts.admin.AccountCreated` Spring application event. Any bean can listen
to it:

```java
@EventListener
void onAccountCreated(AccountCreated event) {
    GeorchestraUser user = event.getUser();
    // ...
}
```

`AccountCreatedEventSender` (HTTP notification) is implemented this way.

## Route filters and predicates

Custom Spring Cloud Gateway filters and predicates are regular `GatewayFilterFactory` /
`RoutePredicateFactory` beans: see `FiltersAutoConfiguration` and the existing factories in
`org.georchestra.gateway.filter` for examples.

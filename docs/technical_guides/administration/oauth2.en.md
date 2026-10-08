# OAuth2 and OpenID Connect

The gateway can delegate authentication to external OAuth2 or OpenID Connect (OIDC) identity
providers: Keycloak, Google, GitHub, ProConnect, a CAS server, etc. It relies on the standard
[Spring Security OAuth2 client](https://docs.spring.io/spring-security/reference/reactive/oauth2/login/core.html).

## Enabling OAuth2

```yaml
georchestra:
  gateway:
    security:
      oauth2:
        enabled: true
```

When `enabled` is `false` (the default), the OAuth2 login flow is not configured, even if client
registrations are declared. Comment the registrations out as well in that case: the login page lists
every declared registration.

## Declaring providers

Providers are declared with the standard Spring Boot properties
`spring.security.oauth2.client.registration.<id>` and `spring.security.oauth2.client.provider.<id>`.

### OpenID Connect provider (e.g. Keycloak)

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          keycloak:
            client-name: Keycloak
            client-id: georchestra
            client-secret: ${KEYCLOAK_CLIENT_SECRET}
            authorization-grant-type: authorization_code
            redirect-uri: "{baseUrl}/login/oauth2/code/keycloak"
            scope: openid, email, profile
        provider:
          keycloak:
            issuer-uri: https://keycloak.example.org/realms/georchestra
            user-name-attribute: preferred_username
```

With `issuer-uri`, the other endpoints are discovered from the provider metadata
(`.well-known/openid-configuration`).

### Plain OAuth2 provider (e.g. CAS)

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          cas-oauth2:
            client-id: external-oauth2
            client-secret: ${CAS_CLIENT_SECRET}
            authorization-grant-type: authorization_code
            redirect-uri: "{baseUrl}/login/oauth2/code/cas-oauth2"
        provider:
          cas-oauth2:
            authorization-uri: https://cas.example.org/cas/oauth2.0/authorize
            token-uri: https://cas.example.org/cas/oauth2.0/accessToken
            user-info-uri: https://cas.example.org/cas/oauth2.0/profile
            user-name-attribute: id
```

### Well-known providers

Spring Security knows the endpoints of `google`, `github` and `facebook`: when the registration id
is one of these names, only the client id and secret are needed.

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            scope: openid, email, profile
```

### Logout endpoint

On `/logout`, if the user logged in through an OIDC provider that advertises an
`end_session_endpoint`, the gateway redirects the user to it, with `{baseUrl}/login?logout` as the
post-logout redirect URI. For providers that are not configured with an `issuer-uri`, the end session
endpoint can be given with the gateway-specific `end-session-uri` property:

```yaml
spring:
  security:
    oauth2:
      client:
        provider:
          myprovider:
            authorization-uri: https://idp.example.org/authorize
            token-uri: https://idp.example.org/token
            user-info-uri: https://idp.example.org/userinfo
            jwk-set-uri: https://idp.example.org/jwks
            end-session-uri: https://idp.example.org/logout
```

Other users are redirected to `georchestra.gateway.logoutUrl` (`/?logout` by default).

## Login page

The login page shows one icon button per registration, linking to `/oauth2/authorization/<id>`.
The registration `client-name` (the registration id if not set) is used as the tooltip and
alternative text of the button, and its icon is
`login/img/<id>.png`, searched in the static resource locations (see
[UI customization](ui_customization.md)); `login/img/default.png` is used when not found. Icons are
provided for `google`, `github` and `proconnect`.

If there is exactly **one** registration and the LDAP login form is not available, `/login` directly
redirects to `/oauth2/authorization/<id>`.

!!! warning
    In the default `login.html`, the provider buttons are displayed inside the LDAP login form block:
    they are only visible when the LDAP form login is enabled. With several providers and no LDAP form,
    adapt the template (see [UI customization](ui_customization.md)).

    For the same reason, error messages such as *User is pending validation* are not visible without
    the LDAP form; with a single provider, `/login?error=...` immediately redirects to the provider.

## HTTP proxy

If the gateway must use an HTTP proxy to reach the providers:

```yaml
georchestra:
  gateway:
    security:
      oauth2:
        proxy:
          enabled: true
          host: proxy.example.org
          port: 3128
          username: user
          password: secret
```

The proxy credentials are always sent: the embedded `application.yml` defines placeholder values
(`localhost:8000`, `jack` / `insecure`), so set `host`, `port`, `username` and `password` explicitly
when enabling the proxy.

When `proxy.enabled` is `false`, the OAuth2 HTTP client uses the JVM proxy system properties
(`http.proxyHost`, `https.proxyHost`, `http.nonProxyHosts`, ...) if they are set.

## How the user is built

### Plain OAuth2

| geOrchestra user | Source |
|---|---|
| username | the `login` attribute of the user info if present, otherwise the principal name (the `user-name-attribute`) |
| email | the `email` attribute |
| roles | the authorities given by Spring Security, except those starting with `SCOPE_` or `ROLE_SCOPE_` |

### OpenID Connect

When the provider returns an ID token, the standard claims are mapped first:

| geOrchestra user | Claim |
|---|---|
| id | `sub` |
| username | `preferred_username`, or `sub` if absent |
| firstname | `given_name` |
| lastname | `family_name` |
| email | `email` |
| telephone | `phone_number` |
| postal address | `address.formatted` |

Then the [custom claims](#custom-claims-mapping) are applied, first the global ones, then the
provider-specific ones.

Finally, the **username is transformed** to avoid collisions between providers: it becomes
`<registration id>_<username>`, lower-cased, every character other than `a-z`, `A-Z`, `0-9`, `-` and
`_` being replaced by `_`. For example `John.Doe@example.org` logged in through `keycloak` becomes
`keycloak_john_doe_example_org`.

The transformation can be disabled for **one** provider:

```yaml
georchestra:
  gateway:
    security:
      disableUidTransformation: keycloak   # registration id
```

The username is then used as-is.

## Custom claims mapping

Non-standard claims can be mapped to the geOrchestra user with [JSONPath](https://github.com/json-path/JsonPath)
expressions evaluated on the ID token claims.

```yaml
georchestra:
  gateway:
    security:
      oidc:
        claims:
          # applied to all OIDC providers
          organization.path: "$.PartyOrganisationID"
          roles:
            json.path: "$.groups_json..['name']"
            uppercase: true
            normalize: true
            append: true
          # applied to one provider, after the global mapping
          provider:
            proconnect:
              id.path: "$.sub"
              email.path: "$.email"
              familyName.path: "$.usual_name"
              givenName.path: "$.given_name"
              organization.path: "$.siret"
              organizationUid.path: "$.siret"
```

| Property | Global (`oidc.claims.`) | Provider (`oidc.claims.provider.<id>.`) | Sets |
|---|---|---|---|
| `id.path` | yes | yes | user id (`sec-userid`) |
| `roles.json.path` | yes | yes | roles (see below) |
| `organization.path` | yes | yes | organization short name (`sec-org`), used as the organization `cn` when the gateway creates accounts |
| `organizationUid.path` | no | yes | organization unique id (e.g. a SIRET number), used to find or create the organization |
| `email.path` | no | yes | email |
| `familyName.path` | no | yes | last name |
| `givenName.path` | no | yes | first name |

Each `path` is a list: several JSONPath expressions can be given (`path[0]`, `path[1]`, or a YAML
list). For single-valued attributes, the first value found is used. Each extractor also accepts a
`value` list, used as a fallback when the paths return nothing, e.g. `organization.value: MY_ORG`.

The values extracted by JSONPath must be strings, otherwise the login fails.

### Roles options

`roles` (global or per provider) accepts the following options:

| Property | Default | Description |
|---|---|---|
| `json.path` | | JSONPath expression(s) returning the role names. |
| `splitcsv` | `false` | Split each extracted value on `;`. |
| `remove-prefix` | | Literal prefix removed from each role name, if present. |
| `uppercase` | `true` | Upper-case the role names. |
| `normalize` | `true` | Replace whitespace by `_` and remove any character other than `a-z`, `A-Z`, `0-9` and `_`. Accented letters are removed, not converted: `Géo` becomes `GO` (`Go` without upper-casing). |
| `append` | `true` | Add the extracted roles to the existing ones (`true`) or replace them (`false`). |

Transformations are applied in this order: split, remove prefix, upper-case, normalize. For example
with the defaults, `GDI Planer (extern)` becomes `GDI_PLANER_EXTERN`.

!!! note "`ROLE_` prefix"
    Roles extracted from claims are **not** prefixed with `ROLE_`. Access rules always look for
    `ROLE_`-prefixed roles: either include the prefix in the role names (e.g. with
    `$.concat("ROLE_", ...)` or a [role mapping](roles_mappings.md)), or let the gateway
    [create the LDAP account](accounts.md) (roles read back from LDAP are prefixed).

## Provider behaviour options

`georchestra.gateway.security.oidc.config` holds options related to account creation, globally or per
provider (the provider value wins). They are described in [Account creation](accounts.md):

```yaml
georchestra:
  gateway:
    security:
      oidc:
        config:
          searchEmail: false
          provider:
            proconnect:
              searchEmail: true
              moderatedSignup: true
              authoritative: false
              overrideExistingOrgName: false
              orgNameResolvers:
                - sirene
                - identifier
```

## Forwarding the access token

To send the OAuth2 access token of the user to a back-end (`Authorization: Bearer ...`), add the
Spring Cloud Gateway `TokenRelay` filter to its route.

## Technical notes

- ID tokens signed with an HMAC algorithm (`HS256`, ...) are verified with the client secret; other
  tokens with the provider JWK set.
- User info responses of type `application/jwt` are supported.
- The user info endpoint is always called when it is configured and the access token carries no
  scope.

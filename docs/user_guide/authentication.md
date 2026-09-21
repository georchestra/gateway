# Authentication

geOrchestra Gateway supports multiple authentication methods:

1. LDAP authentication
2. OAuth2/OpenID Connect authentication
3. Pre-authentication via HTTP headers
4. Redirection
5. Delay after successful login

## LDAP Authentication

LDAP Authentication is the traditional authentication method for geOrchestra.

### Configuration

LDAP Authentication is enabled and configured through the following properties in `application.yml` or `gateway/security.yaml`:

```yaml
georchestra.security.ldap:
  enabled: true
  url: ${ldapScheme}://${ldapHost}:${ldapPort}
  baseDn: ${ldapBaseDn:dc=georchestra,dc=org}
  usersRdn: ${ldapUsersRdn:ou=users}
  userSearchFilter: ${ldapUserSearchFilter:(uid={0})}
  rolesRdn: ${ldapRolesRdn:ou=roles}
  rolesSearchFilter: ${ldapRolesSearchFilter:(member={0})}
```

Configuration parameters:

- `enabled` - Set to `true` to enable LDAP authentication
- `url` - LDAP server URL (e.g., `ldap://ldap:389`)
- `baseDn` - Base DN for LDAP searches (e.g., `dc=georchestra,dc=org`)
- `usersRdn` - RDN for users (e.g., `ou=users`)
- `userSearchFilter` - Filter to find users by username (e.g., `(uid={0})`)
- `rolesRdn` - RDN for roles (e.g., `ou=roles`)
- `rolesSearchFilter` - Filter to find roles for a user (e.g., `(member={0})`)

If `georchestra.security.ldap.enabled` is `false`, the login page won't show the username/password form inputs.

### Extended LDAP Configuration

The "Extended" LDAP configuration corresponds to geOrchestra's specific LDAP schema, which is used by the official `georchestra/ldap` Docker image. This schema includes specialized organizational units for users, roles, and organizations with specific attributes designed for geOrchestra features.

For this extended schema, additional configuration parameters are available:

```yaml
georchestra.security.ldap:
  # Basic authentication settings
  enabled: true
  url: ldap://ldap:389
  baseDn: dc=georchestra,dc=org

  # User search settings
  usersRdn: ou=users
  userSearchFilter: (uid={0})

  # Roles search settings
  rolesRdn: ou=roles
  rolesSearchFilter: (member={0})

  # Organizations settings - specific to geOrchestra LDAP schema
  orgsRdn: ou=orgs
  
  # Manager credentials (if anonymous bind is not allowed)
  managerDn: cn=admin,dc=georchestra,dc=org
  managerPassword: secret

  # Password policy
  passwordPolicy:
    enabled: true
    minLength: 8
    requireSpecialChar: true
    requireDigit: true
    requireLowercase: true
    requireUppercase: true
    expirationDays: 90
```

When using the standard geOrchestra LDAP schema (`georchestra/ldap` image), this extended configuration enables additional features like:

- Organization management
- User-organization relationships
- Pending users and organizations
- Protected users and roles
- Additional user attributes specific to geOrchestra

## OAuth2/OpenID Connect Authentication

geOrchestra Gateway supports OAuth2 and OpenID Connect authentication in addition to LDAP authentication.

### Configuration

Configure OAuth2/OpenID Connect in `application.yml` or `gateway/security.yaml`:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: your-client-id
            client-secret: your-client-secret
            scope: openid,email,profile
          github:
            client-id: your-client-id
            client-secret: your-client-secret
          custom:
            client-id: your-client-id
            client-secret: your-client-secret
            authorization-grant-type: authorization_code
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
            scope: openid,email,profile
            client-authentication-method: basic
        provider:
          custom:
            authorization-uri: https://your-auth-server/auth
            token-uri: https://your-auth-server/token
            jwk-set-uri: https://your-auth-server/certs
            user-info-uri: https://your-auth-server/userinfo
            user-name-attribute: sub

georchestra:
  security:
    oauth2:
      enabled: true
      displayButtons: true
      # OpenID Connect claim mappings
      oidc:
        - registration: google
          username-claim: email
          roles-claim: roles
          org-claim: organization
          firstname-claim: given_name
          lastname-claim: family_name
          email-claim: email
```

You can configure multiple OAuth2 providers, and users will see buttons for each on the login page.

### Provider-Specific Configuration

#### FranceConnect

FranceConnect is a widely used French identity provider that allows individuals to log in to public administration websites using credentials from other public administrations. It has specific requirements for integration:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          franceconnect:
            client-name: FranceConnect
            client-id: <client-id>
            client-secret: <client-secret>
            client-authentication-method: post
            authorization-grant-type: authorization_code
            redirect-uri: https://<gateway-url>/login/oauth2/code/franceconnect
            scope: openid, email, given_name, family_name
        provider:
          franceconnect:
            authorization-uri: https://fcp.integ01.dev-franceconnect.fr/api/v1/authorize
            token-uri: https://fcp.integ01.dev-franceconnect.fr/api/v1/token
            user-info-uri: https://fcp.integ01.dev-franceconnect.fr/api/v1/userinfo
            end-session-uri: https://fcp.integ01.dev-franceconnect.fr/api/v1/logout
            user-name-attribute: sub
```

Important notes for FranceConnect:

- The `end-session-uri` is mandatory because FranceConnect tracks active logins
- FranceConnect doesn't support the general `profile` scope - you must specify individual scopes
- URLs will differ between integration and production environments

#### ProConnect

ProConnect is the equivalent of FranceConnect for professionals, enabling private and public sector professionals to connect to their usual applications:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          proconnect:
            provider: proconnect
            client-name: ProConnect
            client-authentication-method: post
            client-id: <client-id>
            client-secret: <client-secret>
            authorization-grant-type: authorization_code
            redirect-uri: <redirect-url>
            scope: openid,siret,given_name,usual_name,email,uid,custom
        provider:
          proconnect:
            issuer-uri: https://fca.integ01.dev-agentconnect.fr/api/v2
```

ProConnect uses non-standard claims, so you'll need to map them correctly:

```yaml
georchestra:
  gateway:
    security:
      oidc:
        claims:
          provider:
            proconnect:
              id.path: "$.sub"
              email.path: "$.email"
              familyName.path: "$.usual_name"  # Non-standard claim
              givenName.path: "$.given_name"
              organization.path: "$.given_name"
              organizationUid.path: "$.siret"
```

For more information on available ProConnect claims, see the [ProConnect documentation](https://github.com/numerique-gouv/proconnect-documentation/blob/main/doc_fs/scope-claims.md#correspondance-entre-scope-et-claims-sur-proconnect).

## Organization fallback strategy

OIDC and Oauth2 providers supports a fallback strategy to resolve the organization of a user when the organization claim is missing or empty. This is useful for providers that don't provide a specific claim for organization, or when the claim is not consistently filled.

```yaml
georchestra:
  security:
    oauth2:
      oidc:
        config:
          provider:
            proconnect:
              orgNameResolvers: # fallback chain
                - sirene # try to resolve org name from SIRENE API using the siret from the token: need to be configured (enabled + token)
                - identifier # use the identifier from the token (e.g. "siret:12345678901234" if set to organizationUid.path: "$.siret", see above)
                - NO_ORG # fallback to this static short name if the previous resolvers fail. MATCH the shortname of an existing org, or it will be created as a new one with this name.
```

In order to enable the `sirene` resolver, you need to configure the SIRENE API client. 

You can get a token for the SIRENE API by creating an account on [https://portail-api.insee.fr/](https://portail-api.insee.fr/), subscribing to the SIRENE API, and generating a token. Then, add the following configuration:

```yaml

georchestra:
  gateway:
    security:
      sirene:
        enabled: true
        token: <sirenetoken>
```

## Custom Claims Mapping

For OpenID Connect providers, you can customize how claims are mapped to geOrchestra user properties:

```yaml
georchestra:
  security:
    oauth2:
      oidc:
        - registration: custom
          username-claim: preferred_username
          roles-claim: groups
          org-claim: organization
          firstname-claim: given_name
          lastname-claim: family_name
          email-claim: email
          roles-mapping:
            ADMIN: ADMINISTRATOR
            USER: USER
```

The `roles-mapping` section allows you to map provider-specific roles to geOrchestra roles.

### Claims Configuration

Standard OpenID Connect claims are automatically mapped between the token information and Spring Security. For non-standard claims or custom claim mapping, you can configure JSONPath expressions to extract user information from the claims.

Claims configuration has two levels:

- General claims settings: `georchestra.gateway.security.oidc.claims`
- Provider-specific claims settings: `georchestra.gateway.security.oidc.claims.provider.<provider-name>`

During the mapping process, standard claims are mapped first, and non-standard claims are mapped next (overriding the standard mapping when successful).

#### Available Claim Mappings

```yaml
georchestra:
  gateway:
    security:
      oidc:
        claims:
          # JSONPath for user identifier (defaults to standard "sub" claim)
          id.path: "$.sub"
          
          # JSONPath for user email
          email.path: "$.email"
          
          # JSONPath for user's given name (defaults to standard "given_name" claim)
          givenName.path: "$.given_name"
          
          # JSONPath for user's family name (defaults to standard "family_name" claim)
          familyName.path: "$.family_name"
          
          # JSONPath for organization name
          organization.path: "$.org_id"
          
          # JSONPath for organization unique identifier
          organizationUid.path: "$.org_uid"
          
          # Role mapping configuration
          roles:
            # JSONPath expressions to extract role names
            json.path:
              - "$.groups[*]"
              - "$.concat(\"ORG_\", $.org_id)"
            # Convert role names to uppercase
            uppercase: true
            # Remove special characters and replace spaces with underscores
            normalize: true
            # Append mapped roles to OAuth2 roles instead of replacing them
            append: true
```

### Extra roles claim mapping facilities
It could arise that the roles claim is not a simple array of strings, but a string of multiple roles separated by a semicolon. Such string can be transformed into an array of roles by using the "splitcsv" configuration property:
```yaml
georchestra:
  gateway:
    security:
      oidc:
        claims:
          provider:
            <provider-name>: 
              roles:
                json.path:
                  - "$.groups"
                splitcsv: true
```

It could also arise that role names come with an unwanted literal prefix (e.g. `prefix_gdi_admin` when only `admin` is expected). Such prefix can be stripped from each role name (after splitting, if `splitcsv` is used, and before `uppercase`/`normalize` are applied) using the "removePrefix" configuration property:
```yaml
georchestra:
  gateway:
    security:
      oidc:
        claims:
          provider:
            <provider-name>: 
              roles:
                json.path:
                  - "$.groups"
                removePrefix: "role_gdi_"
```

#### Provider-Specific Claims Example

You can override general claim settings for specific providers:

```yaml
georchestra:
  gateway:
    security:
      oidc:
        claims:
          # General settings here...
          
          provider:
            # Provider-specific settings override general settings
            proconnect:
              id.path: "$.sub"
              email.path: "$.email"
              familyName.path: "$.usual_name" # Non-standard claim
              givenName.path: "$.given_name"
              organization.path: "$.given_name"
              organizationUid.path: "$.siret"
              roles:
                json.path:
                  - "$.concat(\"ORG_\", $.siret)"
```

#### Example Scenario

If an OIDC provider returns these claims:

```json
{
    "icuid": "abc123",
    "family_name": "Doe",
    "given_name": "John",
    "preferred_username": "jd@example.com",
    "sub": "...",
    "groups": [
        "GDI Planer",
        "GDI Editor"
    ],
    "PartyOrganisationID": "6007280321"
}
```

You could use this configuration:

```yaml
georchestra:
  gateway:
    security:
      oidc:
        claims:
          id.path: "$.icuid"
          organization.path: "$.PartyOrganisationID"
          roles:
            json.path:
              - "$.concat(\"ORG_\", $.PartyOrganisationID)"
              - "$.groups[*]"
            uppercase: true
            normalize: true
```

This would produce a user with:

- Organization: "6007280321"
- Roles: ["ROLE_ORG_6007280321", "ROLE_GDI_PLANER", "ROLE_GDI_EDITOR"]

### Provider Configuration Options

In addition to claims mapping, you can configure provider-specific behavior:

```yaml
georchestra:
  gateway:
    security:
      oidc:
        config:
          # Global setting for finding user by email instead of by ID
          searchEmail: false
          
          # Provider-specific settings override global settings
          provider:
            proconnect:
              searchEmail: true
            google:
              searchEmail: false
              authoritative: true
```

| Option          | Default | Description                                                                                                                                                                                                                                                                                                                                                  |
|-----------------|---------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `searchEmail`   | `false` | When `true`, finds the user in geOrchestra by email address instead of by ID                                                                                                                                                                                                                                                                                 |
| `authoritative` | `false` | When `true`, each time a user logs in, the user's attributes (including roles and orgs) are aligned with the ones returned by the external identity provider. When 'false', given a user has already logged once, the user is fetched from the geOrchestra LDAP and returned with no modification, ignoring potential divergences with the identity provider |
### External Authentication Flags

When using external authentication (OAuth2/OpenID Connect or pre-authentication), the Gateway adds a special header to requests sent to backend services:

```
sec-external-authentication: true
```

This allows backend applications to adapt their behavior for externally authenticated users. For example, the geOrchestra console can hide password-change forms for users authenticated through external providers since these users don't have a local password.

### Automatically Creating Users in LDAP

For OAuth2/OpenID Connect authentication, you can configure the Gateway to automatically create users in the geOrchestra LDAP directory when they successfully authenticate but don't exist in LDAP yet. This allows administrators to later modify these users' roles or organization settings through the console.

To enable this feature:

```yaml
georchestra:
  gateway:
    security:
      createNonExistingUsersInLDAP: true
      ldap:
        default:
          enabled: true
          extended: true
          # LDAP configuration details
          url: ldap://georchestra-ldap:389/
          baseDn: dc=georchestra,dc=org
          adminDn: cn=admin,dc=georchestra,dc=org
          adminPassword: secret
          # Other LDAP configuration...
```

This works similarly to the pre-authentication auto-creation feature, ensuring a consistent user base regardless of the authentication method used.

## Pre-Authentication via HTTP Headers

geOrchestra Gateway also supports a pre-authentication mechanism where authentication is handled by an external component (such as a proxy server) that sends user information via HTTP headers.

### Configuration

To enable pre-authentication, add the following to your `security.yaml` configuration:

```yaml
georchestra:
  security:
    header:
      enabled: true
      username: preauth-username
      email: preauth-email
      firstName: preauth-firstname
      lastName: preauth-lastname
      organization: preauth-org
      provider: preauth-provider
      providerId: preauth-provider-id
      isAuthenticated: sec-georchestra-preauthenticated
      adminRole: ROLE_ADMINISTRATOR
      headersPrefix: sec-
```

This configuration defines which headers will be used to extract user information. You can customize the header names to match your authentication provider.

### Required Headers

When pre-authentication is enabled, the Gateway expects the following headers:

| Header | Description | Example |
|--------|-------------|---------|
| `sec-georchestra-preauthenticated` | Set to `true` to indicate pre-authentication | `true` |
| `preauth-username` | Username/identifier | `pmauduit` |
| `preauth-email` | User's email address | `pierre.mauduit@example.org` |
| `preauth-firstname` | User's first name | `Pierre` |
| `preauth-lastname` | User's last name | `Mauduit` |
| `preauth-org` | Organization identifier | `geOrchestra` |
| `preauth-provider` | *(optional)* External provider name | `myexternalprovider` |
| `preauth-provider-id` | *(optional)* External provider identifier | `user_123456` |

### Encoding Special Characters

Since HTTP headers are typically ASCII, special characters (like accented characters) can cause issues. To handle this, you can Base64 encode header values by prefixing them with `{base64}`.

Example:
```
RequestHeader set preauth-lastname "{base64}TWF1ZHVpdA==" "expr=-n env('MELLON_SN')"
```

### Account Creation

Pre-authenticated users can be automatically created in the geOrchestra LDAP directory. To enable this:

```yaml
georchestra:
  gateway:
    security:
      header-authentication:
        enabled: true
      createNonExistingUsersInLDAP: true
```

This requires a properly configured LDAP connection with write access:

```yaml
georchestra:
  gateway:
    security:
      ldap:
        default:
          enabled: true
          extended: true
          url: ldap://georchestra-ldap:389/
          baseDn: dc=georchestra,dc=org
          adminDn: cn=admin,dc=georchestra,dc=org
          adminPassword: secret
          users:
            rdn: ou=users
            searchFilter: (uid={0})
            pendingUsersSearchBaseDN: ou=pendingusers
            protectedUsers: geoserver_privileged_user
          roles:
            rdn: ou=roles
            searchFilter: (member={0})
            protectedRoles: ADMINISTRATOR, EXTRACTORAPP, GN_.*, ORGADMIN, REFERENT, USER, SUPERUSER
          orgs:
            rdn: ou=orgs
            orgTypes: Association,Company,NGO,Individual,Other
            pendingOrgSearchBaseDN: ou=pendingorgs
```

If you also want to send an email to administrators, fill console's account creation endpoint:

```yaml
georchestra:
  gateway:
    security:
      events:
        accountcreated:
          url: "http://console:8080/console/internal/events/accountcreated"
```

### Security Considerations

!!! warning "Security Warning"
    When using pre-authentication, it is **mandatory** to have the Gateway behind a secure proxy that sanitizes incoming requests. Otherwise, users could forge headers to impersonate others.

The proxy server must:

1. Strip any incoming `sec-*` or `preauth-*` headers that might be sent by malicious clients
2. Only set pre-authentication headers for properly authenticated users
3. Ensure that the Gateway is not directly accessible from the public internet

### Example Proxy Configuration

#### Apache with mod_mellon (Renater Federation)

```apache
<VirtualHost *:80>
    ServerName https://georchestra.example.org:443/
    UseCanonicalName On

    <Location />
        MellonEnable "info"
        MellonSecureCookie On
        MellonUser eppn
        # Configuration omitted for brevity...

        # Strip any potential forged headers
        RequestHeader unset sec-georchestra-preauthenticated
        RequestHeader unset preauth-username
        RequestHeader unset preauth-email
        RequestHeader unset preauth-firstname
        RequestHeader unset preauth-lastname
        RequestHeader unset preauth-org
        RequestHeader unset preauth-provider
        RequestHeader unset preauth-provider-id

        # Set headers only if authentication succeeded
        RequestHeader set sec-georchestra-preauthenticated true "expr=-n env('MELLON_NAME_ID')"
        RequestHeader set preauth-username %{MELLON_EPPN}e "expr=-n env('MELLON_EPPN')"
        RequestHeader set preauth-email %{MELLON_MAIL}e "expr=-n env('MELLON_MAIL')"
        RequestHeader set preauth-firstname %{MELLON_GIVEN_NAME}e "expr=-n env('MELLON_GIVEN_NAME')"
        RequestHeader set preauth-lastname %{MELLON_SN}e "expr=-n env('MELLON_SN')"
        RequestHeader set preauth-org %{MELLON_O}e "expr=-n env('MELLON_O')"

        ProxyPass "http://georchestra-gateway:8080/"
        ProxyPassReverse "http://georchestra-gateway:8080/"
        ProxyPreserveHost On
    </Location>

    <Location /login/renater>
        AuthType Mellon
        MellonEnable auth
        Require valid-user
        Redirect "/"
    </Location>
</VirtualHost>
```

#### Nginx Configuration

```nginx
server {
    listen 80;
    server_name georchestra.example.org;

    location / {
        # Strip any forged headers
        proxy_set_header sec-georchestra-preauthenticated "";
        proxy_set_header preauth-username "";
        proxy_set_header preauth-email "";
        proxy_set_header preauth-firstname "";
        proxy_set_header preauth-lastname "";
        proxy_set_header preauth-org "";

        # Set pre-authentication headers (in a real setup, these would come from your auth system)
        proxy_set_header sec-georchestra-preauthenticated "true";
        proxy_set_header preauth-username "testadmin";
        proxy_set_header preauth-email "testadmin@georchestra.org";
        proxy_set_header preauth-firstname "Test";
        proxy_set_header preauth-lastname "Admin";
        proxy_set_header preauth-org "PSC";

        proxy_pass http://georchestra-gateway:8080;
    }
}
```

### Testing Pre-Authentication

A sample Docker Compose file is available for testing pre-authentication:

```bash
docker compose -f docker-compose-preauth.yaml up
```

This setup uses an Nginx proxy that automatically logs you in as `testadmin` without requiring credentials.

## Redirection

The Gateway supports redirecting users after login, using a `redirect` query parameter. The target
URL must be allowed by `georchestra.gateway.loginRedirectAllowList` (prefix match) to avoid open
redirects.

Configure the allow list in `gateway.yaml`:

```yaml
georchestra:
  gateway:
    loginRedirectAllowList: >
      https://georchestra.example.fr/geoserver/,
      https://georchestra.example.fr/mapstore/
```

There are two supported flows:

1. Redirect from the geOrchestra login page

When accessing the login page, you can provide a `redirect` parameter. After successful
authentication, the user is sent to the requested resource.

```text
https://georchestra.example.fr/login?redirect=https://georchestra.example.fr/mapstore/
```

2. Redirect from an external portal (OAuth2 authorization endpoint)

An external portal can link directly to the OAuth2 authorization endpoint with a `redirect` target.
This allows a user already authenticated with an external IdP (for example Keycloak) to land
directly on a geOrchestra application without seeing the geOrchestra login page.

```text
https://georchestra.example.fr/oauth2/authorization/enrs?redirect=https://georchestra.example.fr/mapstore/%23/context/secretcontext
```

After successful OAuth2 login, the user is redirected to:

```text
https://georchestra.example.fr/mapstore/#/context/secretcontext
```

## Login Page Customization

You can customize which authentication methods are shown on the login page:

```yaml
georchestra:
  security:
    ldap:
      enabled: true
    oauth2:
      enabled: true
      displayButtons: true
```

If both LDAP and OAuth2 are enabled, users will see both options on the login page.

## Delay after login

In case several seconds are required after a successful login - e.g. because of account
creation and/or the need of a synchronization with external services -, you might want your
user to be redirected onto a landing page which will refresh and actually redirect onto the 
expected page after a 5 seconds delay.

The delaying landing page can be activated into your login flow, defining the following
variable in your configuration with a value strictly positive:

```yaml
georchestra:
  gateway:
    security:
      delayAfterLoginSeconds: 5
```

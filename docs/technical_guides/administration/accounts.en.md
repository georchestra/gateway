# Account creation

Users authenticated by an [OAuth2 / OpenID Connect provider](oauth2.md) or by
[header pre-authentication](preauth.md) do not necessarily exist in the geOrchestra LDAP. The gateway
can create their **LDAP account**, and their **organization**, the first time they log in. They then
behave like any other geOrchestra user: they can be managed in the console, get roles, and the
back-ends receive their organization headers.

Users authenticated with the LDAP login form are not concerned.

## Enabling

All the following conditions are required:

```yaml
georchestra:
  gateway:
    security:
      createNonExistingUsersInLDAP: true
      ldap:
        default:
          enabled: true
          extended: true
          adminDn: cn=admin,dc=georchestra,dc=org
          adminPassword: secret
```

- `georchestra.gateway.security.createNonExistingUsersInLDAP` is `true`. Write the property name
  **exactly** like this (camel case): the feature is activated by a condition on this exact name.
- The `default` LDAP configuration is enabled.
- At least one enabled LDAP configuration is `extended: true` (and not Active Directory), otherwise the
  gateway refuses to start (`LDAP account creation requires an extended LDAP configuration`). The
  **first** enabled extended configuration is used to create the accounts, and its `adminDn` and
  `adminPassword` are mandatory: they must have write access to the users, roles and organizations
  branches.

## What happens at login

1. The gateway looks for an existing account:
    - users coming from an OAuth2/OIDC provider, or from pre-authentication with a
      `preauth-provider` / `preauth-provider-id`: by provider name and provider user id, or **by
      email** if `searchEmail` is enabled for the provider (see below);
    - other pre-authenticated users: by username.
2. If no account is found, it is created:
    - username, email, first and last names come from the authenticated user (for OIDC, the username
      is the [transformed username](oauth2.md#openid-connect));
    - the account is marked **pending** when moderated sign-up applies (see below);
    - the organization is the one of the user (`organization` claim, `preauth-org`), or
      `defaultOrganization` when the user has none. The organization is created if it does not exist;
    - the account gets the `USER` role, the roles of the user (created in LDAP if they do not exist),
      and the roles of its organization.
3. The account stored in LDAP is then used for the session: its roles, organization and attributes
   are sent to the back-ends, and `sec-external-authentication` is `true`.
4. If the account is pending, the login is refused (`/login?error=pending_user`).

If the email or the username already belongs to another account, the login is refused with the
"*An account already exists*" message (`/login?error=duplicate_account`).

!!! note
    With the default `login.html`, the *pending* and *duplicate account* messages are only displayed
    when the LDAP login form is enabled (see [OAuth2](oauth2.md#login-page)).

For an existing account, the LDAP account is used as-is: the attributes and roles given by the
provider are **not** synchronized, unless the provider is [authoritative](#authoritative-providers).

## Options

### Global options

| Property (`georchestra.gateway.security.`) | Default | Description |
|---|---|---|
| `createNonExistingUsersInLDAP` | `true`¹ | Enables account creation (see the conditions above). |
| `defaultOrganization` | `${defaultOrganization:}` (empty) | Organization (`cn`) given to new accounts when the user has none. |
| `moderatedSignup` | `false` | New accounts are pending, for users **without** an external provider (pre-authentication without `preauth-provider`). The development datadir of the repository maps it to `${moderatedSignup:false}` (from `default.properties`). |

¹ The Java default is `true`, but the embedded `application.yml` sets it to `false`; the feature is
only active when the property is explicitly `true`.

### Per provider options

Under `georchestra.gateway.security.oidc.config` (global value) and
`georchestra.gateway.security.oidc.config.provider.<registration id>` (provider value, wins over the
global one). They apply to OAuth2/OIDC users and, except `authoritative`, to pre-authenticated users
sending a `preauth-provider` header (the provider name is then the value of that header). Note that
existing pre-authenticated accounts are only looked up by provider id when both `preauth-provider` and
`preauth-provider-id` are sent; otherwise they are looked up by username.

| Property | Default | Description |
|---|---|---|
| `searchEmail` | `false` | Look for an existing account by email instead of provider id. Useful to link existing LDAP accounts to a new provider. |
| `moderatedSignup` | `false` | New accounts are pending until validated by an administrator in the console. |
| `orgNameResolvers` | empty | Ordered list of resolvers for the organization name, see below. |
| `overrideExistingOrgName` | `false` | When the user is added to an **existing** organization, also update its name with the resolvers. |
| `authoritative` | `false` | Provider level only. See below. |

```yaml
georchestra:
  gateway:
    security:
      oidc:
        config:
          provider:
            proconnect:
              searchEmail: true
              moderatedSignup: true
              overrideExistingOrgName: false
              orgNameResolvers:
                - sirene
                - identifier
                - NO_ORG
```

### Authoritative providers

When `authoritative: true` is set for a provider, the provider is considered the source of truth:

- on **each** login, the LDAP account is updated with the values of the provider (email, names,
  organization). The phone, title and description of the account are emptied, since the provider does
  not supply them;
- the LDAP roles of the user are replaced by the roles given by the provider plus `USER`, then the
  roles of the organization are added again: other roles added in the console are removed at the next
  login;
- the pending status is not checked.

## Organizations

An organization is only handled when the user has an organization identifier (`organization` claim,
`preauth-org`, or `defaultOrganization`).

- If the provider gives an organization unique id (`organizationUid` claim, e.g. a SIRET number), the
  organization is first looked up by this unique id, then by its `cn`.
- If none is found, it is created with this `cn`, the unique id, the type `Other`, and a name given
  by the [name resolvers](#organization-name-resolution) (the `cn` if no resolver returns a name).
- The user is added to the organization and gets its roles.

When a new account is created and the provider sends an organization unique id, the account is
linked to the organization matching it (the previous one is unlinked). For authoritative providers
this is done at each login.

### Organization name resolution

`orgNameResolvers` is an ordered list; the first entry returning a name wins:

| Entry | Name of the organization |
|---|---|
| `sirene` | Resolved with the INSEE SIRENE API from the organization unique id (SIRET), when the [SIRENE module](#sirene-api) is enabled. |
| `identifier` | The identifier itself: the unique id if present, otherwise the `cn`. |
| any other value | The value itself, e.g. `NO_ORG`. |

The short name is derived from the name (32 characters maximum).

!!! warning
    When `sirene` is listed but the SIRENE module is **not** enabled, the entry is treated as a literal
    value: organizations would be named `sirene`.

### SIRENE API

The `sirene` resolver queries the French [INSEE SIRENE API](https://portail-api.insee.fr/) to get the
legal name of an organization from its SIRET number.

```yaml
georchestra:
  gateway:
    security:
      sirene:
        enabled: true
        api-key: ${SIRENE_API_KEY}
        # base-url: https://api.insee.fr/api-sirene/3.11
```

| Property (`georchestra.gateway.security.sirene.`) | Default | Description |
|---|---|---|
| `enabled` | `false` | Enables the resolver. |
| `api-key` | | API key, sent in the `X-INSEE-Api-Key-Integration` header. Free keys are available on the INSEE API portal. |
| `base-url` | `https://api.insee.fr/api-sirene/3.11` | Base URL of the API. The gateway calls `GET {base-url}/siret/{siret}`. |

The name is `etablissement.uniteLegale.denominationUniteLegale`, or
`prenomUsuelUniteLegale nomUsuelUniteLegale` for individual companies. The short name is the
upper-cased name without accents, spaces nor special characters, truncated to 32 characters. When the
SIRET is unknown, the API rate limit is reached or the call fails, the next resolver is tried. Results
are not cached.

## Account creation events

The gateway can notify another service, typically the console so that it sends an email to the
administrators, when an account is created for a user coming from an external provider:

```yaml
georchestra:
  gateway:
    security:
      events:
        accountcreated:
          url: http://console:8080/console/internal/events/accountcreated
```

When `georchestra.gateway.security.events.accountcreated.url` is set (and account creation is
enabled), the gateway sends a `POST` request with a JSON body:

```json
{
  "uid": "550e8400-e29b-41d4-a716-446655440000",
  "subject": "OAUTH2-ACCOUNT-CREATION",
  "fullName": "John Doe",
  "localUid": "keycloak_jdoe",
  "email": "john.doe@example.org",
  "organization": "MY_ORG",
  "providerName": "keycloak",
  "providerUid": "1234567890"
}
```

`uid` is a random identifier of the event. No event is sent for accounts created for users without an
external provider. A failure is only logged; it does not prevent the login.

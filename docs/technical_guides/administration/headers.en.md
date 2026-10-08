# Security headers

The back-end applications get the identity of the user from HTTP request headers added by the gateway,
the `sec-*` headers.

Before adding them, the `RemoveSecurityHeaders` default filter removes every incoming request header
whose name starts with `sec-` (case-insensitive): a client cannot forge them.

## Configuration

Headers are enabled globally with `georchestra.gateway.default-headers`, and can be overridden per
service with `georchestra.gateway.services.<name>.headers`:

```yaml
georchestra:
  gateway:
    default-headers:
      proxy: true
      username: true
      roles: true
      org: true
      orgname: true
    services:
      geonetwork:
        target: http://geonetwork:8080/geonetwork/
        headers:
          username: false
          roles: false
          org: false
          json-user: true
```

A header that is not configured is **not** sent: the embedded configuration enables none of them, the
list above is the one of the official datadir. For a given service, each property set in its
`headers` overrides the same property of `default-headers`; unset properties keep their default value.

## Available headers

| Property | Header | Value |
|---|---|---|
| `proxy` | `sec-proxy` | `true`. Tells the back-end that the request comes through the gateway. See note below. |
| `userid` | `sec-userid` | User unique identifier: the LDAP account UUID for extended LDAP users and for accounts created by the gateway; otherwise, for OpenID Connect, the `sub` claim (unless remapped with `oidc.claims`). |
| `username` | `sec-username` | Username. |
| `roles` | `sec-roles` | Roles, separated by `;` (e.g. `ROLE_USER;ROLE_ADMINISTRATOR`). |
| `org` | `sec-org` | Organization identifier (short name / `cn`) of the user. |
| `email` | `sec-email` | Email address. |
| `firstname` | `sec-firstname` | First name. |
| `lastname` | `sec-lastname` | Last name. |
| `tel` | `sec-tel` | Telephone number. |
| `address` | `sec-address` | Postal address. |
| `title` | `sec-title` | Title. |
| `notes` | `sec-notes` | Notes / description. |
| `last-updated` | `sec-lastupdated` | Hash of the user attributes, which changes whenever the user is modified (extended LDAP users). |
| `json-user` | `sec-user` | The whole user as JSON, Base64-encoded and prefixed with `{base64}`. |
| `orgname` | `sec-orgname` | Display name of the organization. |
| `orgid` | `sec-orgid` | Unique identifier (UUID) of the organization. |
| `orgid` | `sec-org-lastupdated` | Hash of the organization attributes, which changes whenever the organization is modified. See note below. |
| `org-last-updated` | — | Declared but currently has no effect. See note below. |
| `json-organization` | `sec-organization` | The whole organization as JSON, Base64-encoded and prefixed with `{base64}`. |

Property names use Spring relaxed binding: `json-user`, `jsonUser` and `jsonuser` are equivalent.

A header is only added when the corresponding value exists. In particular, the organization headers
(`sec-orgname`, `sec-orgid`, `sec-org-lastupdated`, `sec-organization`) require the user to come from an
**extended** LDAP configuration (see [LDAP authentication](ldap.md)), or to have an LDAP account created
by the gateway (see [Account creation](accounts.md)).

!!! note "Current implementation details"
    - `sec-proxy` is only controlled by `georchestra.gateway.default-headers.proxy`: the `proxy`
      property of a service is ignored.
    - `sec-org-lastupdated` is sent when `orgid` is enabled. The `org-last-updated` property exists but
      is not used.

### Headers that are always sent

These two headers do not depend on the configuration:

| Header | When | Value |
|---|---|---|
| `sec-external-authentication` | the request is authenticated | `true` when the user authenticated through an external provider (OAuth2/OpenID Connect or pre-authentication) and has an account managed by the gateway, `false` otherwise. |
| `sec-ldap-remaining-days` | the LDAP password policy reports that the password will expire soon | Number of days before the password expires. |

## How headers are resolved for a request

1. The route matching the request is found (see [Services and routes](services_and_routes.md)).
2. The service whose `target` is **exactly equal** to the route `uri` is looked up. Write the port
   explicitly in the targets: see [Services and routes](services_and_routes.md#declaring-a-route).
3. If found and it has `headers`, they are merged over `default-headers`; otherwise `default-headers`
   is used as-is.
4. The enabled headers are computed from the user resolved for the request (after
   [role mappings](roles_mappings.md)), then added to the proxied request.

Anonymous requests only get `sec-proxy` (when enabled).

## Removing other headers

`RemoveSecurityHeaders` only removes the `sec-*` headers. To remove other request headers, use the
`RemoveHeaders` filter (see [Filters and predicates](filters.md#removeheaders)) or the standard
`RemoveRequestHeader` filter of Spring Cloud Gateway.

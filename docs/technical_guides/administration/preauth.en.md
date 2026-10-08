# Header pre-authentication

With pre-authentication, the user is authenticated **before** reaching the gateway, by a trusted
component (reverse proxy, SSO agent, ...) that passes the user identity in HTTP request headers.
The gateway trusts these headers without checking any credentials.

!!! danger "Security"
    Only enable pre-authentication when the gateway **cannot** be reached directly by clients, and when
    the component in front of it **removes** the `sec-georchestra-preauthenticated` and `preauth-*`
    headers sent by the clients. Otherwise anybody can impersonate any user.

## Enabling

```yaml
georchestra:
  gateway:
    security:
      header-authentication:
        enabled: true
```

## Request headers

A request is pre-authenticated when it carries `sec-georchestra-preauthenticated: true`. The user is
then described by the following headers:

| Header | Required | Content |
|---|---|---|
| `sec-georchestra-preauthenticated` | yes | `true` |
| `preauth-username` | yes | Username. A pre-authenticated request without it fails. |
| `preauth-email` | no | Email address. |
| `preauth-firstname` | no | First name. |
| `preauth-lastname` | no | Last name. |
| `preauth-org` | no | Organization short name (`cn`). |
| `preauth-roles` | no | Roles, separated by `;`, e.g. `ROLE_EDITOR;ROLE_REFERENT`. `ROLE_USER` is always added. |
| `preauth-provider` | no | Name of the external identity provider. |
| `preauth-provider-id` | no | Identifier of the user in that provider. |

Header values can be Base64-encoded (useful for non-ASCII characters) by prefixing them with
`{base64}`, e.g. `preauth-lastname: {base64}TcO8bGxlcg==`. Comma-separated values are decoded one by
one.

Roles are used as given: include the `ROLE_` prefix so that they can be used in
[access rules](access_rules.md).

The headers listed above are removed from the request before it is proxied to the back-ends. Other
headers starting with `preauth-` are not removed.

## Session

The pre-authentication is **not** stored in the session: every request must carry the
pre-authentication headers. A request without them is anonymous (unless the session holds another
authentication, e.g. an LDAP login).

## Creating the accounts in LDAP

With pre-authentication, the user usually does not exist in the geOrchestra LDAP. When
[account creation](accounts.md) is enabled (`createNonExistingUsersInLDAP: true` and an extended
`default` LDAP), the gateway creates the account, and its organization, the first time the user is
seen, and then uses the LDAP account (with its roles and organization). When the account is pending
validation, the login is refused.

```yaml
georchestra:
  gateway:
    security:
      createNonExistingUsersInLDAP: true
      header-authentication:
        enabled: true
      ldap:
        default:
          enabled: true
          extended: true
          adminDn: cn=admin,dc=georchestra,dc=org
          adminPassword: secret
```

## Example: nginx

A test nginx configuration is provided in the repository (`datadir/nginx-preauth/nginx.conf`, used by
`docker-compose-preauth.yaml`). It sets fixed headers, for testing only:

```nginx
location / {
    proxy_set_header sec-georchestra-preauthenticated "true";
    proxy_set_header preauth-username  "testadmin";
    proxy_set_header preauth-email     "psc+testadmin@georchestra.org";
    proxy_set_header preauth-firstname "test";
    proxy_set_header preauth-lastname  "admin";
    proxy_set_header preauth-org       "georchestra";
    proxy_pass http://gateway:8080;
}
```

In a real deployment, the values come from the authentication module of the reverse proxy, and
`proxy_set_header` overwrites any value sent by the client for these headers.

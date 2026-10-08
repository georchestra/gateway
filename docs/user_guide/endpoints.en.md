# Gateway endpoints

Besides proxying the geOrchestra applications, the gateway serves a few URLs of its own.

| URL | Method | Description |
|---|---|---|
| `/login` | `GET` | Displays the login page. |
| `/login` | `POST` | Submits the username / password form (fields `username` and `password`). |
| `/logout` | `GET` | Ends the session and redirects to the logout URL (`/?logout` by default). |
| `/oauth2/authorization/{provider}` | `GET` | Starts the login with the external identity provider `{provider}`. These are the targets of the provider buttons of the login page. |
| `/login/oauth2/code/{provider}` | `GET` | Callback URL the identity provider redirects to after authentication. |
| `/success` | `GET` | Intermediate page displayed after login, only when a login delay is configured (returns *404* otherwise). |
| `/whoami` | `GET` | Returns, as JSON, the user resolved for the current session. |

## Checking your session with `/whoami`

`/whoami` is useful to check who you are logged in as and which roles you have. It returns a JSON
document with two entries:

- `GeorchestraUser`: the geOrchestra user resolved from your session (username, email, organization,
  roles, ...), or `null` when you are not authenticated;
- an entry named after the type of authentication (for example
  `org.georchestra.gateway.security.ldap.extended.GeorchestraUserNamePasswordAuthenticationToken` for a
  login with the geOrchestra LDAP), or `Authentication: null`
  when you are not authenticated.

```json
{
  "GeorchestraUser": {
    "username": "testadmin",
    "email": "psc+testadmin@georchestra.org",
    "organization": "PSC",
    "roles": ["ROLE_SUPERUSER", "ROLE_ADMINISTRATOR", "ROLE_USER"]
  },
  "org.georchestra.gateway.security.ldap.extended.GeorchestraUserNamePasswordAuthenticationToken": { "...": "..." }
}
```

The example above is shortened: the real output contains more attributes.

## Redirecting after login

The login page accepts a `redirect` query parameter, e.g.
`/login?redirect=https://my.georchestra.org/datahub/`. The target URL is only taken into account
if it starts with one of the prefixes allowed by the administrator
(see [`georchestra.gateway.loginRedirectAllowList`](../technical_guides/administration/ui_customization.md#redirection-after-login-and-logout)).
Otherwise the parameter is ignored.

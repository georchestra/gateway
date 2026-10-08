# Signing in and out

## Accessing a protected page

Most geOrchestra pages can be browsed anonymously. When you try to open a page that requires to be
authenticated, the gateway redirects you to its login page (`/login`). Once logged in, you are sent back
to the page you initially requested.

You can also force the login from any page served through the gateway by adding the `login` query
parameter to its URL, for example `https://my.georchestra.org/datahub/?login`. If you are not
authenticated yet you are redirected to the login page; if you already are, the page is displayed
normally.

## The login page

The login page is available at `/login`. Depending on the platform configuration it can show:

- a **username / password form**, when an LDAP directory is configured. With the default geOrchestra
  LDAP, you can type either your **username** or your **email address**;
- below it, one **button per external identity provider** (OAuth2 / OpenID Connect, e.g. Google,
  GitHub, ProConnect, a Keycloak realm, ...), and links to **create an account**
  (`/console/account/new`) and to **recover a forgotten password**
  (`/console/account/passwordRecovery`), both served by the geOrchestra console.

When the platform only offers a single external identity provider and no username/password form,
the login page is skipped and you are directly redirected to that provider.

The labels of the page are translated according to the language of your browser. English, French,
German, Spanish, Dutch and Russian are provided by default.

### Messages displayed on the login page

| Message (English) | Meaning |
|---|---|
| *Invalid username or password* | The credentials do not match any account. |
| *Your password has been expired and should be changed* | The LDAP password policy reports that your password expired. Follow the link to change it in the console. |
| *An account already exists using this email address* | You logged in with an external provider, and an account with the same email or username already exists in geOrchestra. Contact your administrator. |
| *User is pending validation* | Your account was created but must be validated by an administrator before you can use it. |

### Delayed redirection after login

Administrators can configure a short delay after a successful login. In that case a
"*Login successful*" page is displayed for a few seconds before you are redirected to the page you
requested.

## Signing out

To sign out, open `/logout`. Your gateway session is invalidated and you are redirected to the
platform home page (`/?logout` by default; administrators can change it).

If you logged in through an OpenID Connect provider that supports it, the gateway also ends your
session on that provider, which then redirects you back to the gateway login page (`/login?logout`).

!!! warning
    Closing the browser tab does not always end your session. On shared computers, always use
    `/logout`.

## Error pages

When an application returns an error to a browser (for example *404 Not found* or *403 Forbidden*),
the gateway displays its own error page instead of the raw error. When a back-end application is not
reachable, a *503 Service unavailable* page is displayed.

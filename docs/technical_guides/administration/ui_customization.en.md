# UI customization

The gateway renders a few pages itself, with [Thymeleaf](https://www.thymeleaf.org/) templates:

| Template | Page |
|---|---|
| `login.html` | Login page (`/login`). |
| `success.html` | Intermediate page after login, when `delayAfterLoginSeconds` is set (`/success`). |
| `error/403.html`, `error/404.html`, `error/500.html`, `error/501.html`, `error/503.html` | Error pages. |

The default templates and their static resources (CSS, images) are embedded in the gateway
([`templates`](https://github.com/georchestra/georchestra-gateway/tree/main/gateway/src/main/resources/templates),
[`static`](https://github.com/georchestra/georchestra-gateway/tree/main/gateway/src/main/resources/static)).
The official datadir contains copies of them in `gateway/templates/`, ready to be customized.

## geOrchestra header and look

These properties, usually defined in `default.properties`, control the
[geOrchestra header](https://github.com/georchestra/header) displayed on the gateway pages and their
style:

| Property | Default | Description |
|---|---|---|
| `georchestra.gateway.headerEnabled` | `true` | Display the geOrchestra header on the login page. When `false`, the login page shows the `logoUrl` image instead. |
| `headerScript` | `https://cdn.jsdelivr.net/gh/georchestra/header@dist/header.js` | Script of the `<geor-header>` web component. |
| `headerUrl` | `/header/` | URL of the legacy header (`legacy-url` attribute). |
| `useLegacyHeader` | `false` | Use the legacy (iframe) header. |
| `headerHeight` | `80` | Header height, in pixels. |
| `headerConfigFile` | empty | Configuration file of the header (`config-file` attribute). |
| `logoUrl` | empty | Logo URL, used by the header and displayed on the success page (and on the login page when the header is disabled). |
| `georchestraStylesheet` | empty | Additional CSS stylesheet added to the login, success and error pages, and passed to the header. |

## Customizing the templates

1. Copy the templates you want to change (the official datadir already contains them in
   `gateway/templates/`).
2. Point Thymeleaf to this directory, for example in `application.yaml`:

    ```yaml
    spring:
      thymeleaf:
        prefix: file:${georchestra.datadir}/gateway/templates/
    ```

!!! warning
    `spring.thymeleaf.prefix` **replaces** the template location: every template (`login.html`,
    `success.html`, `error/*.html`) is then looked up in that directory only. Keep all of them there,
    even the ones you do not modify.

The embedded configuration sets `spring.thymeleaf.cache: false`: changes to the templates are taken
into account without restarting the gateway.

### Data available in the templates

The login and success pages receive the header properties listed above as model attributes:
`headerEnabled`, `headerScript`, `headerUrl`, `useLegacyHeader`, `headerHeight`, `headerConfigFile`,
`logoUrl`, `georchestraStylesheet`.

The login page also receives:

| Attribute | Content |
|---|---|
| `ldapFormLoginEnabled` | `true` when an LDAP configuration is enabled and `disableLdapFormLogin` is `false`. |
| `oauth2LoginLinks` | Map of the OAuth2 providers: key = login URL (`/oauth2/authorization/<id>`), value = pair (`left`: label, `right`: icon URL). |
| `invalidCredentials`, `passwordExpired`, `duplicateAccount`, `pendingUser` | `true` when the page is displayed after the corresponding error (`/login?error=...`). |

The success page receives `delay` (seconds) and `finalTargetUrl` (where to redirect).

The error pages receive the standard Spring Boot error attributes (`status`, `error`, `path`,
`timestamp`, ...) plus `georchestraStylesheet` and `logoUrl`.

### Error pages

When an error has to be displayed to a browser, Spring Boot looks for `error/<status>.html` (e.g.
`error/404.html`), then `error/4xx.html` or `error/5xx.html`, then `error/error.html`. Add templates
with these names to cover more statuses. Connection errors to a back-end (unknown host, connection
refused) are reported as *503*, and access denied errors as *403*.

## Static resources

The default templates use static resources served by the gateway under `/login/` (CSS, images,
provider icons) and `/favicon.ico`. To serve your own resources, add a directory to the static
locations, keeping the embedded ones as a fallback:

```yaml
spring:
  web:
    resources:
      static-locations: file:${georchestra.datadir}/gateway/templates/static/, classpath:/static/
```

A file `${georchestra.datadir}/gateway/templates/static/img/logo.png` is then served at `/img/logo.png`.

To serve the static resources under a prefix, set `spring.webflux.static-path-pattern`, e.g.
`/static/**`; the resource is then served at `/static/img/logo.png`. The default templates reference
`/login/...` URLs: adapt them accordingly. The prefix is also used to build the URL of the OAuth2
provider icons on the login page.

Static resources must be reachable anonymously: keep an access rule granting anonymous access to them
(the official `/**` global rule does).

### OAuth2 provider icons

For each OAuth2 registration `<id>`, the login page uses the icon `login/img/<id>.png` if it exists in
one of the static locations, and `login/img/default.png` otherwise.

## Messages and translations

The texts of the login and success pages come from the `messages/login*.properties` bundles embedded in
the gateway (English, French, German, Spanish, Dutch, Russian), selected according to the
`Accept-Language` header of the browser.

Additional bundles can be declared with `spring.messages.basename` (comma-separated), for example to
add keys used by custom templates:

```yaml
spring:
  messages:
    basename: file:${georchestra.datadir}/gateway/messages/custom
```

The embedded bundles are looked up first: a key defined in them cannot be overridden by an additional
bundle. To change an existing text, change the template.

## Redirection after login and logout

| Property | Default | Description |
|---|---|---|
| `georchestra.gateway.loginRedirectAllowList` | empty | Comma-separated list of URL prefixes. `/login?redirect=<url>` redirects to `<url>` after login only if it starts with one of them. Empty: the `redirect` parameter is ignored. |
| `georchestra.gateway.logoutUrl` | `/?logout` | Where users are redirected after `/logout`. Users logged in through an OIDC provider with an end session endpoint are sent to the provider, which redirects them to `/login?logout` (see [OAuth2](oauth2.md#logout-endpoint)). |
| `georchestra.gateway.security.delayAfterLoginSeconds` | `0` | When greater than `0`, a "Login successful" page is displayed for this number of seconds before redirecting the user. |

```yaml
georchestra:
  gateway:
    loginRedirectAllowList: >
      https://my.georchestra.org/datahub/,
      https://my.georchestra.org/geoserver/
    logoutUrl: /datahub/
```

!!! note
    Restrict `loginRedirectAllowList` to the URLs of your platform: allowing any URL would let
    attackers redirect your users to external sites after login (open redirect).

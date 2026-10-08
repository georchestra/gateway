# Access rules

Access rules define, per URL pattern, who can access a resource: nobody, anybody, any authenticated
user, or only users having some roles. They replace the `security-mappings.xml` file of the former
geOrchestra security-proxy.

## Where to define them

- `georchestra.gateway.services.<name>.access-rules`: rules attached to a service, for readability.
- `georchestra.gateway.global-access-rules`: rules applied after all the service rules.

```yaml
georchestra:
  gateway:
    global-access-rules:
    - intercept-url: /**
      anonymous: true
    services:
      console:
        target: http://console:8080/console/
        access-rules:
        - intercept-url:
          - /console/public/**
          - /console/account/new
          anonymous: true
        - intercept-url: /console/internal/**
          allowed-roles: SUPERUSER
        - intercept-url: /console/manager/**
          allowed-roles: SUPERUSER, ORGADMIN
        - intercept-url: /console/account/**
          anonymous: false
```

## Rule properties

| Property | Type | Default | Description |
|---|---|---|---|
| `intercept-url` | list of strings | *(required)* | URL path patterns the rule applies to. A single value or a comma-separated string is also accepted. |
| `forbidden` | boolean | `false` | When `true`, access is denied to everybody. |
| `anonymous` | boolean | `false` | When `true`, access is granted to everybody, authenticated or not. |
| `allowed-roles` | list of strings | empty | Roles allowed to access the URLs. The `ROLE_` prefix is optional: `ADMINISTRATOR` and `ROLE_ADMINISTRATOR` are equivalent. |

The properties of a rule are evaluated in this order:

1. `forbidden: true` → access denied to everybody;
2. else `anonymous: true` → access granted to everybody (`allowed-roles` is ignored);
3. else `allowed-roles` empty → access granted to any **authenticated** user;
4. else → access granted to users having **at least one** of the `allowed-roles`.

A rule with an empty `intercept-url` makes the gateway fail at startup.

## Evaluation order

All the rules are registered in Spring Security when the gateway starts, in this order:

1. the rules of each service, service after service in their declaration order, and rule after rule
   within a service;
2. the global rules, in their declaration order.

For each request, **the first rule whose pattern matches the request path is applied**, and the
following ones are ignored. Hence:

- declare the most specific patterns first;
- service rules always win over global rules;
- rules are **not** restricted to the routes of their service: a pattern declared in service `A`
  also applies to requests routed to service `B` if it matches their path.

!!! danger "Requests matching no rule are denied"
    If no rule matches the request path, access is **denied**, including for the pages served by
    the gateway itself (the `/login` page, `/whoami`, static resources of the login page, ...). Only
    `POST /login` and `GET /logout`, handled before the authorization step, keep working.
    Always end the global rules with a catch-all rule, for example:

    ```yaml
    global-access-rules:
    - intercept-url: /**
      anonymous: true
    ```

When access is denied, an anonymous browser user is redirected to `/login`; an authenticated user
gets a *403 Forbidden* error page.

## URL patterns

Patterns are matched against the request **path** (the query string is never considered) with the
Spring `PathPattern` syntax, an Ant-like syntax:

| Pattern | Matches |
|---|---|
| `?` | one character |
| `*` | zero or more characters within a path segment |
| `**` | zero or more path segments, **only allowed at the end of the pattern** |
| `{name}` | one path segment, captured as a variable |

Examples: `/console/**`, `/console/*/emails`, `/geoserver/web/`, `/mapstore/rest/geostore/**`.

## Which roles are checked

A rule with `allowed-roles` grants access when one of these roles is found either in the roles of
the geOrchestra user resolved for the request, or in the authorities of the Spring Security
authentication. The roles of the geOrchestra user include the roles added by the
[role mappings](roles_mappings.md): they are the roles sent to the back-ends in the `sec-roles`
header, and the ones displayed by the `/whoami` endpoint.

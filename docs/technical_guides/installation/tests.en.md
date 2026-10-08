# Tests

Checks to run after an installation or a configuration change. Replace `localhost:8080` and
`localhost:8090` by the address of your gateway and of its management port.

## 1. The gateway is up

```bash
curl -s http://localhost:8090/actuator/health
```

The status must be `UP`. The response also details the health of the components (Redis, when
sessions are stored in Redis, etc.).

## 2. Routes are loaded

```bash
curl -s http://localhost:8090/actuator/gateway/routes
```

Every route of `routes.yaml` must be listed, with the expected `uri`.

## 3. Anonymous access

```bash
curl -s http://localhost:8080/whoami
```

Expected result: `{"GeorchestraUser":null,"Authentication":null}`.

Open a public page (for instance `/datahub/` or `/geoserver/web/`) in a browser: it must be displayed
without asking for credentials.

## 4. Login

- Open `/login`: the login form (with the identity provider buttons, if any) must be displayed, or,
  with a single identity provider and no LDAP form, you must be redirected to the provider.
- Log in, then open `/whoami`: `GeorchestraUser` must contain the expected username, organization
  and roles.
- With LDAP, HTTP Basic authentication can be tested from the command line:

    ```bash
    curl -s -u testadmin:testadmin http://localhost:8080/whoami
    ```

## 5. Access rules

- As an anonymous user, open a protected URL (for example `/console/manager/`): you must be redirected
  to `/login`.
- As a user without the required role, the same URL must return *403 Forbidden*.

## 6. Headers sent to the back-ends

The development composition of the repository contains an `echo` service (an HTTP server that returns
the request it received) exposed on the `/echo` route. Calling it shows the `sec-*` headers that the
gateway adds:

```bash
curl -s -u testadmin:testadmin http://localhost:8080/echo | jq .request.headers
```

On a production platform, a similar check can be done with any back-end that logs its request
headers. Also check that a `sec-*` header sent by the client is **not** forwarded:

```bash
curl -s -H 'sec-username: hacker' -H 'sec-roles: ROLE_SUPERUSER' http://localhost:8080/echo
```

## 7. Logout

Open `/logout`: you must be redirected to the logout URL (`/?logout` by default) and `/whoami` must
return `null` values again.

# Role mappings

Role mappings grant **additional roles** to a user, based on the roles given by the authentication
provider (LDAP, OAuth2/OpenID Connect, pre-authentication). They are typically used to translate the
groups of an external identity provider into geOrchestra roles.

## Configuration

Property: `georchestra.gateway.roles-mappings`, a map whose keys are role name patterns and values are
lists of roles to add. In the official datadir it lives in `roles-mappings.yaml`.

```yaml
georchestra:
  gateway:
    roles-mappings:
      '[ROLE_GP.GDI.*]':
        - ROLE_USER
      '[ROLE_GP.GDI.ADMINISTRATOR]':
        - ROLE_SUPERUSER
        - ROLE_ADMINISTRATOR
```

With this configuration:

- a user having `ROLE_GP.GDI.EDITOR` gets `ROLE_USER` in addition;
- a user having `ROLE_GP.GDI.ADMINISTRATOR` gets `ROLE_USER`, `ROLE_SUPERUSER` and
  `ROLE_ADMINISTRATOR` in addition: **all** the matching mappings are applied.

!!! warning "Property name"
    The property is `roles-mappings` (plural *roles*). The comments of some versions of
    `roles-mappings.yaml` mention `role-mappings`, which is ignored.

## Pattern syntax

- The key is matched against each role of the user, **including** its `ROLE_` prefix, and must match
  the whole role name.
- `*` matches any sequence of characters; `.` is a literal dot. The key is otherwise used as a Java
  regular expression: other special characters (`?`, `+`, `|`, `(`, `[`, ...) keep their regex meaning,
  and an invalid expression makes the gateway fail at startup. Avoid them unless you mean it.
- Matching is case-sensitive.
- Keys containing special characters such as `.` or `*` must be written between brackets and quotes
  (`'[ROLE_GP.GDI.*]'`), otherwise Spring Boot strips these characters from the map keys.

## Behaviour

- The added roles are kept together with the original roles; duplicates are removed.
- Mappings are not recursive: a role added by a mapping does not trigger other mappings.
- The resulting roles are the ones used by the [access rules](access_rules.md) and sent in the
  `sec-roles` [header](headers.md).
- When the gateway [creates the LDAP account](accounts.md) of a user, the account creation runs
  after the role mappings: the mapped roles are stored in LDAP with the new account. On the next
  logins, the user returned by the account manager (with the roles stored in LDAP) replaces the
  mapped user, so changing the mappings does not affect existing accounts, unless the provider is
  configured as *authoritative* (its roles are then synchronized to LDAP on each login).

# LDAP authentication

The gateway can authenticate users against **one or several** LDAP directories. When at least one
LDAP configuration is enabled, users can log in with:

- the login form (`POST /login`), unless `disableLdapFormLogin` is `true`;
- HTTP Basic authentication (`Authorization: Basic ...`), always enabled when LDAP is enabled.

## Configuration

LDAP directories are declared under `georchestra.gateway.security.ldap.<name>`. `<name>` is free;
the `default` one has a special meaning (see below).

```yaml
georchestra:
  gateway:
    security:
      ldap:
        default:
          enabled: true
          extended: true
          url: ldap://ldap:389/
          baseDn: dc=georchestra,dc=org
          adminDn: cn=admin,dc=georchestra,dc=org
          adminPassword: secret
          users:
            rdn: ou=users
            searchFilter: (uid={0})
          roles:
            rdn: ou=roles
            searchFilter: (member={0})
          orgs:
            rdn: ou=orgs
```

| Property (`georchestra.gateway.security.ldap.<name>.`) | Default | Description |
|---|---|---|
| `enabled` | `false` | Enables this LDAP configuration. |
| `extended` | `false` | Use the geOrchestra extensions: full user details, organizations, login with email. See below. |
| `activeDirectory` | `false` | The server is an Active Directory. See below. |
| `url` | | LDAP URL, e.g. `ldap://ldap:389` or `ldaps://ldap.example.org:636`. **Required.** |
| `baseDn` | | Base DN, e.g. `dc=georchestra,dc=org`. **Required.** |
| `adminDn` | | DN of the account used to search the directory. Required to [create accounts](accounts.md). |
| `adminPassword` | | Password of `adminDn`. |
| `users.rdn` | | Users branch, relative to `baseDn`, e.g. `ou=users`. **Required.** |
| `users.searchFilter` | | Filter to find a user, `{0}` being the login, e.g. `(uid={0})`. **Required**, except for Active Directory. |
| `users.returningAttributes` | all | LDAP attributes to fetch for the user. |
| `roles.rdn` | | Roles (groups) branch, relative to `baseDn`, e.g. `ou=roles`. **Required.** |
| `roles.searchFilter` | | Filter to find the roles of a user, `{0}` being the user DN, e.g. `(member={0})`. **Required.** |
| `orgs.rdn` | `ou=orgs`¹ | Organizations branch (extended configurations only). |
| `orgs.pendingRdn` | `ou=pendingorgs`¹ | Pending organizations branch (extended configurations only). |

¹ These defaults only apply when the `orgs` block exists, i.e. when at least one `orgs.*` property is
set. For an extended configuration, always declare `orgs.rdn` explicitly.

!!! warning "Declare LDAP configurations in a configuration file"
    The gateway detects enabled LDAP configurations by looking for property **names** of the exact form
    `georchestra.gateway.security.ldap.<name>.enabled` in the configuration files and system
    properties, then reads their value (relaxed binding applies to the value lookup). An environment
    variable can therefore change the value of a configuration declared in a file (e.g.
    `GEORCHESTRA_GATEWAY_SECURITY_LDAP_DEFAULT_ENABLED=true` works, since the embedded `application.yml`
    declares `ldap.default.enabled`), but a configuration declared **only** through environment
    variables is not detected.

The required properties are only checked for **enabled** configurations; a missing one makes the
gateway fail at startup with a validation error naming the property (e.g. `ldap.[default].baseDn`).

!!! note
    Other keys found in some datadirs under the LDAP configuration (`users.pendingUsersSearchBaseDN`,
    `users.protectedUsers`, `orgs.protectedRoles`) are not used by the gateway. The pending users
    branch is always `ou=pendingusers`.

### The embedded `default` configuration

The gateway embeds a `default` LDAP configuration matching the geOrchestra OpenLDAP directory,
**disabled** by default. Its values come from `default.properties`:

```yaml
georchestra.gateway.security.ldap.default:
  enabled: false
  extended: true
  url: ${ldapScheme}://${ldapHost}:${ldapPort}
  baseDn: ${ldapBaseDn:dc=georchestra,dc=org}
  users:
    rdn: ${ldapUsersRdn:ou=users}
    searchFilter: ${ldapUserSearchFilter:(uid={0})}
  roles:
    rdn: ${ldapRolesRdn:ou=roles}
    searchFilter: ${ldapRolesSearchFilter:(member={0})}
  orgs:
    rdn: ${ldapOrgsRdn:ou=orgs}
```

In most cases, enabling it and setting the admin credentials is enough:

```yaml
georchestra.gateway.security.ldap.default:
  enabled: true
  adminDn: ${ldapAdminDn}
  adminPassword: ${ldapAdminPassword}
```

[Account creation](accounts.md) requires the `default` configuration to be enabled.

### Disabling the login form

```yaml
georchestra:
  gateway:
    security:
      disableLdapFormLogin: true
```

The username / password form is no longer displayed on the login page, while HTTP Basic
authentication stays available, e.g. for technical clients (QGIS, scripts). This is meant for platforms
where browser users log in through a **single** OAuth2 / OpenID Connect provider: `/login` then
redirects directly to it.

!!! warning
    In the default `login.html` template, the OAuth2 provider buttons are part of the login form
    block. With `disableLdapFormLogin: true` and **several** OAuth2 providers, the login page shows
    neither the form nor the buttons. Customize the template (see [UI customization](ui_customization.md))
    if you need this setup.

Note that this only hides the form: credentials posted to `POST /login` are still checked against
LDAP.

## Extended (geOrchestra) vs basic LDAP

!!! danger "Known issue: basic LDAP configurations"
    In the current version, authentication fails with an internal error for **basic** configurations
    (`extended: false`, which includes Active Directory): the LDAP authentication provider always
    tries to look the login up as an email address in the geOrchestra accounts, and this lookup is not
    available for basic configurations. The error is not an authentication failure, so the other
    configurations are not tried either: enabling **any** basic configuration breaks LDAP logins for
    all the configurations, including the extended ones. The corresponding integration test of the
    project is disabled. Use only `extended: true` configurations with the geOrchestra LDAP.

| | `extended: true` | `extended: false` (basic) |
|---|---|---|
| Target | geOrchestra OpenLDAP schema | Any LDAP, Active Directory |
| User details | All the geOrchestra attributes (email, names, phone, address, title, notes, ...) | Username and roles |
| Organization | Yes (`sec-org`, `sec-orgname`, `sec-orgid`, `sec-organization` headers) | No |
| Login with email address | Yes: if the login matches the email of an account, its username is used | No |
| Roles | Roles of the user in `roles.rdn`, prefixed with `ROLE_` | Roles of the user in `roles.rdn`, prefixed with `ROLE_` |
| Password policy | Expired passwords and expiration warnings (`sec-ldap-remaining-days`) are reported | Expired passwords are reported |
| Account creation | Possible (see [Account creation](accounts.md)) | No |

## Several LDAP directories

Several configurations can be enabled at the same time, with different names. A login is tried on
each enabled configuration in turn, until one succeeds or one reports an expired password. For extended
configurations, the user details are then fetched from the directory that authenticated the user.

Because of the [known issue](#extended-georchestra-vs-basic-ldap) with basic configurations, only
combine extended configurations for now. The example below shows the syntax.

```yaml
georchestra:
  gateway:
    security:
      ldap:
        default:
          enabled: true
        partners:
          enabled: true
          extended: true
          url: ldaps://ldap.partner.org:636
          baseDn: dc=partner,dc=org
          users:
            rdn: ou=people
            searchFilter: (uid={0})
          roles:
            rdn: ou=groups
            searchFilter: (member={0})
          orgs:
            rdn: ou=orgs
```

## Active Directory

Set `activeDirectory: true` and `extended: false`:

```yaml
georchestra:
  gateway:
    security:
      ldap:
        ad:
          enabled: true
          activeDirectory: true
          url: ldap://ad.example.org:389
          baseDn: dc=example,dc=org
          adminDn: cn=gateway,ou=services,dc=example,dc=org
          adminPassword: secret
          users:
            rdn: ou=users
          roles:
            rdn: ou=groups
            searchFilter: (member={0})
```

With `activeDirectory: true`:

- `users.searchFilter` is optional and defaults to `(&(objectClass=user)(userPrincipalName={0}))`;
- `orgs` is ignored (a warning is logged if it is set);
- `roles.rdn` and `roles.searchFilter` are still required.

The directory is otherwise accessed like any other LDAP server (search then bind). See the known
issue about basic configurations [above](#extended-georchestra-vs-basic-ldap).

!!! warning
    A configuration with both `activeDirectory: true` and `extended: true` is silently ignored: it is
    used neither as a basic nor as an extended configuration.

## Password policy

The gateway binds with the LDAP password policy control. When the directory reports that the password
**expired**, the user is redirected to `/login?error=expired_password` and invited to change it in the
console. For extended configurations, when the directory reports that the password **will expire**, the
number of remaining days is sent to the back-ends in the `sec-ldap-remaining-days` header.

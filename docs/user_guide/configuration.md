# Configuration

This guide covers the configuration options for the geOrchestra Gateway.

## Overview

The geOrchestra Gateway acts as the central entry point for all geOrchestra services, handling authentication, authorization, and request routing. Its configuration is designed to be flexible and adaptable to different deployment scenarios.

## Configuration Layers

The Gateway's configuration follows a layered approach:

1. **Spring Boot Configuration**: Core Spring Boot settings (application.yml)

2. **GeOrchestra Data Directory**: External configuration files in a central location

3. **Environment Variables**: Runtime configuration through environment variables

4. **Docker Configuration**: When deployed with Docker, additional settings via Docker Compose

## Configuration Files

The geOrchestra Gateway configuration is primarily managed through YAML files located in the `datadir/gateway/` directory:

- `application.yaml` - Core application properties
- `gateway.yaml` - Gateway services and routing configuration
- `security.yaml` - Authentication and authorization settings
- `routes.yaml` - Route definitions 
- `roles-mappings.yaml` - Role mapping configuration
- `logging.yaml` - Logging configuration

## Data Directory

The Gateway reads its configuration from the geOrchestra "data directory". The location of this directory is specified using the `georchestra.datadir` environment property.

The additional property sources are loaded using Spring Boot's `spring.config.import` property, for example:

```yaml
spring.config.import: ${georchestra.datadir}/default.properties,${georchestra.datadir}/gateway/gateway.yaml
```

### Docker Setup

When using Docker, the data directory is typically mounted as a volume:

```yaml
volumes:
  - georchestra_datadir:/etc/georchestra
```

The environment variable is set to point to this location:

```yaml
environment:
  - JAVA_TOOL_OPTIONS=-Dgeorchestra.datadir=/etc/georchestra
```

### Configuration File Purposes

Each configuration file in the data directory serves a specific purpose:

| File | Purpose |
|------|---------|
| `application.yaml` | Contains filters applicable to all transfers through the gateway, and general Spring settings |
| `gateway.yaml` | Contains access rules for all services available through the gateway |
| `roles-mappings.yaml` | Maps roles returned by authentication providers to standardized geOrchestra roles |
| `routes.yaml` | Contains list of routes for redirecting to the correct service URL based on criteria |
| `security.yaml` | Contains all settings about authentication, including OAuth2 and pre-authentication |

## Core Settings

### Basic Application Properties

```yaml
spring:
  application:
    name: gateway
  main:
    web-application-type: reactive

server:
  port: 8080
  compression:
    enabled: true
```

### Profiles

Spring profiles can be activated to enable specific functionality:

```yaml
spring:
  profiles:
    active: default,json-logs
```

Common profiles include:

- `docker` - Settings for Docker environments
- `json-logs` - Enable JSON-formatted structured logging (see [Logging](logging.md))
- `logging_debug` - Enable more detailed logging (see [Logging](logging.md))
- `preauth` - Enable pre-authentication mode (see [Authentication: Pre-authentication via HTTP Headers](authentication.md#pre-authentication-via-http-headers))

## Structured Logging

The Gateway includes a comprehensive logging module that provides structured logging capabilities, including MDC (Mapped Diagnostic Context) propagation in reactive contexts and configurable access logging.

### JSON Log Format

To enable JSON-formatted logs, activate the `json-logs` Spring profile:

```yaml
spring:
  profiles:
    active: json-logs
```

When enabled, logs will be formatted as structured JSON objects, making them suitable for analysis in tools like Elasticsearch, Splunk, or Graylog.

### Access Logging

Access logging records HTTP requests based on pattern matching. It can be configured to log different URL patterns at different levels (INFO, DEBUG, TRACE). This enables selective logging based on request patterns:

```yaml
logging:
  accesslog:
    enabled: true
    # URLs to log at INFO level
    info:
      - ".*/(ows|ogc|wms|wfs|wcs|wps)(/.*|\?.*)?$"
      - ".*/(api)/.*"
    # URLs to log at DEBUG level
    debug:
      - ".*/(admin|manager)/.*"
    # URLs to log at TRACE level
    trace:
      - "^(?!.*/static/)(?!.*\.(png|jpg|jpeg|gif|svg|webp|ico)(\?.*)?$).*$"
```

Access logs include:

- HTTP method (GET, POST, etc.)
- Status code (200, 404, etc.)
- Request URI
- Additional MDC context properties

### MDC Properties

The Mapped Diagnostic Context (MDC) provides contextual information for log entries. The Gateway allows fine-grained control over which properties are included in the MDC, organized into three categories:

#### User/Authentication MDC Properties

Control which user information is included in logs:

```yaml
logging:
  mdc:
    include:
      user:
        id: true           # Include user ID in enduser.id and enduser.uuid
        roles: true        # Include user roles in enduser.roles
        org: true          # Include user's organization ID: enduser.org.id (shortname) and enduser.org.uuid 
        extras: true       # include human-friendly labels enduser.firstname, enduser.lastname, enduser.org.fullname
        auth-method: true  # Include authentication method in enduser.auth-method
```

#### HTTP Request MDC Properties

Control which HTTP request information is included in logs:

```yaml
logging:
  mdc:
    include:
      http:
        id: true                   # Include request ID
        method: true               # Include HTTP method
        url: true                  # Include request URL
        remote-addr: true          # Include client IP address
        remote-host: false         # Include client hostname
        parameters: false          # Include request parameters
        query-string: false        # Include query string
        session-id: false          # Include session ID
        cookies: false             # Include cookies
        headers: true              # Include HTTP headers
        headers-pattern: "(?i)x-.*|correlation-.*"  # Pattern for included headers
```

#### Application MDC Properties

Control which application information is included in logs:

```yaml
logging:
  mdc:
    include:
      application:
        name: true         # Include application name
        version: true      # Include application version
```

### MDC Propagation in Reactive Contexts

One of the key features of the Gateway's logging system is its ability to maintain MDC context across asynchronous boundaries in the reactive programming model. This ensures that contextual information remains available throughout the entire request processing pipeline, even when execution moves across different threads.

The MDC propagation system:

- Stores MDC data in the Reactor context

- Retrieves MDC data when needed for logging

- Synchronizes between thread-local MDC and reactive context

- Automatically cleans up MDC data after request processing

### Logging Profiles

Several logging profiles are available to customize log detail levels:

- `logging_debug` - Enable debug logging for most components

- `logging_debug_security` - Detailed security logging

- `logging_debug_catalog` - Detailed catalog operations logging

- `logging_debug_events` - Detailed event logging

### Log Levels

Default log levels can be configured in `logging.yaml`:

```yaml
logging:
  level:
    root: warn
    org.springframework: warn
    org.georchestra: info
    org.georchestra.gateway.accesslog: info  # Access log level
```

## CORS Configuration

Cross-Origin Resource Sharing (CORS) allows web applications running at one origin to request resources from a different origin. This is essential for web clients that need to make API calls to the Gateway from a different domain.

### Global CORS Configuration

You can configure CORS globally for all routes:

```yaml
spring:
  cloud:
    gateway:
      globalcors:
        corsConfigurations:
          '[/**]':  # This pattern applies to all paths
            allowedOrigins:  # Domains allowed to make cross-origin requests
              - "https://maps.example.org"
              - "https://viewer.example.org"
            allowedMethods:  # HTTP methods allowed for cross-origin requests
              - "GET"
              - "POST"
              - "PUT"
              - "DELETE"
              - "OPTIONS"
            allowedHeaders:  # HTTP headers allowed in cross-origin requests
              - "Authorization"
              - "Content-Type"
              - "X-Requested-With"
            maxAge: 3600  # How long (in seconds) browsers can cache CORS responses
            allowCredentials: true  # Whether cookies can be included in cross-origin requests
```

### Path-Specific CORS Configuration

For more granular control, you can specify different CORS settings for different path patterns:

```yaml
spring:
  cloud:
    gateway:
      globalcors:
        corsConfigurations:
          # Strict CORS for admin endpoints
          '[/console/admin/**]':
            allowedOrigins: "https://admin.example.org"
            allowedMethods: "GET,POST"
            maxAge: 1800
          
          # More permissive CORS for public API endpoints
          '[/api/**]':
            allowedOrigins:
              - "https://maps.example.org"
              - "https://viewer.example.org"
            allowedMethods: "GET,POST,PUT,DELETE"
            allowCredentials: true
            
          # Allow CORS preflight requests (OPTIONS) for all paths
          '[/**]':
            allowedMethods: "OPTIONS"
            allowedOrigins: "*"
            maxAge: 86400
```

### Security Considerations

When configuring CORS, keep these security best practices in mind:

- Avoid using `allowedOrigins: "*"` in production environments
- Specify only the origins, methods, and headers that are actually needed
- Consider setting a reasonable `maxAge` to reduce preflight requests
- Only set `allowCredentials: true` when necessary, as it has security implications

## Route Configuration

Routes are defined in the `routes.yaml` file and determine how incoming requests are directed to backend services.

### Route Structure

Each route definition consists of:

```yaml
- id: service-name             # Unique identifier for the route
  uri: ${service.url}          # Target URI where requests will be forwarded
  predicates:                  # Conditions that must be met for the route to match
    - Path=/service/**         # Path-based matching
  filters:                     # Optional transformations to apply to the request/response
    - RewritePath=/service/(.*), /$1  # Example: rewrite the path
```

### Example Routes

Here's an example of route configurations for common geOrchestra services:

```yaml
- id: geonetwork
  uri: http://localhost:8080/geonetwork
  predicates:
    - Path=/geonetwork/**
  filters:
    - CookieAffinity=JSESSIONID, /geonetwork/

- id: geoserver
  uri: http://localhost:8080/geoserver
  predicates:
    - Path=/geoserver/**

- id: console
  uri: http://localhost:8080/console
  predicates:
    - Path=/console/**,/login/**,/logout/**,/account/**
```

### Available Predicates

Common predicates include:

- `Path`: Matches based on the request path

- `Method`: Matches based on the HTTP method

- `Header`: Matches based on a request header

- `Query`: Matches based on a query parameter

For a comprehensive list of available predicates and their configuration options, see the [Spring Cloud Gateway Request Predicates Factories](https://cloud.spring.io/spring-cloud-gateway/reference/html/#gateway-request-predicates-factories) documentation.

### Available Filters

Common filters include:

- `RewritePath`: Modifies the request path

- `AddRequestHeader`: Adds a header to the request

- `AddResponseHeader`: Adds a header to the response

- `CookieAffinity`: Manages cookie-based session affinity

For a complete list of available filters and their configuration options, see the [Spring Cloud Gateway GatewayFilter Factories](https://cloud.spring.io/spring-cloud-gateway/reference/html/#gatewayfilter-factories) documentation.

## Access Control Configuration

Access control rules are defined in the `gateway.yaml` file and determine which users or roles can access specific paths.

### Access Rule Structure

```yaml
georchestra:
  gateway:
    services:
      service-name:
        target: https://service-url
        secured: true
        access-rules:
          - pattern: /public/**
            access: permitAll
          - pattern: /admin/**
            access: hasRole('ADMINISTRATOR')
          - pattern: /protected/**
            access: authenticated
```

### Example Access Rules

```yaml
georchestra:
  gateway:
    services:
      console:
        target: http://console:8080/console
        secured: true
        access-rules:
          - pattern: /console/account/new
            access: permitAll
          - pattern: /console/account/register
            access: permitAll
          - pattern: /console/account/passwordRecovery
            access: permitAll
          - pattern: /console/internal/**
            access: hasRole('SUPERUSER')
          - pattern: /console/manager/**
            access: hasRole('SUPERUSER')
          - pattern: /console/private/**
            access: hasRole('SUPERUSER') or hasRole('ORGADMIN')
          - pattern: /console/**
            access: authenticated
```

### Access Types

- `permitAll`: Allows access to all users, authenticated or not
- `authenticated`: Requires the user to be authenticated
- `hasRole('ROLE')`: Requires the user to have the specified role
- `hasAnyRole('ROLE1','ROLE2')`: Requires the user to have any of the specified roles

## Role Mapping Configuration

Role mappings are defined in the `roles-mappings.yaml` file and allow extending security roles dynamically.

### Role Mapping Structure

```yaml
# Basic syntax for role mapping
'source-role':
  - target-role1
  - target-role2

# Wildcard matching
'[ROLE_PREFIX.*]':
  - STANDARD_ROLE
```

### Example Role Mappings

```yaml
# Maps all roles starting with ROLE_GP.GDI.* to ROLE_USER
'[ROLE_GP.GDI.*]':
  - ROLE_USER

# Maps administrator role to standard ROLE_ADMINISTRATOR
'[ROLE_GP.GDI.ADMINISTRATOR]':
  - ROLE_ADMINISTRATOR
```

## Session Management

The Gateway provides flexible session management options to support different deployment scenarios.

### In-Memory Sessions (Default)

By default, the Gateway stores user sessions in memory. This is suitable for single-instance deployments or development environments.

Session timeout can be configured in `application.yaml`:

```yaml
server:
  reactive:
    session:
      timeout: 1440m # 24 hours
```

### Redis-Backed Sessions

For production deployments with multiple Gateway instances, Redis-backed session storage enables session sharing across instances and persistence across restarts.

#### Enabling Redis Sessions

To enable Redis session storage, configure the following in `gateway.yaml`:

```yaml
spring:
  session:
    redis:
      enabled: true
      host: localhost
      port: 6379
      password: my-super-redis-pwd
```

#### Redis Session Timeout

When Redis sessions are enabled, `server.reactive.session.timeout` is **ignored**. Use `spring.session.redis.timeout` instead to set the maximum inactive interval of a session (defaults to `12h`):

```yaml
spring:
  session:
    redis:
      enabled: true
      timeout: 24h # any Spring Duration format, e.g. 30m, 1440m, 2d
```

#### Benefits of Redis Sessions

- **Horizontal Scaling**: Multiple Gateway instances can share the same session store
- **Session Persistence**: Sessions survive Gateway restarts and redeployments
- **High Availability**: Redis replication provides session data redundancy
- **Centralized Management**: All sessions stored in a single, manageable location

#### Requirements

When using Redis sessions, ensure:

1. Redis server is running and accessible from all Gateway instances
2. Network connectivity between Gateway and Redis is reliable
3. Redis is properly secured with authentication if exposed on a network
4. Appropriate Redis persistence settings are configured for your requirements

## Metrics and Monitoring

The Gateway provides comprehensive monitoring and management capabilities through Spring Boot Actuator. By default, these endpoints are exposed on port 8090.

### Basic Actuator Configuration

```yaml
management:
  server:
    port: 8090  # Dedicated port for management endpoints
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus  # Comma-separated list of endpoints to expose
  endpoint:
    health:
      show-details: when_authorized  # Options: never, when-authorized, always
      probes:
        enabled: true  # Enables liveness and readiness endpoints for Kubernetes
```

### Advanced Monitoring Options

For more detailed metrics, you can configure additional settings:

```yaml
management:
  metrics:
    export:
      prometheus:
        enabled: true  # Enable Prometheus metrics format
    distribution:
      percentiles-histogram:
        http.server.requests: true  # Enable histogram metrics for HTTP requests
```

For comprehensive information on monitoring and management features, including all available endpoints and integration with monitoring systems, see the [Monitoring and Management](monitoring.md) guide.

## Login Redirection Configuration

The Gateway allows redirection with a query parameter on login page to URLs specified in the `gateway.yaml` file.

It's a startWith match, so you can specify a whole path or just the beginning of it.

### Login Redirection Structure

```yaml
georchestra:
  gateway:
    loginRedirectAllowList: >
      http://localhost:8080/geoserver/,
      http://localhost:8080/console/
```

## Disable LDAP Login form

It's possible to disable LDAP form login while keeping LDAP HTTP Basic authentication enabled.
Useful when LDAP must remain available for technical clients, while browser users should authenticate only through OAuth2/OIDC.

```yaml
georchestra.gateway.security.disableLdapFormLogin: true
```

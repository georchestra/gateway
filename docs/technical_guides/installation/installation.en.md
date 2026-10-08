# Installation

## With Docker (recommended)

The official image is [`georchestra/gateway`](https://hub.docker.com/r/georchestra/gateway). The
simplest way to deploy a full platform is the official composition
[georchestra/docker](https://github.com/georchestra/docker), which already contains a `gateway`
service.

A minimal service definition looks like this:

```yaml
services:
  gateway:
    image: georchestra/gateway:latest   # pin a released version in production
    volumes:
      - ./datadir:/etc/georchestra       # the geOrchestra datadir
    environment:
      - JAVA_TOOL_OPTIONS=-Dgeorchestra.datadir=/etc/georchestra
    ports:
      - "8080:8080"
```

- `georchestra.datadir` points to the datadir. `/etc/georchestra` is already the default value.
- Configuration properties can also be passed as environment variables using Spring Boot relaxed
  binding (for example `SPRING_PROFILES_ACTIVE=json-logs`), or as `-D` system properties in
  `JAVA_TOOL_OPTIONS`, with a few exceptions listed in
  [Configuration files](configuration.md#loading-order).
- Do not publish port `8090` (management endpoints) outside of your internal network.

The image is built with [Cloud Native Buildpacks](https://paketo.io/) and includes the
[OpenTelemetry Java agent buildpack](https://github.com/paketo-buildpacks/opentelemetry)
(`BP_OPENTELEMETRY_ENABLED=true` at build time). JVM options can be passed at runtime with
`JAVA_TOOL_OPTIONS`.

## With Kubernetes

The geOrchestra Helm chart (`georchestra` chart of [georchestra/helm-charts](https://github.com/georchestra/helm-charts))
deploys the gateway with the same image and datadir conventions.

## Standalone JAR

Build the project (see below), then run:

```bash
java -Dgeorchestra.datadir=/etc/georchestra -jar gateway/target/georchestra-gateway-<version>.jar
```

## Building from source

Requirements: a JDK 21 and, to build the Docker image, a running Docker daemon. The Maven wrapper
(`./mvnw`) is provided.

```bash
git clone https://github.com/georchestra/georchestra-gateway.git
cd georchestra-gateway

# Build all modules, skipping the tests
make install            # same as: ./mvnw clean install -ntp -DskipTests

# Build the Docker image georchestra/gateway:<version>, also tagged as georchestra/gateway:latest
make docker
```

The image name is `georchestra/gateway:${imageTag}`, where `imageTag` defaults to the project version.
`make docker-debug` builds a variant based on the `paketobuildpacks/builder-jammy-base` builder,
tagged `georchestra/gateway:latest-debug`.

## Development composition

The repository contains a `docker-compose.yml` and a development datadir (`datadir/`) to run the
gateway with an LDAP, a database and a few geOrchestra applications locally:

```bash
make docker
docker compose up -d
```

This composition and its datadir are **for development only**: they contain test passwords and
enable a remote debugging port (`5005`). `docker-compose-preauth.yaml` adds an nginx in front of the
gateway to test [header pre-authentication](../administration/preauth.md).

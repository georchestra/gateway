# Contribute

The source code is hosted at
[github.com/georchestra/georchestra-gateway](https://github.com/georchestra/georchestra-gateway).
Issues and pull requests are welcome; see also the
[contributing guidelines](https://github.com/georchestra/georchestra-gateway/blob/main/CONTRIBUTING.md).

- [Architecture](architecture.md) — modules, packages, auto-configurations, request processing.
- [Extension points](extensions.md) — interfaces to plug new behaviour into the gateway.

## Development environment

- JDK 21
- Docker (integration tests use [Testcontainers](https://testcontainers.com/); building the image uses
  Cloud Native Buildpacks)
- The Maven wrapper `./mvnw` is provided.

## Building

```bash
make install   # ./mvnw clean install -ntp -DskipTests
make test      # ./mvnw verify -ntp
make docker    # builds the georchestra/gateway:<version> image, tagged latest too
```

These are the steps run by the pull request workflow (`.github/workflows/pull-request.yml`).

## Running locally

With the development datadir of the repository and the `dev` profile, after a `make install`, from
the `gateway` directory:

```bash
cd gateway
../mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

The `dev` profile imports `../datadir/default.properties`, `../datadir/gateway/gateway.yaml` and
`../datadir/gateway/security.yaml`. The LDAP and the back-ends can be started with the repository
`docker-compose.yml`. Alternatively, run the whole composition, gateway included, with
`docker compose up -d` after `make docker`.

## Tests

- Unit tests: `*Test.java`.
- Integration tests: `*IT.java`, run by the Maven failsafe plugin during `verify`. They start an LDAP
  (`GeorchestraLdapContainer` from `georchestra-testcontainers`) and, for some of them, a Keycloak or a
  Redis with Testcontainers; they are skipped when Docker is not available. Mock back-ends use
  WireMock.
- Test configurations are in `gateway/src/test/resources/application-*.yml`, activated with
  `@ActiveProfiles`.

```bash
./mvnw verify -pl :georchestra-gateway -ntp           # all the tests of the gateway module
./mvnw test -pl :georchestra-gateway -Dtest=AccessRulesCustomizerTest
```

## Code formatting

The build formats the Java code (`formatter-maven-plugin`, configuration in `.mvn/formatter.xml`) and
sorts the `pom.xml` files (`sortpom-maven-plugin`). Run a build before committing, so that the
committed code is formatted. To only check the formatting:

```bash
./mvnw verify -Dfmt.action=validate -Dpom.fmt.action=verify
```

## Documentation

This documentation uses [MkDocs](https://www.mkdocs.org/) with the
[Material](https://squidfunk.github.io/mkdocs-material/) theme, following the
[geOrchestra documentation template](https://github.com/georchestra/georchestra_documentation_template).
Pages are in `docs/`, the navigation in `mkdocs.yml`. Pages are written in English (`*.en.md`); a
French translation can be added later with `*.fr.md` files.

```bash
bash mkdocs_installation.sh   # creates the venv_mkdocs virtual environment (Python 3.11+)
bash mkdocs_run.sh            # serves the documentation on http://localhost:8000
```

The documentation is built and published by [Read the Docs](https://readthedocs.org/) with
`.readthedocs.yaml`.

When you add or change a configuration property, update the [configuration reference](../administration/properties.md)
and the page of the corresponding feature.

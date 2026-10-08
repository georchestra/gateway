# geOrchestra Gateway

<div align="center">
  <img src="docs/images/georchestra-logo.svg" alt="geOrchestra Logo" width="300"/>
  <h3>Modern, Secure, and Flexible Gateway for the geOrchestra SDI Platform</h3>
</div>

<p align="center">
  <a href="https://github.com/georchestra/gateway/actions"><img src="https://github.com/georchestra/gateway/actions/workflows/maven.yml/badge.svg" alt="Build Status"></a>
  <a href="https://docs.georchestra.org/gateway/"><img src="https://img.shields.io/badge/docs-latest-blue" alt="Documentation"></a>
  <a href="https://github.com/georchestra/gateway/blob/main/LICENSE.txt"><img src="https://img.shields.io/github/license/georchestra/gateway" alt="License"></a>
</p>

## Overview

The geOrchestra Gateway is a Spring Cloud Gateway-based service that provides a secure, central entry point to all geOrchestra applications. It replaces the previous security-proxy component, offering enhanced flexibility, modern authentication methods, and improved performance.

## Key Features

- **Multiple Authentication Methods**
  - ✅ OAuth2 and OpenID Connect
  - ✅ LDAP Authentication
  - ✅ Header-based Pre-authentication
  
- **Modern Web Standards**
  - ✅ HTTP/2 Support
  - ✅ WebSocket Support
  - ✅ Spring WebFlux Reactive Stack
  
- **Security**
  - ✅ Role-Based Access Control
  - ✅ Flexible Header Management
  - ✅ Centralized Security Policies
  
- **Developer Experience**
  - ✅ Comprehensive Documentation
  - ✅ Easy Configuration
  - ✅ Docker-Ready

## Documentation

Visit our [comprehensive documentation](https://docs.georchestra.org/gateway/) to learn more about installation, configuration, and development.

## Quick Start

### Using Docker Compose

The Gateway is available as part of the geOrchestra Docker Compose setup:

```bash
git clone https://github.com/georchestra/docker.git
cd docker
docker-compose up -d
```

### Configuration

The Gateway provides flexible configuration options through YAML files:

```yaml
georchestra:
  gateway:
    security:
      ldap:
        default:
          enabled: true
          extended: true
          url: ldap://ldap:389
          # Additional LDAP configuration
```

The configuration is read from the `gateway` directory of the [geOrchestra datadir](https://github.com/georchestra/datadir/tree/master/gateway).
For detailed configuration options, see the [administration guide](https://docs.georchestra.org/gateway/en/latest/technical_guides/administration/)
and the [configuration reference](https://docs.georchestra.org/gateway/en/latest/technical_guides/administration/properties/).

## Building from Source

### Prerequisites

- Java 21+
- Maven 3.8+
- Docker (optional, for building images)

### Build Commands

Build with all tests:
```bash
make
```

Build and install without tests:
```bash
make install
```

Run tests:
```bash
make test
```

Build Docker image:
```bash
make docker
```

Build Debian package:
```bash
make deb
```

## Documentation Development

To work on the documentation locally:

The documentation follows the [geOrchestra documentation template](https://github.com/georchestra/georchestra_documentation_template)
(MkDocs + Material) and is published on Read the Docs.

```bash
bash mkdocs_installation.sh   # creates the venv_mkdocs Python virtual environment
bash mkdocs_run.sh            # serves the documentation
```

Then visit http://127.0.0.1:8000 in your browser.

## Contributing

We welcome contributions! Please see our [Contribution guide](https://docs.georchestra.org/gateway/en/latest/technical_guides/contribute/) for details.

## About geOrchestra

[geOrchestra](https://www.georchestra.org/) is a free, modular, and interoperable Spatial Data Infrastructure solution born in 2009 to meet the requirements of the INSPIRE European directive.

The project is governed by a Project Steering Committee (PSC) and follows the "made by people for people" philosophy, with a focus on community-driven development.

## License

This project is licensed under [GPL-3.0](LICENSE.txt).
# Development Docker Compose Files

This repository includes two Docker Compose files for development purposes:

- `docker-compose.yml`: Standard development setup with LDAP authentication
- `docker-compose-preauth.yaml`: Development setup with header pre-authentication via Nginx

**These files are for development purposes only and should not be used in production environments.**

For production deployments, please use the official [geOrchestra Docker Compose project](https://github.com/georchestra/docker) or follow the [Installation Guide](https://docs.georchestra.org/gateway/en/latest/technical_guides/installation/).

## Development Usage

To start the development environment:

```bash
# Standard setup
docker-compose up -d

# Or with pre-authentication
docker-compose -f docker-compose-preauth.yaml up -d
```

The development environment includes:
- LDAP server
- PostgreSQL database
- Gateway service
- Header proxy
- Test services (console, echo)

## Configuration

The Docker Compose files mount the local `./datadir` directory as configuration. See the [datadir README](./datadir/README.md) for more information.

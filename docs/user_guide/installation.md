# Installation

This guide explains how to install and deploy the geOrchestra Gateway.

## Prerequisites

General requirements for all installation methods:

- A geOrchestra data directory for configuration
- Access to an LDAP directory (for LDAP authentication)
- Optional: OAuth2/OpenID Connect provider credentials

Additional requirements depending on your installation method:

- **Docker/Kubernetes installation**: Docker or Kubernetes environment
- **Debian package installation**: Java 21 or higher installed on the host
- **JAR file installation**: Java 21 or higher installed on the host

!!! note
    Java is only required on the host system if you plan to run the Gateway directly using the Debian package or JAR file. If you're using Docker or Kubernetes, Java runs inside the container and is not needed on the host.

## Installation Options

There are several ways to deploy the geOrchestra Gateway:

1. Docker Compose (recommended for most deployments)
2. Kubernetes (recommended for large-scale deployments)
3. Debian package (for direct installation on Linux servers)
4. JAR file (Spring Boot application for custom deployments)

## Docker Compose Deployment

!!! warning "Development Docker Compose Files"
    Note that the `docker-compose.yml` and `docker-compose-preauth.yaml` files in the repository root are for **development purposes only** and should not be used for production deployments. For production, use the official geOrchestra Docker Compose project as described below.

### Using the geOrchestra Docker Compose Project

The recommended way to deploy the Gateway is using the official [geOrchestra Docker Compose project](https://github.com/georchestra/docker):

```bash
# Clone the repository
git clone https://github.com/georchestra/docker.git
cd docker

# Start the full stack (including the gateway)
docker-compose up -d
```

This will deploy the Gateway along with all required services (LDAP, PostgreSQL, etc.) and other geOrchestra components. Note that while PostgreSQL is used by other geOrchestra components, the Gateway itself does not use a database.

### Custom Docker Compose Setup

For a more targeted setup focusing just on the Gateway:

!!! note "Required vs. Optional Components"
    The Gateway itself only requires:
    - Configuration from a data directory volume
    - LDAP for authentication (or you can use OAuth2 instead)
    
    The example below includes a database service because it's typically needed for other geOrchestra components (Console, GeoServer, etc.), but the Gateway itself does not use a database. Your specific deployment can be simpler if you're only using the Gateway or more complex if deploying the full geOrchestra stack.

!!! tip "Actuator Port"
    By default, the Gateway exposes its Actuator endpoints (metrics, health checks, etc.) on port 8090, separate from the main application port (8080). Make sure to expose this port if you need to access monitoring and management features.

```yaml
# docker-compose.yml
version: '3'
services:
  ldap:
    image: georchestra/ldap:latest
    environment:
      - SLAPD_ORGANISATION=geOrchestra
      - SLAPD_DOMAIN=georchestra.org
      - SLAPD_PASSWORD=secret
    volumes:
      - ldap_data:/var/lib/ldap
      - ldap_config:/etc/ldap


  # Database used by other GeOrchestra components, not by Gateway itself
  database:
    image: postgres:14
    environment:
      - POSTGRES_USER=georchestra
      - POSTGRES_PASSWORD=georchestra
      - POSTGRES_DB=georchestra
    volumes:
      - pg_data:/var/lib/postgresql/data

  gateway:
    image: georchestra/gateway:latest
    depends_on:
      - ldap
    volumes:
      - georchestra_datadir:/etc/georchestra
    environment:
      - JAVA_TOOL_OPTIONS=-Dgeorchestra.datadir=/etc/georchestra
    env_file:
      - .envs-common
      - .envs-ldap
    ports:
      - "8080:8080"   # Main application port
      - "8090:8090"   # Actuator port for monitoring and management
    restart: unless-stopped

volumes:
  pg_data:
  ldap_data:
  ldap_config:
  georchestra_datadir:
```

The `.envs-common` file should contain:

```
FQDN=georchestra-127-0-0-1.nip.io
```

The `.envs-ldap` file should contain LDAP connection information:

```
LDAPHOST=ldap
LDAPPORT=389
LDAPSCHEME=ldap
LDAPADMINPASSWORD=secret
```

Launch with:

```bash
docker-compose up -d
```

### Debugging the Gateway Container

For debugging the Gateway, you can enable remote debugging by adding environment variables:

```yaml
gateway:
  environment:
    - JAVA_OPTIONS=-Dorg.eclipse.jetty.annotations.AnnotationParser.LEVEL=OFF -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=0.0.0.0:5005
    - XMS=256M
    - XMX=1G
  ports:
    - "5005:5005"  # Expose debug port
```

## Kubernetes Deployment

For large-scale or production deployments, Kubernetes provides better scalability, resilience, and management capabilities than Docker Compose.

### Basic Kubernetes Deployment

You can use the following manifests as a starting point for your Kubernetes deployment:

```yaml
# gateway-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: georchestra-gateway
spec:
  replicas: 2  # Multiple replicas for high availability
  selector:
    matchLabels:
      app: georchestra-gateway
  template:
    metadata:
      labels:
        app: georchestra-gateway
    spec:
      containers:
      - name: gateway
        image: georchestra/gateway:latest
        ports:
        - containerPort: 8080  # Main application port
        - containerPort: 8090  # Actuator port for monitoring and management
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "docker"
        - name: JAVA_TOOL_OPTIONS
          value: "-Dgeorchestra.datadir=/etc/georchestra"
        resources:
          requests:
            cpu: "500m"
            memory: "512Mi"
          limits:
            cpu: "2"
            memory: "1Gi"
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 10
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 120
          periodSeconds: 30
        volumeMounts:
        - name: datadir
          mountPath: /etc/georchestra
      volumes:
      - name: datadir
        configMap:
          name: georchestra-config
```

```yaml
# gateway-service.yaml
apiVersion: v1
kind: Service
metadata:
  name: georchestra-gateway
spec:
  selector:
    app: georchestra-gateway
  ports:
  - name: http
    port: 80
    targetPort: 8080
  - name: actuator
    port: 8090
    targetPort: 8090
  type: ClusterIP
```

```yaml
# gateway-ingress.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: georchestra-gateway
  annotations:
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
spec:
  rules:
  - host: georchestra.example.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: georchestra-gateway
            port:
              number: 80
  tls:
  - hosts:
    - georchestra.example.com
    secretName: georchestra-tls
```

### Deploying with Helm

For more advanced deployments, consider using Helm charts:

```bash
# Add the geOrchestra Helm repository (example)
helm repo add georchestra https://charts.georchestra.org

# Install the Gateway chart
helm install gateway georchestra/gateway \
  --set replicaCount=2 \
  --set ingress.enabled=true \
  --set ingress.hosts[0].host=georchestra.example.com
```

### Kubernetes Configuration Considerations

When deploying to Kubernetes, keep these considerations in mind:

1. **Configuration**: Store configuration in ConfigMaps or Secrets
2. **Persistence**: Use PersistentVolumeClaims for data that needs to persist
3. **Scaling**: Configure horizontal pod autoscaling for handling load spikes
4. **Network Policies**: Restrict network access between pods
5. **Resource Limits**: Set appropriate CPU and memory limits. The JVM heap is sized automatically from the container memory limit. The image sets `-XX:MaxDirectMemorySize=128M` by default for Netty's direct buffers; you can override it with `JAVA_TOOL_OPTIONS` (e.g. `-XX:MaxDirectMemorySize=256M`)
6. **Health Checks**: Implement readiness and liveness probes

## Debian Package Installation

### Installing from Package

A Debian package is available for installation on Debian/Ubuntu systems:

```bash
# Download the package
wget https://packages.georchestra.org/georchestra-gateway_latest.deb

# Install the package
sudo dpkg -i georchestra-gateway_latest.deb

# Install dependencies
sudo apt-get install -f
```

### Systemd Service

The Debian package includes a systemd service. You can control the service with:

```bash
# Start the service
sudo systemctl start georchestra-gateway

# Enable the service to start at boot
sudo systemctl enable georchestra-gateway

# Check the status
sudo systemctl status georchestra-gateway
```

## JAR Installation

### Building from Source

To build the Gateway from source:

```bash
# Clone the repository
git clone https://github.com/georchestra/georchestra-gateway.git
cd georchestra-gateway

# Build the project
./mvnw clean install

# The JAR file will be in gateway/target/georchestra-gateway-X.Y.Z.jar
```

### Running the JAR

Run the JAR file with:

```bash
java -Dgeorchestra.datadir=/path/to/datadir -jar gateway/target/georchestra-gateway-X.Y.Z.jar
```

## Configuration

After installation, configure the Gateway by editing the files in the geOrchestra data directory:

1. `default.properties` - Configuration properties common to all geOrchestra applications
2. `gateway/gateway.yaml` - Gateway-specific configuration
3. `gateway/security.yaml` - Security configuration
4. `gateway/routes.yaml` - Route definitions
5. `gateway/roles-mappings.yaml` - Role mapping configuration
6. `gateway/logging.yaml` - Logging configuration

See the [Configuration](configuration.md) page for detailed configuration options.

## Monitoring and Management

### Actuator Endpoints

The Gateway exposes Spring Boot Actuator endpoints on port 8090 for monitoring and management:

```yaml
management:
  server:
    port: 8090  # Separate port for management endpoints
  endpoints:
    web.exposure.include: "*"  # Expose all endpoints
```

#### Health Checks

Health check endpoints provide information about the Gateway's operational state:

- `/actuator/health` - Overall health status with component details
- `/actuator/health/liveness` - Simple liveness check for Kubernetes
- `/actuator/health/readiness` - Readiness check for Kubernetes (verifies dependencies)

#### Metrics

Metrics endpoints provide detailed performance and usage statistics:

- `/actuator/metrics` - List of all available metrics
- `/actuator/metrics/{metric.name}` - Detailed view of a specific metric
- `/actuator/prometheus` - Metrics in Prometheus format (for monitoring systems)

#### Other Useful Endpoints

- `/actuator/info` - Application information
- `/actuator/env` - Environment variables and configuration
- `/actuator/loggers` - View and modify logging levels at runtime
- `/actuator/mappings` - Request mapping information

These endpoints are accessible at `http://your-server:8090/actuator/*` and can be used for monitoring, troubleshooting, and runtime management of the Gateway.

## Verifying the Installation

To verify that the Gateway is running correctly:

1. Check the logs for any errors:
   ```bash
   docker logs georchestra-gateway
   # or
   sudo journalctl -u georchestra-gateway
   ```

2. Access the login page at `http://your-server:8080/login`

3. Try to authenticate (if LDAP or OAuth2 is configured)

## Troubleshooting

### Common Issues

1. **Gateway fails to start with "Connection refused" errors**:
     - Ensure LDAP server is accessible
     - Check LDAP configuration in `security.yaml`

2. **"No route found for path" errors**:
     - Check routes configuration in `routes.yaml`
     - Ensure target services are running

3. **Cannot authenticate**:
     - Verify LDAP or OAuth2 configuration
     - Check logs for authentication errors

### Getting Help

If you encounter issues, you can:

- Check the [geOrchestra documentation](https://docs.georchestra.org/)
- Ask for help on the [geOrchestra mailing list](https://groups.google.com/forum/#!forum/georchestra)
- File an issue on [GitHub](https://github.com/georchestra/georchestra-gateway/issues)


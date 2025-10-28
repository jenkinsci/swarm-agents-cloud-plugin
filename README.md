# Swarm Agents Cloud Plugin for Jenkins

[![Jenkins Plugin](https://img.shields.io/badge/jenkins-plugin-blue.svg)](https://plugins.jenkins.io/)
[![Java 17+](https://img.shields.io/badge/java-17%2B-blue.svg)](https://adoptium.net/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

Provision Jenkins agents dynamically on Docker Swarm clusters.

## Features

- **Dynamic Agent Provisioning**: Automatically creates Docker Swarm services when build demand increases
- **WebSocket Support**: Modern agent connection via WebSocket (no inbound TCP ports required)
- **TLS/SSL Authentication**: Full support for Docker TLS certificates
- **Configuration as Code**: Full JCasC compatibility
- **Resource Management**: CPU/memory limits and reservations per template
- **Health Checks**: Configurable container health monitoring
- **Secrets Support**: Docker Swarm secrets integration
- **Rate Limiting**: Built-in provisioning rate limits to prevent thundering herd
- **Dashboard**: Real-time cluster monitoring at `/swarm-dashboard`
- **REST API**: Programmatic management at `/swarm-api`
- **Template Inheritance**: Inherit settings from parent templates (like K8s `inheritFrom`)
- **GPU Support**: Generic resource allocation for NVIDIA GPUs and other hardware
- **Security Profiles**: Seccomp and AppArmor profile configuration
- **Prometheus Metrics**: `/swarm-api/prometheus` endpoint for monitoring integration
- **Audit Logging**: Track all provisioning and termination events
- **Pipeline DSL**: Native `swarmAgent` step for Jenkinsfiles
- **Retry with Backoff**: Automatic exponential backoff retry on provision failures
- **Configurable Timeouts**: Per-template connection and idle timeouts

## Requirements

- Jenkins 2.479.3 or newer
- Java 17 or newer
- Docker Swarm cluster (initialized with `docker swarm init`)

## Installation

1. Download the `.hpi` file from releases (or build from source)
2. Go to **Manage Jenkins** > **Plugins** > **Advanced settings**
3. Upload the `.hpi` file under **Deploy Plugin**
4. Restart Jenkins

## Configuration

### Via UI

1. Go to **Manage Jenkins** > **Clouds**
2. Click **New cloud** > **Docker Swarm Agents Cloud**
3. Configure:
   - **Docker Host**: `tcp://your-swarm-manager:2376`
   - **Credentials**: Docker Server Credentials (for TLS)
   - **Max Concurrent Agents**: Limit total agents from this cloud

### Via Configuration as Code (JCasC)

```yaml
jenkins:
  clouds:
    - swarmAgentsCloud:
        name: "docker-swarm"
        dockerHost: "tcp://swarm-manager:2376"
        credentialsId: "docker-tls-creds"
        maxConcurrentAgents: 10
        jenkinsUrl: "http://jenkins:8080/"
        swarmNetwork: "jenkins-agents"
        templates:
          - name: "maven"
            image: "jenkins/inbound-agent:latest"
            labelString: "maven docker"
            remoteFs: "/home/jenkins/agent"
            numExecutors: 2
            maxInstances: 5
            cpuLimit: "2.0"
            memoryLimit: "4g"
            mounts:
              - type: "bind"
                source: "/var/run/docker.sock"
                target: "/var/run/docker.sock"
            environmentVariables:
              - key: "MAVEN_OPTS"
                value: "-Xmx1g"
```

## Agent Templates

Each template defines how agent containers are created:

| Field | Description | Default |
|-------|-------------|---------|
| `name` | Template identifier | Required |
| `image` | Docker image | `jenkins/inbound-agent:latest` |
| `labelString` | Jenkins labels (space-separated) | - |
| `remoteFs` | Agent working directory | `/home/jenkins/agent` |
| `numExecutors` | Executors per agent | 1 |
| `maxInstances` | Max containers from template | 5 |
| `cpuLimit` | CPU limit (e.g., "2.0") | - |
| `memoryLimit` | Memory limit (e.g., "4g") | - |

### Advanced Container Options

| Field | Description |
|-------|-------------|
| `privileged` | Run container with elevated privileges |
| `user` | Run as specific user (e.g., "1000:1000") |
| `hostname` | Container hostname |
| `capAddString` | Linux capabilities to add (CAP_NET_ADMIN, etc.) |
| `capDropString` | Linux capabilities to drop |
| `sysctlsString` | Kernel parameters (one per line) |
| `dnsServersString` | Custom DNS servers (comma-separated) |
| `stopSignal` | Signal to stop container (SIGTERM, SIGKILL) |
| `stopGracePeriod` | Grace period in seconds before force kill |

### Template Inheritance

Templates can inherit settings from a parent template using `inheritFrom`:

```yaml
templates:
  - name: "base"
    image: "jenkins/inbound-agent:latest"
    cpuLimit: "2.0"
    memoryLimit: "4g"
    seccompProfile: "default"
    connectionTimeoutSeconds: 300
    idleTimeoutMinutes: 30

  - name: "maven"
    inheritFrom: "base"          # Inherits all settings from "base"
    labelString: "maven docker"
    environmentVariables:
      - key: "MAVEN_OPTS"
        value: "-Xmx1g"
```

Child templates override parent values only when explicitly set.

### GPU and Generic Resources

Allocate NVIDIA GPUs or other generic resources:

```yaml
templates:
  - name: "ml-training"
    image: "nvidia/cuda:12.0-runtime"
    genericResources:
      - kind: "NVIDIA-GPU"
        value: 1
```

Requires Docker Swarm configured with GPU support (`nvidia-container-runtime`).

### Security Profiles

Configure Seccomp and AppArmor profiles (Docker Engine 29+):

```yaml
templates:
  - name: "secure-build"
    seccompProfile: "default"           # or custom profile path
    apparmorProfile: "runtime/default"  # or "unconfined"
```

### Timeouts and Retry

| Field | Description | Default |
|-------|-------------|---------|
| `connectionTimeoutSeconds` | Max time to wait for agent connection | 300 |
| `idleTimeoutMinutes` | Idle time before automatic termination | 30 |
| `provisionRetryCount` | Retries on provision failure | 3 |
| `provisionRetryDelayMs` | Initial retry delay (exponential backoff) | 1000 |

## Pipeline DSL

Use `swarmAgent` step in Jenkinsfiles:

### Using Existing Template
```groovy
pipeline {
    agent none
    stages {
        stage('Build') {
            steps {
                swarmAgent(cloud: 'docker-swarm', template: 'maven') {
                    sh 'mvn clean package'
                }
            }
        }
    }
}
```

### Inline Template Configuration
```groovy
swarmAgent(
    cloud: 'docker-swarm',
    image: 'jenkins/inbound-agent:alpine',
    label: 'build',
    cpuLimit: '2.0',
    memoryLimit: '4g',
    idleTimeout: 30
) {
    sh 'npm install && npm test'
}
```

### Pipeline Step Parameters

| Parameter | Description | Required |
|-----------|-------------|----------|
| `cloud` | Name of Swarm cloud | Yes |
| `template` | Existing template name | No |
| `image` | Docker image (inline template) | No |
| `label` | Agent label (inline template) | No |
| `cpuLimit` | CPU limit | No |
| `memoryLimit` | Memory limit | No |
| `idleTimeout` | Idle timeout in minutes | No |
| `connectionTimeout` | Connection timeout in seconds | No |

## REST API

Base URL: `http://jenkins/swarm-api/`

### Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/clouds` | List all Swarm clouds |
| GET | `/cloud?name=X` | Get cloud details |
| GET | `/templates?cloud=X` | List templates |
| GET | `/template?cloud=X&name=Y` | Get single template |
| GET | `/agents?cloud=X` | List running agents |
| GET | `/metrics?cloud=X` | Get cluster metrics |
| GET | `/prometheus` | Prometheus metrics (OpenMetrics format) |
| GET | `/audit?cloud=X&limit=N` | Audit log entries |
| POST | `/provision?cloud=X&template=Y` | Provision new agent |
| PUT | `/template` | Update template configuration |

### Update Template Example

```bash
curl -X PUT "http://jenkins/swarm-api/template" \
  -H "Content-Type: application/json" \
  -u admin:token \
  -d '{
    "cloud": "docker-swarm",
    "template": "maven",
    "image": "jenkins/inbound-agent:alpine-jdk21"
  }'
```

### Supported Update Fields

- `image` - Docker image
- `labelString` - Labels
- `maxInstances` - Max instances
- `numExecutors` - Number of executors
- `cpuLimit` - CPU limit
- `memoryLimit` - Memory limit
- `remoteFs` - Remote filesystem path

## Dashboard

Access the real-time dashboard at `http://jenkins/swarm-dashboard/`

Features:
- Cluster health status
- Node information (hostname, state, resources)
- Running services and their states
- Resource utilization (CPU, Memory)
- Quick actions (refresh, remove service)

## Prometheus Metrics

Integrate with Prometheus monitoring at `/swarm-api/prometheus`:

```bash
curl http://jenkins/swarm-api/prometheus
```

Available metrics:
- `swarm_clouds_total` - Total configured clouds
- `swarm_cloud_healthy` - Cloud health status (0/1)
- `swarm_agents_max/current` - Agent capacity and usage
- `swarm_nodes_total/ready` - Swarm node counts
- `swarm_tasks_running/pending/failed` - Task states
- `swarm_memory_total_bytes/used_bytes` - Memory usage
- `swarm_cpu_total/used` - CPU usage
- `swarm_template_instances_max/current` - Per-template metrics

Prometheus scrape config example:

```yaml
scrape_configs:
  - job_name: 'jenkins-swarm'
    metrics_path: '/swarm-api/prometheus'
    static_configs:
      - targets: ['jenkins:8080']
```

## Audit Logging

All provisioning, termination, and configuration events are logged:

```bash
# Get recent audit entries
curl "http://jenkins/swarm-api/audit?limit=50"

# Filter by cloud
curl "http://jenkins/swarm-api/audit?cloud=docker-swarm&limit=100"
```

Audit events:
- `PROVISION` - Agent successfully provisioned
- `TERMINATE` - Agent terminated
- `PROVISION_FAILED` - Provisioning failure
- `CONFIG_CHANGE` - Template configuration changed
- `API_ACCESS` - REST API accessed
- `CONNECTION_TEST_SUCCESS/FAILED` - Connection test results

## Docker Host URL Format

Use the correct protocol for your Docker connection:

| Connection Type | URL Format | Example |
|----------------|------------|---------|
| TCP (remote) | `tcp://host:port` | `tcp://swarm-manager:2376` |
| Unix socket | `unix:///path/to/socket` | `unix:///var/run/docker.sock` |
| Named pipe (Windows) | `npipe:////./pipe/name` | `npipe:////./pipe/docker_engine` |

**Important**: Use `tcp://` not `https://` even for TLS connections!

## TLS Configuration

For secure Docker connections:

1. Create Docker Server Credentials in Jenkins:
   - Go to **Manage Jenkins** > **Credentials**
   - Add **Docker Server Credentials**
   - Paste CA certificate, client certificate, and client key

2. Generate certificates (if needed):
```bash
# On Docker Swarm manager
openssl genrsa -out ca-key.pem 4096
openssl req -new -x509 -days 365 -key ca-key.pem -out ca.pem -subj "/CN=Docker CA"
openssl genrsa -out client-key.pem 4096
openssl req -new -key client-key.pem -out client.csr -subj "/CN=client"
openssl x509 -req -days 365 -in client.csr -CA ca.pem -CAkey ca-key.pem -CAcreateserial -out client-cert.pem
```

3. Configure Docker daemon (`/etc/docker/daemon.json`):
```json
{
  "hosts": ["unix:///var/run/docker.sock", "tcp://0.0.0.0:2376"],
  "tls": true,
  "tlscacert": "/etc/docker/ca.pem",
  "tlscert": "/etc/docker/server-cert.pem",
  "tlskey": "/etc/docker/server-key.pem",
  "tlsverify": true
}
```

## Docker Swarm Setup

### Create a Swarm cluster

```bash
# Initialize swarm on manager
docker swarm init

# Get join token for workers
docker swarm join-token worker

# Join workers to swarm
docker swarm join --token <token> <manager-ip>:2377
```

### Create overlay network

```bash
docker network create --driver overlay --attachable jenkins-agents
```

## Usage in Pipeline

```groovy
pipeline {
    agent {
        label 'maven'
    }
    stages {
        stage('Build') {
            steps {
                sh 'mvn clean package'
            }
        }
    }
}
```

## Troubleshooting

### Common Errors

| Error | Solution |
|-------|----------|
| "Unsupported protocol scheme: https" | Use `tcp://` instead of `https://` |
| "Connection refused" | Check Docker daemon is running and API is exposed |
| "TLS handshake failed" | Configure Docker Server Credentials |
| "This node is not a swarm manager" | Run `docker swarm init` on the host |
| "Unknown host" | Check hostname/IP address |
| "Connection timed out" | Check firewall rules, ensure port 2376 is open |

### Enable Debug Logging

In Jenkins, go to **Manage Jenkins** > **System Log** > **Add new log recorder**:
- Logger: `io.jenkins.plugins.swarmcloud`
- Level: `FINE`

## Comparison with docker-swarm-plugin

This plugin addresses known issues in the [official docker-swarm-plugin](https://plugins.jenkins.io/docker-swarm/):

| Issue | docker-swarm-plugin | swarm-agents-cloud |
|-------|--------------------|--------------------|
| XSS vulnerabilities (SECURITY-2811) | Affected | Fixed |
| TLS support | Broken | Working |
| Error messages | Generic | Detailed |
| Quiet-down handling (#113) | Jobs hang | Graceful stop |
| Resource monitoring (#129) | Always 100% free | Real usage |
| REST API updates (#123) | Read-only | Full CRUD |
| Mount parameter (#121) | Missing | Supported |
| Container limits (#116) | N/A | maxInstances |
| Extra parameters (#120) | Limited | capabilities, sysctls, dns |
| Maintenance | Abandoned (5+ years) | Active |

## Building from Source

```bash
# Clone repository
git clone https://github.com/jenkinsci/swarm-agents-cloud-plugin.git
cd swarm-agents-cloud-plugin

# Build (skip tests for faster build)
mvn clean package -Dmaven.test.skip=true

# Plugin will be at target/swarm-agents-cloud.hpi

# Run with local Jenkins
mvn hpi:run
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Run tests: `mvn test`
5. Submit a pull request

## License

MIT License - see [LICENSE](LICENSE) for details.

## Links

- [Jenkins Plugin Site](https://plugins.jenkins.io/)
- [Issue Tracker](https://github.com/jenkinsci/swarm-agents-cloud-plugin/issues)
- [Docker Swarm Documentation](https://docs.docker.com/engine/swarm/)
- [Jenkins Inbound Agent Image](https://hub.docker.com/r/jenkins/inbound-agent)

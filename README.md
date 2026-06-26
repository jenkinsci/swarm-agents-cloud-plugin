# Swarm Agents Cloud Plugin for Jenkins

[![en](https://img.shields.io/badge/lang-en-blue.svg)](README.md)
[![ru](https://img.shields.io/badge/lang-ru-green.svg)](README.ru.md)

[![Jenkins Plugin](https://img.shields.io/jenkins/plugin/v/swarm-agents-cloud.svg)](https://plugins.jenkins.io/swarm-agents-cloud/)
[![Java 21+](https://img.shields.io/badge/java-21%2B-blue.svg)](https://adoptium.net/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

A modern Jenkins plugin for dynamic agent provisioning on Docker Swarm clusters.

## Why This Plugin?

This plugin is a **modern replacement** for the abandoned [docker-swarm-plugin](https://github.com/jenkinsci/docker-swarm-plugin) (last updated February 2021, 54+ unresolved issues).

### Comparison with docker-swarm-plugin

| Feature | **swarm-agents-cloud** | docker-swarm-plugin |
|---------|------------------------|---------------------|
| **Last Update** | 2025 (active) | 2021 (abandoned) |
| **Open Issues** | — | 54+ |
| **Java Version** | 21+ | 8+ |
| **Jenkins Version** | 2.528.3+ | Outdated |
| **WebSocket Connection** | ✅ | ❌ |
| **JCasC Support** | ✅ Full | ❌ |
| **REST API** | ✅ Full CRUD | ❌ |
| **Prometheus Metrics** | ✅ | ❌ |
| **Docker Secrets/Configs** | ✅ | ❌ |
| **Template Inheritance** | ✅ | ❌ |
| **Rate Limiting** | ✅ | ❌ |
| **Health Checks** | ✅ | ❌ |
| **Pipeline DSL** | ✅ `swarmAgent {}` | ❌ |
| **Audit Logging** | ✅ | ❌ |
| **Orphan Cleanup** | ✅ Automatic | ❌ |
| **Dark Theme Dashboard** | ✅ | ❌ |
| **TLS Support** | ✅ Working | ⚠️ Broken |
| **XSS Vulnerabilities** | ✅ Fixed | ⚠️ SECURITY-2811 |

### Key Advantages

- **Modern Stack** — Java 21, WebSocket, current Jenkins API
- **DevOps-Ready** — JCasC, REST API, Prometheus, Pipeline DSL
- **Security** — TLS, Secrets, Input Validation
- **Reliability** — Rate Limiting, Retry with Backoff, Health Checks, Orphan Cleanup
- **Active Support** — Regular updates vs abandoned project

## Features

- **Dynamic Agent Provisioning** — Automatically creates Docker Swarm services on demand
- **WebSocket Support** — Modern agent connection (no inbound TCP ports required)
- **TLS/SSL Authentication** — Full Docker TLS certificate support
- **Configuration as Code** — Complete JCasC compatibility
- **Resource Management** — CPU/memory limits and reservations per template
- **Health Checks** — Configurable container health monitoring
- **Secrets & Configs** — Docker Swarm secrets and configs integration
- **Rate Limiting** — Built-in provisioning rate limits (thundering herd protection)
- **Dashboard** — Real-time cluster monitoring at `/swarm-dashboard`
- **REST API** — Programmatic management at `/swarm-api`
- **Template Inheritance** — Inherit settings from parent templates (`inheritFrom`)
- **Prometheus Metrics** — `/swarm-api/prometheus` endpoint
- **Audit Logging** — Track all provisioning events
- **Pipeline DSL** — Native `swarmAgent` step for Jenkinsfiles
- **Retry with Backoff** — Automatic exponential backoff on failures
- **Orphan Cleanup** — Automatic cleanup of stale services

## Requirements

- Jenkins 2.528.3 or newer (see [Jenkins Java requirements](https://www.jenkins.io/doc/book/platform-information/support-policy-java/) for Java version)
- Docker Swarm cluster (`docker swarm init`)

## Quick Start

### Via UI

1. Go to **Manage Jenkins** → **Clouds**
2. Click **New cloud** → **Docker Swarm Agents Cloud**
3. Configure:
   - **Docker Host**: `tcp://your-swarm-manager:2376`
   - **Credentials**: Docker Server Credentials (for TLS)
   - **Max Concurrent Agents**: Limit total agents

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
            connectionTimeoutSeconds: 300
            idleTimeoutMinutes: 30
```

## Agent Templates

| Field | Description | Default |
|-------|-------------|---------|
| `name` | Template identifier | Required |
| `image` | Docker image | `jenkins/inbound-agent:latest` |
| `labelString` | Jenkins labels (space-separated) | — |
| `remoteFs` | Agent working directory | `/home/jenkins/agent` |
| `numExecutors` | Executors per agent | 1 |
| `maxInstances` | Max containers from template | 5 |
| `cpuLimit` | CPU limit (e.g., "2.0") | — |
| `memoryLimit` | Memory limit (e.g., "4g") | — |
| `cpuReservation` | CPU reservation | — |
| `memoryReservation` | Memory reservation | — |

### Advanced Options

| Field | Description |
|-------|-------------|
| `privileged` | Run with elevated privileges |
| `oneShot` | Terminate the agent after a single completed build (requires `numExecutors=1`) |
| `user` | Run as specific user (e.g., "1000:1000") |
| `hostname` | Container hostname |
| `entrypoint` | Custom container entrypoint |
| `disableContainerArgs` | Don't pass args to entrypoint |
| `capAdd` / `capAddString` | Linux capabilities to add (requires Docker Engine 20.10+ / API 1.41+) |
| `capDrop` / `capDropString` | Linux capabilities to drop (requires Docker Engine 20.10+ / API 1.41+) |
| `dnsServersString` | Custom DNS servers |

### Connection, Timeouts & Retry

These template fields control how long the plugin waits for an agent and how it
retries provisioning. All are optional and fall back to the defaults below.

| Field | Description | Default |
|-------|-------------|---------|
| `connectionTimeoutSeconds` | Max time to wait for the agent to connect before giving up | 300 |
| `idleTimeoutMinutes` | Idle time before a (non one-shot) agent is terminated | 30 |
| `provisionRetryCount` | Number of provisioning retries on failure | 3 |
| `provisionRetryDelayMs` | Initial delay between retries in ms (exponential backoff) | 1000 |

```yaml
templates:
  - name: "build"
    image: "jenkins/inbound-agent:latest"
    connectionTimeoutSeconds: 300
    idleTimeoutMinutes: 30
    provisionRetryCount: 3
    provisionRetryDelayMs: 1000
```

### Health Checks

Attach a Docker container health check so the agent container is monitored while
it runs. A health check is enabled only when `healthCheckCommand` is set — the
plugin wraps the command as `CMD-SHELL`, so provide just the shell command.

| Field | Description | Default |
|-------|-------------|---------|
| `healthCheckCommand` | Shell command run inside the container to check health. Empty = no health check | — |
| `healthCheckIntervalSeconds` | Time between checks | 30 |
| `healthCheckTimeoutSeconds` | Time to wait for a single check before treating it as failed | 10 |
| `healthCheckRetries` | Consecutive failures before the container is marked unhealthy | 3 |

```yaml
templates:
  - name: "with-healthcheck"
    image: "jenkins/inbound-agent:latest"
    healthCheckCommand: "curl -f http://localhost:8080/ || exit 1"
    healthCheckIntervalSeconds: 30
    healthCheckTimeoutSeconds: 10
    healthCheckRetries: 3
```

All of these fields are merged through template inheritance like the rest.

### Template Inheritance

```yaml
templates:
  - name: "base"
    image: "jenkins/inbound-agent:latest"
    cpuLimit: "2.0"
    memoryLimit: "4g"
    connectionTimeoutSeconds: 300

  - name: "maven"
    inheritFrom: "base"
    labelString: "maven docker"
    environmentVariables:
      - key: "MAVEN_OPTS"
        value: "-Xmx1g"
```

Inheritance is resolved across **multiple levels**, so you can build a hierarchy
(`base` → intermediate → leaf) and each template only declares what it adds:

```yaml
templates:
  - name: "base"                 # shared defaults for everything
    image: "jenkins/inbound-agent:latest"
    cpuLimit: "2.0"

  - name: "jvm"                  # adds JVM-specific settings
    inheritFrom: "base"
    environmentVariables:
      - key: "JAVA_OPTS"
        value: "-XX:MaxRAMPercentage=75"

  - name: "maven"               # leaf: inherits the whole base → jvm chain
    inheritFrom: "jvm"
    labelString: "maven"
```

Resolution rules:

- Descendant values take precedence over ancestors; lists (environment variables,
  mounts, capabilities, etc.) are merged across the whole chain.
- Cyclic references (`a` → `b` → `a`) are detected and stopped with a warning
  instead of recursing forever.
- Label matching uses the **resolved** template. A template with no effective
  `labelString` — neither its own nor an inherited one, like a `base` kept only
  for inheritance — matches only label-less jobs; it is **not** a catch-all and
  will not provision agents for jobs that require a specific label. Labels declared
  on an ancestor are inherited, so a leaf is matched by them too.

### Ephemeral / One-Shot Agents

Set `oneShot: true` on a template to make agents self-destruct immediately after a single
completed build, instead of being kept alive for the idle-timeout window. Useful when each
build needs a guaranteed-clean filesystem and process state (security-sensitive jobs,
workloads that leak temporary state, etc.).

```yaml
templates:
  - name: "ephemeral-build"
    image: "jenkins/inbound-agent:latest"
    labelString: "ephemeral"
    numExecutors: 1            # Must be 1 with oneShot=true
    oneShot: true
    idleTimeoutMinutes: 1      # Fallback for the rare "agent connected but no build dispatched" case
```

**Trade-offs:**

- Each build pays the full container provisioning cost (image pull + agent connection
  handshake), adding tens of seconds to queue time.
- `numExecutors` must be `1`. The form rejects `oneShot=true` combined with
  `numExecutors > 1` because the one-shot retention strategy terminates the agent after
  the first completed build, which would abort any other build still running on a second
  executor.
- If a parent template (referenced via `inheritFrom`) has `oneShot: true`, child templates
  inherit it and cannot disable it (same OR-merge as `privileged`).

### Container Capabilities

`capAdd` / `capDrop` propagate Linux capabilities to the agent container's
`TaskSpec.ContainerSpec`. Docker Engine 20.10 (API 1.41) or newer is required — older
Engine versions silently ignore these fields.

```yaml
templates:
  - name: "net-admin-agent"
    image: "jenkins/inbound-agent:latest"
    capAdd:
      - "CAP_NET_ADMIN"
    capDrop:
      - "CAP_CHOWN"
```

The newline-separated string form (`capAddString`) is also accepted for compatibility
with existing docker-swarm-plugin configurations.

### Docker Secrets

```yaml
templates:
  - name: "with-secrets"
    secrets:
      - secretName: "my-secret"
        fileName: "secret.txt"
        targetPath: "/run/secrets"
```

### Docker Configs

Docker Swarm **configs** work like secrets but are meant for non-sensitive
configuration data. Only `configName` is required; the other fields are optional.

| Field | Description | Default |
|-------|-------------|---------|
| `configName` | Name of the existing Docker Swarm config | Required |
| `targetPath` | Mount path inside the container | `/{configName}` |
| `fileName` | File name at the target location | Derived from `targetPath` |
| `fileMode` | File permissions in octal (e.g. `0644`) | — |
| `uid` / `gid` | Owner UID / GID of the file inside the container | — |

```yaml
templates:
  - name: "with-configs"
    configs:
      - configName: "app-config"
        targetPath: "/etc/app/config.ini"
        fileName: "config.ini"
        fileMode: "0644"
```

### Registry Authentication (Private Images)

Support for pulling images from private Docker registries when launching agents.

**Supported Registries:**
- Docker Hub (public and private repositories)
- Google Container Registry (gcr.io)
- AWS Elastic Container Registry (ECR)
- GitHub Container Registry (ghcr.io)
- Azure Container Registry (azurecr.io)
- Any private registry with username/password authentication

**Setup Steps:**

1. **Create Credentials in Jenkins:**
   - Go to **Manage Jenkins** → **Credentials**
   - Add **Username with password** credentials for your registry
   - Note the credentials ID

2. **Configure Template:**
   - In template configuration, select credentials from **Registry Credentials** dropdown
   - Plugin automatically detects registry from image name

**Configuration as Code Example:**

```yaml
jenkins:
  clouds:
    - swarmAgentsCloud:
        name: "docker-swarm"
        templates:
          - name: "private-agent"
            image: "myregistry.com/jenkins-agent:latest"
            registryCredentialsId: "docker-registry-creds"
            labelString: "private docker"
```

**UI Configuration:**
Navigate to template settings → **Registry Credentials** dropdown → Select your credentials

**Template Inheritance:**
Registry credentials can be inherited from parent templates:

```yaml
templates:
  - name: "base-private"
    image: "myregistry.com/base:latest"
    registryCredentialsId: "docker-registry-creds"

  - name: "maven-private"
    inheritFrom: "base-private"
    image: "myregistry.com/maven:latest"
    # Inherits registryCredentialsId from base-private
```

### Extra Hosts (Custom /etc/hosts Entries)

Add custom hostname-to-IP mappings to container's `/etc/hosts` file, equivalent to Docker's `--add-host` flag.

**Use Cases:**
- Local development and testing
- Custom DNS resolution for internal services
- Database and service aliases

**Format:** `hostname:IP` (one entry per line, supports IPv4 and IPv6)

**Configuration as Code Example:**

```yaml
templates:
  - name: "agent-with-hosts"
    image: "jenkins/inbound-agent:latest"
    extraHosts:
      - "database.local:10.0.0.50"
      - "internal-registry:192.168.1.100"
      - "api.internal:172.16.0.10"
```

**UI Configuration:**
Navigate to template settings → **Extra Hosts** textarea → Enter `hostname:IP` pairs (one per line)

**Example:**
```
myhost:192.168.1.1
database:10.0.0.5
registry.local:172.16.0.20
```

**Template Inheritance:**
Extra hosts are merged when using template inheritance:

```yaml
templates:
  - name: "base"
    extraHosts:
      - "shared-db:10.0.0.1"

  - name: "maven"
    inheritFrom: "base"
    extraHosts:
      - "maven-repo:192.168.1.50"
    # Container will have both extra hosts entries
```

**Validation:**
- Automatic validation of IP addresses (IPv4 and IPv6)
- Hostname format checking
- Clear error messages for invalid entries

## Pipeline DSL

### Declarative pipeline (`agent { swarmAgent { ... } }`)

The `swarmAgent` directive is registered with the Pipeline Syntax → Declarative
Directive Generator → `agent`, so the new plugin is a drop-in replacement for
classic `docker-swarm-plugin` usage:

```groovy
pipeline {
    agent {
        swarmAgent {
            cloud 'docker-swarm'
            template 'maven'
            label 'maven java'
        }
    }
    stages {
        stage('Build') {
            steps { sh 'mvn clean package' }
        }
    }
}
```

If only one Docker Swarm cloud is configured, `cloud` may be omitted. The label
defaults to the referenced template's `labelString` when not given. Inline
configuration without a template is also supported:

```groovy
pipeline {
    agent {
        swarmAgent {
            image 'jenkins/inbound-agent:alpine'
            label 'docker'
            cpuLimit '2.0'
            memoryLimit '4g'
        }
    }
    stages { ... }
}
```

### Scripted pipeline (`swarmAgent { ... }` step)

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

## Image Requirements / Custom Images

When the plugin provisions an inbound agent, it passes Jenkins connection details to the
container in **two parallel ways** so most images "just work":

- **Environment variables** (always set):
  - `JENKINS_URL`, `JENKINS_AGENT_NAME`, `JENKINS_SECRET`, `JENKINS_AGENT_WORKDIR`
  - `JENKINS_WEB_SOCKET=true` (WebSocket inbound protocol)
  - Aliases for compatibility: `JNLP_URL`, `JENKINS_JNLP_URL`,
    `DOCKER_SWARM_PLUGIN_JENKINS_AGENT_JNLP_URL`, `DOCKER_SWARM_PLUGIN_JENKINS_AGENT_SECRET`
- **Command-line args** (positional): `[<JENKINS_URL>, <SECRET>, <AGENT_NAME>]`, appended
  to the image's `ENTRYPOINT`.

### Recommended: the official inbound-agent image

```yaml
templates:
  - name: default
    image: "jenkins/inbound-agent:alpine"   # or :latest, :jdk21, ...
    labelString: "docker"
```

`jenkins/inbound-agent` ships with `ENTRYPOINT ["/usr/local/bin/jenkins-agent"]` that
accepts the positional args correctly — no extra configuration needed.

### Custom images without a Jenkins-compatible ENTRYPOINT

If your image has **no `ENTRYPOINT`** (or one that does not understand the
`url secret name` argument convention), the positional args become the container's whole
command. Docker will then try to `exec` the URL as a binary and fail with:

```text
OCI runtime create failed: ... exec: "https://<jenkins>/": no such file or directory
```

Two equivalent fixes — pick whichever fits your image:

**Option A — let the plugin set a real ENTRYPOINT** (`entrypoint:` takes a free-form
command, args are skipped):

```yaml
templates:
  - name: custom
    image: "my-registry/my-agent:1.2.3"
    entrypoint: "/usr/local/bin/my-startup.sh"
```

**Option B — use env vars only** (the image must read `$JENKINS_URL`, `$JENKINS_SECRET`,
`$JENKINS_AGENT_NAME` itself):

```yaml
templates:
  - name: custom
    image: "my-registry/my-agent:1.2.3"
    disableContainerArgs: true   # skip positional [url, secret, name] args
```

`disableContainerArgs` is the safest default for slim or non-standard images.

## REST API

Base URL: `http://jenkins/swarm-api/`

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/clouds` | List all clouds |
| GET | `/cloud?name=X` | Cloud details |
| GET | `/templates?cloud=X` | List templates |
| GET | `/template?cloud=X&name=Y` | Template details |
| GET | `/agents?cloud=X` | List agents |
| GET | `/prometheus` | Prometheus metrics |
| GET | `/audit?cloud=X` | Audit log |
| POST | `/provision?cloud=X&template=Y` | Provision agent |
| PUT | `/template` | Update template |

## Dashboard

Access at `http://jenkins/swarm-dashboard/`

- Cluster health overview
- Node status and resources
- Active services list
- Dark theme support

## Prometheus Metrics

```text
http://jenkins/swarm-api/prometheus
```

Metrics: `swarm_agents_total`, `swarm_agents_active`, `swarm_nodes_total`, `swarm_memory_total_bytes`, etc.

## Troubleshooting

### Enable Debug Logging

**Manage Jenkins** → **System Log** → Add logger `io.jenkins.plugins.swarmcloud` with level `FINE`

### Common Issues

| Error | Solution |
|-------|----------|
| "Unsupported protocol scheme: https" | Use `tcp://` not `https://` |
| `OCI runtime create failed: ... exec: "https://...": no such file or directory` | Image has no `ENTRYPOINT`. Use `jenkins/inbound-agent`, set `entrypoint:` on the template, or set `disableContainerArgs: true`. See [Image Requirements](#image-requirements--custom-images). The plugin also surfaces a `HINT:` line in the build log on this failure. |
| "Connection refused" | Check Docker API is exposed |
| "TLS handshake failed" | Configure Docker Server Credentials |
| "This node is not a swarm manager" | Run `docker swarm init` |

## Building from Source

```bash
git clone https://github.com/jenkinsci/swarm-agents-cloud-plugin.git
cd swarm-agents-cloud-plugin
mvn clean package -DskipTests
# Result: target/swarm-agents-cloud.hpi
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md)

## License

MIT License — see [LICENSE](LICENSE)

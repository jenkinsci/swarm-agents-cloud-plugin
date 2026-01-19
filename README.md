# Swarm Agents Cloud Plugin for Jenkins

[![en](https://img.shields.io/badge/lang-en-blue.svg)](README.md)
[![ru](https://img.shields.io/badge/lang-ru-green.svg)](README.ru.md)

[![Jenkins Plugin](https://img.shields.io/badge/jenkins-plugin-blue.svg)](https://plugins.jenkins.io/)
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
| **GPU Support** | ✅ | ❌ |
| **Rate Limiting** | ✅ | ❌ |
| **Health Checks** | ✅ | ❌ |
| **Security Profiles** | ✅ Seccomp, AppArmor | ❌ |
| **Pipeline DSL** | ✅ `swarmAgent {}` | ❌ |
| **Audit Logging** | ✅ | ❌ |
| **Orphan Cleanup** | ✅ Automatic | ❌ |
| **Dark Theme Dashboard** | ✅ | ❌ |
| **TLS Support** | ✅ Working | ⚠️ Broken |
| **XSS Vulnerabilities** | ✅ Fixed | ⚠️ SECURITY-2811 |

### Key Advantages

- **Modern Stack** — Java 21, WebSocket, current Jenkins API
- **DevOps-Ready** — JCasC, REST API, Prometheus, Pipeline DSL
- **Security** — TLS, Secrets, Security Profiles, Input Validation
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
- **GPU Support** — Generic resource allocation for NVIDIA GPUs
- **Security Profiles** — Seccomp and AppArmor configuration
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
| `user` | Run as specific user (e.g., "1000:1000") |
| `hostname` | Container hostname |
| `command` | Custom entrypoint command |
| `disableContainerArgs` | Don't pass args to entrypoint |
| `capAddString` | Linux capabilities to add |
| `capDropString` | Linux capabilities to drop |
| `dnsServersString` | Custom DNS servers |
| `seccompProfile` | Seccomp security profile |
| `apparmorProfile` | AppArmor security profile |

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

### Docker Secrets

```yaml
templates:
  - name: "with-secrets"
    secrets:
      - secretName: "my-secret"
        fileName: "secret.txt"
        targetPath: "/run/secrets"
```

### GPU Support

```yaml
templates:
  - name: "ml-training"
    image: "nvidia/cuda:12.0-runtime"
    genericResources:
      - kind: "NVIDIA-GPU"
        value: 1
```

## Pipeline DSL

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

## REST API

Base URL: `http://jenkins/swarm-api/`

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/status` | Cluster status |
| GET | `/clouds` | List all clouds |
| GET | `/templates?cloud=X` | List templates |
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
| "Connection refused" | Check Docker API is exposed |
| "TLS handshake failed" | Configure Docker Server Credentials |
| "This node is not a swarm manager" | Run `docker swarm init` |

## Building from Source

```bash
git clone https://github.com/AronMav/swarm-agents-cloud.git
cd swarm-agents-cloud
mvn clean package -DskipTests
# Result: target/swarm-agents-cloud.hpi
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md)

## License

MIT License — see [LICENSE](LICENSE)

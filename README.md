# Swarm Agents Cloud Plugin

Jenkins plugin for provisioning agents on Docker Swarm clusters.

## Features

- **WebSocket Connections**: Modern agent connectivity through WebSocket (with JNLP fallback)
- **Real Monitoring**: Accurate CPU/RAM metrics from Docker Stats API
- **Configuration as Code**: Full JCasC support for declarative configuration
- **Resource Management**: CPU and memory limits/reservations per template
- **Volume Mounts**: Support for bind mounts, volumes, and tmpfs
- **Placement Constraints**: Control agent placement within the cluster
- **Instance Limits**: Per-template and global agent limits
- **REST API**: Programmatic access to templates and services

## Requirements

- Jenkins 2.479+
- Java 17+
- Docker Swarm cluster

## Installation

1. Build the plugin:
   ```bash
   mvn clean package
   ```

2. Install the HPI file:
   - Go to Jenkins → Manage Jenkins → Plugins → Advanced
   - Upload `target/swarm-agents-cloud.hpi`
   - Restart Jenkins

## Configuration

### Via UI

1. Go to Jenkins → Manage Jenkins → Clouds
2. Click "Add a new cloud" → "Docker Swarm Agents Cloud"
3. Configure:
   - **Name**: Unique cloud name
   - **Docker Host**: Swarm manager URL (e.g., `tcp://swarm-manager:2376`)
   - **Jenkins URL**: URL for agents to connect back
   - **Swarm Network**: Docker network for agents
   - **Max Concurrent Agents**: Global limit

4. Add agent templates with:
   - Docker image
   - Labels
   - Resource constraints
   - Volume mounts
   - Environment variables

### Via Configuration as Code (JCasC)

```yaml
jenkins:
  clouds:
    - swarmAgentsCloud:
        name: "docker-swarm"
        dockerHost: "tcp://swarm-manager:2376"
        credentialsId: "docker-certs"
        jenkinsUrl: "http://jenkins:8080"
        swarmNetwork: "jenkins-network"
        maxConcurrentAgents: 10
        templates:
          - name: "maven-agent"
            image: "jenkins/inbound-agent:latest"
            labelString: "maven java"
            remoteFs: "/home/jenkins/agent"
            numExecutors: 2
            maxInstances: 5
            cpuLimit: "2.0"
            memoryLimit: "4g"
            mounts:
              - type: "volume"
                source: "maven-cache"
                target: "/root/.m2"
            environmentVariables:
              - name: "JAVA_OPTS"
                value: "-Xmx512m"
          - name: "node-agent"
            image: "jenkins/inbound-agent:latest"
            labelString: "node npm"
            maxInstances: 3
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

## Docker Swarm Setup

### Create a Swarm cluster

```bash
# Initialize swarm on manager
docker swarm init

# Join workers
docker swarm join --token <token> <manager-ip>:2377
```

### Create overlay network

```bash
docker network create --driver overlay --attachable jenkins-network
```

### Configure Docker for remote access

On the Swarm manager, enable TCP access:

```bash
# /etc/docker/daemon.json
{
  "hosts": ["unix:///var/run/docker.sock", "tcp://0.0.0.0:2376"],
  "tls": true,
  "tlscacert": "/etc/docker/ca.pem",
  "tlscert": "/etc/docker/server-cert.pem",
  "tlskey": "/etc/docker/server-key.pem",
  "tlsverify": true
}
```

## Development

### Build

```bash
mvn clean package
```

### Run tests

```bash
mvn test
```

### Run with Jenkins

```bash
mvn hpi:run
```

## License

MIT License

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Run tests: `mvn test`
5. Submit a pull request

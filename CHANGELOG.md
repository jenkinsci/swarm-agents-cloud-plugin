# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
Versions are automatically generated using Jenkins CD pipeline.

## [36.v2f7377c14682] - 2026-01-20

First official release in Jenkins Update Center.

### Added

#### Core Features

- Docker Swarm cloud provisioning for Jenkins agents
- WebSocket-based agent connection (primary) with fallback to TCP/JNLP
- Dynamic agent provisioning based on queue demand
- Template-based agent configuration with inheritance support (`inheritFrom`)

#### Configuration

- JCasC (Jenkins Configuration as Code) full support
- Comprehensive template settings:
  - Docker image configuration
  - Resource limits and reservations (CPU, Memory)
  - Volume mounts (binds, tmpfs, configs, secrets)
  - Network configuration
  - Environment variables
  - Labels and placement constraints
  - Health checks

#### Management & Monitoring

- Dashboard with real-time cluster status at `/swarm-dashboard`
- Agent lifecycle management (provision, terminate, cleanup)
- Automatic orphan service cleanup mechanism
- Prometheus metrics endpoint at `/swarm-api/prometheus`
- Comprehensive audit logging at `/swarm-api/audit`

#### REST API

- Full CRUD operations for clouds and templates
- Service management endpoints
- Status and health monitoring
- Programmatic provisioning

#### Pipeline Support

- Native `swarmAgent` pipeline step for declarative pipelines
- Agent template selection in pipeline scripts

#### Security

- CSRF protection on all API endpoints
- Permission-based access control (ADMINISTER)
- Secure secret handling (no secrets in logs)
- XSS protection in UI
- TLS/SSL authentication for Docker API

### Requirements

- Jenkins 2.528.3 or newer
- Java 21 or newer
- Docker Swarm cluster

[36.v2f7377c14682]: https://github.com/jenkinsci/swarm-agents-cloud-plugin/releases/tag/36.v2f7377c14682

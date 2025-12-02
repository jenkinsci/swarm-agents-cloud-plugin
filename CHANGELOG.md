# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2025-01-15

### Added

#### Core Features
- Docker Swarm cloud provisioning for Jenkins agents
- WebSocket-based agent connection (primary) with fallback to TCP/JNLP
- Dynamic agent provisioning based on queue demand
- Template-based agent configuration with inheritance support

#### Configuration
- JCasC (Jenkins Configuration as Code) support
- Comprehensive template settings:
  - Docker image configuration
  - Resource limits (CPU, Memory)
  - Volume mounts (binds, tmpfs, configs, secrets)
  - Network configuration
  - Environment variables
  - Labels and placement constraints

#### Management & Monitoring
- Dashboard with real-time cluster status
- Agent lifecycle management (provision, terminate, cleanup)
- Orphan service cleanup mechanism
- Prometheus metrics endpoint (`/prometheus`)
- Comprehensive audit logging

#### REST API
- Service management endpoints
- Template configuration API
- Status and health monitoring

#### Pipeline Support
- `swarmAgent` pipeline step for declarative pipelines
- Agent template selection in pipeline scripts

#### Security
- CSRF protection on all API endpoints
- Permission-based access control
- Secure secret handling (no secrets in logs)
- XSS protection in UI

### Security
- Fixed potential secret leak in container args logging
- Added CSRF protection to REST API endpoints

## [Unreleased]

### Planned
- Multi-cloud support
- Agent pooling for faster provisioning
- Custom health checks
- Extended metrics

[1.0.0]: https://github.com/AronMav/swarm-agents-cloud/releases/tag/v1.0.0
[Unreleased]: https://github.com/AronMav/swarm-agents-cloud/compare/v1.0.0...HEAD

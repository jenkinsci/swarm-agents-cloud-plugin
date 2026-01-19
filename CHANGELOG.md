# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
Versions are automatically generated using Jenkins CD pipeline.

## [1.1.0] - 2026-01-19

### Changed

#### Requirements
- Minimum Java version increased from 17 to 21
- Minimum Jenkins version updated to 2.528.3

#### Dashboard UI
- Migrated to Jenkins Design Library components (`jenkins-button`, `jenkins-table`, `jenkins-empty-state`)
- Custom `swarm-status` CSS badges using Jenkins CSS variables for theme compatibility
- Improved dark theme and solarized theme support

#### Dependencies
- Updated `git-changelist-maven-extension` from 1.8 to 1.13
- Updated `testcontainers` from 1.20.4 to 2.0.3
- Updated `testcontainers-junit-jupiter` to 2.0.3

#### Build
- Removed unnecessary build scripts (`build.cmd`, `run-tests.bat`, `mvnw.cmd`)
- Removed incomplete Maven Wrapper

### Fixed
- Fixed non-existent `jenkins-tag` CSS classes in dashboard
- Fixed CSP compliance issues with adjunct folder structure

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

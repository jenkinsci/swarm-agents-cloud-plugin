# Contributing to Swarm Agents Cloud Plugin

Thank you for your interest in contributing to the Swarm Agents Cloud Plugin!

## Getting Started

### Prerequisites

- JDK 21 or later
- Maven 3.9.6+
- Docker with Swarm mode enabled (for testing)
- Jenkins 2.528.3 or later

### Building

```bash
# Clone the repository
git clone https://github.com/AronMav/swarm-agents-cloud.git
cd swarm-agents-cloud

# Build the plugin
mvn clean package

# Run with embedded Jenkins for testing
mvn hpi:run
```

### Running Tests

```bash
# Run all tests
mvn verify

# Run only unit tests
mvn test

# Run with coverage
mvn verify jacoco:report
```

## Development

### Project Structure

```
src/
├── main/
│   ├── java/io/jenkins/plugins/swarmcloud/
│   │   ├── api/           # Docker Swarm API client
│   │   ├── metrics/       # Prometheus metrics
│   │   ├── pipeline/      # Pipeline step support
│   │   ├── rest/          # REST API endpoints
│   │   └── ...            # Core plugin classes
│   └── resources/
│       └── io/jenkins/plugins/swarmcloud/
│           └── */         # Jelly views and configs
└── test/
    └── java/              # Unit and integration tests
```

### Code Style

- Follow Jenkins coding conventions
- Use meaningful variable and method names
- Add Javadoc for public APIs
- Keep methods focused and small

### UI Development

Use Jenkins Design Library components instead of custom CSS:

- **Buttons**: `jenkins-button`, `jenkins-button--primary`, `jenkins-!-destructive-color`
- **Alerts**: `jenkins-alert`, `jenkins-alert-danger`
- **Tables**: `jenkins-table`
- **Empty state**: `<l:notice title="..." icon="...">`
- **Dialogs**: Use `dialog.confirm().then()` promise pattern
- **Enums in forms**: Use `<f:enum>` for dropdown selects

Reference: [Jenkins Design Library](https://weekly.ci.jenkins.io/design-library/)

### Testing

- Write unit tests for new functionality
- Use JUnit 5 for tests
- Mock external dependencies (Docker API)
- Test both success and failure scenarios

## Submitting Changes

### Pull Request Process

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/my-feature`)
3. Make your changes
4. Run tests (`mvn verify`)
5. Commit with clear message
6. Push to your fork
7. Open a Pull Request

### Commit Messages

Follow conventional commits:

```
feat: Add support for Docker configs
fix: Correct memory calculation in dashboard
docs: Update README with new features
test: Add tests for orphan service cleanup
```

### Pull Request Guidelines

- Keep changes focused and atomic
- Update documentation if needed
- Add tests for new features
- Ensure CI passes

## Reporting Issues

- Use GitHub Issues for bug reports and feature requests
- Include Jenkins version, plugin version, and Docker Swarm version
- Provide steps to reproduce for bugs
- Include relevant logs (sanitize secrets!)

## Security

If you discover a security vulnerability, please report it privately:
- Do NOT open a public issue
- Email the maintainer or use GitHub's security advisory feature

## License

By contributing, you agree that your contributions will be licensed under the MIT License.

## Getting Help

- Open an issue for questions
- Check existing issues and PRs
- Review Jenkins plugin development docs: https://www.jenkins.io/doc/developer/

Thank you for contributing!

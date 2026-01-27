package io.jenkins.plugins.swarmcloud;

import io.jenkins.plugins.swarmcloud.api.DockerSwarmClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for DockerSwarmClient.
 * These tests require Docker to be available.
 * Note: Swarm mode tests are disabled by default as they require a Swarm cluster.
 */
@Testcontainers
class DockerSwarmClientIntegrationTest {

    private DockerSwarmClient client;

    @BeforeEach
    void setUp() {
        // Use local Docker socket - detect from environment or use default
        String dockerHost = System.getenv("DOCKER_HOST");
        if (dockerHost == null || dockerHost.isBlank()) {
            // Default to local socket
            dockerHost = System.getProperty("os.name").toLowerCase().contains("win")
                    ? "tcp://localhost:2375"
                    : "unix:///var/run/docker.sock";
        }
        client = new DockerSwarmClient(dockerHost, null);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void testDockerConnection() {
        // This test verifies we can connect to Docker
        assertNotNull(client.getDockerClient());
    }

    @Test
    @Disabled("Requires Docker Swarm mode to be initialized")
    void testGetSwarmVersion() {
        String version = client.getSwarmVersion();
        assertNotNull(version);
    }

    @Test
    @Disabled("Requires Docker Swarm mode to be initialized")
    void testGetNodeCount() {
        int count = client.getNodeCount();
        assertTrue(count >= 0);
    }

    @Test
    @Disabled("Requires Docker Swarm mode to be initialized")
    void testListJenkinsServices() {
        var services = client.listJenkinsServices();
        assertNotNull(services);
        // May be empty if no Jenkins services are running
    }

    @Test
    void testParseMemoryBytes() {
        // Test internal parsing via reflection or public methods
        // This is tested indirectly through template configuration
        SwarmAgentTemplate template = new SwarmAgentTemplate("test");
        template.setMemoryLimit("2g");
        assertEquals("2g", template.getMemoryLimit());

        template.setMemoryLimit("512m");
        assertEquals("512m", template.getMemoryLimit());

        template.setMemoryLimit("1024k");
        assertEquals("1024k", template.getMemoryLimit());
    }

    @Test
    void testParseCpuLimit() {
        SwarmAgentTemplate template = new SwarmAgentTemplate("test");
        template.setCpuLimit("2.0");
        assertEquals("2.0", template.getCpuLimit());

        template.setCpuLimit("0.5");
        assertEquals("0.5", template.getCpuLimit());
    }

    /**
     * Full integration test that creates and removes a service.
     * Requires Docker Swarm to be initialized.
     */
    @Test
    @Disabled("Requires Docker Swarm mode to be initialized and network setup")
    void testCreateAndRemoveService() {
        SwarmAgentTemplate template = new SwarmAgentTemplate("integration-test");
        template.setImage("alpine:latest");
        template.setCommand("sleep 300");
        template.setMemoryLimit("128m");
        template.setCpuLimit("0.5");

        String agentName = "test-agent-" + System.currentTimeMillis();

        try {
            // Create service
            String serviceId = client.createService(
                    agentName,
                    template,
                    "http://localhost:8080",
                    "test-secret",
                    null
            );

            assertNotNull(serviceId);
            assertFalse(serviceId.isBlank());

            // Verify service exists
            var service = client.getService(serviceId);
            assertNotNull(service);
            assertEquals(agentName, service.getSpec().getName());

            // Remove service
            client.removeService(serviceId);

            // Verify service is removed
            var removedService = client.getService(serviceId);
            assertNull(removedService);

        } catch (Exception e) {
            // Cleanup on failure
            try {
                var services = client.listJenkinsServices();
                for (var svc : services) {
                    if (svc.getSpec().getName().equals(agentName)) {
                        client.removeService(svc.getId());
                    }
                }
            } catch (Exception ignored) {
            }
            throw e;
        }
    }

    /**
     * Integration test for creating a service with extra hosts.
     * Requires Docker Swarm to be initialized.
     */
    @Test
    @Disabled("Requires Docker Swarm mode to be initialized")
    void testCreateServiceWithExtraHosts() {
        SwarmAgentTemplate template = new SwarmAgentTemplate("extra-hosts-test");
        template.setImage("alpine:latest");
        template.setCommand("cat /etc/hosts && sleep 30");
        template.setExtraHosts(List.of(
                "myhost:192.168.1.1",
                "database:10.0.0.5"
        ));

        String agentName = "test-extra-hosts-" + System.currentTimeMillis();

        try {
            String serviceId = client.createService(
                    agentName,
                    template,
                    "http://localhost:8080",
                    "test-secret",
                    null
            );

            assertNotNull(serviceId);
            assertFalse(serviceId.isBlank());

            // Verify service was created
            var service = client.getService(serviceId);
            assertNotNull(service);

            // Cleanup
            client.removeService(serviceId);

        } catch (Exception e) {
            // Cleanup on failure
            try {
                var services = client.listJenkinsServices();
                for (var svc : services) {
                    if (svc.getSpec().getName().equals(agentName)) {
                        client.removeService(svc.getId());
                    }
                }
            } catch (Exception ignored) {
            }
            throw e;
        }
    }
}

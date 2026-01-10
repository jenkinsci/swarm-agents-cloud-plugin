package io.jenkins.plugins.swarmcloud;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SwarmComputerLauncher.
 */
@WithJenkins
class SwarmComputerLauncherTest {

    private JenkinsRule jenkins;

    @BeforeEach
    void setUp(JenkinsRule jenkins) {
        this.jenkins = jenkins;
    }

    @Test
    void testLauncherCreation() {
        SwarmComputerLauncher launcher = new SwarmComputerLauncher(
                "test-cloud",
                "jenkins/inbound-agent:latest"
        );

        assertEquals("test-cloud", launcher.getCloudName());
        assertEquals("jenkins/inbound-agent:latest", launcher.getImage());
        assertTrue(launcher.isUseWebSocket());
        assertEquals(300, launcher.getConnectionTimeoutSeconds()); // Default 5 minutes
    }

    @Test
    void testLauncherWithCustomTimeout() {
        SwarmComputerLauncher launcher = new SwarmComputerLauncher(
                "test-cloud",
                "custom/agent:v1",
                true,
                null,
                "/work",
                600
        );

        assertEquals("test-cloud", launcher.getCloudName());
        assertEquals("custom/agent:v1", launcher.getImage());
        assertTrue(launcher.isUseWebSocket());
        assertEquals("/work", launcher.getWorkDir());
        assertEquals(600, launcher.getConnectionTimeoutSeconds());
    }

    @Test
    void testLauncherWithTcpMode() {
        SwarmComputerLauncher launcher = new SwarmComputerLauncher(
                "test-cloud",
                "jenkins/inbound-agent:latest",
                false, // TCP mode
                "jenkins:50000",
                "/home/jenkins",
                300
        );

        assertFalse(launcher.isUseWebSocket());
    }

    @Test
    void testInvalidTimeoutUsesDefault() {
        SwarmComputerLauncher launcher = new SwarmComputerLauncher(
                "test-cloud",
                "jenkins/inbound-agent:latest",
                true,
                null,
                null,
                0 // Invalid
        );

        assertEquals(300, launcher.getConnectionTimeoutSeconds()); // Should use default
    }

    @Test
    void testNegativeTimeoutUsesDefault() {
        SwarmComputerLauncher launcher = new SwarmComputerLauncher(
                "test-cloud",
                "jenkins/inbound-agent:latest",
                true,
                null,
                null,
                -100 // Negative
        );

        assertEquals(300, launcher.getConnectionTimeoutSeconds()); // Should use default
    }

    @Test
    void testGetAgentSecret() {
        // This requires Jenkins to be running
        String secret = SwarmComputerLauncher.getAgentSecret("test-agent");

        assertNotNull(secret);
        assertFalse(secret.isEmpty());

        // Same agent name should produce same secret
        String secret2 = SwarmComputerLauncher.getAgentSecret("test-agent");
        assertEquals(secret, secret2);

        // Different agent name should produce different secret
        String secret3 = SwarmComputerLauncher.getAgentSecret("other-agent");
        assertNotEquals(secret, secret3);
    }

    @Test
    void testBuildAgentCommandWithWebSocket() {
        String[] args = SwarmComputerLauncher.buildAgentCommand(
                "http://jenkins:8080/",
                "my-agent",
                "secret-123",
                true,
                "/home/jenkins/agent"
        );

        assertNotNull(args);
        assertTrue(args.length >= 6);

        // Verify required arguments are present
        boolean hasUrl = false, hasWebSocket = false, hasName = false, hasSecret = false, hasWorkDir = false;
        for (int i = 0; i < args.length; i++) {
            if ("-url".equals(args[i])) hasUrl = true;
            if ("-webSocket".equals(args[i])) hasWebSocket = true;
            if ("-name".equals(args[i])) hasName = true;
            if ("-secret".equals(args[i])) hasSecret = true;
            if ("-workDir".equals(args[i])) hasWorkDir = true;
        }

        assertTrue(hasUrl, "Should have -url argument");
        assertTrue(hasWebSocket, "Should have -webSocket argument");
        assertTrue(hasName, "Should have -name argument");
        assertTrue(hasSecret, "Should have -secret argument");
        assertTrue(hasWorkDir, "Should have -workDir argument");
    }

    @Test
    void testBuildAgentCommandWithoutWebSocket() {
        String[] args = SwarmComputerLauncher.buildAgentCommand(
                "http://jenkins:8080/",
                "my-agent",
                "secret-123",
                false, // No WebSocket
                null
        );

        assertNotNull(args);

        // Should NOT have -webSocket
        boolean hasWebSocket = false;
        for (String arg : args) {
            if ("-webSocket".equals(arg)) hasWebSocket = true;
        }

        assertFalse(hasWebSocket, "Should NOT have -webSocket argument");
    }

    @Test
    void testBuildAgentCommandWithoutWorkDir() {
        String[] args = SwarmComputerLauncher.buildAgentCommand(
                "http://jenkins:8080/",
                "my-agent",
                "secret-123",
                true,
                null // No workDir
        );

        // Should NOT have -workDir
        boolean hasWorkDir = false;
        for (String arg : args) {
            if ("-workDir".equals(arg)) hasWorkDir = true;
        }

        assertFalse(hasWorkDir, "Should NOT have -workDir argument when workDir is null");

        // Test with blank workDir
        args = SwarmComputerLauncher.buildAgentCommand(
                "http://jenkins:8080/",
                "my-agent",
                "secret-123",
                true,
                "   " // Blank workDir
        );

        hasWorkDir = false;
        for (String arg : args) {
            if ("-workDir".equals(arg)) hasWorkDir = true;
        }

        assertFalse(hasWorkDir, "Should NOT have -workDir argument when workDir is blank");
    }

    @Test
    void testBuildAgentEnvironment() {
        Map<String, String> env = SwarmComputerLauncher.buildAgentEnvironment(
                "http://jenkins:8080/",
                "my-agent",
                "secret-123",
                true,
                "/home/jenkins/agent"
        );

        assertNotNull(env);
        assertEquals("http://jenkins:8080/", env.get("JENKINS_URL"));
        assertEquals("my-agent", env.get("JENKINS_AGENT_NAME"));
        assertEquals("secret-123", env.get("JENKINS_SECRET"));
        assertEquals("true", env.get("JENKINS_WEB_SOCKET"));
        assertEquals("/home/jenkins/agent", env.get("JENKINS_AGENT_WORKDIR"));
        assertNotNull(env.get("JENKINS_DIRECT_CONNECTION"));
    }

    @Test
    void testBuildAgentEnvironmentWithoutWebSocket() {
        Map<String, String> env = SwarmComputerLauncher.buildAgentEnvironment(
                "http://jenkins:8080/",
                "my-agent",
                "secret-123",
                false, // No WebSocket
                null
        );

        assertNull(env.get("JENKINS_WEB_SOCKET"));
        assertNull(env.get("JENKINS_AGENT_WORKDIR"));
    }

    @Test
    void testBuildAgentEnvironmentDirectConnection() {
        Map<String, String> env = SwarmComputerLauncher.buildAgentEnvironment(
                "http://jenkins:8080/",
                "my-agent",
                "secret-123",
                true,
                null
        );

        // Should strip protocol from direct connection
        assertEquals("jenkins:8080/", env.get("JENKINS_DIRECT_CONNECTION"));

        env = SwarmComputerLauncher.buildAgentEnvironment(
                "https://secure-jenkins:8443/",
                "my-agent",
                "secret-123",
                true,
                null
        );

        assertEquals("secure-jenkins:8443/", env.get("JENKINS_DIRECT_CONNECTION"));
    }

    @Test
    void testGetJnlpUrl() {
        String url = SwarmComputerLauncher.getJnlpUrl("http://jenkins:8080/", "my-agent");
        assertEquals("http://jenkins:8080/computer/my-agent/jenkins-agent.jnlp", url);

        // Without trailing slash
        url = SwarmComputerLauncher.getJnlpUrl("http://jenkins:8080", "my-agent");
        assertEquals("http://jenkins:8080/computer/my-agent/jenkins-agent.jnlp", url);
    }

    @Test
    void testDescriptorDisplayName() {
        SwarmComputerLauncher.DescriptorImpl descriptor = new SwarmComputerLauncher.DescriptorImpl();
        assertEquals("Docker Swarm Agent Launcher", descriptor.getDisplayName());
    }
}

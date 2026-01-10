package io.jenkins.plugins.swarmcloud;

import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.RetentionStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SwarmAgent.
 */
@WithJenkins
class SwarmAgentTest {

    private JenkinsRule jenkins;
    private SwarmCloud cloud;
    private SwarmAgentTemplate template;

    @BeforeEach
    void setUp(JenkinsRule jenkins) {
        this.jenkins = jenkins;
        cloud = new SwarmCloud("test-cloud");
        cloud.setDockerHost("tcp://localhost:2376");

        template = new SwarmAgentTemplate("test-template");
        template.setImage("jenkins/inbound-agent:latest");
        template.setLabelString("test-label");
        template.setNumExecutors(2);
        template.setRemoteFs("/home/jenkins/agent");
        template.setConnectionTimeoutSeconds(120);
    }

    @Test
    void testAgentCreation() throws Descriptor.FormException, IOException {
        SwarmAgent agent = new SwarmAgent(
                "test-agent",
                template,
                "test-cloud",
                "service-123"
        );

        assertEquals("test-agent", agent.getNodeName());
        assertEquals("test-cloud", agent.getCloudName());
        assertEquals("service-123", agent.getServiceId());
        assertEquals("test-template", agent.getTemplateName());
    }

    @Test
    void testAgentProperties() throws Descriptor.FormException, IOException {
        SwarmAgent agent = new SwarmAgent(
                "test-agent",
                template,
                "test-cloud",
                "service-456"
        );

        assertEquals("/home/jenkins/agent", agent.getRemoteFS());
        assertEquals(2, agent.getNumExecutors());
        assertEquals("test-label", agent.getLabelString());
        assertEquals(Node.Mode.NORMAL, agent.getMode());
    }

    @Test
    void testAgentDescription() throws Descriptor.FormException, IOException {
        SwarmAgent agent = new SwarmAgent(
                "my-agent",
                template,
                "test-cloud",
                "service-789"
        );

        assertEquals("Swarm Agent my-agent", agent.getNodeDescription());
    }

    @Test
    void testCreatedTime() throws Descriptor.FormException, IOException {
        long beforeCreation = System.currentTimeMillis();

        SwarmAgent agent = new SwarmAgent(
                "test-agent",
                template,
                "test-cloud",
                "service-123"
        );

        long afterCreation = System.currentTimeMillis();

        assertTrue(agent.getCreatedTime() >= beforeCreation);
        assertTrue(agent.getCreatedTime() <= afterCreation);
    }

    @Test
    void testCustomIdleTimeout() throws Descriptor.FormException, IOException {
        SwarmAgent agent = new SwarmAgent(
                "test-agent",
                template,
                "test-cloud",
                "service-123",
                30 // 30 minutes idle timeout
        );

        RetentionStrategy<?> retention = agent.getRetentionStrategy();
        assertNotNull(retention);
        assertInstanceOf(SwarmRetentionStrategy.class, retention);

        SwarmRetentionStrategy swarmRetention = (SwarmRetentionStrategy) retention;
        assertEquals(30, swarmRetention.getIdleMinutes());
    }

    @Test
    void testDefaultIdleTimeout() throws Descriptor.FormException, IOException {
        SwarmAgent agent = new SwarmAgent(
                "test-agent",
                template,
                "test-cloud",
                "service-123"
        );

        RetentionStrategy<?> retention = agent.getRetentionStrategy();
        assertNotNull(retention);
        assertInstanceOf(SwarmRetentionStrategy.class, retention);

        SwarmRetentionStrategy swarmRetention = (SwarmRetentionStrategy) retention;
        assertEquals(10, swarmRetention.getIdleMinutes()); // Default is 10 minutes
    }

    @Test
    void testInvalidIdleTimeoutUsesDefault() throws Descriptor.FormException, IOException {
        SwarmAgent agent = new SwarmAgent(
                "test-agent",
                template,
                "test-cloud",
                "service-123",
                0 // Invalid - should use default
        );

        RetentionStrategy<?> retention = agent.getRetentionStrategy();
        SwarmRetentionStrategy swarmRetention = (SwarmRetentionStrategy) retention;
        assertEquals(10, swarmRetention.getIdleMinutes()); // Should use default
    }

    @Test
    void testNegativeIdleTimeoutUsesDefault() throws Descriptor.FormException, IOException {
        SwarmAgent agent = new SwarmAgent(
                "test-agent",
                template,
                "test-cloud",
                "service-123",
                -5 // Negative - should use default
        );

        RetentionStrategy<?> retention = agent.getRetentionStrategy();
        SwarmRetentionStrategy swarmRetention = (SwarmRetentionStrategy) retention;
        assertEquals(10, swarmRetention.getIdleMinutes()); // Should use default
    }

    @Test
    void testComputerLauncher() throws Descriptor.FormException, IOException {
        SwarmAgent agent = new SwarmAgent(
                "test-agent",
                template,
                "test-cloud",
                "service-123"
        );

        ComputerLauncher launcher = agent.getLauncher();
        assertNotNull(launcher);
        assertInstanceOf(SwarmComputerLauncher.class, launcher);

        SwarmComputerLauncher swarmLauncher = (SwarmComputerLauncher) launcher;
        assertEquals("test-cloud", swarmLauncher.getCloudName());
        assertEquals("jenkins/inbound-agent:latest", swarmLauncher.getImage());
        assertTrue(swarmLauncher.isUseWebSocket());
    }

    @Test
    void testConnectionTimeout() throws Descriptor.FormException, IOException {
        template.setConnectionTimeoutSeconds(180);

        SwarmAgent agent = new SwarmAgent(
                "test-agent",
                template,
                "test-cloud",
                "service-123"
        );

        ComputerLauncher launcher = agent.getLauncher();
        SwarmComputerLauncher swarmLauncher = (SwarmComputerLauncher) launcher;
        assertEquals(180, swarmLauncher.getConnectionTimeoutSeconds());
    }

    @Test
    void testCreateComputer() throws Descriptor.FormException, IOException {
        SwarmAgent agent = new SwarmAgent(
                "test-agent",
                template,
                "test-cloud",
                "service-123"
        );

        // Register agent with Jenkins to properly initialize computer
        jenkins.jenkins.addNode(agent);

        SwarmAgent.SwarmComputer computer = (SwarmAgent.SwarmComputer) agent.toComputer();
        assertNotNull(computer);
        // After registration, getNode() should return the agent
        assertNotNull(computer.getNode());
        assertEquals("service-123", computer.getServiceId());
        assertEquals("test-cloud", computer.getCloudName());

        // Clean up
        jenkins.jenkins.removeNode(agent);
    }

    @Test
    void testSwarmComputerToString() throws Descriptor.FormException, IOException {
        SwarmAgent agent = new SwarmAgent(
                "test-agent",
                template,
                "test-cloud",
                "service-123"
        );

        // Register agent with Jenkins to properly initialize computer
        jenkins.jenkins.addNode(agent);

        SwarmAgent.SwarmComputer computer = (SwarmAgent.SwarmComputer) agent.toComputer();
        assertNotNull(computer);
        String str = computer.toString();

        assertTrue(str.contains("SwarmComputer"));
        // After registration, toString should include serviceId and cloudName
        assertTrue(str.contains("service-123"));
        assertTrue(str.contains("test-cloud"));

        // Clean up
        jenkins.jenkins.removeNode(agent);
    }

    @Test
    void testDescriptorDisplayName() {
        SwarmAgent.DescriptorImpl descriptor = new SwarmAgent.DescriptorImpl();
        assertEquals("Docker Swarm Agent", descriptor.getDisplayName());
    }

    @Test
    void testDescriptorNotInstantiable() {
        SwarmAgent.DescriptorImpl descriptor = new SwarmAgent.DescriptorImpl();
        assertFalse(descriptor.isInstantiable());
    }

    @Test
    void testGetCloudWhenNotRegistered() throws Descriptor.FormException, IOException {
        SwarmAgent agent = new SwarmAgent(
                "test-agent",
                template,
                "non-existent-cloud",
                "service-123"
        );

        // Cloud is not registered in Jenkins
        assertNull(agent.getCloud());
    }

    @Test
    void testGetTemplateWhenCloudNotRegistered() throws Descriptor.FormException, IOException {
        SwarmAgent agent = new SwarmAgent(
                "test-agent",
                template,
                "non-existent-cloud",
                "service-123"
        );

        // Cloud is not registered, so template should be null
        assertNull(agent.getTemplate());
    }
}

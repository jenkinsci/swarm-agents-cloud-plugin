package io.jenkins.plugins.swarmcloud;

import hudson.model.Label;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SwarmCloud.
 */
@WithJenkins
class SwarmCloudTest {

    private JenkinsRule jenkins;
    private SwarmCloud cloud;

    @BeforeEach
    void setUp(JenkinsRule jenkins) {
        this.jenkins = jenkins;
        cloud = new SwarmCloud("test-swarm");
        cloud.setDockerHost("tcp://localhost:2376");
        cloud.setMaxConcurrentAgents(10);
    }

    @Test
    void testCloudCreation() {
        assertEquals("test-swarm", cloud.name);
        assertEquals("tcp://localhost:2376", cloud.getDockerHost());
        assertEquals(10, cloud.getMaxConcurrentAgents());
    }

    @Test
    void testDefaultValues() {
        SwarmCloud defaultCloud = new SwarmCloud("default");
        assertEquals(10, defaultCloud.getMaxConcurrentAgents());
        assertTrue(defaultCloud.getTemplates().isEmpty());
        assertEquals("", defaultCloud.getDockerHost());
    }

    @Test
    void testSetTemplates() {
        SwarmAgentTemplate template1 = new SwarmAgentTemplate("maven");
        template1.setImage("jenkins/inbound-agent:latest");
        template1.setLabelString("maven java");

        SwarmAgentTemplate template2 = new SwarmAgentTemplate("node");
        template2.setImage("jenkins/inbound-agent:latest");
        template2.setLabelString("node npm");

        cloud.setTemplates(List.of(template1, template2));

        assertEquals(2, cloud.getTemplates().size());
        assertSame(cloud, template1.getParent());
        assertSame(cloud, template2.getParent());
    }

    @Test
    void testGetTemplateByLabel() {
        SwarmAgentTemplate mavenTemplate = new SwarmAgentTemplate("maven");
        mavenTemplate.setLabelString("maven java");

        SwarmAgentTemplate nodeTemplate = new SwarmAgentTemplate("node");
        nodeTemplate.setLabelString("node npm");

        cloud.setTemplates(List.of(mavenTemplate, nodeTemplate));

        SwarmAgentTemplate found = cloud.getTemplate(Label.parseExpression("maven"));
        assertNotNull(found);
        assertEquals("maven", found.getName());

        found = cloud.getTemplate(Label.parseExpression("node"));
        assertNotNull(found);
        assertEquals("node", found.getName());

        found = cloud.getTemplate(Label.parseExpression("python"));
        assertNull(found);
    }

    @Test
    void testEffectiveJenkinsUrl() {
        // With custom URL
        cloud.setJenkinsUrl("http://custom-jenkins:8080/");
        assertEquals("http://custom-jenkins:8080/", cloud.getEffectiveJenkinsUrl());

        // Without custom URL - should fall back to Jenkins root URL
        cloud.setJenkinsUrl(null);
        String effectiveUrl = cloud.getEffectiveJenkinsUrl();
        assertNotNull(effectiveUrl);
    }

    @Test
    void testMaxConcurrentAgentsValidation() {
        cloud.setMaxConcurrentAgents(-5);
        assertEquals(10, cloud.getMaxConcurrentAgents()); // Should default to 10

        cloud.setMaxConcurrentAgents(0);
        assertEquals(10, cloud.getMaxConcurrentAgents()); // Should default to 10

        cloud.setMaxConcurrentAgents(50);
        assertEquals(50, cloud.getMaxConcurrentAgents());
    }

    @Test
    void testCanProvision() {
        SwarmAgentTemplate template = new SwarmAgentTemplate("test");
        template.setLabelString("test-label");
        cloud.setTemplates(List.of(template));

        assertTrue(cloud.canProvision());
    }

    @Test
    void testCountCurrentAgents() {
        // No agents initially
        assertEquals(0, cloud.countCurrentAgents());
    }
}

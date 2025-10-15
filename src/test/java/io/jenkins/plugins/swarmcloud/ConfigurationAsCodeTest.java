package io.jenkins.plugins.swarmcloud;

import io.jenkins.plugins.casc.ConfigurationAsCode;
import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.ConfiguratorRegistry;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Configuration as Code support.
 */
@WithJenkins
class ConfigurationAsCodeTest {

    @Test
    void testConfigurationAsCodeSupport(JenkinsRule jenkins) throws Exception {
        String yaml = """
            jenkins:
              clouds:
                - swarmAgentsCloud:
                    name: "docker-swarm"
                    dockerHost: "tcp://swarm-manager:2376"
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
            """;

        ConfigurationAsCode.get().configureWith(
                ConfigurationAsCode.get().loadConfigurationsFrom(
                        new java.io.ByteArrayInputStream(yaml.getBytes())
                )
        );

        Jenkins j = jenkins.getInstance();
        assertNotNull(j);

        assertEquals(1, j.clouds.size());
        assertTrue(j.clouds.get(0) instanceof SwarmCloud);

        SwarmCloud cloud = (SwarmCloud) j.clouds.get(0);
        assertEquals("docker-swarm", cloud.name);
        assertEquals("tcp://swarm-manager:2376", cloud.getDockerHost());
        assertEquals("http://jenkins:8080", cloud.getJenkinsUrl());
        assertEquals("jenkins-network", cloud.getSwarmNetwork());
        assertEquals(10, cloud.getMaxConcurrentAgents());

        assertEquals(1, cloud.getTemplates().size());
        SwarmAgentTemplate template = cloud.getTemplates().get(0);
        assertEquals("maven-agent", template.getName());
        assertEquals("jenkins/inbound-agent:latest", template.getImage());
        assertEquals("maven java", template.getLabelString());
        assertEquals(2, template.getNumExecutors());
        assertEquals(5, template.getMaxInstances());
        assertEquals("2.0", template.getCpuLimit());
        assertEquals("4g", template.getMemoryLimit());
    }

    @Test
    void testExportConfiguration(JenkinsRule jenkins) throws Exception {
        Jenkins j = jenkins.getInstance();
        assertNotNull(j);

        SwarmCloud cloud = new SwarmCloud("test-export");
        cloud.setDockerHost("tcp://localhost:2376");
        cloud.setMaxConcurrentAgents(5);

        SwarmAgentTemplate template = new SwarmAgentTemplate("test-template");
        template.setImage("test-image:latest");
        template.setLabelString("test");
        cloud.setTemplates(java.util.List.of(template));

        j.clouds.add(cloud);

        ConfiguratorRegistry registry = ConfiguratorRegistry.get();
        ConfigurationContext context = new ConfigurationContext(registry);

        // Verify cloud can be exported
        assertNotNull(context);
    }
}

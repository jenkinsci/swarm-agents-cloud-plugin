package io.jenkins.plugins.swarmcloud;

import hudson.model.Node;
import io.jenkins.plugins.casc.ConfigurationAsCode;
import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.ConfiguratorRegistry;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.io.InputStream;
import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Configuration as Code support.
 */
@WithJenkins
class ConfigurationAsCodeTest {

    @Test
    void testSimpleConfiguration(JenkinsRule jenkins) throws Exception {
        try (InputStream is = getClass().getResourceAsStream("simple-config.yaml")) {
            ConfigurationAsCode.get().configureWith(
                    ConfigurationAsCode.get().loadConfigurationsFrom(is)
            );
        }

        Jenkins j = jenkins.getInstance();
        assertNotNull(j);
        assertEquals(1, j.clouds.size());

        SwarmCloud cloud = (SwarmCloud) j.clouds.get(0);
        assertEquals("docker-swarm", cloud.name);
        assertEquals("tcp://swarm-manager:2376", cloud.getDockerHost());
        assertEquals("http://jenkins:8080", cloud.getJenkinsUrl());
        assertEquals("jenkins-network", cloud.getSwarmNetwork());
        assertEquals(10, cloud.getMaxConcurrentAgents());

        assertEquals(1, cloud.getTemplates().size());
        SwarmAgentTemplate template = cloud.getTemplates().get(0);
        assertEquals("default", template.getName());
        assertEquals("jenkins/inbound-agent:latest", template.getImage());
        assertEquals("docker swarm", template.getLabelString());
        assertEquals(1, template.getNumExecutors());
        assertEquals(5, template.getMaxInstances());
    }

    @Test
    void testFullConfiguration(JenkinsRule jenkins) throws Exception {
        try (InputStream is = getClass().getResourceAsStream("full-config.yaml")) {
            ConfigurationAsCode.get().configureWith(
                    ConfigurationAsCode.get().loadConfigurationsFrom(is)
            );
        }

        Jenkins j = jenkins.getInstance();
        assertNotNull(j);
        assertEquals(1, j.clouds.size());

        SwarmCloud cloud = (SwarmCloud) j.clouds.get(0);
        assertEquals("production-swarm", cloud.name);
        assertEquals("tcp://swarm-manager.prod.local:2376", cloud.getDockerHost());
        assertEquals("docker-tls-creds", cloud.getCredentialsId());
        assertEquals("https://jenkins.prod.local", cloud.getJenkinsUrl());
        assertEquals("jenkins-agents-network", cloud.getSwarmNetwork());
        assertEquals(50, cloud.getMaxConcurrentAgents());

        assertEquals(3, cloud.getTemplates().size());

        // Test maven template with full configuration
        SwarmAgentTemplate mavenTemplate = cloud.getTemplates().get(0);
        assertEquals("maven-jdk17", mavenTemplate.getName());
        assertEquals("myregistry/jenkins-agent-maven:jdk17", mavenTemplate.getImage());
        assertEquals("maven java17 linux", mavenTemplate.getLabelString());
        assertEquals(2, mavenTemplate.getNumExecutors());
        assertEquals(10, mavenTemplate.getMaxInstances());
        assertEquals(Node.Mode.EXCLUSIVE, mavenTemplate.getMode());
        assertEquals("2.0", mavenTemplate.getCpuLimit());
        assertEquals("4g", mavenTemplate.getMemoryLimit());
        assertEquals("0.5", mavenTemplate.getCpuReservation());
        assertEquals("1g", mavenTemplate.getMemoryReservation());

        // Test mounts
        List<SwarmAgentTemplate.MountConfig> mounts = mavenTemplate.getMounts();
        assertEquals(2, mounts.size());
        assertEquals("bind", mounts.get(0).getType());
        assertEquals("/var/cache/maven", mounts.get(0).getSource());
        assertEquals("/root/.m2/repository", mounts.get(0).getTarget());
        assertFalse(mounts.get(0).isReadOnly());

        assertEquals("volume", mounts.get(1).getType());
        assertEquals("jenkins-workspace", mounts.get(1).getSource());
        assertEquals("/workspace", mounts.get(1).getTarget());

        // Test environment variables
        List<SwarmAgentTemplate.EnvironmentVariable> envVars = mavenTemplate.getEnvironmentVariables();
        assertEquals(2, envVars.size());
        assertEquals("MAVEN_OPTS", envVars.get(0).getName());
        assertEquals("-Xmx2g -XX:+UseG1GC", envVars.get(0).getValue());
        assertEquals("JAVA_HOME", envVars.get(1).getName());
        assertEquals("/opt/java/openjdk", envVars.get(1).getValue());

        // Test placement constraints
        List<String> constraints = mavenTemplate.getPlacementConstraints();
        assertEquals(2, constraints.size());
        assertTrue(constraints.contains("node.role==worker"));
        assertTrue(constraints.contains("node.labels.type==build"));

        // Test network aliases
        List<String> aliases = mavenTemplate.getNetworkAliases();
        assertEquals(2, aliases.size());
        assertTrue(aliases.contains("maven-agent"));
        assertTrue(aliases.contains("build-agent"));

        // Test nodejs template
        SwarmAgentTemplate nodejsTemplate = cloud.getTemplates().get(1);
        assertEquals("nodejs-agent", nodejsTemplate.getName());
        assertEquals("myregistry/jenkins-agent-node:20", nodejsTemplate.getImage());
        assertEquals(4, nodejsTemplate.getNumExecutors());
        assertEquals(15, nodejsTemplate.getMaxInstances());

        // Test docker builder template
        SwarmAgentTemplate dockerTemplate = cloud.getTemplates().get(2);
        assertEquals("docker-builder", dockerTemplate.getName());
        assertEquals(Node.Mode.EXCLUSIVE, dockerTemplate.getMode());
        assertEquals("4.0", dockerTemplate.getCpuLimit());
        assertEquals("8g", dockerTemplate.getMemoryLimit());
    }

    @Test
    void testMultiCloudConfiguration(JenkinsRule jenkins) throws Exception {
        try (InputStream is = getClass().getResourceAsStream("multi-cloud-config.yaml")) {
            ConfigurationAsCode.get().configureWith(
                    ConfigurationAsCode.get().loadConfigurationsFrom(is)
            );
        }

        Jenkins j = jenkins.getInstance();
        assertNotNull(j);
        assertEquals(3, j.clouds.size());

        // Verify all clouds are SwarmCloud instances
        for (var cloud : j.clouds) {
            assertTrue(cloud instanceof SwarmCloud);
        }

        // Test dev cloud
        SwarmCloud devCloud = (SwarmCloud) j.clouds.get(0);
        assertEquals("dev-swarm", devCloud.name);
        assertEquals("tcp://swarm-dev:2376", devCloud.getDockerHost());
        assertEquals(10, devCloud.getMaxConcurrentAgents());

        // Test staging cloud
        SwarmCloud stagingCloud = (SwarmCloud) j.clouds.get(1);
        assertEquals("staging-swarm", stagingCloud.name);
        assertEquals("tcp://swarm-staging:2376", stagingCloud.getDockerHost());
        assertEquals(20, stagingCloud.getMaxConcurrentAgents());

        // Test prod cloud
        SwarmCloud prodCloud = (SwarmCloud) j.clouds.get(2);
        assertEquals("prod-swarm", prodCloud.name);
        assertEquals("tcp://swarm-prod:2376", prodCloud.getDockerHost());
        assertEquals("prod-docker-creds", prodCloud.getCredentialsId());
        assertEquals(100, prodCloud.getMaxConcurrentAgents());
        assertEquals(Node.Mode.EXCLUSIVE, prodCloud.getTemplates().get(0).getMode());
    }

    @Test
    void testConfigurationFromYamlString(JenkinsRule jenkins) throws Exception {
        String yaml = """
            jenkins:
              clouds:
                - swarmAgentsCloud:
                    name: "test-cloud"
                    dockerHost: "tcp://localhost:2376"
                    maxConcurrentAgents: 5
                    templates:
                      - name: "test-agent"
                        image: "jenkins/inbound-agent:alpine"
                        labelString: "test alpine"
                        numExecutors: 1
                        maxInstances: 3
            """;

        ConfigurationAsCode.get().configureWith(
                ConfigurationAsCode.get().loadConfigurationsFrom(
                        new java.io.ByteArrayInputStream(yaml.getBytes())
                )
        );

        Jenkins j = jenkins.getInstance();
        assertNotNull(j);

        assertEquals(1, j.clouds.size());
        SwarmCloud cloud = (SwarmCloud) j.clouds.get(0);
        assertEquals("test-cloud", cloud.name);
        assertEquals(5, cloud.getMaxConcurrentAgents());

        SwarmAgentTemplate template = cloud.getTemplates().get(0);
        assertEquals("test-agent", template.getName());
        assertEquals("jenkins/inbound-agent:alpine", template.getImage());
        assertEquals("test alpine", template.getLabelString());
        assertEquals(3, template.getMaxInstances());
    }

    @Test
    void testExportConfiguration(JenkinsRule jenkins) throws Exception {
        Jenkins j = jenkins.getInstance();
        assertNotNull(j);

        // Create cloud with full configuration
        SwarmCloud cloud = new SwarmCloud("export-test");
        cloud.setDockerHost("tcp://localhost:2376");
        cloud.setJenkinsUrl("http://jenkins:8080");
        cloud.setSwarmNetwork("test-network");
        cloud.setMaxConcurrentAgents(15);

        SwarmAgentTemplate template = new SwarmAgentTemplate("export-template");
        template.setImage("jenkins/inbound-agent:latest");
        template.setLabelString("export test");
        template.setNumExecutors(2);
        template.setMaxInstances(10);
        template.setCpuLimit("1.5");
        template.setMemoryLimit("2g");
        template.setMode(Node.Mode.EXCLUSIVE);

        // Add mount
        SwarmAgentTemplate.MountConfig mount = new SwarmAgentTemplate.MountConfig(
                "bind", "/host/path", "/container/path");
        mount.setReadOnly(true);
        template.setMounts(List.of(mount));

        // Add environment variable
        SwarmAgentTemplate.EnvironmentVariable envVar =
                new SwarmAgentTemplate.EnvironmentVariable("TEST_VAR", "test_value");
        template.setEnvironmentVariables(List.of(envVar));

        cloud.setTemplates(List.of(template));
        j.clouds.add(cloud);

        // Verify configuration can be exported
        ConfiguratorRegistry registry = ConfiguratorRegistry.get();
        ConfigurationContext context = new ConfigurationContext(registry);
        assertNotNull(context);

        // Export and verify
        StringWriter sw = new StringWriter();
        ConfigurationAsCode.get().export(sw);
        String exported = sw.toString();

        assertTrue(exported.contains("swarmAgentsCloud"));
        assertTrue(exported.contains("export-test"));
        assertTrue(exported.contains("tcp://localhost:2376"));
        assertTrue(exported.contains("export-template"));
    }

    @Test
    void testMinimalConfiguration(JenkinsRule jenkins) throws Exception {
        String yaml = """
            jenkins:
              clouds:
                - swarmAgentsCloud:
                    name: "minimal"
                    dockerHost: "tcp://docker:2376"
                    templates:
                      - name: "minimal-agent"
            """;

        ConfigurationAsCode.get().configureWith(
                ConfigurationAsCode.get().loadConfigurationsFrom(
                        new java.io.ByteArrayInputStream(yaml.getBytes())
                )
        );

        Jenkins j = jenkins.getInstance();
        assertNotNull(j);

        SwarmCloud cloud = (SwarmCloud) j.clouds.get(0);
        assertEquals("minimal", cloud.name);
        assertEquals("tcp://docker:2376", cloud.getDockerHost());
        assertEquals(10, cloud.getMaxConcurrentAgents()); // default value

        SwarmAgentTemplate template = cloud.getTemplates().get(0);
        assertEquals("minimal-agent", template.getName());
        assertEquals("jenkins/inbound-agent:latest", template.getImage()); // default
        assertEquals(1, template.getNumExecutors()); // default
        assertEquals(5, template.getMaxInstances()); // default
        assertEquals(Node.Mode.NORMAL, template.getMode()); // default
    }

    @Test
    void testTemplateParentReference(JenkinsRule jenkins) throws Exception {
        String yaml = """
            jenkins:
              clouds:
                - swarmAgentsCloud:
                    name: "parent-test"
                    dockerHost: "tcp://docker:2376"
                    templates:
                      - name: "child-template"
                        image: "test:latest"
            """;

        ConfigurationAsCode.get().configureWith(
                ConfigurationAsCode.get().loadConfigurationsFrom(
                        new java.io.ByteArrayInputStream(yaml.getBytes())
                )
        );

        Jenkins j = jenkins.getInstance();
        assertNotNull(j);

        SwarmCloud cloud = (SwarmCloud) j.clouds.get(0);
        SwarmAgentTemplate template = cloud.getTemplates().get(0);

        // Verify parent reference is set
        assertNotNull(template.getParent());
        assertEquals(cloud, template.getParent());
        assertEquals("parent-test", template.getParent().name);
    }
}

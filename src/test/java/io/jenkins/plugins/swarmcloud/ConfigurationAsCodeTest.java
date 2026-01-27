package io.jenkins.plugins.swarmcloud;

import hudson.model.Node;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.misc.junit.jupiter.WithJenkinsConfiguredWithCode;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Configuration as Code support.
 */
@WithJenkinsConfiguredWithCode
class ConfigurationAsCodeTest {

    @Test
    @ConfiguredWithCode("simple-config.yaml")
    void testSimpleConfiguration(JenkinsConfiguredWithCodeRule j) {
        Jenkins jenkins = j.jenkins;
        assertNotNull(jenkins);
        assertEquals(1, jenkins.clouds.size());

        SwarmCloud cloud = (SwarmCloud) jenkins.clouds.get(0);
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
    @ConfiguredWithCode("full-config.yaml")
    void testFullConfiguration(JenkinsConfiguredWithCodeRule j) {
        Jenkins jenkins = j.jenkins;
        assertNotNull(jenkins);
        assertEquals(1, jenkins.clouds.size());

        SwarmCloud cloud = (SwarmCloud) jenkins.clouds.get(0);
        assertEquals("production-swarm", cloud.name);
        assertEquals("tcp://swarm-manager.prod.local:2376", cloud.getDockerHost());
        assertEquals("docker-tls-creds", cloud.getCredentialsId());
        assertEquals("https://jenkins.prod.local", cloud.getJenkinsUrl());
        assertEquals("jenkins-agents-network", cloud.getSwarmNetwork());
        assertEquals(50, cloud.getMaxConcurrentAgents());

        assertEquals(3, cloud.getTemplates().size());

        // Test maven template with full configuration
        SwarmAgentTemplate mavenTemplate = cloud.getTemplates().get(0);
        assertEquals("maven-jdk21", mavenTemplate.getName());
        assertEquals("myregistry/jenkins-agent-maven:jdk21", mavenTemplate.getImage());
        assertEquals("maven java21 linux", mavenTemplate.getLabelString());
        assertEquals(2, mavenTemplate.getNumExecutors());
        assertEquals(10, mavenTemplate.getMaxInstances());
        assertEquals(Node.Mode.EXCLUSIVE, mavenTemplate.getMode());
        assertEquals("2.0", mavenTemplate.getCpuLimit());
        assertEquals("4g", mavenTemplate.getMemoryLimit());
        assertEquals("0.5", mavenTemplate.getCpuReservation());
        assertEquals("1g", mavenTemplate.getMemoryReservation());

        // Test mounts (via hostBinds alias)
        List<SwarmAgentTemplate.MountConfig> mounts = mavenTemplate.getMounts();
        assertEquals(2, mounts.size());
        assertEquals(SwarmAgentTemplate.SwarmMountType.BIND, mounts.get(0).getType());
        assertEquals("/var/cache/maven", mounts.get(0).getSource());
        assertEquals("/root/.m2/repository", mounts.get(0).getTarget());
        assertFalse(mounts.get(0).isReadOnly());

        assertEquals(SwarmAgentTemplate.SwarmMountType.VOLUME, mounts.get(1).getType());
        assertEquals("jenkins-workspace", mounts.get(1).getSource());
        assertEquals("/workspace", mounts.get(1).getTarget());

        // Test environment variables (via envVars alias)
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

        // Test extra hosts
        List<String> extraHosts = mavenTemplate.getExtraHosts();
        assertEquals(2, extraHosts.size());
        assertTrue(extraHosts.contains("internal-registry:192.168.1.100"));
        assertTrue(extraHosts.contains("database.local:10.0.0.50"));

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
    @ConfiguredWithCode("multi-cloud-config.yaml")
    void testMultiCloudConfiguration(JenkinsConfiguredWithCodeRule j) {
        Jenkins jenkins = j.jenkins;
        assertNotNull(jenkins);
        assertEquals(3, jenkins.clouds.size());

        // Verify all clouds are SwarmCloud instances
        for (var cloud : jenkins.clouds) {
            assertInstanceOf(SwarmCloud.class, cloud);
        }

        // Test dev cloud
        SwarmCloud devCloud = (SwarmCloud) jenkins.clouds.get(0);
        assertEquals("dev-swarm", devCloud.name);
        assertEquals("tcp://swarm-dev:2376", devCloud.getDockerHost());
        assertEquals(10, devCloud.getMaxConcurrentAgents());

        // Test staging cloud
        SwarmCloud stagingCloud = (SwarmCloud) jenkins.clouds.get(1);
        assertEquals("staging-swarm", stagingCloud.name);
        assertEquals("tcp://swarm-staging:2376", stagingCloud.getDockerHost());
        assertEquals(20, stagingCloud.getMaxConcurrentAgents());

        // Test prod cloud
        SwarmCloud prodCloud = (SwarmCloud) jenkins.clouds.get(2);
        assertEquals("prod-swarm", prodCloud.name);
        assertEquals("tcp://swarm-prod:2376", prodCloud.getDockerHost());
        assertEquals("prod-docker-creds", prodCloud.getCredentialsId());
        assertEquals(100, prodCloud.getMaxConcurrentAgents());
        assertEquals(Node.Mode.EXCLUSIVE, prodCloud.getTemplates().get(0).getMode());
    }

    @Test
    void testResourceFieldAliases(JenkinsConfiguredWithCodeRule j) {
        // Test NanoCPUs and MemoryBytes aliases for docker-swarm-plugin compatibility
        SwarmAgentTemplate template = new SwarmAgentTemplate("resource-test");

        // Test CPU limit/reservation (nanoCPUs = CPUs * 1e9)
        template.setCpuLimit("2.0");
        assertEquals(Long.valueOf(2_000_000_000L), template.getLimitsNanoCPUs());

        template.setLimitsNanoCPUs(1_500_000_000L);
        assertEquals("1.5", template.getCpuLimit());

        template.setCpuReservation("0.5");
        assertEquals(Long.valueOf(500_000_000L), template.getReservationsNanoCPUs());

        // Test memory limit/reservation (bytes)
        template.setMemoryLimit("4g");
        assertEquals(Long.valueOf(4L * 1024 * 1024 * 1024), template.getLimitsMemoryBytes());

        template.setLimitsMemoryBytes(2L * 1024 * 1024 * 1024);
        assertEquals("2g", template.getMemoryLimit());

        template.setMemoryReservation("512m");
        assertEquals(Long.valueOf(512L * 1024 * 1024), template.getReservationsMemoryBytes());

        template.setReservationsMemoryBytes(1024L * 1024 * 1024);
        assertEquals("1g", template.getMemoryReservation());
    }

    @Test
    void testFieldAliases(JenkinsConfiguredWithCodeRule j) {
        SwarmAgentTemplate template = new SwarmAgentTemplate("alias-test");

        // Test label/labelString alias
        template.setLabel("test-label");
        assertEquals("test-label", template.getLabelString());
        assertEquals("test-label", template.getLabel());

        template.setLabelString("another-label");
        assertEquals("another-label", template.getLabel());

        // Test workingDir/remoteFs alias
        template.setWorkingDir("/custom/workspace");
        assertEquals("/custom/workspace", template.getRemoteFs());
        assertEquals("/custom/workspace", template.getWorkingDir());

        template.setRemoteFs("/another/path");
        assertEquals("/another/path", template.getWorkingDir());

        // Test portBinds/portBindingsString alias
        template.setPortBinds("80:8080\n:5900");
        assertEquals(2, template.getPortBindings().size());
        assertEquals("80:8080\n:5900", template.getPortBinds());

        // Test dnsIps/dnsServersString alias
        template.setDnsIps("8.8.8.8, 1.1.1.1");
        assertEquals(2, template.getDnsServers().size());
        assertTrue(template.getDnsServers().contains("8.8.8.8"));
        assertTrue(template.getDnsServers().contains("1.1.1.1"));
    }
}

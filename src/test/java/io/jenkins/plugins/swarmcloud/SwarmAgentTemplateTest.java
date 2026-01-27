package io.jenkins.plugins.swarmcloud;

import hudson.model.Label;
import hudson.model.Node;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import hudson.util.FormValidation;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SwarmAgentTemplate.
 */
@WithJenkins
class SwarmAgentTemplateTest {

    private JenkinsRule jenkins;
    private SwarmAgentTemplate template;

    @BeforeEach
    void setUp(JenkinsRule jenkins) {
        this.jenkins = jenkins;
        template = new SwarmAgentTemplate("test-template");
    }

    @Test
    void testTemplateCreation() {
        assertEquals("test-template", template.getName());
        assertEquals("jenkins/inbound-agent:latest", template.getImage());
        assertEquals("/home/jenkins/agent", template.getRemoteFs());
        assertEquals(1, template.getNumExecutors());
        assertEquals(5, template.getMaxInstances());
        assertEquals(Node.Mode.NORMAL, template.getMode());
    }

    @Test
    void testSetImage() {
        template.setImage("custom/agent:v1");
        assertEquals("custom/agent:v1", template.getImage());

        template.setImage(null);
        assertEquals("jenkins/inbound-agent:latest", template.getImage());

        template.setImage("  ");
        assertEquals("jenkins/inbound-agent:latest", template.getImage());
    }

    @Test
    void testSetLabelString() {
        template.setLabelString("maven java build");

        Set<hudson.model.labels.LabelAtom> labels = template.getLabelSet();
        assertEquals(3, labels.size());
        assertTrue(labels.stream().anyMatch(l -> l.getName().equals("maven")));
        assertTrue(labels.stream().anyMatch(l -> l.getName().equals("java")));
        assertTrue(labels.stream().anyMatch(l -> l.getName().equals("build")));
    }

    @Test
    void testMatchesLabel() {
        template.setLabelString("maven java");
        template.setMode(Node.Mode.NORMAL);

        assertTrue(template.matches(Label.parseExpression("maven")));
        assertTrue(template.matches(Label.parseExpression("java")));
        assertTrue(template.matches(Label.parseExpression("maven && java")));
        assertFalse(template.matches(Label.parseExpression("python")));
    }

    @Test
    void testMatchesNullLabel() {
        template.setMode(Node.Mode.NORMAL);
        assertTrue(template.matches(null));

        template.setMode(Node.Mode.EXCLUSIVE);
        assertFalse(template.matches(null));
    }

    @Test
    void testResourceConstraints() {
        template.setCpuLimit("2.0");
        template.setMemoryLimit("4g");
        template.setCpuReservation("1.0");
        template.setMemoryReservation("2g");

        assertEquals("2.0", template.getCpuLimit());
        assertEquals("4g", template.getMemoryLimit());
        assertEquals("1.0", template.getCpuReservation());
        assertEquals("2g", template.getMemoryReservation());
    }

    @Test
    void testMounts() {
        SwarmAgentTemplate.MountConfig mount1 = new SwarmAgentTemplate.MountConfig(
                SwarmAgentTemplate.SwarmMountType.VOLUME, "maven-cache", "/root/.m2");
        SwarmAgentTemplate.MountConfig mount2 = new SwarmAgentTemplate.MountConfig(
                SwarmAgentTemplate.SwarmMountType.BIND, "/host/path", "/container/path");
        mount2.setReadOnly(true);

        template.setMounts(List.of(mount1, mount2));

        assertEquals(2, template.getMounts().size());
        assertEquals(SwarmAgentTemplate.SwarmMountType.VOLUME, template.getMounts().get(0).getType());
        assertEquals("maven-cache", template.getMounts().get(0).getSource());
        assertEquals("/root/.m2", template.getMounts().get(0).getTarget());
        assertFalse(template.getMounts().get(0).isReadOnly());

        assertEquals(SwarmAgentTemplate.SwarmMountType.BIND, template.getMounts().get(1).getType());
        assertTrue(template.getMounts().get(1).isReadOnly());
    }

    @Test
    void testEnvironmentVariables() {
        SwarmAgentTemplate.EnvironmentVariable var1 =
                new SwarmAgentTemplate.EnvironmentVariable("JAVA_OPTS", "-Xmx512m");
        SwarmAgentTemplate.EnvironmentVariable var2 =
                new SwarmAgentTemplate.EnvironmentVariable("DEBUG", "true");

        template.setEnvironmentVariables(List.of(var1, var2));

        assertEquals(2, template.getEnvironmentVariables().size());
        assertEquals("JAVA_OPTS", template.getEnvironmentVariables().get(0).getName());
        assertEquals("-Xmx512m", template.getEnvironmentVariables().get(0).getValue());
    }

    @Test
    void testGenerateAgentName() {
        String name1 = template.generateAgentName();
        String name2 = template.generateAgentName();

        assertNotNull(name1);
        assertNotNull(name2);
        assertNotEquals(name1, name2);
        assertTrue(name1.startsWith("swarm-test-template-"));
        assertTrue(name2.startsWith("swarm-test-template-"));
    }

    @Test
    void testInstanceCounting() {
        assertEquals(0, template.getCurrentInstances());
        assertEquals(5, template.getAvailableCapacity());

        template.incrementInstances();
        assertEquals(1, template.getCurrentInstances());
        assertEquals(4, template.getAvailableCapacity());

        template.incrementInstances();
        template.incrementInstances();
        assertEquals(3, template.getCurrentInstances());
        assertEquals(2, template.getAvailableCapacity());

        template.decrementInstances();
        assertEquals(2, template.getCurrentInstances());
        assertEquals(3, template.getAvailableCapacity());
    }

    @Test
    void testNumExecutorsValidation() {
        template.setNumExecutors(-1);
        assertEquals(1, template.getNumExecutors());

        template.setNumExecutors(0);
        assertEquals(1, template.getNumExecutors());

        template.setNumExecutors(4);
        assertEquals(4, template.getNumExecutors());
    }

    @Test
    void testMaxInstancesValidation() {
        template.setMaxInstances(-1);
        assertEquals(5, template.getMaxInstances());

        template.setMaxInstances(0);
        assertEquals(5, template.getMaxInstances());

        template.setMaxInstances(20);
        assertEquals(20, template.getMaxInstances());
    }

    @Test
    void testPlacementConstraints() {
        template.setPlacementConstraints(List.of(
                "node.role == worker",
                "node.labels.type == compute"
        ));

        assertEquals(2, template.getPlacementConstraints().size());
        assertEquals("node.role == worker", template.getPlacementConstraints().get(0));
    }

    @Test
    void testNetworkAliases() {
        template.setNetworkAliases(List.of("agent", "jenkins-agent"));

        assertEquals(2, template.getNetworkAliases().size());
        assertTrue(template.getNetworkAliases().contains("agent"));
    }

    @Test
    void testExtraHosts() {
        template.setExtraHosts(List.of(
                "myhost:192.168.1.1",
                "database:10.0.0.5"
        ));

        assertEquals(2, template.getExtraHosts().size());
        assertEquals("myhost:192.168.1.1", template.getExtraHosts().get(0));
        assertEquals("database:10.0.0.5", template.getExtraHosts().get(1));
    }

    @Test
    void testExtraHostsString() {
        // Test setter
        template.setExtraHostsString("myhost:192.168.1.1\ndatabase:10.0.0.5");

        assertEquals(2, template.getExtraHosts().size());
        assertEquals("myhost:192.168.1.1", template.getExtraHosts().get(0));

        // Test getter
        String result = template.getExtraHostsString();
        assertNotNull(result);
        assertTrue(result.contains("myhost:192.168.1.1"));
        assertTrue(result.contains("database:10.0.0.5"));
    }

    @Test
    void testExtraHostsFiltersInvalid() {
        // Invalid entries (no colon) should be filtered out
        template.setExtraHostsString("invalid-entry\nvalid:1.2.3.4\n");

        assertEquals(1, template.getExtraHosts().size());
        assertEquals("valid:1.2.3.4", template.getExtraHosts().get(0));
    }

    @Test
    void testExtraHostsEmpty() {
        template.setExtraHostsString(null);
        assertTrue(template.getExtraHosts().isEmpty());

        template.setExtraHostsString("   ");
        assertTrue(template.getExtraHosts().isEmpty());
    }

    @Test
    void testExtraHostsImmutable() {
        template.setExtraHosts(List.of("host:1.2.3.4"));

        // Verify returned list is immutable
        List<String> hosts = template.getExtraHosts();
        assertThrows(UnsupportedOperationException.class, () -> hosts.add("another:5.6.7.8"));
    }

    @Test
    void testExtraHostsWhitespace() {
        // Test trimming of whitespace
        template.setExtraHostsString("  myhost:192.168.1.1  \n  database:10.0.0.5  \n");

        assertEquals(2, template.getExtraHosts().size());
        assertEquals("myhost:192.168.1.1", template.getExtraHosts().get(0));
        assertEquals("database:10.0.0.5", template.getExtraHosts().get(1));
    }

    // ========================
    // doCheckExtraHostsString validation tests
    // ========================

    @Test
    void testDoCheckExtraHostsStringEmpty() {
        SwarmAgentTemplate.DescriptorImpl descriptor =
                jenkins.jenkins.getDescriptorByType(SwarmAgentTemplate.DescriptorImpl.class);

        assertEquals(FormValidation.Kind.OK, descriptor.doCheckExtraHostsString(null).kind);
        assertEquals(FormValidation.Kind.OK, descriptor.doCheckExtraHostsString("").kind);
        assertEquals(FormValidation.Kind.OK, descriptor.doCheckExtraHostsString("   ").kind);
    }

    @Test
    void testDoCheckExtraHostsStringValidIPv4() {
        SwarmAgentTemplate.DescriptorImpl descriptor =
                jenkins.jenkins.getDescriptorByType(SwarmAgentTemplate.DescriptorImpl.class);

        assertEquals(FormValidation.Kind.OK,
                descriptor.doCheckExtraHostsString("myhost:192.168.1.1").kind);
        assertEquals(FormValidation.Kind.OK,
                descriptor.doCheckExtraHostsString("db:10.0.0.5").kind);
        assertEquals(FormValidation.Kind.OK,
                descriptor.doCheckExtraHostsString("localhost:127.0.0.1").kind);
        assertEquals(FormValidation.Kind.OK,
                descriptor.doCheckExtraHostsString("host1:192.168.1.1\nhost2:10.0.0.2").kind);
    }

    @Test
    void testDoCheckExtraHostsStringValidIPv6() {
        SwarmAgentTemplate.DescriptorImpl descriptor =
                jenkins.jenkins.getDescriptorByType(SwarmAgentTemplate.DescriptorImpl.class);

        assertEquals(FormValidation.Kind.OK,
                descriptor.doCheckExtraHostsString("myhost:2001:db8::1").kind);
        assertEquals(FormValidation.Kind.OK,
                descriptor.doCheckExtraHostsString("ipv6host:fe80::1").kind);
    }

    @Test
    void testDoCheckExtraHostsStringInvalidFormat() {
        SwarmAgentTemplate.DescriptorImpl descriptor =
                jenkins.jenkins.getDescriptorByType(SwarmAgentTemplate.DescriptorImpl.class);

        // Missing colon
        assertEquals(FormValidation.Kind.ERROR,
                descriptor.doCheckExtraHostsString("invalidentry").kind);
        // Colon at start
        assertEquals(FormValidation.Kind.ERROR,
                descriptor.doCheckExtraHostsString(":192.168.1.1").kind);
        // Colon at end
        assertEquals(FormValidation.Kind.ERROR,
                descriptor.doCheckExtraHostsString("hostname:").kind);
    }

    @Test
    void testDoCheckExtraHostsStringInvalidHostname() {
        SwarmAgentTemplate.DescriptorImpl descriptor =
                jenkins.jenkins.getDescriptorByType(SwarmAgentTemplate.DescriptorImpl.class);

        // Hostname starting with hyphen
        assertEquals(FormValidation.Kind.ERROR,
                descriptor.doCheckExtraHostsString("-invalid:192.168.1.1").kind);
        // Hostname starting with dot
        assertEquals(FormValidation.Kind.ERROR,
                descriptor.doCheckExtraHostsString(".invalid:192.168.1.1").kind);
        // Hostname with special characters
        assertEquals(FormValidation.Kind.ERROR,
                descriptor.doCheckExtraHostsString("host_name:192.168.1.1").kind);
    }

    @Test
    void testDoCheckExtraHostsStringInvalidIP() {
        SwarmAgentTemplate.DescriptorImpl descriptor =
                jenkins.jenkins.getDescriptorByType(SwarmAgentTemplate.DescriptorImpl.class);

        // Invalid IPv4 - octet > 255
        assertEquals(FormValidation.Kind.ERROR,
                descriptor.doCheckExtraHostsString("myhost:192.168.1.256").kind);
        // Invalid IP format
        assertEquals(FormValidation.Kind.ERROR,
                descriptor.doCheckExtraHostsString("myhost:not-an-ip").kind);
        // Invalid IP - only dots
        assertEquals(FormValidation.Kind.ERROR,
                descriptor.doCheckExtraHostsString("myhost:...").kind);
    }

    @Test
    void testDoCheckExtraHostsStringMultipleWithInvalid() {
        SwarmAgentTemplate.DescriptorImpl descriptor =
                jenkins.jenkins.getDescriptorByType(SwarmAgentTemplate.DescriptorImpl.class);

        // First entry valid, second invalid
        assertEquals(FormValidation.Kind.ERROR,
                descriptor.doCheckExtraHostsString("valid:192.168.1.1\ninvalid").kind);
    }

    // ========================
    // registryCredentialsId tests
    // ========================

    @Test
    void testRegistryCredentialsId() {
        template.setRegistryCredentialsId("my-docker-creds");
        assertEquals("my-docker-creds", template.getRegistryCredentialsId());

        template.setRegistryCredentialsId(null);
        assertNull(template.getRegistryCredentialsId());

        template.setRegistryCredentialsId("   ");
        assertNull(template.getRegistryCredentialsId());
    }
}

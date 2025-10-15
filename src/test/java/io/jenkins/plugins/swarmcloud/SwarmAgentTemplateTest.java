package io.jenkins.plugins.swarmcloud;

import hudson.model.Label;
import hudson.model.Node;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SwarmAgentTemplate.
 */
class SwarmAgentTemplateTest {

    private SwarmAgentTemplate template;

    @BeforeEach
    void setUp() {
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
                "volume", "maven-cache", "/root/.m2");
        SwarmAgentTemplate.MountConfig mount2 = new SwarmAgentTemplate.MountConfig(
                "bind", "/host/path", "/container/path");
        mount2.setReadOnly(true);

        template.setMounts(List.of(mount1, mount2));

        assertEquals(2, template.getMounts().size());
        assertEquals("volume", template.getMounts().get(0).getType());
        assertEquals("maven-cache", template.getMounts().get(0).getSource());
        assertEquals("/root/.m2", template.getMounts().get(0).getTarget());
        assertFalse(template.getMounts().get(0).isReadOnly());

        assertEquals("bind", template.getMounts().get(1).getType());
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
}

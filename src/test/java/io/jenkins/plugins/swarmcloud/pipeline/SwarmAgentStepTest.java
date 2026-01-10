package io.jenkins.plugins.swarmcloud.pipeline;

import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SwarmAgentStep.
 */
@WithJenkins
class SwarmAgentStepTest {

    private JenkinsRule jenkins;

    @BeforeEach
    void setUp(JenkinsRule jenkins) {
        this.jenkins = jenkins;
    }

    @Test
    void testStepCreation() {
        SwarmAgentStep step = new SwarmAgentStep("test-cloud");

        assertEquals("test-cloud", step.getCloud());
        assertNull(step.getTemplate());
        assertNull(step.getImage());
        assertNull(step.getLabel());
        assertEquals(1, step.getNumExecutors());
        assertEquals(60, step.getIdleTimeout());
        assertEquals(300, step.getConnectionTimeout());
    }

    @Test
    void testSetTemplate() {
        SwarmAgentStep step = new SwarmAgentStep("test-cloud");
        step.setTemplate("maven-template");

        assertEquals("maven-template", step.getTemplate());
    }

    @Test
    void testSetImage() {
        SwarmAgentStep step = new SwarmAgentStep("test-cloud");
        step.setImage("jenkins/inbound-agent:alpine");

        assertEquals("jenkins/inbound-agent:alpine", step.getImage());
    }

    @Test
    void testSetLabel() {
        SwarmAgentStep step = new SwarmAgentStep("test-cloud");
        step.setLabel("maven java");

        assertEquals("maven java", step.getLabel());
    }

    @Test
    void testSetNumExecutors() {
        SwarmAgentStep step = new SwarmAgentStep("test-cloud");

        step.setNumExecutors(4);
        assertEquals(4, step.getNumExecutors());

        // Invalid values should default to 1
        step.setNumExecutors(0);
        assertEquals(1, step.getNumExecutors());

        step.setNumExecutors(-5);
        assertEquals(1, step.getNumExecutors());
    }

    @Test
    void testSetCpuLimit() {
        SwarmAgentStep step = new SwarmAgentStep("test-cloud");
        step.setCpuLimit("2.0");

        assertEquals("2.0", step.getCpuLimit());
    }

    @Test
    void testSetMemoryLimit() {
        SwarmAgentStep step = new SwarmAgentStep("test-cloud");
        step.setMemoryLimit("4g");

        assertEquals("4g", step.getMemoryLimit());
    }

    @Test
    void testSetIdleTimeout() {
        SwarmAgentStep step = new SwarmAgentStep("test-cloud");

        step.setIdleTimeout(120);
        assertEquals(120, step.getIdleTimeout());

        // Invalid values should default to 60
        step.setIdleTimeout(0);
        assertEquals(60, step.getIdleTimeout());

        step.setIdleTimeout(-10);
        assertEquals(60, step.getIdleTimeout());
    }

    @Test
    void testSetConnectionTimeout() {
        SwarmAgentStep step = new SwarmAgentStep("test-cloud");

        step.setConnectionTimeout(600);
        assertEquals(600, step.getConnectionTimeout());

        // Invalid values should default to 300
        step.setConnectionTimeout(0);
        assertEquals(300, step.getConnectionTimeout());

        step.setConnectionTimeout(-100);
        assertEquals(300, step.getConnectionTimeout());
    }

    @Test
    void testDescriptorFunctionName() {
        SwarmAgentStep.DescriptorImpl descriptor = new SwarmAgentStep.DescriptorImpl();
        assertEquals("swarmAgent", descriptor.getFunctionName());
    }

    @Test
    void testDescriptorDisplayName() {
        SwarmAgentStep.DescriptorImpl descriptor = new SwarmAgentStep.DescriptorImpl();
        assertEquals("Provision Docker Swarm Agent", descriptor.getDisplayName());
    }

    @Test
    void testDescriptorTakesImplicitBlockArgument() {
        SwarmAgentStep.DescriptorImpl descriptor = new SwarmAgentStep.DescriptorImpl();
        assertTrue(descriptor.takesImplicitBlockArgument());
    }

    @Test
    void testDescriptorRequiredContext() {
        SwarmAgentStep.DescriptorImpl descriptor = new SwarmAgentStep.DescriptorImpl();
        var context = descriptor.getRequiredContext();

        assertNotNull(context);
        assertEquals(2, context.size());
        assertTrue(context.contains(hudson.model.TaskListener.class));
        assertTrue(context.contains(org.jenkinsci.plugins.workflow.graph.FlowNode.class));
    }

    @Test
    void testStepDescriptorRegistered() {
        // The descriptor should be registered as StepDescriptor extension
        var descriptors = jenkins.jenkins.getExtensionList(StepDescriptor.class);

        boolean found = false;
        for (StepDescriptor descriptor : descriptors) {
            if ("swarmAgent".equals(descriptor.getFunctionName())) {
                found = true;
                break;
            }
        }

        assertTrue(found, "swarmAgent step should be registered");
    }

    @Test
    void testSerialVersionUID() {
        // SwarmAgentStep implements Serializable
        SwarmAgentStep step = new SwarmAgentStep("test-cloud");
        assertTrue(step instanceof java.io.Serializable);
    }

    @Test
    void testMultipleStepsIndependent() {
        SwarmAgentStep step1 = new SwarmAgentStep("cloud-1");
        step1.setTemplate("template-1");
        step1.setNumExecutors(2);

        SwarmAgentStep step2 = new SwarmAgentStep("cloud-2");
        step2.setTemplate("template-2");
        step2.setNumExecutors(4);

        // Verify they are independent
        assertEquals("cloud-1", step1.getCloud());
        assertEquals("template-1", step1.getTemplate());
        assertEquals(2, step1.getNumExecutors());

        assertEquals("cloud-2", step2.getCloud());
        assertEquals("template-2", step2.getTemplate());
        assertEquals(4, step2.getNumExecutors());
    }

    @Test
    void testInlineTemplateConfiguration() {
        SwarmAgentStep step = new SwarmAgentStep("test-cloud");
        step.setImage("custom/agent:latest");
        step.setLabel("build linux");
        step.setCpuLimit("1.0");
        step.setMemoryLimit("2g");
        step.setNumExecutors(2);

        // All inline configurations should be set
        assertEquals("custom/agent:latest", step.getImage());
        assertEquals("build linux", step.getLabel());
        assertEquals("1.0", step.getCpuLimit());
        assertEquals("2g", step.getMemoryLimit());
        assertEquals(2, step.getNumExecutors());

        // Template should be null when using inline config
        assertNull(step.getTemplate());
    }
}

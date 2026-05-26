package io.jenkins.plugins.swarmcloud.monitoring;

import com.github.dockerjava.api.model.Service;
import com.github.dockerjava.api.model.ServiceSpec;
import io.jenkins.plugins.swarmcloud.ServiceLabels;
import io.jenkins.plugins.swarmcloud.SwarmAgentTemplate;
import io.jenkins.plugins.swarmcloud.SwarmCloud;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithJenkins
class ClusterMonitorTest {

    private ClusterMonitor monitor;
    private SwarmCloud cloud;

    @BeforeEach
    void setUp(JenkinsRule jenkins) {
        monitor = jenkins.jenkins.getExtensionList(ClusterMonitor.class).get(0);
        assertNotNull(monitor, "ClusterMonitor extension must be registered");
        cloud = new SwarmCloud("test-cloud");
    }

    @Test
    void testIsOneShotServiceTrueFromLabel() {
        Service service = serviceWithLabels(Map.of(
                ServiceLabels.ONE_SHOT, "true",
                ServiceLabels.TEMPLATE, "missing-template"
        ));

        assertTrue(monitor.isOneShotService(cloud, service));
    }

    @Test
    void testIsOneShotServiceFalseFromLabel() {
        Service service = serviceWithLabels(Map.of(
                ServiceLabels.ONE_SHOT, "false"
        ));

        assertFalse(monitor.isOneShotService(cloud, service));
    }

    @Test
    void testIsOneShotServiceFallbackToTemplate() {
        SwarmAgentTemplate template = new SwarmAgentTemplate("one-shot-tmpl");
        template.setImage("jenkins/inbound-agent:latest");
        template.setOneShot(true);
        cloud.setTemplates(List.of(template));

        // Legacy service from a pre-PR-13 version: no ONE_SHOT label.
        Service service = serviceWithLabels(Map.of(
                ServiceLabels.TEMPLATE, "one-shot-tmpl"
        ));

        assertTrue(monitor.isOneShotService(cloud, service),
                "should fall back to template.isOneShot() when label is missing");
    }

    @Test
    void testIsOneShotServiceFallbackUnknownTemplate() {
        Service service = serviceWithLabels(Map.of(
                ServiceLabels.TEMPLATE, "deleted-template"
        ));

        assertFalse(monitor.isOneShotService(cloud, service),
                "should return false when template no longer exists");
    }

    @Test
    void testIsOneShotServiceNoLabelsOrTemplate() {
        Service service = new Service().withSpec(new ServiceSpec());

        assertFalse(monitor.isOneShotService(cloud, service));
    }

    private static Service serviceWithLabels(Map<String, String> labels) {
        return new Service().withSpec(new ServiceSpec().withLabels(labels));
    }
}

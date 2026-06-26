package io.jenkins.plugins.swarmcloud.monitoring;

import com.github.dockerjava.api.model.Service;
import com.github.dockerjava.api.model.ServiceSpec;
import io.jenkins.plugins.swarmcloud.ServiceLabels;
import io.jenkins.plugins.swarmcloud.SwarmAgent;
import io.jenkins.plugins.swarmcloud.SwarmAgentTemplate;
import io.jenkins.plugins.swarmcloud.SwarmCloud;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithJenkins
class ClusterMonitorTest {

    private ClusterMonitor monitor;
    private SwarmCloud cloud;
    private JenkinsRule jenkins;

    @BeforeEach
    void setUp(JenkinsRule jenkins) {
        this.jenkins = jenkins;
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

    // --- removal guard: never delete a one-shot service out from under a still-registered node ---

    @Test
    void doesNotRemoveCompletedOneShotWhileAgentNodeRegistered() throws Exception {
        // A -noReconnect one-shot agent can report a transiently "complete" Docker task while its
        // build is still in progress (channel briefly dropped, executor waiting to reconnect). As
        // long as the Jenkins node still exists, its retention strategy owns teardown; removing the
        // service here would kill the build with "Connection was broken".
        SwarmAgentTemplate template = new SwarmAgentTemplate("t");
        template.setImage("jenkins/inbound-agent:latest");
        template.setOneShot(true);
        cloud.setTemplates(List.of(template));

        SwarmAgent agent = new SwarmAgent("busy-agent", template, "test-cloud", "svc-1");
        jenkins.jenkins.addNode(agent);

        Service service = serviceWithLabels(Map.of(
                ServiceLabels.ONE_SHOT, "true",
                ServiceLabels.AGENT_NAME, "busy-agent"
        ));

        assertFalse(monitor.shouldRemoveCompletedOneShot(cloud, service, "complete"),
                "must not remove a completed one-shot service while its Jenkins node still exists");
    }

    @Test
    void removesCompletedOneShotWhenNodeGone() {
        // No matching node -> a real orphan whose build is long gone; safe to reap.
        SwarmAgentTemplate template = new SwarmAgentTemplate("t");
        template.setOneShot(true);
        cloud.setTemplates(List.of(template));

        Service service = serviceWithLabels(Map.of(
                ServiceLabels.ONE_SHOT, "true",
                ServiceLabels.AGENT_NAME, "ghost-agent"
        ));

        assertTrue(monitor.shouldRemoveCompletedOneShot(cloud, service, "complete"),
                "a completed one-shot service whose node is gone must be removed");
    }

    @Test
    void doesNotRemoveOneShotServiceThatIsNotComplete() {
        SwarmAgentTemplate template = new SwarmAgentTemplate("t");
        template.setOneShot(true);
        cloud.setTemplates(List.of(template));

        Service service = serviceWithLabels(Map.of(
                ServiceLabels.ONE_SHOT, "true",
                ServiceLabels.AGENT_NAME, "ghost-agent"
        ));

        assertFalse(monitor.shouldRemoveCompletedOneShot(cloud, service, "running"),
                "only services in the 'complete' state are removal candidates");
    }

    @Test
    void doesNotRemoveNonOneShotCompletedService() {
        Service service = serviceWithLabels(Map.of(
                ServiceLabels.ONE_SHOT, "false",
                ServiceLabels.AGENT_NAME, "ghost-agent"
        ));

        assertFalse(monitor.shouldRemoveCompletedOneShot(cloud, service, "complete"),
                "non-one-shot services are not reaped by the monitor on completion");
    }

    // --- synchronizeTemplateCounters: symmetric ±1 buffer (release 1.0.72) ---

    @Test
    void testSyncCountersIgnoresSingleInflightProvision() {
        // Reproduces the race between SwarmCloud.provision()'s pre-increment and the next
        // monitor cycle: counter=1, no service yet → must NOT sync down or the increment is lost.
        SwarmAgentTemplate template = new SwarmAgentTemplate("t");
        template.setImage("jenkins/inbound-agent:latest");
        template.incrementInstances();
        cloud.setTemplates(List.of(template));

        monitor.synchronizeTemplateCounters(cloud, List.of());

        assertEquals(1, template.getCurrentInstances(),
                "in-flight reservation must not be force-reset to 0 by the monitor");
    }

    @Test
    void testSyncCountersSyncsDownWhenDiffGreaterThanOne() {
        SwarmAgentTemplate template = new SwarmAgentTemplate("t");
        template.setImage("jenkins/inbound-agent:latest");
        template.getCurrentInstancesCounter().set(5);
        cloud.setTemplates(List.of(template));

        monitor.synchronizeTemplateCounters(cloud, List.of());

        assertEquals(0, template.getCurrentInstances(),
                "stale counter (>1 above actual) must be synced down");
    }

    @Test
    void testSyncCountersIgnoresSingleMissedIncrement() {
        SwarmAgentTemplate template = new SwarmAgentTemplate("t");
        template.setImage("jenkins/inbound-agent:latest");
        cloud.setTemplates(List.of(template));

        Service service = serviceWithLabels(Map.of(ServiceLabels.TEMPLATE, "t"));

        monitor.synchronizeTemplateCounters(cloud, List.of(service));

        assertEquals(0, template.getCurrentInstances(),
                "single 'missing' increment is tolerated for one cycle to handle race with provision()");
    }

    @Test
    void testSyncCountersSyncsUpWhenDiffGreaterThanOne() {
        SwarmAgentTemplate template = new SwarmAgentTemplate("t");
        template.setImage("jenkins/inbound-agent:latest");
        cloud.setTemplates(List.of(template));

        List<Service> services = List.of(
                serviceWithLabels(Map.of(ServiceLabels.TEMPLATE, "t")),
                serviceWithLabels(Map.of(ServiceLabels.TEMPLATE, "t")),
                serviceWithLabels(Map.of(ServiceLabels.TEMPLATE, "t"))
        );

        monitor.synchronizeTemplateCounters(cloud, services);

        assertEquals(3, template.getCurrentInstances(),
                "counter must be synced up when 2+ services are unaccounted for");
    }

    private static Service serviceWithLabels(Map<String, String> labels) {
        return new Service().withSpec(new ServiceSpec().withLabels(labels));
    }
}

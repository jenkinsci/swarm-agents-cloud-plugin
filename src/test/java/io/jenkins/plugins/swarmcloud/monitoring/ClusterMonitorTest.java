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

    // --- synchronizeTemplateCounters: two-cycle confirmation for drift of 1 (#40) ---

    @Test
    void testSyncCountersConfirmsMissedDecrementAfterTwoCycles() {
        // Issue #40: a single missed decrement permanently blocks the last agent slot
        // because the ±1 tolerance never corrects it. A drift of exactly 1 must be
        // corrected once the same drift is seen on two consecutive monitor cycles.
        SwarmAgentTemplate template = new SwarmAgentTemplate("t");
        template.setImage("jenkins/inbound-agent:latest");
        template.getCurrentInstancesCounter().set(1); // stale: leaked reservation (service died)
        cloud.setTemplates(List.of(template));

        // Cycle 1: counter=1 but no service -> first sighting of drift, tolerated
        // (this is indistinguishable from an in-flight reservation)
        monitor.synchronizeTemplateCounters(cloud, List.of());
        assertEquals(1, template.getCurrentInstances(),
                "drift of 1 must be tolerated on the first cycle (in-flight window)");

        // Cycle 2: same drift persists -> the reservation window has passed,
        // the counter is stale - sync it down
        monitor.synchronizeTemplateCounters(cloud, List.of());
        assertEquals(0, template.getCurrentInstances(),
                "drift of 1 persisting across two cycles must be synced down (#40)");
    }

    @Test
    void testSyncCountersSyncsUpMissedIncrementAfterTwoCycles() {
        // Symmetric case: one service exists but the counter was never incremented
        // (missed increment). Tolerated on the first cycle, corrected on the second.
        SwarmAgentTemplate template = new SwarmAgentTemplate("t");
        template.setImage("jenkins/inbound-agent:latest");
        cloud.setTemplates(List.of(template));

        Service service = serviceWithLabels(Map.of(ServiceLabels.TEMPLATE, "t"));

        monitor.synchronizeTemplateCounters(cloud, List.of(service));
        assertEquals(0, template.getCurrentInstances(),
                "drift of 1 must be tolerated on the first cycle");

        monitor.synchronizeTemplateCounters(cloud, List.of(service));
        assertEquals(1, template.getCurrentInstances(),
                "drift of 1 persisting across two cycles must be synced up");
    }

    @Test
    void testSyncCountersResetsConfirmationWhenDriftClears() {
        // The two-cycle confirmation must reset if the drift disappears: an in-flight
        // reservation that completes normally must not leave confirmation state behind,
        // otherwise an unrelated later drift would be corrected on its first sighting.
        SwarmAgentTemplate template = new SwarmAgentTemplate("t");
        template.setImage("jenkins/inbound-agent:latest");
        cloud.setTemplates(List.of(template));

        // Cycle 1: in-flight increment (counter=1, no service) -> observed, tolerated
        template.incrementInstances();
        monitor.synchronizeTemplateCounters(cloud, List.of());
        assertEquals(1, template.getCurrentInstances(), "in-flight must be tolerated");

        // In-flight completes: service appears, counter matches -> drift cleared
        Service service = serviceWithLabels(Map.of(ServiceLabels.TEMPLATE, "t"));
        monitor.synchronizeTemplateCounters(cloud, List.of(service));

        // A fresh drift (missed decrement this time) must start a NEW confirmation window
        template.decrementInstances(); // counter=0 but 1 service exists
        monitor.synchronizeTemplateCounters(cloud, List.of(service));
        assertEquals(0, template.getCurrentInstances(),
                "fresh drift must be tolerated on its first sighting");

        monitor.synchronizeTemplateCounters(cloud, List.of(service));
        assertEquals(1, template.getCurrentInstances(),
                "fresh drift must only be corrected after two consecutive cycles");
    }

    @Test
    void testSyncCountersTracksConfirmationPerTemplate() {
        // Confirmation state must be per template: template 'a' drifting since cycle 1
        // must be corrected on cycle 2, while template 'b' drifting only since cycle 2
        // must still be tolerated on cycle 2.
        SwarmAgentTemplate a = new SwarmAgentTemplate("a");
        SwarmAgentTemplate b = new SwarmAgentTemplate("b");
        a.setImage("jenkins/inbound-agent:latest");
        b.setImage("jenkins/inbound-agent:latest");
        a.getCurrentInstancesCounter().set(1); // a: stale counter, no service ever
        b.getCurrentInstancesCounter().set(1); // b: matches its 1 service initially
        cloud.setTemplates(List.of(a, b));

        Service serviceB = serviceWithLabels(Map.of(ServiceLabels.TEMPLATE, "b"));

        // Cycle 1: a drifts (1st sighting), b matches
        monitor.synchronizeTemplateCounters(cloud, List.of(serviceB));
        assertEquals(1, a.getCurrentInstances(), "a: first sighting tolerated");
        assertEquals(1, b.getCurrentInstances(), "b: no drift");

        // Cycle 2: b's service disappeared -> b drifts for the first time;
        // a drifts for the second time and must be corrected NOW
        monitor.synchronizeTemplateCounters(cloud, List.of());
        assertEquals(0, a.getCurrentInstances(),
                "a: confirmed drift must be corrected on the second cycle");
        assertEquals(1, b.getCurrentInstances(),
                "b: first sighting must be tolerated, not borrowed from a's confirmation");
    }

    private static Service serviceWithLabels(Map<String, String> labels) {
        return new Service().withSpec(new ServiceSpec().withLabels(labels));
    }
}

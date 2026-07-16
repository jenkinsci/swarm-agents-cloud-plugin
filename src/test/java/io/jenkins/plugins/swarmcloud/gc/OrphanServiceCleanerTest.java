package io.jenkins.plugins.swarmcloud.gc;

import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OrphanServiceCleaner.
 */
@WithJenkins
class OrphanServiceCleanerTest {

    @Test
    void testCleanerConstruction(JenkinsRule jenkins) {
        OrphanServiceCleaner cleaner = new OrphanServiceCleaner();
        assertNotNull(cleaner);
    }

    @Test
    void testRecurrencePeriod(JenkinsRule jenkins) {
        OrphanServiceCleaner cleaner = new OrphanServiceCleaner();

        // Should run every 5 minutes
        long expected = TimeUnit.MINUTES.toMillis(5);
        assertEquals(expected, cleaner.getRecurrencePeriod());
    }

    @Test
    void testExtensionRegistered(JenkinsRule jenkins) {
        // OrphanServiceCleaner should be registered as an extension
        var extensions = jenkins.jenkins.getExtensionList(OrphanServiceCleaner.class);
        assertEquals(1, extensions.size());
    }

    @Test
    void testCleanupNowWithValidCloud(JenkinsRule jenkins) {
        // Create a cloud (no Docker connection, so it won't actually clean anything)
        io.jenkins.plugins.swarmcloud.SwarmCloud cloud =
                new io.jenkins.plugins.swarmcloud.SwarmCloud("cleanup-test-cloud");
        cloud.setDockerHost("tcp://non-existent-host:2376");
        jenkins.jenkins.clouds.add(cloud);

        // Cleanup should return 0 (connection will fail, no services to clean)
        int cleaned = OrphanServiceCleaner.cleanupNow(cloud);
        assertEquals(0, cleaned);
    }

    @Test
    void testCleanupNowWithNoServices(JenkinsRule jenkins) {
        // Create a cloud without Docker connection
        io.jenkins.plugins.swarmcloud.SwarmCloud cloud =
                new io.jenkins.plugins.swarmcloud.SwarmCloud("test-cloud");
        cloud.setDockerHost("tcp://non-existent:2376");
        jenkins.jenkins.clouds.add(cloud);

        // Cleanup should return 0 (no services or connection issues)
        int cleaned = OrphanServiceCleaner.cleanupNow(cloud);
        assertEquals(0, cleaned);
    }

    // --- removal guard ---
    // shouldRemoveService(isOrphan, isTooOld, agentAlive, confirmedOrphan):
    //   agentAlive      = node registered OR a computer for the agent is online/busy
    //   confirmedOrphan = the service was already a removal candidate in the previous sweep (debounce)

    @Test
    void removesConfirmedOrphanWhenAgentDead() {
        OrphanServiceCleaner cleaner = new OrphanServiceCleaner();
        assertTrue(cleaner.shouldRemoveService(true, false, false, true),
                "an orphan service with no live agent, seen across two sweeps, must be removed");
    }

    @Test
    void keepsHealthyService() {
        OrphanServiceCleaner cleaner = new OrphanServiceCleaner();
        assertFalse(cleaner.shouldRemoveService(false, false, true, true),
                "a service that is neither orphan nor too old is never removed");
    }

    @Test
    void keepsTooOldServiceWhileAgentAlive() {
        OrphanServiceCleaner cleaner = new OrphanServiceCleaner();
        // A long-running build can outlive MAX_SERVICE_AGE while its agent is still alive.
        assertFalse(cleaner.shouldRemoveService(false, true, true, true),
                "a too-old service must NOT be removed while its agent is still alive");
    }

    @Test
    void removesConfirmedTooOldServiceWhenAgentDead() {
        OrphanServiceCleaner cleaner = new OrphanServiceCleaner();
        assertTrue(cleaner.shouldRemoveService(false, true, false, true),
                "a too-old service whose agent is dead, confirmed across two sweeps, must be removed");
    }

    @Test
    void keepsOrphanServiceWhileComputerStillOnline() {
        OrphanServiceCleaner cleaner = new OrphanServiceCleaner();
        // The #27 bug: a one-shot agent's node is transiently absent from getNodes() while it is
        // still connected and building. agentAlive (a live computer) must keep the service.
        assertFalse(cleaner.shouldRemoveService(true, false, true, true),
                "an orphan service must be kept while a computer for the agent is still online");
    }

    @Test
    void keepsOrphanServiceUntilSeenAcrossTwoSweeps() {
        OrphanServiceCleaner cleaner = new OrphanServiceCleaner();
        // Debounce: a single getNodes() snapshot is unreliable, so a first-time orphan is kept.
        assertFalse(cleaner.shouldRemoveService(true, false, false, false),
                "an orphan seen for the first time (not confirmed) must not be removed yet");
    }
}

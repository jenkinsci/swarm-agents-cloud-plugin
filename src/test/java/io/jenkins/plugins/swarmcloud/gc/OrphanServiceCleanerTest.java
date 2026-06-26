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

    // --- removal guard: never delete a service out from under a still-registered node ---

    @Test
    void removesOrphanWhenNodeGone() {
        OrphanServiceCleaner cleaner = new OrphanServiceCleaner();
        assertTrue(cleaner.shouldRemoveService(true, false, false),
                "an orphan service (no registered node) must be removed");
    }

    @Test
    void keepsTooOldServiceWhileNodeStillRegistered() {
        OrphanServiceCleaner cleaner = new OrphanServiceCleaner();
        // A long-running build can outlive MAX_SERVICE_AGE while its node is still registered and
        // possibly mid-build. The age check alone must not delete the service out from under it.
        assertFalse(cleaner.shouldRemoveService(false, true, true),
                "a too-old service must NOT be removed while its node is still registered");
    }

    @Test
    void removesTooOldServiceWhenNodeGone() {
        OrphanServiceCleaner cleaner = new OrphanServiceCleaner();
        assertTrue(cleaner.shouldRemoveService(false, true, false),
                "a too-old service whose node is gone must be removed");
    }

    @Test
    void keepsHealthyServiceWithRegisteredNode() {
        OrphanServiceCleaner cleaner = new OrphanServiceCleaner();
        assertFalse(cleaner.shouldRemoveService(false, false, true),
                "a service that is neither orphan nor too old is never removed");
    }
}

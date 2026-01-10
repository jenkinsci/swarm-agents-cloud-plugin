package io.jenkins.plugins.swarmcloud.monitoring;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SwarmAuditLog.
 */
@WithJenkins
class SwarmAuditLogTest {

    private JenkinsRule jenkins;

    @BeforeEach
    void setUp(JenkinsRule jenkins) {
        this.jenkins = jenkins;
        SwarmAuditLog.clear();
    }

    @AfterEach
    void tearDown() {
        SwarmAuditLog.clear();
    }

    @Test
    void testLogProvision() {
        SwarmAuditLog.logProvision("my-cloud", "maven-template", "agent-123", "svc-456");

        List<SwarmAuditLog.AuditEntry> entries = SwarmAuditLog.getRecentEntries(10);
        assertEquals(1, entries.size());

        SwarmAuditLog.AuditEntry entry = entries.get(0);
        assertEquals(SwarmAuditLog.AuditEvent.PROVISION, entry.getEvent());
        assertEquals("my-cloud", entry.getCloudName());
        assertEquals("maven-template", entry.getTemplateName());
        assertEquals("agent-123", entry.getAgentName());
        assertEquals("svc-456", entry.getServiceId());
        assertNull(entry.getMessage());
    }

    @Test
    void testLogTermination() {
        SwarmAuditLog.logTermination("my-cloud", "agent-123", "svc-456", "Idle timeout");

        List<SwarmAuditLog.AuditEntry> entries = SwarmAuditLog.getRecentEntries(10);
        assertEquals(1, entries.size());

        SwarmAuditLog.AuditEntry entry = entries.get(0);
        assertEquals(SwarmAuditLog.AuditEvent.TERMINATE, entry.getEvent());
        assertEquals("my-cloud", entry.getCloudName());
        assertNull(entry.getTemplateName());
        assertEquals("agent-123", entry.getAgentName());
        assertEquals("svc-456", entry.getServiceId());
        assertEquals("Idle timeout", entry.getMessage());
    }

    @Test
    void testLogProvisionFailure() {
        SwarmAuditLog.logProvisionFailure("my-cloud", "maven-template", "Connection refused");

        List<SwarmAuditLog.AuditEntry> entries = SwarmAuditLog.getRecentEntries(10);
        assertEquals(1, entries.size());

        SwarmAuditLog.AuditEntry entry = entries.get(0);
        assertEquals(SwarmAuditLog.AuditEvent.PROVISION_FAILED, entry.getEvent());
        assertEquals("my-cloud", entry.getCloudName());
        assertEquals("maven-template", entry.getTemplateName());
        assertNull(entry.getAgentName());
        assertNull(entry.getServiceId());
        assertEquals("Connection refused", entry.getMessage());
    }

    @Test
    void testLogConfigChange() {
        SwarmAuditLog.logConfigChange("my-cloud", "maven-template", "Updated memory limit");

        List<SwarmAuditLog.AuditEntry> entries = SwarmAuditLog.getRecentEntries(10);
        assertEquals(1, entries.size());

        SwarmAuditLog.AuditEntry entry = entries.get(0);
        assertEquals(SwarmAuditLog.AuditEvent.CONFIG_CHANGE, entry.getEvent());
        assertEquals("my-cloud", entry.getCloudName());
        assertEquals("maven-template", entry.getTemplateName());
        assertEquals("Updated memory limit", entry.getMessage());
    }

    @Test
    void testLogApiAccess() {
        SwarmAuditLog.logApiAccess("/swarm/clouds", "GET", "my-cloud");

        List<SwarmAuditLog.AuditEntry> entries = SwarmAuditLog.getRecentEntries(10);
        assertEquals(1, entries.size());

        SwarmAuditLog.AuditEntry entry = entries.get(0);
        assertEquals(SwarmAuditLog.AuditEvent.API_ACCESS, entry.getEvent());
        assertEquals("my-cloud", entry.getCloudName());
        assertEquals("GET /swarm/clouds", entry.getMessage());
    }

    @Test
    void testLogConnectionTestSuccess() {
        SwarmAuditLog.logConnectionTest("my-cloud", "tcp://localhost:2376", true, null);

        List<SwarmAuditLog.AuditEntry> entries = SwarmAuditLog.getRecentEntries(10);
        assertEquals(1, entries.size());

        SwarmAuditLog.AuditEntry entry = entries.get(0);
        assertEquals(SwarmAuditLog.AuditEvent.CONNECTION_TEST_SUCCESS, entry.getEvent());
        assertEquals("my-cloud", entry.getCloudName());
        assertTrue(entry.getMessage().contains("Connection successful"));
    }

    @Test
    void testLogConnectionTestFailure() {
        SwarmAuditLog.logConnectionTest("my-cloud", "tcp://localhost:2376", false, "Connection refused");

        List<SwarmAuditLog.AuditEntry> entries = SwarmAuditLog.getRecentEntries(10);
        assertEquals(1, entries.size());

        SwarmAuditLog.AuditEntry entry = entries.get(0);
        assertEquals(SwarmAuditLog.AuditEvent.CONNECTION_TEST_FAILED, entry.getEvent());
        assertEquals("my-cloud", entry.getCloudName());
        assertTrue(entry.getMessage().contains("Connection failed"));
        assertTrue(entry.getMessage().contains("Connection refused"));
    }

    @Test
    void testGetRecentEntriesWithLimit() {
        for (int i = 0; i < 10; i++) {
            SwarmAuditLog.logProvision("cloud-" + i, "template", "agent-" + i, "svc-" + i);
        }

        List<SwarmAuditLog.AuditEntry> entries = SwarmAuditLog.getRecentEntries(5);
        assertEquals(5, entries.size());
    }

    @Test
    void testGetRecentEntriesOrder() {
        SwarmAuditLog.logProvision("cloud-1", "template", "agent-1", "svc-1");
        SwarmAuditLog.logProvision("cloud-2", "template", "agent-2", "svc-2");
        SwarmAuditLog.logProvision("cloud-3", "template", "agent-3", "svc-3");

        List<SwarmAuditLog.AuditEntry> entries = SwarmAuditLog.getRecentEntries(10);
        assertEquals(3, entries.size());
        // Most recent first
        assertEquals("cloud-3", entries.get(0).getCloudName());
        assertEquals("cloud-2", entries.get(1).getCloudName());
        assertEquals("cloud-1", entries.get(2).getCloudName());
    }

    @Test
    void testGetEntriesForCloud() {
        SwarmAuditLog.logProvision("cloud-1", "template", "agent-1", "svc-1");
        SwarmAuditLog.logProvision("cloud-2", "template", "agent-2", "svc-2");
        SwarmAuditLog.logProvision("cloud-1", "template", "agent-3", "svc-3");
        SwarmAuditLog.logTermination("cloud-1", "agent-1", "svc-1", "Idle");

        List<SwarmAuditLog.AuditEntry> entries = SwarmAuditLog.getEntriesForCloud("cloud-1", 10);
        assertEquals(3, entries.size());
        for (SwarmAuditLog.AuditEntry entry : entries) {
            assertEquals("cloud-1", entry.getCloudName());
        }
    }

    @Test
    void testGetEntriesForCloudWithLimit() {
        for (int i = 0; i < 10; i++) {
            SwarmAuditLog.logProvision("my-cloud", "template", "agent-" + i, "svc-" + i);
        }

        List<SwarmAuditLog.AuditEntry> entries = SwarmAuditLog.getEntriesForCloud("my-cloud", 3);
        assertEquals(3, entries.size());
    }

    @Test
    void testClear() {
        SwarmAuditLog.logProvision("cloud-1", "template", "agent-1", "svc-1");
        SwarmAuditLog.logProvision("cloud-2", "template", "agent-2", "svc-2");

        assertEquals(2, SwarmAuditLog.getRecentEntries(10).size());

        SwarmAuditLog.clear();

        assertEquals(0, SwarmAuditLog.getRecentEntries(10).size());
    }

    @Test
    void testAuditEntryTimestamp() {
        long beforeLog = System.currentTimeMillis();
        SwarmAuditLog.logProvision("my-cloud", "template", "agent-1", "svc-1");
        long afterLog = System.currentTimeMillis();

        List<SwarmAuditLog.AuditEntry> entries = SwarmAuditLog.getRecentEntries(1);
        SwarmAuditLog.AuditEntry entry = entries.get(0);

        assertTrue(entry.getTimestamp() >= beforeLog);
        assertTrue(entry.getTimestamp() <= afterLog);
    }

    @Test
    void testAuditEntryFormattedTimestamp() {
        SwarmAuditLog.logProvision("my-cloud", "template", "agent-1", "svc-1");

        List<SwarmAuditLog.AuditEntry> entries = SwarmAuditLog.getRecentEntries(1);
        String formatted = entries.get(0).getFormattedTimestamp();

        assertNotNull(formatted);
        // Format: yyyy-MM-dd HH:mm:ss
        assertTrue(formatted.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
    }

    @Test
    void testAuditEntryToString() {
        SwarmAuditLog.logProvision("my-cloud", "maven-template", "agent-123", "svc-456");

        List<SwarmAuditLog.AuditEntry> entries = SwarmAuditLog.getRecentEntries(1);
        String str = entries.get(0).toString();

        assertTrue(str.contains("PROVISION"));
        assertTrue(str.contains("my-cloud"));
        assertTrue(str.contains("maven-template"));
        assertTrue(str.contains("agent-123"));
    }

    @Test
    void testAuditEntryUser() {
        SwarmAuditLog.logProvision("my-cloud", "template", "agent-1", "svc-1");

        List<SwarmAuditLog.AuditEntry> entries = SwarmAuditLog.getRecentEntries(1);
        String user = entries.get(0).getUser();

        assertNotNull(user);
        // In tests without authenticated user, should be SYSTEM or ANONYMOUS
        assertTrue(user.equals("SYSTEM") || user.equals("ANONYMOUS") || !user.isEmpty());
    }
}

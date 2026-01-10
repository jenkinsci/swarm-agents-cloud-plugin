package io.jenkins.plugins.swarmcloud.monitoring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ServiceInfo.
 */
class ServiceInfoTest {

    private ServiceInfo serviceInfo;

    @BeforeEach
    void setUp() {
        serviceInfo = new ServiceInfo();
    }

    @Test
    void testSettersAndGetters() {
        serviceInfo.setId("abc123def456");
        serviceInfo.setName("swarm-maven-agent-12345");
        serviceInfo.setState("running");
        serviceInfo.setTemplateName("maven");
        serviceInfo.setCreatedTime(System.currentTimeMillis() - 3600_000); // 1 hour ago
        serviceInfo.setError(null);

        assertEquals("abc123def456", serviceInfo.getId());
        assertEquals("swarm-maven-agent-12345", serviceInfo.getName());
        assertEquals("running", serviceInfo.getState());
        assertEquals("maven", serviceInfo.getTemplateName());
        assertTrue(serviceInfo.getCreatedTime() > 0);
        assertNull(serviceInfo.getError());
    }

    @Test
    void testErrorMessage() {
        serviceInfo.setError("OOM killed");
        assertEquals("OOM killed", serviceInfo.getError());
    }

    @Test
    void testStateClassRunning() {
        serviceInfo.setState("running");
        assertEquals("success", serviceInfo.getStateClass());

        serviceInfo.setState("RUNNING");
        assertEquals("success", serviceInfo.getStateClass());
    }

    @Test
    void testStateClassPending() {
        serviceInfo.setState("pending");
        assertEquals("warning", serviceInfo.getStateClass());

        serviceInfo.setState("PENDING");
        assertEquals("warning", serviceInfo.getStateClass());
    }

    @Test
    void testStateClassFailed() {
        serviceInfo.setState("failed");
        assertEquals("danger", serviceInfo.getStateClass());

        serviceInfo.setState("FAILED");
        assertEquals("danger", serviceInfo.getStateClass());
    }

    @Test
    void testStateClassComplete() {
        serviceInfo.setState("complete");
        assertEquals("info", serviceInfo.getStateClass());

        serviceInfo.setState("COMPLETE");
        assertEquals("info", serviceInfo.getStateClass());
    }

    @Test
    void testStateClassShutdown() {
        serviceInfo.setState("shutdown");
        assertEquals("secondary", serviceInfo.getStateClass());

        serviceInfo.setState("stopped");
        assertEquals("secondary", serviceInfo.getStateClass());
    }

    @Test
    void testStateClassUnknown() {
        serviceInfo.setState("unknown");
        assertEquals("secondary", serviceInfo.getStateClass());

        serviceInfo.setState(null);
        assertEquals("secondary", serviceInfo.getStateClass());
    }

    @Test
    void testIsRunning() {
        serviceInfo.setState("running");
        assertTrue(serviceInfo.isRunning());

        serviceInfo.setState("RUNNING");
        assertTrue(serviceInfo.isRunning());

        serviceInfo.setState("pending");
        assertFalse(serviceInfo.isRunning());

        serviceInfo.setState(null);
        assertFalse(serviceInfo.isRunning());
    }

    @Test
    void testIsFailed() {
        serviceInfo.setState("failed");
        assertTrue(serviceInfo.isFailed());

        serviceInfo.setState("FAILED");
        assertTrue(serviceInfo.isFailed());

        serviceInfo.setState("running");
        assertFalse(serviceInfo.isFailed());

        serviceInfo.setState(null);
        assertFalse(serviceInfo.isFailed());
    }

    @Test
    void testUptimeSeconds() {
        serviceInfo.setCreatedTime(System.currentTimeMillis() - 30_000); // 30 seconds ago
        String uptime = serviceInfo.getUptime();
        assertTrue(uptime.endsWith("s"));
    }

    @Test
    void testUptimeMinutes() {
        serviceInfo.setCreatedTime(System.currentTimeMillis() - 300_000); // 5 minutes ago
        String uptime = serviceInfo.getUptime();
        assertTrue(uptime.endsWith("m"));
    }

    @Test
    void testUptimeHours() {
        serviceInfo.setCreatedTime(System.currentTimeMillis() - 7200_000); // 2 hours ago
        String uptime = serviceInfo.getUptime();
        assertTrue(uptime.contains("h"));
    }

    @Test
    void testUptimeDays() {
        serviceInfo.setCreatedTime(System.currentTimeMillis() - 172800_000L); // 2 days ago
        String uptime = serviceInfo.getUptime();
        assertTrue(uptime.contains("d"));
    }

    @Test
    void testUptimeZeroCreatedTime() {
        serviceInfo.setCreatedTime(0);
        assertEquals("unknown", serviceInfo.getUptime());
    }

    @Test
    void testShortId() {
        serviceInfo.setId("abc123def456ghi789");
        assertEquals("abc123def456", serviceInfo.getShortId());
    }

    @Test
    void testShortIdForShortId() {
        serviceInfo.setId("abc123");
        assertEquals("abc123", serviceInfo.getShortId());
    }

    @Test
    void testShortIdExactly12() {
        serviceInfo.setId("abc123def456");
        assertEquals("abc123def456", serviceInfo.getShortId());
    }

    @Test
    void testShortIdNull() {
        serviceInfo.setId(null);
        assertEquals("", serviceInfo.getShortId());
    }
}

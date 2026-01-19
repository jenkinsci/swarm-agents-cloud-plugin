package io.jenkins.plugins.swarmcloud.monitoring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for NodeInfo.
 */
class NodeInfoTest {

    private NodeInfo nodeInfo;

    @BeforeEach
    void setUp() {
        nodeInfo = new NodeInfo();
    }

    @Test
    void testSettersAndGetters() {
        nodeInfo.setId("node-abc123");
        nodeInfo.setHostname("worker-01.example.com");
        nodeInfo.setState("READY");
        nodeInfo.setRole("worker");
        nodeInfo.setAvailability("active");
        nodeInfo.setMemoryBytes(8L * 1024 * 1024 * 1024); // 8 GB
        nodeInfo.setCpuNanos(4_000_000_000L); // 4 cores

        assertEquals("node-abc123", nodeInfo.getId());
        assertEquals("worker-01.example.com", nodeInfo.getHostname());
        assertEquals("READY", nodeInfo.getState());
        assertEquals("worker", nodeInfo.getRole());
        assertEquals("active", nodeInfo.getAvailability());
        assertEquals(8L * 1024 * 1024 * 1024, nodeInfo.getMemoryBytes());
        assertEquals(4_000_000_000L, nodeInfo.getCpuNanos());
    }

    @Test
    void testGetCpuCores() {
        nodeInfo.setCpuNanos(4_000_000_000L); // 4 cores
        assertEquals(4.0, nodeInfo.getCpuCores());

        nodeInfo.setCpuNanos(2_500_000_000L); // 2.5 cores
        assertEquals(2.5, nodeInfo.getCpuCores());
    }

    @Test
    void testFormattedMemoryBytes() {
        nodeInfo.setMemoryBytes(512);
        assertEquals("512 B", nodeInfo.getFormattedMemory());
    }

    @Test
    void testFormattedMemoryKilobytes() {
        nodeInfo.setMemoryBytes(2048);
        String formatted = nodeInfo.getFormattedMemory();
        assertTrue(formatted.contains("KB"), "Should contain KB unit");
        assertTrue(formatted.contains("2"), "Should contain value 2");
    }

    @Test
    void testFormattedMemoryMegabytes() {
        nodeInfo.setMemoryBytes(512L * 1024 * 1024);
        String formatted = nodeInfo.getFormattedMemory();
        assertTrue(formatted.contains("MB"), "Should contain MB unit");
        assertTrue(formatted.contains("512"), "Should contain value 512");
    }

    @Test
    void testFormattedMemoryGigabytes() {
        nodeInfo.setMemoryBytes(16L * 1024 * 1024 * 1024);
        String formatted = nodeInfo.getFormattedMemory();
        assertTrue(formatted.contains("GB"), "Should contain GB unit");
        assertTrue(formatted.contains("16"), "Should contain value 16");
    }

    @Test
    void testStateClassReady() {
        nodeInfo.setState("READY");
        assertEquals("green", nodeInfo.getStateClass());

        nodeInfo.setState("ready");
        assertEquals("green", nodeInfo.getStateClass());
    }

    @Test
    void testStateClassDown() {
        nodeInfo.setState("DOWN");
        assertEquals("red", nodeInfo.getStateClass());

        nodeInfo.setState("down");
        assertEquals("red", nodeInfo.getStateClass());
    }

    @Test
    void testStateClassOther() {
        nodeInfo.setState("PENDING");
        assertEquals("yellow", nodeInfo.getStateClass());

        nodeInfo.setState("UNKNOWN");
        assertEquals("yellow", nodeInfo.getStateClass());

        nodeInfo.setState(null);
        assertEquals("yellow", nodeInfo.getStateClass());
    }

    @Test
    void testIsReady() {
        nodeInfo.setState("READY");
        assertTrue(nodeInfo.isReady());

        nodeInfo.setState("ready");
        assertTrue(nodeInfo.isReady());

        nodeInfo.setState("DOWN");
        assertFalse(nodeInfo.isReady());

        nodeInfo.setState(null);
        assertFalse(nodeInfo.isReady());
    }

    @Test
    void testIsManager() {
        nodeInfo.setRole("MANAGER");
        assertTrue(nodeInfo.isManager());

        nodeInfo.setRole("manager");
        assertTrue(nodeInfo.isManager());

        nodeInfo.setRole("worker");
        assertFalse(nodeInfo.isManager());

        nodeInfo.setRole(null);
        assertFalse(nodeInfo.isManager());
    }
}

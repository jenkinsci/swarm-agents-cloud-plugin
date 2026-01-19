package io.jenkins.plugins.swarmcloud.monitoring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ClusterStatus.
 */
class ClusterStatusTest {

    private ClusterStatus status;

    @BeforeEach
    void setUp() {
        status = new ClusterStatus("test-cloud");
    }

    @Test
    void testCreation() {
        assertEquals("test-cloud", status.getCloudName());
        assertTrue(status.getLastUpdate() > 0);
    }

    @Test
    void testErrorFactory() {
        ClusterStatus errorStatus = ClusterStatus.error("my-cloud", "Connection failed");

        assertEquals("my-cloud", errorStatus.getCloudName());
        assertFalse(errorStatus.isHealthy());
        assertEquals("Connection failed", errorStatus.getErrorMessage());
    }

    @Test
    void testUnknownFactory() {
        ClusterStatus unknownStatus = ClusterStatus.unknown("my-cloud");

        assertEquals("my-cloud", unknownStatus.getCloudName());
        assertFalse(unknownStatus.isHealthy());
        assertEquals("Status not yet collected", unknownStatus.getErrorMessage());
    }

    @Test
    void testSettersAndGetters() {
        status.setSwarmVersion("20.10.14");
        status.setTotalNodes(5);
        status.setReadyNodes(4);
        status.setManagerNodes(3);
        status.setTotalMemory(16L * 1024 * 1024 * 1024); // 16 GB
        status.setUsedMemory(8L * 1024 * 1024 * 1024);   // 8 GB
        status.setReservedMemory(12L * 1024 * 1024 * 1024); // 12 GB
        status.setTotalCpu(8.0);
        status.setUsedCpu(4.0);
        status.setReservedCpu(6.0);
        status.setActiveServices(10);
        status.setRunningTasks(8);
        status.setPendingTasks(2);
        status.setFailedTasks(1);
        status.setMaxAgents(20);
        status.setCurrentAgents(10);
        status.setTemplateCount(3);
        status.setHealthy(true);

        assertEquals("20.10.14", status.getSwarmVersion());
        assertEquals(5, status.getTotalNodes());
        assertEquals(4, status.getReadyNodes());
        assertEquals(3, status.getManagerNodes());
        assertEquals(16L * 1024 * 1024 * 1024, status.getTotalMemory());
        assertEquals(8L * 1024 * 1024 * 1024, status.getUsedMemory());
        assertEquals(12L * 1024 * 1024 * 1024, status.getReservedMemory());
        assertEquals(8.0, status.getTotalCpu());
        assertEquals(4.0, status.getUsedCpu());
        assertEquals(6.0, status.getReservedCpu());
        assertEquals(10, status.getActiveServices());
        assertEquals(8, status.getRunningTasks());
        assertEquals(2, status.getPendingTasks());
        assertEquals(1, status.getFailedTasks());
        assertEquals(20, status.getMaxAgents());
        assertEquals(10, status.getCurrentAgents());
        assertEquals(3, status.getTemplateCount());
        assertTrue(status.isHealthy());
    }

    @Test
    void testAddNodes() {
        NodeInfo node1 = new NodeInfo();
        node1.setId("node-1");
        node1.setHostname("worker-1");

        NodeInfo node2 = new NodeInfo();
        node2.setId("node-2");
        node2.setHostname("worker-2");

        status.addNode(node1);
        status.addNode(node2);

        assertEquals(2, status.getNodes().size());
        assertEquals("node-1", status.getNodes().get(0).getId());
        assertEquals("node-2", status.getNodes().get(1).getId());
    }

    @Test
    void testNodesListIsUnmodifiable() {
        NodeInfo node = new NodeInfo();
        node.setId("node-1");
        status.addNode(node);

        assertThrows(UnsupportedOperationException.class, () -> {
            status.getNodes().add(new NodeInfo());
        });
    }

    @Test
    void testAddServices() {
        ServiceInfo service1 = new ServiceInfo();
        service1.setId("svc-1");
        service1.setName("agent-1");

        ServiceInfo service2 = new ServiceInfo();
        service2.setId("svc-2");
        service2.setName("agent-2");

        status.addService(service1);
        status.addService(service2);

        assertEquals(2, status.getServices().size());
        assertEquals("svc-1", status.getServices().get(0).getId());
        assertEquals("svc-2", status.getServices().get(1).getId());
    }

    @Test
    void testServicesListIsUnmodifiable() {
        ServiceInfo service = new ServiceInfo();
        service.setId("svc-1");
        status.addService(service);

        assertThrows(UnsupportedOperationException.class, () -> {
            status.getServices().add(new ServiceInfo());
        });
    }

    @Test
    void testFormattedMemoryBytes() {
        status.setTotalMemory(512);
        assertEquals("512 B", status.getFormattedMemory());
    }

    @Test
    void testFormattedMemoryKilobytes() {
        status.setTotalMemory(2048);
        // Locale-agnostic check - just verify it contains KB and the number
        String formatted = status.getFormattedMemory();
        assertTrue(formatted.contains("KB"), "Should contain KB unit");
        assertTrue(formatted.contains("2"), "Should contain value 2");
    }

    @Test
    void testFormattedMemoryMegabytes() {
        status.setTotalMemory(512L * 1024 * 1024);
        String formatted = status.getFormattedMemory();
        assertTrue(formatted.contains("MB"), "Should contain MB unit");
        assertTrue(formatted.contains("512"), "Should contain value 512");
    }

    @Test
    void testFormattedMemoryGigabytes() {
        status.setTotalMemory(16L * 1024 * 1024 * 1024);
        String formatted = status.getFormattedMemory();
        assertTrue(formatted.contains("GB"), "Should contain GB unit");
        assertTrue(formatted.contains("16"), "Should contain value 16");
    }

    @Test
    void testFormattedUsedAndReservedMemory() {
        status.setUsedMemory(4L * 1024 * 1024 * 1024);
        status.setReservedMemory(8L * 1024 * 1024 * 1024);

        String usedFormatted = status.getFormattedUsedMemory();
        assertTrue(usedFormatted.contains("GB") && usedFormatted.contains("4"), "Used memory format");
        String reservedFormatted = status.getFormattedReservedMemory();
        assertTrue(reservedFormatted.contains("GB") && reservedFormatted.contains("8"), "Reserved memory format");
    }

    @Test
    void testFormattedCpu() {
        status.setTotalCpu(8.5);
        status.setUsedCpu(4.25);

        // Locale-agnostic - check for presence of digits
        String totalCpu = status.getFormattedTotalCpu();
        assertTrue(totalCpu.contains("8"), "Should contain value 8");
        String usedCpu = status.getFormattedUsedCpu();
        assertTrue(usedCpu.contains("4"), "Should contain value 4");
    }

    @Test
    void testMemoryUsagePercent() {
        status.setTotalMemory(100);
        status.setUsedMemory(50);

        assertEquals(50.0, status.getMemoryUsagePercent());
    }

    @Test
    void testMemoryUsagePercentZeroTotal() {
        status.setTotalMemory(0);
        status.setUsedMemory(50);

        assertEquals(0.0, status.getMemoryUsagePercent());
    }

    @Test
    void testMemoryReservedPercent() {
        status.setTotalMemory(100);
        status.setReservedMemory(75);

        assertEquals(75.0, status.getMemoryReservedPercent());
    }

    @Test
    void testCpuUsagePercent() {
        status.setTotalCpu(8.0);
        status.setUsedCpu(2.0);

        assertEquals(25.0, status.getCpuUsagePercent());
    }

    @Test
    void testCpuUsagePercentZeroTotal() {
        status.setTotalCpu(0);
        status.setUsedCpu(2.0);

        assertEquals(0.0, status.getCpuUsagePercent());
    }

    @Test
    void testCpuReservedPercent() {
        status.setTotalCpu(8.0);
        status.setReservedCpu(4.0);

        assertEquals(50.0, status.getCpuReservedPercent());
    }

    @Test
    void testAvailableCapacity() {
        status.setMaxAgents(20);
        status.setCurrentAgents(15);

        assertEquals(5, status.getAvailableCapacity());
    }

    @Test
    void testAvailableCapacityNotNegative() {
        status.setMaxAgents(10);
        status.setCurrentAgents(15);

        assertEquals(0, status.getAvailableCapacity());
    }

    @Test
    void testUtilizationPercent() {
        status.setMaxAgents(20);
        status.setCurrentAgents(10);

        assertEquals(50.0, status.getUtilizationPercent());
    }

    @Test
    void testUtilizationPercentZeroMax() {
        status.setMaxAgents(0);
        status.setCurrentAgents(10);

        assertEquals(0.0, status.getUtilizationPercent());
    }

    @Test
    void testStatusClassDanger() {
        status.setHealthy(false);
        assertEquals("error", status.getStatusClass());
    }

    @Test
    void testStatusClassWarningFailedTasks() {
        status.setHealthy(true);
        status.setFailedTasks(5);
        assertEquals("warning", status.getStatusClass());
    }

    @Test
    void testStatusClassWarningAtCapacity() {
        status.setHealthy(true);
        status.setFailedTasks(0);
        status.setMaxAgents(10);
        status.setCurrentAgents(10);
        assertEquals("warning", status.getStatusClass());
    }

    @Test
    void testStatusClassSuccess() {
        status.setHealthy(true);
        status.setFailedTasks(0);
        status.setMaxAgents(20);
        status.setCurrentAgents(10);
        assertEquals("success", status.getStatusClass());
    }
}

package io.jenkins.plugins.swarmcloud.monitoring;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents the current status of a Docker Swarm cluster.
 */
public class ClusterStatus {

    private final String cloudName;
    private String swarmVersion;
    private int totalNodes;
    private int readyNodes;
    private int managerNodes;
    private long totalMemory;
    private long usedMemory;
    private long reservedMemory;
    private double totalCpu;
    private double usedCpu;
    private double reservedCpu;
    private int activeServices;
    private int runningTasks;
    private int pendingTasks;
    private int failedTasks;
    private int maxAgents;
    private int currentAgents;
    private int templateCount;
    private boolean healthy;
    private String errorMessage;
    private long lastUpdate;

    private final List<NodeInfo> nodes = new ArrayList<>();
    private final List<ServiceInfo> services = new ArrayList<>();

    public ClusterStatus(@NonNull String cloudName) {
        this.cloudName = cloudName;
        this.lastUpdate = System.currentTimeMillis();
    }

    public static ClusterStatus error(String cloudName, String message) {
        ClusterStatus status = new ClusterStatus(cloudName);
        status.setHealthy(false);
        status.setErrorMessage(message);
        return status;
    }

    public static ClusterStatus unknown(String cloudName) {
        ClusterStatus status = new ClusterStatus(cloudName);
        status.setHealthy(false);
        status.setErrorMessage("Status not yet collected");
        return status;
    }

    // Getters and setters
    public String getCloudName() { return cloudName; }

    public String getSwarmVersion() { return swarmVersion; }
    public void setSwarmVersion(String swarmVersion) { this.swarmVersion = swarmVersion; }

    public int getTotalNodes() { return totalNodes; }
    public void setTotalNodes(int totalNodes) { this.totalNodes = totalNodes; }

    public int getReadyNodes() { return readyNodes; }
    public void setReadyNodes(int readyNodes) { this.readyNodes = readyNodes; }

    public int getManagerNodes() { return managerNodes; }
    public void setManagerNodes(int managerNodes) { this.managerNodes = managerNodes; }

    public long getTotalMemory() { return totalMemory; }
    public void setTotalMemory(long totalMemory) { this.totalMemory = totalMemory; }

    public long getUsedMemory() { return usedMemory; }
    public void setUsedMemory(long usedMemory) { this.usedMemory = usedMemory; }

    public long getReservedMemory() { return reservedMemory; }
    public void setReservedMemory(long reservedMemory) { this.reservedMemory = reservedMemory; }

    public double getTotalCpu() { return totalCpu; }
    public void setTotalCpu(double totalCpu) { this.totalCpu = totalCpu; }

    public double getUsedCpu() { return usedCpu; }
    public void setUsedCpu(double usedCpu) { this.usedCpu = usedCpu; }

    public double getReservedCpu() { return reservedCpu; }
    public void setReservedCpu(double reservedCpu) { this.reservedCpu = reservedCpu; }

    public int getActiveServices() { return activeServices; }
    public void setActiveServices(int activeServices) { this.activeServices = activeServices; }

    public int getRunningTasks() { return runningTasks; }
    public void setRunningTasks(int runningTasks) { this.runningTasks = runningTasks; }

    public int getPendingTasks() { return pendingTasks; }
    public void setPendingTasks(int pendingTasks) { this.pendingTasks = pendingTasks; }

    public int getFailedTasks() { return failedTasks; }
    public void setFailedTasks(int failedTasks) { this.failedTasks = failedTasks; }

    public int getMaxAgents() { return maxAgents; }
    public void setMaxAgents(int maxAgents) { this.maxAgents = maxAgents; }

    public int getCurrentAgents() { return currentAgents; }
    public void setCurrentAgents(int currentAgents) { this.currentAgents = currentAgents; }

    public int getTemplateCount() { return templateCount; }
    public void setTemplateCount(int templateCount) { this.templateCount = templateCount; }

    public boolean isHealthy() { return healthy; }
    public void setHealthy(boolean healthy) { this.healthy = healthy; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public long getLastUpdate() { return lastUpdate; }
    public void setLastUpdate(long lastUpdate) { this.lastUpdate = lastUpdate; }

    public List<NodeInfo> getNodes() { return Collections.unmodifiableList(nodes); }
    public void addNode(NodeInfo node) { nodes.add(node); }

    public List<ServiceInfo> getServices() { return Collections.unmodifiableList(services); }
    public void addService(ServiceInfo service) { services.add(service); }

    // Computed properties
    public String getFormattedMemory() {
        return formatBytes(totalMemory);
    }

    public String getFormattedUsedMemory() {
        return formatBytes(usedMemory);
    }

    public String getFormattedReservedMemory() {
        return formatBytes(reservedMemory);
    }

    public double getMemoryUsagePercent() {
        if (totalMemory == 0) return 0;
        return (double) usedMemory / totalMemory * 100;
    }

    public double getMemoryReservedPercent() {
        if (totalMemory == 0) return 0;
        return (double) reservedMemory / totalMemory * 100;
    }

    public double getCpuUsagePercent() {
        if (totalCpu == 0) return 0;
        return usedCpu / totalCpu * 100;
    }

    public double getCpuReservedPercent() {
        if (totalCpu == 0) return 0;
        return reservedCpu / totalCpu * 100;
    }

    public int getAvailableCapacity() {
        return Math.max(0, maxAgents - currentAgents);
    }

    public double getUtilizationPercent() {
        if (maxAgents == 0) return 0;
        return (double) currentAgents / maxAgents * 100;
    }

    public String getStatusClass() {
        if (!healthy) return "danger";
        if (failedTasks > 0) return "warning";
        if (currentAgents >= maxAgents) return "warning";
        return "success";
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}

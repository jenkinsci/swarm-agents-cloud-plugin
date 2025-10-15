package io.jenkins.plugins.swarmcloud.api;

/**
 * Metrics for a Docker Swarm cluster.
 */
public class SwarmMetrics {

    private int totalNodes;
    private int readyNodes;
    private int activeAgents;
    private long totalMemory;
    private long usedMemory;
    private double totalCpu;
    private double usedCpu;

    public int getTotalNodes() {
        return totalNodes;
    }

    public void setTotalNodes(int totalNodes) {
        this.totalNodes = totalNodes;
    }

    public int getReadyNodes() {
        return readyNodes;
    }

    public void setReadyNodes(int readyNodes) {
        this.readyNodes = readyNodes;
    }

    public int getActiveAgents() {
        return activeAgents;
    }

    public void setActiveAgents(int activeAgents) {
        this.activeAgents = activeAgents;
    }

    public long getTotalMemory() {
        return totalMemory;
    }

    public void setTotalMemory(long totalMemory) {
        this.totalMemory = totalMemory;
    }

    public long getUsedMemory() {
        return usedMemory;
    }

    public void setUsedMemory(long usedMemory) {
        this.usedMemory = usedMemory;
    }

    public double getTotalCpu() {
        return totalCpu;
    }

    public void setTotalCpu(double totalCpu) {
        this.totalCpu = totalCpu;
    }

    public double getUsedCpu() {
        return usedCpu;
    }

    public void setUsedCpu(double usedCpu) {
        this.usedCpu = usedCpu;
    }

    public double getMemoryUsagePercent() {
        if (totalMemory == 0) return 0;
        return (double) usedMemory / totalMemory * 100;
    }

    public double getCpuUsagePercent() {
        if (totalCpu == 0) return 0;
        return usedCpu / totalCpu * 100;
    }

    public String getFormattedTotalMemory() {
        return formatBytes(totalMemory);
    }

    public String getFormattedUsedMemory() {
        return formatBytes(usedMemory);
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}

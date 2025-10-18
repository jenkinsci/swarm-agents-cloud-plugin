package io.jenkins.plugins.swarmcloud.monitoring;

/**
 * Information about a Docker Swarm node.
 */
public class NodeInfo {

    private String id;
    private String hostname;
    private String state;
    private String role;
    private String availability;
    private long memoryBytes;
    private long cpuNanos;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getHostname() { return hostname; }
    public void setHostname(String hostname) { this.hostname = hostname; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getAvailability() { return availability; }
    public void setAvailability(String availability) { this.availability = availability; }

    public long getMemoryBytes() { return memoryBytes; }
    public void setMemoryBytes(long memoryBytes) { this.memoryBytes = memoryBytes; }

    public long getCpuNanos() { return cpuNanos; }
    public void setCpuNanos(long cpuNanos) { this.cpuNanos = cpuNanos; }

    public double getCpuCores() {
        return cpuNanos / 1_000_000_000.0;
    }

    public String getFormattedMemory() {
        if (memoryBytes < 1024) return memoryBytes + " B";
        if (memoryBytes < 1024 * 1024) return String.format("%.1f KB", memoryBytes / 1024.0);
        if (memoryBytes < 1024L * 1024 * 1024) return String.format("%.1f MB", memoryBytes / (1024.0 * 1024));
        return String.format("%.1f GB", memoryBytes / (1024.0 * 1024 * 1024));
    }

    public String getStateClass() {
        if ("READY".equalsIgnoreCase(state)) return "success";
        if ("DOWN".equalsIgnoreCase(state)) return "danger";
        return "warning";
    }

    public boolean isReady() {
        return "READY".equalsIgnoreCase(state);
    }

    public boolean isManager() {
        return "MANAGER".equalsIgnoreCase(role);
    }
}

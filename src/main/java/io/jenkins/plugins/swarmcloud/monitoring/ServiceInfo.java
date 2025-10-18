package io.jenkins.plugins.swarmcloud.monitoring;

import java.time.Duration;
import java.time.Instant;

/**
 * Information about a Docker Swarm service (Jenkins agent).
 */
public class ServiceInfo {

    private String id;
    private String name;
    private String state;
    private String templateName;
    private long createdTime;
    private String error;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }

    public long getCreatedTime() { return createdTime; }
    public void setCreatedTime(long createdTime) { this.createdTime = createdTime; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public String getStateClass() {
        if ("running".equalsIgnoreCase(state)) return "success";
        if ("pending".equalsIgnoreCase(state)) return "warning";
        if ("failed".equalsIgnoreCase(state)) return "danger";
        return "secondary";
    }

    public boolean isRunning() {
        return "running".equalsIgnoreCase(state);
    }

    public boolean isFailed() {
        return "failed".equalsIgnoreCase(state);
    }

    public String getUptime() {
        if (createdTime == 0) return "unknown";

        long now = System.currentTimeMillis();
        long uptimeMs = now - createdTime;

        if (uptimeMs < 60_000) {
            return (uptimeMs / 1000) + "s";
        } else if (uptimeMs < 3600_000) {
            return (uptimeMs / 60_000) + "m";
        } else if (uptimeMs < 86400_000) {
            return (uptimeMs / 3600_000) + "h " + ((uptimeMs % 3600_000) / 60_000) + "m";
        } else {
            return (uptimeMs / 86400_000) + "d " + ((uptimeMs % 86400_000) / 3600_000) + "h";
        }
    }

    public String getShortId() {
        if (id == null) return "";
        return id.length() > 12 ? id.substring(0, 12) : id;
    }
}

package io.jenkins.plugins.swarmcloud.monitoring;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.model.User;
import jenkins.model.Jenkins;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Audit logging for Swarm agent operations.
 * Logs provisioning, termination, and error events for security and debugging.
 */
@Extension
public class SwarmAuditLog {

    private static final Logger LOGGER = Logger.getLogger(SwarmAuditLog.class.getName());
    private static final int MAX_ENTRIES = 1000;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private static final ConcurrentLinkedDeque<AuditEntry> auditLog = new ConcurrentLinkedDeque<>();

    /**
     * Log agent provisioning event.
     */
    public static void logProvision(@NonNull String cloudName,
                                    @NonNull String templateName,
                                    @NonNull String agentName,
                                    @Nullable String serviceId) {
        String user = getCurrentUser();
        AuditEntry entry = new AuditEntry(
                AuditEvent.PROVISION,
                cloudName,
                templateName,
                agentName,
                serviceId,
                null,
                user
        );
        addEntry(entry);
        LOGGER.log(Level.FINE, "[AUDIT] PROVISION: cloud={0}, template={1}, agent={2}, serviceId={3}, user={4}",
                new Object[]{cloudName, templateName, agentName, serviceId, user});
    }

    /**
     * Log agent termination event.
     */
    public static void logTermination(@NonNull String cloudName,
                                      @NonNull String agentName,
                                      @Nullable String serviceId,
                                      @NonNull String reason) {
        String user = getCurrentUser();
        AuditEntry entry = new AuditEntry(
                AuditEvent.TERMINATE,
                cloudName,
                null,
                agentName,
                serviceId,
                reason,
                user
        );
        addEntry(entry);
        LOGGER.log(Level.FINE, "[AUDIT] TERMINATE: cloud={0}, agent={1}, serviceId={2}, reason={3}, user={4}",
                new Object[]{cloudName, agentName, serviceId, reason, user});
    }

    /**
     * Log provision failure event.
     */
    public static void logProvisionFailure(@NonNull String cloudName,
                                           @NonNull String templateName,
                                           @NonNull String errorMessage) {
        String user = getCurrentUser();
        AuditEntry entry = new AuditEntry(
                AuditEvent.PROVISION_FAILED,
                cloudName,
                templateName,
                null,
                null,
                errorMessage,
                user
        );
        addEntry(entry);
        LOGGER.log(Level.WARNING, "[AUDIT] PROVISION_FAILED: cloud={0}, template={1}, error={2}, user={3}",
                new Object[]{cloudName, templateName, errorMessage, user});
    }

    /**
     * Log configuration change event.
     */
    public static void logConfigChange(@NonNull String cloudName,
                                       @Nullable String templateName,
                                       @NonNull String changeDescription) {
        String user = getCurrentUser();
        AuditEntry entry = new AuditEntry(
                AuditEvent.CONFIG_CHANGE,
                cloudName,
                templateName,
                null,
                null,
                changeDescription,
                user
        );
        addEntry(entry);
        LOGGER.log(Level.FINE, "[AUDIT] CONFIG_CHANGE: cloud={0}, template={1}, change={2}, user={3}",
                new Object[]{cloudName, templateName, changeDescription, user});
    }

    /**
     * Log API access event.
     */
    public static void logApiAccess(@NonNull String endpoint,
                                    @NonNull String method,
                                    @Nullable String cloudName) {
        String user = getCurrentUser();
        String message = String.format("%s %s", method, endpoint);
        AuditEntry entry = new AuditEntry(
                AuditEvent.API_ACCESS,
                cloudName,
                null,
                null,
                null,
                message,
                user
        );
        addEntry(entry);
        LOGGER.log(Level.FINE, "[AUDIT] API_ACCESS: {0} {1}, cloud={2}, user={3}",
                new Object[]{method, endpoint, cloudName, user});
    }

    /**
     * Log connection test event.
     */
    public static void logConnectionTest(@NonNull String cloudName,
                                         @NonNull String dockerHost,
                                         boolean success,
                                         @Nullable String errorMessage) {
        String user = getCurrentUser();
        String message = success ? "Connection successful to " + dockerHost
                : "Connection failed to " + dockerHost + ": " + errorMessage;
        AuditEntry entry = new AuditEntry(
                success ? AuditEvent.CONNECTION_TEST_SUCCESS : AuditEvent.CONNECTION_TEST_FAILED,
                cloudName,
                null,
                null,
                null,
                message,
                user
        );
        addEntry(entry);
        if (success) {
            LOGGER.log(Level.FINE, "[AUDIT] CONNECTION_TEST_SUCCESS: cloud={0}, host={1}, user={2}",
                    new Object[]{cloudName, dockerHost, user});
        } else {
            LOGGER.log(Level.WARNING, "[AUDIT] CONNECTION_TEST_FAILED: cloud={0}, host={1}, error={2}, user={3}",
                    new Object[]{cloudName, dockerHost, errorMessage, user});
        }
    }

    private static void addEntry(AuditEntry entry) {
        auditLog.addFirst(entry);
        // Keep only last MAX_ENTRIES
        while (auditLog.size() > MAX_ENTRIES) {
            auditLog.removeLast();
        }
    }

    @NonNull
    private static String getCurrentUser() {
        User user = User.current();
        if (user != null) {
            return user.getId();
        }
        // Check for system user or anonymous
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins != null) {
            return "SYSTEM";
        }
        return "ANONYMOUS";
    }

    /**
     * Get recent audit entries.
     *
     * @param limit Maximum number of entries to return
     * @return List of recent audit entries
     */
    @NonNull
    public static List<AuditEntry> getRecentEntries(int limit) {
        List<AuditEntry> entries = new ArrayList<>();
        int count = 0;
        for (AuditEntry entry : auditLog) {
            if (count >= limit) break;
            entries.add(entry);
            count++;
        }
        return Collections.unmodifiableList(entries);
    }

    /**
     * Get all audit entries for a specific cloud.
     */
    @NonNull
    public static List<AuditEntry> getEntriesForCloud(@NonNull String cloudName, int limit) {
        List<AuditEntry> entries = new ArrayList<>();
        for (AuditEntry entry : auditLog) {
            if (entries.size() >= limit) break;
            if (cloudName.equals(entry.getCloudName())) {
                entries.add(entry);
            }
        }
        return Collections.unmodifiableList(entries);
    }

    /**
     * Clear all audit entries (for testing).
     */
    public static void clear() {
        auditLog.clear();
    }

    /**
     * Audit event types.
     */
    public enum AuditEvent {
        PROVISION,
        TERMINATE,
        PROVISION_FAILED,
        CONFIG_CHANGE,
        API_ACCESS,
        CONNECTION_TEST_SUCCESS,
        CONNECTION_TEST_FAILED
    }

    /**
     * Audit log entry.
     */
    public static class AuditEntry {
        private final long timestamp;
        private final AuditEvent event;
        private final String cloudName;
        private final String templateName;
        private final String agentName;
        private final String serviceId;
        private final String message;
        private final String user;

        public AuditEntry(AuditEvent event, String cloudName, String templateName,
                          String agentName, String serviceId, String message, String user) {
            this.timestamp = System.currentTimeMillis();
            this.event = event;
            this.cloudName = cloudName;
            this.templateName = templateName;
            this.agentName = agentName;
            this.serviceId = serviceId;
            this.message = message;
            this.user = user;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public String getFormattedTimestamp() {
            return FORMATTER.format(Instant.ofEpochMilli(timestamp));
        }

        public AuditEvent getEvent() {
            return event;
        }

        public String getCloudName() {
            return cloudName;
        }

        public String getTemplateName() {
            return templateName;
        }

        public String getAgentName() {
            return agentName;
        }

        public String getServiceId() {
            return serviceId;
        }

        public String getMessage() {
            return message;
        }

        public String getUser() {
            return user;
        }

        @Override
        public String toString() {
            return String.format("[%s] %s: cloud=%s, template=%s, agent=%s, user=%s, message=%s",
                    getFormattedTimestamp(), event, cloudName, templateName, agentName, user, message);
        }
    }
}

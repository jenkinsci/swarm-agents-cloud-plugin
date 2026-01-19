package io.jenkins.plugins.swarmcloud.gc;

import com.github.dockerjava.api.model.Service;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.AsyncPeriodicWork;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.Cloud;
import io.jenkins.plugins.swarmcloud.SwarmAgent;
import io.jenkins.plugins.swarmcloud.SwarmCloud;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Periodic task that cleans up orphan Docker Swarm services.
 * A service is considered orphan if:
 * - It has Jenkins agent labels but no corresponding Jenkins node exists
 * - It has been running longer than the maximum allowed time
 */
@Extension
public class OrphanServiceCleaner extends AsyncPeriodicWork {

    private static final Logger LOGGER = Logger.getLogger(OrphanServiceCleaner.class.getName());

    /**
     * How often to run the cleanup (in milliseconds).
     */
    private static final long RECURRENCE_PERIOD = TimeUnit.MINUTES.toMillis(5);

    /**
     * Maximum age of a service before it's considered orphan (in milliseconds).
     */
    private static final long MAX_SERVICE_AGE = TimeUnit.HOURS.toMillis(24);

    /**
     * Grace period for newly created services (in milliseconds).
     */
    private static final long GRACE_PERIOD = TimeUnit.MINUTES.toMillis(10);

    public OrphanServiceCleaner() {
        super("Swarm Agent Orphan Service Cleaner");
    }

    @Override
    public long getRecurrencePeriod() {
        return RECURRENCE_PERIOD;
    }

    @Override
    protected void execute(TaskListener listener) throws IOException, InterruptedException {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            return;
        }

        LOGGER.log(Level.FINE, "Starting orphan service cleanup");

        int totalCleaned = 0;

        for (Cloud cloud : jenkins.clouds) {
            if (cloud instanceof SwarmCloud) {
                SwarmCloud swarmCloud = (SwarmCloud) cloud;
                int cleaned = cleanOrphanServices(swarmCloud, jenkins, listener);
                totalCleaned += cleaned;
            }
        }

        if (totalCleaned > 0) {
            LOGGER.log(Level.INFO, "Orphan service cleanup completed. Cleaned: {0}", totalCleaned);
        } else {
            LOGGER.log(Level.FINE, "Orphan service cleanup completed. No services cleaned");
        }
    }

    /**
     * Cleans orphan services for a specific cloud.
     */
    private int cleanOrphanServices(SwarmCloud cloud, Jenkins jenkins, TaskListener listener) {
        int cleaned = 0;

        try {
            var dockerClient = cloud.getDockerClient();

            // Get all known agent names from Jenkins
            Set<String> knownAgentNames = getKnownAgentNames(jenkins, cloud.name);

            // Get all services from Docker Swarm
            List<Service> services = dockerClient.listServicesForCloud(cloud.name);

            LOGGER.log(Level.FINE, "Found {0} services for cloud: {1}",
                    new Object[]{services.size(), cloud.name});

            long now = System.currentTimeMillis();

            for (Service service : services) {
                var serviceSpec = service.getSpec();
                if (serviceSpec == null) {
                    continue;
                }
                String serviceName = serviceSpec.getName();
                Map<String, String> labels = serviceSpec.getLabels();

                if (labels == null) {
                    continue;
                }

                // Check if this is a Jenkins agent service
                if (!"true".equals(labels.get("jenkins.agent"))) {
                    continue;
                }

                // Check creation time
                String createdStr = labels.get("jenkins.created");
                long createdTime = 0;
                if (createdStr != null) {
                    try {
                        createdTime = Long.parseLong(createdStr);
                    } catch (NumberFormatException e) {
                        LOGGER.log(Level.FINE, "Invalid creation time for service: {0}", serviceName);
                    }
                }

                // Skip recently created services (grace period)
                if (createdTime > 0 && (now - createdTime) < GRACE_PERIOD) {
                    LOGGER.log(Level.FINE, "Service {0} is within grace period, skipping", serviceName);
                    continue;
                }

                // Check if agent exists in Jenkins
                String agentName = labels.get("jenkins.agent.name");
                if (agentName == null) {
                    agentName = serviceName;
                }

                boolean isOrphan = !knownAgentNames.contains(agentName);

                // Also check for very old services
                boolean isTooOld = createdTime > 0 && (now - createdTime) > MAX_SERVICE_AGE;

                if (isOrphan || isTooOld) {
                    String reason = isOrphan ? "orphan (no Jenkins node)" : "too old";
                    LOGGER.log(Level.FINE, "Removing {0} service: {1}", new Object[]{reason, serviceName});

                    try {
                        String serviceId = service.getId();
                        if (serviceId != null) {
                            dockerClient.removeService(serviceId);
                            cleaned++;
                        } else {
                            LOGGER.log(Level.WARNING, "Service ID is null for service: {0}", serviceName);
                        }
                    } catch (RuntimeException e) {
                        LOGGER.log(Level.WARNING, "Failed to remove orphan service: " + serviceName, e);
                        listener.error("Failed to remove service " + serviceName + ": " + e.getMessage());
                    }
                }
            }
        } catch (RuntimeException e) {
            // Catch runtime exceptions to prevent the periodic task from failing
            LOGGER.log(Level.WARNING, "Error during orphan service cleanup for cloud: " + cloud.name, e);
            listener.error("Error during cleanup: " + e.getMessage());
        }

        return cleaned;
    }

    /**
     * Gets the names of all known agents for a cloud.
     */
    @NonNull
    private Set<String> getKnownAgentNames(Jenkins jenkins, String cloudName) {
        Set<String> names = new HashSet<>();

        for (Node node : jenkins.getNodes()) {
            if (node instanceof SwarmAgent) {
                SwarmAgent agent = (SwarmAgent) node;
                if (cloudName.equals(agent.getCloudName())) {
                    names.add(agent.getNodeName());
                }
            }
        }

        return names;
    }

    /**
     * Manually triggers cleanup for a specific cloud.
     */
    public static int cleanupNow(SwarmCloud cloud) {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            return 0;
        }

        OrphanServiceCleaner cleaner = jenkins.getExtensionList(OrphanServiceCleaner.class)
                .stream()
                .findFirst()
                .orElse(null);

        if (cleaner == null) {
            return 0;
        }

        return cleaner.cleanOrphanServices(cloud, jenkins, TaskListener.NULL);
    }
}

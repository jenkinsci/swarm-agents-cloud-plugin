package io.jenkins.plugins.swarmcloud.monitoring;

import com.github.dockerjava.api.model.ResourceRequirements;
import com.github.dockerjava.api.model.ResourceSpecs;
import com.github.dockerjava.api.model.Service;
import com.github.dockerjava.api.model.SwarmNode;
import com.github.dockerjava.api.model.SwarmNodeState;
import com.github.dockerjava.api.model.SwarmNodeStatus;
import com.github.dockerjava.api.model.Task;
import com.github.dockerjava.api.model.TaskState;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.jenkins.plugins.swarmcloud.ServiceLabels;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.model.AsyncPeriodicWork;
import hudson.model.TaskListener;
import hudson.slaves.Cloud;
import io.jenkins.plugins.swarmcloud.SwarmAgentTemplate;
import io.jenkins.plugins.swarmcloud.SwarmCloud;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Monitors Docker Swarm clusters and collects metrics.
 */
@Extension
public class ClusterMonitor extends AsyncPeriodicWork {

    private static final Logger LOGGER = Logger.getLogger(ClusterMonitor.class.getName());
    private static final long RECURRENCE_PERIOD = TimeUnit.SECONDS.toMillis(30);
    private static final Map<String, ClusterStatus> statusCache = new ConcurrentHashMap<>();
    private static volatile long lastUpdate = 0;

    /**
     * Per-template memory of the last observed drift of exactly one agent (counter minus
     * actual Swarm service count), keyed by "cloudName/templateName". Used by
     * {@link #synchronizeTemplateCounters} to distinguish a transient in-flight
     * reservation (resolves within one monitor cycle) from a genuinely leaked
     * increment/decrement (persists), so that a single missed decrement can no longer
     * permanently block the last agent slot until the next restart (#40).
     * Instance-scoped: the monitor is a singleton per Jenkins, so the state does not
     * survive restarts and does not leak across tests.
     */
    private final transient Map<String, Integer> pendingOneAgentDrift = new ConcurrentHashMap<>();

    public ClusterMonitor() {
        super("Swarm Cluster Monitor");
    }

    @Override
    public long getRecurrencePeriod() {
        return RECURRENCE_PERIOD;
    }

    @Override
    @SuppressFBWarnings(value = "ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD",
            justification = "Volatile static field intentionally used for global last update timestamp")
    protected void execute(TaskListener listener) throws IOException, InterruptedException {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) return;

        for (Cloud cloud : jenkins.clouds) {
            if (cloud instanceof SwarmCloud) {
                SwarmCloud swarmCloud = (SwarmCloud) cloud;
                try {
                    ClusterStatus status = collectMetrics(swarmCloud);
                    statusCache.put(swarmCloud.name, status);
                } catch (RuntimeException e) {
                    LOGGER.log(Level.WARNING, "Failed to collect metrics for cloud: " + swarmCloud.name, e);
                    statusCache.put(swarmCloud.name, ClusterStatus.error(swarmCloud.name, e.getMessage()));
                }
            }
        }
        // Update global last update timestamp (volatile field for thread-safe read)
        lastUpdate = System.currentTimeMillis();
    }

    @NonNull
    private ClusterStatus collectMetrics(SwarmCloud cloud) {
        ClusterStatus status = new ClusterStatus(cloud.name);
        try {
            var dockerClient = cloud.getDockerClient();
            status.setSwarmVersion(dockerClient.getSwarmVersion());

            List<SwarmNode> nodes = dockerClient.getDockerClient().listSwarmNodesCmd().exec();
            status.setTotalNodes(nodes.size());

            int readyNodes = 0, managerNodes = 0;
            long totalMemory = 0, totalCpu = 0;

            for (SwarmNode node : nodes) {
                SwarmNodeStatus nodeStatus = node.getStatus();
                SwarmNodeState nodeState = (nodeStatus != null) ? nodeStatus.getState() : null;

                if (SwarmNodeState.READY.equals(nodeState)) {
                    readyNodes++;
                }
                if (node.getManagerStatus() != null) managerNodes++;

                var desc = node.getDescription();
                var resources = (desc != null) ? desc.getResources() : null;
                if (resources != null) {
                    Long memBytes = resources.getMemoryBytes();
                    Long cpuNano = resources.getNanoCPUs();
                    if (memBytes != null) totalMemory += memBytes;
                    if (cpuNano != null) totalCpu += cpuNano;
                }

                NodeInfo nodeInfo = new NodeInfo();
                nodeInfo.setId(node.getId());
                nodeInfo.setHostname(desc != null ? desc.getHostname() : "unknown");
                nodeInfo.setState(nodeState != null ? nodeState.name() : "unknown");
                if (resources != null) {
                    Long memBytes = resources.getMemoryBytes();
                    Long cpuNano = resources.getNanoCPUs();
                    nodeInfo.setMemoryBytes(memBytes != null ? memBytes : 0);
                    nodeInfo.setCpuNanos(cpuNano != null ? cpuNano : 0);
                }
                // Set role based on manager status
                nodeInfo.setRole(node.getManagerStatus() != null ? "manager" : "worker");
                status.addNode(nodeInfo);
            }

            status.setReadyNodes(readyNodes);
            status.setManagerNodes(managerNodes);
            status.setTotalMemory(totalMemory);
            status.setTotalCpu(totalCpu / 1_000_000_000.0);

            List<Service> services = dockerClient.listServicesForCloud(cloud.name);
            List<Service> monitoredServices = new ArrayList<>();

            // Count services by state (not tasks)
            int runningServices = 0, pendingServices = 0, failedServices = 0;
            long reservedMemory = 0;
            long reservedCpuNano = 0;

            for (Service service : services) {
                String serviceId = service.getId();
                if (serviceId == null) continue; // Skip services without ID

                ServiceInfo info = new ServiceInfo();
                info.setId(serviceId);
                var serviceSpec = service.getSpec();
                info.setName(serviceSpec != null ? serviceSpec.getName() : "unknown");

                // Extract template name from service labels
                if (serviceSpec != null) {
                    Map<String, String> labels = serviceSpec.getLabels();
                    if (labels != null) {
                        info.setTemplateName(labels.get(ServiceLabels.TEMPLATE));
                    }
                }

                // Extract created time from service
                var createdAt = service.getCreatedAt();
                if (createdAt != null) {
                    info.setCreatedTime(createdAt.getTime());
                }

                List<Task> tasks = dockerClient.getServiceTasks(serviceId);

                // Determine service state based on task states
                // Priority: running > pending > complete > shutdown > failed
                boolean hasRunning = false, hasPending = false, hasFailed = false;
                boolean hasComplete = false, hasShutdown = false;
                String lastError = null;

                for (Task task : tasks) {
                    if (task.getStatus() != null) {
                        TaskState state = task.getStatus().getState();
                        if (state == TaskState.RUNNING) {
                            hasRunning = true;
                            // Collect resource reservations from running tasks
                            reservedMemory += getTaskReservedMemory(task);
                            reservedCpuNano += getTaskReservedCpu(task);
                        } else if (state == TaskState.PENDING || state == TaskState.ASSIGNED
                                || state == TaskState.ACCEPTED || state == TaskState.PREPARING
                                || state == TaskState.READY || state == TaskState.STARTING) {
                            hasPending = true;
                        } else if (state == TaskState.COMPLETE) {
                            hasComplete = true;
                        } else if (state == TaskState.SHUTDOWN || state == TaskState.ORPHANED) {
                            hasShutdown = true;
                        } else if (state == TaskState.FAILED || state == TaskState.REJECTED) {
                            hasFailed = true;
                            // Capture error message from failed tasks
                            if (task.getStatus().getErr() != null) {
                                lastError = task.getStatus().getErr();
                            }
                        }
                    }
                }

                // Set service state based on priority and count by state
                if (hasRunning) {
                    info.setState("running");
                    runningServices++;
                    // Clear error if service recovered and has running tasks
                    // (Docker Swarm automatically restarts failed tasks)
                } else if (hasPending) {
                    info.setState("pending");
                    pendingServices++;
                    // Show error for pending services if previous task failed
                    if (hasFailed && lastError != null) {
                        info.setError(lastError);
                    }
                } else if (hasComplete) {
                    info.setState("complete");
                } else if (hasShutdown) {
                    info.setState("shutdown");
                } else if (hasFailed) {
                    info.setState("failed");
                    failedServices++;
                    // Only show error when service is actually in failed state
                    if (lastError != null) {
                        info.setError(lastError);
                    }
                } else if (tasks.isEmpty()) {
                    info.setState("stopped");
                } else {
                    info.setState("unknown");
                }

                if (shouldRemoveCompletedOneShot(cloud, service, info.getState())) {
                    LOGGER.log(Level.FINE, "Removing completed one-shot service: {0}", serviceId);
                    try {
                        dockerClient.removeService(serviceId);
                        continue;
                    } catch (RuntimeException e) {
                        // Keep the service visible so operators can see what's stuck.
                        LOGGER.log(Level.WARNING, "Failed to remove completed one-shot service: " + serviceId, e);
                        String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                        info.setError("Failed to remove completed service: " + reason);
                    }
                }

                monitoredServices.add(service);
                status.addService(info);
            }

            // Use service counts instead of task counts
            status.setActiveServices(monitoredServices.size());
            status.setRunningTasks(runningServices);
            status.setPendingTasks(pendingServices);
            status.setFailedTasks(failedServices);
            status.setReservedMemory(reservedMemory);
            status.setReservedCpu(reservedCpuNano / 1_000_000_000.0);
            // For now, usedMemory/usedCpu equals reservedMemory/reservedCpu
            // Real-time stats would require container stats API calls
            status.setUsedMemory(reservedMemory);
            status.setUsedCpu(reservedCpuNano / 1_000_000_000.0);
            status.setMaxAgents(cloud.getMaxConcurrentAgents());
            status.setCurrentAgents(cloud.countCurrentAgents());
            status.setTemplateCount(cloud.getTemplates().size());
            status.setHealthy(true);
            status.setLastUpdate(System.currentTimeMillis());

            // Synchronize template instance counters with actual service count
            synchronizeTemplateCounters(cloud, monitoredServices);

        } catch (RuntimeException e) {
            // Catch runtime exceptions to prevent monitor from failing
            LOGGER.log(Level.SEVERE, "Error collecting metrics for cloud: " + cloud.name, e);
            status.setHealthy(false);
            status.setErrorMessage(e.getMessage());
        }
        return status;
    }

    /**
     * Decides whether a one-shot service whose Docker task reports {@code complete} may be removed.
     *
     * <p>Removal is withheld while the agent's Jenkins node is still registered: its retention
     * strategy owns teardown, and the agent may still be mid-build — e.g. a {@code -noReconnect}
     * one-shot whose channel briefly dropped while the build waits to reconnect. Deleting the
     * service then aborts the build with
     * {@code AgentOfflineException: Unable to create live FilePath ... Connection was broken}.
     * Once the node is gone, the service is a real orphan and is reaped.</p>
     *
     * <p>Package-private for tests.</p>
     */
    boolean shouldRemoveCompletedOneShot(SwarmCloud cloud, Service service, String state) {
        if (!"complete".equals(state) || !isOneShotService(cloud, service)) {
            return false;
        }
        return !isAgentNodeRegistered(service);
    }

    private boolean isAgentNodeRegistered(Service service) {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            return false;
        }
        String agentName = agentNameForService(service);
        return agentName != null && jenkins.getNode(agentName) != null;
    }

    private String agentNameForService(Service service) {
        var spec = service.getSpec();
        if (spec == null) {
            return null;
        }
        Map<String, String> labels = spec.getLabels();
        String agentName = labels != null ? labels.get(ServiceLabels.AGENT_NAME) : null;
        return agentName != null ? agentName : spec.getName();
    }

    // Package-private for tests.
    boolean isOneShotService(SwarmCloud cloud, Service service) {
        var serviceSpec = service.getSpec();
        Map<String, String> labels = serviceSpec != null ? serviceSpec.getLabels() : null;
        if (labels == null) {
            return false;
        }

        String oneShotLabel = labels.get(ServiceLabels.ONE_SHOT);
        if (oneShotLabel != null) {
            return Boolean.parseBoolean(oneShotLabel);
        }

        String templateName = labels.get(ServiceLabels.TEMPLATE);
        if (templateName == null) {
            return false;
        }

        SwarmAgentTemplate template = cloud.getTemplateByName(templateName);
        return template != null && template.resolve().isOneShot();
    }

    @NonNull
    public static ClusterStatus getStatus(@NonNull String cloudName) {
        ClusterStatus status = statusCache.get(cloudName);
        return status != null ? status : ClusterStatus.unknown(cloudName);
    }

    @NonNull
    public static Map<String, ClusterStatus> getAllStatuses() {
        return Map.copyOf(statusCache);
    }

    public static long getLastUpdate() {
        return lastUpdate;
    }

    public static void refreshNow(@NonNull String cloudName) {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) return;

        for (Cloud cloud : jenkins.clouds) {
            if (cloud instanceof SwarmCloud && cloud.name.equals(cloudName)) {
                ClusterMonitor monitor = jenkins.getExtensionList(ClusterMonitor.class)
                        .stream().findFirst().orElse(null);
                if (monitor != null) {
                    try {
                        ClusterStatus status = monitor.collectMetrics((SwarmCloud) cloud);
                        statusCache.put(cloudName, status);
                    } catch (RuntimeException e) {
                        LOGGER.log(Level.WARNING, "Refresh failed: " + cloudName, e);
                    }
                }
                break;
            }
        }
    }

    /**
     * Synchronizes template instance counters with actual running services in Docker Swarm.
     * This ensures the dashboard shows accurate agent counts even after service failures or manual deletions.
     * Uses atomic compare-and-set to avoid race conditions with concurrent provisioning.
     */
    // Package-private for tests.
    void synchronizeTemplateCounters(SwarmCloud cloud, List<Service> services) {
        // Count services per template
        Map<String, Integer> templateServiceCount = new java.util.HashMap<>();

        for (Service service : services) {
            var serviceSpec = service.getSpec();
            if (serviceSpec != null) {
                Map<String, String> labels = serviceSpec.getLabels();
                if (labels != null) {
                    String templateName = labels.get(ServiceLabels.TEMPLATE);
                    if (templateName != null) {
                        templateServiceCount.merge(templateName, 1, Integer::sum);
                    }
                }
            }
        }

        // Update each template's counter to match actual service count
        for (var template : cloud.getTemplates()) {
            String templateName = template.getName();
            int actualCount = templateServiceCount.getOrDefault(templateName, 0);

            // Use atomic update to avoid race conditions with concurrent provisioning
            var counter = template.getCurrentInstancesCounter();
            int currentCount = counter.get();

            if (currentCount == actualCount) {
                // Drift resolved on its own (in-flight reservation completed): clear the
                // confirmation memory so an unrelated later drift starts a fresh window.
                pendingOneAgentDrift.remove(cloud.name + "/" + templateName);
                continue;
            }

            int drift = currentCount - actualCount;
            if (Math.abs(drift) > 1) {
                syncCounter(cloud, template, counter, currentCount, actualCount);
            } else {
                // Drift of exactly 1: this is either a transient in-flight reservation
                // (provision() pre-increments before the service exists in Swarm) or a
                // genuinely leaked increment/decrement. Tolerate it for one monitor cycle
                // (30s); if the same drift is still observed on the next cycle, the
                // reservation window has long passed and the counter is stale - sync it.
                // Without this, a single missed decrement permanently blocked the last
                // agent slot until the next restart (#40).
                String key = cloud.name + "/" + templateName;
                Integer previous = pendingOneAgentDrift.put(key, drift);
                if (previous != null && previous.equals(drift)) {
                    syncCounter(cloud, template, counter, currentCount, actualCount);
                }
            }
        }
    }

    /**
     * Forces the template counter to the actual Swarm service count via atomic
     * compare-and-set and logs the correction.
     */
    private void syncCounter(SwarmCloud cloud, SwarmAgentTemplate template,
            java.util.concurrent.atomic.AtomicInteger counter, int currentCount, int actualCount) {
        if (counter.compareAndSet(currentCount, actualCount)) {
            LOGGER.log(Level.INFO, "Synchronized template ''{0}'' counter: {1} -> {2} (cloud: {3})",
                    new Object[]{template.getName(), currentCount, actualCount, cloud.name});
            pendingOneAgentDrift.remove(cloud.name + "/" + template.getName());
        }
        // If compareAndSet fails, another thread modified the counter - skip this cycle
    }

    /**
     * Extracts reserved memory from a task's resource requirements.
     */
    private long getTaskReservedMemory(Task task) {
        var taskSpec = task.getSpec();
        if (taskSpec == null) return 0;
        ResourceRequirements resources = taskSpec.getResources();
        if (resources == null) return 0;

        // Try reservations first (what Swarm scheduler uses)
        ResourceSpecs reservations = resources.getReservations();
        Long memBytes = (reservations != null) ? reservations.getMemoryBytes() : null;
        if (memBytes != null) {
            return memBytes;
        }

        // Fall back to limits
        ResourceSpecs limits = resources.getLimits();
        memBytes = (limits != null) ? limits.getMemoryBytes() : null;
        if (memBytes != null) {
            return memBytes;
        }

        return 0;
    }

    /**
     * Extracts reserved CPU (in nanoCPUs) from a task's resource requirements.
     */
    private long getTaskReservedCpu(Task task) {
        var taskSpec = task.getSpec();
        if (taskSpec == null) return 0;
        ResourceRequirements resources = taskSpec.getResources();
        if (resources == null) return 0;

        // Try reservations first (what Swarm scheduler uses)
        ResourceSpecs reservations = resources.getReservations();
        Long cpuNano = (reservations != null) ? reservations.getNanoCPUs() : null;
        if (cpuNano != null) {
            return cpuNano;
        }

        // Fall back to limits
        ResourceSpecs limits = resources.getLimits();
        cpuNano = (limits != null) ? limits.getNanoCPUs() : null;
        if (cpuNano != null) {
            return cpuNano;
        }

        return 0;
    }
}

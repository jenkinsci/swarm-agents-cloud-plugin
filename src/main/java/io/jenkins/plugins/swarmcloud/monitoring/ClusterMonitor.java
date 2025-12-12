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
import hudson.Extension;
import hudson.model.AsyncPeriodicWork;
import hudson.model.TaskListener;
import hudson.slaves.Cloud;
import io.jenkins.plugins.swarmcloud.SwarmCloud;
import jenkins.model.Jenkins;

import java.io.IOException;
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

    public ClusterMonitor() {
        super("Swarm Cluster Monitor");
    }

    @Override
    public long getRecurrencePeriod() {
        return RECURRENCE_PERIOD;
    }

    @Override
    protected void execute(TaskListener listener) throws IOException, InterruptedException {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) return;

        for (Cloud cloud : jenkins.clouds) {
            if (cloud instanceof SwarmCloud) {
                SwarmCloud swarmCloud = (SwarmCloud) cloud;
                try {
                    ClusterStatus status = collectMetrics(swarmCloud);
                    statusCache.put(swarmCloud.name, status);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to collect metrics for cloud: " + swarmCloud.name, e);
                    statusCache.put(swarmCloud.name, ClusterStatus.error(swarmCloud.name, e.getMessage()));
                }
            }
        }
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

            List<Service> services = dockerClient.listJenkinsServices();
            status.setActiveServices(services.size());

            // Count services by state (not tasks)
            int runningServices = 0, pendingServices = 0, failedServices = 0;
            long reservedMemory = 0;
            long reservedCpuNano = 0;

            for (Service service : services) {
                ServiceInfo info = new ServiceInfo();
                info.setId(service.getId());
                var serviceSpec = service.getSpec();
                info.setName(serviceSpec != null ? serviceSpec.getName() : "unknown");

                // Extract template name from service labels
                if (serviceSpec != null && serviceSpec.getLabels() != null) {
                    info.setTemplateName(serviceSpec.getLabels().get("jenkins.template"));
                }

                // Extract created time from service
                if (service.getCreatedAt() != null) {
                    info.setCreatedTime(service.getCreatedAt().getTime());
                }

                List<Task> tasks = dockerClient.getServiceTasks(service.getId());
                if (tasks == null) tasks = java.util.Collections.emptyList();

                // Determine service state based on task states
                // Priority: running > pending > complete > shutdown > failed
                boolean hasRunning = false, hasPending = false, hasFailed = false;
                boolean hasComplete = false, hasShutdown = false;

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
                            // Capture error message
                            if (task.getStatus().getErr() != null) {
                                info.setError(task.getStatus().getErr());
                            }
                        }
                    }
                }

                // Set service state based on priority and count by state
                if (hasRunning) {
                    info.setState("running");
                    runningServices++;
                } else if (hasPending) {
                    info.setState("pending");
                    pendingServices++;
                } else if (hasComplete) {
                    info.setState("complete");
                } else if (hasShutdown) {
                    info.setState("shutdown");
                } else if (hasFailed) {
                    info.setState("failed");
                    failedServices++;
                } else if (tasks.isEmpty()) {
                    info.setState("stopped");
                } else {
                    info.setState("unknown");
                }

                status.addService(info);
            }

            // Use service counts instead of task counts
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
            synchronizeTemplateCounters(cloud, services);

        } catch (Exception e) {
            status.setHealthy(false);
            status.setErrorMessage(e.getMessage());
        }
        return status;
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
                    } catch (Exception e) {
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
     */
    private void synchronizeTemplateCounters(SwarmCloud cloud, List<Service> services) {
        // Count services per template
        Map<String, Integer> templateServiceCount = new java.util.HashMap<>();

        for (Service service : services) {
            var serviceSpec = service.getSpec();
            if (serviceSpec != null && serviceSpec.getLabels() != null) {
                String templateName = serviceSpec.getLabels().get("jenkins.template");
                if (templateName != null) {
                    templateServiceCount.merge(templateName, 1, Integer::sum);
                }
            }
        }

        // Update each template's counter to match actual service count
        for (var template : cloud.getTemplates()) {
            String templateName = template.getName();
            int actualCount = templateServiceCount.getOrDefault(templateName, 0);
            int currentCount = template.getCurrentInstances();

            if (currentCount != actualCount) {
                LOGGER.log(Level.INFO, "Synchronizing template ''{0}'' counter: {1} -> {2}",
                        new Object[]{templateName, currentCount, actualCount});
                template.setCurrentInstances(actualCount);
            }
        }
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

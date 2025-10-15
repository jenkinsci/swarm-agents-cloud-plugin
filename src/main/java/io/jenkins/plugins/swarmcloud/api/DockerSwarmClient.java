package io.jenkins.plugins.swarmcloud.api;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateServiceResponse;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.jenkins.plugins.swarmcloud.SwarmAgentTemplate;

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Client for Docker Swarm API operations.
 */
public class DockerSwarmClient implements Closeable {

    private static final Logger LOGGER = Logger.getLogger(DockerSwarmClient.class.getName());

    private final DockerClient dockerClient;
    private final String dockerHost;

    public DockerSwarmClient(@NonNull String dockerHost, @Nullable String credentialsId) {
        this.dockerHost = dockerHost;

        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerHost)
                .build();

        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45))
                .build();

        this.dockerClient = DockerClientImpl.getInstance(config, httpClient);
    }

    @NonNull
    public String getSwarmVersion() {
        try {
            Swarm swarm = dockerClient.inspectSwarmCmd().exec();
            SwarmVersion version = swarm.getVersion();
            return version != null ? String.valueOf(version.getIndex()) : "unknown";
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get Swarm version", e);
            return "unknown";
        }
    }

    public int getNodeCount() {
        try {
            List<SwarmNode> nodes = dockerClient.listSwarmNodesCmd().exec();
            return nodes.size();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get node count", e);
            return 0;
        }
    }

    @NonNull
    public String createService(@NonNull String agentName,
                                @NonNull SwarmAgentTemplate template,
                                @NonNull String jenkinsUrl,
                                @Nullable String networkName) {

        LOGGER.log(Level.INFO, "Creating service for agent: {0}, template: {1}",
                new Object[]{agentName, template.getName()});

        ContainerSpec containerSpec = new ContainerSpec()
                .withImage(template.getImage())
                .withEnv(buildEnvironmentVariables(template, jenkinsUrl, agentName));

        List<Mount> mounts = buildMounts(template);
        if (!mounts.isEmpty()) {
            containerSpec.withMounts(mounts);
        }

        if (template.getCommand() != null && !template.getCommand().isBlank()) {
            containerSpec.withCommand(template.getCommand().split("\\s+"));
        }

        TaskSpec taskSpec = new TaskSpec().withContainerSpec(containerSpec);

        ResourceRequirements resources = buildResourceRequirements(template);
        if (resources != null) {
            taskSpec.withResources(resources);
        }

        Placement placement = buildPlacement(template);
        if (placement != null) {
            taskSpec.withPlacement(placement);
        }

        ServiceSpec serviceSpec = new ServiceSpec()
                .withName(agentName)
                .withTaskTemplate(taskSpec)
                .withMode(new ServiceModeConfig().withReplicated(
                        new ServiceReplicatedModeOptions().withReplicas(1L)));

        Map<String, String> labels = new HashMap<>();
        labels.put("jenkins.agent", "true");
        labels.put("jenkins.template", template.getName());
        labels.put("jenkins.cloud", "swarm-agents-cloud");
        serviceSpec.withLabels(labels);

        if (networkName != null && !networkName.isBlank()) {
            serviceSpec.withNetworks(List.of(new NetworkAttachmentConfig().withTarget(networkName)));
        }

        CreateServiceResponse response = dockerClient.createServiceCmd(serviceSpec).exec();
        String serviceId = response.getId();

        LOGGER.log(Level.INFO, "Created service: {0} with ID: {1}", new Object[]{agentName, serviceId});
        template.incrementInstances();

        return serviceId;
    }

    public void removeService(@NonNull String serviceId) {
        LOGGER.log(Level.INFO, "Removing service: {0}", serviceId);
        try {
            dockerClient.removeServiceCmd(serviceId).exec();
            LOGGER.log(Level.INFO, "Removed service: {0}", serviceId);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to remove service: " + serviceId, e);
            throw new RuntimeException("Failed to remove service: " + serviceId, e);
        }
    }

    @Nullable
    public Service getService(@NonNull String serviceId) {
        try {
            return dockerClient.inspectServiceCmd(serviceId).exec();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to inspect service: " + serviceId, e);
            return null;
        }
    }

    @NonNull
    public List<Service> listJenkinsServices() {
        try {
            return dockerClient.listServicesCmd()
                    .withLabelFilter(Map.of("jenkins.agent", "true"))
                    .exec();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to list Jenkins services", e);
            return List.of();
        }
    }

    @NonNull
    public SwarmMetrics getClusterMetrics() {
        SwarmMetrics metrics = new SwarmMetrics();
        try {
            List<SwarmNode> nodes = dockerClient.listSwarmNodesCmd().exec();
            metrics.setTotalNodes(nodes.size());

            int readyNodes = 0;
            for (SwarmNode node : nodes) {
                if (node.getStatus() != null &&
                        node.getStatus().getState() == SwarmNodeState.READY) {
                    readyNodes++;
                }
            }
            metrics.setReadyNodes(readyNodes);

            List<Service> services = listJenkinsServices();
            metrics.setActiveAgents(services.size());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get cluster metrics", e);
        }
        return metrics;
    }

    private List<String> buildEnvironmentVariables(SwarmAgentTemplate template,
                                                    String jenkinsUrl,
                                                    String agentName) {
        List<String> env = new ArrayList<>();
        env.add("JENKINS_URL=" + jenkinsUrl);
        env.add("JENKINS_AGENT_NAME=" + agentName);
        env.add("JENKINS_WEB_SOCKET=true");

        for (SwarmAgentTemplate.EnvironmentVariable var : template.getEnvironmentVariables()) {
            env.add(var.getName() + "=" + var.getValue());
        }
        return env;
    }

    private List<Mount> buildMounts(SwarmAgentTemplate template) {
        List<Mount> mounts = new ArrayList<>();
        for (SwarmAgentTemplate.MountConfig config : template.getMounts()) {
            Mount mount = new Mount()
                    .withSource(config.getSource())
                    .withTarget(config.getTarget())
                    .withReadOnly(config.isReadOnly());

            switch (config.getType().toLowerCase()) {
                case "bind":
                    mount.withType(MountType.BIND);
                    break;
                case "volume":
                    mount.withType(MountType.VOLUME);
                    break;
                case "tmpfs":
                    mount.withType(MountType.TMPFS);
                    break;
                default:
                    mount.withType(MountType.VOLUME);
            }
            mounts.add(mount);
        }
        return mounts;
    }

    @Nullable
    private ResourceRequirements buildResourceRequirements(SwarmAgentTemplate template) {
        ResourceSpecs limits = null;
        ResourceSpecs reservations = null;

        if (template.getCpuLimit() != null || template.getMemoryLimit() != null) {
            limits = new ResourceSpecs();
            if (template.getCpuLimit() != null) {
                limits.withNanoCPUs(parseNanoCPUs(template.getCpuLimit()));
            }
            if (template.getMemoryLimit() != null) {
                limits.withMemoryBytes(parseMemoryBytes(template.getMemoryLimit()));
            }
        }

        if (template.getCpuReservation() != null || template.getMemoryReservation() != null) {
            reservations = new ResourceSpecs();
            if (template.getCpuReservation() != null) {
                reservations.withNanoCPUs(parseNanoCPUs(template.getCpuReservation()));
            }
            if (template.getMemoryReservation() != null) {
                reservations.withMemoryBytes(parseMemoryBytes(template.getMemoryReservation()));
            }
        }

        if (limits != null || reservations != null) {
            ResourceRequirements resources = new ResourceRequirements();
            if (limits != null) resources.withLimits(limits);
            if (reservations != null) resources.withReservations(reservations);
            return resources;
        }
        return null;
    }

    @Nullable
    private Placement buildPlacement(SwarmAgentTemplate template) {
        List<String> constraints = template.getPlacementConstraints();
        if (constraints.isEmpty()) return null;
        return new Placement().withConstraints(constraints);
    }

    private long parseNanoCPUs(String cpu) {
        double cpuValue = Double.parseDouble(cpu);
        return (long) (cpuValue * 1_000_000_000);
    }

    private long parseMemoryBytes(String memory) {
        memory = memory.toLowerCase().trim();
        long multiplier = 1;

        if (memory.endsWith("g")) {
            multiplier = 1024L * 1024L * 1024L;
            memory = memory.substring(0, memory.length() - 1);
        } else if (memory.endsWith("m")) {
            multiplier = 1024L * 1024L;
            memory = memory.substring(0, memory.length() - 1);
        } else if (memory.endsWith("k")) {
            multiplier = 1024L;
            memory = memory.substring(0, memory.length() - 1);
        } else if (memory.endsWith("b")) {
            memory = memory.substring(0, memory.length() - 1);
        }
        return Long.parseLong(memory) * multiplier;
    }

    @Override
    public void close() throws IOException {
        if (dockerClient != null) {
            dockerClient.close();
        }
    }
}

package io.jenkins.plugins.swarmcloud.api;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateServiceResponse;
import com.github.dockerjava.api.command.LogSwarmObjectCmd;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.core.SSLConfig;
import com.github.dockerjava.core.command.LogContainerResultCallback;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.jenkins.plugins.swarmcloud.SwarmAgentTemplate;
import io.jenkins.plugins.swarmcloud.SwarmComputerLauncher;
import io.jenkins.plugins.swarmcloud.SwarmSecretConfig;
import io.jenkins.plugins.swarmcloud.config.DockerCredentialsHelper;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerCredentials;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
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

        DefaultDockerClientConfig.Builder configBuilder = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerHost)
                .withDockerTlsVerify(false);  // Disable TLS by default to ignore system env variables

        SSLConfig sslConfig = null;

        // Configure TLS if credentials are provided
        if (credentialsId != null && !credentialsId.isBlank()) {
            DockerServerCredentials credentials = DockerCredentialsHelper.lookupCredentials(credentialsId, dockerHost);
            if (credentials != null) {
                LOGGER.log(Level.INFO, "Configuring TLS with credentials: {0}", credentialsId);
                try {
                    sslConfig = createSslConfig(credentials);
                    configBuilder.withDockerTlsVerify(true);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to configure TLS, falling back to insecure connection", e);
                }
            } else {
                LOGGER.log(Level.WARNING, "Credentials not found: {0}", credentialsId);
            }
        }

        DockerClientConfig config = configBuilder.build();

        ApacheDockerHttpClient.Builder httpClientBuilder = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45));

        // Apply SSL config if available
        if (sslConfig != null) {
            httpClientBuilder.sslConfig(sslConfig);
        } else {
            httpClientBuilder.sslConfig(config.getSSLConfig());
        }

        DockerHttpClient httpClient = httpClientBuilder.build();
        this.dockerClient = DockerClientImpl.getInstance(config, httpClient);
    }

    /**
     * Creates an SSLConfig from Docker server credentials.
     */
    @Nullable
    private SSLConfig createSslConfig(DockerServerCredentials credentials) throws Exception {
        String caCert = DockerCredentialsHelper.getCaCertificate(credentials);
        String clientCert = DockerCredentialsHelper.getClientCertificate(credentials);
        String clientKey = DockerCredentialsHelper.getClientKey(credentials);

        if (caCert == null || clientCert == null || clientKey == null) {
            LOGGER.log(Level.WARNING, "Incomplete TLS credentials - missing certificate or key");
            return null;
        }

        return new SSLConfig() {
            @Override
            public SSLContext getSSLContext() {
                try {
                    // Load CA certificate
                    CertificateFactory cf = CertificateFactory.getInstance("X.509");
                    X509Certificate caCertificate = (X509Certificate) cf.generateCertificate(
                            new ByteArrayInputStream(caCert.getBytes(StandardCharsets.UTF_8)));

                    // Load client certificate
                    X509Certificate clientCertificate = (X509Certificate) cf.generateCertificate(
                            new ByteArrayInputStream(clientCert.getBytes(StandardCharsets.UTF_8)));

                    // Load client private key
                    PrivateKey privateKey = loadPrivateKey(clientKey);

                    // Create trust store with CA cert
                    KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
                    trustStore.load(null, null);
                    trustStore.setCertificateEntry("ca", caCertificate);

                    TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                    tmf.init(trustStore);

                    // Create key store with client cert and key
                    KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
                    keyStore.load(null, null);
                    keyStore.setKeyEntry("client", privateKey, "docker".toCharArray(),
                            new Certificate[]{clientCertificate});

                    KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                    kmf.init(keyStore, "docker".toCharArray());

                    // Create SSL context
                    SSLContext sslContext = SSLContext.getInstance("TLS");
                    sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

                    return sslContext;
                } catch (Exception e) {
                    throw new RuntimeException("Failed to create SSL context", e);
                }
            }
        };
    }

    /**
     * Loads a private key from PEM format.
     */
    private PrivateKey loadPrivateKey(String pemKey) throws Exception {
        String keyContent = pemKey
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] keyBytes = Base64.getDecoder().decode(keyContent);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(keySpec);
    }

    @NonNull
    public String getSwarmVersion() {
        try {
            Swarm swarm = dockerClient.inspectSwarmCmd().exec();
            ResourceVersion version = swarm.getVersion();
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

    /**
     * Creates a Docker Swarm service for a Jenkins agent.
     *
     * @param agentName   Unique name for the agent
     * @param template    Agent template configuration
     * @param jenkinsUrl  URL for agents to connect to Jenkins
     * @param secret      Secret for agent authentication
     * @param networkName Docker network to attach (optional)
     * @return Service ID
     */
    @NonNull
    public String createService(@NonNull String agentName,
                                @NonNull SwarmAgentTemplate template,
                                @NonNull String jenkinsUrl,
                                @NonNull String secret,
                                @Nullable String networkName) {

        LOGGER.log(Level.INFO, "Creating service for agent: {0}, template: {1}, image: {2}",
                new Object[]{agentName, template.getName(), template.getImage()});

        // Build environment variables with secret
        List<String> env = buildEnvironmentVariables(template, jenkinsUrl, agentName, secret);

        ContainerSpec containerSpec = new ContainerSpec()
                .withImage(template.getImage())
                .withEnv(env);

        // Add mounts
        List<Mount> mounts = buildMounts(template);
        if (!mounts.isEmpty()) {
            containerSpec.withMounts(mounts);
        }

        // Add command if specified, otherwise rely on image default (inbound-agent)
        if (template.getCommand() != null && !template.getCommand().isBlank()) {
            containerSpec.withCommand(List.of(template.getCommand().split("\\s+")));
        }

        // Add working directory
        containerSpec.withDir(template.getRemoteFs());

        // Add health check if configured
        if (template.hasHealthCheck()) {
            HealthCheck healthCheck = new HealthCheck()
                    .withTest(List.of("CMD-SHELL", template.getHealthCheckCommand()))
                    .withInterval(Duration.ofSeconds(template.getHealthCheckIntervalSeconds()).toNanos())
                    .withTimeout(Duration.ofSeconds(template.getHealthCheckTimeoutSeconds()).toNanos())
                    .withRetries(template.getHealthCheckRetries());
            containerSpec.withHealthCheck(healthCheck);
        }

        // Add secrets if configured
        List<ContainerSpecSecret> secretRefs = buildSecretReferences(template);
        if (!secretRefs.isEmpty()) {
            containerSpec.withSecrets(secretRefs);
        }

        // Add advanced container options (#120)
        applyAdvancedContainerOptions(containerSpec, template);

        // Build task template
        TaskSpec taskSpec = new TaskSpec().withContainerSpec(containerSpec);

        // Add resource constraints
        ResourceRequirements resources = buildResourceRequirements(template);
        if (resources != null) {
            taskSpec.withResources(resources);
        }

        // Add placement constraints
        ServicePlacement placement = buildPlacement(template);
        if (placement != null) {
            taskSpec.withPlacement(placement);
        }

        // Configure restart policy - on-failure with limited retries
        taskSpec.withRestartPolicy(new ServiceRestartPolicy()
                .withCondition(ServiceRestartCondition.ON_FAILURE)
                .withMaxAttempts(3L)
                .withDelay(5_000_000_000L)); // 5 seconds in nanoseconds

        // Build service spec
        ServiceSpec serviceSpec = new ServiceSpec()
                .withName(agentName)
                .withTaskTemplate(taskSpec)
                .withMode(new ServiceModeConfig().withReplicated(
                        new ServiceReplicatedModeOptions().withReplicas(1)));

        // Add labels for identification and management
        Map<String, String> labels = new HashMap<>();
        labels.put("jenkins.agent", "true");
        labels.put("jenkins.agent.name", agentName);
        labels.put("jenkins.template", template.getName());
        labels.put("jenkins.cloud", "swarm-agents-cloud");
        labels.put("jenkins.created", String.valueOf(System.currentTimeMillis()));
        serviceSpec.withLabels(labels);

        // Add network
        if (networkName != null && !networkName.isBlank()) {
            NetworkAttachmentConfig networkConfig = new NetworkAttachmentConfig()
                    .withTarget(networkName);

            // Add network aliases if configured
            List<String> aliases = template.getNetworkAliases();
            if (!aliases.isEmpty()) {
                networkConfig.withAliases(aliases);
            }

            serviceSpec.withNetworks(List.of(networkConfig));
        }

        // Create the service
        CreateServiceResponse response = dockerClient.createServiceCmd(serviceSpec).exec();
        String serviceId = response.getId();

        LOGGER.log(Level.INFO, "Created service: {0} with ID: {1}", new Object[]{agentName, serviceId});
        template.incrementInstances();

        return serviceId;
    }

    /**
     * Overloaded method for backwards compatibility.
     */
    @NonNull
    public String createService(@NonNull String agentName,
                                @NonNull SwarmAgentTemplate template,
                                @NonNull String jenkinsUrl,
                                @Nullable String networkName) {
        String secret = SwarmComputerLauncher.getAgentSecret(agentName);
        return createService(agentName, template, jenkinsUrl, secret, networkName);
    }

    /**
     * Removes a Docker Swarm service.
     */
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

    /**
     * Gets information about a service.
     */
    @Nullable
    public Service getService(@NonNull String serviceId) {
        try {
            return dockerClient.inspectServiceCmd(serviceId).exec();
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to inspect service: " + serviceId, e);
            return null;
        }
    }

    /**
     * Gets logs from a service.
     */
    @Nullable
    public String getServiceLogs(@NonNull String serviceId, int tailLines) {
        try {
            StringBuilder logs = new StringBuilder();

            dockerClient.logServiceCmd(serviceId)
                    .withStdout(true)
                    .withStderr(true)
                    .withTail(tailLines)
                    .withTimestamps(true)
                    .exec(new LogContainerResultCallback() {
                        @Override
                        public void onNext(Frame frame) {
                            logs.append(new String(frame.getPayload()));
                        }
                    })
                    .awaitCompletion(10, TimeUnit.SECONDS);

            return logs.toString();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get service logs: " + serviceId, e);
            return null;
        }
    }

    /**
     * Lists all Jenkins agent services.
     */
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

    /**
     * Lists services for a specific cloud.
     */
    @NonNull
    public List<Service> listServicesForCloud(@NonNull String cloudName) {
        try {
            return dockerClient.listServicesCmd()
                    .withLabelFilter(Map.of(
                            "jenkins.agent", "true",
                            "jenkins.cloud", cloudName
                    ))
                    .exec();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to list services for cloud: " + cloudName, e);
            return List.of();
        }
    }

    /**
     * Gets tasks for a service.
     */
    @NonNull
    public List<Task> getServiceTasks(@NonNull String serviceId) {
        try {
            return dockerClient.listTasksCmd()
                    .withServiceFilter(serviceId)
                    .exec();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get tasks for service: " + serviceId, e);
            return List.of();
        }
    }

    /**
     * Checks if a service is running (has at least one running task).
     */
    public boolean isServiceRunning(@NonNull String serviceId) {
        List<Task> tasks = getServiceTasks(serviceId);
        for (Task task : tasks) {
            TaskStatus status = task.getStatus();
            if (status != null && status.getState() == TaskState.RUNNING) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets cluster metrics.
     */
    @NonNull
    public SwarmMetrics getClusterMetrics() {
        SwarmMetrics metrics = new SwarmMetrics();
        try {
            // Get nodes
            List<SwarmNode> nodes = dockerClient.listSwarmNodesCmd().exec();
            metrics.setTotalNodes(nodes.size());

            int readyNodes = 0;
            long totalMemory = 0;
            double totalCpu = 0;

            for (SwarmNode node : nodes) {
                if (node.getStatus() != null &&
                        node.getStatus().getState() == SwarmNodeState.READY) {
                    readyNodes++;
                }

                // Get node resources
                SwarmNodeDescription desc = node.getDescription();
                if (desc != null && desc.getResources() != null) {
                    SwarmNodeResources resources = desc.getResources();
                    if (resources.getMemoryBytes() != null) {
                        totalMemory += resources.getMemoryBytes();
                    }
                    if (resources.getNanoCPUs() != null) {
                        totalCpu += resources.getNanoCPUs() / 1_000_000_000.0;
                    }
                }
            }
            metrics.setReadyNodes(readyNodes);
            metrics.setTotalMemory(totalMemory);
            metrics.setTotalCpu(totalCpu);

            // Get Jenkins services
            List<Service> services = listJenkinsServices();
            metrics.setActiveAgents(services.size());

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get cluster metrics", e);
        }
        return metrics;
    }

    /**
     * Builds environment variables for the agent container.
     */
    private List<String> buildEnvironmentVariables(SwarmAgentTemplate template,
                                                    String jenkinsUrl,
                                                    String agentName,
                                                    String secret) {
        List<String> env = new ArrayList<>();

        // Core Jenkins agent environment variables
        env.add("JENKINS_URL=" + jenkinsUrl);
        env.add("JENKINS_AGENT_NAME=" + agentName);
        env.add("JENKINS_SECRET=" + secret);
        env.add("JENKINS_WEB_SOCKET=true");
        env.add("JENKINS_AGENT_WORKDIR=" + template.getRemoteFs());

        // For jenkins/inbound-agent image compatibility
        env.add("JENKINS_DIRECT_CONNECTION=" +
                jenkinsUrl.replace("http://", "").replace("https://", "").replaceAll("/$", ""));

        // Add template-specific environment variables
        for (SwarmAgentTemplate.EnvironmentVariable var : template.getEnvironmentVariables()) {
            env.add(var.getName() + "=" + var.getValue());
        }

        return env;
    }

    /**
     * Builds mounts from template configuration.
     */
    private List<Mount> buildMounts(SwarmAgentTemplate template) {
        List<Mount> mounts = new ArrayList<>();
        for (SwarmAgentTemplate.MountConfig config : template.getMounts()) {
            Mount mount = new Mount()
                    .withTarget(config.getTarget())
                    .withReadOnly(config.isReadOnly());

            switch (config.getType().toLowerCase()) {
                case "bind":
                    mount.withType(MountType.BIND);
                    mount.withSource(config.getSource());
                    break;
                case "volume":
                    mount.withType(MountType.VOLUME);
                    mount.withSource(config.getSource());
                    break;
                case "tmpfs":
                    mount.withType(MountType.TMPFS);
                    // tmpfs doesn't have a source
                    break;
                default:
                    mount.withType(MountType.VOLUME);
                    mount.withSource(config.getSource());
            }
            mounts.add(mount);
        }
        return mounts;
    }

    /**
     * Builds resource requirements from template.
     */
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

    /**
     * Builds placement constraints from template.
     */
    @Nullable
    private ServicePlacement buildPlacement(SwarmAgentTemplate template) {
        List<String> constraints = template.getPlacementConstraints();
        if (constraints.isEmpty()) return null;
        return new ServicePlacement().withConstraints(constraints);
    }

    /**
     * Builds secret references from template configuration.
     */
    @NonNull
    private List<ContainerSpecSecret> buildSecretReferences(SwarmAgentTemplate template) {
        List<ContainerSpecSecret> refs = new ArrayList<>();

        for (SwarmSecretConfig secretConfig : template.getSecrets()) {
            try {
                String secretId = findSecretId(secretConfig.getSecretName());
                if (secretId == null) {
                    LOGGER.log(Level.WARNING, "Secret not found: {0}", secretConfig.getSecretName());
                    continue;
                }

                ContainerSpecSecret ref = new ContainerSpecSecret()
                        .withSecretId(secretId)
                        .withSecretName(secretConfig.getSecretName())
                        .withFile(new ContainerSpecFile()
                                .withName(secretConfig.getEffectiveFileName())
                                .withUid(secretConfig.getUid() != null ? secretConfig.getUid() : "0")
                                .withGid(secretConfig.getGid() != null ? secretConfig.getGid() : "0")
                                .withMode(secretConfig.getFileModeAsLong() != null ?
                                        secretConfig.getFileModeAsLong() : 0444L));

                refs.add(ref);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to configure secret: " + secretConfig.getSecretName(), e);
            }
        }

        return refs;
    }

    @Nullable
    private String findSecretId(String secretName) {
        try {
            List<Secret> secrets = dockerClient.listSecretsCmd()
                    .withNameFilter(List.of(secretName))
                    .exec();

            // The name filter already matches by name, so return first result
            if (!secrets.isEmpty()) {
                return secrets.get(0).getId();
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to find secret: " + secretName, e);
        }
        return null;
    }

    @NonNull
    public String createSecret(@NonNull String name, @NonNull byte[] data) {
        SecretSpec spec = new SecretSpec()
                .withName(name)
                .withData(java.util.Base64.getEncoder().encodeToString(data));
        return dockerClient.createSecretCmd(spec).exec().getId();
    }

    public void deleteSecret(@NonNull String secretId) {
        try {
            dockerClient.removeSecretCmd(secretId).exec();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to delete secret: " + secretId, e);
        }
    }

    @NonNull
    public List<Secret> listSecrets() {
        try {
            return dockerClient.listSecretsCmd().exec();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to list secrets", e);
            return List.of();
        }
    }

    /**
     * Applies advanced container options to the container spec (#120).
     */
    private void applyAdvancedContainerOptions(ContainerSpec containerSpec, SwarmAgentTemplate template) {
        // Capabilities
        List<String> capAdd = template.getCapAdd();
        List<String> capDrop = template.getCapDrop();
        if (!capAdd.isEmpty() || !capDrop.isEmpty()) {
            ContainerSpecPrivileges privileges = new ContainerSpecPrivileges();
            // Note: docker-java uses different approach for capabilities
            // They are set via SELinuxContext or credential spec
            // For Swarm mode, capabilities are limited - using privileged as workaround
            if (!capAdd.isEmpty()) {
                LOGGER.log(Level.FINE, "Adding capabilities: {0}", capAdd);
            }
            if (!capDrop.isEmpty()) {
                LOGGER.log(Level.FINE, "Dropping capabilities: {0}", capDrop);
            }
        }

        // Privileged mode
        if (template.isPrivileged()) {
            containerSpec.withPrivileges(new ContainerSpecPrivileges());
            LOGGER.log(Level.FINE, "Running container in privileged mode");
        }

        // User
        String user = template.getUser();
        if (user != null && !user.isBlank()) {
            containerSpec.withUser(user);
        }

        // Hostname
        String hostname = template.getHostname();
        if (hostname != null && !hostname.isBlank()) {
            containerSpec.withHostname(hostname);
        }

        // DNS configuration
        List<String> dnsServers = template.getDnsServers();
        List<String> dnsOptions = template.getDnsOptions();
        List<String> dnsSearch = template.getDnsSearch();
        if (!dnsServers.isEmpty() || !dnsOptions.isEmpty() || !dnsSearch.isEmpty()) {
            ContainerDNSConfig dnsConfig = new ContainerDNSConfig();
            if (!dnsServers.isEmpty()) {
                dnsConfig.withNameservers(dnsServers);
            }
            if (!dnsOptions.isEmpty()) {
                dnsConfig.withOptions(dnsOptions);
            }
            if (!dnsSearch.isEmpty()) {
                dnsConfig.withSearch(dnsSearch);
            }
            containerSpec.withDnsConfig(dnsConfig);
        }

        // Stop signal
        String stopSignal = template.getStopSignal();
        if (stopSignal != null && !stopSignal.isBlank()) {
            containerSpec.withStopSignal(stopSignal);
        }

        // Stop grace period
        long stopGracePeriod = template.getStopGracePeriod();
        if (stopGracePeriod > 0) {
            containerSpec.withStopGracePeriod(stopGracePeriod * 1_000_000_000L); // Convert to nanoseconds
        }

        // Sysctls - set via Privileges in Swarm mode
        List<String> sysctls = template.getSysctls();
        if (!sysctls.isEmpty()) {
            LOGGER.log(Level.FINE, "Sysctls configured: {0}. Note: Limited Swarm support.", sysctls);
            // Sysctls in Swarm mode have limited support and require host configuration
        }

        // Security Profiles (Seccomp, AppArmor)
        applySecurityProfiles(containerSpec, template);

        // Generic Resources (GPU) - logged for future docker-java support
        var genericResources = template.getGenericResources();
        if (!genericResources.isEmpty()) {
            LOGGER.log(Level.INFO, "Generic resources requested: {0}. " +
                    "Note: Requires Docker daemon configured with node-generic-resources.", genericResources);
            // docker-java 3.3.5 doesn't fully support generic resources in ResourceSpecs.
            // GPU support requires:
            // 1. Docker daemon configured with node-generic-resources in daemon.json
            // 2. nvidia-container-runtime with swarm-resource enabled
            // The resources will be used if ServiceSpec API is extended in future docker-java versions.
        }
    }

    /**
     * Applies security profiles (Seccomp, AppArmor) to container spec.
     * Available since Docker Engine 19.03+
     */
    private void applySecurityProfiles(ContainerSpec containerSpec, SwarmAgentTemplate template) {
        String seccompProfile = template.getSeccompProfile();
        String apparmorProfile = template.getApparmorProfile();

        if ((seccompProfile == null || seccompProfile.isBlank()) &&
            (apparmorProfile == null || apparmorProfile.isBlank())) {
            return;
        }

        ContainerSpecPrivileges privileges = containerSpec.getPrivileges();
        if (privileges == null) {
            privileges = new ContainerSpecPrivileges();
        }

        // Seccomp profile
        if (seccompProfile != null && !seccompProfile.isBlank()) {
            LOGGER.log(Level.FINE, "Setting Seccomp profile: {0}", seccompProfile);
            // docker-java ContainerSpecPrivileges doesn't have direct seccomp support
            // In production, this would be set via raw API or custom ContainerSpecPrivileges extension
            // For now, log and use default Docker behavior
            if (!"default".equalsIgnoreCase(seccompProfile) && !"unconfined".equalsIgnoreCase(seccompProfile)) {
                LOGGER.log(Level.WARNING, "Custom Seccomp profile '{0}' requires Docker Engine configuration. " +
                        "Using default profile.", seccompProfile);
            }
        }

        // AppArmor profile
        if (apparmorProfile != null && !apparmorProfile.isBlank()) {
            LOGGER.log(Level.FINE, "Setting AppArmor profile: {0}", apparmorProfile);
            // Similar limitation as Seccomp - docker-java has limited direct support
            // AppArmor profiles must be pre-loaded on Docker hosts
            if (!"runtime/default".equalsIgnoreCase(apparmorProfile) && !"unconfined".equalsIgnoreCase(apparmorProfile)) {
                LOGGER.log(Level.INFO, "Custom AppArmor profile '{0}' must be loaded on Docker hosts.", apparmorProfile);
            }
        }

        containerSpec.withPrivileges(privileges);
    }

    /**
     * Parses CPU value to nanoCPUs.
     */
    private long parseNanoCPUs(String cpu) {
        double cpuValue = Double.parseDouble(cpu.trim());
        return (long) (cpuValue * 1_000_000_000);
    }

    /**
     * Parses memory string to bytes.
     */
    private long parseMemoryBytes(String memory) {
        memory = memory.toLowerCase().trim();
        long multiplier = 1;

        if (memory.endsWith("g") || memory.endsWith("gb")) {
            multiplier = 1024L * 1024L * 1024L;
            memory = memory.replaceAll("[gb]+$", "");
        } else if (memory.endsWith("m") || memory.endsWith("mb")) {
            multiplier = 1024L * 1024L;
            memory = memory.replaceAll("[mb]+$", "");
        } else if (memory.endsWith("k") || memory.endsWith("kb")) {
            multiplier = 1024L;
            memory = memory.replaceAll("[kb]+$", "");
        } else if (memory.endsWith("b")) {
            memory = memory.substring(0, memory.length() - 1);
        }

        return Long.parseLong(memory.trim()) * multiplier;
    }

    /**
     * Gets the underlying Docker client for advanced operations.
     */
    public DockerClient getDockerClient() {
        return dockerClient;
    }

    public String getDockerHost() {
        return dockerHost;
    }

    @Override
    public void close() throws IOException {
        if (dockerClient != null) {
            dockerClient.close();
        }
    }
}

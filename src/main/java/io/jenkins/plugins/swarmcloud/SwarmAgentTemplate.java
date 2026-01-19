package io.jenkins.plugins.swarmcloud;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Template for Docker Swarm agents.
 * Defines the configuration for agent containers.
 */
public class SwarmAgentTemplate extends AbstractDescribableImpl<SwarmAgentTemplate> {

    private static final Logger LOGGER = Logger.getLogger(SwarmAgentTemplate.class.getName());
    private static final AtomicInteger AGENT_COUNTER = new AtomicInteger(0);

    private final String name;
    private String image;
    private String labelString;
    private String command;
    private String remoteFs;
    private int numExecutors;
    private int maxInstances;
    private Node.Mode mode;

    // Resource constraints
    private String cpuLimit;
    private String memoryLimit;
    private String cpuReservation;
    private String memoryReservation;

    // Mount configuration
    private List<MountConfig> mounts;

    // Environment variables
    private List<EnvironmentVariable> environmentVariables;

    // Placement constraints
    private List<String> placementConstraints;

    // Network aliases
    private List<String> networkAliases;

    // Docker Swarm Secrets
    private List<SwarmSecretConfig> secrets;

    // Docker Swarm Configs (for configuration files)
    private List<SwarmConfigFile> configs;

    // Cache directories (mounted as tmpfs or volumes for build caching)
    private List<String> cacheDirs;

    // Health check configuration
    private String healthCheckCommand;
    private int healthCheckIntervalSeconds;
    private int healthCheckTimeoutSeconds;
    private int healthCheckRetries;

    // Advanced container options (#120)
    private List<String> capAdd;        // Linux capabilities to add (e.g., CAP_NET_ADMIN)
    private List<String> capDrop;       // Linux capabilities to drop
    private List<String> sysctls;       // Kernel parameters (e.g., net.core.somaxconn=1024)
    private boolean privileged;         // Run in privileged mode
    private String user;                // User to run container as (e.g., "1000:1000")
    private String hostname;            // Container hostname
    private List<String> dnsServers;    // Custom DNS servers
    private List<String> dnsOptions;    // DNS options
    private List<String> dnsSearch;     // DNS search domains
    private String stopSignal;          // Signal to stop container (e.g., SIGTERM)
    private long stopGracePeriod;       // Grace period in seconds before force kill

    // Template inheritance (like K8s plugin inheritFrom)
    private String inheritFrom;         // Name of parent template to inherit from

    // Generic resources (GPU support)
    private List<GenericResource> genericResources;  // e.g., NVIDIA-GPU=1

    // Security profiles (Docker Engine 29+)
    private String seccompProfile;      // "default", "unconfined", or custom profile path
    private String apparmorProfile;     // "runtime/default", "unconfined", or custom profile

    // Connection and idle timeouts
    private int connectionTimeoutSeconds;  // Max time to wait for agent connection (default 300)
    private int idleTimeoutMinutes;        // Idle time before termination (default 30)

    // Retry configuration for provisioning
    private int provisionRetryCount;       // Number of retries on failure (default 3)
    private long provisionRetryDelayMs;    // Initial delay between retries (default 1000)

    // Port bindings for published ports
    private List<PortBinding> portBindings;  // e.g., 80:8080, :5900

    // Container args control - when true, don't pass args to container entrypoint
    // Useful for images that only use environment variables (JENKINS_URL, JENKINS_SECRET, etc.)
    private boolean disableContainerArgs;

    // Parent cloud reference
    private transient SwarmCloud parent;

    // Current instance count
    private transient AtomicInteger currentInstances;

    /**
     * Ensures transient fields are initialized after deserialization.
     */
    protected Object readResolve() {
        if (currentInstances == null) {
            currentInstances = new AtomicInteger(0);
        }
        return this;
    }

    /**
     * Gets the currentInstances counter, initializing if needed.
     * Public for atomic operations from ClusterMonitor.
     */
    public AtomicInteger getCurrentInstancesCounter() {
        if (currentInstances == null) {
            currentInstances = new AtomicInteger(0);
        }
        return currentInstances;
    }

    @DataBoundConstructor
    public SwarmAgentTemplate(@NonNull String name) {
        this.name = Util.fixEmptyAndTrim(name);
        this.image = "jenkins/inbound-agent:latest";
        this.remoteFs = "/home/jenkins/agent";
        this.numExecutors = 1;
        this.maxInstances = 5;
        this.mode = Node.Mode.NORMAL;
        this.currentInstances = new AtomicInteger(0);
    }

    @NonNull
    public String getName() {
        return name != null ? name : "";
    }

    @NonNull
    public String getImage() {
        return image != null ? image : "jenkins/inbound-agent:latest";
    }

    @DataBoundSetter
    public void setImage(String image) {
        this.image = Util.fixEmptyAndTrim(image);
    }

    @Nullable
    public String getLabelString() {
        return labelString;
    }

    @DataBoundSetter
    public void setLabelString(String labelString) {
        this.labelString = Util.fixEmptyAndTrim(labelString);
    }

    // Alias for docker-swarm-plugin compatibility
    @Nullable
    public String getLabel() {
        return labelString;
    }

    @DataBoundSetter
    public void setLabel(String label) {
        this.labelString = Util.fixEmptyAndTrim(label);
    }

    @Nullable
    public String getCommand() {
        return command;
    }

    @DataBoundSetter
    public void setCommand(String command) {
        this.command = Util.fixEmptyAndTrim(command);
    }

    @NonNull
    public String getRemoteFs() {
        return remoteFs != null ? remoteFs : "/home/jenkins/agent";
    }

    @DataBoundSetter
    public void setRemoteFs(String remoteFs) {
        this.remoteFs = Util.fixEmptyAndTrim(remoteFs);
    }

    // Alias for docker-swarm-plugin compatibility
    @NonNull
    public String getWorkingDir() {
        return getRemoteFs();
    }

    @DataBoundSetter
    public void setWorkingDir(String workingDir) {
        this.remoteFs = Util.fixEmptyAndTrim(workingDir);
    }

    public int getNumExecutors() {
        return numExecutors > 0 ? numExecutors : 1;
    }

    @DataBoundSetter
    public void setNumExecutors(int numExecutors) {
        this.numExecutors = numExecutors > 0 ? numExecutors : 1;
    }

    public int getMaxInstances() {
        return maxInstances > 0 ? maxInstances : 5;
    }

    @DataBoundSetter
    public void setMaxInstances(int maxInstances) {
        this.maxInstances = maxInstances > 0 ? maxInstances : 5;
    }

    @NonNull
    public Node.Mode getMode() {
        return mode != null ? mode : Node.Mode.NORMAL;
    }

    @DataBoundSetter
    public void setMode(Node.Mode mode) {
        this.mode = mode;
    }

    @Nullable
    public String getCpuLimit() {
        return cpuLimit;
    }

    @DataBoundSetter
    public void setCpuLimit(String cpuLimit) {
        this.cpuLimit = Util.fixEmptyAndTrim(cpuLimit);
    }

    @Nullable
    public String getMemoryLimit() {
        return memoryLimit;
    }

    @DataBoundSetter
    public void setMemoryLimit(String memoryLimit) {
        this.memoryLimit = Util.fixEmptyAndTrim(memoryLimit);
    }

    // Aliases for docker-swarm-plugin compatibility (NanoCPUs and Bytes)

    /**
     * Gets CPU limit in nanoCPUs (1e9 = 1 CPU).
     * For docker-swarm-plugin compatibility.
     */
    @Nullable
    public Long getLimitsNanoCPUs() {
        if (cpuLimit == null || cpuLimit.isBlank()) return null;
        try {
            return (long) (Double.parseDouble(cpuLimit) * 1_000_000_000L);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @DataBoundSetter
    public void setLimitsNanoCPUs(Long nanoCPUs) {
        if (nanoCPUs == null || nanoCPUs <= 0) {
            this.cpuLimit = null;
        } else {
            this.cpuLimit = String.valueOf(nanoCPUs / 1_000_000_000.0);
        }
    }

    /**
     * Gets memory limit in bytes.
     * For docker-swarm-plugin compatibility.
     */
    @Nullable
    public Long getLimitsMemoryBytes() {
        return parseMemoryToBytes(memoryLimit);
    }

    @DataBoundSetter
    public void setLimitsMemoryBytes(Long bytes) {
        this.memoryLimit = formatBytesToMemory(bytes);
    }

    /**
     * Gets CPU reservation in nanoCPUs.
     * For docker-swarm-plugin compatibility.
     */
    @Nullable
    public Long getReservationsNanoCPUs() {
        if (cpuReservation == null || cpuReservation.isBlank()) return null;
        try {
            return (long) (Double.parseDouble(cpuReservation) * 1_000_000_000L);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @DataBoundSetter
    public void setReservationsNanoCPUs(Long nanoCPUs) {
        if (nanoCPUs == null || nanoCPUs <= 0) {
            this.cpuReservation = null;
        } else {
            this.cpuReservation = String.valueOf(nanoCPUs / 1_000_000_000.0);
        }
    }

    /**
     * Gets memory reservation in bytes.
     * For docker-swarm-plugin compatibility.
     */
    @Nullable
    public Long getReservationsMemoryBytes() {
        return parseMemoryToBytes(memoryReservation);
    }

    @DataBoundSetter
    public void setReservationsMemoryBytes(Long bytes) {
        this.memoryReservation = formatBytesToMemory(bytes);
    }

    // Memory conversion helpers

    @Nullable
    private static Long parseMemoryToBytes(String memory) {
        if (memory == null || memory.isBlank()) return null;
        memory = memory.trim().toLowerCase(Locale.ROOT);
        try {
            long multiplier = 1;
            if (memory.endsWith("g")) {
                multiplier = 1024L * 1024 * 1024;
                memory = memory.substring(0, memory.length() - 1);
            } else if (memory.endsWith("m")) {
                multiplier = 1024L * 1024;
                memory = memory.substring(0, memory.length() - 1);
            } else if (memory.endsWith("k")) {
                multiplier = 1024L;
                memory = memory.substring(0, memory.length() - 1);
            } else if (memory.endsWith("b")) {
                memory = memory.substring(0, memory.length() - 1);
            }
            return Long.parseLong(memory) * multiplier;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Nullable
    private static String formatBytesToMemory(Long bytes) {
        if (bytes == null || bytes <= 0) return null;
        if (bytes >= 1024L * 1024 * 1024 && bytes % (1024L * 1024 * 1024) == 0) {
            return (bytes / (1024L * 1024 * 1024)) + "g";
        } else if (bytes >= 1024L * 1024 && bytes % (1024L * 1024) == 0) {
            return (bytes / (1024L * 1024)) + "m";
        } else if (bytes >= 1024L && bytes % 1024L == 0) {
            return (bytes / 1024L) + "k";
        }
        return bytes.toString();
    }

    @Nullable
    public String getCpuReservation() {
        return cpuReservation;
    }

    @DataBoundSetter
    public void setCpuReservation(String cpuReservation) {
        this.cpuReservation = Util.fixEmptyAndTrim(cpuReservation);
    }

    @Nullable
    public String getMemoryReservation() {
        return memoryReservation;
    }

    @DataBoundSetter
    public void setMemoryReservation(String memoryReservation) {
        this.memoryReservation = Util.fixEmptyAndTrim(memoryReservation);
    }

    @NonNull
    public List<MountConfig> getMounts() {
        return mounts != null ? Collections.unmodifiableList(mounts) : Collections.emptyList();
    }

    @DataBoundSetter
    public void setMounts(List<MountConfig> mounts) {
        this.mounts = mounts;
    }

    // Alias for docker-swarm-plugin compatibility
    @NonNull
    public List<MountConfig> getHostBinds() {
        return getMounts();
    }

    @DataBoundSetter
    public void setHostBinds(List<MountConfig> hostBinds) {
        this.mounts = hostBinds;
    }

    @NonNull
    public List<EnvironmentVariable> getEnvironmentVariables() {
        return environmentVariables != null ? Collections.unmodifiableList(environmentVariables) : Collections.emptyList();
    }

    @DataBoundSetter
    public void setEnvironmentVariables(List<EnvironmentVariable> environmentVariables) {
        this.environmentVariables = environmentVariables;
    }

    // Alias for docker-swarm-plugin compatibility
    @NonNull
    public List<EnvironmentVariable> getEnvVars() {
        return getEnvironmentVariables();
    }

    @DataBoundSetter
    public void setEnvVars(List<EnvironmentVariable> envVars) {
        this.environmentVariables = envVars;
    }

    @NonNull
    public List<String> getPlacementConstraints() {
        return placementConstraints != null ? Collections.unmodifiableList(placementConstraints) : Collections.emptyList();
    }

    @DataBoundSetter
    public void setPlacementConstraints(List<String> placementConstraints) {
        this.placementConstraints = placementConstraints;
    }

    /**
     * Sets placement constraints from a newline-separated string (for Jelly UI).
     */
    @DataBoundSetter
    public void setPlacementConstraintsString(String constraints) {
        if (constraints == null || constraints.isBlank()) {
            this.placementConstraints = null;
            return;
        }
        this.placementConstraints = Arrays.stream(constraints.split("\\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Gets placement constraints as a newline-separated string (for Jelly UI).
     */
    @Nullable
    public String getPlacementConstraintsString() {
        if (placementConstraints == null || placementConstraints.isEmpty()) {
            return null;
        }
        return String.join("\n", placementConstraints);
    }

    @NonNull
    public List<String> getNetworkAliases() {
        return networkAliases != null ? Collections.unmodifiableList(networkAliases) : Collections.emptyList();
    }

    @DataBoundSetter
    public void setNetworkAliases(List<String> networkAliases) {
        this.networkAliases = networkAliases;
    }

    /**
     * Sets network aliases from a comma-separated string (for Jelly UI).
     */
    @DataBoundSetter
    public void setNetworkAliasesString(String aliases) {
        if (aliases == null || aliases.isBlank()) {
            this.networkAliases = null;
            return;
        }
        this.networkAliases = Arrays.stream(aliases.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Gets network aliases as a comma-separated string (for Jelly UI).
     */
    @Nullable
    public String getNetworkAliasesString() {
        if (networkAliases == null || networkAliases.isEmpty()) {
            return null;
        }
        return String.join(", ", networkAliases);
    }

    @NonNull
    public List<SwarmSecretConfig> getSecrets() {
        return secrets != null ? Collections.unmodifiableList(secrets) : Collections.emptyList();
    }

    @DataBoundSetter
    public void setSecrets(List<SwarmSecretConfig> secrets) {
        this.secrets = secrets;
    }

    @NonNull
    public List<SwarmConfigFile> getConfigs() {
        return configs != null ? Collections.unmodifiableList(configs) : Collections.emptyList();
    }

    @DataBoundSetter
    public void setConfigs(List<SwarmConfigFile> configs) {
        this.configs = configs;
    }

    /**
     * Gets configs as newline-separated string for UI (format: configName:targetPath).
     */
    @Nullable
    public String getConfigsString() {
        if (configs == null || configs.isEmpty()) {
            return null;
        }
        return configs.stream()
                .map(SwarmConfigFile::toString)
                .collect(Collectors.joining("\n"));
    }

    /**
     * Sets configs from newline-separated string (format: configName:targetPath).
     * Example: nethasp.ini:/opt/1cv8/current/conf/nethasp.ini
     */
    @DataBoundSetter
    public void setConfigsString(String configsStr) {
        if (configsStr == null || configsStr.isBlank()) {
            this.configs = null;
            return;
        }
        this.configs = Arrays.stream(configsStr.split("\\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(SwarmConfigFile::parse)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
    }

    @NonNull
    public List<String> getCacheDirs() {
        return cacheDirs != null ? Collections.unmodifiableList(cacheDirs) : Collections.emptyList();
    }

    @DataBoundSetter
    public void setCacheDirs(List<String> cacheDirs) {
        this.cacheDirs = cacheDirs;
    }

    /**
     * Gets cache directories as newline-separated string for UI.
     */
    @Nullable
    public String getCacheDirsString() {
        if (cacheDirs == null || cacheDirs.isEmpty()) {
            return null;
        }
        return String.join("\n", cacheDirs);
    }

    /**
     * Sets cache directories from newline-separated string.
     */
    @DataBoundSetter
    public void setCacheDirsString(String cacheDirsStr) {
        if (cacheDirsStr == null || cacheDirsStr.isBlank()) {
            this.cacheDirs = null;
            return;
        }
        this.cacheDirs = Arrays.stream(cacheDirsStr.split("\\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty() && s.startsWith("/"))
                .collect(Collectors.toList());
    }

    @Nullable
    public String getHealthCheckCommand() {
        return healthCheckCommand;
    }

    @DataBoundSetter
    public void setHealthCheckCommand(String healthCheckCommand) {
        this.healthCheckCommand = Util.fixEmptyAndTrim(healthCheckCommand);
    }

    public int getHealthCheckIntervalSeconds() {
        return healthCheckIntervalSeconds > 0 ? healthCheckIntervalSeconds : 30;
    }

    @DataBoundSetter
    public void setHealthCheckIntervalSeconds(int healthCheckIntervalSeconds) {
        this.healthCheckIntervalSeconds = healthCheckIntervalSeconds;
    }

    public int getHealthCheckTimeoutSeconds() {
        return healthCheckTimeoutSeconds > 0 ? healthCheckTimeoutSeconds : 10;
    }

    @DataBoundSetter
    public void setHealthCheckTimeoutSeconds(int healthCheckTimeoutSeconds) {
        this.healthCheckTimeoutSeconds = healthCheckTimeoutSeconds;
    }

    public int getHealthCheckRetries() {
        return healthCheckRetries > 0 ? healthCheckRetries : 3;
    }

    @DataBoundSetter
    public void setHealthCheckRetries(int healthCheckRetries) {
        this.healthCheckRetries = healthCheckRetries;
    }

    /**
     * Checks if health check is configured.
     */
    public boolean hasHealthCheck() {
        return healthCheckCommand != null && !healthCheckCommand.isBlank();
    }

    // Advanced container options getters and setters (#120)

    @NonNull
    public List<String> getCapAdd() {
        return capAdd != null ? Collections.unmodifiableList(capAdd) : Collections.emptyList();
    }

    @DataBoundSetter
    public void setCapAdd(List<String> capAdd) {
        this.capAdd = capAdd;
    }

    @DataBoundSetter
    public void setCapAddString(String caps) {
        this.capAdd = parseCommaSeparated(caps);
    }

    @Nullable
    public String getCapAddString() {
        return capAdd != null && !capAdd.isEmpty() ? String.join(", ", capAdd) : null;
    }

    @NonNull
    public List<String> getCapDrop() {
        return capDrop != null ? Collections.unmodifiableList(capDrop) : Collections.emptyList();
    }

    @DataBoundSetter
    public void setCapDrop(List<String> capDrop) {
        this.capDrop = capDrop;
    }

    @DataBoundSetter
    public void setCapDropString(String caps) {
        this.capDrop = parseCommaSeparated(caps);
    }

    @Nullable
    public String getCapDropString() {
        return capDrop != null && !capDrop.isEmpty() ? String.join(", ", capDrop) : null;
    }

    @NonNull
    public List<String> getSysctls() {
        return sysctls != null ? Collections.unmodifiableList(sysctls) : Collections.emptyList();
    }

    @DataBoundSetter
    public void setSysctls(List<String> sysctls) {
        this.sysctls = sysctls;
    }

    @DataBoundSetter
    public void setSysctlsString(String sysctlsStr) {
        this.sysctls = parseNewlineSeparated(sysctlsStr);
    }

    @Nullable
    public String getSysctlsString() {
        return sysctls != null && !sysctls.isEmpty() ? String.join("\n", sysctls) : null;
    }

    public boolean isPrivileged() {
        return privileged;
    }

    @DataBoundSetter
    public void setPrivileged(boolean privileged) {
        this.privileged = privileged;
    }

    /**
     * Returns true if container args should be disabled.
     * When disabled, only environment variables are passed to the container.
     */
    public boolean isDisableContainerArgs() {
        return disableContainerArgs;
    }

    @DataBoundSetter
    public void setDisableContainerArgs(boolean disableContainerArgs) {
        this.disableContainerArgs = disableContainerArgs;
    }

    @Nullable
    public String getUser() {
        return user;
    }

    @DataBoundSetter
    public void setUser(String user) {
        this.user = Util.fixEmptyAndTrim(user);
    }

    @Nullable
    public String getHostname() {
        return hostname;
    }

    @DataBoundSetter
    public void setHostname(String hostname) {
        this.hostname = Util.fixEmptyAndTrim(hostname);
    }

    @NonNull
    public List<String> getDnsServers() {
        return dnsServers != null ? Collections.unmodifiableList(dnsServers) : Collections.emptyList();
    }

    @DataBoundSetter
    public void setDnsServers(List<String> dnsServers) {
        this.dnsServers = dnsServers;
    }

    @DataBoundSetter
    public void setDnsServersString(String dns) {
        this.dnsServers = parseCommaSeparated(dns);
    }

    @Nullable
    public String getDnsServersString() {
        return dnsServers != null && !dnsServers.isEmpty() ? String.join(", ", dnsServers) : null;
    }

    // Alias for docker-swarm-plugin compatibility
    @Nullable
    public String getDnsIps() {
        return getDnsServersString();
    }

    @DataBoundSetter
    public void setDnsIps(String dnsIps) {
        setDnsServersString(dnsIps);
    }

    @NonNull
    public List<String> getDnsOptions() {
        return dnsOptions != null ? Collections.unmodifiableList(dnsOptions) : Collections.emptyList();
    }

    @DataBoundSetter
    public void setDnsOptions(List<String> dnsOptions) {
        this.dnsOptions = dnsOptions;
    }

    @NonNull
    public List<String> getDnsSearch() {
        return dnsSearch != null ? Collections.unmodifiableList(dnsSearch) : Collections.emptyList();
    }

    @DataBoundSetter
    public void setDnsSearch(List<String> dnsSearch) {
        this.dnsSearch = dnsSearch;
    }

    @Nullable
    public String getStopSignal() {
        return stopSignal;
    }

    @DataBoundSetter
    public void setStopSignal(String stopSignal) {
        this.stopSignal = Util.fixEmptyAndTrim(stopSignal);
    }

    public long getStopGracePeriod() {
        return stopGracePeriod > 0 ? stopGracePeriod : 10;
    }

    @DataBoundSetter
    public void setStopGracePeriod(long stopGracePeriod) {
        this.stopGracePeriod = stopGracePeriod;
    }

    // ==================== New features getters/setters ====================

    @Nullable
    public String getInheritFrom() {
        return inheritFrom;
    }

    @DataBoundSetter
    public void setInheritFrom(String inheritFrom) {
        this.inheritFrom = Util.fixEmptyAndTrim(inheritFrom);
    }

    @NonNull
    public List<GenericResource> getGenericResources() {
        return genericResources != null ? Collections.unmodifiableList(genericResources) : Collections.emptyList();
    }

    @DataBoundSetter
    public void setGenericResources(List<GenericResource> genericResources) {
        this.genericResources = genericResources;
    }

    /**
     * Gets generic resources as string for UI (comma-separated: NVIDIA-GPU=1, FPGA=2)
     */
    @Nullable
    public String getGenericResourcesString() {
        if (genericResources == null || genericResources.isEmpty()) return null;
        return genericResources.stream()
                .map(r -> r.getKind() + "=" + r.getValue())
                .collect(Collectors.joining(", "));
    }

    @DataBoundSetter
    public void setGenericResourcesString(String str) {
        if (str == null || str.isBlank()) {
            this.genericResources = null;
            return;
        }
        this.genericResources = Arrays.stream(str.split(","))
                .map(String::trim)
                .filter(s -> s.contains("="))
                .map(s -> {
                    String[] parts = s.split("=", 2);
                    return new GenericResource(parts[0].trim(), Long.parseLong(parts[1].trim()));
                })
                .collect(Collectors.toList());
    }

    @Nullable
    public String getSeccompProfile() {
        return seccompProfile;
    }

    @DataBoundSetter
    public void setSeccompProfile(String seccompProfile) {
        this.seccompProfile = Util.fixEmptyAndTrim(seccompProfile);
    }

    @Nullable
    public String getApparmorProfile() {
        return apparmorProfile;
    }

    @DataBoundSetter
    public void setApparmorProfile(String apparmorProfile) {
        this.apparmorProfile = Util.fixEmptyAndTrim(apparmorProfile);
    }

    public int getConnectionTimeoutSeconds() {
        return connectionTimeoutSeconds > 0 ? connectionTimeoutSeconds : 300;
    }

    @DataBoundSetter
    public void setConnectionTimeoutSeconds(int connectionTimeoutSeconds) {
        this.connectionTimeoutSeconds = connectionTimeoutSeconds;
    }

    public int getIdleTimeoutMinutes() {
        return idleTimeoutMinutes > 0 ? idleTimeoutMinutes : 30;
    }

    @DataBoundSetter
    public void setIdleTimeoutMinutes(int idleTimeoutMinutes) {
        this.idleTimeoutMinutes = idleTimeoutMinutes;
    }

    public int getProvisionRetryCount() {
        return provisionRetryCount > 0 ? provisionRetryCount : 3;
    }

    @DataBoundSetter
    public void setProvisionRetryCount(int provisionRetryCount) {
        this.provisionRetryCount = provisionRetryCount;
    }

    public long getProvisionRetryDelayMs() {
        return provisionRetryDelayMs > 0 ? provisionRetryDelayMs : 1000;
    }

    @DataBoundSetter
    public void setProvisionRetryDelayMs(long provisionRetryDelayMs) {
        this.provisionRetryDelayMs = provisionRetryDelayMs;
    }

    @NonNull
    public List<PortBinding> getPortBindings() {
        return portBindings != null ? Collections.unmodifiableList(portBindings) : Collections.emptyList();
    }

    @DataBoundSetter
    public void setPortBindings(List<PortBinding> portBindings) {
        this.portBindings = portBindings;
    }

    /**
     * Gets port bindings as newline-separated string for UI.
     * Format: [hostPort:]containerPort[/protocol]
     * Examples: 80:8080, :5900, 443:8443/tcp
     */
    @Nullable
    public String getPortBindingsString() {
        if (portBindings == null || portBindings.isEmpty()) return null;
        return portBindings.stream()
                .map(PortBinding::toString)
                .collect(Collectors.joining("\n"));
    }

    /**
     * Sets port bindings from newline-separated string.
     */
    @DataBoundSetter
    public void setPortBindingsString(String str) {
        if (str == null || str.isBlank()) {
            this.portBindings = null;
            return;
        }
        this.portBindings = Arrays.stream(str.split("\\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(PortBinding::parse)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
    }

    // Alias for docker-swarm-plugin compatibility
    @Nullable
    public String getPortBinds() {
        return getPortBindingsString();
    }

    @DataBoundSetter
    public void setPortBinds(String portBinds) {
        setPortBindingsString(portBinds);
    }

    /**
     * Resolves this template by merging with parent template if inheritFrom is set.
     * Similar to Kubernetes plugin podTemplate inheritance.
     *
     * @return Resolved template with inherited values
     */
    @NonNull
    public SwarmAgentTemplate resolve() {
        if (inheritFrom == null || inheritFrom.isBlank() || parent == null) {
            return this;
        }

        SwarmAgentTemplate parentTemplate = parent.getTemplateByName(inheritFrom);
        if (parentTemplate == null) {
            LOGGER.warning("Parent template '" + inheritFrom + "' not found, using current template as-is");
            return this;
        }

        // Create merged template
        SwarmAgentTemplate resolved = new SwarmAgentTemplate(this.name);
        resolved.setParent(this.parent);

        // Inherit from parent, override with current values
        resolved.setImage(this.image != null ? this.image : parentTemplate.getImage());
        resolved.setLabelString(mergeLabelStrings(parentTemplate.getLabelString(), this.labelString));
        resolved.setCommand(this.command != null ? this.command : parentTemplate.getCommand());
        resolved.setRemoteFs(this.remoteFs != null ? this.remoteFs : parentTemplate.getRemoteFs());
        resolved.setNumExecutors(this.numExecutors > 0 ? this.numExecutors : parentTemplate.getNumExecutors());
        resolved.setMaxInstances(this.maxInstances > 0 ? this.maxInstances : parentTemplate.getMaxInstances());
        resolved.setMode(this.mode != null ? this.mode : parentTemplate.getMode());

        // Resources - child overrides parent
        resolved.setCpuLimit(this.cpuLimit != null ? this.cpuLimit : parentTemplate.getCpuLimit());
        resolved.setMemoryLimit(this.memoryLimit != null ? this.memoryLimit : parentTemplate.getMemoryLimit());
        resolved.setCpuReservation(this.cpuReservation != null ? this.cpuReservation : parentTemplate.getCpuReservation());
        resolved.setMemoryReservation(this.memoryReservation != null ? this.memoryReservation : parentTemplate.getMemoryReservation());

        // Merge lists (mounts, envVars, secrets, configs, cacheDirs)
        resolved.setMounts(mergeLists(parentTemplate.getMounts(), this.mounts));
        resolved.setEnvironmentVariables(mergeLists(parentTemplate.getEnvironmentVariables(), this.environmentVariables));
        resolved.setSecrets(mergeLists(parentTemplate.getSecrets(), this.secrets));
        resolved.setConfigs(mergeLists(parentTemplate.getConfigs(), this.configs));
        resolved.setCacheDirs(mergeLists(parentTemplate.getCacheDirs(), this.cacheDirs));

        // Placement constraints - merge
        resolved.setPlacementConstraints(mergeLists(parentTemplate.getPlacementConstraints(), this.placementConstraints));
        resolved.setNetworkAliases(mergeLists(parentTemplate.getNetworkAliases(), this.networkAliases));

        // Health check - child overrides
        resolved.setHealthCheckCommand(this.healthCheckCommand != null ? this.healthCheckCommand : parentTemplate.getHealthCheckCommand());
        resolved.setHealthCheckIntervalSeconds(this.healthCheckIntervalSeconds > 0 ? this.healthCheckIntervalSeconds : parentTemplate.getHealthCheckIntervalSeconds());
        resolved.setHealthCheckTimeoutSeconds(this.healthCheckTimeoutSeconds > 0 ? this.healthCheckTimeoutSeconds : parentTemplate.getHealthCheckTimeoutSeconds());
        resolved.setHealthCheckRetries(this.healthCheckRetries > 0 ? this.healthCheckRetries : parentTemplate.getHealthCheckRetries());

        // Advanced options - merge capabilities, override others
        resolved.setCapAdd(mergeLists(parentTemplate.getCapAdd(), this.capAdd));
        resolved.setCapDrop(mergeLists(parentTemplate.getCapDrop(), this.capDrop));
        resolved.setSysctls(mergeLists(parentTemplate.getSysctls(), this.sysctls));
        resolved.setPrivileged(this.privileged || parentTemplate.isPrivileged());
        resolved.setUser(this.user != null ? this.user : parentTemplate.getUser());
        resolved.setHostname(this.hostname != null ? this.hostname : parentTemplate.getHostname());
        resolved.setDnsServers(mergeLists(parentTemplate.getDnsServers(), this.dnsServers));
        resolved.setDnsOptions(mergeLists(parentTemplate.getDnsOptions(), this.dnsOptions));
        resolved.setDnsSearch(mergeLists(parentTemplate.getDnsSearch(), this.dnsSearch));
        resolved.setStopSignal(this.stopSignal != null ? this.stopSignal : parentTemplate.getStopSignal());
        resolved.setStopGracePeriod(this.stopGracePeriod > 0 ? this.stopGracePeriod : parentTemplate.getStopGracePeriod());

        // New features
        resolved.setGenericResources(mergeLists(parentTemplate.getGenericResources(), this.genericResources));
        resolved.setSeccompProfile(this.seccompProfile != null ? this.seccompProfile : parentTemplate.getSeccompProfile());
        resolved.setApparmorProfile(this.apparmorProfile != null ? this.apparmorProfile : parentTemplate.getApparmorProfile());
        resolved.setConnectionTimeoutSeconds(this.connectionTimeoutSeconds > 0 ? this.connectionTimeoutSeconds : parentTemplate.getConnectionTimeoutSeconds());
        resolved.setIdleTimeoutMinutes(this.idleTimeoutMinutes > 0 ? this.idleTimeoutMinutes : parentTemplate.getIdleTimeoutMinutes());
        resolved.setProvisionRetryCount(this.provisionRetryCount > 0 ? this.provisionRetryCount : parentTemplate.getProvisionRetryCount());
        resolved.setProvisionRetryDelayMs(this.provisionRetryDelayMs > 0 ? this.provisionRetryDelayMs : parentTemplate.getProvisionRetryDelayMs());
        resolved.setPortBindings(mergeLists(parentTemplate.getPortBindings(), this.portBindings));

        return resolved;
    }

    private String mergeLabelStrings(String parent, String child) {
        if (child == null || child.isBlank()) return parent;
        if (parent == null || parent.isBlank()) return child;
        // Combine labels
        Set<String> labels = new java.util.LinkedHashSet<>();
        labels.addAll(Arrays.asList(parent.split("\\s+")));
        labels.addAll(Arrays.asList(child.split("\\s+")));
        return String.join(" ", labels);
    }

    private <T> List<T> mergeLists(List<T> parent, List<T> child) {
        if (child != null && !child.isEmpty()) {
            if (parent == null || parent.isEmpty()) {
                return new ArrayList<>(child);
            }
            List<T> merged = new ArrayList<>(parent);
            merged.addAll(child);
            return merged;
        }
        return parent != null ? new ArrayList<>(parent) : Collections.emptyList();
    }

    private List<String> parseCommaSeparated(String str) {
        if (str == null || str.isBlank()) return null;
        return Arrays.stream(str.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private List<String> parseNewlineSeparated(String str) {
        if (str == null || str.isBlank()) return null;
        return Arrays.stream(str.split("\\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    public void setParent(SwarmCloud parent) {
        this.parent = parent;
    }

    @Nullable
    public SwarmCloud getParent() {
        return parent;
    }

    /**
     * Gets the set of labels for this template.
     */
    @NonNull
    public Set<LabelAtom> getLabelSet() {
        if (labelString == null || labelString.isBlank()) {
            return Collections.emptySet();
        }
        return Label.parse(labelString);
    }

    /**
     * Checks if this template matches the given label.
     */
    public boolean matches(@Nullable Label label) {
        if (label == null) {
            return mode == Node.Mode.NORMAL;
        }

        if (labelString == null || labelString.isBlank()) {
            return mode == Node.Mode.NORMAL;
        }

        return label.matches(getLabelSet());
    }

    /**
     * Generates a unique agent name.
     */
    @NonNull
    public String generateAgentName() {
        String shortUuid = UUID.randomUUID().toString().substring(0, 8);
        return String.format("swarm-%s-%d-%s",
                name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "-"),
                AGENT_COUNTER.incrementAndGet(),
                shortUuid);
    }

    /**
     * Gets the available capacity for new instances.
     */
    public int getAvailableCapacity() {
        return Math.max(0, maxInstances - getCurrentInstancesCounter().get());
    }

    /**
     * Increments the current instance count.
     */
    public void incrementInstances() {
        getCurrentInstancesCounter().incrementAndGet();
    }

    /**
     * Decrements the current instance count.
     */
    public void decrementInstances() {
        getCurrentInstancesCounter().updateAndGet(current -> Math.max(0, current - 1));
    }

    /**
     * Gets the current instance count.
     */
    public int getCurrentInstances() {
        return getCurrentInstancesCounter().get();
    }

    /**
     * Sets the current instance count directly.
     * Used for synchronizing with actual service count from Docker Swarm.
     */
    public void setCurrentInstances(int count) {
        getCurrentInstancesCounter().set(Math.max(0, count));
    }

    /**
     * Generic resource configuration for Docker Swarm (e.g., GPU).
     * Maps to Swarm's GenericResource in task resource requirements.
     */
    public static class GenericResource extends AbstractDescribableImpl<GenericResource> {
        private final String kind;  // e.g., "NVIDIA-GPU", "FPGA", "SSD"
        private final long value;   // e.g., 1, 2

        @DataBoundConstructor
        public GenericResource(String kind, long value) {
            this.kind = kind;
            this.value = value;
        }

        public String getKind() {
            return kind;
        }

        public long getValue() {
            return value;
        }

        @Override
        public String toString() {
            return kind + "=" + value;
        }

        @Extension
        @Symbol("swarmGenericResource")
        public static class DescriptorImpl extends Descriptor<GenericResource> {
            @NonNull
            @Override
            public String getDisplayName() {
                return "Generic Resource";
            }
        }
    }

    /**
     * Mount configuration for Docker volumes.
     */
    public static class MountConfig extends AbstractDescribableImpl<MountConfig> {
        private final String type; // bind, volume, tmpfs
        private final String source;
        private final String target;
        private boolean readOnly;

        @DataBoundConstructor
        public MountConfig(String type, String source, String target) {
            this.type = type;
            this.source = source;
            this.target = target;
        }

        public String getType() {
            return type;
        }

        public String getSource() {
            return source;
        }

        public String getTarget() {
            return target;
        }

        public boolean isReadOnly() {
            return readOnly;
        }

        @DataBoundSetter
        public void setReadOnly(boolean readOnly) {
            this.readOnly = readOnly;
        }

        @Extension
        @Symbol("swarmMount")
        public static class DescriptorImpl extends Descriptor<MountConfig> {
            @NonNull
            @Override
            public String getDisplayName() {
                return "Mount Configuration";
            }

            /**
             * Validates the mount type field.
             */
            @POST
            public FormValidation doCheckType(@QueryParameter String value) {
                Jenkins.get().checkPermission(Jenkins.ADMINISTER);
                if (Util.fixEmptyAndTrim(value) == null) {
                    return FormValidation.error("Type is required. Use: bind, volume, or tmpfs");
                }
                String type = value.toLowerCase(Locale.ROOT).trim();
                if (!type.equals("bind") && !type.equals("volume") && !type.equals("tmpfs")) {
                    return FormValidation.error("Invalid type. Must be: bind, volume, or tmpfs");
                }
                return FormValidation.ok();
            }

            /**
             * Validates the source field.
             */
            @POST
            public FormValidation doCheckSource(@QueryParameter String value, @QueryParameter String type) {
                Jenkins.get().checkPermission(Jenkins.ADMINISTER);
                String t = Util.fixEmptyAndTrim(type);
                String s = Util.fixEmptyAndTrim(value);
                if (t != null && t.equalsIgnoreCase("tmpfs")) {
                    return FormValidation.ok(); // Source not needed for tmpfs
                }
                if (s == null) {
                    return FormValidation.warning("Source is required for bind and volume mounts");
                }
                return FormValidation.ok();
            }

            /**
             * Validates the target field.
             */
            @POST
            public FormValidation doCheckTarget(@QueryParameter String value) {
                Jenkins.get().checkPermission(Jenkins.ADMINISTER);
                if (Util.fixEmptyAndTrim(value) == null) {
                    return FormValidation.error("Target path is required");
                }
                if (!value.startsWith("/")) {
                    return FormValidation.error("Target must be an absolute path (start with /)");
                }
                return FormValidation.ok();
            }
        }
    }

    /**
     * Environment variable configuration.
     */
    public static class EnvironmentVariable extends AbstractDescribableImpl<EnvironmentVariable> {
        private final String name;
        private final String value;

        @DataBoundConstructor
        public EnvironmentVariable(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }

        @Extension
        @Symbol("swarmEnvVar")
        public static class DescriptorImpl extends Descriptor<EnvironmentVariable> {
            @NonNull
            @Override
            public String getDisplayName() {
                return "Environment Variable";
            }
        }
    }

    /**
     * Port binding configuration for Docker Swarm service.
     * Format: [hostPort:]containerPort[/protocol]
     * Examples: 80:8080, :5900, 443:8443/tcp
     */
    public static class PortBinding extends AbstractDescribableImpl<PortBinding> {
        private final int publishedPort;   // Host port (0 = random)
        private final int targetPort;      // Container port
        private final String protocol;     // tcp or udp (default: tcp)

        @DataBoundConstructor
        public PortBinding(int publishedPort, int targetPort, String protocol) {
            this.publishedPort = publishedPort;
            this.targetPort = targetPort;
            this.protocol = protocol != null && !protocol.isBlank() ? protocol.toLowerCase(Locale.ROOT) : "tcp";
        }

        public int getPublishedPort() {
            return publishedPort;
        }

        public int getTargetPort() {
            return targetPort;
        }

        public String getProtocol() {
            return protocol != null ? protocol : "tcp";
        }

        /**
         * Parses a port binding string.
         * Formats: 80:8080, :5900, 443:8443/tcp, 53:53/udp
         */
        @Nullable
        public static PortBinding parse(String str) {
            if (str == null || str.isBlank()) return null;

            str = str.trim();
            String protocol = "tcp";
            int slashIdx = str.indexOf('/');
            if (slashIdx > 0) {
                protocol = str.substring(slashIdx + 1).toLowerCase(Locale.ROOT);
                str = str.substring(0, slashIdx);
            }

            int colonIdx = str.indexOf(':');
            if (colonIdx < 0) {
                // Just container port
                try {
                    int targetPort = Integer.parseInt(str);
                    return new PortBinding(0, targetPort, protocol);
                } catch (NumberFormatException e) {
                    return null;
                }
            }

            String hostPart = str.substring(0, colonIdx).trim();
            String containerPart = str.substring(colonIdx + 1).trim();

            try {
                int publishedPort = hostPart.isEmpty() ? 0 : Integer.parseInt(hostPart);
                int targetPort = Integer.parseInt(containerPart);
                return new PortBinding(publishedPort, targetPort, protocol);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            if (publishedPort > 0) {
                sb.append(publishedPort);
            }
            sb.append(':').append(targetPort);
            if (!"tcp".equalsIgnoreCase(protocol)) {
                sb.append('/').append(protocol);
            }
            return sb.toString();
        }

        @Extension
        @Symbol("swarmPortBinding")
        public static class DescriptorImpl extends Descriptor<PortBinding> {
            @NonNull
            @Override
            public String getDisplayName() {
                return "Port Binding";
            }
        }
    }

    @Extension
    @Symbol("swarmAgentTemplate")
    public static class DescriptorImpl extends Descriptor<SwarmAgentTemplate> {

        @NonNull
        @Override
        public String getDisplayName() {
            return "Docker Swarm Agent Template";
        }

        @POST
        public FormValidation doCheckName(@QueryParameter String value) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            if (Util.fixEmptyAndTrim(value) == null) {
                return FormValidation.error("Name is required");
            }
            if (!value.matches("[a-zA-Z0-9_-]+")) {
                return FormValidation.error("Name must contain only letters, numbers, hyphens, and underscores");
            }
            return FormValidation.ok();
        }

        @POST
        public FormValidation doCheckImage(@QueryParameter String value) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            if (Util.fixEmptyAndTrim(value) == null) {
                return FormValidation.error("Docker image is required");
            }
            return FormValidation.ok();
        }

        @POST
        public FormValidation doCheckMaxInstances(@QueryParameter int value) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            if (value <= 0) {
                return FormValidation.error("Max instances must be greater than 0");
            }
            if (value > 100) {
                return FormValidation.warning("High max instances value. Consider the cluster capacity.");
            }
            return FormValidation.ok();
        }

        @POST
        public FormValidation doCheckMemoryLimit(@QueryParameter String value) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            if (Util.fixEmptyAndTrim(value) == null) {
                return FormValidation.ok(); // Optional
            }
            if (!value.matches("\\d+[bkmgBKMG]?")) {
                return FormValidation.error("Invalid memory format. Use formats like: 512m, 1g, 2048m");
            }
            return FormValidation.ok();
        }

        @POST
        public FormValidation doCheckCpuLimit(@QueryParameter String value) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            if (Util.fixEmptyAndTrim(value) == null) {
                return FormValidation.ok(); // Optional
            }
            try {
                double cpu = Double.parseDouble(value);
                if (cpu <= 0) {
                    return FormValidation.error("CPU limit must be positive");
                }
            } catch (NumberFormatException e) {
                return FormValidation.error("Invalid CPU format. Use decimal number like: 0.5, 1.0, 2.0");
            }
            return FormValidation.ok();
        }
    }
}

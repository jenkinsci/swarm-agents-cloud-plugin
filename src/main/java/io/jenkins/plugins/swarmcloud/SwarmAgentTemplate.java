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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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

    // Parent cloud reference
    private transient SwarmCloud parent;

    // Current instance count
    private transient AtomicInteger currentInstances = new AtomicInteger(0);

    @DataBoundConstructor
    public SwarmAgentTemplate(@NonNull String name) {
        this.name = Util.fixEmptyAndTrim(name);
        this.image = "jenkins/inbound-agent:latest";
        this.remoteFs = "/home/jenkins/agent";
        this.numExecutors = 1;
        this.maxInstances = 5;
        this.mode = Node.Mode.NORMAL;
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

    @NonNull
    public List<EnvironmentVariable> getEnvironmentVariables() {
        return environmentVariables != null ? Collections.unmodifiableList(environmentVariables) : Collections.emptyList();
    }

    @DataBoundSetter
    public void setEnvironmentVariables(List<EnvironmentVariable> environmentVariables) {
        this.environmentVariables = environmentVariables;
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
                name.toLowerCase().replaceAll("[^a-z0-9]", "-"),
                AGENT_COUNTER.incrementAndGet(),
                shortUuid);
    }

    /**
     * Gets the available capacity for new instances.
     */
    public int getAvailableCapacity() {
        return Math.max(0, maxInstances - currentInstances.get());
    }

    /**
     * Increments the current instance count.
     */
    public void incrementInstances() {
        currentInstances.incrementAndGet();
    }

    /**
     * Decrements the current instance count.
     */
    public void decrementInstances() {
        currentInstances.decrementAndGet();
    }

    /**
     * Gets the current instance count.
     */
    public int getCurrentInstances() {
        return currentInstances.get();
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

    @Extension
    @Symbol("swarmAgentTemplate")
    public static class DescriptorImpl extends Descriptor<SwarmAgentTemplate> {

        @NonNull
        @Override
        public String getDisplayName() {
            return "Docker Swarm Agent Template";
        }

        public FormValidation doCheckName(@QueryParameter String value) {
            if (Util.fixEmptyAndTrim(value) == null) {
                return FormValidation.error("Name is required");
            }
            if (!value.matches("[a-zA-Z0-9_-]+")) {
                return FormValidation.error("Name must contain only letters, numbers, hyphens, and underscores");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckImage(@QueryParameter String value) {
            if (Util.fixEmptyAndTrim(value) == null) {
                return FormValidation.error("Docker image is required");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckMaxInstances(@QueryParameter int value) {
            if (value <= 0) {
                return FormValidation.error("Max instances must be greater than 0");
            }
            if (value > 100) {
                return FormValidation.warning("High max instances value. Consider the cluster capacity.");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckMemoryLimit(@QueryParameter String value) {
            if (Util.fixEmptyAndTrim(value) == null) {
                return FormValidation.ok(); // Optional
            }
            if (!value.matches("\\d+[bkmgBKMG]?")) {
                return FormValidation.error("Invalid memory format. Use formats like: 512m, 1g, 2048m");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckCpuLimit(@QueryParameter String value) {
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

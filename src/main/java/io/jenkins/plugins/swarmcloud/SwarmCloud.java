package io.jenkins.plugins.swarmcloud;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.Label;
import hudson.model.Node;
import hudson.security.ACL;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.swarmcloud.api.DockerSwarmClient;
import io.jenkins.plugins.swarmcloud.monitoring.SwarmAuditLog;
import io.jenkins.plugins.swarmcloud.ratelimit.ProvisionRateLimiter;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerCredentials;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import javax.net.ssl.SSLHandshakeException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Docker Swarm Cloud implementation for Jenkins.
 * Provisions agents on a Docker Swarm cluster.
 */
public class SwarmCloud extends Cloud {

    private static final Logger LOGGER = Logger.getLogger(SwarmCloud.class.getName());

    private String dockerHost;
    private String credentialsId;
    private String jenkinsUrl;
    private String swarmNetwork;
    private int maxConcurrentAgents;
    private int maxProvisionsPerMinute;
    private long minProvisionIntervalMs;
    private boolean rateLimitEnabled;
    private List<SwarmAgentTemplate> templates;
    private transient DockerSwarmClient dockerClient;

    @DataBoundConstructor
    public SwarmCloud(@NonNull String name) {
        super(name);
        this.maxConcurrentAgents = 10;
        this.maxProvisionsPerMinute = ProvisionRateLimiter.DEFAULT_MAX_PROVISIONS_PER_MINUTE;
        this.minProvisionIntervalMs = ProvisionRateLimiter.DEFAULT_MIN_INTERVAL_MS;
        this.rateLimitEnabled = true;
        this.templates = new ArrayList<>();
    }

    @NonNull
    public String getDockerHost() {
        return dockerHost != null ? dockerHost : "";
    }

    @DataBoundSetter
    public void setDockerHost(String dockerHost) {
        this.dockerHost = dockerHost;
    }

    @Nullable
    public String getCredentialsId() {
        return credentialsId;
    }

    @DataBoundSetter
    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    @Nullable
    public String getJenkinsUrl() {
        return jenkinsUrl;
    }

    @DataBoundSetter
    public void setJenkinsUrl(String jenkinsUrl) {
        this.jenkinsUrl = jenkinsUrl;
    }

    @Nullable
    public String getSwarmNetwork() {
        return swarmNetwork;
    }

    @DataBoundSetter
    public void setSwarmNetwork(String swarmNetwork) {
        this.swarmNetwork = swarmNetwork;
    }

    public int getMaxConcurrentAgents() {
        return maxConcurrentAgents;
    }

    @DataBoundSetter
    public void setMaxConcurrentAgents(int maxConcurrentAgents) {
        this.maxConcurrentAgents = maxConcurrentAgents > 0 ? maxConcurrentAgents : 10;
    }

    public int getMaxProvisionsPerMinute() {
        return maxProvisionsPerMinute > 0 ? maxProvisionsPerMinute : ProvisionRateLimiter.DEFAULT_MAX_PROVISIONS_PER_MINUTE;
    }

    @DataBoundSetter
    public void setMaxProvisionsPerMinute(int maxProvisionsPerMinute) {
        this.maxProvisionsPerMinute = maxProvisionsPerMinute > 0 ? maxProvisionsPerMinute : ProvisionRateLimiter.DEFAULT_MAX_PROVISIONS_PER_MINUTE;
    }

    public long getMinProvisionIntervalMs() {
        return minProvisionIntervalMs > 0 ? minProvisionIntervalMs : ProvisionRateLimiter.DEFAULT_MIN_INTERVAL_MS;
    }

    @DataBoundSetter
    public void setMinProvisionIntervalMs(long minProvisionIntervalMs) {
        this.minProvisionIntervalMs = minProvisionIntervalMs > 0 ? minProvisionIntervalMs : ProvisionRateLimiter.DEFAULT_MIN_INTERVAL_MS;
    }

    public boolean isRateLimitEnabled() {
        return rateLimitEnabled;
    }

    @DataBoundSetter
    public void setRateLimitEnabled(boolean rateLimitEnabled) {
        this.rateLimitEnabled = rateLimitEnabled;
    }

    @NonNull
    public List<SwarmAgentTemplate> getTemplates() {
        return templates != null ? Collections.unmodifiableList(templates) : Collections.emptyList();
    }

    @DataBoundSetter
    public void setTemplates(List<SwarmAgentTemplate> templates) {
        this.templates = templates != null ? new ArrayList<>(templates) : new ArrayList<>();
        for (SwarmAgentTemplate template : this.templates) {
            template.setParent(this);
        }
    }

    /**
     * Returns the effective Jenkins URL for agents to connect to.
     */
    @NonNull
    public String getEffectiveJenkinsUrl() {
        if (jenkinsUrl != null && !jenkinsUrl.isBlank()) {
            return jenkinsUrl;
        }
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins != null) {
            String rootUrl = jenkins.getRootUrl();
            if (rootUrl != null) {
                return rootUrl;
            }
        }
        return "http://localhost:8080/";
    }

    /**
     * Gets the Docker Swarm client, creating one if necessary.
     */
    @NonNull
    public synchronized DockerSwarmClient getDockerClient() {
        if (dockerClient == null) {
            dockerClient = new DockerSwarmClient(dockerHost, credentialsId);
        }
        return dockerClient;
    }

    /**
     * Finds a template matching the given label.
     */
    @Nullable
    public SwarmAgentTemplate getTemplate(@Nullable Label label) {
        for (SwarmAgentTemplate template : getTemplates()) {
            if (template.matches(label)) {
                return template;
            }
        }
        return null;
    }

    /**
     * Finds a template by name.
     * Used for template inheritance resolution.
     */
    @Nullable
    public SwarmAgentTemplate getTemplateByName(@NonNull String name) {
        for (SwarmAgentTemplate template : getTemplates()) {
            if (name.equals(template.getName())) {
                return template;
            }
        }
        return null;
    }

    /**
     * Checks if we can provision more agents.
     */
    public boolean canProvision() {
        int currentAgents = countCurrentAgents();
        return currentAgents < maxConcurrentAgents;
    }

    /**
     * Counts the current number of agents provisioned by this cloud.
     */
    public int countCurrentAgents() {
        int count = 0;
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins != null) {
            for (Node node : jenkins.getNodes()) {
                if (node instanceof SwarmAgent) {
                    SwarmAgent agent = (SwarmAgent) node;
                    if (name.equals(agent.getCloudName())) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    @Override
    public boolean canProvision(@NonNull Cloud.CloudState state) {
        // Check if Jenkins is shutting down
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null || jenkins.isQuietingDown() || jenkins.isTerminating()) {
            LOGGER.log(Level.FINE, "Not provisioning: Jenkins is shutting down or in quiet mode");
            return false;
        }

        Label label = state.getLabel();
        LOGGER.log(Level.FINE, "canProvision called for cloud ''{0}'' with label: {1}, templates count: {2}",
                new Object[]{name, label, templates != null ? templates.size() : 0});

        if (!canProvision()) {
            LOGGER.log(Level.FINE, "canProvision=false: max agents reached ({0}/{1})",
                    new Object[]{countCurrentAgents(), maxConcurrentAgents});
            return false;
        }

        SwarmAgentTemplate template = getTemplate(label);
        if (template == null) {
            // Log available templates for debugging
            if (templates != null && !templates.isEmpty()) {
                for (SwarmAgentTemplate t : templates) {
                    LOGGER.log(Level.FINE, "Available template: name=''{0}'', label=''{1}''",
                            new Object[]{t.getName(), t.getLabelString()});
                }
            }
            LOGGER.log(Level.FINE, "canProvision=false: no template found for label ''{0}''", label);
            return false;
        }
        LOGGER.log(Level.FINE, "Found matching template: ''{0}'' for label ''{1}''",
                new Object[]{template.getName(), label});

        // Check rate limit
        if (rateLimitEnabled && !ProvisionRateLimiter.canProvision(name, getMaxProvisionsPerMinute(), getMinProvisionIntervalMs())) {
            LOGGER.log(Level.FINE, "Provision rate limited for cloud: {0}", name);
            return false;
        }
        return true;
    }

    @Override
    @NonNull
    public Collection<NodeProvisioner.PlannedNode> provision(@NonNull Cloud.CloudState state, int excessWorkload) {
        List<NodeProvisioner.PlannedNode> plannedNodes = new ArrayList<>();
        Label label = state.getLabel();

        // Double-check if Jenkins is shutting down (in case canProvision was called earlier)
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null || jenkins.isQuietingDown() || jenkins.isTerminating()) {
            LOGGER.log(Level.FINE, "Skipping provision: Jenkins is shutting down or in quiet mode");
            return Collections.emptyList();
        }

        LOGGER.log(Level.FINE, "Provision requested for label: {0}, excessWorkload: {1}",
                new Object[]{label, excessWorkload});

        // Check rate limit
        if (rateLimitEnabled && !ProvisionRateLimiter.canProvision(name, getMaxProvisionsPerMinute(), getMinProvisionIntervalMs())) {
            long waitTime = ProvisionRateLimiter.getWaitTime(name, getMaxProvisionsPerMinute(), getMinProvisionIntervalMs());
            LOGGER.log(Level.FINE, "Provision rate limited for cloud: {0}, wait time: {1}ms", new Object[]{name, waitTime});
            return Collections.emptyList();
        }

        SwarmAgentTemplate template = getTemplate(label);
        if (template == null) {
            LOGGER.log(Level.WARNING, "No template found for label: {0}", label);
            return Collections.emptyList();
        }

        int availableCapacity = maxConcurrentAgents - countCurrentAgents();
        int toProvision = Math.min(excessWorkload, availableCapacity);
        toProvision = Math.min(toProvision, template.getAvailableCapacity());

        // Apply rate limit to number of provisions
        if (rateLimitEnabled) {
            int maxAllowed = getMaxProvisionsPerMinute() - ProvisionRateLimiter.getInfo(name).getProvisionCount();
            toProvision = Math.min(toProvision, Math.max(1, maxAllowed));
        }

        LOGGER.log(Level.FINE, "Will provision {0} agents using template: {1}",
                new Object[]{toProvision, template.getName()});

        for (int i = 0; i < toProvision; i++) {
            String agentName = template.generateAgentName();
            plannedNodes.add(new NodeProvisioner.PlannedNode(
                    agentName,
                    Computer.threadPoolForRemoting.submit(new ProvisioningCallback(this, template, agentName)),
                    template.getNumExecutors()
            ));
            // Record provision for rate limiting
            if (rateLimitEnabled) {
                ProvisionRateLimiter.recordProvision(name);
            }
        }

        return plannedNodes;
    }

    /**
     * Callback for provisioning an agent with retry support and audit logging.
     */
    private static class ProvisioningCallback implements Callable<Node> {
        private final SwarmCloud cloud;
        private final SwarmAgentTemplate template;
        private final String agentName;

        ProvisioningCallback(SwarmCloud cloud, SwarmAgentTemplate template, String agentName) {
            this.cloud = cloud;
            this.template = template;
            this.agentName = agentName;
        }

        @Override
        public Node call() throws Exception {
            LOGGER.log(Level.FINE, "Provisioning agent: {0}", agentName);

            // Resolve template inheritance
            SwarmAgentTemplate resolvedTemplate = template.resolve();

            // Get retry configuration from template
            int maxRetries = resolvedTemplate.getProvisionRetryCount();
            long baseDelayMs = resolvedTemplate.getProvisionRetryDelayMs();
            int idleTimeoutMinutes = resolvedTemplate.getIdleTimeoutMinutes();

            Exception lastException = null;

            for (int attempt = 0; attempt <= maxRetries; attempt++) {
                try {
                    if (attempt > 0) {
                        // Exponential backoff: baseDelay * 2^(attempt-1)
                        long delayMs = baseDelayMs * (1L << (attempt - 1));
                        // Cap at 30 seconds
                        delayMs = Math.min(delayMs, 30000L);
                        LOGGER.log(Level.FINE, "Retry attempt {0}/{1} for agent {2}, waiting {3}ms",
                                new Object[]{attempt, maxRetries, agentName, delayMs});
                        Thread.sleep(delayMs);
                    }

                    // Create Docker Swarm service
                    String serviceId = cloud.getDockerClient().createService(
                            agentName,
                            resolvedTemplate,
                            cloud.getEffectiveJenkinsUrl(),
                            cloud.getSwarmNetwork()
                    );

                    LOGGER.log(Level.FINE, "Created Docker Swarm service: {0} for agent: {1}",
                            new Object[]{serviceId, agentName});

                    // Create Jenkins agent with idle timeout from template
                    SwarmAgent agent = new SwarmAgent(
                            agentName,
                            resolvedTemplate,
                            cloud.name,
                            serviceId,
                            idleTimeoutMinutes
                    );

                    // Reset failure count on success
                    if (cloud.isRateLimitEnabled()) {
                        ProvisionRateLimiter.resetFailures(cloud.name);
                    }

                    // Audit log success
                    SwarmAuditLog.logProvision(cloud.name, template.getName(), agentName, serviceId);

                    return agent;

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw e;
                } catch (Exception e) {
                    lastException = e;
                    LOGGER.log(Level.WARNING, "Provision attempt {0}/{1} failed for agent {2}: {3}",
                            new Object[]{attempt + 1, maxRetries + 1, agentName, e.getMessage()});
                }
            }

            // All retries exhausted
            LOGGER.log(Level.SEVERE, "Failed to provision agent after " + (maxRetries + 1) + " attempts: " + agentName, lastException);

            // Record failure for rate limiting
            if (cloud.isRateLimitEnabled()) {
                ProvisionRateLimiter.recordFailure(cloud.name);
            }

            // Audit log failure
            String errorMsg = lastException != null ? lastException.getMessage() : "Unknown error";
            SwarmAuditLog.logProvisionFailure(cloud.name, template.getName(), errorMsg);

            throw lastException != null ? lastException : new Exception("Failed to provision agent");
        }
    }

    @Extension
    @Symbol("swarmAgentsCloud")
    public static class DescriptorImpl extends Descriptor<Cloud> {

        @NonNull
        @Override
        public String getDisplayName() {
            return "Docker Swarm Agents Cloud";
        }

        private static final Pattern DOCKER_HOST_PATTERN = Pattern.compile(
                "^(tcp|unix|npipe)://[^\\s]+$", Pattern.CASE_INSENSITIVE);

        @POST
        public FormValidation doTestConnection(
                @QueryParameter("dockerHost") String dockerHost,
                @QueryParameter("credentialsId") String credentialsId) {

            Jenkins.get().checkPermission(Jenkins.ADMINISTER);

            if (dockerHost == null || dockerHost.isBlank()) {
                return FormValidation.error("Docker host is required");
            }

            // Validate URL format
            String trimmedHost = dockerHost.trim();
            if (!DOCKER_HOST_PATTERN.matcher(trimmedHost).matches()) {
                if (trimmedHost.startsWith("https://") || trimmedHost.startsWith("http://")) {
                    return FormValidation.error(
                            "Invalid protocol. Use 'tcp://' instead of '%s'. Example: tcp://docker-host:2376",
                            trimmedHost.substring(0, trimmedHost.indexOf("://")));
                }
                return FormValidation.error(
                        "Invalid Docker host format. Expected: tcp://host:port, unix:///var/run/docker.sock, or npipe:////./pipe/docker_engine");
            }

            try {
                DockerSwarmClient client = new DockerSwarmClient(trimmedHost, credentialsId);
                String version = client.getSwarmVersion();
                int nodes = client.getNodeCount();
                return FormValidation.ok("Connected to Docker Swarm. Version: %s, Nodes: %d", version, nodes);
            } catch (Exception e) {
                return handleConnectionError(e, trimmedHost, credentialsId);
            }
        }

        private FormValidation handleConnectionError(Exception e, String dockerHost, String credentialsId) {
            Throwable cause = e;
            while (cause.getCause() != null && cause.getCause() != cause) {
                cause = cause.getCause();
            }

            if (cause instanceof SSLHandshakeException) {
                if (credentialsId == null || credentialsId.isBlank()) {
                    return FormValidation.error(
                            "TLS/SSL handshake failed. The Docker host requires TLS authentication. " +
                            "Please select Docker Server Credentials with client certificate.");
                }
                return FormValidation.error(
                        "TLS/SSL certificate error: %s. Check that credentials contain valid certificates " +
                        "matching the Docker host CA.", cause.getMessage());
            }

            if (cause instanceof ConnectException) {
                return FormValidation.error(
                        "Connection refused. Please verify: (1) Docker daemon is running, " +
                        "(2) Host and port are correct, (3) Docker API is exposed (not just Docker socket).");
            }

            if (cause instanceof UnknownHostException) {
                return FormValidation.error(
                        "Unknown host: '%s'. Please check the hostname or IP address.", cause.getMessage());
            }

            if (cause instanceof SocketTimeoutException) {
                return FormValidation.error(
                        "Connection timed out. The Docker host may be unreachable or behind a firewall.");
            }

            // Check for Swarm-specific errors
            String message = e.getMessage();
            if (message != null) {
                if (message.contains("This node is not a swarm manager")) {
                    return FormValidation.error(
                            "Docker is running but Swarm mode is not enabled. " +
                            "Initialize Swarm with: docker swarm init");
                }
                if (message.contains("Unsupported protocol scheme")) {
                    return FormValidation.error(
                            "Invalid protocol scheme. Use 'tcp://' for remote connections. " +
                            "Example: tcp://docker-host:2376");
                }
            }

            // Generic error with sanitized message
            String sanitizedMessage = message != null ? message.replaceAll("[<>&'\"]", "") : "Unknown error";
            return FormValidation.error("Connection failed: %s", sanitizedMessage);
        }

        /**
         * Fills the credentials dropdown with available Docker server credentials.
         *
         * @param dockerHost The Docker host URL for domain requirements
         * @return ListBoxModel with available credentials
         */
        public ListBoxModel doFillCredentialsIdItems(
                @AncestorInPath Item item,
                @QueryParameter String dockerHost) {

            StandardListBoxModel result = new StandardListBoxModel();

            if (item == null) {
                if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                    return result.includeCurrentValue("");
                }
            } else {
                if (!item.hasPermission(Item.EXTENDED_READ)
                        && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
                    return result.includeCurrentValue("");
                }
            }

            result.includeEmptyValue();
            result.includeMatchingAs(
                    item instanceof hudson.model.Queue.Task
                            ? ((hudson.model.Queue.Task) item).getDefaultAuthentication()
                            : ACL.SYSTEM,
                    item,
                    DockerServerCredentials.class,
                    dockerHost != null && !dockerHost.isBlank()
                            ? URIRequirementBuilder.fromUri(dockerHost).build()
                            : URIRequirementBuilder.create().build(),
                    CredentialsMatchers.always()
            );

            return result;
        }

        /**
         * Validates the selected credentials.
         */
        public FormValidation doCheckCredentialsId(
                @AncestorInPath Item item,
                @QueryParameter String value,
                @QueryParameter String dockerHost) {

            if (item == null) {
                if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                    return FormValidation.ok();
                }
            } else {
                if (!item.hasPermission(Item.EXTENDED_READ)
                        && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
                    return FormValidation.ok();
                }
            }

            if (value == null || value.isBlank()) {
                return FormValidation.ok(); // Credentials are optional
            }

            if (CredentialsProvider.listCredentials(
                    DockerServerCredentials.class,
                    item,
                    item instanceof hudson.model.Queue.Task
                            ? ((hudson.model.Queue.Task) item).getDefaultAuthentication()
                            : ACL.SYSTEM,
                    dockerHost != null && !dockerHost.isBlank()
                            ? URIRequirementBuilder.fromUri(dockerHost).build()
                            : URIRequirementBuilder.create().build(),
                    CredentialsMatchers.withId(value)
            ).isEmpty()) {
                return FormValidation.error("Cannot find currently selected credentials");
            }

            return FormValidation.ok();
        }
    }
}

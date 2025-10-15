package io.jenkins.plugins.swarmcloud;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.swarmcloud.api.DockerSwarmClient;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

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
    private List<SwarmAgentTemplate> templates;
    private transient DockerSwarmClient dockerClient;

    @DataBoundConstructor
    public SwarmCloud(@NonNull String name) {
        super(name);
        this.maxConcurrentAgents = 10;
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
        Label label = state.getLabel();
        return canProvision() && getTemplate(label) != null;
    }

    @Override
    @NonNull
    public Collection<NodeProvisioner.PlannedNode> provision(@NonNull Cloud.CloudState state, int excessWorkload) {
        List<NodeProvisioner.PlannedNode> plannedNodes = new ArrayList<>();
        Label label = state.getLabel();

        LOGGER.log(Level.INFO, "Provision requested for label: {0}, excessWorkload: {1}",
                new Object[]{label, excessWorkload});

        SwarmAgentTemplate template = getTemplate(label);
        if (template == null) {
            LOGGER.log(Level.WARNING, "No template found for label: {0}", label);
            return Collections.emptyList();
        }

        int availableCapacity = maxConcurrentAgents - countCurrentAgents();
        int toProvision = Math.min(excessWorkload, availableCapacity);
        toProvision = Math.min(toProvision, template.getAvailableCapacity());

        LOGGER.log(Level.INFO, "Will provision {0} agents using template: {1}",
                new Object[]{toProvision, template.getName()});

        for (int i = 0; i < toProvision; i++) {
            String agentName = template.generateAgentName();
            plannedNodes.add(new NodeProvisioner.PlannedNode(
                    agentName,
                    Computer.threadPoolForRemoting.submit(new ProvisioningCallback(this, template, agentName)),
                    template.getNumExecutors()
            ));
        }

        return plannedNodes;
    }

    /**
     * Callback for provisioning an agent.
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
            LOGGER.log(Level.INFO, "Provisioning agent: {0}", agentName);

            try {
                // Create Docker Swarm service
                String serviceId = cloud.getDockerClient().createService(
                        agentName,
                        template,
                        cloud.getEffectiveJenkinsUrl(),
                        cloud.getSwarmNetwork()
                );

                LOGGER.log(Level.INFO, "Created Docker Swarm service: {0} for agent: {1}",
                        new Object[]{serviceId, agentName});

                // Create Jenkins agent
                SwarmAgent agent = new SwarmAgent(
                        agentName,
                        template,
                        cloud.name,
                        serviceId
                );

                return agent;
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to provision agent: " + agentName, e);
                throw e;
            }
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

        @POST
        public FormValidation doTestConnection(
                @QueryParameter("dockerHost") String dockerHost,
                @QueryParameter("credentialsId") String credentialsId) {

            Jenkins.get().checkPermission(Jenkins.ADMINISTER);

            if (dockerHost == null || dockerHost.isBlank()) {
                return FormValidation.error("Docker host is required");
            }

            try {
                DockerSwarmClient client = new DockerSwarmClient(dockerHost, credentialsId);
                String version = client.getSwarmVersion();
                int nodes = client.getNodeCount();
                return FormValidation.ok("Connected to Docker Swarm. Version: %s, Nodes: %d", version, nodes);
            } catch (Exception e) {
                return FormValidation.error("Failed to connect: " + e.getMessage());
            }
        }

        public ListBoxModel doFillCredentialsIdItems() {
            Jenkins jenkins = Jenkins.getInstanceOrNull();
            if (jenkins == null || !jenkins.hasPermission(Jenkins.ADMINISTER)) {
                return new ListBoxModel();
            }

            ListBoxModel items = new ListBoxModel();
            items.add("- none -", "");
            // TODO: Add credentials lookup
            return items;
        }
    }
}

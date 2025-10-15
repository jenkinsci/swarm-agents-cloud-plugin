package io.jenkins.plugins.swarmcloud;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.AbstractCloudComputer;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.CloudRetentionStrategy;
import hudson.slaves.RetentionStrategy;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Jenkins agent running on Docker Swarm.
 */
public class SwarmAgent extends AbstractCloudSlave {

    private static final Logger LOGGER = Logger.getLogger(SwarmAgent.class.getName());

    private final String cloudName;
    private final String serviceId;
    private final String templateName;

    @DataBoundConstructor
    public SwarmAgent(@NonNull String name,
                      @NonNull SwarmAgentTemplate template,
                      @NonNull String cloudName,
                      @NonNull String serviceId) throws Descriptor.FormException, IOException {
        super(
                name,
                template.getRemoteFs(),
                new SwarmComputerLauncher(cloudName, template.getImage()),
                template.getNumExecutors(),
                template.getMode(),
                template.getLabelString(),
                new CloudRetentionStrategy(1) // Terminate after 1 minute idle
        );
        this.cloudName = cloudName;
        this.serviceId = serviceId;
        this.templateName = template.getName();
    }

    @NonNull
    public String getCloudName() {
        return cloudName;
    }

    @NonNull
    public String getServiceId() {
        return serviceId;
    }

    @NonNull
    public String getTemplateName() {
        return templateName;
    }

    /**
     * Gets the cloud that provisioned this agent.
     */
    public SwarmCloud getCloud() {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins != null) {
            for (hudson.slaves.Cloud cloud : jenkins.clouds) {
                if (cloud instanceof SwarmCloud && cloud.name.equals(cloudName)) {
                    return (SwarmCloud) cloud;
                }
            }
        }
        return null;
    }

    @Override
    public AbstractCloudComputer<?> createComputer() {
        return new SwarmComputer(this);
    }

    @Override
    protected void _terminate(TaskListener listener) throws IOException, InterruptedException {
        LOGGER.log(Level.INFO, "Terminating Swarm agent: {0}, service: {1}", new Object[]{name, serviceId});

        SwarmCloud cloud = getCloud();
        if (cloud != null) {
            try {
                cloud.getDockerClient().removeService(serviceId);
                LOGGER.log(Level.INFO, "Removed Docker Swarm service: {0}", serviceId);

                // Decrement instance count on template
                SwarmAgentTemplate template = cloud.getTemplate(null);
                if (template != null && template.getName().equals(templateName)) {
                    template.decrementInstances();
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to remove Docker Swarm service: " + serviceId, e);
                listener.error("Failed to remove Docker Swarm service: %s", e.getMessage());
            }
        } else {
            LOGGER.log(Level.WARNING, "Cloud not found for agent: {0}", name);
        }
    }

    /**
     * Computer implementation for Swarm agents.
     */
    public static class SwarmComputer extends AbstractCloudComputer<SwarmAgent> {

        public SwarmComputer(SwarmAgent agent) {
            super(agent);
        }

        @Override
        public void taskAccepted(hudson.model.Executor executor, hudson.model.Queue.Task task) {
            super.taskAccepted(executor, task);
            LOGGER.log(Level.FINE, "Task accepted on Swarm agent: {0}", getName());
        }

        @Override
        public void taskCompleted(hudson.model.Executor executor, hudson.model.Queue.Task task, long durationMS) {
            super.taskCompleted(executor, task, durationMS);
            LOGGER.log(Level.FINE, "Task completed on Swarm agent: {0}, duration: {1}ms",
                    new Object[]{getName(), durationMS});
        }
    }

    @Extension
    @Symbol("swarmAgent")
    public static class DescriptorImpl extends SlaveDescriptor {

        @NonNull
        @Override
        public String getDisplayName() {
            return "Docker Swarm Agent";
        }

        @Override
        public boolean isInstantiable() {
            return false; // Agents are created by the cloud
        }
    }
}

package io.jenkins.plugins.swarmcloud;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.AbstractCloudComputer;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.OfflineCause;
import io.jenkins.plugins.swarmcloud.monitoring.SwarmAuditLog;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Jenkins agent running on Docker Swarm.
 */
public class SwarmAgent extends AbstractCloudSlave {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(SwarmAgent.class.getName());
    private static final int DEFAULT_IDLE_MINUTES = 10;

    private final String cloudName;
    private final String serviceId;
    private final String templateName;
    private final long createdTime;

    @DataBoundConstructor
    public SwarmAgent(@NonNull String name,
                      @NonNull SwarmAgentTemplate template,
                      @NonNull String cloudName,
                      @NonNull String serviceId) throws Descriptor.FormException, IOException {
        this(name, template, cloudName, serviceId, DEFAULT_IDLE_MINUTES);
    }

    /**
     * Constructor with custom idle timeout.
     * Connection timeout is taken from the template configuration.
     */
    public SwarmAgent(@NonNull String name,
                      @NonNull SwarmAgentTemplate template,
                      @NonNull String cloudName,
                      @NonNull String serviceId,
                      int idleMinutes) throws Descriptor.FormException, IOException {
        super(
                name,
                "Swarm Agent " + name,
                template.getRemoteFs(),
                template.getNumExecutors(),
                template.getMode(),
                template.getLabelString(),
                new SwarmComputerLauncher(
                        cloudName,
                        template.getImage(),
                        true, // useWebSocket
                        null, // tunnel
                        template.getRemoteFs(),
                        template.getConnectionTimeoutSeconds()
                ),
                new SwarmRetentionStrategy(idleMinutes > 0 ? idleMinutes : DEFAULT_IDLE_MINUTES),
                java.util.Collections.emptyList()
        );
        this.cloudName = cloudName;
        this.serviceId = serviceId;
        this.templateName = template.getName();
        this.createdTime = System.currentTimeMillis();
    }

    /**
     * Called after deserialization to restore transient state.
     * Jenkins requires this to properly initialize the agent after loading from disk.
     */
    @Override
    protected Object readResolve() {
        // Call parent implementation to properly restore agent state
        return super.readResolve();
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

    public long getCreatedTime() {
        return createdTime;
    }

    /**
     * Gets the cloud that provisioned this agent.
     */
    @Nullable
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

    /**
     * Gets the template that was used to create this agent.
     */
    @Nullable
    public SwarmAgentTemplate getTemplate() {
        SwarmCloud cloud = getCloud();
        if (cloud != null) {
            for (SwarmAgentTemplate template : cloud.getTemplates()) {
                if (template.getName().equals(templateName)) {
                    return template;
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
        PrintStream logger = listener.getLogger();
        logger.println("Terminating Swarm agent: " + name);
        LOGGER.log(Level.INFO, "Terminating Swarm agent: {0}, service: {1}", new Object[]{name, serviceId});

        // Determine termination reason
        String terminationReason = "Manual termination";
        Computer computer = toComputer();
        if (computer != null) {
            if (computer.isIdle()) {
                terminationReason = "Idle timeout";
            } else if (computer.isOffline()) {
                var offlineCause = computer.getOfflineCause();
                if (offlineCause != null) {
                    terminationReason = offlineCause.toString();
                }
            }
        }

        SwarmCloud cloud = getCloud();
        if (cloud != null) {
            // Decrement instance count on template
            SwarmAgentTemplate template = getTemplate();
            if (template != null) {
                template.decrementInstances();
                LOGGER.log(Level.FINE, "Decremented instance count for template: {0}", templateName);
            }

            // Remove Docker Swarm service
            try {
                cloud.getDockerClient().removeService(serviceId);
                logger.println("Removed Docker Swarm service: " + serviceId);
                LOGGER.log(Level.INFO, "Removed Docker Swarm service: {0}", serviceId);

                // Audit log termination
                SwarmAuditLog.logTermination(cloudName, name, serviceId, terminationReason);
            } catch (Exception e) {
                // Service might already be removed
                if (e.getMessage() != null && e.getMessage().contains("not found")) {
                    logger.println("Service already removed: " + serviceId);
                    LOGGER.log(Level.FINE, "Service already removed: {0}", serviceId);
                    // Still log termination
                    SwarmAuditLog.logTermination(cloudName, name, serviceId, "Service already removed");
                } else {
                    LOGGER.log(Level.WARNING, "Failed to remove Docker Swarm service: " + serviceId, e);
                    logger.println("Warning: Failed to remove service: " + e.getMessage());
                }
            }
        } else {
            LOGGER.log(Level.WARNING, "Cloud not found for agent: {0}, cloud name: {1}",
                    new Object[]{name, cloudName});
            logger.println("Warning: Cloud not found, service may need manual cleanup: " + serviceId);
            // Still log termination for audit
            SwarmAuditLog.logTermination(cloudName, name, serviceId, "Cloud not found");
        }
    }

    /**
     * Computer implementation for Swarm agents.
     */
    public static class SwarmComputer extends AbstractCloudComputer<SwarmAgent> {

        private static final Logger COMPUTER_LOGGER = Logger.getLogger(SwarmComputer.class.getName());

        public SwarmComputer(SwarmAgent agent) {
            super(agent);
        }

        @Override
        public void taskAccepted(hudson.model.Executor executor, hudson.model.Queue.Task task) {
            super.taskAccepted(executor, task);
            COMPUTER_LOGGER.log(Level.FINE, "Task accepted on Swarm agent: {0}, task: {1}",
                    new Object[]{getName(), task.getFullDisplayName()});
        }

        @Override
        public void taskCompleted(hudson.model.Executor executor, hudson.model.Queue.Task task, long durationMS) {
            super.taskCompleted(executor, task, durationMS);
            COMPUTER_LOGGER.log(Level.FINE, "Task completed on Swarm agent: {0}, task: {1}, duration: {2}ms",
                    new Object[]{getName(), task.getFullDisplayName(), durationMS});
        }

        @Override
        public void taskCompletedWithProblems(hudson.model.Executor executor,
                                               hudson.model.Queue.Task task,
                                               long durationMS,
                                               Throwable problems) {
            super.taskCompletedWithProblems(executor, task, durationMS, problems);
            COMPUTER_LOGGER.log(Level.WARNING, "Task completed with problems on Swarm agent: {0}, task: {1}",
                    new Object[]{getName(), task.getFullDisplayName()});
        }

        /**
         * Gets the agent node.
         */
        @Nullable
        @Override
        public SwarmAgent getNode() {
            return (SwarmAgent) super.getNode();
        }

        /**
         * Gets the service ID for this agent.
         */
        @Nullable
        public String getServiceId() {
            SwarmAgent node = getNode();
            return node != null ? node.getServiceId() : null;
        }

        /**
         * Gets the cloud name for this agent.
         */
        @Nullable
        public String getCloudName() {
            SwarmAgent node = getNode();
            return node != null ? node.getCloudName() : null;
        }

        @Override
        public String toString() {
            return "SwarmComputer{" +
                    "name=" + getName() +
                    ", serviceId=" + getServiceId() +
                    ", cloudName=" + getCloudName() +
                    '}';
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

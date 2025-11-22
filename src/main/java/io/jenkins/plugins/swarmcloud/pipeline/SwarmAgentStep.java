package io.jenkins.plugins.swarmcloud.pipeline;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.model.TaskListener;
import hudson.slaves.Cloud;
import io.jenkins.plugins.swarmcloud.SwarmAgent;
import io.jenkins.plugins.swarmcloud.SwarmAgentTemplate;
import io.jenkins.plugins.swarmcloud.SwarmCloud;
import io.jenkins.plugins.swarmcloud.monitoring.SwarmAuditLog;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pipeline step to provision a Docker Swarm agent and execute a closure.
 *
 * <p>Usage in Jenkinsfile:</p>
 * <pre>
 * swarmAgent(cloud: 'my-cloud', template: 'maven') {
 *     sh 'mvn clean package'
 * }
 * </pre>
 *
 * <p>Or with inline template configuration:</p>
 * <pre>
 * swarmAgent(cloud: 'my-cloud', image: 'jenkins/inbound-agent:alpine', label: 'build') {
 *     sh 'npm install &amp;&amp; npm test'
 * }
 * </pre>
 *
 * <p>The agent is automatically terminated when the block completes.</p>
 */
public class SwarmAgentStep extends Step implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(SwarmAgentStep.class.getName());

    private final String cloud;
    private String template;
    private String image;
    private String label;
    private int numExecutors;
    private String cpuLimit;
    private String memoryLimit;
    private int idleTimeout;
    private int connectionTimeout;

    @DataBoundConstructor
    public SwarmAgentStep(@NonNull String cloud) {
        this.cloud = cloud;
        this.numExecutors = 1;
        this.idleTimeout = 60; // Keep for 60 mins for pipeline use
        this.connectionTimeout = 300; // 5 minutes default
    }

    @NonNull
    public String getCloud() {
        return cloud;
    }

    @Nullable
    public String getTemplate() {
        return template;
    }

    @DataBoundSetter
    public void setTemplate(String template) {
        this.template = template;
    }

    @Nullable
    public String getImage() {
        return image;
    }

    @DataBoundSetter
    public void setImage(String image) {
        this.image = image;
    }

    @Nullable
    public String getLabel() {
        return label;
    }

    @DataBoundSetter
    public void setLabel(String label) {
        this.label = label;
    }

    public int getNumExecutors() {
        return numExecutors;
    }

    @DataBoundSetter
    public void setNumExecutors(int numExecutors) {
        this.numExecutors = numExecutors > 0 ? numExecutors : 1;
    }

    @Nullable
    public String getCpuLimit() {
        return cpuLimit;
    }

    @DataBoundSetter
    public void setCpuLimit(String cpuLimit) {
        this.cpuLimit = cpuLimit;
    }

    @Nullable
    public String getMemoryLimit() {
        return memoryLimit;
    }

    @DataBoundSetter
    public void setMemoryLimit(String memoryLimit) {
        this.memoryLimit = memoryLimit;
    }

    public int getIdleTimeout() {
        return idleTimeout;
    }

    @DataBoundSetter
    public void setIdleTimeout(int idleTimeout) {
        this.idleTimeout = idleTimeout > 0 ? idleTimeout : 60;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    @DataBoundSetter
    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout > 0 ? connectionTimeout : 300;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new SwarmAgentStepExecution(this, context);
    }

    /**
     * Execution for SwarmAgentStep.
     */
    private static class SwarmAgentStepExecution extends StepExecution {

        private static final long serialVersionUID = 1L;

        private final SwarmAgentStep step;
        private String agentName;
        private String cloudName;

        SwarmAgentStepExecution(SwarmAgentStep step, StepContext context) {
            super(context);
            this.step = step;
            this.cloudName = step.getCloud();
        }

        @Override
        public boolean start() throws Exception {
            TaskListener listener = getContext().get(TaskListener.class);
            PrintStream logger = listener != null ? listener.getLogger() : System.out;

            logger.println("=== Swarm Agent Pipeline Step ===");
            logger.println("Provisioning Swarm agent from cloud: " + step.getCloud());

            // Find the cloud
            Jenkins jenkins = Jenkins.get();
            SwarmCloud swarmCloud = null;
            for (Cloud cloud : jenkins.clouds) {
                if (cloud instanceof SwarmCloud && cloud.name.equals(step.getCloud())) {
                    swarmCloud = (SwarmCloud) cloud;
                    break;
                }
            }

            if (swarmCloud == null) {
                getContext().onFailure(new IllegalArgumentException("Swarm cloud not found: " + step.getCloud()));
                return true;
            }

            // Find or create template
            SwarmAgentTemplate agentTemplate;
            if (step.getTemplate() != null && !step.getTemplate().isBlank()) {
                // Use existing template
                agentTemplate = swarmCloud.getTemplateByName(step.getTemplate());
                if (agentTemplate == null) {
                    getContext().onFailure(new IllegalArgumentException("Template not found: " + step.getTemplate()));
                    return true;
                }
                logger.println("Using template: " + step.getTemplate());
            } else {
                // Create inline template
                String inlineName = "pipeline-" + System.currentTimeMillis();
                agentTemplate = new SwarmAgentTemplate(inlineName);

                if (step.getImage() != null && !step.getImage().isBlank()) {
                    agentTemplate.setImage(step.getImage());
                }
                if (step.getLabel() != null && !step.getLabel().isBlank()) {
                    agentTemplate.setLabelString(step.getLabel());
                }
                agentTemplate.setNumExecutors(step.getNumExecutors());
                if (step.getCpuLimit() != null && !step.getCpuLimit().isBlank()) {
                    agentTemplate.setCpuLimit(step.getCpuLimit());
                }
                if (step.getMemoryLimit() != null && !step.getMemoryLimit().isBlank()) {
                    agentTemplate.setMemoryLimit(step.getMemoryLimit());
                }
                agentTemplate.setMaxInstances(1); // Pipeline agents are single-use
                agentTemplate.setIdleTimeoutMinutes(step.getIdleTimeout());
                agentTemplate.setConnectionTimeoutSeconds(step.getConnectionTimeout());

                logger.println("Using inline template with image: " + agentTemplate.getImage());
            }

            // Check capacity
            if (!swarmCloud.canProvision()) {
                getContext().onFailure(new IllegalStateException(
                        "Cannot provision: max concurrent agents reached for cloud " + step.getCloud()));
                return true;
            }

            // Create service and agent
            agentName = agentTemplate.generateAgentName();
            logger.println("Creating Swarm service for agent: " + agentName);

            try {
                String serviceId = swarmCloud.getDockerClient().createService(
                        agentName,
                        agentTemplate,
                        swarmCloud.getEffectiveJenkinsUrl(),
                        swarmCloud.getSwarmNetwork()
                );

                logger.println("Created Docker Swarm service: " + serviceId);

                SwarmAgent agent = new SwarmAgent(
                        agentName,
                        agentTemplate,
                        swarmCloud.name,
                        serviceId,
                        step.getIdleTimeout()
                );

                jenkins.addNode(agent);
                logger.println("Agent registered: " + agentName);
                logger.println("Agent label: " + (step.getLabel() != null ? step.getLabel() : agentTemplate.getLabelString()));

                // Audit log
                SwarmAuditLog.logProvision(swarmCloud.name, agentTemplate.getName(), agentName, serviceId);

                // Execute the body on the new agent
                // The body will be scheduled on the agent via the label
                getContext().newBodyInvoker()
                        .withCallback(new SwarmAgentCallback(agentName, cloudName))
                        .start();

                return false; // Not complete yet, waiting for body

            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to provision pipeline agent", e);
                SwarmAuditLog.logProvisionFailure(swarmCloud.name, agentTemplate.getName(), e.getMessage());
                getContext().onFailure(e);
                return true;
            }
        }

        @Override
        public void stop(Throwable cause) throws Exception {
            // Terminate the agent when the step is stopped
            terminateAgent();
        }

        @Override
        public void onResume() {
            // Resume is handled automatically
        }

        private void terminateAgent() {
            if (agentName != null) {
                try {
                    Jenkins jenkins = Jenkins.getInstanceOrNull();
                    if (jenkins != null) {
                        hudson.model.Node node = jenkins.getNode(agentName);
                        if (node instanceof SwarmAgent) {
                            jenkins.removeNode(node);
                            LOGGER.log(Level.FINE, "Terminated pipeline agent: {0}", agentName);
                        }
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to terminate agent: " + agentName, e);
                }
            }
        }
    }

    /**
     * Callback to handle agent cleanup after body execution.
     */
    private static class SwarmAgentCallback extends BodyExecutionCallback {

        private static final long serialVersionUID = 1L;

        private final String agentName;
        private final String cloudName;

        SwarmAgentCallback(String agentName, String cloudName) {
            this.agentName = agentName;
            this.cloudName = cloudName;
        }

        @Override
        public void onSuccess(StepContext context, Object result) {
            try {
                TaskListener listener = context.get(TaskListener.class);
                if (listener != null) {
                    listener.getLogger().println("Swarm agent pipeline step completed successfully");
                }
                // Don't terminate immediately - let retention strategy handle it
                // This allows reuse within the same pipeline
                context.onSuccess(result);
            } catch (Exception e) {
                context.onFailure(e);
            }
        }

        @Override
        public void onFailure(StepContext context, Throwable t) {
            try {
                TaskListener listener = context.get(TaskListener.class);
                if (listener != null) {
                    listener.getLogger().println("Swarm agent pipeline step failed: " + t.getMessage());
                }
                terminateAgent();
            } catch (IOException | InterruptedException e) {
                LOGGER.log(Level.FINE, "Cleanup error during onFailure", e);
            }
            context.onFailure(t);
        }

        private void terminateAgent() {
            if (agentName != null) {
                try {
                    Jenkins jenkins = Jenkins.getInstanceOrNull();
                    if (jenkins != null) {
                        hudson.model.Node node = jenkins.getNode(agentName);
                        if (node instanceof SwarmAgent) {
                            jenkins.removeNode(node);
                            LOGGER.log(Level.FINE, "Terminated pipeline agent on failure: {0}", agentName);
                        }
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to terminate agent: " + agentName, e);
                }
            }
        }
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "swarmAgent";
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return "Provision Docker Swarm Agent";
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            Set<Class<?>> context = new HashSet<>();
            context.add(TaskListener.class);
            context.add(FlowNode.class);
            return context;
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
        }
    }
}

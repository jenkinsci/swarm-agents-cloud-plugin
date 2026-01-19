package io.jenkins.plugins.swarmcloud;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.SlaveComputer;
import jenkins.model.Jenkins;
import jenkins.slaves.JnlpSlaveAgentProtocol;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Launcher for Docker Swarm agents.
 * Extends JNLPLauncher to allow inbound agent connections via WebSocket or JNLP.
 */
public class SwarmComputerLauncher extends JNLPLauncher {

    private static final Logger LOGGER = Logger.getLogger(SwarmComputerLauncher.class.getName());
    private static final int DEFAULT_TIMEOUT_SECONDS = 300; // 5 minutes
    private static final int CHECK_INTERVAL_MS = 5000; // 5 seconds

    private final String cloudName;
    private final String image;
    private final boolean useWebSocket;
    private final String workDir;
    private final int connectionTimeoutSeconds;

    @SuppressFBWarnings(value = "NP_METHOD_PARAMETER_TIGHTENS_ANNOTATION",
            justification = "Convenience constructor delegates to main constructor with null defaults")
    public SwarmComputerLauncher(@NonNull String cloudName, @NonNull String image) {
        this(cloudName, image, true, null, null, DEFAULT_TIMEOUT_SECONDS);
    }

    public SwarmComputerLauncher(@NonNull String cloudName,
                                  @NonNull String image,
                                  boolean useWebSocket,
                                  @CheckForNull String tunnel,
                                  @CheckForNull String workDir) {
        this(cloudName, image, useWebSocket, tunnel, workDir, DEFAULT_TIMEOUT_SECONDS);
    }

    public SwarmComputerLauncher(@NonNull String cloudName,
                                  @NonNull String image,
                                  boolean useWebSocket,
                                  @CheckForNull String tunnel,
                                  @CheckForNull String workDir,
                                  int connectionTimeoutSeconds) {
        super(tunnel, null); // Pass tunnel to JNLPLauncher
        this.cloudName = cloudName;
        this.image = image;
        this.useWebSocket = useWebSocket;
        this.workDir = workDir;
        this.connectionTimeoutSeconds = connectionTimeoutSeconds > 0 ? connectionTimeoutSeconds : DEFAULT_TIMEOUT_SECONDS;
    }

    @Override
    public void launch(SlaveComputer computer, TaskListener listener) {
        PrintStream logger = listener.getLogger();

        try {
            if (!(computer instanceof SwarmAgent.SwarmComputer)) {
                throw new IllegalArgumentException("Expected SwarmComputer, got: " + computer.getClass().getName());
            }

            SwarmAgent.SwarmComputer swarmComputer = (SwarmAgent.SwarmComputer) computer;
            SwarmAgent agent = swarmComputer.getNode();

            if (agent == null) {
                logger.println("ERROR: Agent node is null");
                LOGGER.log(Level.SEVERE, "Agent node is null for computer: {0}", computer.getName());
                return;
            }

            logger.println("=== Swarm Agent Launch ===");
            logger.println("Agent name: " + agent.getNodeName());
            logger.println("Service ID: " + agent.getServiceId());
            logger.println("Image: " + image);
            logger.println("Connection mode: " + (useWebSocket ? "WebSocket" : "JNLP/TCP"));

            LOGGER.log(Level.FINE, "Launching Swarm agent: {0}, service: {1}, webSocket: {2}",
                    new Object[]{agent.getNodeName(), agent.getServiceId(), useWebSocket});

            // The Docker container is already started by the provision() method
            // Agent will connect back to Jenkins using the secret embedded in environment variables
            logger.println("Waiting for agent to connect (timeout: " + connectionTimeoutSeconds + "s)...");

            waitForConnection(computer, listener, connectionTimeoutSeconds);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.println("ERROR: Launch interrupted");
            LOGGER.log(Level.WARNING, "Launch interrupted for: " + computer.getName(), e);
        } catch (Exception e) {
            logger.println("ERROR: " + e.getMessage());
            LOGGER.log(Level.SEVERE, "Launch failed for: " + computer.getName(), e);
        }
    }

    /**
     * Waits for the agent to connect.
     */
    @SuppressFBWarnings(value = "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE",
            justification = "Defensive null check for Jenkins API method")
    private void waitForConnection(SlaveComputer computer, TaskListener listener, int timeoutSeconds)
            throws IOException, InterruptedException {

        PrintStream logger = listener.getLogger();
        long startTime = System.currentTimeMillis();
        long timeoutMs = timeoutSeconds * 1000L;
        int checkCount = 0;

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (computer.isOnline()) {
                long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                logger.println("Agent connected successfully after " + elapsed + " seconds");
                LOGGER.log(Level.FINE, "Agent connected: {0} in {1}s", new Object[]{computer.getName(), elapsed});
                return;
            }

            // Check for terminal failure conditions
            if (computer.isOffline()) {
                String offlineCause = computer.getOfflineCauseReason();
                if (offlineCause != null && !offlineCause.isEmpty()
                        && !offlineCause.contains("Waiting for")) {
                    LOGGER.log(Level.WARNING, "Agent offline with cause: {0}", offlineCause);
                }
            }

            // Check service status periodically
            if (++checkCount % 6 == 0) { // Every 30 seconds
                SwarmAgent agent = (SwarmAgent) computer.getNode();
                if (agent != null) {
                    SwarmCloud cloud = agent.getCloud();
                    if (cloud != null) {
                        checkServiceHealth(cloud, agent.getServiceId(), logger);
                    }
                }
            }

            Thread.sleep(CHECK_INTERVAL_MS);

            long elapsed = (System.currentTimeMillis() - startTime) / 1000;
            if (elapsed % 30 == 0) { // Log every 30 seconds
                logger.println("Still waiting for agent to connect... (" + elapsed + "s elapsed)");
            }
        }

        // Timeout - try to get more information
        String errorMsg = "Timeout waiting for agent to connect after " + timeoutSeconds + " seconds";
        LOGGER.log(Level.SEVERE, errorMsg);
        logger.println("ERROR: " + errorMsg);

        // Try to get service logs for debugging
        SwarmAgent agent = (SwarmAgent) computer.getNode();
        if (agent != null) {
            SwarmCloud cloud = agent.getCloud();
            if (cloud != null) {
                try {
                    String logs = cloud.getDockerClient().getServiceLogs(agent.getServiceId(), 50);
                    if (logs != null && !logs.isEmpty()) {
                        logger.println("=== Service Logs ===");
                        logger.println(logs);
                    }
                } catch (Exception e) {
                    logger.println("Could not retrieve service logs: " + e.getMessage());
                }
            }
        }

        throw new IOException(errorMsg);
    }

    /**
     * Checks the health of the Docker Swarm service.
     */
    private void checkServiceHealth(SwarmCloud cloud, String serviceId, PrintStream logger) {
        try {
            var service = cloud.getDockerClient().getService(serviceId);
            if (service == null) {
                logger.println("WARNING: Service not found - may have been terminated");
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Error checking service health", e);
        }
    }

    @Override
    public void afterDisconnect(SlaveComputer computer, TaskListener listener) {
        LOGGER.log(Level.FINE, "Agent disconnected: {0}", computer.getName());
    }

    @Override
    public void beforeDisconnect(SlaveComputer computer, TaskListener listener) {
        LOGGER.log(Level.FINE, "Agent about to disconnect: {0}", computer.getName());
    }

    /**
     * Gets the secret for an agent from Jenkins.
     */
    @NonNull
    public static String getAgentSecret(@NonNull String agentName) {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            throw new IllegalStateException("Jenkins instance is not available");
        }

        // Use Jenkins' built-in secret generation
        return JnlpSlaveAgentProtocol.SLAVE_SECRET.mac(agentName);
    }

    /**
     * Builds the command for the inbound agent container.
     */
    @NonNull
    public static String[] buildAgentCommand(@NonNull String jenkinsUrl,
                                              @NonNull String agentName,
                                              @NonNull String secret,
                                              boolean useWebSocket,
                                              @CheckForNull String workDir) {
        List<String> args = new ArrayList<>();

        args.add("-url");
        args.add(jenkinsUrl);

        if (useWebSocket) {
            args.add("-webSocket");
        }

        args.add("-name");
        args.add(agentName);

        args.add("-secret");
        args.add(secret);

        if (workDir != null && !workDir.isBlank()) {
            args.add("-workDir");
            args.add(workDir);
        }

        return args.toArray(new String[0]);
    }

    /**
     * Builds environment variables for the agent container.
     */
    @NonNull
    public static Map<String, String> buildAgentEnvironment(@NonNull String jenkinsUrl,
                                                             @NonNull String agentName,
                                                             @NonNull String secret,
                                                             boolean useWebSocket,
                                                             @CheckForNull String workDir) {
        Map<String, String> env = new LinkedHashMap<>();

        env.put("JENKINS_URL", jenkinsUrl);
        env.put("JENKINS_AGENT_NAME", agentName);
        env.put("JENKINS_SECRET", secret);

        if (useWebSocket) {
            env.put("JENKINS_WEB_SOCKET", "true");
        }

        if (workDir != null && !workDir.isBlank()) {
            env.put("JENKINS_AGENT_WORKDIR", workDir);
        }

        // Direct connection mode (recommended for Docker Swarm)
        env.put("JENKINS_DIRECT_CONNECTION", jenkinsUrl.replace("http://", "").replace("https://", ""));

        return env;
    }

    /**
     * Gets the JNLP URL for legacy connections.
     */
    @NonNull
    public static String getJnlpUrl(@NonNull String jenkinsUrl, @NonNull String agentName) {
        String baseUrl = jenkinsUrl.endsWith("/") ? jenkinsUrl : jenkinsUrl + "/";
        return baseUrl + "computer/" + agentName + "/jenkins-agent.jnlp";
    }

    public String getCloudName() {
        return cloudName;
    }

    public String getImage() {
        return image;
    }

    public boolean isUseWebSocket() {
        return useWebSocket;
    }

    public String getWorkDir() {
        return workDir;
    }

    public int getConnectionTimeoutSeconds() {
        return connectionTimeoutSeconds;
    }

    /**
     * Descriptor for SwarmComputerLauncher.
     * This descriptor is required for Jenkins to recognize the launcher.
     */
    @Extension
    public static class DescriptorImpl extends Descriptor<ComputerLauncher> {

        @NonNull
        @Override
        public String getDisplayName() {
            return "Docker Swarm Agent Launcher";
        }
    }
}

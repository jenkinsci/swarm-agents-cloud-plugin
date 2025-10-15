package io.jenkins.plugins.swarmcloud;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Launcher for Docker Swarm agents.
 * Agents connect via WebSocket (preferred) or JNLP.
 */
public class SwarmComputerLauncher extends ComputerLauncher {

    private static final Logger LOGGER = Logger.getLogger(SwarmComputerLauncher.class.getName());

    private final String cloudName;
    private final String image;

    public SwarmComputerLauncher(@NonNull String cloudName, @NonNull String image) {
        this.cloudName = cloudName;
        this.image = image;
    }

    @Override
    public void launch(SlaveComputer computer, TaskListener listener) throws IOException, InterruptedException {
        if (!(computer instanceof SwarmAgent.SwarmComputer)) {
            throw new IllegalArgumentException("Expected SwarmComputer, got: " + computer.getClass().getName());
        }

        SwarmAgent.SwarmComputer swarmComputer = (SwarmAgent.SwarmComputer) computer;
        SwarmAgent agent = swarmComputer.getNode();

        if (agent == null) {
            throw new IOException("Agent node is null");
        }

        listener.getLogger().println("Waiting for Swarm agent to connect: " + agent.getNodeName());
        LOGGER.log(Level.INFO, "Waiting for Swarm agent to connect: {0}, service: {1}",
                new Object[]{agent.getNodeName(), agent.getServiceId()});

        // Agent will connect via WebSocket/JNLP
        // The container startup command includes the connection parameters
        waitForConnection(computer, listener, 300); // 5 minute timeout
    }

    /**
     * Waits for the agent to connect.
     */
    private void waitForConnection(SlaveComputer computer, TaskListener listener, int timeoutSeconds)
            throws IOException, InterruptedException {

        long startTime = System.currentTimeMillis();
        long timeoutMs = timeoutSeconds * 1000L;

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (computer.isOnline()) {
                listener.getLogger().println("Agent connected successfully");
                LOGGER.log(Level.INFO, "Agent connected: {0}", computer.getName());
                return;
            }

            if (computer.isOffline()) {
                String offlineCause = computer.getOfflineCauseReason();
                if (offlineCause != null && !offlineCause.isEmpty()) {
                    listener.getLogger().println("Agent offline: " + offlineCause);
                }
            }

            Thread.sleep(5000); // Check every 5 seconds
            listener.getLogger().println("Still waiting for agent to connect...");
        }

        throw new IOException("Timeout waiting for agent to connect after " + timeoutSeconds + " seconds");
    }

    @Override
    public void afterDisconnect(SlaveComputer computer, TaskListener listener) {
        LOGGER.log(Level.INFO, "Agent disconnected: {0}", computer.getName());
    }

    @Override
    public void beforeDisconnect(SlaveComputer computer, TaskListener listener) {
        LOGGER.log(Level.INFO, "Agent about to disconnect: {0}", computer.getName());
    }

    /**
     * Gets the connection arguments for the agent container.
     */
    @NonNull
    public static String[] getConnectionArgs(@NonNull String jenkinsUrl,
                                              @NonNull String agentName,
                                              @NonNull String secret) {
        // Prefer WebSocket connection (Jenkins 2.217+)
        return new String[]{
                "-url", jenkinsUrl,
                "-name", agentName,
                "-secret", secret,
                "-webSocket" // Use WebSocket for connection
        };
    }

    /**
     * Gets the JNLP URL for legacy connections.
     */
    @NonNull
    public static String getJnlpUrl(@NonNull String jenkinsUrl, @NonNull String agentName) {
        return jenkinsUrl + "computer/" + agentName + "/jenkins-agent.jnlp";
    }

    public String getCloudName() {
        return cloudName;
    }

    public String getImage() {
        return image;
    }
}

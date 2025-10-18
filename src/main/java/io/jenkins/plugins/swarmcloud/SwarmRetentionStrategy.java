package io.jenkins.plugins.swarmcloud;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.slaves.CloudRetentionStrategy;
import hudson.slaves.RetentionStrategy;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Retention strategy for Docker Swarm agents.
 * Terminates agents after they've been idle for a configurable period.
 *
 * Uses the built-in CloudRetentionStrategy which handles idle timeout.
 */
public class SwarmRetentionStrategy extends CloudRetentionStrategy {

    private static final int DEFAULT_IDLE_MINUTES = 10;

    private final int idleMinutes;

    @DataBoundConstructor
    public SwarmRetentionStrategy(int idleMinutes) {
        super(idleMinutes > 0 ? idleMinutes : DEFAULT_IDLE_MINUTES);
        this.idleMinutes = idleMinutes > 0 ? idleMinutes : DEFAULT_IDLE_MINUTES;
    }

    public SwarmRetentionStrategy() {
        this(DEFAULT_IDLE_MINUTES);
    }

    public int getIdleMinutes() {
        return idleMinutes;
    }

    @Extension
    @Symbol("swarmRetention")
    public static class DescriptorImpl extends Descriptor<RetentionStrategy<?>> {

        @Override
        public String getDisplayName() {
            return "Swarm Agent Retention Strategy";
        }
    }
}

package io.jenkins.plugins.swarmcloud.ratelimit;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Rate limiter for agent provisioning to prevent overwhelming the Docker Swarm cluster.
 * Implements a sliding window rate limiter with configurable limits.
 */
public class ProvisionRateLimiter {

    private static final Logger LOGGER = Logger.getLogger(ProvisionRateLimiter.class.getName());

    private static final ConcurrentHashMap<String, RateLimitState> CLOUD_STATES = new ConcurrentHashMap<>();

    /**
     * Default maximum provisions per minute.
     */
    public static final int DEFAULT_MAX_PROVISIONS_PER_MINUTE = 10;

    /**
     * Default minimum interval between provisions in milliseconds.
     */
    public static final long DEFAULT_MIN_INTERVAL_MS = 1000;

    /**
     * Cooldown period after failures in milliseconds.
     */
    public static final long FAILURE_COOLDOWN_MS = TimeUnit.SECONDS.toMillis(30);

    /**
     * Check if provisioning is allowed for the given cloud.
     *
     * @param cloudName The cloud name
     * @return true if provisioning is allowed
     */
    public static boolean canProvision(@NonNull String cloudName) {
        return canProvision(cloudName, DEFAULT_MAX_PROVISIONS_PER_MINUTE, DEFAULT_MIN_INTERVAL_MS);
    }

    /**
     * Check if provisioning is allowed with custom limits.
     *
     * @param cloudName The cloud name
     * @param maxPerMinute Maximum provisions per minute
     * @param minIntervalMs Minimum interval between provisions
     * @return true if provisioning is allowed
     */
    public static boolean canProvision(@NonNull String cloudName, int maxPerMinute, long minIntervalMs) {
        RateLimitState state = CLOUD_STATES.computeIfAbsent(cloudName, k -> new RateLimitState());
        return state.canProvision(maxPerMinute, minIntervalMs);
    }

    /**
     * Record a successful provision.
     *
     * @param cloudName The cloud name
     */
    public static void recordProvision(@NonNull String cloudName) {
        RateLimitState state = CLOUD_STATES.computeIfAbsent(cloudName, k -> new RateLimitState());
        state.recordProvision();
        LOGGER.log(Level.FINE, "Recorded provision for cloud: {0}, count this minute: {1}",
                new Object[]{cloudName, state.getProvisionCountInWindow()});
    }

    /**
     * Record a failed provision attempt.
     *
     * @param cloudName The cloud name
     */
    public static void recordFailure(@NonNull String cloudName) {
        RateLimitState state = CLOUD_STATES.computeIfAbsent(cloudName, k -> new RateLimitState());
        state.recordFailure();
        LOGGER.log(Level.WARNING, "Recorded provision failure for cloud: {0}, consecutive failures: {1}",
                new Object[]{cloudName, state.getConsecutiveFailures()});
    }

    /**
     * Reset failure count after successful recovery.
     *
     * @param cloudName The cloud name
     */
    public static void resetFailures(@NonNull String cloudName) {
        RateLimitState state = CLOUD_STATES.get(cloudName);
        if (state != null) {
            state.resetFailures();
        }
    }

    /**
     * Get the current rate limit state for a cloud.
     *
     * @param cloudName The cloud name
     * @return The state or null if not tracked
     */
    public static RateLimitInfo getInfo(@NonNull String cloudName) {
        RateLimitState state = CLOUD_STATES.get(cloudName);
        if (state == null) {
            return new RateLimitInfo(0, 0, 0, true);
        }
        return new RateLimitInfo(
                state.getProvisionCountInWindow(),
                state.getConsecutiveFailures(),
                state.getLastProvisionTime(),
                state.canProvision(DEFAULT_MAX_PROVISIONS_PER_MINUTE, DEFAULT_MIN_INTERVAL_MS)
        );
    }

    /**
     * Get wait time until next provision is allowed.
     *
     * @param cloudName The cloud name
     * @return Wait time in milliseconds, 0 if allowed now
     */
    public static long getWaitTime(@NonNull String cloudName) {
        return getWaitTime(cloudName, DEFAULT_MAX_PROVISIONS_PER_MINUTE, DEFAULT_MIN_INTERVAL_MS);
    }

    /**
     * Get wait time with custom limits.
     *
     * @param cloudName The cloud name
     * @param maxPerMinute Maximum provisions per minute
     * @param minIntervalMs Minimum interval between provisions
     * @return Wait time in milliseconds
     */
    public static long getWaitTime(@NonNull String cloudName, int maxPerMinute, long minIntervalMs) {
        RateLimitState state = CLOUD_STATES.get(cloudName);
        if (state == null) {
            return 0;
        }
        return state.getWaitTime(maxPerMinute, minIntervalMs);
    }

    /**
     * Clear all rate limit state (for testing).
     */
    public static void clearAll() {
        CLOUD_STATES.clear();
    }

    /**
     * Internal state for tracking rate limits per cloud.
     */
    private static class RateLimitState {
        private final AtomicLong lastProvisionTime = new AtomicLong(0);
        private final AtomicLong windowStart = new AtomicLong(System.currentTimeMillis());
        private final AtomicInteger windowCount = new AtomicInteger(0);
        private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
        private final AtomicLong lastFailureTime = new AtomicLong(0);

        boolean canProvision(int maxPerMinute, long minIntervalMs) {
            long now = System.currentTimeMillis();

            // Check if in failure cooldown
            long lastFail = lastFailureTime.get();
            int failures = consecutiveFailures.get();
            if (failures > 0) {
                long cooldown = FAILURE_COOLDOWN_MS * Math.min(failures, 5); // Max 2.5 min cooldown
                if (now - lastFail < cooldown) {
                    LOGGER.log(Level.FINE, "In failure cooldown, remaining: {0}ms",
                            cooldown - (now - lastFail));
                    return false;
                }
            }

            // Check minimum interval
            long lastProv = lastProvisionTime.get();
            if (lastProv > 0 && now - lastProv < minIntervalMs) {
                return false;
            }

            // Reset window if expired
            long windowAge = now - windowStart.get();
            if (windowAge >= TimeUnit.MINUTES.toMillis(1)) {
                windowStart.set(now);
                windowCount.set(0);
            }

            // Check window limit
            return windowCount.get() < maxPerMinute;
        }

        void recordProvision() {
            long now = System.currentTimeMillis();
            lastProvisionTime.set(now);

            // Reset window if expired
            if (now - windowStart.get() >= TimeUnit.MINUTES.toMillis(1)) {
                windowStart.set(now);
                windowCount.set(0);
            }
            windowCount.incrementAndGet();
        }

        void recordFailure() {
            consecutiveFailures.incrementAndGet();
            lastFailureTime.set(System.currentTimeMillis());
        }

        void resetFailures() {
            consecutiveFailures.set(0);
            lastFailureTime.set(0);
        }

        int getProvisionCountInWindow() {
            long now = System.currentTimeMillis();
            if (now - windowStart.get() >= TimeUnit.MINUTES.toMillis(1)) {
                return 0;
            }
            return windowCount.get();
        }

        int getConsecutiveFailures() {
            return consecutiveFailures.get();
        }

        long getLastProvisionTime() {
            return lastProvisionTime.get();
        }

        long getWaitTime(int maxPerMinute, long minIntervalMs) {
            long now = System.currentTimeMillis();
            long wait = 0;

            // Check failure cooldown
            long lastFail = lastFailureTime.get();
            int failures = consecutiveFailures.get();
            if (failures > 0) {
                long cooldown = FAILURE_COOLDOWN_MS * Math.min(failures, 5);
                long cooldownRemaining = cooldown - (now - lastFail);
                if (cooldownRemaining > 0) {
                    wait = Math.max(wait, cooldownRemaining);
                }
            }

            // Check minimum interval
            long lastProv = lastProvisionTime.get();
            if (lastProv > 0) {
                long intervalRemaining = minIntervalMs - (now - lastProv);
                if (intervalRemaining > 0) {
                    wait = Math.max(wait, intervalRemaining);
                }
            }

            // Check window limit
            if (windowCount.get() >= maxPerMinute) {
                long windowRemaining = TimeUnit.MINUTES.toMillis(1) - (now - windowStart.get());
                if (windowRemaining > 0) {
                    wait = Math.max(wait, windowRemaining);
                }
            }

            return wait;
        }
    }

    /**
     * Rate limit information for monitoring.
     */
    public static class RateLimitInfo {
        private final int provisionCount;
        private final int failureCount;
        private final long lastProvision;
        private final boolean canProvision;

        public RateLimitInfo(int provisionCount, int failureCount, long lastProvision, boolean canProvision) {
            this.provisionCount = provisionCount;
            this.failureCount = failureCount;
            this.lastProvision = lastProvision;
            this.canProvision = canProvision;
        }

        public int getProvisionCount() { return provisionCount; }
        public int getFailureCount() { return failureCount; }
        public long getLastProvision() { return lastProvision; }
        public boolean canProvision() { return canProvision; }
    }
}

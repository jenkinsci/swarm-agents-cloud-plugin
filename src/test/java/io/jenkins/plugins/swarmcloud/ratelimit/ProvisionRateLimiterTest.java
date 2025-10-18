package io.jenkins.plugins.swarmcloud.ratelimit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ProvisionRateLimiter.
 */
class ProvisionRateLimiterTest {

    @BeforeEach
    void setUp() {
        ProvisionRateLimiter.clearAll();
    }

    @AfterEach
    void tearDown() {
        ProvisionRateLimiter.clearAll();
    }

    @Test
    void testCanProvisionWhenNoHistory() {
        assertTrue(ProvisionRateLimiter.canProvision("test-cloud"));
    }

    @Test
    void testRecordProvision() {
        String cloudName = "test-cloud";

        ProvisionRateLimiter.recordProvision(cloudName);

        ProvisionRateLimiter.RateLimitInfo info = ProvisionRateLimiter.getInfo(cloudName);
        assertEquals(1, info.getProvisionCount());
        assertTrue(info.getLastProvision() > 0);
    }

    @Test
    void testMinimumIntervalEnforced() throws InterruptedException {
        String cloudName = "test-cloud";
        long minInterval = 100; // 100ms

        // First provision should succeed
        assertTrue(ProvisionRateLimiter.canProvision(cloudName, 10, minInterval));
        ProvisionRateLimiter.recordProvision(cloudName);

        // Second provision immediately should fail
        assertFalse(ProvisionRateLimiter.canProvision(cloudName, 10, minInterval));

        // Wait for interval to pass
        Thread.sleep(minInterval + 10);

        // Now it should succeed
        assertTrue(ProvisionRateLimiter.canProvision(cloudName, 10, minInterval));
    }

    @Test
    void testMaxProvisionsPerMinute() {
        String cloudName = "test-cloud";
        int maxPerMinute = 3;
        long minInterval = 0; // No minimum interval for this test

        // Provision up to the limit
        for (int i = 0; i < maxPerMinute; i++) {
            assertTrue(ProvisionRateLimiter.canProvision(cloudName, maxPerMinute, minInterval));
            ProvisionRateLimiter.recordProvision(cloudName);
        }

        // Next provision should be blocked
        assertFalse(ProvisionRateLimiter.canProvision(cloudName, maxPerMinute, minInterval));

        ProvisionRateLimiter.RateLimitInfo info = ProvisionRateLimiter.getInfo(cloudName);
        assertEquals(maxPerMinute, info.getProvisionCount());
    }

    @Test
    void testFailureCooldown() {
        String cloudName = "test-cloud";

        // Record a failure
        ProvisionRateLimiter.recordFailure(cloudName);

        // Should be blocked due to cooldown
        assertFalse(ProvisionRateLimiter.canProvision(cloudName, 100, 0));

        ProvisionRateLimiter.RateLimitInfo info = ProvisionRateLimiter.getInfo(cloudName);
        assertEquals(1, info.getFailureCount());
    }

    @Test
    void testResetFailures() {
        String cloudName = "test-cloud";

        // Record failures
        ProvisionRateLimiter.recordFailure(cloudName);
        ProvisionRateLimiter.recordFailure(cloudName);

        ProvisionRateLimiter.RateLimitInfo infoBefore = ProvisionRateLimiter.getInfo(cloudName);
        assertEquals(2, infoBefore.getFailureCount());

        // Reset failures
        ProvisionRateLimiter.resetFailures(cloudName);

        ProvisionRateLimiter.RateLimitInfo infoAfter = ProvisionRateLimiter.getInfo(cloudName);
        assertEquals(0, infoAfter.getFailureCount());
    }

    @Test
    void testGetWaitTime() throws InterruptedException {
        String cloudName = "test-cloud";
        long minInterval = 200; // 200ms

        // Record a provision
        ProvisionRateLimiter.recordProvision(cloudName);

        // Wait time should be positive
        long waitTime = ProvisionRateLimiter.getWaitTime(cloudName, 10, minInterval);
        assertTrue(waitTime > 0);
        assertTrue(waitTime <= minInterval);

        // Wait for interval
        Thread.sleep(minInterval + 10);

        // Wait time should be 0
        waitTime = ProvisionRateLimiter.getWaitTime(cloudName, 10, minInterval);
        assertEquals(0, waitTime);
    }

    @Test
    void testMultipleClouds() {
        String cloud1 = "cloud-1";
        String cloud2 = "cloud-2";

        // Provision on cloud1
        ProvisionRateLimiter.recordProvision(cloud1);
        ProvisionRateLimiter.recordProvision(cloud1);

        // Provision on cloud2
        ProvisionRateLimiter.recordProvision(cloud2);

        // Verify counts are independent
        assertEquals(2, ProvisionRateLimiter.getInfo(cloud1).getProvisionCount());
        assertEquals(1, ProvisionRateLimiter.getInfo(cloud2).getProvisionCount());
    }

    @Test
    void testConsecutiveFailuresIncreaseCooldown() {
        String cloudName = "test-cloud";

        // Record multiple failures
        ProvisionRateLimiter.recordFailure(cloudName);
        long waitTime1 = ProvisionRateLimiter.getWaitTime(cloudName, 100, 0);

        ProvisionRateLimiter.recordFailure(cloudName);
        long waitTime2 = ProvisionRateLimiter.getWaitTime(cloudName, 100, 0);

        // Cooldown should increase with consecutive failures
        assertTrue(waitTime2 > waitTime1);
    }

    @Test
    void testInfoForUnknownCloud() {
        ProvisionRateLimiter.RateLimitInfo info = ProvisionRateLimiter.getInfo("unknown-cloud");

        assertEquals(0, info.getProvisionCount());
        assertEquals(0, info.getFailureCount());
        assertEquals(0, info.getLastProvision());
        assertTrue(info.canProvision());
    }
}

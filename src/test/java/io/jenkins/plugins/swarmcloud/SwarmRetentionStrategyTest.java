package io.jenkins.plugins.swarmcloud;

import hudson.slaves.RetentionStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SwarmRetentionStrategy.
 */
class SwarmRetentionStrategyTest {

    @Test
    void testDefaultIdleTimeout() {
        SwarmRetentionStrategy strategy = new SwarmRetentionStrategy();
        assertEquals(10, strategy.getIdleMinutes());
    }

    @Test
    void testCustomIdleTimeout() {
        SwarmRetentionStrategy strategy = new SwarmRetentionStrategy(30);
        assertEquals(30, strategy.getIdleMinutes());
    }

    @Test
    void testZeroIdleTimeoutUsesDefault() {
        SwarmRetentionStrategy strategy = new SwarmRetentionStrategy(0);
        assertEquals(10, strategy.getIdleMinutes());
    }

    @Test
    void testNegativeIdleTimeoutUsesDefault() {
        SwarmRetentionStrategy strategy = new SwarmRetentionStrategy(-5);
        assertEquals(10, strategy.getIdleMinutes());
    }

    @Test
    void testLargeIdleTimeout() {
        SwarmRetentionStrategy strategy = new SwarmRetentionStrategy(1440); // 24 hours
        assertEquals(1440, strategy.getIdleMinutes());
    }

    @Test
    void testDescriptorDisplayName() {
        SwarmRetentionStrategy.DescriptorImpl descriptor = new SwarmRetentionStrategy.DescriptorImpl();
        assertEquals("Swarm Agent Retention Strategy", descriptor.getDisplayName());
    }

    @Test
    void testIsInstanceOfRetentionStrategy() {
        SwarmRetentionStrategy strategy = new SwarmRetentionStrategy(15);
        assertInstanceOf(RetentionStrategy.class, strategy);
    }

    @Test
    void testMultipleInstances() {
        SwarmRetentionStrategy strategy1 = new SwarmRetentionStrategy(10);
        SwarmRetentionStrategy strategy2 = new SwarmRetentionStrategy(20);

        assertEquals(10, strategy1.getIdleMinutes());
        assertEquals(20, strategy2.getIdleMinutes());

        // They should be different instances
        assertNotSame(strategy1, strategy2);
    }
}

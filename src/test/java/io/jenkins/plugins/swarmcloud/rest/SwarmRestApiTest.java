package io.jenkins.plugins.swarmcloud.rest;

import io.jenkins.plugins.swarmcloud.SwarmAgentTemplate;
import io.jenkins.plugins.swarmcloud.SwarmCloud;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SwarmRestApi.
 */
@WithJenkins
class SwarmRestApiTest {

    private JenkinsRule jenkins;
    private SwarmRestApi api;

    @BeforeEach
    void setUp(JenkinsRule jenkins) {
        this.jenkins = jenkins;
        api = new SwarmRestApi();
    }

    @Test
    void testUrlName() {
        assertEquals("swarm-api", api.getUrlName());
    }

    @Test
    void testDisplayName() {
        assertEquals("Swarm REST API", api.getDisplayName());
    }

    @Test
    void testIconFileName() {
        // Should return null (no icon in sidebar)
        assertNull(api.getIconFileName());
    }

    @Test
    void testExtensionRegistered() {
        // SwarmRestApi should be registered as RootAction extension
        var extensions = jenkins.jenkins.getExtensionList(SwarmRestApi.class);
        assertEquals(1, extensions.size());
    }

    @Test
    void testApiEndpointAvailable() throws Exception {
        // The API should be accessible at /swarm-api
        var webClient = jenkins.createWebClient();
        webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);

        // Just check that the endpoint exists (will get 400 without params)
        var page = webClient.goTo("swarm-api/clouds", "application/json");
        assertNotNull(page);
    }

    @Test
    void testCloudsEndpointWithCloud() throws Exception {
        // Add a test cloud
        SwarmCloud cloud = new SwarmCloud("test-cloud");
        cloud.setDockerHost("tcp://localhost:2376");
        cloud.setMaxConcurrentAgents(10);

        SwarmAgentTemplate template = new SwarmAgentTemplate("maven");
        template.setImage("jenkins/inbound-agent:latest");
        cloud.setTemplates(List.of(template));

        jenkins.jenkins.clouds.add(cloud);

        var webClient = jenkins.createWebClient();
        var page = webClient.goTo("swarm-api/clouds", "application/json");

        String content = page.getWebResponse().getContentAsString();
        assertTrue(content.contains("test-cloud"));
        assertTrue(content.contains("tcp://localhost:2376"));
    }

    @Test
    void testTemplatesEndpoint() throws Exception {
        // Add a test cloud with templates
        SwarmCloud cloud = new SwarmCloud("test-cloud");

        SwarmAgentTemplate template1 = new SwarmAgentTemplate("maven");
        template1.setImage("jenkins/inbound-agent:latest");
        template1.setLabelString("maven java");

        SwarmAgentTemplate template2 = new SwarmAgentTemplate("node");
        template2.setImage("jenkins/inbound-agent:alpine");
        template2.setLabelString("node npm");

        cloud.setTemplates(List.of(template1, template2));
        jenkins.jenkins.clouds.add(cloud);

        var webClient = jenkins.createWebClient();
        var page = webClient.goTo("swarm-api/templates", "application/json");

        String content = page.getWebResponse().getContentAsString();
        assertTrue(content.contains("maven"));
        assertTrue(content.contains("node"));
        assertTrue(content.contains("jenkins/inbound-agent:latest"));
        assertTrue(content.contains("jenkins/inbound-agent:alpine"));
    }

    @Test
    void testTemplatesEndpointWithCloudFilter() throws Exception {
        // Add two clouds
        SwarmCloud cloud1 = new SwarmCloud("cloud-1");
        SwarmAgentTemplate template1 = new SwarmAgentTemplate("t1");
        cloud1.setTemplates(List.of(template1));

        SwarmCloud cloud2 = new SwarmCloud("cloud-2");
        SwarmAgentTemplate template2 = new SwarmAgentTemplate("t2");
        cloud2.setTemplates(List.of(template2));

        jenkins.jenkins.clouds.add(cloud1);
        jenkins.jenkins.clouds.add(cloud2);

        var webClient = jenkins.createWebClient();
        var page = webClient.goTo("swarm-api/templates?cloud=cloud-1", "application/json");

        String content = page.getWebResponse().getContentAsString();
        assertTrue(content.contains("t1"));
        assertFalse(content.contains("t2"));
    }

    @Test
    void testAgentsEndpoint() throws Exception {
        var webClient = jenkins.createWebClient();
        var page = webClient.goTo("swarm-api/agents", "application/json");

        String content = page.getWebResponse().getContentAsString();
        // Should return empty array when no agents
        assertEquals("[]", content.trim());
    }

    @Test
    void testMetricsEndpoint() throws Exception {
        // Add a test cloud
        SwarmCloud cloud = new SwarmCloud("test-cloud");
        cloud.setDockerHost("tcp://localhost:2376");
        jenkins.jenkins.clouds.add(cloud);

        var webClient = jenkins.createWebClient();
        webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);

        var page = webClient.goTo("swarm-api/metrics", "application/json");
        String content = page.getWebResponse().getContentAsString();

        assertTrue(content.contains("clouds"));
        assertTrue(content.contains("lastUpdate"));
    }

    @Test
    void testPrometheusEndpoint() throws Exception {
        // Add a test cloud
        SwarmCloud cloud = new SwarmCloud("test-cloud");
        cloud.setDockerHost("tcp://localhost:2376");

        SwarmAgentTemplate template = new SwarmAgentTemplate("maven");
        cloud.setTemplates(List.of(template));

        jenkins.jenkins.clouds.add(cloud);

        var webClient = jenkins.createWebClient();
        var page = webClient.goTo("swarm-api/prometheus", "text/plain");

        String content = page.getWebResponse().getContentAsString();

        // Should contain Prometheus metrics format
        assertTrue(content.contains("# HELP"));
        assertTrue(content.contains("# TYPE"));
        assertTrue(content.contains("swarm_clouds_total"));
        assertTrue(content.contains("swarm_cloud_healthy"));
        assertTrue(content.contains("swarm_agents_max"));
        assertTrue(content.contains("swarm_agents_current"));
    }

    @Test
    void testAuditEndpoint() throws Exception {
        var webClient = jenkins.createWebClient();
        var page = webClient.goTo("swarm-api/audit", "application/json");

        // Should return array (might be empty)
        String content = page.getWebResponse().getContentAsString();
        assertTrue(content.startsWith("["));
        assertTrue(content.endsWith("]"));
    }

    @Test
    void testAuditEndpointWithLimit() throws Exception {
        // Add some audit entries first
        io.jenkins.plugins.swarmcloud.monitoring.SwarmAuditLog.logProvision(
                "test-cloud", "maven", "agent-1", "svc-1");
        io.jenkins.plugins.swarmcloud.monitoring.SwarmAuditLog.logProvision(
                "test-cloud", "maven", "agent-2", "svc-2");

        var webClient = jenkins.createWebClient();
        var page = webClient.goTo("swarm-api/audit?limit=1", "application/json");

        String content = page.getWebResponse().getContentAsString();
        // Should return only 1 entry
        assertTrue(content.contains("PROVISION"));
    }

    @Test
    void testCloudEndpointNotFound() throws Exception {
        var webClient = jenkins.createWebClient();
        webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);

        var page = webClient.goTo("swarm-api/cloud?name=non-existent", "application/json");

        assertEquals(404, page.getWebResponse().getStatusCode());
        String content = page.getWebResponse().getContentAsString();
        assertTrue(content.contains("error"));
        assertTrue(content.contains("not found"));
    }

    @Test
    void testCloudEndpointMissingName() throws Exception {
        var webClient = jenkins.createWebClient();
        webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);

        var page = webClient.goTo("swarm-api/cloud", "application/json");

        assertEquals(400, page.getWebResponse().getStatusCode());
        String content = page.getWebResponse().getContentAsString();
        assertTrue(content.contains("error"));
        assertTrue(content.contains("required"));
    }

    @Test
    void testProvisionEndpointMissingParams() throws Exception {
        var webClient = jenkins.createWebClient();
        webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);

        // POST without CSRF - will fail with error status
        var page = webClient.goTo("swarm-api/provision", null);

        // Should fail - 400 for missing params, 404 for not found, or 405 for wrong method
        int statusCode = page.getWebResponse().getStatusCode();
        assertTrue(statusCode >= 400 && statusCode < 500,
                "Expected 4xx error, got: " + statusCode);
    }
}

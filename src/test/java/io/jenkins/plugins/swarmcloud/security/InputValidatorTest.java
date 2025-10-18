package io.jenkins.plugins.swarmcloud.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for InputValidator.
 */
class InputValidatorTest {

    @Test
    void testValidCloudName() {
        assertTrue(InputValidator.isValidCloudName("my-cloud"));
        assertTrue(InputValidator.isValidCloudName("cloud_1"));
        assertTrue(InputValidator.isValidCloudName("Cloud123"));
    }

    @Test
    void testInvalidCloudName() {
        assertFalse(InputValidator.isValidCloudName(null));
        assertFalse(InputValidator.isValidCloudName(""));
        assertFalse(InputValidator.isValidCloudName("  "));
        assertFalse(InputValidator.isValidCloudName("cloud with spaces"));
        assertFalse(InputValidator.isValidCloudName("cloud<script>"));
        assertFalse(InputValidator.isValidCloudName("a".repeat(65)));
    }

    @Test
    void testValidTemplateName() {
        assertTrue(InputValidator.isValidTemplateName("maven-agent"));
        assertTrue(InputValidator.isValidTemplateName("nodejs_20"));
        assertTrue(InputValidator.isValidTemplateName("Agent1"));
    }

    @Test
    void testInvalidTemplateName() {
        assertFalse(InputValidator.isValidTemplateName(null));
        assertFalse(InputValidator.isValidTemplateName(""));
        assertFalse(InputValidator.isValidTemplateName("template/name"));
        assertFalse(InputValidator.isValidTemplateName("name@123"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "nginx",
            "nginx:latest",
            "nginx:1.21",
            "my-registry/nginx:v1",
            "gcr.io/project/image:tag",
            "registry.example.com:5000/image:tag",
            "jenkins/inbound-agent:latest",
            "image@sha256:abc123def456"
    })
    void testValidDockerImages(String image) {
        assertTrue(InputValidator.isValidDockerImage(image), "Should be valid: " + image);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "-invalid",
            "image with space",
            "image\nwith\nnewlines"
    })
    void testInvalidDockerImages(String image) {
        assertFalse(InputValidator.isValidDockerImage(image), "Should be invalid: " + image);
    }

    @Test
    void testValidServiceId() {
        assertTrue(InputValidator.isValidServiceId("abcdefghijklmnopqrstuvwxy")); // 25 chars
    }

    @Test
    void testInvalidServiceId() {
        assertFalse(InputValidator.isValidServiceId(null));
        assertFalse(InputValidator.isValidServiceId(""));
        assertFalse(InputValidator.isValidServiceId("tooshort"));
        assertFalse(InputValidator.isValidServiceId("a".repeat(26)));
        assertFalse(InputValidator.isValidServiceId("abc123-with-dashes-here!"));
    }

    @Test
    void testValidLabelString() {
        assertTrue(InputValidator.isValidLabelString("maven java"));
        assertTrue(InputValidator.isValidLabelString("linux docker"));
        assertTrue(InputValidator.isValidLabelString(null)); // Optional
        assertTrue(InputValidator.isValidLabelString("")); // Optional
    }

    @Test
    void testInvalidLabelString() {
        assertFalse(InputValidator.isValidLabelString("label<script>"));
        assertFalse(InputValidator.isValidLabelString("a".repeat(201)));
    }

    @Test
    void testValidNetworkName() {
        assertTrue(InputValidator.isValidNetworkName("jenkins-network"));
        assertTrue(InputValidator.isValidNetworkName("net_1"));
        assertTrue(InputValidator.isValidNetworkName(null)); // Optional
    }

    @Test
    void testInvalidNetworkName() {
        assertFalse(InputValidator.isValidNetworkName("net work"));
        assertFalse(InputValidator.isValidNetworkName("net<>work"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "tcp://localhost:2376",
            "tcp://192.168.1.1:2376",
            "unix:///var/run/docker.sock",
            "npipe:////./pipe/docker_engine",
            "ssh://user@host"
    })
    void testValidDockerHost(String host) {
        assertTrue(InputValidator.isValidDockerHost(host), "Should be valid: " + host);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "http://localhost:2376",
            "localhost:2376",
            "ftp://host"
    })
    void testInvalidDockerHost(String host) {
        assertFalse(InputValidator.isValidDockerHost(host), "Should be invalid: " + host);
    }

    @Test
    void testSanitizeForLog() {
        assertEquals("test", InputValidator.sanitizeForLog("test"));
        assertEquals("line1line2", InputValidator.sanitizeForLog("line1\nline2"));
        assertEquals("line1line2", InputValidator.sanitizeForLog("line1\r\nline2"));
        assertEquals("", InputValidator.sanitizeForLog(null));

        // Test length limit
        String longString = "a".repeat(300);
        String sanitized = InputValidator.sanitizeForLog(longString);
        assertTrue(sanitized.length() <= 203);
        assertTrue(sanitized.endsWith("..."));
    }

    @Test
    void testSanitizeForDisplay() {
        assertEquals("test", InputValidator.sanitizeForDisplay("test"));
        assertEquals("&lt;script&gt;", InputValidator.sanitizeForDisplay("<script>"));
        assertEquals("", InputValidator.sanitizeForDisplay(null));
    }

    @Test
    void testValidMemorySpec() {
        assertTrue(InputValidator.isValidMemorySpec("512m"));
        assertTrue(InputValidator.isValidMemorySpec("1g"));
        assertTrue(InputValidator.isValidMemorySpec("2048M"));
        assertTrue(InputValidator.isValidMemorySpec("1024"));
        assertTrue(InputValidator.isValidMemorySpec(null)); // Optional
    }

    @Test
    void testInvalidMemorySpec() {
        assertFalse(InputValidator.isValidMemorySpec("1.5g"));
        assertFalse(InputValidator.isValidMemorySpec("abc"));
        assertFalse(InputValidator.isValidMemorySpec("-512m"));
    }

    @Test
    void testValidCpuSpec() {
        assertTrue(InputValidator.isValidCpuSpec("0.5"));
        assertTrue(InputValidator.isValidCpuSpec("2.0"));
        assertTrue(InputValidator.isValidCpuSpec("4"));
        assertTrue(InputValidator.isValidCpuSpec(null)); // Optional
    }

    @Test
    void testInvalidCpuSpec() {
        assertFalse(InputValidator.isValidCpuSpec("0"));
        assertFalse(InputValidator.isValidCpuSpec("-1"));
        assertFalse(InputValidator.isValidCpuSpec("abc"));
        assertFalse(InputValidator.isValidCpuSpec("101"));
    }

    @Test
    void testValidPort() {
        assertTrue(InputValidator.isValidPort(80));
        assertTrue(InputValidator.isValidPort(443));
        assertTrue(InputValidator.isValidPort(8080));
        assertTrue(InputValidator.isValidPort(65535));
    }

    @Test
    void testInvalidPort() {
        assertFalse(InputValidator.isValidPort(0));
        assertFalse(InputValidator.isValidPort(-1));
        assertFalse(InputValidator.isValidPort(65536));
    }

    @Test
    void testValidTimeout() {
        assertTrue(InputValidator.isValidTimeout(1));
        assertTrue(InputValidator.isValidTimeout(60));
        assertTrue(InputValidator.isValidTimeout(3600));
    }

    @Test
    void testInvalidTimeout() {
        assertFalse(InputValidator.isValidTimeout(0));
        assertFalse(InputValidator.isValidTimeout(-1));
        assertFalse(InputValidator.isValidTimeout(3601));
    }

    @Test
    void testValidUrl() {
        assertTrue(InputValidator.isValidUrl("http://jenkins:8080"));
        assertTrue(InputValidator.isValidUrl("https://jenkins.example.com"));
        assertTrue(InputValidator.isValidUrl("http://localhost:8080/"));
    }

    @Test
    void testInvalidUrl() {
        assertFalse(InputValidator.isValidUrl(null));
        assertFalse(InputValidator.isValidUrl(""));
        assertFalse(InputValidator.isValidUrl("ftp://host"));
        assertFalse(InputValidator.isValidUrl("not-a-url"));
    }

    @Test
    void testValidPlacementConstraint() {
        assertTrue(InputValidator.isValidPlacementConstraint("node.role==worker"));
        assertTrue(InputValidator.isValidPlacementConstraint("node.labels.type!=gpu"));
        assertTrue(InputValidator.isValidPlacementConstraint("node.hostname==server-1"));
    }

    @Test
    void testInvalidPlacementConstraint() {
        assertFalse(InputValidator.isValidPlacementConstraint(null));
        assertFalse(InputValidator.isValidPlacementConstraint(""));
        assertFalse(InputValidator.isValidPlacementConstraint("invalid"));
        assertFalse(InputValidator.isValidPlacementConstraint("role==worker"));
    }

    @Test
    void testValidEnvVarName() {
        assertTrue(InputValidator.isValidEnvVarName("MY_VAR"));
        assertTrue(InputValidator.isValidEnvVarName("_PRIVATE"));
        assertTrue(InputValidator.isValidEnvVarName("VAR123"));
    }

    @Test
    void testInvalidEnvVarName() {
        assertFalse(InputValidator.isValidEnvVarName(null));
        assertFalse(InputValidator.isValidEnvVarName(""));
        assertFalse(InputValidator.isValidEnvVarName("123VAR"));
        assertFalse(InputValidator.isValidEnvVarName("my-var"));
        assertFalse(InputValidator.isValidEnvVarName("var name"));
    }
}

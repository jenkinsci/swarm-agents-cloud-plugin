package io.jenkins.plugins.swarmcloud.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DockerCredentialsHelper.
 */
class DockerCredentialsHelperTest {

    @Test
    void testExtractRegistryAddressDockerHubOfficial() {
        // Docker Hub (official images - no slash)
        assertEquals(DockerCredentialsHelper.DOCKER_HUB_REGISTRY_URL,
                DockerCredentialsHelper.extractRegistryAddress("nginx:latest"));
        assertEquals(DockerCredentialsHelper.DOCKER_HUB_REGISTRY_URL,
                DockerCredentialsHelper.extractRegistryAddress("ubuntu"));
        assertEquals(DockerCredentialsHelper.DOCKER_HUB_REGISTRY_URL,
                DockerCredentialsHelper.extractRegistryAddress("redis:7.0"));
    }

    @Test
    void testExtractRegistryAddressDockerHubUser() {
        // Docker Hub (user images - single slash, no dots/colons in first part)
        assertEquals(DockerCredentialsHelper.DOCKER_HUB_REGISTRY_URL,
                DockerCredentialsHelper.extractRegistryAddress("myuser/myimage:v1"));
        assertEquals(DockerCredentialsHelper.DOCKER_HUB_REGISTRY_URL,
                DockerCredentialsHelper.extractRegistryAddress("jenkins/inbound-agent:latest"));
        assertEquals(DockerCredentialsHelper.DOCKER_HUB_REGISTRY_URL,
                DockerCredentialsHelper.extractRegistryAddress("library/alpine"));
    }

    @Test
    void testExtractRegistryAddressPrivateRegistry() {
        // Private registry (contains dot)
        assertEquals("myregistry.com",
                DockerCredentialsHelper.extractRegistryAddress("myregistry.com/myimage:v1"));
        assertEquals("registry.example.org",
                DockerCredentialsHelper.extractRegistryAddress("registry.example.org/project/image:tag"));
    }

    @Test
    void testExtractRegistryAddressGcr() {
        // Google Container Registry
        assertEquals("gcr.io",
                DockerCredentialsHelper.extractRegistryAddress("gcr.io/project/image:tag"));
        assertEquals("us.gcr.io",
                DockerCredentialsHelper.extractRegistryAddress("us.gcr.io/project/image:tag"));
    }

    @Test
    void testExtractRegistryAddressAwsEcr() {
        // AWS ECR
        assertEquals("123456789012.dkr.ecr.us-east-1.amazonaws.com",
                DockerCredentialsHelper.extractRegistryAddress(
                        "123456789012.dkr.ecr.us-east-1.amazonaws.com/my-repo:latest"));
    }

    @Test
    void testExtractRegistryAddressWithPort() {
        // Registry with port
        assertEquals("myregistry.com:5000",
                DockerCredentialsHelper.extractRegistryAddress("myregistry.com:5000/myimage:v1"));
        assertEquals("localhost:5000",
                DockerCredentialsHelper.extractRegistryAddress("localhost:5000/test/image:latest"));
    }

    @Test
    void testExtractRegistryAddressGitHubContainerRegistry() {
        // GitHub Container Registry
        assertEquals("ghcr.io",
                DockerCredentialsHelper.extractRegistryAddress("ghcr.io/owner/image:tag"));
    }

    @Test
    void testExtractRegistryAddressAzureContainerRegistry() {
        // Azure Container Registry
        assertEquals("myregistry.azurecr.io",
                DockerCredentialsHelper.extractRegistryAddress("myregistry.azurecr.io/myimage:tag"));
    }

    @Test
    void testLookupRegistryCredentialsWithNull() {
        assertNull(DockerCredentialsHelper.lookupRegistryCredentials(null));
        assertNull(DockerCredentialsHelper.lookupRegistryCredentials(""));
        assertNull(DockerCredentialsHelper.lookupRegistryCredentials("   "));
    }

    @Test
    void testCreateAuthConfigWithNull() {
        assertNull(DockerCredentialsHelper.createAuthConfig(null, "registry.com"));
        assertNull(DockerCredentialsHelper.createAuthConfig("", "registry.com"));
        assertNull(DockerCredentialsHelper.createAuthConfig("   ", "registry.com"));
    }
}

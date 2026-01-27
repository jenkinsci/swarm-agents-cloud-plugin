package io.jenkins.plugins.swarmcloud.config;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.github.dockerjava.api.model.AuthConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.model.Item;
import hudson.model.Queue;
import hudson.model.queue.Tasks;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerCredentials;

import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Helper class for working with Docker server credentials.
 */
public class DockerCredentialsHelper {

    private static final Logger LOGGER = Logger.getLogger(DockerCredentialsHelper.class.getName());

    /** Default Docker Hub registry URL for official and user images. */
    public static final String DOCKER_HUB_REGISTRY_URL = "https://index.docker.io/v1/";

    private DockerCredentialsHelper() {
        // Utility class
    }

    /**
     * Looks up Docker server credentials by ID.
     *
     * @param credentialsId The credentials ID
     * @param dockerHost    The Docker host URL (for domain matching)
     * @return The credentials, or null if not found
     */
    @Nullable
    public static DockerServerCredentials lookupCredentials(@Nullable String credentialsId,
                                                             @NonNull String dockerHost) {
        if (credentialsId == null || credentialsId.isBlank()) {
            return null;
        }

        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            return null;
        }

        try {
            return CredentialsMatchers.firstOrNull(
                    CredentialsProvider.lookupCredentials(
                            DockerServerCredentials.class,
                            jenkins,
                            ACL.SYSTEM,
                            URIRequirementBuilder.fromUri(dockerHost).build()
                    ),
                    CredentialsMatchers.withId(credentialsId)
            );
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to lookup credentials: " + credentialsId, e);
            return null;
        }
    }

    /**
     * Fills a ListBoxModel with available Docker server credentials.
     *
     * @param context    The context item (for permission checks)
     * @param dockerHost The Docker host URL (for domain matching)
     * @return ListBoxModel with credentials
     */
    @NonNull
    public static ListBoxModel fillCredentialsIdItems(@Nullable Item context,
                                                       @NonNull String dockerHost) {
        StandardListBoxModel model = new StandardListBoxModel();

        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            return model;
        }

        // Check permissions
        if (context == null) {
            if (!jenkins.hasPermission(Jenkins.ADMINISTER)) {
                return model.includeCurrentValue("");
            }
        } else {
            if (!context.hasPermission(Item.EXTENDED_READ)
                    && !context.hasPermission(CredentialsProvider.USE_ITEM)) {
                return model.includeCurrentValue("");
            }
        }

        model.add("- none -", "");

        model.includeMatchingAs(
                context instanceof Queue.Task
                        ? Tasks.getAuthenticationOf((Queue.Task) context)
                        : ACL.SYSTEM,
                context,
                DockerServerCredentials.class,
                dockerHost.isBlank()
                        ? Collections.emptyList()
                        : URIRequirementBuilder.fromUri(dockerHost).build(),
                CredentialsMatchers.always()
        );

        return model;
    }

    /**
     * Gets the CA certificate from credentials.
     */
    @Nullable
    public static String getCaCertificate(@Nullable DockerServerCredentials credentials) {
        if (credentials == null) {
            return null;
        }
        return credentials.getServerCaCertificate();
    }

    /**
     * Gets the client certificate from credentials.
     */
    @Nullable
    public static String getClientCertificate(@Nullable DockerServerCredentials credentials) {
        if (credentials == null) {
            return null;
        }
        return credentials.getClientCertificate();
    }

    /**
     * Gets the client key from credentials.
     */
    @Nullable
    public static String getClientKey(@Nullable DockerServerCredentials credentials) {
        if (credentials == null) {
            return null;
        }
        var secret = credentials.getClientKeySecret();
        return secret != null ? secret.getPlainText() : null;
    }

    // ==========================================
    // Registry Authentication Support
    // ==========================================

    /**
     * Looks up username/password credentials for Docker registry authentication.
     *
     * @param credentialsId The credentials ID
     * @return The credentials, or null if not found
     */
    @Nullable
    public static StandardUsernamePasswordCredentials lookupRegistryCredentials(
            @Nullable String credentialsId) {
        if (credentialsId == null || credentialsId.isBlank()) {
            return null;
        }

        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            return null;
        }

        try {
            return CredentialsMatchers.firstOrNull(
                    CredentialsProvider.lookupCredentials(
                            StandardUsernamePasswordCredentials.class,
                            jenkins,
                            ACL.SYSTEM,
                            Collections.emptyList()
                    ),
                    CredentialsMatchers.withId(credentialsId)
            );
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to lookup registry credentials: " + credentialsId, e);
            return null;
        }
    }

    /**
     * Creates AuthConfig for Docker registry from credentials.
     *
     * @param credentialsId   The credentials ID for registry authentication
     * @param registryAddress The registry address (e.g., "docker.io", "gcr.io")
     * @return AuthConfig or null if credentials not found
     */
    @Nullable
    public static AuthConfig createAuthConfig(
            @Nullable String credentialsId,
            @Nullable String registryAddress) {
        StandardUsernamePasswordCredentials creds = lookupRegistryCredentials(credentialsId);
        if (creds == null) {
            return null;
        }

        AuthConfig authConfig = new AuthConfig()
                .withUsername(creds.getUsername())
                .withPassword(creds.getPassword().getPlainText());

        if (registryAddress != null && !registryAddress.isBlank()) {
            authConfig.withRegistryAddress(registryAddress);
        }

        return authConfig;
    }

    /**
     * Extracts registry address from Docker image name.
     * <p>
     * Examples:
     * <ul>
     *   <li>"myregistry.com/image:tag" -&gt; "myregistry.com"</li>
     *   <li>"gcr.io/project/image:tag" -&gt; "gcr.io"</li>
     *   <li>"image:tag" -&gt; "https://index.docker.io/v1/" (Docker Hub)</li>
     *   <li>"myuser/myimage:v1" -&gt; "https://index.docker.io/v1/" (Docker Hub)</li>
     *   <li>"myregistry.com:5000/image:tag" -&gt; "myregistry.com:5000"</li>
     * </ul>
     *
     * @param imageName The Docker image name
     * @return The registry address
     */
    @NonNull
    public static String extractRegistryAddress(@NonNull String imageName) {
        if (!imageName.contains("/")) {
            // No slash means official Docker Hub image (e.g., "nginx:latest")
            return DOCKER_HUB_REGISTRY_URL;
        }

        String firstPart = imageName.split("/")[0];

        // Check if first part looks like a registry (contains . or :)
        if (firstPart.contains(".") || firstPart.contains(":")) {
            return firstPart;
        }

        // Likely Docker Hub user/image format (e.g., "myuser/myimage:v1")
        return DOCKER_HUB_REGISTRY_URL;
    }
}

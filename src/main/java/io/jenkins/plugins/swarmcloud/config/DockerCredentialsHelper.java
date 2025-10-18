package io.jenkins.plugins.swarmcloud.config;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
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
        return credentials.getClientKeySecret().getPlainText();
    }
}

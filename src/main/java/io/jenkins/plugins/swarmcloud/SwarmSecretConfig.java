package io.jenkins.plugins.swarmcloud;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import java.util.Collections;

/**
 * Configuration for Docker Swarm Secrets to be mounted in agent containers.
 * Supports both external secrets (pre-created in Swarm) and referencing
 * Jenkins credentials.
 */
public class SwarmSecretConfig extends AbstractDescribableImpl<SwarmSecretConfig> {

    /**
     * Name of the Docker Swarm secret.
     */
    private final String secretName;

    /**
     * Target path inside the container where the secret will be mounted.
     * Defaults to /run/secrets/{secretName}
     */
    private String targetPath;

    /**
     * File name inside the target directory.
     * Defaults to the secret name.
     */
    private String fileName;

    /**
     * File mode (permissions) in octal format (e.g., "0400").
     */
    private String fileMode;

    /**
     * UID of the file owner inside the container.
     */
    private String uid;

    /**
     * GID of the file owner inside the container.
     */
    private String gid;

    /**
     * Optional Jenkins credentials ID to create the secret from.
     * If specified, the secret will be created/updated before service creation.
     */
    private String credentialsId;

    @DataBoundConstructor
    public SwarmSecretConfig(@NonNull String secretName) {
        this.secretName = Util.fixEmptyAndTrim(secretName);
    }

    @NonNull
    public String getSecretName() {
        return secretName != null ? secretName : "";
    }

    @Nullable
    public String getTargetPath() {
        return targetPath;
    }

    @DataBoundSetter
    public void setTargetPath(String targetPath) {
        this.targetPath = Util.fixEmptyAndTrim(targetPath);
    }

    @Nullable
    public String getFileName() {
        return fileName;
    }

    @DataBoundSetter
    public void setFileName(String fileName) {
        this.fileName = Util.fixEmptyAndTrim(fileName);
    }

    @Nullable
    public String getFileMode() {
        return fileMode;
    }

    @DataBoundSetter
    public void setFileMode(String fileMode) {
        this.fileMode = Util.fixEmptyAndTrim(fileMode);
    }

    @Nullable
    public String getUid() {
        return uid;
    }

    @DataBoundSetter
    public void setUid(String uid) {
        this.uid = Util.fixEmptyAndTrim(uid);
    }

    @Nullable
    public String getGid() {
        return gid;
    }

    @DataBoundSetter
    public void setGid(String gid) {
        this.gid = Util.fixEmptyAndTrim(gid);
    }

    @Nullable
    public String getCredentialsId() {
        return credentialsId;
    }

    @DataBoundSetter
    public void setCredentialsId(String credentialsId) {
        this.credentialsId = Util.fixEmptyAndTrim(credentialsId);
    }

    /**
     * Returns the effective target path for the secret.
     */
    @NonNull
    public String getEffectiveTargetPath() {
        if (targetPath != null && !targetPath.isBlank()) {
            return targetPath;
        }
        return "/run/secrets/" + secretName;
    }

    /**
     * Returns the effective file name.
     */
    @NonNull
    public String getEffectiveFileName() {
        if (fileName != null && !fileName.isBlank()) {
            return fileName;
        }
        return secretName;
    }

    /**
     * Returns the file mode as a Long, or null if not specified.
     */
    @Nullable
    public Long getFileModeAsLong() {
        if (fileMode == null || fileMode.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(fileMode, 8);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Checks if this secret should be created from Jenkins credentials.
     */
    public boolean usesCredentials() {
        return credentialsId != null && !credentialsId.isBlank();
    }

    @Extension
    @Symbol("swarmSecret")
    public static class DescriptorImpl extends Descriptor<SwarmSecretConfig> {

        @NonNull
        @Override
        public String getDisplayName() {
            return "Docker Swarm Secret";
        }

        /**
         * Fills the credentials dropdown with available string credentials.
         */
        public ListBoxModel doFillCredentialsIdItems(
                @AncestorInPath Item item,
                @QueryParameter String credentialsId) {

            StandardListBoxModel result = new StandardListBoxModel();

            if (item == null) {
                if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                    return result.includeCurrentValue(credentialsId);
                }
            } else {
                if (!item.hasPermission(Item.EXTENDED_READ)
                        && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
                    return result.includeCurrentValue(credentialsId);
                }
            }

            result.includeEmptyValue();
            result.includeMatchingAs(
                    item instanceof hudson.model.Queue.Task
                            ? ((hudson.model.Queue.Task) item).getDefaultAuthentication()
                            : ACL.SYSTEM,
                    item,
                    StringCredentials.class,
                    Collections.<DomainRequirement>emptyList(),
                    CredentialsMatchers.always()
            );

            return result;
        }
    }
}

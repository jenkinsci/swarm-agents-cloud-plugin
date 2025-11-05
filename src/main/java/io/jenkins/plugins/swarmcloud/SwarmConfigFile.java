package io.jenkins.plugins.swarmcloud;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

/**
 * Configuration for Docker Swarm Configs to be mounted in agent containers.
 * Docker Configs are similar to Secrets but designed for non-sensitive configuration data.
 *
 * Format for parsing: configName:targetPath (e.g., nethasp.ini:/opt/1cv8/current/conf/nethasp.ini)
 */
public class SwarmConfigFile extends AbstractDescribableImpl<SwarmConfigFile> {

    /**
     * Name of the Docker Swarm config.
     */
    private final String configName;

    /**
     * Target path inside the container where the config will be mounted.
     */
    private String targetPath;

    /**
     * File name at the target location.
     * Defaults to the config name if not specified.
     */
    private String fileName;

    /**
     * File mode (permissions) in octal format (e.g., "0644").
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

    @DataBoundConstructor
    public SwarmConfigFile(@NonNull String configName) {
        this.configName = Util.fixEmptyAndTrim(configName);
    }

    /**
     * Parses a config string in format "configName:targetPath".
     * Example: "nethasp.ini:/opt/1cv8/current/conf/nethasp.ini"
     *
     * @param configString The config string to parse
     * @return SwarmConfigFile instance or null if parsing fails
     */
    @Nullable
    public static SwarmConfigFile parse(String configString) {
        if (configString == null || configString.isBlank()) {
            return null;
        }

        configString = configString.trim();
        int colonIdx = configString.indexOf(':');

        if (colonIdx <= 0) {
            // No colon or empty config name - just config name without target path
            return new SwarmConfigFile(configString);
        }

        // Check if this is a Windows path starting with drive letter (e.g., C:)
        if (colonIdx == 1 && Character.isLetter(configString.charAt(0))) {
            // Windows path, treat as just config name
            return new SwarmConfigFile(configString);
        }

        String configName = configString.substring(0, colonIdx).trim();
        String targetPath = configString.substring(colonIdx + 1).trim();

        if (configName.isEmpty()) {
            return null;
        }

        SwarmConfigFile config = new SwarmConfigFile(configName);
        if (!targetPath.isEmpty()) {
            config.setTargetPath(targetPath);
        }
        return config;
    }

    @NonNull
    public String getConfigName() {
        return configName != null ? configName : "";
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

    /**
     * Returns the effective target path for the config.
     * If target path is not set, defaults to /{configName}
     */
    @NonNull
    public String getEffectiveTargetPath() {
        if (targetPath != null && !targetPath.isBlank()) {
            return targetPath;
        }
        return "/" + configName;
    }

    /**
     * Returns the effective file name.
     * Extracts filename from target path if not explicitly set.
     */
    @NonNull
    public String getEffectiveFileName() {
        if (fileName != null && !fileName.isBlank()) {
            return fileName;
        }
        // Extract filename from target path
        String path = getEffectiveTargetPath();
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < path.length() - 1) {
            return path.substring(lastSlash + 1);
        }
        return configName;
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
     * Returns string representation in format "configName:targetPath".
     */
    @Override
    public String toString() {
        if (targetPath != null && !targetPath.isBlank()) {
            return configName + ":" + targetPath;
        }
        return configName;
    }

    @Extension
    @Symbol("swarmConfig")
    public static class DescriptorImpl extends Descriptor<SwarmConfigFile> {

        @NonNull
        @Override
        public String getDisplayName() {
            return "Docker Swarm Config";
        }

        public FormValidation doCheckConfigName(@QueryParameter String value) {
            if (Util.fixEmptyAndTrim(value) == null) {
                return FormValidation.error("Config name is required");
            }
            if (!value.matches("[a-zA-Z0-9_.-]+")) {
                return FormValidation.error("Config name can only contain letters, numbers, underscores, dots, and hyphens");
            }
            if (value.length() > 64) {
                return FormValidation.error("Config name must be 64 characters or less");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckFileMode(@QueryParameter String value) {
            if (Util.fixEmptyAndTrim(value) == null) {
                return FormValidation.ok(); // Optional
            }
            if (!value.matches("[0-7]{3,4}")) {
                return FormValidation.error("File mode must be in octal format (e.g., 0644)");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckTargetPath(@QueryParameter String value) {
            if (Util.fixEmptyAndTrim(value) == null) {
                return FormValidation.ok(); // Optional
            }
            if (!value.startsWith("/")) {
                return FormValidation.error("Target path must be an absolute path");
            }
            return FormValidation.ok();
        }
    }
}

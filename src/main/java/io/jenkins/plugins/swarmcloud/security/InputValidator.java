package io.jenkins.plugins.swarmcloud.security;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Util;

import java.util.regex.Pattern;

/**
 * Utility class for validating and sanitizing user input.
 * Provides protection against injection attacks and invalid input.
 */
public final class InputValidator {

    private InputValidator() {
        // Utility class
    }

    // Patterns for validation
    private static final Pattern CLOUD_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");
    private static final Pattern TEMPLATE_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");
    private static final Pattern DOCKER_IMAGE_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9][a-zA-Z0-9._:/-]*(?::[a-zA-Z0-9._-]+)?(?:@sha256:[a-f0-9]+)?$");
    private static final Pattern SERVICE_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9]{25}$");
    private static final Pattern LABEL_PATTERN = Pattern.compile("^[a-zA-Z0-9_ -]{0,200}$");
    private static final Pattern NETWORK_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_.-]{1,64}$");
    private static final Pattern DOCKER_HOST_PATTERN = Pattern.compile(
            "^(tcp|unix|npipe|ssh)://[a-zA-Z0-9._:/@-]+$");

    /**
     * Validates a cloud name.
     *
     * @param name The cloud name to validate
     * @return true if valid
     */
    public static boolean isValidCloudName(@Nullable String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return CLOUD_NAME_PATTERN.matcher(name).matches();
    }

    /**
     * Validates a template name.
     *
     * @param name The template name to validate
     * @return true if valid
     */
    public static boolean isValidTemplateName(@Nullable String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return TEMPLATE_NAME_PATTERN.matcher(name).matches();
    }

    /**
     * Validates a Docker image name.
     *
     * @param image The Docker image to validate
     * @return true if valid
     */
    public static boolean isValidDockerImage(@Nullable String image) {
        if (image == null || image.isBlank()) {
            return false;
        }
        return DOCKER_IMAGE_PATTERN.matcher(image).matches();
    }

    /**
     * Validates a Docker Swarm service ID.
     *
     * @param serviceId The service ID to validate
     * @return true if valid
     */
    public static boolean isValidServiceId(@Nullable String serviceId) {
        if (serviceId == null || serviceId.isBlank()) {
            return false;
        }
        return SERVICE_ID_PATTERN.matcher(serviceId).matches();
    }

    /**
     * Validates a label string.
     *
     * @param labels The label string to validate
     * @return true if valid
     */
    public static boolean isValidLabelString(@Nullable String labels) {
        if (labels == null || labels.isBlank()) {
            return true; // Labels are optional
        }
        return LABEL_PATTERN.matcher(labels).matches();
    }

    /**
     * Validates a Docker network name.
     *
     * @param network The network name to validate
     * @return true if valid
     */
    public static boolean isValidNetworkName(@Nullable String network) {
        if (network == null || network.isBlank()) {
            return true; // Network is optional
        }
        return NETWORK_NAME_PATTERN.matcher(network).matches();
    }

    /**
     * Validates a Docker host URL.
     *
     * @param dockerHost The Docker host URL to validate
     * @return true if valid
     */
    public static boolean isValidDockerHost(@Nullable String dockerHost) {
        if (dockerHost == null || dockerHost.isBlank()) {
            return false;
        }
        return DOCKER_HOST_PATTERN.matcher(dockerHost).matches();
    }

    /**
     * Sanitizes a string for safe use in logs.
     * Removes newlines and limits length to prevent log injection.
     *
     * @param input The input string
     * @return Sanitized string
     */
    @NonNull
    public static String sanitizeForLog(@Nullable String input) {
        if (input == null) {
            return "";
        }
        // Remove newlines and carriage returns
        String sanitized = input.replace("\n", "").replace("\r", "");
        // Limit length
        if (sanitized.length() > 200) {
            sanitized = sanitized.substring(0, 200) + "...";
        }
        return sanitized;
    }

    /**
     * Sanitizes a string for display, escaping HTML.
     *
     * @param input The input string
     * @return Sanitized string safe for HTML display
     */
    @NonNull
    public static String sanitizeForDisplay(@Nullable String input) {
        if (input == null) {
            return "";
        }
        return Util.escape(input);
    }

    /**
     * Validates a memory specification (e.g., "512m", "1g", "2048m").
     *
     * @param memory The memory specification to validate
     * @return true if valid
     */
    public static boolean isValidMemorySpec(@Nullable String memory) {
        if (memory == null || memory.isBlank()) {
            return true; // Optional
        }
        return memory.matches("^\\d+[bkmgBKMG]?$");
    }

    /**
     * Validates a CPU limit specification (e.g., "0.5", "2.0", "4").
     *
     * @param cpu The CPU specification to validate
     * @return true if valid
     */
    public static boolean isValidCpuSpec(@Nullable String cpu) {
        if (cpu == null || cpu.isBlank()) {
            return true; // Optional
        }
        try {
            double value = Double.parseDouble(cpu);
            return value > 0 && value <= 100;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Validates a port number.
     *
     * @param port The port number to validate
     * @return true if valid
     */
    public static boolean isValidPort(int port) {
        return port > 0 && port <= 65535;
    }

    /**
     * Validates a timeout value in seconds.
     *
     * @param timeout The timeout in seconds
     * @return true if valid (1 second to 1 hour)
     */
    public static boolean isValidTimeout(int timeout) {
        return timeout >= 1 && timeout <= 3600;
    }

    /**
     * Validates a URL.
     *
     * @param url The URL to validate
     * @return true if valid
     */
    public static boolean isValidUrl(@Nullable String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            java.net.URI uri = new java.net.URI(url);
            String scheme = uri.getScheme();
            return "http".equals(scheme) || "https".equals(scheme);
        } catch (java.net.URISyntaxException e) {
            return false;
        }
    }

    /**
     * Validates a placement constraint.
     *
     * @param constraint The placement constraint to validate
     * @return true if valid
     */
    public static boolean isValidPlacementConstraint(@Nullable String constraint) {
        if (constraint == null || constraint.isBlank()) {
            return false;
        }
        // Basic format: node.xxx==value or node.xxx!=value
        return constraint.matches("^node\\.[a-zA-Z0-9._-]+[!=]=.+$");
    }

    /**
     * Validates an environment variable name.
     *
     * @param name The environment variable name
     * @return true if valid
     */
    public static boolean isValidEnvVarName(@Nullable String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return name.matches("^[a-zA-Z_][a-zA-Z0-9_]*$");
    }
}

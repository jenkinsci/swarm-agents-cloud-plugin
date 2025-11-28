package io.jenkins.plugins.swarmcloud.rest;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.RootAction;
import hudson.slaves.Cloud;
import io.jenkins.plugins.swarmcloud.SwarmAgent;
import io.jenkins.plugins.swarmcloud.SwarmAgentTemplate;
import io.jenkins.plugins.swarmcloud.SwarmCloud;
import io.jenkins.plugins.swarmcloud.monitoring.ClusterMonitor;
import io.jenkins.plugins.swarmcloud.monitoring.ClusterStatus;
import io.jenkins.plugins.swarmcloud.monitoring.SwarmAuditLog;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.verb.DELETE;
import org.kohsuke.stapler.verb.GET;
import org.kohsuke.stapler.verb.POST;
import org.kohsuke.stapler.verb.PUT;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * REST API for managing Docker Swarm agents.
 */
@Extension
public class SwarmRestApi implements RootAction {

    private static final String JSON_CONTENT_TYPE = "application/json;charset=UTF-8";

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return "Swarm REST API";
    }

    @Override
    public String getUrlName() {
        return "swarm-api";
    }

    /**
     * GET /swarm-api/clouds
     */
    @GET
    public void doClouds(StaplerRequest req, StaplerResponse rsp) throws IOException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        JSONArray clouds = new JSONArray();
        for (SwarmCloud cloud : getSwarmClouds()) {
            JSONObject c = new JSONObject();
            c.put("name", cloud.name);
            c.put("dockerHost", cloud.getDockerHost());
            c.put("maxConcurrentAgents", cloud.getMaxConcurrentAgents());
            c.put("currentAgents", cloud.countCurrentAgents());
            c.put("templateCount", cloud.getTemplates().size());
            clouds.add(c);
        }

        writeJsonResponse(rsp, 200, clouds.toString());
    }

    /**
     * GET /swarm-api/cloud?name={name}
     */
    @GET
    public void doCloud(StaplerRequest req, StaplerResponse rsp,
                        @QueryParameter String name) throws IOException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        if (name == null || name.isBlank()) {
            writeJsonError(rsp, 400, "Cloud name is required");
            return;
        }

        SwarmCloud cloud = findCloud(name);
        if (cloud == null) {
            writeJsonError(rsp, 404, "Cloud not found: " + name);
            return;
        }

        JSONObject result = new JSONObject();
        result.put("name", cloud.name);
        result.put("dockerHost", cloud.getDockerHost());
        result.put("jenkinsUrl", cloud.getJenkinsUrl());
        result.put("swarmNetwork", cloud.getSwarmNetwork());
        result.put("maxConcurrentAgents", cloud.getMaxConcurrentAgents());
        result.put("currentAgents", cloud.countCurrentAgents());
        result.put("canProvision", cloud.canProvision());

        JSONArray templates = new JSONArray();
        for (SwarmAgentTemplate template : cloud.getTemplates()) {
            templates.add(templateToJson(template));
        }
        result.put("templates", templates);

        writeJsonResponse(rsp, 200, result.toString());
    }

    /**
     * GET /swarm-api/templates
     */
    @GET
    public void doTemplates(StaplerRequest req, StaplerResponse rsp,
                            @QueryParameter String cloud) throws IOException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        JSONArray templates = new JSONArray();

        if (cloud != null && !cloud.isBlank()) {
            SwarmCloud swarmCloud = findCloud(cloud);
            if (swarmCloud == null) {
                writeJsonError(rsp, 404, "Cloud not found: " + cloud);
                return;
            }
            for (SwarmAgentTemplate template : swarmCloud.getTemplates()) {
                JSONObject t = templateToJson(template);
                t.put("cloudName", swarmCloud.name);
                templates.add(t);
            }
        } else {
            for (SwarmCloud swarmCloud : getSwarmClouds()) {
                for (SwarmAgentTemplate template : swarmCloud.getTemplates()) {
                    JSONObject t = templateToJson(template);
                    t.put("cloudName", swarmCloud.name);
                    templates.add(t);
                }
            }
        }

        writeJsonResponse(rsp, 200, templates.toString());
    }

    /**
     * GET /swarm-api/agents
     */
    @GET
    public void doAgents(StaplerRequest req, StaplerResponse rsp,
                         @QueryParameter String cloud) throws IOException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        JSONArray agents = new JSONArray();

        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins != null) {
            for (var node : jenkins.getNodes()) {
                if (node instanceof SwarmAgent) {
                    SwarmAgent agent = (SwarmAgent) node;

                    if (cloud != null && !cloud.isBlank() && !cloud.equals(agent.getCloudName())) {
                        continue;
                    }

                    JSONObject a = new JSONObject();
                    a.put("name", agent.getNodeName());
                    a.put("cloudName", agent.getCloudName());
                    a.put("serviceId", agent.getServiceId());
                    a.put("templateName", agent.getTemplateName());
                    a.put("remoteFS", agent.getRemoteFS());
                    a.put("numExecutors", agent.getNumExecutors());
                    a.put("labelString", agent.getLabelString());

                    var computer = agent.toComputer();
                    if (computer != null) {
                        a.put("online", computer.isOnline());
                        a.put("idle", computer.isIdle());
                        a.put("connecting", computer.isConnecting());
                    }

                    agents.add(a);
                }
            }
        }

        writeJsonResponse(rsp, 200, agents.toString());
    }

    /**
     * GET /swarm-api/metrics
     */
    @GET
    public void doMetrics(StaplerRequest req, StaplerResponse rsp,
                          @QueryParameter String cloud) throws IOException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        if (cloud != null && !cloud.isBlank()) {
            ClusterStatus status = ClusterMonitor.getStatus(cloud);
            writeJsonResponse(rsp, 200, statusToJson(status).toString());
            return;
        }

        JSONObject result = new JSONObject();
        JSONArray clouds = new JSONArray();

        for (var entry : ClusterMonitor.getAllStatuses().entrySet()) {
            clouds.add(statusToJson(entry.getValue()));
        }

        result.put("clouds", clouds);
        result.put("lastUpdate", ClusterMonitor.getLastUpdate());

        writeJsonResponse(rsp, 200, result.toString());
    }

    /**
     * POST /swarm-api/provision
     */
    @POST
    public void doProvision(StaplerRequest req, StaplerResponse rsp,
                            @QueryParameter String cloud,
                            @QueryParameter String template) throws IOException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        if (cloud == null || cloud.isBlank() || template == null || template.isBlank()) {
            writeJsonError(rsp, 400, "Cloud name and template name are required");
            return;
        }

        SwarmCloud swarmCloud = findCloud(cloud);
        if (swarmCloud == null) {
            writeJsonError(rsp, 404, "Cloud not found: " + cloud);
            return;
        }

        SwarmAgentTemplate tmpl = null;
        for (SwarmAgentTemplate t : swarmCloud.getTemplates()) {
            if (t.getName().equals(template)) {
                tmpl = t;
                break;
            }
        }

        if (tmpl == null) {
            writeJsonError(rsp, 404, "Template not found: " + template);
            return;
        }

        if (!swarmCloud.canProvision()) {
            writeJsonError(rsp, 400, "Cannot provision: max agents reached");
            return;
        }

        if (tmpl.getAvailableCapacity() <= 0) {
            writeJsonError(rsp, 400, "Cannot provision: template max instances reached");
            return;
        }

        try {
            String agentName = tmpl.generateAgentName();
            String serviceId = swarmCloud.getDockerClient().createService(
                    agentName,
                    tmpl,
                    swarmCloud.getEffectiveJenkinsUrl(),
                    swarmCloud.getSwarmNetwork()
            );

            SwarmAgent agent = new SwarmAgent(agentName, tmpl, swarmCloud.name, serviceId);
            Jenkins.get().addNode(agent);

            JSONObject result = new JSONObject();
            result.put("agentName", agentName);
            result.put("serviceId", serviceId);
            result.put("status", "provisioning");

            writeJsonResponse(rsp, 200, result.toString());
        } catch (Exception e) {
            writeJsonError(rsp, 500, "Failed to provision: " + e.getMessage());
        }
    }

    /**
     * PUT /swarm-api/template - Update a template's configuration
     *
     * Request body (JSON):
     * {
     *   "cloud": "cloud-name",
     *   "template": "template-name",
     *   "image": "new-image:tag",         // optional
     *   "labelString": "new-labels",       // optional
     *   "maxInstances": 10,                // optional
     *   "cpuLimit": "2.0",                 // optional
     *   "memoryLimit": "4g"                // optional
     * }
     */
    @PUT
    @POST  // CSRF protection - requires crumb token
    public void doTemplate(StaplerRequest req, StaplerResponse rsp) throws IOException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        // Parse JSON body
        JSONObject body;
        try {
            StringBuilder sb = new StringBuilder();
            String line;
            var reader = req.getReader();
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            body = JSONObject.fromObject(sb.toString());
        } catch (Exception e) {
            writeJsonError(rsp, 400, "Invalid JSON body: " + e.getMessage());
            return;
        }

        String cloudName = body.optString("cloud", null);
        String templateName = body.optString("template", null);

        if (cloudName == null || cloudName.isBlank() || templateName == null || templateName.isBlank()) {
            writeJsonError(rsp, 400, "Cloud name and template name are required");
            return;
        }

        SwarmCloud cloud = findCloud(cloudName);
        if (cloud == null) {
            writeJsonError(rsp, 404, "Cloud not found: " + cloudName);
            return;
        }

        SwarmAgentTemplate template = null;
        for (SwarmAgentTemplate t : cloud.getTemplates()) {
            if (t.getName().equals(templateName)) {
                template = t;
                break;
            }
        }

        if (template == null) {
            writeJsonError(rsp, 404, "Template not found: " + templateName);
            return;
        }

        // Update fields if provided
        boolean updated = false;

        if (body.has("image")) {
            String newImage = body.getString("image");
            if (newImage != null && !newImage.isBlank()) {
                template.setImage(newImage);
                updated = true;
            }
        }

        if (body.has("labelString")) {
            template.setLabelString(body.getString("labelString"));
            updated = true;
        }

        if (body.has("maxInstances")) {
            template.setMaxInstances(body.getInt("maxInstances"));
            updated = true;
        }

        if (body.has("cpuLimit")) {
            template.setCpuLimit(body.getString("cpuLimit"));
            updated = true;
        }

        if (body.has("memoryLimit")) {
            template.setMemoryLimit(body.getString("memoryLimit"));
            updated = true;
        }

        if (body.has("numExecutors")) {
            template.setNumExecutors(body.getInt("numExecutors"));
            updated = true;
        }

        if (body.has("remoteFs")) {
            template.setRemoteFs(body.getString("remoteFs"));
            updated = true;
        }

        if (!updated) {
            writeJsonError(rsp, 400, "No fields to update provided");
            return;
        }

        // Save configuration
        try {
            Jenkins.get().save();
        } catch (Exception e) {
            writeJsonError(rsp, 500, "Failed to save configuration: " + e.getMessage());
            return;
        }

        JSONObject result = new JSONObject();
        result.put("status", "updated");
        result.put("template", templateToJson(template));
        writeJsonResponse(rsp, 200, result.toString());
    }

    /**
     * GET /swarm-api/template - Get single template details
     */
    @GET
    public void doTemplateGet(StaplerRequest req, StaplerResponse rsp,
                              @QueryParameter String cloud,
                              @QueryParameter String name) throws IOException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        if (cloud == null || cloud.isBlank() || name == null || name.isBlank()) {
            writeJsonError(rsp, 400, "Cloud name and template name are required");
            return;
        }

        SwarmCloud swarmCloud = findCloud(cloud);
        if (swarmCloud == null) {
            writeJsonError(rsp, 404, "Cloud not found: " + cloud);
            return;
        }

        for (SwarmAgentTemplate template : swarmCloud.getTemplates()) {
            if (template.getName().equals(name)) {
                JSONObject result = templateToJson(template);
                result.put("cloudName", cloud);
                writeJsonResponse(rsp, 200, result.toString());
                return;
            }
        }

        writeJsonError(rsp, 404, "Template not found: " + name);
    }

    // Helper methods

    private void writeJsonResponse(StaplerResponse rsp, int status, String json) throws IOException {
        rsp.setStatus(status);
        rsp.setContentType(JSON_CONTENT_TYPE);
        try (PrintWriter writer = rsp.getWriter()) {
            writer.write(json);
        }
    }

    private void writeJsonError(StaplerResponse rsp, int status, String message) throws IOException {
        JSONObject error = new JSONObject();
        error.put("error", message);
        writeJsonResponse(rsp, status, error.toString());
    }

    @NonNull
    private List<SwarmCloud> getSwarmClouds() {
        List<SwarmCloud> clouds = new ArrayList<>();
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins != null) {
            for (Cloud cloud : jenkins.clouds) {
                if (cloud instanceof SwarmCloud) {
                    clouds.add((SwarmCloud) cloud);
                }
            }
        }
        return clouds;
    }

    private SwarmCloud findCloud(String name) {
        for (SwarmCloud cloud : getSwarmClouds()) {
            if (cloud.name.equals(name)) {
                return cloud;
            }
        }
        return null;
    }

    private JSONObject templateToJson(SwarmAgentTemplate template) {
        JSONObject t = new JSONObject();
        t.put("name", template.getName());
        t.put("image", template.getImage());
        t.put("labelString", template.getLabelString());
        t.put("remoteFs", template.getRemoteFs());
        t.put("numExecutors", template.getNumExecutors());
        t.put("maxInstances", template.getMaxInstances());
        t.put("currentInstances", template.getCurrentInstances());
        t.put("availableCapacity", template.getAvailableCapacity());
        t.put("cpuLimit", template.getCpuLimit());
        t.put("memoryLimit", template.getMemoryLimit());
        return t;
    }

    /**
     * GET /swarm-api/prometheus - Prometheus metrics endpoint
     * Returns metrics in Prometheus text format (OpenMetrics compatible)
     */
    @GET
    public void doPrometheus(StaplerRequest req, StaplerResponse rsp) throws IOException {
        // Prometheus endpoint can be accessed without admin permission for monitoring
        // but we still check basic read permission
        Jenkins.get().checkPermission(Jenkins.READ);

        StringBuilder sb = new StringBuilder();

        // Header comment
        sb.append("# HELP swarm_clouds_total Total number of configured Swarm clouds\n");
        sb.append("# TYPE swarm_clouds_total gauge\n");
        sb.append("swarm_clouds_total ").append(getSwarmClouds().size()).append("\n\n");

        long totalAgents = 0;
        long totalProvisioned = 0;

        for (SwarmCloud cloud : getSwarmClouds()) {
            String cloudName = sanitizeMetricLabel(cloud.name);
            ClusterStatus status = ClusterMonitor.getStatus(cloud.name);

            // Cloud health
            sb.append("# HELP swarm_cloud_healthy Whether the cloud is healthy (1) or not (0)\n");
            sb.append("# TYPE swarm_cloud_healthy gauge\n");
            sb.append("swarm_cloud_healthy{cloud=\"").append(cloudName).append("\"} ")
                    .append(status.isHealthy() ? 1 : 0).append("\n\n");

            // Agents metrics
            sb.append("# HELP swarm_agents_max Maximum agents allowed for this cloud\n");
            sb.append("# TYPE swarm_agents_max gauge\n");
            sb.append("swarm_agents_max{cloud=\"").append(cloudName).append("\"} ")
                    .append(cloud.getMaxConcurrentAgents()).append("\n");

            sb.append("# HELP swarm_agents_current Current number of agents\n");
            sb.append("# TYPE swarm_agents_current gauge\n");
            sb.append("swarm_agents_current{cloud=\"").append(cloudName).append("\"} ")
                    .append(status.getCurrentAgents()).append("\n\n");

            totalAgents += status.getCurrentAgents();

            // Cluster nodes
            sb.append("# HELP swarm_nodes_total Total nodes in the Swarm cluster\n");
            sb.append("# TYPE swarm_nodes_total gauge\n");
            sb.append("swarm_nodes_total{cloud=\"").append(cloudName).append("\"} ")
                    .append(status.getTotalNodes()).append("\n");

            sb.append("# HELP swarm_nodes_ready Ready nodes in the Swarm cluster\n");
            sb.append("# TYPE swarm_nodes_ready gauge\n");
            sb.append("swarm_nodes_ready{cloud=\"").append(cloudName).append("\"} ")
                    .append(status.getReadyNodes()).append("\n\n");

            // Tasks
            sb.append("# HELP swarm_tasks_running Running tasks in the cluster\n");
            sb.append("# TYPE swarm_tasks_running gauge\n");
            sb.append("swarm_tasks_running{cloud=\"").append(cloudName).append("\"} ")
                    .append(status.getRunningTasks()).append("\n");

            sb.append("# HELP swarm_tasks_pending Pending tasks in the cluster\n");
            sb.append("# TYPE swarm_tasks_pending gauge\n");
            sb.append("swarm_tasks_pending{cloud=\"").append(cloudName).append("\"} ")
                    .append(status.getPendingTasks()).append("\n");

            sb.append("# HELP swarm_tasks_failed Failed tasks in the cluster\n");
            sb.append("# TYPE swarm_tasks_failed gauge\n");
            sb.append("swarm_tasks_failed{cloud=\"").append(cloudName).append("\"} ")
                    .append(status.getFailedTasks()).append("\n\n");

            // Resources
            sb.append("# HELP swarm_memory_total_bytes Total memory in the cluster\n");
            sb.append("# TYPE swarm_memory_total_bytes gauge\n");
            sb.append("swarm_memory_total_bytes{cloud=\"").append(cloudName).append("\"} ")
                    .append(status.getTotalMemory()).append("\n");

            sb.append("# HELP swarm_memory_used_bytes Used memory in the cluster\n");
            sb.append("# TYPE swarm_memory_used_bytes gauge\n");
            sb.append("swarm_memory_used_bytes{cloud=\"").append(cloudName).append("\"} ")
                    .append(status.getUsedMemory()).append("\n");

            sb.append("# HELP swarm_cpu_total Total CPU cores in the cluster\n");
            sb.append("# TYPE swarm_cpu_total gauge\n");
            sb.append("swarm_cpu_total{cloud=\"").append(cloudName).append("\"} ")
                    .append(String.format("%.2f", status.getTotalCpu())).append("\n");

            sb.append("# HELP swarm_cpu_used Used CPU cores in the cluster\n");
            sb.append("# TYPE swarm_cpu_used gauge\n");
            sb.append("swarm_cpu_used{cloud=\"").append(cloudName).append("\"} ")
                    .append(String.format("%.2f", status.getUsedCpu())).append("\n\n");

            // Utilization
            sb.append("# HELP swarm_utilization_percent Agent utilization percentage\n");
            sb.append("# TYPE swarm_utilization_percent gauge\n");
            sb.append("swarm_utilization_percent{cloud=\"").append(cloudName).append("\"} ")
                    .append(String.format("%.2f", status.getUtilizationPercent())).append("\n\n");

            // Per-template metrics
            for (SwarmAgentTemplate template : cloud.getTemplates()) {
                String templateName = sanitizeMetricLabel(template.getName());

                sb.append("# HELP swarm_template_instances_max Max instances for template\n");
                sb.append("# TYPE swarm_template_instances_max gauge\n");
                sb.append("swarm_template_instances_max{cloud=\"").append(cloudName)
                        .append("\",template=\"").append(templateName).append("\"} ")
                        .append(template.getMaxInstances()).append("\n");

                sb.append("# HELP swarm_template_instances_current Current instances for template\n");
                sb.append("# TYPE swarm_template_instances_current gauge\n");
                sb.append("swarm_template_instances_current{cloud=\"").append(cloudName)
                        .append("\",template=\"").append(templateName).append("\"} ")
                        .append(template.getCurrentInstances()).append("\n");

                totalProvisioned += template.getCurrentInstances();
            }
            sb.append("\n");
        }

        // Summary metrics
        sb.append("# HELP swarm_agents_total_all Total agents across all clouds\n");
        sb.append("# TYPE swarm_agents_total_all gauge\n");
        sb.append("swarm_agents_total_all ").append(totalAgents).append("\n");

        // Write response in Prometheus format
        rsp.setStatus(200);
        rsp.setContentType("text/plain; version=0.0.4; charset=utf-8");
        try (PrintWriter writer = rsp.getWriter()) {
            writer.write(sb.toString());
        }
    }

    /**
     * Sanitizes a string for use as a Prometheus metric label value.
     */
    private String sanitizeMetricLabel(String value) {
        if (value == null) return "unknown";
        // Replace non-alphanumeric chars with underscore, remove consecutive underscores
        return value.replaceAll("[^a-zA-Z0-9_]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }

    /**
     * GET /swarm-api/audit - Get audit log entries
     *
     * @param cloud Optional cloud name filter
     * @param limit Maximum entries to return (default 100)
     */
    @GET
    public void doAudit(StaplerRequest req, StaplerResponse rsp,
                        @QueryParameter String cloud,
                        @QueryParameter(value = "limit") Integer limitParam) throws IOException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        int limit = (limitParam != null && limitParam > 0) ? Math.min(limitParam, 500) : 100;

        var entries = (cloud != null && !cloud.isBlank())
                ? SwarmAuditLog.getEntriesForCloud(cloud, limit)
                : SwarmAuditLog.getRecentEntries(limit);

        JSONArray result = new JSONArray();
        for (var entry : entries) {
            JSONObject e = new JSONObject();
            e.put("timestamp", entry.getTimestamp());
            e.put("formattedTimestamp", entry.getFormattedTimestamp());
            e.put("event", entry.getEvent().name());
            e.put("cloudName", entry.getCloudName());
            e.put("templateName", entry.getTemplateName());
            e.put("agentName", entry.getAgentName());
            e.put("serviceId", entry.getServiceId());
            e.put("message", entry.getMessage());
            e.put("user", entry.getUser());
            result.add(e);
        }

        writeJsonResponse(rsp, 200, result.toString());
    }

    private JSONObject statusToJson(ClusterStatus status) {
        JSONObject json = new JSONObject();
        json.put("cloudName", status.getCloudName());
        json.put("healthy", status.isHealthy());
        json.put("errorMessage", status.getErrorMessage());
        json.put("totalNodes", status.getTotalNodes());
        json.put("readyNodes", status.getReadyNodes());
        json.put("activeServices", status.getActiveServices());
        json.put("runningTasks", status.getRunningTasks());
        json.put("pendingTasks", status.getPendingTasks());
        json.put("failedTasks", status.getFailedTasks());
        json.put("maxAgents", status.getMaxAgents());
        json.put("currentAgents", status.getCurrentAgents());
        json.put("utilizationPercent", status.getUtilizationPercent());
        json.put("lastUpdate", status.getLastUpdate());
        return json;
    }
}
